"""Build the small SQLite runtime layer from the complete synthetic area-cell export.

The full road_segments.parquet table remains the street-level analytics/training source.
This runtime database contains its 1 km area-cell aggregation for fast, reproducible
mobile demonstration queries across the full documented Mumbai/Navi Mumbai coverage.
"""

from __future__ import annotations

import argparse
import csv
import gzip
import json
import re
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

NUMBER = re.compile(r"-?\d+(?:\.\d+)?")


def geometry_bounds(wkt: str) -> tuple[float, float, float, float]:
    values = [float(value) for value in NUMBER.findall(wkt)]
    if len(values) < 4 or len(values) % 2:
        raise ValueError("Invalid polygon WKT")
    longitudes = values[0::2]
    latitudes = values[1::2]
    return min(longitudes), min(latitudes), max(longitudes), max(latitudes)


def build(source: Path, output: Path, manifest_path: Path | None = None) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    if output.exists():
        output.unlink()
    connection = sqlite3.connect(output)
    connection.executescript(
        """
        PRAGMA journal_mode=DELETE;
        PRAGMA synchronous=FULL;
        CREATE TABLE cells (
            id INTEGER PRIMARY KEY,
            area_cell_id TEXT NOT NULL,
            time_period TEXT NOT NULL,
            jurisdiction TEXT NOT NULL,
            area_name TEXT NOT NULL,
            road_segment_count INTEGER NOT NULL,
            risk_score REAL NOT NULL,
            risk_label TEXT NOT NULL,
            traffic_congestion_index REAL NOT NULL,
            crime_risk_index REAL NOT NULL,
            lighting_quality_index REAL NOT NULL,
            population_density_index REAL NOT NULL,
            road_condition_index REAL NOT NULL,
            pedestrian_activity_index REAL NOT NULL,
            emergency_access_index REAL NOT NULL,
            flood_risk_index REAL NOT NULL,
            isolation_index REAL NOT NULL,
            public_transport_access_index REAL NOT NULL,
            min_lng REAL NOT NULL,
            min_lat REAL NOT NULL,
            max_lng REAL NOT NULL,
            max_lat REAL NOT NULL,
            center_lng REAL NOT NULL,
            center_lat REAL NOT NULL
        );
        CREATE INDEX cells_period_idx ON cells(time_period);
        CREATE INDEX cells_area_idx ON cells(area_cell_id, time_period);
        CREATE VIRTUAL TABLE cell_rtree USING rtree(id, min_lng, max_lng, min_lat, max_lat);
        CREATE TABLE metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL);
        """
    )
    opener = gzip.open if source.suffix == ".gz" else open
    with opener(source, "rt", encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle)
        for row_id, row in enumerate(reader, start=1):
            min_lng, min_lat, max_lng, max_lat = geometry_bounds(row["geometry_wkt"])
            values = [float(row[factor]) for factor in FACTORS]
            connection.execute(
                """
                INSERT INTO cells VALUES (
                    ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                    ?, ?, ?, ?, ?, ?
                )
                """,
                (
                    row_id,
                    row["area_cell_id"],
                    row["time_period"],
                    row["jurisdiction"],
                    row.get("area_name") or "Unnamed area",
                    int(row["road_segment_count"]),
                    float(row["synthetic_safety_risk_score"]),
                    row["synthetic_risk_label"],
                    *values,
                    min_lng,
                    min_lat,
                    max_lng,
                    max_lat,
                    (min_lng + max_lng) / 2,
                    (min_lat + max_lat) / 2,
                ),
            )
            connection.execute(
                "INSERT INTO cell_rtree VALUES (?, ?, ?, ?, ?)",
                (row_id, min_lng, max_lng, min_lat, max_lat),
            )
    metadata = {
        "runtime_source": source.name,
        "runtime_resolution": "1 km area-cell aggregation",
        "synthetic": True,
        "factor_count": 10,
    }
    if manifest_path and manifest_path.exists():
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        metadata.update(
            dataset_name=manifest.get("dataset_name"),
            dataset_version=manifest.get("version"),
            physical_road_segments=manifest.get("statistics", {}).get("physical_road_segments"),
            road_time_rows=manifest.get("statistics", {}).get("road_time_rows"),
            total_physical_road_length_km=manifest.get("statistics", {}).get("total_physical_road_length_km"),
            osm_attribution=manifest.get("osm_attribution"),
        )
    connection.executemany(
        "INSERT INTO metadata(key, value) VALUES (?, ?)",
        [(key, json.dumps(value)) for key, value in metadata.items()],
    )
    connection.commit()
    connection.execute("VACUUM")
    connection.close()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--manifest", type=Path)
    args = parser.parse_args()
    build(args.source, args.output, args.manifest)


if __name__ == "__main__":
    main()
