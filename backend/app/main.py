from __future__ import annotations

import asyncio
from collections import defaultdict
from contextlib import asynccontextmanager, suppress
import logging
import hmac
import hashlib
import math
import os
import re
import time
from typing import Literal

from fastapi import FastAPI, HTTPException, Query, Header
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

from .database import FACTORS, db_path, lookup_point, metadata
from .product_api import router as product_router
from .product_database import active_hazards, news_configuration, news_ingestion_due
from .model_provider import predict_point, predict_routes, provider_metadata
from .news_intelligence import configured_feeds, ingest_configured_feeds
from .security import RateLimitMiddleware
from .cloud_configuration import scheduler_mode, validate_cloud_configuration
from .cloud_database import database_url, ProductDatabaseUnavailable
from .evidence_storage import backend_name
from .route_explanations import explain as explain_route, markers as explanation_markers

PERIODS = {"morning_peak", "midday", "evening_peak", "night"}
WEIGHTS = {"fastest": 0.0, "balanced": 0.35, "safest": 1.0}


def ranking_score(route: dict, profile: str) -> float:
    # Safest is risk-first, not a second time/risk compromise.
    if profile == "safest":
        return route["risk_exposure"]
    return route["travel_minutes"] + WEIGHTS[profile] * route["risk_exposure"]


def ranking_key(route: dict, profile: str) -> tuple:
    return (ranking_score(route, profile), route["travel_minutes"], route["risk_exposure"], route["route_id"])

logger = logging.getLogger("safenavi.news")


async def scheduled_news_ingestion(stop: asyncio.Event) -> None:
    while not stop.is_set():
        try:
            if await asyncio.to_thread(news_ingestion_due):
                result = await asyncio.to_thread(ingest_configured_feeds, False)
                logger.info("Scheduled news ingestion completed: inserted=%s errors=%s", result.get("inserted"), len(result.get("errors", [])))
        except Exception as error:
            logger.warning("News scheduler will retry after %s", type(error).__name__)
        try:
            await asyncio.wait_for(stop.wait(), timeout=60)
        except TimeoutError:
            continue


@asynccontextmanager
async def lifespan(_: FastAPI):
    await asyncio.to_thread(validate_cloud_configuration)
    stop = asyncio.Event()
    task: asyncio.Task | None = None
    if configured_feeds() and scheduler_mode() == "internal":
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


@app.exception_handler(ProductDatabaseUnavailable)
async def unavailable_product_database(_, error):
    return JSONResponse(status_code=503, content={"detail": str(error)}, headers={"Retry-After": "30"})

SEVERITY_RISK = {"LOW": 18.0, "MODERATE": 35.0, "HIGH": 60.0, "CRITICAL": 85.0}
ROAD_RISK = {"SAFE": -10.0, "CAUTION": 4.0, "UNSAFE": 15.0, "WORK_REQUIRED": 9.0, "CLOSED": 25.0}


class Coordinate(BaseModel):
    latitude: float = Field(ge=-90, le=90)
    longitude: float = Field(ge=-180, le=180)


class RouteInput(BaseModel):
    route_id: str
    travel_minutes: float = Field(gt=0)
    distance_meters: float = Field(gt=0)
    coordinates: list[Coordinate] = Field(min_length=2, max_length=50000)


class RouteRiskRequest(BaseModel):
    time_period: Literal["morning_peak", "midday", "evening_peak", "night"]
    profile: Literal["fastest", "balanced", "safest"]
    routes: list[RouteInput] = Field(min_length=1, max_length=5)


@app.get("/health")
def health() -> dict:
    provider = provider_metadata()
    news = news_configuration()
    return {"status": "ok", "flood_passability_policy": "explicit_passable_or_excluded_v1", "database": db_path().name, "risk_provider": provider["active_provider"],
            "news_scheduler": ("external" if scheduler_mode() == "external" else "active") if configured_feeds() else "not_configured",
            "product_store": "postgresql" if database_url() else "sqlite",
            "evidence_store": backend_name(),
            "news_refresh_hours": news["refresh_hours"], "news_last_ingested_at": news["last_ingested_at"],
            "news_next_ingestion_at": news["next_ingestion_at"]}


@app.post("/api/v1/internal/news/tick", tags=["scheduler"])
def external_news_tick(x_safenavi_scheduler_timestamp: str | None = Header(default=None),
                       x_safenavi_scheduler_signature: str | None = Header(default=None)) -> dict:
    expected = os.getenv("SAFE_NAVI_SCHEDULER_KEY", "")
    if len(expected) < 32:
        raise HTTPException(503, "External news scheduler is not configured")
    timestamp = x_safenavi_scheduler_timestamp or ""
    valid_time = bool(re.fullmatch(r"[0-9]{1,12}", timestamp)) and abs(time.time() - int(timestamp)) <= 300
    signature = hmac.new(expected.encode(), (timestamp + "\nPOST\n/api/v1/internal/news/tick").encode(), hashlib.sha256).hexdigest()
    if not valid_time or not hmac.compare_digest((x_safenavi_scheduler_signature or "").encode(), signature.encode()):
        raise HTTPException(403, "Scheduler authentication required")
    return ingest_configured_feeds(force=False)


@app.get("/api/v1/dataset/stats")
def dataset_stats() -> dict:
    return metadata()


@app.get("/api/v1/risk/provider")
def risk_provider() -> dict:
    return {**provider_metadata(), "ranking_policy": "risk_first_v2",
            "objectives": {"fastest": "lowest_time", "balanced": "time + 0.35 * assessed_risk",
                           "safest": "lowest_assessed_risk_then_time"}}


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


from .crime_labels import crime_labels


@app.get("/api/v1/risk/area-crime")
def area_crime(latitude: float = Query(ge=-90, le=90), longitude: float = Query(ge=-180, le=180),
               radius_m: int = Query(default=3000, ge=100, le=10000)) -> dict:
    """Only public verified active records; never infer crime types from synthetic factors or news."""
    counts: dict[str, int] = defaultdict(int)
    records = []
    for hazard in active_hazards():
        if hazard.get("status") not in {"ACTIVE", "MONITORING", "VERIFIED"} or "CRIME" not in hazard.get("category_key", "").upper():
            continue
        distance = hazard_distance(latitude, longitude, hazard)
        if distance > radius_m:
            continue
        text = " ".join(str(hazard.get(key) or "") for key in ("title", "description", "reason")).lower()
        types = crime_labels(hazard)
        for name in types:
            counts[name] += 1
        records.append({"hazard_id": hazard["id"], "title": hazard["title"], "types": types,
                        "severity": hazard["severity"], "distance_m": round(distance),
                        "latitude": hazard["latitude"], "longitude": hazard["longitude"]})
    records.sort(key=lambda item: (item["distance_m"], item["hazard_id"]))
    return {"radius_m": radius_m, "verified_record_count": len(records),
            "types": [{"type": name, "count": count} for name, count in sorted(counts.items(), key=lambda item: (-item[1], item[0]))],
            "records": records, "source": "government_verified_active_hazards",
            "classification": "keyword_labels_from_verified_text_not_independently_verified_crime_subtypes",
            "notice": "Active verified reports, not crime prevalence statistics. No records does not mean no crime. "
                      "One report can have multiple labels. The ML dataset provides a synthetic general crime-risk index, not historical crime types."}


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


def segment_distance(a: tuple[float, float], b: tuple[float, float],
                     c: tuple[float, float], d: tuple[float, float]) -> float:
    """Distance between complete segments, including crossings and collinear contact."""
    cosine = math.cos(math.radians((a[0] + b[0] + c[0] + d[0]) / 4))
    def xy(point): return (point[1] * cosine, point[0])
    p, q, r, s = map(xy, (a, b, c, d))
    def cross(x, y, z): return (y[0]-x[0])*(z[1]-x[1]) - (y[1]-x[1])*(z[0]-x[0])
    def on(x, y, z):
        return abs(cross(x, y, z)) <= 1e-12 and min(x[0], y[0])-1e-12 <= z[0] <= max(x[0], y[0])+1e-12 and min(x[1], y[1])-1e-12 <= z[1] <= max(x[1], y[1])+1e-12
    first, second, third, fourth = cross(p,q,r), cross(p,q,s), cross(r,s,p), cross(r,s,q)
    if ((first * second < 0 and third * fourth < 0) or on(p,q,r) or on(p,q,s) or on(r,s,p) or on(r,s,q)):
        return 0.0
    return min(distance_to_segment(*a, *c, *d), distance_to_segment(*b, *c, *d),
               distance_to_segment(*c, *a, *b), distance_to_segment(*d, *a, *b))


def route_geometry_distance(points: list[Coordinate], hazard: dict) -> float:
    if not points: return math.inf
    geometry = hazard.get("geometry_coordinates")
    if not geometry or len(geometry) < 2 or hazard.get("geometry_type") == "POINT":
        if len(points) == 1: return hazard_distance(points[0].latitude, points[0].longitude, hazard)
        return min(distance_to_segment(hazard["latitude"], hazard["longitude"],
                   a.latitude, a.longitude, b.latitude, b.longitude) for a,b in zip(points, points[1:]))
    boundary = [(float(p["latitude"]), float(p["longitude"])) for p in geometry]
    polygon = hazard.get("geometry_type") == "POLYGON"
    if polygon and any(point_in_polygon(p.latitude, p.longitude, boundary) for p in points): return 0.0
    if len(points) == 1: return hazard_distance(points[0].latitude, points[0].longitude, hazard)
    edges = list(zip(boundary, boundary[1:] + ([boundary[0]] if polygon else [])))
    best = math.inf
    for a,b in zip(points, points[1:]):
        for c,d in edges:
            best = min(best, segment_distance((a.latitude,a.longitude), (b.latitude,b.longitude), c,d))
            if best == 0: return 0.0
    return best


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
        distance = route_geometry_distance(points, hazard)
        contribution = hazard_contribution(hazard, distance)
        if contribution > 0:
            exposures.append(contribution)
            affecting.append({
                "id": hazard["id"], "title": hazard["title"], "severity": hazard["severity"],
                "road_status": hazard["road_status"], "distance_meters": round(distance),
                "latitude": hazard["latitude"], "longitude": hazard["longitude"],
                "reason": hazard.get("reason") or hazard.get("description") or "Government-verified hazard",
                "category_key": hazard.get("category_key", "OTHER"),
            })
    affecting.sort(key=lambda item: item["distance_meters"])
    return combined_contributions(exposures), affecting[:10]


def route_no_go_conflicts(points: list[Coordinate]) -> list[dict]:
    """Find verified restrictions that must never be treated as a soft preference."""
    conflicts = []
    from .hazard_policy import restriction_reason
    for hazard in active_hazards():
        restriction = restriction_reason(hazard)
        if restriction is None:
            continue
        default_radius = 40.0 if restriction == "CRITICAL_UNSAFE" else 60.0
        radius = float(hazard.get("no_go_radius_m") or default_radius)
        distance = route_geometry_distance(points, hazard)
        if distance <= radius:
            conflicts.append({
                "id": hazard["id"], "title": hazard["title"], "severity": hazard["severity"],
                "road_status": hazard["road_status"], "distance_meters": round(distance),
                "passability": hazard.get("passability") or "UNKNOWN", "restriction_reason": restriction,
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
        points = sampled(sampled(route.coordinates, 100))
        conflicts = route_no_go_conflicts(route.coordinates)
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
        verified_exposure, affecting_hazards = route_hazard_risk(route.coordinates)
        combined_exposure = combine_risk(exposure, verified_exposure)
        sections = explain_route(route.coordinates, request.time_period, lookup_point, predict_point)
        official_points = [{"id": h["id"], "latitude": h["latitude"], "longitude": h["longitude"],
                           "title": h["title"], "reason": h["reason"], "verified": True,
                           "source": "government_verified_hazard", "risk_exposure": SEVERITY_RISK.get(h["severity"], 0)}
                          for h in affecting_hazards]
        score = ranking_score({"travel_minutes": route.travel_minutes, "risk_exposure": combined_exposure}, request.profile)
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
                "risk_points": explanation_markers(sections, official_points),
                "risk_sections": sections,
                "explanation_notice": "Distance-sampled area estimates across the full route. Grey sections are unassessed, not safe. Official hazards are separate verified records.",
            }
        )
    if not results:
        raise HTTPException(status_code=409, detail={
            "code": "NO_SAFE_ALTERNATIVE",
            "message": "No confirmed safe alternative: every available route intersects an authoritative closure, impassable flood or flood with unconfirmed passability.",
            "excluded_routes": excluded_routes,
        })
    model_payload = {"time_period": request.time_period, "profile": request.profile,
                     # Preserve the established 100-point inference representation while closure
                     # checks above always use full road geometry. More shape vertices must not
                     # silently change the trained model's length/location features.
                     "routes": [{**route.model_dump(), "coordinates": [point.model_dump() for point in sampled(route.coordinates, 100)]}
                                for route in request.routes], "baseline_results": results}
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
            result["ranking_score"] = round(ranking_score(result, request.profile), 2)
    results.sort(key=lambda result: ranking_key(result, request.profile))
    recommendations = {}
    for profile, weight in WEIGHTS.items():
        chosen = min(results, key=lambda item: ranking_key(item, profile))
        recommendations[profile] = {"route_id": chosen["route_id"], "travel_minutes": chosen["travel_minutes"],
                                    "distance_meters": chosen["distance_meters"], "risk_exposure": chosen["risk_exposure"],
                                    "risk_weight": weight,
                                    "objective": "lowest_assessed_risk_then_time" if profile == "safest" else
                                                 "lowest_time" if profile == "fastest" else "time_risk_tradeoff",
                                    "ranking_score": round(ranking_score(chosen, profile), 2)}
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
        "recommendations": recommendations,
        "distinct_recommended_routes": len({item["route_id"] for item in recommendations.values()}),
        "ranking_policy": "risk_first_v2",
        "ranking_formula": "fastest: time; balanced: time + 0.35 * risk; safest: risk, then time (among evaluated non-excluded roads)",
        "results": results,
    }
