"""Export the validated teammate model to a non-executable Safe-Navi JSON artifact.

Run this only against a trusted pickle. The production API loads the resulting JSON,
never the Python pickle, so deployment does not execute serialized Python objects.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
from pathlib import Path

import joblib


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", type=Path, required=True)
    parser.add_argument("--features", type=Path, required=True)
    parser.add_argument("--comparison", type=Path, required=True)
    parser.add_argument("--cv-summary", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    model = joblib.load(args.model)
    features = list(joblib.load(args.features))
    if type(model).__module__ != "sklearn.linear_model._base" or type(model).__name__ != "LinearRegression":
        raise ValueError("Expected an sklearn LinearRegression artifact")
    if len(features) != int(model.n_features_in_) or features != list(model.feature_names_in_):
        raise ValueError("Feature metadata does not match the trained model")
    if len(model.coef_) != len(features):
        raise ValueError("Coefficient count does not match feature count")

    with args.comparison.open(newline="", encoding="utf-8") as handle:
        comparison = {row["model"]: row for row in csv.DictReader(handle)}
    with args.cv_summary.open(newline="", encoding="utf-8") as handle:
        cv = {row["model"]: row for row in csv.DictReader(handle)}
    selected = comparison["Linear Regression"]
    selected_cv = cv["Linear Regression"]
    payload = {
        "format": "safenavi-linear-risk-v1",
        "model_version": "safe-route-linear-2026.10",
        "model_type": "LinearRegression",
        "target": "synthetic_safety_risk_score",
        "feature_count": len(features),
        "features": features,
        "coefficients": [float(value) for value in model.coef_],
        "intercept": float(model.intercept_),
        "metrics": {
            "held_out": {key: float(selected[key]) for key in ("rmse", "mae", "r2")},
            "grouped_cross_validation": {
                key: float(selected_cv[key]) for key in (
                    "rmse_mean", "rmse_std", "mae_mean", "mae_std", "r2_mean", "r2_std"
                )
            },
        },
        "training_scope": {
            "rows": 1_809_864,
            "physical_road_segments": 452_466,
            "jurisdictions": ["Mumbai", "Navi Mumbai"],
            "time_periods": ["morning_peak", "midday", "evening_peak", "night"],
            "synthetic_target": True,
        },
        "source_sha256": {
            "model_pickle": hashlib.sha256(args.model.read_bytes()).hexdigest(),
            "feature_pickle": hashlib.sha256(args.features.read_bytes()).hexdigest(),
        },
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    print(f"Exported {len(features)} coefficients to {args.output}")


if __name__ == "__main__":
    main()
