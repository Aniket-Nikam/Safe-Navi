import pytest
from app import main


def test_all_preferences_share_one_evaluation_and_keep_physical_metrics(monkeypatch):
    monkeypatch.setattr(main, "active_hazards", lambda: [])
    monkeypatch.setattr(main, "lookup_point", lambda *args: None)
    monkeypatch.setattr(main, "predict_routes", lambda payload: (None, "test", "deterministic_baseline"))
    result = main.route_risk(main.RouteRiskRequest(time_period="midday", profile="balanced", routes=[
        dict(route_id="same", travel_minutes=20, distance_meters=2000,
             coordinates=[dict(latitude=19, longitude=73), dict(latitude=19.01, longitude=73.01)])]))
    assert result["distinct_recommended_routes"] == 1
    assert set(result["recommendations"]) == {"fastest", "balanced", "safest"}
    for option in result["recommendations"].values():
        assert option["route_id"] == "same"
        assert option["travel_minutes"] == 20
        assert option["distance_meters"] == 2000


def test_three_preferences_choose_real_tradeoffs(monkeypatch):
    monkeypatch.setattr(main, "active_hazards", lambda: [])
    monkeypatch.setattr(main, "lookup_point", lambda *args: None)
    risks = {"a": 86.54, "b": 53.82, "c": 49.29}
    monkeypatch.setattr(main, "predict_routes", lambda payload: ({key: dict(risk_exposure=risk,
        confidence=.8, model_version="test", factor_contributions={}) for key, risk in risks.items()}, None, "trained_ml"))
    routes = [dict(route_id=key, travel_minutes=time, distance_meters=2000,
        coordinates=[dict(latitude=19, longitude=73), dict(latitude=19.01, longitude=73.01)])
        for key, time in [("a", 16.6), ("b", 20.2), ("c", 22)]]
    result = main.route_risk(main.RouteRiskRequest(time_period="morning_peak", profile="balanced", routes=routes))
    assert [result["recommendations"][key]["route_id"] for key in ("fastest", "balanced", "safest")] == ["a", "b", "c"]
    assert result["distinct_recommended_routes"] == 3


def test_crime_types_only_from_nearby_verified_active_records(monkeypatch):
    base = dict(id="crime", title="Phone theft and harassment", description="", reason="Reviewed",
                category_key="CRIME", severity="HIGH", road_status="UNSAFE", status="ACTIVE", latitude=19, longitude=73)
    monkeypatch.setattr(main, "active_hazards", lambda: [base, {**base, "id": "far", "latitude": 20},
        {**base, "id": "resolved", "status": "RESOLVED"}, {**base, "id": "road", "category_key": "ROAD_DAMAGE"}])
    result = main.area_crime(19, 73, 3000)
    assert result["verified_record_count"] == 1
    assert {item["type"] for item in result["types"]} == {"Theft / burglary", "Harassment"}
    assert result["records"][0]["hazard_id"] == "crime"
    assert "not crime prevalence" in result["notice"]


def test_empty_crime_information_is_unknown_not_safe(monkeypatch):
    monkeypatch.setattr(main, "active_hazards", lambda: [])
    value = main.area_crime(19, 73, 3000)
    assert value["types"] == []
    assert "does not mean no crime" in value["notice"]


