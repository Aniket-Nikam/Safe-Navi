from __future__ import annotations

import asyncio
from collections import defaultdict
from contextlib import asynccontextmanager, suppress
import logging
import math
import os
from typing import Literal

from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

from .database import FACTORS, db_path, lookup_point, metadata
from .product_api import router as product_router
from .product_database import active_hazards, news_configuration, news_ingestion_due
from .model_provider import predict_point, predict_routes, provider_metadata
from .news_intelligence import configured_feeds, ingest_configured_feeds
from .security import RateLimitMiddleware

PERIODS = {"morning_peak", "midday", "evening_peak", "night"}
WEIGHTS = {"fastest": 0.0, "balanced": 0.35, "safest": 1.0}

logger = logging.getLogger("safenavi.news")


async def scheduled_news_ingestion(stop: asyncio.Event) -> None:
    while not stop.is_set():
        if await asyncio.to_thread(news_ingestion_due):
            result = await asyncio.to_thread(ingest_configured_feeds)
            logger.info("Scheduled news ingestion completed: %s", result)
        try:
            await asyncio.wait_for(stop.wait(), timeout=60)
        except TimeoutError:
            continue


@asynccontextmanager
async def lifespan(_: FastAPI):
    stop = asyncio.Event()
    task: asyncio.Task | None = None
    if configured_feeds():
        task = asyncio.create_task(scheduled_news_ingestion(stop))
    try:
        yield
    finally:
        stop.set()
        if task:
            task.cancel()
            with suppress(asyncio.CancelledError):
                await task


app = FastAPI(
    title="Safe-Navi Safety Intelligence API",
    version="1.1.0",
    description="Authenticated reporting, verified-hazard routing, community, notifications, and review-only intelligence.",
    lifespan=lifespan,
)
allowed_origins = [origin.strip() for origin in os.getenv(
    "SAFE_NAVI_CORS_ORIGINS", "http://localhost:3000,http://127.0.0.1:3000"
).split(",") if origin.strip()]
app.add_middleware(CORSMiddleware, allow_origins=allowed_origins, allow_methods=["GET", "POST", "PATCH", "DELETE"],
                   allow_headers=["Authorization", "Content-Type"])
app.add_middleware(RateLimitMiddleware)
app.include_router(product_router)

SEVERITY_RISK = {"LOW": 18.0, "MODERATE": 35.0, "HIGH": 60.0, "CRITICAL": 85.0}
ROAD_RISK = {"SAFE": -10.0, "CAUTION": 4.0, "UNSAFE": 15.0, "WORK_REQUIRED": 9.0, "CLOSED": 25.0}


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
    provider = provider_metadata()
    news = news_configuration()
    return {"status": "ok", "database": db_path().name, "risk_provider": provider["active_provider"],
            "news_scheduler": "active" if configured_feeds() else "not_configured",
            "news_refresh_hours": news["refresh_hours"], "news_last_ingested_at": news["last_ingested_at"],
            "news_next_ingestion_at": news["next_ingestion_at"]}


@app.get("/api/v1/dataset/stats")
def dataset_stats() -> dict:
    return metadata()


@app.get("/api/v1/risk/provider")
def risk_provider() -> dict:
    return provider_metadata()


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
    live_risk, nearby = verified_hazard_risk(latitude, longitude)
    dataset_risk = cell["risk_score"]
    prediction, model_error, prediction_provider = predict_point(
        factors=cell["factors"], time_period=time_period, jurisdiction=cell["jurisdiction"],
        latitude=latitude, longitude=longitude,
    )
    if prediction:
        cell["baseline_risk_score"] = dataset_risk
        dataset_risk = prediction["risk_exposure"]
        cell["ml_risk_score"] = dataset_risk
        cell["model_version"] = prediction["model_version"]
        cell["model_confidence"] = prediction["confidence"]
        cell["model_factor_contributions"] = prediction["factor_contributions"]
        cell["risk_provider"] = prediction_provider
        if model_error:
            cell["ml_fallback_reason"] = model_error
    else:
        cell["risk_provider"] = "deterministic_baseline"
        cell["ml_fallback_reason"] = model_error
    combined = combine_risk(dataset_risk, live_risk)
    cell["dataset_risk_score"] = dataset_risk
    cell["verified_hazard_risk"] = live_risk
    cell["risk_score"] = combined
    cell["safety_score"] = round(100 - combined, 2)
    cell["verified_hazards"] = nearby
    return cell


def sampled(coordinates: list[Coordinate], maximum: int = 80) -> list[Coordinate]:
    if len(coordinates) <= maximum:
        return coordinates
    indexes = {round(i * (len(coordinates) - 1) / (maximum - 1)) for i in range(maximum)}
    return [coordinates[index] for index in sorted(indexes)]


def haversine_meters(lat1: float, lng1: float, lat2: float, lng2: float) -> float:
    radius = 6_371_000.0
    phi1, phi2 = math.radians(lat1), math.radians(lat2)
    d_phi = math.radians(lat2 - lat1)
    d_lng = math.radians(lng2 - lng1)
    value = math.sin(d_phi / 2) ** 2 + math.cos(phi1) * math.cos(phi2) * math.sin(d_lng / 2) ** 2
    return radius * 2 * math.atan2(math.sqrt(value), math.sqrt(1 - value))


def hazard_distance(latitude: float, longitude: float, hazard: dict) -> float:
    coordinates = hazard.get("geometry_coordinates")
    if not coordinates or hazard.get("geometry_type") == "POINT":
        return haversine_meters(latitude, longitude, hazard["latitude"], hazard["longitude"])
    points = [(float(point["latitude"]), float(point["longitude"])) for point in coordinates]
    if hazard.get("geometry_type") == "POLYGON" and point_in_polygon(latitude, longitude, points):
        return 0.0
    return min(distance_to_segment(latitude, longitude, *start, *end)
               for start, end in zip(points, points[1:] + ([points[0]] if hazard.get("geometry_type") == "POLYGON" else [])))


def distance_to_segment(latitude: float, longitude: float, lat1: float, lng1: float, lat2: float, lng2: float) -> float:
    radius = 6_371_000.0
    cosine = math.cos(math.radians(latitude))
    px, py = math.radians(longitude) * radius * cosine, math.radians(latitude) * radius
    ax, ay = math.radians(lng1) * radius * cosine, math.radians(lat1) * radius
    bx, by = math.radians(lng2) * radius * cosine, math.radians(lat2) * radius
    dx, dy = bx - ax, by - ay
    if dx == 0 and dy == 0: return math.hypot(px - ax, py - ay)
    position = max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)))
    return math.hypot(px - (ax + position * dx), py - (ay + position * dy))


def point_in_polygon(latitude: float, longitude: float, points: list[tuple[float, float]]) -> bool:
    inside = False
    previous = points[-1]
    for current in points:
        lat1, lng1 = previous; lat2, lng2 = current
        if (lat1 > latitude) != (lat2 > latitude):
            crossing = (lng2 - lng1) * (latitude - lat1) / (lat2 - lat1) + lng1
            if longitude < crossing: inside = not inside
        previous = current
    return inside


def hazard_contribution(hazard: dict, distance_meters: float) -> float:
    if distance_meters > 750:
        return 0.0
    proximity = max(0.0, 1.0 - distance_meters / 750.0)
    status_weight = 0.65 if hazard["status"] == "MONITORING" else 1.0
    base = SEVERITY_RISK.get(hazard["severity"], 0.0) + ROAD_RISK.get(hazard["road_status"], 0.0)
    return max(0.0, min(95.0, base * proximity * status_weight))


def combined_contributions(contributions: list[float]) -> float:
    remaining_safe = 1.0
    for contribution in contributions:
        remaining_safe *= 1.0 - max(0.0, min(100.0, contribution)) / 100.0
    return round(100.0 * (1.0 - remaining_safe), 2)


def verified_hazard_risk(latitude: float, longitude: float) -> tuple[float, list[dict]]:
    nearby = []
    contributions = []
    for hazard in active_hazards():
        distance = hazard_distance(latitude, longitude, hazard)
        contribution = hazard_contribution(hazard, distance)
        if contribution <= 0:
            continue
        contributions.append(contribution)
        nearby.append({
            "id": hazard["id"],
            "title": hazard["title"],
            "category_key": hazard["category_key"],
            "severity": hazard["severity"],
            "road_status": hazard["road_status"],
            "distance_meters": round(distance),
            "contribution": round(contribution, 2),
        })
    nearby.sort(key=lambda item: item["distance_meters"])
    return combined_contributions(contributions), nearby[:10]


def route_hazard_risk(points: list[Coordinate]) -> tuple[float, list[dict]]:
    exposures = []
    affecting = []
    for hazard in active_hazards():
        distance = min(hazard_distance(point.latitude, point.longitude, hazard) for point in points)
        contribution = hazard_contribution(hazard, distance)
        if contribution > 0:
            exposures.append(contribution)
            affecting.append({
                "id": hazard["id"], "title": hazard["title"], "severity": hazard["severity"],
                "road_status": hazard["road_status"], "distance_meters": round(distance),
            })
    affecting.sort(key=lambda item: item["distance_meters"])
    return combined_contributions(exposures), affecting[:10]


def route_no_go_conflicts(points: list[Coordinate]) -> list[dict]:
    """Find verified restrictions that must never be treated as a soft preference."""
    conflicts = []
    for hazard in active_hazards():
        authoritative_closed = hazard["road_status"] == "CLOSED"
        critical_unsafe = hazard["severity"] == "CRITICAL" and hazard["road_status"] == "UNSAFE"
        if not authoritative_closed and not critical_unsafe:
            continue
        default_radius = 60.0 if authoritative_closed else 40.0
        radius = float(hazard.get("no_go_radius_m") or default_radius)
        distance = min(hazard_distance(point.latitude, point.longitude, hazard) for point in points)
        if distance <= radius:
            conflicts.append({
                "id": hazard["id"], "title": hazard["title"], "severity": hazard["severity"],
                "road_status": hazard["road_status"], "distance_meters": round(distance),
                "no_go_radius_m": radius,
            })
    return conflicts


def combine_risk(dataset_risk: float, verified_risk: float) -> float:
    return combined_contributions([dataset_risk, verified_risk])


@app.post("/api/v1/risk/route")
def route_risk(request: RouteRiskRequest) -> dict:
    results = []
    excluded_routes = []
    for route in request.routes:
        points = sampled(route.coordinates)
        conflicts = route_no_go_conflicts(points)
        if conflicts:
            excluded_routes.append({"route_id": route.route_id, "blocked_by": conflicts})
            continue
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
            jurisdiction_counts: dict[str, int] = defaultdict(int)
            for cell in matched:
                area_counts[cell["area_name"]] += 1
                jurisdiction_counts[cell["jurisdiction"]] += 1
            areas = [name for name, _ in sorted(area_counts.items(), key=lambda item: item[1], reverse=True)[:3]]
        jurisdiction = (max(jurisdiction_counts, key=jurisdiction_counts.get) if matched else None)
        verified_exposure, affecting_hazards = route_hazard_risk(points)
        combined_exposure = combine_risk(exposure, verified_exposure)
        score = route.travel_minutes + WEIGHTS[request.profile] * combined_exposure
        results.append(
            {
                "route_id": route.route_id,
                "travel_minutes": route.travel_minutes,
                "distance_meters": route.distance_meters,
                "risk_exposure": combined_exposure,
                "dataset_risk_exposure": round(exposure, 2),
                "verified_hazard_exposure": verified_exposure,
                "affecting_hazards": affecting_hazards,
                "ranking_score": round(score, 2),
                "coverage_ratio": round(len(matched) / len(points), 3),
                "factor_averages": factors,
                "areas": areas,
                "jurisdiction": jurisdiction,
            }
        )
    if not results:
        raise HTTPException(status_code=409, detail={
            "code": "NO_SAFE_ALTERNATIVE",
            "message": "Every available route intersects an authoritative closure or critical no-go area.",
            "excluded_routes": excluded_routes,
        })
    model_payload = {"time_period": request.time_period, "profile": request.profile,
                     "routes": [route.model_dump() for route in request.routes], "baseline_results": results}
    predictions, model_error, prediction_provider = predict_routes(model_payload)
    if predictions:
        for result in results:
            prediction = predictions[result["route_id"]]
            result["baseline_risk_exposure"] = result["dataset_risk_exposure"]
            result["ml_risk_exposure"] = prediction["risk_exposure"]
            result["model_confidence"] = prediction["confidence"]
            result["model_version"] = prediction["model_version"]
            result["model_factor_contributions"] = prediction["factor_contributions"]
            result["dataset_risk_exposure"] = prediction["risk_exposure"]
            result["risk_exposure"] = combine_risk(prediction["risk_exposure"], result["verified_hazard_exposure"])
            result["ranking_score"] = round(result["travel_minutes"] + WEIGHTS[request.profile] * result["risk_exposure"], 2)
    results.sort(key=lambda result: result["ranking_score"])
    return {
        "profile": request.profile,
        "time_period": request.time_period,
        "synthetic": True,
        "runtime_resolution": "1 km area cells derived from the complete road-segment dataset",
        "risk_sources": ([prediction_provider, "government_verified_hazards"] if predictions else ["baseline_dataset", "government_verified_hazards"]),
        "risk_provider": prediction_provider if predictions else "deterministic_baseline",
        "ml_fallback_reason": model_error,
        "excluded_routes": excluded_routes,
        "closures_enforced": True,
        "results": results,
    }
