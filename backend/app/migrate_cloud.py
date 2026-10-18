"""Read-only preflight or explicit SQLite-to-Postgres/private-storage migration.

No sessions or journey-share tokens are exported. Source database and original files
are never changed. --apply is required for any upload or target database changes.
"""
from __future__ import annotations

import argparse
from contextlib import contextmanager
import hashlib
import json
from pathlib import Path
import re
import sqlite3
import tempfile

from .product_database import _initialize_product_schema, connect_product, utc_now
from .cloud_database import database_url
from . import evidence_storage

TABLES = (
    "users", "administration_audit", "administration_settings", "reports", "hazards", "hazard_history",
    "report_evidence", "report_messages", "report_confirmations", "community_posts", "community_votes",
    "community_comments", "notifications", "news_signals", "news_hazard_links", "news_configuration", "saved_places",
    "emergency_contacts", "journeys",
)


@contextmanager
def source_snapshot(source: Path):
    source = source.resolve(strict=True)
    if not source.is_file():
        raise ValueError("Source must be a saved Safe-Navi product database")
    with tempfile.TemporaryDirectory(prefix="safenavi-migration-") as folder:
        with sqlite3.connect(source.as_uri() + "?mode=ro", uri=True) as original:
            connection = sqlite3.connect(str(Path(folder) / "snapshot.sqlite"))
            try:
                original.backup(connection)
                connection.row_factory = sqlite3.Row
                tables = {row[0] for row in connection.execute("SELECT name FROM sqlite_master WHERE type='table'")}
                if not {"users", "reports", "report_evidence"} <= tables:
                    raise ValueError("Source is not a Safe-Navi product database")
                # Schema upgrades affect this temporary snapshot only, not the live source.
                _initialize_product_schema(connection)
                connection.commit()
                yield connection
            finally:
                connection.close()


def preflight(connection, evidence_dir: Path):
    counts = {table: connection.execute(f'SELECT COUNT(*) FROM "{table}"').fetchone()[0] for table in TABLES}
    total = 0
    for row in connection.execute("SELECT storage_key,size_bytes,sha256 FROM report_evidence"):
        key = evidence_storage.validate_key(row["storage_key"])
        path = evidence_dir / key
        if not path.is_file():
            raise ValueError(f"Missing source evidence: {key}")
        size = path.stat().st_size
        if size != row["size_bytes"] or size > evidence_storage.MAX_EVIDENCE_BYTES or size <= 0:
            raise ValueError(f"Evidence size mismatch: {key}")
        if hashlib.sha256(path.read_bytes()).hexdigest() != row["sha256"]:
            raise ValueError(f"Evidence integrity mismatch: {key}")
        total += size
    return {"rows": counts, "evidence_bytes": total,
            "sessions_migrated": False, "journey_sharing_migrated": False, "source_modified": False}


def _assert_empty_target(connection):
    for table in (*TABLES, "sessions"):
        if table != "news_configuration" and connection.execute(f'SELECT COUNT(*) FROM "{table}"').fetchone()[0]:
            raise ValueError("Target is not empty; refusing to merge or overwrite existing cloud records")


def _ordered_comments(rows):
    pending = {row["id"]: row for row in rows}
    ordered, added = [], set()
    while pending:
        ready = [row for row in pending.values() if not row.get("parent_id") or row["parent_id"] in added]
        if not ready:
            raise ValueError("Source comments contain missing parents or a cycle")
        for row in ready:
            ordered.append(row); added.add(row["id"]); del pending[row["id"]]
    return ordered


def apply_migration(snapshot, evidence_dir: Path):
    if not database_url() or evidence_storage.backend_name() != "supabase":
        raise ValueError("Migration requires an explicitly configured cloud database and private Supabase bucket")
    summary = preflight(snapshot, evidence_dir)
    evidence_storage.verify_private_bucket()
    with connect_product() as target:
        _assert_empty_target(target)
    # Validate and upload before opening the relational transaction. Upload IDs remain
    # stable; a failed attempt can resume if the existing object's hash matches.
    for row in snapshot.execute("SELECT * FROM report_evidence"):
        key = evidence_storage.validate_key(row["storage_key"])
        try:
            evidence_storage.put(key, (evidence_dir / key).read_bytes(), row["media_type"])
        except evidence_storage.EvidenceStorageError:
            evidence_storage.read(key, expected_size=row["size_bytes"], expected_sha256=row["sha256"])
    with connect_product() as target:
        _assert_empty_target(target)
        target.execute("DELETE FROM news_configuration")
        for table in TABLES:
            rows = [dict(row) for row in snapshot.execute(f'SELECT * FROM "{table}"')]
            if table == "community_comments":
                rows = _ordered_comments(rows)
            for row in rows:
                if table == "news_configuration":
                    row["ingestion_lease_token"] = row["ingestion_lease_until"] = None
                if table == "journeys":
                    # Consent to LAN sharing is not consent to publish a link on the internet.
                    row["share_token"] = None
                    row["checkin_status"] = "OFF"; row["next_checkin_at"] = None
                    if row["status"] == "ACTIVE":
                        row["status"] = "COMPLETED"; row["ended_at"] = row["updated_at"] = utc_now()
                columns = list(row)
                if not all(re.fullmatch(r"[a-z_][a-z0-9_]*", column) for column in columns):
                    raise ValueError("Unexpected source column")
                names = ",".join(f'"{column}"' for column in columns)
                target.execute(f'INSERT INTO "{table}" ({names}) VALUES ({",".join("?" for _ in columns)})',
                               tuple(row[column] for column in columns))
        for table in TABLES:
            if target.execute(f'SELECT COUNT(*) FROM "{table}"').fetchone()[0] != summary["rows"][table]:
                raise ValueError("Target row count verification failed; transaction rolled back")
    return {**summary, "applied": True}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True, type=Path)
    parser.add_argument("--evidence-dir", type=Path)
    parser.add_argument("--env-file", type=Path)
    parser.add_argument("--apply", action="store_true", help="Explicitly authorise uploading existing project records/photos")
    options = parser.parse_args()
    if options.env_file:
        from .environment import load_environment_file
        load_environment_file(options.env_file, override=True)
    evidence_dir = (options.evidence_dir or options.source.resolve().parent / "evidence").resolve()
    with source_snapshot(options.source) as snapshot:
        result = apply_migration(snapshot, evidence_dir) if options.apply else preflight(snapshot, evidence_dir)
        print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
