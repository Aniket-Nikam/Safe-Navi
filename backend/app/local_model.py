"""Dependency-free inference for the validated Safe-Route linear model."""
from __future__ import annotations

from functools import lru_cache
import json
import math
import os
from pathlib import Path
from typing import Any

from .database import FACTORS

DEFAULT_MODEL = Path(__file__).resolve().parents[1] / "models" / "safety_score_linear_v1.json"


def model_path() -> Path:
    return Path(os.getenv("SAFE_NAVI_LOCAL_MODEL", str(DEFAULT_MODEL))).resolve()


@lru_cache(maxsize=2)
def _load(path: str) -> dict[str, Any]:
    value = json.loads(Path(path).read_text(encoding="utf-8"))
    if value.get("format") != "safenavi-linear-risk-v1":
        raise ValueError("Unsupported local model format")
    features, coefficients = value.get("features"), value.get("coefficients")
    if not isinstance(features, list) or not isinstance(coefficients, list) or len(features) != len(coefficients):
        raise ValueError("Invalid local model feature contract")
    if value.get("feature_count") != len(features) or len(set(features)) != len(features):
        raise ValueError("Invalid local model feature metadata")
    if not all(isinstance(item, str) for item in features):
        raise ValueError("Invalid local model feature name")
    if not all(isinstance(item, (int, float)) and math.isfinite(float(item)) for item in coefficients):
        raise ValueError("Invalid local model coefficient")
    if not isinstance(value.get("intercept"), (int, float)):
        raise ValueError("Invalid local model intercept")
    return value


def metadata() -> dict[str, Any] | None:
    path = model_path()
    if not path.is_file():
        return None
    try:
        value = _load(str(path))
        return {
            "model_version": value["model_version"],
            "model_type": value["model_type"],
            "target": value["target"],
            "feature_count": value["feature_count"],
            "metrics": value["metrics"],
            "training_scope": value["training_scope"],
            "artifact_format": value["format"],
        }
    except (OSError, ValueError, KeyError, json.JSONDecodeError):
        return None


def _utm_43n(latitude: float, longitude: float) -> tuple[float, float]:
    """WGS84 to UTM zone 43N, matching the training dataset's mid_x/mid_y."""
    a, ecc_sq, k0 = 6378137.0, 0.00669438, 0.9996
    lat = math.radians(latitude)
    lon = math.radians(longitude)
    origin = math.radians(75.0)
    ecc_prime_sq = ecc_sq / (1 - ecc_sq)
    n = a / math.sqrt(1 - ecc_sq * math.sin(lat) ** 2)
    t = math.tan(lat) ** 2
    c = ecc_prime_sq * math.cos(lat) ** 2
    aa = math.cos(lat) * (lon - origin)
    m = a * ((1 - ecc_sq / 4 - 3 * ecc_sq**2 / 64 - 5 * ecc_sq**3 / 256) * lat
             - (3 * ecc_sq / 8 + 3 * ecc_sq**2 / 32 + 45 * ecc_sq**3 / 1024) * math.sin(2 * lat)
             + (15 * ecc_sq**2 / 256 + 45 * ecc_sq**3 / 1024) * math.sin(4 * lat)
             - (35 * ecc_sq**3 / 3072) * math.sin(6 * lat))
    easting = k0 * n * (aa + (1 - t + c) * aa**3 / 6
                        + (5 - 18 * t + t**2 + 72 * c - 58 * ecc_prime_sq) * aa**5 / 120) + 500000.0
    northing = k0 * (m + n * math.tan(lat) * (aa**2 / 2
                      + (5 - t + 9 * c + 4 * c**2) * aa**4 / 24
                      + (61 - 58 * t + t**2 + 600 * c - 330 * ecc_prime_sq) * aa**6 / 720))
    return easting, northing


def _observation(factors: dict[str, float], time_period: str, jurisdiction: str | None,
                 latitude: float, longitude: float, length_m: float) -> dict[str, float]:
    model = _load(str(model_path()))
    values = {name: 0.0 for name in model["features"]}
    values.update({
        "lanes": 2.0,
        "maxspeed_kph": 40.0,
        "length_m": max(0.0, float(length_m)),
        "area_label_distance_m": 0.0,
    })
    values["mid_x"], values["mid_y"] = _utm_43n(latitude, longitude)
    for factor in FACTORS:
        values[factor] = min(100.0, max(0.0, float(factors.get(factor, 0.0))))
    categorical = {
        f"jurisdiction_{jurisdiction}" if jurisdiction in {"Mumbai", "Navi Mumbai"} else "jurisdiction_nan",
        "road_class_nan", "oneway_nan", "surface_unknown", "lit_osm_unknown",
        "access_unknown", "place_type_nan", f"time_period_{time_period}",
    }
    for name in categorical:
        if name in values:
            values[name] = 1.0
    return values


def predict(factors: dict[str, float], time_period: str, jurisdiction: str | None,
            latitude: float, longitude: float, length_m: float, coverage: float = 1.0) -> dict[str, Any]:
    model = _load(str(model_path()))
    values = _observation(factors, time_period, jurisdiction, latitude, longitude, length_m)
    coefficients = dict(zip(model["features"], model["coefficients"]))
    raw = float(model["intercept"]) + sum(float(coefficients[name]) * values[name] for name in model["features"])
    contributions = {factor: round(float(coefficients[factor]) * values[factor], 3) for factor in FACTORS}
    cv_r2 = float(model["metrics"]["grouped_cross_validation"]["r2_mean"])
    # Factors are fully available; road metadata is conservatively defaulted at area-cell resolution.
    confidence = min(0.95, max(0.0, cv_r2 * min(1.0, max(0.0, coverage)) * 0.90))
    return {
        "risk_exposure": round(min(100.0, max(0.0, raw)), 2),
        "confidence": round(confidence, 4),
        "factor_contributions": contributions,
        "model_version": model["model_version"],
    }


def predict_routes(payload: dict[str, Any]) -> dict[str, dict[str, Any]]:
    routes = {route["route_id"]: route for route in payload["routes"]}
    output: dict[str, dict[str, Any]] = {}
    for baseline in payload["baseline_results"]:
        route = routes[baseline["route_id"]]
        coordinates = route["coordinates"]
        latitude = sum(point["latitude"] for point in coordinates) / len(coordinates)
        longitude = sum(point["longitude"] for point in coordinates) / len(coordinates)
        length = float(route["distance_meters"]) / max(1, len(coordinates) - 1)
        output[baseline["route_id"]] = predict(
            baseline["factor_averages"], payload["time_period"], baseline.get("jurisdiction"),
            latitude, longitude, length, float(baseline.get("coverage_ratio", 0.0)),
        )
    return output
