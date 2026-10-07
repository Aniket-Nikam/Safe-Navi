"""Build the compact Android A* graph from the documented road-segment Parquet dataset."""
from __future__ import annotations

import argparse
import math
import re
import sqlite3
from pathlib import Path

import pyarrow.parquet as pq


SPEEDS = {
    "motorway": 80, "motorway_link": 50, "trunk": 65, "trunk_link": 45,
    "primary": 45, "primary_link": 35, "secondary": 38, "secondary_link": 30,
    "tertiary": 30, "tertiary_link": 25, "residential": 24, "living_street": 12,
    "service": 16, "unclassified": 20, "track": 10, "busway": 25,
}


def endpoints(wkt: str) -> tuple[tuple[float, float], tuple[float, float]]:
    match = re.match(r"LINESTRING\s*\((.+)\)", wkt)
    if not match:
        raise ValueError("unsupported geometry")
    values = match.group(1).split(",")
    first = [float(value) for value in values[0].strip().split()[:2]]
    last = [float(value) for value in values[-1].strip().split()[:2]]
    return (first[1], first[0]), (last[1], last[0])


def build(source: Path, output: Path) -> None:
    columns = ["segment_uid", "u", "v", "street_name", "area_name", "road_class", "oneway", "maxspeed_kph",
               "length_m", "geometry_wkt", "synthetic_safety_risk_score"]
    table = pq.read_table(source, columns=columns + ["jurisdiction"], filters=[("jurisdiction", "=", "Navi Mumbai")])
    frame = table.drop(["jurisdiction"]).to_pandas()
    grouped = frame.groupby("segment_uid", sort=False, as_index=False).agg({
        "u": "first", "v": "first", "street_name": "first", "area_name": "first", "road_class": "first", "oneway": "first",
        "maxspeed_kph": "first", "length_m": "first", "geometry_wkt": "first",
        "synthetic_safety_risk_score": "max",
    })
    output.parent.mkdir(parents=True, exist_ok=True)
    if output.exists():
        output.unlink()
    connection = sqlite3.connect(output)
    connection.executescript("""
        PRAGMA journal_mode=OFF; PRAGMA synchronous=OFF; PRAGMA temp_store=MEMORY; PRAGMA page_size=4096;
        CREATE TABLE metadata(key TEXT PRIMARY KEY,value TEXT NOT NULL);
        CREATE TABLE nodes(node_id INTEGER PRIMARY KEY,latitude REAL NOT NULL,longitude REAL NOT NULL);
        CREATE INDEX nodes_location_idx ON nodes(latitude,longitude);
        CREATE TABLE edges(id INTEGER PRIMARY KEY,from_node INTEGER NOT NULL,to_node INTEGER NOT NULL,
          to_lat REAL NOT NULL,to_lon REAL NOT NULL,distance_m REAL NOT NULL,risk REAL NOT NULL,
          drive_seconds REAL,walk_seconds REAL,cycle_seconds REAL,street_name TEXT,area_name TEXT,road_class TEXT);
        CREATE INDEX edges_from_idx ON edges(from_node);
    """)
    nodes: dict[int, tuple[float, float]] = {}
    edges = []
    edge_id = 0
    for row in grouped.itertuples(index=False):
        try:
            start, end = endpoints(row.geometry_wkt)
        except (TypeError, ValueError):
            continue
        u, v = int(row.u), int(row.v)
        nodes[u] = start; nodes[v] = end
        road_class = str(row.road_class or "unclassified")
        speed = float(row.maxspeed_kph or 0) or SPEEDS.get(road_class, 20)
        distance = max(1.0, float(row.length_m))
        drive = distance / (speed / 3.6)
        walk = None if road_class in {"motorway", "motorway_link"} else distance / 1.35
        cycle = None if road_class in {"motorway", "motorway_link", "trunk", "trunk_link"} else distance / 4.2
        risk = float(row.synthetic_safety_risk_score)
        edge_id += 1
        edges.append((edge_id, u, v, end[0], end[1], distance, risk, drive, walk, cycle,
                      str(row.street_name or ""), str(row.area_name or ""), road_class))
        reverse_drive = None if bool(row.oneway) else drive
        if reverse_drive is not None or walk is not None or cycle is not None:
            edge_id += 1
            edges.append((edge_id, v, u, start[0], start[1], distance, risk, reverse_drive, walk, cycle,
                          str(row.street_name or ""), str(row.area_name or ""), road_class))
    connection.executemany("INSERT INTO nodes VALUES (?,?,?)", ((key, value[0], value[1]) for key, value in nodes.items()))
    connection.executemany("INSERT INTO edges VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)", edges)
    connection.executemany("INSERT INTO metadata VALUES (?,?)", [
        ("region", "Navi Mumbai"), ("source", source.name), ("risk", "maximum synthetic risk across four periods"),
        ("nodes", str(len(nodes))), ("edges", str(len(edges))),
    ])
    connection.commit(); connection.execute("VACUUM"); connection.close()
    print(f"Built {output} with {len(nodes):,} nodes and {len(edges):,} directed edges ({output.stat().st_size / 1048576:.1f} MB)")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    build(args.source.resolve(), args.output.resolve())
