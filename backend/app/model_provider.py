"""Validated optional ML boundary for teammate-supplied model serving."""
from __future__ import annotations

import os
from typing import Any

import httpx

from . import local_model


def provider_metadata() -> dict[str, Any]:
    endpoint = os.getenv("SAFE_NAVI_ML_ENDPOINT", "").strip()
    bundled = local_model.metadata()
    value = {
        "active_provider": "external_ml" if endpoint else "trained_ml" if bundled else "deterministic_baseline",
        "ml_configured": bool(endpoint or bundled),
        "deployment": "external_https" if endpoint else "bundled_safe_json" if bundled else "none",
        "contract_version": "safenavi-risk-v1",
        "required_output": ["route_id", "risk_exposure", "confidence", "factor_contributions", "model_version"],
        "fallback": "bundled_trained_ml" if bundled and endpoint else "deterministic_baseline",
    }
    if bundled:
        value["bundled_model"] = bundled
    return value


def validate_predictions(value: Any, expected_route_ids: set[str]) -> dict[str, dict[str, Any]]:
    if not isinstance(value, dict) or not isinstance(value.get("predictions"), list):
        raise ValueError("Model response must contain predictions")
    predictions: dict[str, dict[str, Any]] = {}
    for item in value["predictions"]:
        if not isinstance(item, dict): raise ValueError("Prediction must be an object")
        route_id = item.get("route_id"); risk = item.get("risk_exposure"); confidence = item.get("confidence")
        factors = item.get("factor_contributions"); version = item.get("model_version")
        if route_id not in expected_route_ids or route_id in predictions: raise ValueError("Unexpected or duplicate route_id")
        if not isinstance(risk, (int, float)) or not 0 <= float(risk) <= 100: raise ValueError("risk_exposure must be 0..100")
        if not isinstance(confidence, (int, float)) or not 0 <= float(confidence) <= 1: raise ValueError("confidence must be 0..1")
        if not isinstance(factors, dict) or not all(isinstance(key, str) and isinstance(val, (int, float)) for key, val in factors.items()):
            raise ValueError("factor_contributions must be numeric")
        if not isinstance(version, str) or not version.strip(): raise ValueError("model_version required")
        predictions[route_id] = {"risk_exposure": round(float(risk), 2), "confidence": round(float(confidence), 4),
                                 "factor_contributions": factors, "model_version": version[:100]}
    if set(predictions) != expected_route_ids: raise ValueError("A prediction is required for every route")
    return predictions


def _bundled_routes(payload: dict[str, Any], reason: str | None = None) -> tuple[dict[str, dict[str, Any]] | None, str | None, str]:
    try:
        if local_model.metadata():
            return local_model.predict_routes(payload), reason, "trained_ml"
        return None, reason or "not_configured", "deterministic_baseline"
    except Exception as error:
        return None, "local_" + type(error).__name__, "deterministic_baseline"


def predict_routes(payload: dict[str, Any]) -> tuple[dict[str, dict[str, Any]] | None, str | None, str]:
    endpoint = os.getenv("SAFE_NAVI_ML_ENDPOINT", "").strip()
    if not endpoint:
        return _bundled_routes(payload)
    if not endpoint.startswith("https://") and not endpoint.startswith("http://127.0.0.1") and not endpoint.startswith("http://localhost"):
        return _bundled_routes(payload, "external_endpoint_must_use_https")
    try:
        with httpx.Client(timeout=httpx.Timeout(5.0, connect=2.0)) as client:
            response = client.post(endpoint, json={"contract_version": "safenavi-risk-v1", **payload})
            response.raise_for_status()
            expected = {route["route_id"] for route in payload["routes"]}
            return validate_predictions(response.json(), expected), None, "external_ml"
    except Exception as error:
        return _bundled_routes(payload, "external_" + type(error).__name__)


def predict_point(*, factors: dict[str, float], time_period: str, jurisdiction: str,
                  latitude: float, longitude: float) -> tuple[dict[str, Any] | None, str | None, str]:
    try:
        if not local_model.metadata():
            return None, "not_configured", "deterministic_baseline"
        fallback = "external_point_contract_uses_bundled_model" if os.getenv("SAFE_NAVI_ML_ENDPOINT", "").strip() else None
        return local_model.predict(factors, time_period, jurisdiction, latitude, longitude, 0.0), fallback, "trained_ml"
    except Exception as error:
        return None, "local_" + type(error).__name__, "deterministic_baseline"
