from fastapi.testclient import TestClient
import pytest

from app.main import app
from app.product_database import create_user
from app.news_intelligence import classify, parse_feed
from app.model_provider import validate_predictions
from app.local_model import predict as predict_local_risk


client = TestClient(app)


@pytest.fixture(autouse=True)
def isolated_product_database(tmp_path, monkeypatch):
    monkeypatch.setenv("SAFE_NAVI_PRODUCT_DB", str(tmp_path / "product.sqlite"))


def auth(token: str) -> dict[str, str]:
    return {"Authorization": f"Bearer {token}"}


def test_report_review_map_and_route_risk_workflow():
    citizen_response = client.post(
        "/api/v1/auth/register",
        json={"name": "Asha Citizen", "email": "asha@example.test", "password": "very-safe-password"},
    )
    assert citizen_response.status_code == 201
    citizen_token = citizen_response.json()["access_token"]

    create_user("Gov Reviewer", "reviewer@example.test", "government-password", "government")
    government_response = client.post(
        "/api/v1/auth/login",
        json={"email": "reviewer@example.test", "password": "government-password"},
    )
    assert government_response.status_code == 200
    government_token = government_response.json()["access_token"]

    report_response = client.post(
        "/api/v1/reports",
        headers=auth(citizen_token),
        json={
            "title": "Large open drain beside road",
            "category_key": "CIVIC_ROAD_HAZARD",
            "description": "The drain cover is missing and traffic is moving close to the opening.",
            "latitude": 19.0657,
            "longitude": 72.9986,
            "address": "Test road, Navi Mumbai",
        },
    )
    assert report_response.status_code == 201
    report = report_response.json()
    assert report["status"] == "REPORTED"

    public_before_review = client.get("/api/v1/hazards/map").json()
    assert public_before_review["count"] == 0

    triage_response = client.post(
        f"/api/v1/government/reports/{report['id']}/triage",
        headers=auth(government_token),
        json={"reason": "Assigned for field verification"},
    )
    assert triage_response.status_code == 200
    assert triage_response.json()["status"] == "UNDER_REVIEW"

    with client.websocket_connect("/api/v1/live") as socket:
        decision_response = client.post(
            f"/api/v1/government/reports/{report['id']}/decision",
            headers=auth(government_token),
            json={
                "decision": "verify",
                "severity": "HIGH",
                "road_status": "UNSAFE",
                "hazard_status": "ACTIVE",
                "reason": "Field team confirmed an uncovered drain beside the carriageway",
            },
        )
        assert socket.receive_json() == {"event": "hazards_changed"}
    assert decision_response.status_code == 200
    hazard = decision_response.json()["hazard"]

    public_after_review = client.get("/api/v1/hazards/map").json()
    assert public_after_review["count"] == 1
    assert public_after_review["hazards"][0]["road_status"] == "UNSAFE"

    point = client.get(
        "/api/v1/risk/point",
        params={"latitude": 19.0657, "longitude": 72.9986, "time_period": "evening_peak"},
    ).json()
    assert point["risk_provider"] == "trained_ml"
    assert point["model_version"] == "safe-route-linear-2026.10"
    assert 0 < point["model_confidence"] <= 1
    assert point["verified_hazard_risk"] > 0
    assert point["risk_score"] >= point["dataset_risk_score"]
    assert point["verified_hazards"][0]["id"] == hazard["id"]

    route = client.post(
        "/api/v1/risk/route",
        json={
            "time_period": "evening_peak",
            "profile": "safest",
            "routes": [{
                "route_id": "affected-route",
                "travel_minutes": 20,
                "distance_meters": 3000,
                "coordinates": [
                    {"latitude": 19.0657, "longitude": 72.9986},
                    {"latitude": 19.0700, "longitude": 73.0030},
                ],
            }],
        },
    )
    assert route.status_code == 200
    route_result = route.json()["results"][0]
    assert route.json()["risk_provider"] == "trained_ml"
    assert route_result["model_version"] == "safe-route-linear-2026.10"
    assert route_result["ml_risk_exposure"] == route_result["dataset_risk_exposure"]
    assert route_result["verified_hazard_exposure"] > 0
    assert route_result["affecting_hazards"][0]["id"] == hazard["id"]

    history = client.get(
        f"/api/v1/government/hazards/{hazard['id']}/history", headers=auth(government_token)
    )
    assert history.status_code == 200
    assert history.json()[-1]["action"] == "REPORT_VERIFIED"

    geometry = client.patch(
        f"/api/v1/government/hazards/{hazard['id']}", headers=auth(government_token),
        json={"status": "ACTIVE", "reason": "Mapped the affected road corridor",
              "geometry_type": "LINESTRING", "geometry_coordinates": [
                  {"latitude": 19.0657, "longitude": 72.9986},
                  {"latitude": 19.0750, "longitude": 73.0080},
              ]},
    )
    assert geometry.status_code == 200
    assert geometry.json()["geometry_type"] == "LINESTRING"
    mapped = client.get("/api/v1/hazards/map").json()["hazards"][0]
    assert len(mapped["geometry_coordinates"]) == 2

    closed = client.patch(
        f"/api/v1/government/hazards/{hazard['id']}", headers=auth(government_token),
        json={"status": "ACTIVE", "road_status": "CLOSED", "reason": "Road closed by field authority",
              "no_go_radius_m": 80},
    )
    assert closed.status_code == 200
    alternatives = client.post("/api/v1/risk/route", json={
        "time_period": "evening_peak", "profile": "fastest", "routes": [
            {"route_id": "closed-route", "travel_minutes": 10, "distance_meters": 1000,
             "coordinates": [{"latitude": 19.0657, "longitude": 72.9986}, {"latitude": 19.066, "longitude": 72.999}]},
            {"route_id": "open-route", "travel_minutes": 14, "distance_meters": 1500,
             "coordinates": [{"latitude": 19.30, "longitude": 73.20}, {"latitude": 19.31, "longitude": 73.21}]},
        ]})
    assert alternatives.status_code == 200
    assert [value["route_id"] for value in alternatives.json()["results"]] == ["open-route"]
    assert alternatives.json()["excluded_routes"][0]["route_id"] == "closed-route"
    all_closed = client.post("/api/v1/risk/route", json={
        "time_period": "evening_peak", "profile": "fastest", "routes": [{
            "route_id": "closed-route", "travel_minutes": 10, "distance_meters": 1000,
            "coordinates": [{"latitude": 19.0657, "longitude": 72.9986}, {"latitude": 19.066, "longitude": 72.999}],
        }]})
    assert all_closed.status_code == 409
    assert all_closed.json()["detail"]["code"] == "NO_SAFE_ALTERNATIVE"

    resolved = client.patch(
        f"/api/v1/government/hazards/{hazard['id']}",
        headers=auth(government_token),
        json={"status": "RESOLVED", "road_status": "SAFE", "reason": "Drain covered after inspection"},
    )
    assert resolved.status_code == 200
    assert client.get("/api/v1/hazards/map").json()["count"] == 0


def test_citizen_cannot_make_government_decision():
    response = client.post(
        "/api/v1/auth/register",
        json={"name": "Citizen User", "email": "citizen@example.test", "password": "another-safe-password"},
    )
    token = response.json()["access_token"]
    forbidden = client.post(
        "/api/v1/government/reports/does-not-exist/triage",
        headers=auth(token),
        json={"reason": "Should not be allowed"},
    )
    assert forbidden.status_code == 403


def test_report_client_request_id_is_idempotent_for_offline_retry():
    account = client.post(
        "/api/v1/auth/register",
        json={"name": "Offline Reporter", "email": "offline@example.test", "password": "offline-safe-password"},
    ).json()
    payload = {"title": "Road edge has collapsed", "category_key": "ROAD_DAMAGE",
               "description": "A section of the road edge has collapsed after heavy rain.",
               "latitude": 19.07, "longitude": 72.99, "address": "Sector 9, Navi Mumbai",
               "client_request_id": "offline-request-0001"}
    first = client.post("/api/v1/reports", headers=auth(account["access_token"]), json=payload)
    second = client.post("/api/v1/reports", headers=auth(account["access_token"]), json=payload)
    assert first.status_code == second.status_code == 201
    assert first.json()["id"] == second.json()["id"]
    assert len(client.get("/api/v1/reports", headers=auth(account["access_token"])).json()) == 1


def test_journey_checkin_schedule_acknowledgement_and_public_status():
    account = client.post(
        "/api/v1/auth/register",
        json={"name": "Journey User", "email": "journey@example.test", "password": "journey-safe-password"},
    ).json()
    created = client.post("/api/v1/journeys", headers=auth(account["access_token"]), json={
        "source_label": "Vashi", "destination_label": "Nerul", "travel_mode": "WALKING",
        "route_profile": "SAFEST", "route_summary": "Safer route via lit main roads"
    })
    assert created.status_code == 201
    journey = created.json()
    scheduled = client.post(f"/api/v1/journeys/{journey['id']}/check-in/schedule",
                            headers=auth(account["access_token"]), json={"interval_minutes": 15})
    assert scheduled.status_code == 200
    assert scheduled.json()["checkin_status"] == "SCHEDULED"
    assert scheduled.json()["next_checkin_at"]
    acknowledged = client.post(f"/api/v1/journeys/{journey['id']}/check-in",
                               headers=auth(account["access_token"]), json={})
    assert acknowledged.status_code == 200
    assert acknowledged.json()["checkin_status"] == "SAFE"
    assert acknowledged.json()["last_checkin_at"]
    public = client.get(f"/api/v1/journeys/share/{journey['share_token']}")
    assert public.status_code == 200
    assert public.json()["checkin_status"] == "SAFE"


def test_persistent_community_post_vote_and_reply():
    first = client.post(
        "/api/v1/auth/register",
        json={"name": "Asha", "email": "asha-community@example.test", "password": "community-password"},
    ).json()
    second = client.post(
        "/api/v1/auth/register",
        json={"name": "Vikram", "email": "vikram-community@example.test", "password": "community-password"},
    ).json()
    created = client.post(
        "/api/v1/community/posts",
        headers=auth(first["access_token"]),
        json={"category": "question", "content": "Is the station underpass accessible after the rain?"},
    )
    assert created.status_code == 201
    post_id = created.json()["id"]
    voted = client.post(f"/api/v1/community/posts/{post_id}/vote", headers=auth(second["access_token"]))
    assert voted.status_code == 200
    assert voted.json()["upvotes"] == 1
    reply = client.post(
        f"/api/v1/community/posts/{post_id}/comments",
        headers=auth(second["access_token"]),
        json={"content": "The community update is useful, but wait for an official alert."},
    )
    assert reply.status_code == 201
    feed = client.get("/api/v1/community/posts", headers=auth(first["access_token"])).json()
    assert feed[0]["comments_count"] == 1
    assert client.get(
        f"/api/v1/community/posts/{post_id}/comments", headers=auth(first["access_token"])
    ).json()[0]["author_name"] == "Vikram"


def test_assistant_requires_server_side_groq_key(monkeypatch):
    monkeypatch.delenv("GROQ_API_KEY", raising=False)
    account = client.post(
        "/api/v1/auth/register",
        json={"name": "Navi User", "email": "navi@example.test", "password": "assistant-password"},
    ).json()
    response = client.post(
        "/api/v1/assistant/chat",
        headers=auth(account["access_token"]),
        json={"message": "How do I report a broken street light?", "history": []},
    )
    assert response.status_code == 503
    assert "GROQ_API_KEY" in response.json()["detail"]


def test_evidence_assignment_notifications_and_immutable_timeline():
    citizen = client.post(
        "/api/v1/auth/register",
        json={"name": "Private Reporter", "email": "private@example.test", "password": "secure-private-password"},
    ).json()
    reviewer = create_user("Ward Officer", "ward@example.test", "government-password", "government")
    government = client.post(
        "/api/v1/auth/login", json={"email": "ward@example.test", "password": "government-password"}
    ).json()

    upload = client.post(
        "/api/v1/evidence", headers=auth(citizen["access_token"]),
        files={"file": ("street.jpg", b"safe-navi-test-image", "image/jpeg")},
    )
    assert upload.status_code == 201
    evidence_id = upload.json()["id"]
    created = client.post(
        "/api/v1/reports", headers=auth(citizen["access_token"]),
        json={
            "title": "Street light is not working", "category_key": "POOR_LIGHTING",
            "description": "The entire corner is dark after sunset and pedestrians cannot see traffic.",
            "latitude": 19.076, "longitude": 72.9777, "address": "Sector 10, Navi Mumbai",
            "anonymous": True, "evidence_ids": [evidence_id],
        },
    )
    assert created.status_code == 201
    report_id = created.json()["id"]

    assignment = client.post(
        f"/api/v1/government/reports/{report_id}/assign", headers=auth(government["access_token"]),
        json={"department": "Street Lighting", "assigned_to": "Night inspection team", "due_hours": 12},
    )
    assert assignment.status_code == 200
    request = client.post(
        f"/api/v1/government/reports/{report_id}/request-information", headers=auth(government["access_token"]),
        json={"message": "Please confirm whether both lamps at the corner are affected."},
    )
    assert request.status_code == 200

    citizen_detail = client.get(f"/api/v1/reports/{report_id}", headers=auth(citizen["access_token"])).json()
    assert citizen_detail["assigned_department"] == "Street Lighting"
    assert citizen_detail["evidence"][0]["id"] == evidence_id
    assert any(item["action"] == "INFORMATION_REQUESTED" for item in citizen_detail["timeline"])
    government_detail = client.get(f"/api/v1/reports/{report_id}", headers=auth(government["access_token"])).json()
    assert government_detail["citizen_name"] == "Anonymous citizen"
    assert client.get(f"/api/v1/evidence/{evidence_id}", headers=auth(government["access_token"])).status_code == 200

    notices = client.get("/api/v1/notifications", headers=auth(citizen["access_token"])).json()
    assert any(item["kind"] == "INFORMATION_REQUESTED" for item in notices)
    marked = client.post(f"/api/v1/notifications/{notices[0]['id']}/read", headers=auth(citizen["access_token"]))
    assert marked.status_code == 200
    metrics = client.get("/api/v1/government/metrics", headers=auth(government["access_token"])).json()
    assert metrics["reports_by_status"]["REQUESTED_INFO"] == 1


def test_evidence_rejects_unsupported_files():
    account = client.post(
        "/api/v1/auth/register",
        json={"name": "Evidence Tester", "email": "evidence@example.test", "password": "evidence-password"},
    ).json()
    response = client.post(
        "/api/v1/evidence", headers=auth(account["access_token"]),
        files={"file": ("unsafe.exe", b"not-media", "application/octet-stream")},
    )
    assert response.status_code == 415


def test_news_rss_is_deduplicated_classified_and_review_only():
    category, confidence = classify("Waterlogging closes road in Vashi", "Heavy rain affects traffic")
    assert category == "FLOODING"
    assert confidence > 0.5
    parsed = parse_feed(b"""<rss><channel><item><title>Waterlogging closes road in Vashi</title>
        <link>https://publisher.example/vashi-rain</link><description>Heavy rain affects traffic</description>
        <pubDate>2026-09-30T08:00:00Z</pubDate></item></channel></rss>""", "publisher.example")
    assert parsed[0]["location_text"] == "Vashi"
    from app.product_database import store_news_signal
    assert store_news_signal(parsed[0]) is True
    assert store_news_signal(parsed[0]) is False

    create_user("News Reviewer", "news-reviewer@example.test", "government-password", "government")
    token = client.post("/api/v1/auth/login", json={"email": "news-reviewer@example.test", "password": "government-password"}).json()["access_token"]
    queue = client.get("/api/v1/government/news-signals", headers=auth(token)).json()
    assert queue[0]["review_status"] == "UNVERIFIED"
    assert client.get("/api/v1/hazards/map").json()["count"] == 0
    reviewed = client.post(f"/api/v1/government/news-signals/{queue[0]['id']}/review", headers=auth(token),
                           json={"review_status": "NEEDS_FIELD_CHECK"})
    assert reviewed.status_code == 200
    assert client.get("/api/v1/hazards/map").json()["count"] == 0


def test_saved_places_emergency_contacts_and_consent_based_journey_sharing():
    account = client.post("/api/v1/auth/register", json={
        "name": "Journey User", "email": "journey@example.test", "password": "journey-password"
    }).json()
    headers = auth(account["access_token"])
    place = client.post("/api/v1/saved-places", headers=headers, json={
        "label": "College", "address": "Nerul, Navi Mumbai", "latitude": 19.033, "longitude": 73.0297
    })
    assert place.status_code == 201
    assert client.get("/api/v1/saved-places", headers=headers).json()[0]["label"] == "College"
    contact = client.post("/api/v1/emergency-contacts", headers=headers, json={
        "name": "Family", "phone": "+91 90000 00000", "relationship": "Parent"
    })
    assert contact.status_code == 201
    assert len(client.get("/api/v1/emergency-contacts", headers=headers).json()) == 1
    journey = client.post("/api/v1/journeys", headers=headers, json={
        "source_label": "Vashi", "destination_label": "College", "destination_latitude": 19.033,
        "destination_longitude": 73.0297, "travel_mode": "DRIVING", "route_profile": "SAFEST",
        "route_summary": "24 min · avoids one verified hazard", "latitude": 19.076, "longitude": 72.9777,
    })
    assert journey.status_code == 201
    value = journey.json()
    updated = client.patch(f"/api/v1/journeys/{value['id']}/location", headers=headers,
                           json={"latitude": 19.060, "longitude": 73.000})
    assert updated.status_code == 200
    shared = client.get(f"/api/v1/journeys/share/{value['share_token']}")
    assert shared.status_code == 200
    assert shared.json()["last_latitude"] == 19.060
    assert "user_id" not in shared.json()
    assert client.post(f"/api/v1/journeys/{value['id']}/end", headers=headers).json()["status"] == "COMPLETED"


def test_ml_provider_contract_rejects_partial_or_unbounded_predictions():
    valid = validate_predictions({"predictions": [{
        "route_id": "r1", "risk_exposure": 42.5, "confidence": 0.81,
        "factor_contributions": {"lighting": 0.3, "flood": 0.2}, "model_version": "team-model-1"
    }]}, {"r1"})
    assert valid["r1"]["risk_exposure"] == 42.5
    with pytest.raises(ValueError):
        validate_predictions({"predictions": [{
            "route_id": "r1", "risk_exposure": 140, "confidence": 0.8,
            "factor_contributions": {}, "model_version": "bad"
        }]}, {"r1"})
    provider = client.get("/api/v1/risk/provider").json()
    assert provider["contract_version"] == "safenavi-risk-v1"
    assert provider["active_provider"] == "trained_ml"
    assert provider["deployment"] == "bundled_safe_json"
    assert provider["bundled_model"]["metrics"]["grouped_cross_validation"]["r2_mean"] == 0.9325


def test_trained_model_responds_to_risk_and_protective_factors():
    neutral = {name: 50.0 for name in (
        "traffic_congestion_index", "crime_risk_index", "lighting_quality_index",
        "population_density_index", "road_condition_index", "pedestrian_activity_index",
        "emergency_access_index", "flood_risk_index", "isolation_index",
        "public_transport_access_index",
    )}
    safer = dict(neutral, crime_risk_index=15, lighting_quality_index=90,
                 flood_risk_index=10, isolation_index=10, emergency_access_index=90)
    dangerous = dict(neutral, crime_risk_index=90, lighting_quality_index=10,
                     flood_risk_index=90, isolation_index=90, emergency_access_index=10)
    safe_score = predict_local_risk(safer, "night", "Navi Mumbai", 19.07, 73.00, 30)["risk_exposure"]
    dangerous_score = predict_local_risk(dangerous, "night", "Navi Mumbai", 19.07, 73.00, 30)["risk_exposure"]
    assert dangerous_score > safe_score + 30


def test_community_photos_and_report_conversation_workflow():
    citizen = client.post("/api/v1/auth/register", json={
        "name": "Photo Reporter", "email": "photo@example.test", "password": "photo-safe-password"
    }).json()
    neighbour = client.post("/api/v1/auth/register", json={
        "name": "Helpful Neighbour", "email": "neighbour@example.test", "password": "neighbour-safe-password"
    }).json()
    create_user("Field Reviewer", "field@example.test", "field-safe-password", "government")
    government = client.post("/api/v1/auth/login", json={
        "email": "field@example.test", "password": "field-safe-password"
    }).json()
    citizen_headers, neighbour_headers, government_headers = (
        auth(citizen["access_token"]), auth(neighbour["access_token"]), auth(government["access_token"])
    )

    uploaded = client.post("/api/v1/evidence", headers=citizen_headers,
                           files={"file": ("blocked-road.png", b"\x89PNG\r\ncommunity-photo", "image/png")})
    assert uploaded.status_code == 201
    evidence_id = uploaded.json()["id"]
    post = client.post("/api/v1/community/posts", headers=citizen_headers, json={
        "category": "update", "content": "The service road is blocked near the station.",
        "evidence_id": evidence_id,
    })
    assert post.status_code == 201
    assert post.json()["evidence_id"] == evidence_id
    assert client.get(f"/api/v1/evidence/{evidence_id}", headers=neighbour_headers).status_code == 200

    report = client.post("/api/v1/reports", headers=citizen_headers, json={
        "title": "Unsafe road obstruction", "category_key": "ROAD_DAMAGE",
        "description": "A concrete obstruction blocks most of the service road.",
        "latitude": 19.076, "longitude": 72.9986, "address": "Vashi service road",
    })
    assert report.status_code == 201
    report_id = report.json()["id"]
    requested = client.post(f"/api/v1/government/reports/{report_id}/request-information",
                            headers=government_headers, json={"message": "Please add a closer photograph."})
    assert requested.status_code == 200
    followup = client.post("/api/v1/evidence", headers=citizen_headers,
                           files={"file": ("close-view.jpg", b"\xff\xd8\xfffollow-up", "image/jpeg")}).json()
    reply = client.post(f"/api/v1/reports/{report_id}/messages", headers=citizen_headers, json={
        "content": "Added a close photograph taken from the pavement.", "evidence_id": followup["id"],
    })
    assert reply.status_code == 201
    assert client.get(f"/api/v1/reports/{report_id}", headers=citizen_headers).json()["status"] == "UNDER_REVIEW"
    thread = client.get(f"/api/v1/reports/{report_id}/messages", headers=government_headers)
    assert thread.status_code == 200
    assert thread.json()[0]["content"] == "Please add a closer photograph."
    assert thread.json()[1]["evidence_id"] == followup["id"]
    assert client.get(f"/api/v1/evidence/{followup['id']}", headers=government_headers).status_code == 200
    official_reply = client.post(f"/api/v1/reports/{report_id}/messages", headers=government_headers,
                                 json={"content": "Thank you. A field team has been assigned."})
    assert official_reply.status_code == 201
    citizen_thread = client.get(f"/api/v1/reports/{report_id}/messages", headers=citizen_headers).json()
    assert [item["author_role"] for item in citizen_thread] == ["government", "citizen", "government"]


def test_admin_controls_persistent_live_news_schedule():
    create_user("News Administrator", "news-admin@example.test", "administrator-password", "admin")
    create_user("News Reviewer", "news-reviewer@example.test", "reviewer-password", "government")
    admin = client.post("/api/v1/auth/login", json={
        "email": "news-admin@example.test", "password": "administrator-password"
    }).json()
    reviewer = client.post("/api/v1/auth/login", json={
        "email": "news-reviewer@example.test", "password": "reviewer-password"
    }).json()
    admin_headers, reviewer_headers = auth(admin["access_token"]), auth(reviewer["access_token"])

    initial = client.get("/api/v1/government/news/settings", headers=admin_headers)
    assert initial.status_code == 200
    assert initial.json()["refresh_hours"] == 12
    assert initial.json()["last_ingested_at"] is None

    updated = client.patch("/api/v1/government/news/settings", headers=admin_headers,
                           json={"refresh_hours": 24})
    assert updated.status_code == 200
    assert updated.json()["refresh_hours"] == 24
    assert client.get("/api/v1/government/news/settings", headers=admin_headers).json()["refresh_hours"] == 24
    assert client.patch("/api/v1/government/news/settings", headers=admin_headers,
                        json={"refresh_hours": 6}).status_code == 422
    assert client.get("/api/v1/government/news/settings", headers=reviewer_headers).status_code == 403
    assert client.post("/api/v1/government/news/ingest", headers=reviewer_headers).status_code == 403
