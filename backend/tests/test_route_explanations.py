from app import main
from app.route_explanations import explain, markers, partition, distance


def points(values):
    return [main.Coordinate(latitude=lat, longitude=lon) for lat, lon in values]


def test_full_route_preserves_bends_endpoints_and_distance():
    road=points([(19,73),(19.01,73),(19.01,73.01),(19.02,73.01)])
    chunks=partition(road)
    assert chunks[0][0][0]==(19,73)
    assert chunks[-1][0][-1]==(19.02,73.01)
    assert all(a[0][-1]==b[0][0] for a,b in zip(chunks,chunks[1:]))
    assert (19.01,73) in [p for shape,_ in chunks for p in shape]
    assert abs(sum(length for _,length in chunks)-sum(distance((a.latitude,a.longitude),(b.latitude,b.longitude)) for a,b in zip(road,road[1:])))<1


def test_unknown_sections_are_explicit_and_elevated_markers_span_journey():
    def lookup(lat,lon,period):
        if lat<19.03: return None
        return dict(area_cell_id=round(lat,2),area_name="Test",jurisdiction="Test",risk_score=80,factors={})
    sections=explain(points([(19,73),(19.1,73)]),"midday",lookup,lambda **kwargs:(None,None,"baseline"))
    assert sections[0]["band"]=="unknown" and sections[0]["risk_exposure"] is None
    assert sections[-1]["band"]=="high"
    values=markers(sections,[])
    assert 1<len(values)<=8
    assert max(p["latitude"] for p in values)-min(p["latitude"] for p in values)>.04
    assert all(not p["verified"] for p in values)


def test_display_work_is_bounded_and_unknown_is_not_fabricated():
    sections=explain(points([(19,73),(21,73)]),"midday",lambda *args:None,lambda **kwargs:(_ for _ in ()).throw(AssertionError("No inference outside coverage")))
    assert len(sections)<=128
    assert all(s["source"]=="unknown" for s in sections)
    assert markers(sections,[])==[]


def test_route_api_exposes_full_display_coverage(monkeypatch):
    monkeypatch.setattr(main,"active_hazards",lambda:[])
    monkeypatch.setattr(main,"lookup_point",lambda *args:None)
    monkeypatch.setattr(main,"predict_routes",lambda payload:(None,"test","baseline"))
    value=main.route_risk(main.RouteRiskRequest(time_period="midday",profile="balanced",routes=[dict(route_id="road",travel_minutes=20,distance_meters=11000,coordinates=[dict(latitude=19,longitude=73),dict(latitude=19.1,longitude=73)])]))
    road=value["results"][0]
    assert len(road["risk_sections"])>3
    assert road["risk_exposure"]==0 and road["coverage_ratio"]==0
    assert road["risk_sections"][-1]["coordinates"][-1]==dict(latitude=19.1,longitude=73)


def test_official_markers_are_preserved_separately_from_estimates():
    official=dict(id="verified",verified=True,reason="Official inspection")
    assert markers([], [official])==[official]
