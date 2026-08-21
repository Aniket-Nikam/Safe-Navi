from __future__ import annotations

import json
import math
import os
import sqlite3
from pathlib import Path

FACTORS = [
    "traffic_congestion_index",
    "crime_risk_index",
    "lighting_quality_index",
    "population_density_index",
    "road_condition_index",
    "pedestrian_activity_index",
    "emergency_access_index",
    "flood_risk_index",
    "isolation_index",
    "public_transport_access_index",
]

DEFAULT_DB = Path(__file__).resolve().parents[1] / "data" / "safe_navi_runtime.sqlite"


def db_path() -> Path:
    return Path(os.getenv("SAFE_NAVI_DB", str(DEFAULT_DB))).resolve()


def connect() -> sqlite3.Connection:
    connection = sqlite3.connect(db_path())
    connection.row_factory = sqlite3.Row
    return connection


def metadata() -> dict:
    with connect() as connection:
        return {row["key"]: json.loads(row["value"]) for row in connection.execute("SELECT key, value FROM metadata")}


def lookup_point(latitude: float, longitude: float, time_period: str) -> dict | None:
    with connect() as connection:
        row = connection.execute(
            """
            SELECT c.* FROM cell_rtree r
            JOIN cells c ON c.id = r.id
            WHERE c.time_period = ?
              AND r.min_lng <= ? AND r.max_lng >= ?
              AND r.min_lat <= ? AND r.max_lat >= ?
            ORDER BY (c.max_lng-c.min_lng) * (c.max_lat-c.min_lat)
            LIMIT 1
            """,
            (time_period, longitude, longitude, latitude, latitude),
        ).fetchone()
        if row is None:
            row = connection.execute(
                """
                SELECT *, ((center_lat-?)*(center_lat-?) + (center_lng-?)*(center_lng-?)) AS distance_sq
                FROM cells WHERE time_period = ? ORDER BY distance_sq LIMIT 1
                """,
                (latitude, latitude, longitude, longitude, time_period),
            ).fetchone()
            if row is None or math.sqrt(row["distance_sq"]) > 0.03:
                return None
        return serialize_cell(row)


def serialize_cell(row: sqlite3.Row) -> dict:
    return {
        "area_cell_id": row["area_cell_id"],
        "time_period": row["time_period"],
        "jurisdiction": row["jurisdiction"],
        "area_name": row["area_name"],
        "road_segment_count": row["road_segment_count"],
        "risk_score": round(row["risk_score"], 2),
        "safety_score": round(100 - row["risk_score"], 2),
        "risk_label": row["risk_label"],
        "factors": {factor: round(row[factor], 2) for factor in FACTORS},
        "synthetic": True,
        "source": "Safe-Navi SQLite area-cell runtime derived from the Mumbai–Navi Mumbai dataset",
    }
