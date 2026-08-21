from __future__ import annotations

from collections import defaultdict
from typing import Literal

from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

from .database import FACTORS, db_path, lookup_point, metadata

PERIODS = {"morning_peak", "midday", "evening_peak", "night"}
WEIGHTS = {"fastest": 0.0, "balanced": 0.35, "safest": 1.0}

app = FastAPI(
    title="Safe-Navi Synthetic Risk API",
    version="1.0.0",
    description="SQLite-backed classroom API derived from the documented Mumbai–Navi Mumbai synthetic dataset.",
)
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])


class Coordinate(BaseModel):
    latitude: float = Field(ge=-90, le=90)
    longitude: float = Field(ge=-180, le=180)


class RouteInput(BaseModel):
    route_id: str
    travel_minutes: float = Field(gt=0)
    distance_meters: float = Field(gt=0)
    coordinates: list[Coordinate] = Field(min_length=2)


class RouteRiskRequest(BaseModel):
    time_period: Literal["morning_peak", "midday", "evening_peak", "night"]
    profile: Literal["fastest", "balanced", "safest"]
    routes: list[RouteInput] = Field(min_length=1, max_length=5)


@app.get("/health")
def health() -> dict:
    return {"status": "ok", "database": db_path().name, "synthetic": True}


@app.get("/api/v1/dataset/stats")
def dataset_stats() -> dict:
    return metadata()


@app.get("/api/v1/risk/point")
def point_risk(
    latitude: float = Query(ge=-90, le=90),
    longitude: float = Query(ge=-180, le=180),
    time_period: str = Query(default="evening_peak"),
) -> dict:
    if time_period not in PERIODS:
        raise HTTPException(422, "Invalid time period")
    cell = lookup_point(latitude, longitude, time_period)
    if cell is None:
        raise HTTPException(404, "Point is outside the documented dataset coverage")
    return cell


def sampled(coordinates: list[Coordinate], maximum: int = 80) -> list[Coordinate]:
    if len(coordinates) <= maximum:
        return coordinates
    indexes = {round(i * (len(coordinates) - 1) / (maximum - 1)) for i in range(maximum)}
    return [coordinates[index] for index in sorted(indexes)]


@app.post("/api/v1/risk/route")
def route_risk(request: RouteRiskRequest) -> dict:
    results = []
    for route in request.routes:
        points = sampled(route.coordinates)
        cells = [lookup_point(point.latitude, point.longitude, request.time_period) for point in points]
        matched = [cell for cell in cells if cell is not None]
        if not matched:
            exposure = 0.0
            factors = {factor: 0.0 for factor in FACTORS}
            areas = []
        else:
            exposure = sum(cell["risk_score"] for cell in matched) / len(matched)
            factors = {
                factor: round(sum(cell["factors"][factor] for cell in matched) / len(matched), 2)
                for factor in FACTORS
            }
            area_counts: dict[str, int] = defaultdict(int)
            for cell in matched:
                area_counts[cell["area_name"]] += 1
            areas = [name for name, _ in sorted(area_counts.items(), key=lambda item: item[1], reverse=True)[:3]]
        score = route.travel_minutes + WEIGHTS[request.profile] * exposure
        results.append(
            {
                "route_id": route.route_id,
                "travel_minutes": route.travel_minutes,
                "distance_meters": route.distance_meters,
                "risk_exposure": round(exposure, 2),
                "ranking_score": round(score, 2),
                "coverage_ratio": round(len(matched) / len(points), 3),
                "factor_averages": factors,
                "areas": areas,
            }
        )
    results.sort(key=lambda result: result["ranking_score"])
    return {
        "profile": request.profile,
        "time_period": request.time_period,
        "synthetic": True,
        "runtime_resolution": "1 km area cells derived from the complete road-segment dataset",
        "results": results,
    }
