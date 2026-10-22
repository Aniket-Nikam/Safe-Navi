import base64
import pytest
from fastapi.testclient import TestClient
from app.main import app
from app.product_database import create_user, store_news_signal, list_news_signals, connect_product
from app.news_intelligence import parse_feed

client=TestClient(app)


@pytest.fixture
def people():
    result={}
    for role in ("citizen","government","admin"):
        user=create_user(role,role+"@integration.test","integration-password",role)
        value=client.post("/api/v1/auth/login",json=dict(email=user["email"],password="integration-password")).json()
        result[role]={"Authorization":"Bearer "+value["access_token"]}
    return result


def report(headers):
    response=client.post("/api/v1/reports",headers=headers,json=dict(title="Road lighting problem",category_key="POOR_LIGHTING",description="Street lighting is not working on this road.",latitude=19.07,longitude=73,address="Vashi road, Navi Mumbai",client_request_id="report-integration"))
    assert response.status_code==201,response.text
    return response.json()["id"]


def signal(category="FLOODING"):
    assert store_news_signal(dict(headline="Flooding affects Vashi road",publisher="example.test",source_url="https://example.test/vashi-flood",summary="Current road disruption requires official checking",published_at="2026-10-18T01:00:00+00:00",location_text="Vashi",category=category,confidence=.69))
    return list_news_signals()[0]["id"]


def publication():
    return dict(latitude=19.07,longitude=73,address="Vashi affected road",severity="HIGH",road_status="CLOSED",verification_note="Confirmed the currently flooded road with a field inspection",confirmed_current=True,valid_hours=3,no_go_radius_m=60)


def routes():
    return dict(time_period="midday",profile="fastest",routes=[dict(route_id="crossing",travel_minutes=10,distance_meters=2200,coordinates=[dict(latitude=19.06,longitude=73),dict(latitude=19.08,longitude=73)]),dict(route_id="avoiding",travel_minutes=15,distance_meters=2200,coordinates=[dict(latitude=19.06,longitude=73.02),dict(latitude=19.08,longitude=73.02)])])


def test_news_only_enters_map_and_excludes_route_after_official_publication(people):
    news=signal(); path=f"/api/v1/government/news-signals/{news}/publish"
    assert client.get("/api/v1/hazards/map").json()["hazards"]==[]
    assert client.get("/api/v1/government/news-signals",headers=people["government"]).json()[0]["routing_effect"]=="AWAITING_OFFICIAL_VERIFICATION"
    for role in ("citizen","admin"):
        assert client.post(path,headers=people[role],json=publication()).status_code==403
    payload=publication();payload["confirmed_current"]=False
    assert client.post(path,headers=people["government"],json=payload).status_code==422
    published=client.post(path,headers=people["government"],json=publication())
    assert published.status_code==200,published.text
    hazard=published.json();assert hazard["source"]=="NEWS_VERIFIED" and hazard["valid_until"]
    published_signal=client.get("/api/v1/government/news-signals",headers=people["government"]).json()[0]
    assert published_signal["routing_effect"]=="EXCLUDES_INTERSECTING_ROUTES"
    assert published_signal["linked_hazard_id"]==hazard["id"]
    # Existing published records from before the canonical-column fix still work.
    with connect_product() as db:
        db.execute("UPDATE news_signals SET linked_hazard_id=NULL WHERE id=?",(news,))
    assert client.get("/api/v1/government/news-signals",headers=people["government"]).json()[0]["linked_hazard_id"]==hazard["id"]
    record=client.get("/api/v1/hazards/map").json()["hazards"][0]
    assert record["news_source_url"]=="https://example.test/vashi-flood"
    evaluated=client.post("/api/v1/risk/route",json=routes()).json()
    assert evaluated["recommendations"]["fastest"]["route_id"]=="avoiding"
    assert evaluated["excluded_routes"][0]["route_id"]=="crossing"
    assert client.post(path,headers=people["government"],json=publication()).json()["id"]==hazard["id"]
    assert client.post(f"/api/v1/government/news-signals/{news}/review",headers=people["admin"],json=dict(review_status="DISMISSED")).status_code==409
    with connect_product() as db:
        assert db.execute("SELECT COUNT(*) FROM hazard_history WHERE hazard_id=?",(hazard["id"],)).fetchone()[0]==1
        db.execute("UPDATE hazards SET valid_until=? WHERE id=?",("2000-01-01T00:00:00+00:00",hazard["id"]))
    assert client.get("/api/v1/hazards/map").json()["hazards"]==[]
    assert client.get("/api/v1/government/news-signals",headers=people["government"]).json()[0]["routing_effect"]=="INACTIVE"
    assert client.post("/api/v1/risk/route",json=routes()).json()["excluded_routes"]==[]


def test_news_severity_changes_exposure_without_an_automatic_closure(people):
    news=signal("TRAFFIC");payload=publication();payload["road_status"]="UNSAFE"
    before=client.post("/api/v1/risk/route",json=routes()).json()
    assert client.post(f"/api/v1/government/news-signals/{news}/publish",headers=people["government"],json=payload).status_code==200
    after=client.post("/api/v1/risk/route",json=routes()).json()
    a={r["route_id"]:r for r in before["results"]};b={r["route_id"]:r for r in after["results"]}
    assert b["crossing"]["risk_exposure"]>a["crossing"]["risk_exposure"]
    assert after["excluded_routes"]==[]
    assert client.get("/api/v1/government/news-signals",headers=people["government"]).json()[0]["routing_effect"]=="INCREASES_NEARBY_ROUTE_RISK"


@pytest.mark.parametrize("passability", ["UNKNOWN", "IMPASSABLE", "PASSABLE"])
def test_published_flood_news_uses_explicit_passability(people, passability):
    news=signal();payload=publication();payload.update(road_status="CAUTION",passability=passability)
    hazard=client.post(f"/api/v1/government/news-signals/{news}/publish",headers=people["government"],json=payload)
    assert hazard.status_code==200 and hazard.json()["passability"]==passability
    result=client.post("/api/v1/risk/route",json=routes()).json()
    blocked=passability!="PASSABLE"
    assert bool(result["excluded_routes"])==blocked
    record=client.get("/api/v1/government/news-signals",headers=people["government"]).json()[0]
    assert record["routing_effect"]==("EXCLUDES_INTERSECTING_ROUTES" if blocked else "INCREASES_NEARBY_ROUTE_RISK")


def test_chat_retries_are_idempotent_and_pagination_is_case_scoped(people):
    case=report(people["citizen"]);path=f"/api/v1/reports/{case}/messages"
    payload=dict(content="Please inspect this road",client_request_id="stable-message-request")
    first=client.post(path,headers=people["citizen"],json=payload);assert first.status_code==201,first.text
    assert client.post(path,headers=people["citizen"],json=payload).json()["id"]==first.json()["id"]
    changed={**payload,"content":"A different message with reused retry ID"}
    assert client.post(path,headers=people["citizen"],json=changed).status_code==400
    ids=[first.json()["id"]]
    for i in range(4):ids.append(client.post(path,headers=people["government"],json=dict(content=f"Official update {i}",client_request_id=f"official-request-{i}")).json()["id"])
    latest=client.get(path,headers=people["citizen"],params=dict(limit=2)).json();assert [m["id"] for m in latest]==ids[-2:]
    previous=client.get(path,headers=people["citizen"],params=dict(limit=2,before_id=latest[0]["id"])).json();assert [m["id"] for m in previous]==ids[1:3]
    newer=client.get(path,headers=people["citizen"],params=dict(after_id=ids[0])).json();assert [m["id"] for m in newer]==ids[1:]
    assert client.post(path,headers=people["admin"],json=payload).status_code==403
    assert client.get(path,headers=people["citizen"],params=dict(before_id="not-in-case")).status_code==400


def test_official_photo_can_be_shared_with_the_case_owner_but_not_another_citizen(people):
    case=report(people["citizen"])
    image=base64.b64decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a89sAAAAASUVORK5CYII=")
    upload=client.post("/api/v1/evidence",headers=people["government"],files={"file":("field.png",image,"image/png")});assert upload.status_code==201,upload.text
    evidence=upload.json()["id"]
    sent=client.post(f"/api/v1/reports/{case}/messages",headers=people["government"],json=dict(content="Field inspection photograph",evidence_id=evidence,client_request_id="field-photo-001"));assert sent.status_code==201,sent.text
    assert client.get(f"/api/v1/evidence/{evidence}",headers=people["citizen"]).status_code==200
    create_user("Other","other@integration.test","integration-password")
    other=client.post("/api/v1/auth/login",json=dict(email="other@integration.test",password="integration-password")).json()
    headers={"Authorization":"Bearer "+other["access_token"]}
    assert client.get(f"/api/v1/evidence/{evidence}",headers=headers).status_code==404
    assert client.get(f"/api/v1/reports/{case}/messages",headers=headers).status_code==404


def test_missing_publication_date_is_not_fabricated():
    value=parse_feed(b"<rss><channel><item><title>Flood in Nerul, Navi Mumbai</title><link>https://example.test/a</link></item></channel></rss>","example.test")[0]
    assert value["published_at"]=="" and value["location_text"]=="Nerul"


def test_community_retries_do_not_duplicate_posts_or_replies(people):
    headers=people["citizen"]
    payload=dict(content="Please take care near this road",client_request_id="community-post-request-123")
    a=client.post("/api/v1/community/posts",headers=headers,json=payload)
    assert a.status_code==201,a.text
    assert client.post("/api/v1/community/posts",headers=headers,json=payload).json()["id"]==a.json()["id"]
    assert client.post("/api/v1/community/posts",headers=headers,json={**payload,"content":"Changed content"}).status_code==400
    path=f"/api/v1/community/posts/{a.json()['id']}/comments"
    reply=dict(content="Thanks for the update",client_request_id="community-reply-request-123")
    first=client.post(path,headers=headers,json=reply)
    assert first.status_code==201,first.text
    assert client.post(path,headers=headers,json=reply).json()["id"]==first.json()["id"]
    assert len(client.get(path,headers=headers).json())==1
    assert client.post(path,headers=headers,json={**reply,"content":"Changed reply"}).status_code==400
    other=client.post("/api/v1/community/posts",headers=people["government"],json=payload)
    assert other.json()["id"]!=a.json()["id"]


def test_verified_crime_news_has_map_labels_and_source(people):
    assert store_news_signal(dict(headline="Robbery and theft reported in Vashi",publisher="example.test",
        source_url="https://example.test/crime-test",summary="Official checking required",
        published_at="",location_text="Vashi",category="CRIME",confidence=.7))
    news=list_news_signals()[0]["id"]
    assert client.get("/api/v1/hazards/map").json()["hazards"]==[]
    payload=publication();payload["road_status"]="UNSAFE"
    response=client.post(f"/api/v1/government/news-signals/{news}/publish",headers=people["government"],json=payload)
    assert response.status_code==200,response.text
    hazard=client.get("/api/v1/hazards/map").json()["hazards"][0]
    assert "Theft / burglary" in hazard["crime_types"] and "Robbery / snatching" in hazard["crime_types"]
    assert hazard["news_source_url"]=="https://example.test/crime-test"
