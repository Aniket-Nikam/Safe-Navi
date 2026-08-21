from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


def test_health_and_stats():
    assert client.get("/health").json()["status"] == "ok"
    stats = client.get("/api/v1/dataset/stats").json()
    assert stats["factor_count"] == 10
    assert stats["physical_road_segments"] == 452466


def test_point_risk_uses_dataset():
    response = client.get(
        "/api/v1/risk/point",
        params={"latitude": 19.0657, "longitude": 72.9986, "time_period": "evening_peak"},
    )
    assert response.status_code == 200
    result = response.json()
    assert result["synthetic"] is True
    assert len(result["factors"]) == 10
    assert 0 <= result["risk_score"] <= 100


def test_route_profiles_change_ranking_score():
    route = {
        "route_id": "test-route",
        "travel_minutes": 20,
        "distance_meters": 5000,
        "coordinates": [
            {"latitude": 19.0657, "longitude": 72.9986},
            {"latitude": 19.0185, "longitude": 73.0390},
        ],
    }
    fastest = client.post("/api/v1/risk/route", json={"time_period": "evening_peak", "profile": "fastest", "routes": [route]}).json()
    safest = client.post("/api/v1/risk/route", json={"time_period": "evening_peak", "profile": "safest", "routes": [route]}).json()
    assert fastest["results"][0]["ranking_score"] == 20
    assert safest["results"][0]["ranking_score"] > 20
