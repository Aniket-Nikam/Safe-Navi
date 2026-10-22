import pytest
from fastapi import HTTPException
from fastapi.testclient import TestClient
from app import main, product_database as products
from app.hazard_policy import restriction_reason, flood_label
from app.main import Coordinate, RouteRiskRequest


def flood(**changes):
    return dict(id="flood-test", title="Waterlogging", category_key="FLOODING", severity="LOW",
                status="ACTIVE", road_status="CAUTION", latitude=19.0657, longitude=72.9986,
                **changes)


@pytest.mark.parametrize("passability", [None, "UNKNOWN", "IMPASSABLE"])
@pytest.mark.parametrize("profile", ["fastest", "balanced", "safest"])
def test_unconfirmed_and_impassable_floods_are_not_tradeable(monkeypatch, passability, profile):
    hazard = flood(**({} if passability is None else {"passability": passability}))
    monkeypatch.setattr(main, "active_hazards", lambda: [hazard])
    crossing = dict(route_id="crossing", travel_minutes=10, distance_meters=2000,
                    coordinates=[dict(latitude=19.0657, longitude=72.9886), dict(latitude=19.0657, longitude=73.0086)])
    with pytest.raises(HTTPException) as error:
        main.route_risk(RouteRiskRequest(time_period="midday", profile=profile, routes=[crossing]))
    assert error.value.status_code == 409


def test_explicitly_passable_waterlogging_retains_a_penalty(monkeypatch):
    hazard = flood(passability="PASSABLE")
    monkeypatch.setattr(main, "active_hazards", lambda: [hazard])
    points = [Coordinate(latitude=19.0657, longitude=72.9886), Coordinate(latitude=19.0657, longitude=73.0086)]
    assert not main.route_no_go_conflicts(points)
    assert main.route_hazard_risk(points)[0] > 0
    assert flood_label(hazard) == "Waterlogging · passable with caution"


@pytest.mark.parametrize("road_status", ["CLOSED", "UNSAFE"])
def test_passability_cannot_override_a_closed_or_unsafe_flood(road_status):
    hazard = flood(passability="PASSABLE")
    hazard["road_status"] = road_status
    assert restriction_reason(hazard)
    assert flood_label(hazard) == "Flooding · road blocked"


def test_nearby_but_unaffected_road_is_not_blocked(monkeypatch):
    monkeypatch.setattr(main, "active_hazards", lambda: [flood(passability="UNKNOWN")])
    points = [Coordinate(latitude=19.0757, longitude=72.9886), Coordinate(latitude=19.0757, longitude=73.0086)]
    assert not main.route_no_go_conflicts(points)


@pytest.mark.parametrize("kind", ["LINESTRING", "POLYGON"])
def test_flood_geometry_intersections_are_blocked(monkeypatch, kind):
    shape = [(19.05, 73), (19.09, 73)] if kind == "LINESTRING" else [(19.05, 72.99), (19.05, 73.01), (19.09, 73.01), (19.09, 72.99)]
    hazard = flood(passability="UNKNOWN", geometry_type=kind,
                   geometry_coordinates=[dict(latitude=a, longitude=b) for a, b in shape])
    monkeypatch.setattr(main, "active_hazards", lambda: [hazard])
    assert main.route_no_go_conflicts([Coordinate(latitude=19.07, longitude=72.98), Coordinate(latitude=19.07, longitude=73.02)])


def test_official_passability_persists_is_public_and_audited():
    citizen = products.create_user("Citizen", "citizen@flood.test", "test-flood-password")
    official = products.create_user("Official", "official@flood.test", "test-flood-password", "government")
    admin = products.create_user("Admin", "admin@flood.test", "test-flood-password", "admin")
    tokens = {u["role"]: products.login(u["email"], "test-flood-password")[0] for u in (citizen, official, admin)}
    headers = lambda role: {"Authorization": "Bearer " + tokens[role]}
    with TestClient(main.app) as client:
        report = client.post("/api/v1/reports", headers=headers("citizen"), json=dict(
            title="Waterlogging needs review", category_key="FLOODING", description="Local fixture only",
            latitude=19.0657, longitude=72.9986, address="Test road approach", evidence_ids=[])).json()
        response = client.post(f"/api/v1/government/reports/{report['id']}/decision", headers=headers("government"),
                               json=dict(decision="verify", severity="LOW", road_status="CAUTION", reason="Fixture review"))
        assert response.status_code == 200
        hazard = response.json()["hazard"]
        assert hazard["passability"] == "UNKNOWN"
        record = client.get("/api/v1/hazards/map").json()["hazards"][0]
        assert record["routing_blocked"] and "unconfirmed" in record["passability_label"]
        path = f"/api/v1/government/hazards/{hazard['id']}"
        for role in ("citizen", "admin"):
            assert client.patch(path, headers=headers(role), json=dict(passability="PASSABLE", reason="Not allowed")).status_code == 403
        update = client.patch(path, headers=headers("government"), json=dict(passability="PASSABLE", reason="Official fixture inspection"))
        assert update.status_code == 200 and update.json()["passability"] == "PASSABLE"
        record = client.get("/api/v1/hazards/map").json()["hazards"][0]
        assert not record["routing_blocked"] and "passable with caution" in record["passability_label"]
        history = client.get(path+"/history", headers=headers("government")).json()
        assert any("UNKNOWN -> PASSABLE" in row["reason"] for row in history)
        client.patch(path, headers=headers("government"), json=dict(road_status="CLOSED", reason="Road is closed"))
        assert client.get("/api/v1/hazards/map").json()["hazards"][0]["routing_blocked"]
        assert client.patch(path, headers=headers("government"), json=dict(passability="maybe", reason="Invalid state")).status_code == 422
