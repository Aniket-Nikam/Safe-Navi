import pytest
from fastapi import HTTPException
from app import main
from app.main import Coordinate, RouteRiskRequest


def hazard(kind="POINT", geometry=None):
    return dict(id="test-closure", title="Closure", category_key="ROAD_DAMAGE", severity="HIGH",
                road_status="CLOSED", status="ACTIVE", latitude=19.0657, longitude=72.9986,
                geometry_type=kind, geometry_coordinates=geometry, no_go_radius_m=60)


def test_point_buffer_checks_complete_edge(monkeypatch):
    monkeypatch.setattr(main, "active_hazards", lambda: [hazard()])
    points=[Coordinate(latitude=19.0657,longitude=72.9886),Coordinate(latitude=19.0657,longitude=73.0086)]
    assert main.route_no_go_conflicts(points)
    assert not main.route_no_go_conflicts([Coordinate(latitude=19.0757,longitude=72.9886),Coordinate(latitude=19.0757,longitude=73.0086)])


@pytest.mark.parametrize("kind", ["LINESTRING", "POLYGON"])
def test_corridor_and_polygon_crossings(kind):
    geometry=[dict(latitude=19.05,longitude=73),dict(latitude=19.09,longitude=73)]
    if kind=="POLYGON": geometry=[dict(latitude=a,longitude=b) for a,b in [(19.05,72.98),(19.05,73.02),(19.09,73.02),(19.09,72.98)]]
    points=[Coordinate(latitude=19.07,longitude=72.96),Coordinate(latitude=19.07,longitude=73.04)]
    assert main.route_geometry_distance(points,hazard(kind,geometry))==0


def test_polygon_interior_and_collinear_boundary():
    geometry=[dict(latitude=a,longitude=b) for a,b in [(19.05,72.98),(19.05,73.02),(19.09,73.02),(19.09,72.98)]]
    assert main.route_geometry_distance([Coordinate(latitude=19.07,longitude=73),Coordinate(latitude=19.071,longitude=73.001)],hazard("POLYGON",geometry))==0
    assert main.segment_distance((19.07,72.99),(19.07,73.01),(19.07,73),(19.07,73.02))==0


@pytest.mark.parametrize("profile", ["fastest", "balanced", "safest"])
def test_api_excludes_crossing_for_every_profile(monkeypatch,profile):
    monkeypatch.setattr(main,"active_hazards",lambda:[hazard()])
    route=dict(route_id="blocked",travel_minutes=10,distance_meters=2000,coordinates=[dict(latitude=19.0657,longitude=72.9886),dict(latitude=19.0657,longitude=73.0086)])
    with pytest.raises(HTTPException) as error:
        main.route_risk(RouteRiskRequest(time_period="night",profile=profile,routes=[route]))
    assert error.value.status_code==409


def test_full_geometry_avoids_false_shortcut_intersection(monkeypatch):
    monkeypatch.setattr(main,"active_hazards",lambda:[hazard()])
    points=[Coordinate(latitude=19.0657,longitude=72.9886),Coordinate(latitude=19.0857,longitude=72.9886),Coordinate(latitude=19.0857,longitude=73.0086),Coordinate(latitude=19.0657,longitude=73.0086)]
    assert not main.route_no_go_conflicts(points)
