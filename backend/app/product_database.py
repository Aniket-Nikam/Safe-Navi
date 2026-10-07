from __future__ import annotations

import hashlib
import hmac
import json
import os
import secrets
import sqlite3
import threading
import uuid
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any


DEFAULT_PRODUCT_DB = Path(__file__).resolve().parents[1] / "data" / "safe_navi_product.sqlite"
PBKDF2_ITERATIONS = 310_000
SESSION_DAYS = 30
_initialized: set[Path] = set()
_schema_lock = threading.Lock()


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def product_db_path() -> Path:
    return Path(os.getenv("SAFE_NAVI_PRODUCT_DB", str(DEFAULT_PRODUCT_DB))).resolve()


def connect_product() -> sqlite3.Connection:
    path = product_db_path()
    ensure_schema(path)
    connection = sqlite3.connect(path)
    connection.row_factory = sqlite3.Row
    connection.execute("PRAGMA foreign_keys=ON")
    return connection


def ensure_schema(path: Path | None = None) -> None:
    path = path or product_db_path()
    if path in _initialized and path.exists():
        return
    with _schema_lock:
        if path in _initialized and path.exists():
            return
        path.parent.mkdir(parents=True, exist_ok=True)
        with sqlite3.connect(path) as connection:
            connection.execute("PRAGMA foreign_keys=ON")
            connection.executescript(
                """
                CREATE TABLE IF NOT EXISTS users (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    email TEXT NOT NULL UNIQUE COLLATE NOCASE,
                    password_hash TEXT NOT NULL,
                    role TEXT NOT NULL CHECK(role IN ('citizen','government','admin')),
                    active INTEGER NOT NULL DEFAULT 1,
                    created_at TEXT NOT NULL
                );
                CREATE TABLE IF NOT EXISTS sessions (
                    token_hash TEXT PRIMARY KEY,
                    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                    expires_at TEXT NOT NULL,
                    created_at TEXT NOT NULL
                );
                CREATE TABLE IF NOT EXISTS reports (
                    id TEXT PRIMARY KEY,
                    citizen_id TEXT NOT NULL REFERENCES users(id),
                    title TEXT NOT NULL,
                    category_key TEXT NOT NULL,
                    description TEXT NOT NULL,
                    latitude REAL NOT NULL,
                    longitude REAL NOT NULL,
                    address TEXT NOT NULL,
                    evidence_url TEXT,
                    status TEXT NOT NULL,
                    linked_hazard_id TEXT,
                    client_request_id TEXT,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                );
                CREATE INDEX IF NOT EXISTS reports_status_idx ON reports(status, updated_at);
                CREATE INDEX IF NOT EXISTS reports_citizen_idx ON reports(citizen_id, created_at);
                CREATE TABLE IF NOT EXISTS hazards (
                    id TEXT PRIMARY KEY,
                    report_id TEXT REFERENCES reports(id),
                    title TEXT NOT NULL,
                    category_key TEXT NOT NULL,
                    description TEXT NOT NULL,
                    latitude REAL NOT NULL,
                    longitude REAL NOT NULL,
                    address TEXT NOT NULL,
                    severity TEXT NOT NULL,
                    status TEXT NOT NULL,
                    road_status TEXT NOT NULL,
                    reason TEXT NOT NULL,
                    verified_by TEXT NOT NULL REFERENCES users(id),
                    source TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    resolved_at TEXT,
                    valid_until TEXT,
                    geometry_type TEXT NOT NULL DEFAULT 'POINT',
                    geometry_json TEXT
                );
                CREATE INDEX IF NOT EXISTS hazards_map_idx ON hazards(status, latitude, longitude);
                CREATE TABLE IF NOT EXISTS hazard_history (
                    id TEXT PRIMARY KEY,
                    hazard_id TEXT REFERENCES hazards(id),
                    report_id TEXT REFERENCES reports(id),
                    actor_id TEXT NOT NULL REFERENCES users(id),
                    action TEXT NOT NULL,
                    previous_status TEXT,
                    new_status TEXT,
                    reason TEXT NOT NULL,
                    created_at TEXT NOT NULL
                );
                CREATE INDEX IF NOT EXISTS history_hazard_idx ON hazard_history(hazard_id, created_at);
                CREATE TABLE IF NOT EXISTS community_posts (
                    id TEXT PRIMARY KEY,
                    author_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                    category TEXT NOT NULL,
                    content TEXT NOT NULL,
                    pinned INTEGER NOT NULL DEFAULT 0,
                    removed INTEGER NOT NULL DEFAULT 0,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                );
                CREATE INDEX IF NOT EXISTS community_posts_created_idx
                    ON community_posts(removed, pinned, created_at);
                CREATE TABLE IF NOT EXISTS community_votes (
                    post_id TEXT NOT NULL REFERENCES community_posts(id) ON DELETE CASCADE,
                    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                    created_at TEXT NOT NULL,
                    PRIMARY KEY(post_id, user_id)
                );
                CREATE TABLE IF NOT EXISTS community_comments (
                    id TEXT PRIMARY KEY,
                    post_id TEXT NOT NULL REFERENCES community_posts(id) ON DELETE CASCADE,
                    author_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                    content TEXT NOT NULL,
                    removed INTEGER NOT NULL DEFAULT 0,
                    created_at TEXT NOT NULL
                );
                CREATE INDEX IF NOT EXISTS community_comments_post_idx
                    ON community_comments(post_id, created_at);
                CREATE TABLE IF NOT EXISTS report_evidence (
                    id TEXT PRIMARY KEY,
                    report_id TEXT REFERENCES reports(id) ON DELETE CASCADE,
                    owner_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                    storage_key TEXT NOT NULL,
                    media_type TEXT NOT NULL,
                    original_name TEXT NOT NULL,
                    size_bytes INTEGER NOT NULL,
                    sha256 TEXT NOT NULL,
                    created_at TEXT NOT NULL
                );
                CREATE TABLE IF NOT EXISTS report_messages (
                    id TEXT PRIMARY KEY,
                    report_id TEXT NOT NULL REFERENCES reports(id) ON DELETE CASCADE,
                    author_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                    content TEXT NOT NULL,
                    evidence_id TEXT REFERENCES report_evidence(id),
                    created_at TEXT NOT NULL
                );
                CREATE INDEX IF NOT EXISTS report_messages_report_idx
                    ON report_messages(report_id, created_at);
                CREATE TABLE IF NOT EXISTS report_confirmations (
                    report_id TEXT NOT NULL REFERENCES reports(id) ON DELETE CASCADE,
                    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                    created_at TEXT NOT NULL,
                    PRIMARY KEY(report_id, user_id)
                );
                CREATE TABLE IF NOT EXISTS notifications (
                    id TEXT PRIMARY KEY,
                    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                    kind TEXT NOT NULL,
                    title TEXT NOT NULL,
                    message TEXT NOT NULL,
                    entity_type TEXT,
                    entity_id TEXT,
                    read_at TEXT,
                    created_at TEXT NOT NULL
                );
                CREATE INDEX IF NOT EXISTS notifications_user_idx ON notifications(user_id, read_at, created_at);
                CREATE TABLE IF NOT EXISTS news_signals (
                    id TEXT PRIMARY KEY,
                    publisher TEXT NOT NULL,
                    source_url TEXT NOT NULL UNIQUE,
                    headline TEXT NOT NULL,
                    summary TEXT NOT NULL,
                    published_at TEXT NOT NULL,
                    latitude REAL,
                    longitude REAL,
                    location_text TEXT,
                    category TEXT NOT NULL,
                    confidence REAL NOT NULL,
                    content_hash TEXT NOT NULL UNIQUE,
                    review_status TEXT NOT NULL DEFAULT 'UNVERIFIED',
                    linked_hazard_id TEXT REFERENCES hazards(id),
                    created_at TEXT NOT NULL
                );
                CREATE INDEX IF NOT EXISTS news_review_idx ON news_signals(review_status, published_at);
                CREATE TABLE IF NOT EXISTS news_configuration (
                    singleton_id INTEGER PRIMARY KEY CHECK(singleton_id=1),
                    refresh_hours INTEGER NOT NULL CHECK(refresh_hours IN (12,24)),
                    last_ingested_at TEXT,
                    updated_at TEXT NOT NULL,
                    updated_by TEXT REFERENCES users(id)
                );
                INSERT OR IGNORE INTO news_configuration(singleton_id,refresh_hours,updated_at)
                    VALUES (1,12,CURRENT_TIMESTAMP);
                CREATE TABLE IF NOT EXISTS saved_places (
                    id TEXT PRIMARY KEY,
                    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                    label TEXT NOT NULL,
                    address TEXT NOT NULL,
                    latitude REAL NOT NULL,
                    longitude REAL NOT NULL,
                    created_at TEXT NOT NULL
                );
                CREATE INDEX IF NOT EXISTS saved_places_user_idx ON saved_places(user_id, created_at);
                CREATE TABLE IF NOT EXISTS emergency_contacts (
                    id TEXT PRIMARY KEY,
                    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                    name TEXT NOT NULL,
                    phone TEXT NOT NULL,
                    relationship TEXT NOT NULL,
                    created_at TEXT NOT NULL
                );
                CREATE INDEX IF NOT EXISTS emergency_contacts_user_idx ON emergency_contacts(user_id, created_at);
                CREATE TABLE IF NOT EXISTS journeys (
                    id TEXT PRIMARY KEY,
                    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                    source_label TEXT NOT NULL,
                    destination_label TEXT NOT NULL,
                    destination_latitude REAL,
                    destination_longitude REAL,
                    travel_mode TEXT NOT NULL,
                    route_profile TEXT NOT NULL,
                    route_summary TEXT NOT NULL,
                    status TEXT NOT NULL,
                    share_token TEXT UNIQUE,
                    last_latitude REAL,
                    last_longitude REAL,
                    started_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    ended_at TEXT
                );
                CREATE INDEX IF NOT EXISTS journeys_user_idx ON journeys(user_id, started_at);
                """
            )
            _ensure_column(connection, "reports", "anonymous INTEGER NOT NULL DEFAULT 0")
            _ensure_column(connection, "reports", "assigned_department TEXT")
            _ensure_column(connection, "reports", "assigned_to TEXT")
            _ensure_column(connection, "reports", "requested_info TEXT")
            _ensure_column(connection, "reports", "due_at TEXT")
            _ensure_column(connection, "reports", "client_request_id TEXT")
            _ensure_column(connection, "community_posts", "evidence_id TEXT")
            connection.execute("CREATE UNIQUE INDEX IF NOT EXISTS reports_client_request_idx ON reports(citizen_id,client_request_id) WHERE client_request_id IS NOT NULL")
            _ensure_column(connection, "hazards", "geometry_type TEXT NOT NULL DEFAULT 'POINT'")
            _ensure_column(connection, "hazards", "geometry_json TEXT")
            _ensure_column(connection, "hazards", "no_go_radius_m REAL")
            _ensure_column(connection, "journeys", "checkin_interval_minutes INTEGER")
            _ensure_column(connection, "journeys", "last_checkin_at TEXT")
            _ensure_column(connection, "journeys", "next_checkin_at TEXT")
            _ensure_column(connection, "journeys", "checkin_status TEXT NOT NULL DEFAULT 'OFF'")
        _initialized.add(path)


def _ensure_column(connection: sqlite3.Connection, table: str, definition: str) -> None:
    name = definition.split()[0]
    columns = {row[1] for row in connection.execute(f"PRAGMA table_info({table})")}
    if name not in columns:
        connection.execute(f"ALTER TABLE {table} ADD COLUMN {definition}")


def _password_hash(password: str, salt: bytes | None = None) -> str:
    salt = salt or secrets.token_bytes(16)
    digest = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt, PBKDF2_ITERATIONS)
    return f"pbkdf2_sha256${PBKDF2_ITERATIONS}${salt.hex()}${digest.hex()}"


def _password_matches(password: str, encoded: str) -> bool:
    try:
        algorithm, iterations, salt_hex, expected_hex = encoded.split("$", 3)
        if algorithm != "pbkdf2_sha256":
            return False
        actual = hashlib.pbkdf2_hmac(
            "sha256", password.encode("utf-8"), bytes.fromhex(salt_hex), int(iterations)
        )
        return hmac.compare_digest(actual.hex(), expected_hex)
    except (ValueError, TypeError):
        return False


def public_user(row: sqlite3.Row) -> dict[str, Any]:
    return {"id": row["id"], "name": row["name"], "email": row["email"], "role": row["role"]}


def create_user(name: str, email: str, password: str, role: str = "citizen") -> dict[str, Any]:
    if role not in {"citizen", "government", "admin"}:
        raise ValueError("Invalid role")
    if len(password) < 10:
        raise ValueError("Password must contain at least 10 characters")
    user_id = f"usr_{uuid.uuid4().hex}"
    with connect_product() as connection:
        try:
            connection.execute(
                "INSERT INTO users(id,name,email,password_hash,role,created_at) VALUES (?,?,?,?,?,?)",
                (user_id, name.strip(), email.strip().lower(), _password_hash(password), role, utc_now()),
            )
        except sqlite3.IntegrityError as error:
            raise ValueError("An account already exists for this email") from error
        row = connection.execute("SELECT * FROM users WHERE id=?", (user_id,)).fetchone()
        return public_user(row)


def login(email: str, password: str) -> tuple[str, dict[str, Any]] | None:
    with connect_product() as connection:
        row = connection.execute(
            "SELECT * FROM users WHERE email=? COLLATE NOCASE AND active=1", (email.strip(),)
        ).fetchone()
        if row is None or not _password_matches(password, row["password_hash"]):
            return None
        token = secrets.token_urlsafe(32)
        connection.execute(
            "INSERT INTO sessions(token_hash,user_id,expires_at,created_at) VALUES (?,?,?,?)",
            (
                hashlib.sha256(token.encode()).hexdigest(),
                row["id"],
                (datetime.now(timezone.utc) + timedelta(days=SESSION_DAYS)).isoformat(),
                utc_now(),
            ),
        )
        return token, public_user(row)


def user_for_token(token: str) -> dict[str, Any] | None:
    token_hash = hashlib.sha256(token.encode()).hexdigest()
    with connect_product() as connection:
        row = connection.execute(
            """
            SELECT u.* FROM sessions s JOIN users u ON u.id=s.user_id
            WHERE s.token_hash=? AND s.expires_at>? AND u.active=1
            """,
            (token_hash, utc_now()),
        ).fetchone()
        return public_user(row) if row else None


def revoke_session(token: str) -> bool:
    token_hash = hashlib.sha256(token.encode()).hexdigest()
    with connect_product() as connection:
        return connection.execute("DELETE FROM sessions WHERE token_hash=?", (token_hash,)).rowcount > 0


def change_password(user_id: str, current_password: str, new_password: str) -> bool:
    if len(new_password) < 10: raise ValueError("Password must contain at least 10 characters")
    with connect_product() as connection:
        row = connection.execute("SELECT password_hash FROM users WHERE id=? AND active=1", (user_id,)).fetchone()
        if row is None or not _password_matches(current_password, row["password_hash"]): return False
        connection.execute("UPDATE users SET password_hash=? WHERE id=?", (_password_hash(new_password), user_id))
        connection.execute("DELETE FROM sessions WHERE user_id=?", (user_id,))
        return True


def deactivate_account(user_id: str, password: str) -> bool:
    with connect_product() as connection:
        row = connection.execute("SELECT password_hash,role FROM users WHERE id=? AND active=1", (user_id,)).fetchone()
        if row is None or row["role"] != "citizen" or not _password_matches(password, row["password_hash"]): return False
        connection.execute("DELETE FROM sessions WHERE user_id=?", (user_id,))
        connection.execute("UPDATE users SET active=0,name='Deleted citizen',email=?,password_hash=? WHERE id=?",
                           (f"deleted-{user_id}@invalid.local", _password_hash(secrets.token_urlsafe(32)), user_id))
        return True


def row_dict(row: sqlite3.Row) -> dict[str, Any]:
    return dict(row)


def create_report(user_id: str, payload: dict[str, Any]) -> dict[str, Any]:
    report_id = f"rpt_{uuid.uuid4().hex}"
    now = utc_now()
    with connect_product() as connection:
        client_request_id = payload.get("client_request_id")
        if client_request_id:
            existing = connection.execute(
                "SELECT * FROM reports WHERE citizen_id=? AND client_request_id=?", (user_id, client_request_id)
            ).fetchone()
            if existing: return row_dict(existing)
        connection.execute(
            """
            INSERT INTO reports(id,citizen_id,title,category_key,description,latitude,longitude,
                                address,evidence_url,status,created_at,updated_at,client_request_id)
            VALUES (?,?,?,?,?,?,?,?,?,'REPORTED',?,?,?)
            """,
            (
                report_id, user_id, payload["title"], payload["category_key"], payload["description"],
                payload["latitude"], payload["longitude"], payload["address"], payload.get("evidence_url"),
                now, now, client_request_id,
            ),
        )
        if payload.get("anonymous"):
            connection.execute("UPDATE reports SET anonymous=1 WHERE id=?", (report_id,))
        for evidence_id in payload.get("evidence_ids") or []:
            connection.execute(
                "UPDATE report_evidence SET report_id=? WHERE id=? AND owner_id=? AND report_id IS NULL",
                (report_id, evidence_id, user_id),
            )
        for reviewer in connection.execute("SELECT id FROM users WHERE role IN ('government','admin') AND active=1"):
            _notify(connection, reviewer["id"], "NEW_REPORT", "New citizen report", payload["title"], "report", report_id)
        row = connection.execute("SELECT * FROM reports WHERE id=?", (report_id,)).fetchone()
        return row_dict(row)


def list_reports(user: dict[str, Any], status: str | None = None, category: str | None = None,
                 department: str | None = None, assigned_to: str | None = None,
                 ward: str | None = None, severity: str | None = None,
                 older_than_hours: int | None = None, overdue: bool = False) -> list[dict[str, Any]]:
    query = "SELECT r.*,h.severity AS hazard_severity FROM reports r LEFT JOIN hazards h ON h.report_id=r.id"
    params: list[Any] = []
    clauses = []
    if user["role"] == "citizen":
        clauses.append("r.citizen_id=?")
        params.append(user["id"])
    if status:
        clauses.append("r.status=?")
        params.append(status)
    if category:
        clauses.append("r.category_key=?")
        params.append(category)
    if department:
        clauses.append("r.assigned_department=?")
        params.append(department)
    if assigned_to:
        clauses.append("r.assigned_to LIKE ?"); params.append(f"%{assigned_to}%")
    if ward:
        clauses.append("r.address LIKE ?"); params.append(f"%{ward}%")
    if severity:
        clauses.append("h.severity=?"); params.append(severity)
    if older_than_hours:
        clauses.append("r.created_at<=?"); params.append((datetime.now(timezone.utc) - timedelta(hours=older_than_hours)).isoformat())
    if overdue:
        clauses.append("r.due_at IS NOT NULL AND r.due_at<? AND r.status NOT IN ('VERIFIED','REJECTED','DUPLICATE')")
        params.append(utc_now())
    if clauses:
        query += " WHERE " + " AND ".join(clauses)
    query += " ORDER BY r.updated_at DESC"
    with connect_product() as connection:
        return [row_dict(row) for row in connection.execute(query, params)]


def set_under_review(report_id: str, actor_id: str, reason: str) -> dict[str, Any] | None:
    now = utc_now()
    with connect_product() as connection:
        report = connection.execute("SELECT * FROM reports WHERE id=?", (report_id,)).fetchone()
        if report is None:
            return None
        connection.execute(
            "UPDATE reports SET status='UNDER_REVIEW',updated_at=? WHERE id=?", (now, report_id)
        )
        connection.execute(
            "INSERT INTO hazard_history VALUES (?,?,?,?,?,?,?,?,?)",
            (f"hst_{uuid.uuid4().hex}", None, report_id, actor_id, "REPORT_TRIAGED", report["status"],
             "UNDER_REVIEW", reason, now),
        )
        _notify(connection, report["citizen_id"], "REPORT_TRIAGED", "Report under review",
                f"{report['title']} is now under official review.", "report", report_id)
        return row_dict(connection.execute("SELECT * FROM reports WHERE id=?", (report_id,)).fetchone())


def decide_report(report_id: str, actor_id: str, payload: dict[str, Any]) -> dict[str, Any] | None:
    now = utc_now()
    decision = payload["decision"]
    with connect_product() as connection:
        report = connection.execute("SELECT * FROM reports WHERE id=?", (report_id,)).fetchone()
        if report is None:
            return None
        if report["status"] in {"VERIFIED", "REJECTED", "DUPLICATE"}:
            raise ValueError("This report already has a final decision")
        if decision == "reject":
            new_status = "REJECTED"
            connection.execute(
                "UPDATE reports SET status=?,updated_at=? WHERE id=?", (new_status, now, report_id)
            )
            hazard = None
        elif decision == "duplicate":
            linked = payload.get("linked_hazard_id")
            if not linked or connection.execute("SELECT 1 FROM hazards WHERE id=?", (linked,)).fetchone() is None:
                raise ValueError("A valid linked_hazard_id is required")
            new_status = "DUPLICATE"
            connection.execute(
                "UPDATE reports SET status=?,linked_hazard_id=?,updated_at=? WHERE id=?",
                (new_status, linked, now, report_id),
            )
            hazard = row_dict(connection.execute("SELECT * FROM hazards WHERE id=?", (linked,)).fetchone())
        else:
            required = ("severity", "road_status", "reason")
            if any(not payload.get(field) for field in required):
                raise ValueError("Verification requires severity, road_status, and reason")
            hazard_id = f"haz_{uuid.uuid4().hex}"
            new_status = "VERIFIED"
            hazard_status = payload.get("hazard_status") or "ACTIVE"
            connection.execute(
                """
                INSERT INTO hazards(id,report_id,title,category_key,description,latitude,longitude,address,
                                    severity,status,road_status,reason,verified_by,source,created_at,updated_at,valid_until,
                                    geometry_type,geometry_json,no_go_radius_m)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,'CITIZEN_REPORT',?,?,?,?,?,?)
                """,
                (hazard_id, report_id, report["title"], report["category_key"], report["description"],
                 report["latitude"], report["longitude"], report["address"], payload["severity"],
                 hazard_status, payload["road_status"], payload["reason"], actor_id, now, now,
                 payload.get("valid_until"), payload.get("geometry_type") or "POINT",
                 write_json(payload.get("geometry_coordinates")) if payload.get("geometry_coordinates") else None,
                 payload.get("no_go_radius_m")),
            )
            connection.execute(
                "UPDATE reports SET status=?,linked_hazard_id=?,updated_at=? WHERE id=?",
                (new_status, hazard_id, now, report_id),
            )
            hazard = row_dict(connection.execute("SELECT * FROM hazards WHERE id=?", (hazard_id,)).fetchone())
        connection.execute(
            "INSERT INTO hazard_history VALUES (?,?,?,?,?,?,?,?,?)",
            (f"hst_{uuid.uuid4().hex}", hazard["id"] if hazard else None, report_id, actor_id,
             f"REPORT_{new_status}", report["status"], new_status, payload["reason"], now),
        )
        decision_message = (f"{report['title']} was verified and can now affect public safety layers."
                            if new_status == "VERIFIED" else f"{report['title']} was marked {new_status.lower()}.")
        _notify(connection, report["citizen_id"], f"REPORT_{new_status}", "Report status updated",
                decision_message, "report", report_id)
        return {"report": row_dict(connection.execute("SELECT * FROM reports WHERE id=?", (report_id,)).fetchone()),
                "hazard": hazard}


def update_hazard(hazard_id: str, actor_id: str, payload: dict[str, Any]) -> dict[str, Any] | None:
    now = utc_now()
    with connect_product() as connection:
        hazard = connection.execute("SELECT * FROM hazards WHERE id=?", (hazard_id,)).fetchone()
        if hazard is None:
            return None
        new_status = payload.get("status") or hazard["status"]
        new_severity = payload.get("severity") or hazard["severity"]
        new_road_status = payload.get("road_status") or hazard["road_status"]
        new_no_go_radius = payload.get("no_go_radius_m", hazard["no_go_radius_m"])
        resolved_at = now if new_status in {"RESOLVED", "CLOSED"} else None
        connection.execute(
            """
            UPDATE hazards SET status=?,severity=?,road_status=?,reason=?,updated_at=?,resolved_at=?,valid_until=?,
                               geometry_type=?,geometry_json=?,no_go_radius_m=?
            WHERE id=?
            """,
            (new_status, new_severity, new_road_status, payload["reason"], now, resolved_at,
             payload.get("valid_until", hazard["valid_until"]), payload.get("geometry_type", hazard["geometry_type"]),
             write_json(payload["geometry_coordinates"]) if payload.get("geometry_coordinates") else hazard["geometry_json"],
             new_no_go_radius, hazard_id),
        )
        connection.execute(
            "INSERT INTO hazard_history VALUES (?,?,?,?,?,?,?,?,?)",
            (f"hst_{uuid.uuid4().hex}", hazard_id, hazard["report_id"], actor_id, "HAZARD_UPDATED",
             hazard["status"], new_status, payload["reason"], now),
        )
        if hazard["report_id"]:
            owner = connection.execute("SELECT citizen_id,title FROM reports WHERE id=?", (hazard["report_id"],)).fetchone()
            if owner:
                _notify(connection, owner["citizen_id"], "HAZARD_UPDATED", "Verified hazard updated",
                        f"{owner['title']} is now {new_status.lower().replace('_', ' ')}.", "hazard", hazard_id)
        value = row_dict(connection.execute("SELECT * FROM hazards WHERE id=?", (hazard_id,)).fetchone())
        value["geometry_coordinates"] = json.loads(value["geometry_json"]) if value.get("geometry_json") else None
        return value


def active_hazards(bounds: tuple[float, float, float, float] | None = None) -> list[dict[str, Any]]:
    query = "SELECT * FROM hazards WHERE status IN ('ACTIVE','MONITORING','VERIFIED') AND (valid_until IS NULL OR valid_until>?)"
    params: list[Any] = [utc_now()]
    if bounds:
        min_lat, min_lng, max_lat, max_lng = bounds
        query += " AND latitude BETWEEN ? AND ? AND longitude BETWEEN ? AND ?"
        params.extend([min_lat, max_lat, min_lng, max_lng])
    query += " ORDER BY updated_at DESC"
    with connect_product() as connection:
        values = [row_dict(row) for row in connection.execute(query, params)]
        for value in values:
            value["geometry_coordinates"] = json.loads(value["geometry_json"]) if value.get("geometry_json") else None
        return values


def hazard_history(hazard_id: str) -> list[dict[str, Any]]:
    with connect_product() as connection:
        return [row_dict(row) for row in connection.execute(
            "SELECT * FROM hazard_history WHERE hazard_id=? ORDER BY created_at", (hazard_id,)
        )]


def create_community_post(author_id: str, category: str, content: str,
                          evidence_id: str | None = None) -> dict[str, Any]:
    post_id = f"pst_{uuid.uuid4().hex}"
    now = utc_now()
    with connect_product() as connection:
        if evidence_id:
            evidence = connection.execute(
                "SELECT owner_id,media_type FROM report_evidence WHERE id=?", (evidence_id,)
            ).fetchone()
            if evidence is None or evidence["owner_id"] != author_id or not evidence["media_type"].startswith("image/"):
                raise ValueError("Community attachments must be an image uploaded by the author")
        connection.execute(
            "INSERT INTO community_posts(id,author_id,category,content,evidence_id,created_at,updated_at) VALUES (?,?,?,?,?,?,?)",
            (post_id, author_id, category, content.strip(), evidence_id, now, now),
        )
        return get_community_post(connection, post_id, author_id)


def get_community_post(connection: sqlite3.Connection, post_id: str, viewer_id: str) -> dict[str, Any] | None:
    row = connection.execute(
        """
        SELECT p.id,p.category,p.content,p.evidence_id,p.pinned,p.created_at,p.updated_at,
               u.id AS author_id,u.name AS author_name,
               (SELECT COUNT(*) FROM community_votes v WHERE v.post_id=p.id) AS upvotes,
               (SELECT COUNT(*) FROM community_comments c WHERE c.post_id=p.id AND c.removed=0) AS comments_count,
               EXISTS(SELECT 1 FROM community_votes mine WHERE mine.post_id=p.id AND mine.user_id=?) AS upvoted
        FROM community_posts p JOIN users u ON u.id=p.author_id
        WHERE p.id=? AND p.removed=0
        """,
        (viewer_id, post_id),
    ).fetchone()
    return row_dict(row) if row else None


def list_community_posts(viewer_id: str, category: str | None = None, query: str | None = None) -> list[dict[str, Any]]:
    clauses = ["p.removed=0"]
    params: list[Any] = [viewer_id]
    if category and category != "all":
        clauses.append("p.category=?")
        params.append(category)
    if query:
        clauses.append("(LOWER(p.content) LIKE ? OR LOWER(u.name) LIKE ?)")
        needle = f"%{query.strip().lower()}%"
        params.extend([needle, needle])
    sql = f"""
        SELECT p.id,p.category,p.content,p.evidence_id,p.pinned,p.created_at,p.updated_at,
               u.id AS author_id,u.name AS author_name,
               (SELECT COUNT(*) FROM community_votes v WHERE v.post_id=p.id) AS upvotes,
               (SELECT COUNT(*) FROM community_comments c WHERE c.post_id=p.id AND c.removed=0) AS comments_count,
               EXISTS(SELECT 1 FROM community_votes mine WHERE mine.post_id=p.id AND mine.user_id=?) AS upvoted
        FROM community_posts p JOIN users u ON u.id=p.author_id
        WHERE {' AND '.join(clauses)}
        ORDER BY p.pinned DESC, p.updated_at DESC
        LIMIT 100
    """
    with connect_product() as connection:
        return [row_dict(row) for row in connection.execute(sql, params)]


def toggle_community_vote(post_id: str, user_id: str) -> dict[str, Any] | None:
    with connect_product() as connection:
        if connection.execute("SELECT 1 FROM community_posts WHERE id=? AND removed=0", (post_id,)).fetchone() is None:
            return None
        existing = connection.execute(
            "SELECT 1 FROM community_votes WHERE post_id=? AND user_id=?", (post_id, user_id)
        ).fetchone()
        if existing:
            connection.execute("DELETE FROM community_votes WHERE post_id=? AND user_id=?", (post_id, user_id))
        else:
            connection.execute(
                "INSERT INTO community_votes(post_id,user_id,created_at) VALUES (?,?,?)",
                (post_id, user_id, utc_now()),
            )
        return get_community_post(connection, post_id, user_id)


def create_community_comment(post_id: str, author_id: str, content: str) -> dict[str, Any] | None:
    comment_id = f"cmt_{uuid.uuid4().hex}"
    now = utc_now()
    with connect_product() as connection:
        if connection.execute("SELECT 1 FROM community_posts WHERE id=? AND removed=0", (post_id,)).fetchone() is None:
            return None
        connection.execute(
            "INSERT INTO community_comments(id,post_id,author_id,content,created_at) VALUES (?,?,?,?,?)",
            (comment_id, post_id, author_id, content.strip(), now),
        )
        connection.execute("UPDATE community_posts SET updated_at=? WHERE id=?", (now, post_id))
        row = connection.execute(
            """SELECT c.id,c.post_id,c.content,c.created_at,u.id AS author_id,u.name AS author_name
               FROM community_comments c JOIN users u ON u.id=c.author_id WHERE c.id=?""",
            (comment_id,),
        ).fetchone()
        return row_dict(row)


def list_community_comments(post_id: str) -> list[dict[str, Any]]:
    with connect_product() as connection:
        return [row_dict(row) for row in connection.execute(
            """SELECT c.id,c.post_id,c.content,c.created_at,u.id AS author_id,u.name AS author_name
               FROM community_comments c JOIN users u ON u.id=c.author_id
               WHERE c.post_id=? AND c.removed=0 ORDER BY c.created_at""",
            (post_id,),
        )]


def _can_access_report(connection: sqlite3.Connection, report_id: str,
                       user: dict[str, Any]) -> sqlite3.Row | None:
    report = connection.execute("SELECT * FROM reports WHERE id=?", (report_id,)).fetchone()
    if report is None or (user["role"] == "citizen" and report["citizen_id"] != user["id"]):
        return None
    return report


def list_report_messages(report_id: str, user: dict[str, Any]) -> list[dict[str, Any]] | None:
    with connect_product() as connection:
        if _can_access_report(connection, report_id, user) is None:
            return None
        return [row_dict(row) for row in connection.execute(
            """SELECT m.id,m.report_id,m.content,m.evidence_id,m.created_at,
                      u.id AS author_id,u.name AS author_name,u.role AS author_role
               FROM report_messages m JOIN users u ON u.id=m.author_id
               WHERE m.report_id=? ORDER BY m.created_at""", (report_id,),
        )]


def create_report_message(report_id: str, user: dict[str, Any], content: str,
                          evidence_id: str | None = None) -> dict[str, Any] | None:
    message_id, now = f"msg_{uuid.uuid4().hex}", utc_now()
    with connect_product() as connection:
        report = _can_access_report(connection, report_id, user)
        if report is None:
            return None
        if evidence_id:
            evidence = connection.execute(
                "SELECT owner_id,media_type,report_id FROM report_evidence WHERE id=?", (evidence_id,)
            ).fetchone()
            if evidence is None or evidence["owner_id"] != user["id"] or not evidence["media_type"].startswith("image/"):
                raise ValueError("Message attachment must be an image uploaded by its author")
            if evidence["report_id"] not in (None, report_id):
                raise ValueError("Evidence is already attached to another report")
            connection.execute("UPDATE report_evidence SET report_id=? WHERE id=?", (report_id, evidence_id))
        connection.execute(
            "INSERT INTO report_messages(id,report_id,author_id,content,evidence_id,created_at) VALUES (?,?,?,?,?,?)",
            (message_id, report_id, user["id"], content.strip(), evidence_id, now),
        )
        if user["role"] == "citizen":
            if report["status"] == "REQUESTED_INFO":
                connection.execute("UPDATE reports SET status='UNDER_REVIEW',updated_at=? WHERE id=?", (now, report_id))
                connection.execute("INSERT INTO hazard_history VALUES (?,?,?,?,?,?,?,?,?)", (
                    f"hst_{uuid.uuid4().hex}", None, report_id, user["id"], "CITIZEN_INFORMATION_PROVIDED",
                    "REQUESTED_INFO", "UNDER_REVIEW", "Citizen supplied requested information", now,
                ))
            for recipient in connection.execute(
                "SELECT id FROM users WHERE active=1 AND role IN ('government','admin')"
            ):
                _notify(connection, recipient["id"], "REPORT_MESSAGE", "Citizen replied to a report",
                        report["title"], "report", report_id)
        else:
            _notify(connection, report["citizen_id"], "REPORT_MESSAGE", "New official message",
                    f"An official replied about {report['title']}.", "report", report_id)
        row = connection.execute(
            """SELECT m.id,m.report_id,m.content,m.evidence_id,m.created_at,
                      u.id AS author_id,u.name AS author_name,u.role AS author_role
               FROM report_messages m JOIN users u ON u.id=m.author_id WHERE m.id=?""",
            (message_id,),
        ).fetchone()
        return row_dict(row)


def write_json(value: Any) -> str:
    return json.dumps(value, separators=(",", ":"))


def report_detail(report_id: str, user: dict[str, Any]) -> dict[str, Any] | None:
    with connect_product() as connection:
        report = connection.execute("SELECT * FROM reports WHERE id=?", (report_id,)).fetchone()
        if report is None or (user["role"] == "citizen" and report["citizen_id"] != user["id"]):
            return None
        value = row_dict(report)
        owner = connection.execute("SELECT name,email FROM users WHERE id=?", (report["citizen_id"],)).fetchone()
        value["citizen_name"] = "Anonymous citizen" if report["anonymous"] else owner["name"]
        value["citizen_email"] = None if report["anonymous"] else owner["email"]
        value["confirmations"] = connection.execute(
            "SELECT COUNT(*) FROM report_confirmations WHERE report_id=?", (report_id,)
        ).fetchone()[0]
        value["evidence"] = [row_dict(row) for row in connection.execute(
            "SELECT id,media_type,original_name,size_bytes,created_at FROM report_evidence WHERE report_id=? ORDER BY created_at",
            (report_id,),
        )]
        value["timeline"] = [row_dict(row) for row in connection.execute(
            "SELECT action,previous_status,new_status,reason,created_at FROM hazard_history WHERE report_id=? ORDER BY created_at",
            (report_id,),
        )]
        return value


def assign_report(report_id: str, actor_id: str, department: str, assigned_to: str | None, due_hours: int) -> dict[str, Any] | None:
    now = utc_now()
    due_at = (datetime.now(timezone.utc) + timedelta(hours=due_hours)).isoformat()
    with connect_product() as connection:
        report = connection.execute("SELECT * FROM reports WHERE id=?", (report_id,)).fetchone()
        if report is None: return None
        connection.execute(
            "UPDATE reports SET assigned_department=?,assigned_to=?,due_at=?,updated_at=? WHERE id=?",
            (department, assigned_to, due_at, now, report_id),
        )
        connection.execute("INSERT INTO hazard_history VALUES (?,?,?,?,?,?,?,?,?)", (
            f"hst_{uuid.uuid4().hex}", None, report_id, actor_id, "REPORT_ASSIGNED", report["status"],
            report["status"], f"Assigned to {department}" + (f" / {assigned_to}" if assigned_to else ""), now,
        ))
        _notify(connection, report["citizen_id"], "REPORT_ASSIGNED", "Report assigned",
                f"{report['title']} was assigned to {department}.", "report", report_id)
        return row_dict(connection.execute("SELECT * FROM reports WHERE id=?", (report_id,)).fetchone())


def request_report_information(report_id: str, actor_id: str, message: str) -> dict[str, Any] | None:
    now = utc_now()
    with connect_product() as connection:
        report = connection.execute("SELECT * FROM reports WHERE id=?", (report_id,)).fetchone()
        if report is None: return None
        connection.execute(
            "UPDATE reports SET status='REQUESTED_INFO',requested_info=?,updated_at=? WHERE id=?",
            (message, now, report_id),
        )
        connection.execute("INSERT INTO hazard_history VALUES (?,?,?,?,?,?,?,?,?)", (
            f"hst_{uuid.uuid4().hex}", None, report_id, actor_id, "INFORMATION_REQUESTED", report["status"],
            "REQUESTED_INFO", message, now,
        ))
        connection.execute(
            "INSERT INTO report_messages(id,report_id,author_id,content,evidence_id,created_at) VALUES (?,?,?,?,?,?)",
            (f"msg_{uuid.uuid4().hex}", report_id, actor_id, message.strip(), None, now),
        )
        _notify(connection, report["citizen_id"], "INFORMATION_REQUESTED", "More information needed", message, "report", report_id)
        return row_dict(connection.execute("SELECT * FROM reports WHERE id=?", (report_id,)).fetchone())


def confirm_report(report_id: str, user_id: str) -> dict[str, Any] | None:
    with connect_product() as connection:
        report = connection.execute("SELECT * FROM reports WHERE id=?", (report_id,)).fetchone()
        if report is None: return None
        connection.execute(
            "INSERT OR IGNORE INTO report_confirmations(report_id,user_id,created_at) VALUES (?,?,?)",
            (report_id, user_id, utc_now()),
        )
        return {"report_id": report_id, "confirmations": connection.execute(
            "SELECT COUNT(*) FROM report_confirmations WHERE report_id=?", (report_id,)
        ).fetchone()[0]}


def duplicate_candidates(report_id: str, maximum: int = 8) -> list[dict[str, Any]]:
    with connect_product() as connection:
        source = connection.execute("SELECT * FROM reports WHERE id=?", (report_id,)).fetchone()
        if source is None: return []
        candidates = connection.execute(
            """SELECT id,title,status,latitude,longitude,address,created_at FROM reports
               WHERE id<>? AND category_key=? AND status NOT IN ('REJECTED','DUPLICATE')
               AND latitude BETWEEN ? AND ? AND longitude BETWEEN ? AND ? ORDER BY created_at DESC LIMIT 30""",
            (report_id, source["category_key"], source["latitude"] - .01, source["latitude"] + .01,
             source["longitude"] - .01, source["longitude"] + .01),
        ).fetchall()
        results = []
        for row in candidates:
            distance = _haversine(source["latitude"], source["longitude"], row["latitude"], row["longitude"])
            if distance <= 750:
                value = row_dict(row); value["distance_meters"] = round(distance); results.append(value)
        return sorted(results, key=lambda item: item["distance_meters"])[:maximum]


def suggest_duplicate_reports(user_id: str, category: str, latitude: float, longitude: float,
                              maximum: int = 5) -> list[dict[str, Any]]:
    """Return privacy-safe nearby issues before a citizen creates another report."""
    with connect_product() as connection:
        candidates = connection.execute(
            """SELECT r.id,r.title,r.status,r.latitude,r.longitude,r.address,r.created_at,
                      COUNT(c.user_id) AS confirmations
               FROM reports r LEFT JOIN report_confirmations c ON c.report_id=r.id
               WHERE r.citizen_id<>? AND r.category_key=? AND r.status NOT IN ('REJECTED','DUPLICATE','CLOSED')
               AND r.latitude BETWEEN ? AND ? AND r.longitude BETWEEN ? AND ?
               GROUP BY r.id ORDER BY r.created_at DESC LIMIT 40""",
            (user_id, category, latitude - .01, latitude + .01, longitude - .01, longitude + .01),
        ).fetchall()
        results = []
        for row in candidates:
            distance = _haversine(latitude, longitude, row["latitude"], row["longitude"])
            if distance <= 750:
                value = row_dict(row); value["distance_meters"] = round(distance); results.append(value)
        return sorted(results, key=lambda item: item["distance_meters"])[:maximum]


def _haversine(lat1: float, lng1: float, lat2: float, lng2: float) -> float:
    import math
    phi1, phi2 = math.radians(lat1), math.radians(lat2)
    dphi, dlambda = math.radians(lat2 - lat1), math.radians(lng2 - lng1)
    value = math.sin(dphi / 2) ** 2 + math.cos(phi1) * math.cos(phi2) * math.sin(dlambda / 2) ** 2
    return 6_371_000 * 2 * math.atan2(math.sqrt(value), math.sqrt(1 - value))


def _notify(connection: sqlite3.Connection, user_id: str, kind: str, title: str, message: str,
            entity_type: str | None = None, entity_id: str | None = None) -> None:
    connection.execute("INSERT INTO notifications VALUES (?,?,?,?,?,?,?,?,?)", (
        f"ntf_{uuid.uuid4().hex}", user_id, kind, title, message, entity_type, entity_id, None, utc_now(),
    ))


def list_notifications(user_id: str) -> list[dict[str, Any]]:
    with connect_product() as connection:
        return [row_dict(row) for row in connection.execute(
            "SELECT * FROM notifications WHERE user_id=? ORDER BY created_at DESC LIMIT 100", (user_id,),
        )]


def mark_notification_read(notification_id: str, user_id: str) -> bool:
    with connect_product() as connection:
        cursor = connection.execute(
            "UPDATE notifications SET read_at=? WHERE id=? AND user_id=?", (utc_now(), notification_id, user_id)
        )
        return cursor.rowcount > 0


def government_metrics() -> dict[str, Any]:
    with connect_product() as connection:
        rows = connection.execute("SELECT status,COUNT(*) AS count FROM reports GROUP BY status").fetchall()
        statuses = {row["status"]: row["count"] for row in rows}
        overdue = connection.execute(
            "SELECT COUNT(*) FROM reports WHERE due_at IS NOT NULL AND due_at<? AND status NOT IN ('VERIFIED','REJECTED','DUPLICATE')",
            (utc_now(),),
        ).fetchone()[0]
        return {"reports_by_status": statuses, "overdue": overdue,
                "active_hazards": connection.execute("SELECT COUNT(*) FROM hazards WHERE status IN ('ACTIVE','MONITORING')").fetchone()[0]}


def evidence_storage_dir() -> Path:
    path = Path(os.getenv("SAFE_NAVI_EVIDENCE_DIR", str(product_db_path().parent / "evidence"))).resolve()
    path.mkdir(parents=True, exist_ok=True)
    return path


def store_evidence(owner_id: str, original_name: str, media_type: str, content: bytes) -> dict[str, Any]:
    digest = hashlib.sha256(content).hexdigest()
    extensions = {"image/jpeg": ".jpg", "image/png": ".png", "image/webp": ".webp",
                  "video/mp4": ".mp4", "video/webm": ".webm"}
    extension = extensions.get(media_type, "")
    evidence_id = f"evd_{uuid.uuid4().hex}"
    storage_key = f"{evidence_id}{extension}"
    (evidence_storage_dir() / storage_key).write_bytes(content)
    with connect_product() as connection:
        connection.execute(
            """INSERT INTO report_evidence(id,report_id,owner_id,storage_key,media_type,original_name,size_bytes,sha256,created_at)
               VALUES (?,NULL,?,?,?,?,?,?,?)""",
            (evidence_id, owner_id, storage_key, media_type, original_name[:255], len(content), digest, utc_now()),
        )
        return row_dict(connection.execute(
            "SELECT id,media_type,original_name,size_bytes,created_at FROM report_evidence WHERE id=?", (evidence_id,)
        ).fetchone())


def evidence_record(evidence_id: str, user: dict[str, Any]) -> dict[str, Any] | None:
    with connect_product() as connection:
        row = connection.execute("SELECT * FROM report_evidence WHERE id=?", (evidence_id,)).fetchone()
        if row is None: return None
        if user["role"] == "citizen" and row["owner_id"] != user["id"]:
            community_visible = connection.execute(
                "SELECT 1 FROM community_posts WHERE evidence_id=? AND removed=0", (evidence_id,)
            ).fetchone()
            report_visible = connection.execute(
                """SELECT 1 FROM reports r LEFT JOIN report_messages m ON m.report_id=r.id
                   WHERE r.citizen_id=? AND (r.id=? OR m.evidence_id=?) LIMIT 1""",
                (user["id"], row["report_id"], evidence_id),
            ).fetchone()
            if community_visible is None and report_visible is None:
                return None
        value = row_dict(row)
        value["path"] = str(evidence_storage_dir() / row["storage_key"])
        return value


def store_news_signal(value: dict[str, Any]) -> bool:
    normalized = f"{value['headline'].strip().lower()}|{value.get('summary','').strip().lower()}"
    digest = hashlib.sha256(normalized.encode("utf-8")).hexdigest()
    with connect_product() as connection:
        try:
            connection.execute(
                """INSERT INTO news_signals(id,publisher,source_url,headline,summary,published_at,latitude,longitude,
                   location_text,category,confidence,content_hash,review_status,created_at)
                   VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
                (f"news_{uuid.uuid4().hex}", value["publisher"], value["source_url"], value["headline"],
                 value.get("summary", ""), value["published_at"], value.get("latitude"), value.get("longitude"),
                 value.get("location_text"), value["category"], float(value["confidence"]), digest, "UNVERIFIED", utc_now()),
            )
            return True
        except sqlite3.IntegrityError:
            return False


def list_news_signals(review_status: str | None = None) -> list[dict[str, Any]]:
    query = "SELECT * FROM news_signals"; params: list[Any] = []
    if review_status:
        query += " WHERE review_status=?"; params.append(review_status)
    query += " ORDER BY published_at DESC LIMIT 250"
    with connect_product() as connection:
        return [row_dict(row) for row in connection.execute(query, params).fetchall()]


def review_news_signal(signal_id: str, review_status: str) -> dict[str, Any] | None:
    if review_status not in {"RELEVANT", "DISMISSED", "NEEDS_FIELD_CHECK"}: raise ValueError("Invalid review status")
    with connect_product() as connection:
        changed = connection.execute("UPDATE news_signals SET review_status=? WHERE id=?", (review_status, signal_id)).rowcount
        if not changed: return None
        return row_dict(connection.execute("SELECT * FROM news_signals WHERE id=?", (signal_id,)).fetchone())


def news_configuration() -> dict[str, Any]:
    with connect_product() as connection:
        row = connection.execute("SELECT * FROM news_configuration WHERE singleton_id=1").fetchone()
        assert row is not None
        value = row_dict(row)
    last = value.get("last_ingested_at")
    value["next_ingestion_at"] = (
        (datetime.fromisoformat(last) + timedelta(hours=int(value["refresh_hours"]))).isoformat()
        if last else None
    )
    value["scheduler_enabled"] = bool(os.getenv("SAFE_NAVI_NEWS_FEEDS", "").strip())
    return value


def update_news_configuration(refresh_hours: int, actor_id: str) -> dict[str, Any]:
    if refresh_hours not in {12, 24}:
        raise ValueError("News refresh must be 12 or 24 hours")
    with connect_product() as connection:
        connection.execute(
            "UPDATE news_configuration SET refresh_hours=?,updated_at=?,updated_by=? WHERE singleton_id=1",
            (refresh_hours, utc_now(), actor_id),
        )
    return news_configuration()


def record_news_ingestion() -> dict[str, Any]:
    with connect_product() as connection:
        connection.execute(
            "UPDATE news_configuration SET last_ingested_at=? WHERE singleton_id=1", (utc_now(),)
        )
    return news_configuration()


def news_ingestion_due() -> bool:
    value = news_configuration()
    last = value.get("last_ingested_at")
    if not last:
        return True
    return datetime.now(timezone.utc) >= datetime.fromisoformat(value["next_ingestion_at"])


def create_saved_place(user_id: str, value: dict[str, Any]) -> dict[str, Any]:
    place_id = f"plc_{uuid.uuid4().hex}"
    with connect_product() as connection:
        connection.execute("INSERT INTO saved_places VALUES (?,?,?,?,?,?,?)", (
            place_id, user_id, value["label"].strip(), value["address"].strip(),
            value["latitude"], value["longitude"], utc_now(),
        ))
        return row_dict(connection.execute("SELECT * FROM saved_places WHERE id=?", (place_id,)).fetchone())


def list_saved_places(user_id: str) -> list[dict[str, Any]]:
    with connect_product() as connection:
        return [row_dict(row) for row in connection.execute(
            "SELECT * FROM saved_places WHERE user_id=? ORDER BY created_at DESC", (user_id,)
        )]


def delete_saved_place(user_id: str, place_id: str) -> bool:
    with connect_product() as connection:
        return connection.execute("DELETE FROM saved_places WHERE id=? AND user_id=?", (place_id, user_id)).rowcount > 0


def create_emergency_contact(user_id: str, value: dict[str, Any]) -> dict[str, Any]:
    contact_id = f"ect_{uuid.uuid4().hex}"
    with connect_product() as connection:
        connection.execute("INSERT INTO emergency_contacts VALUES (?,?,?,?,?,?)", (
            contact_id, user_id, value["name"].strip(), value["phone"].strip(),
            value["relationship"].strip(), utc_now(),
        ))
        return row_dict(connection.execute("SELECT * FROM emergency_contacts WHERE id=?", (contact_id,)).fetchone())


def list_emergency_contacts(user_id: str) -> list[dict[str, Any]]:
    with connect_product() as connection:
        return [row_dict(row) for row in connection.execute(
            "SELECT * FROM emergency_contacts WHERE user_id=? ORDER BY created_at DESC", (user_id,)
        )]


def delete_emergency_contact(user_id: str, contact_id: str) -> bool:
    with connect_product() as connection:
        return connection.execute("DELETE FROM emergency_contacts WHERE id=? AND user_id=?", (contact_id, user_id)).rowcount > 0


def create_journey(user_id: str, value: dict[str, Any]) -> dict[str, Any]:
    journey_id = f"jrn_{uuid.uuid4().hex}"; now = utc_now(); share_token = secrets.token_urlsafe(24)
    with connect_product() as connection:
        connection.execute("""INSERT INTO journeys(id,user_id,source_label,destination_label,destination_latitude,
            destination_longitude,travel_mode,route_profile,route_summary,status,share_token,last_latitude,last_longitude,
            started_at,updated_at,ended_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,NULL)""", (
            journey_id, user_id, value["source_label"], value["destination_label"], value.get("destination_latitude"),
            value.get("destination_longitude"), value["travel_mode"], value["route_profile"], value["route_summary"],
            "ACTIVE", share_token, value.get("latitude"), value.get("longitude"), now, now,
        ))
        return row_dict(connection.execute("SELECT * FROM journeys WHERE id=?", (journey_id,)).fetchone())


def update_journey_location(user_id: str, journey_id: str, latitude: float, longitude: float) -> dict[str, Any] | None:
    with connect_product() as connection:
        changed = connection.execute("""UPDATE journeys SET last_latitude=?,last_longitude=?,updated_at=?
            WHERE id=? AND user_id=? AND status='ACTIVE'""", (latitude, longitude, utc_now(), journey_id, user_id)).rowcount
        if not changed: return None
        return row_dict(connection.execute("SELECT * FROM journeys WHERE id=?", (journey_id,)).fetchone())


def end_journey(user_id: str, journey_id: str) -> dict[str, Any] | None:
    now = utc_now()
    with connect_product() as connection:
        changed = connection.execute("UPDATE journeys SET status='COMPLETED',checkin_status='OFF',next_checkin_at=NULL,updated_at=?,ended_at=? WHERE id=? AND user_id=? AND status='ACTIVE'",
                                     (now, now, journey_id, user_id)).rowcount
        if not changed: return None
        return row_dict(connection.execute("SELECT * FROM journeys WHERE id=?", (journey_id,)).fetchone())


def list_journeys(user_id: str) -> list[dict[str, Any]]:
    with connect_product() as connection:
        return [row_dict(row) for row in connection.execute(
            "SELECT * FROM journeys WHERE user_id=? ORDER BY started_at DESC LIMIT 100", (user_id,)
        )]


def shared_journey(share_token: str) -> dict[str, Any] | None:
    with connect_product() as connection:
        row = connection.execute("""SELECT source_label,destination_label,travel_mode,route_profile,status,
            last_latitude,last_longitude,started_at,updated_at,ended_at,checkin_interval_minutes,
            last_checkin_at,next_checkin_at,checkin_status FROM journeys WHERE share_token=?""", (share_token,)).fetchone()
        return row_dict(row) if row else None


def schedule_journey_checkin(user_id: str, journey_id: str, interval_minutes: int | None) -> dict[str, Any] | None:
    now = datetime.now(timezone.utc)
    status = "SCHEDULED" if interval_minutes else "OFF"
    next_at = (now + timedelta(minutes=interval_minutes)).isoformat() if interval_minutes else None
    with connect_product() as connection:
        changed = connection.execute("""UPDATE journeys SET checkin_interval_minutes=?,next_checkin_at=?,
            checkin_status=?,updated_at=? WHERE id=? AND user_id=? AND status='ACTIVE'""",
            (interval_minutes, next_at, status, now.isoformat(), journey_id, user_id)).rowcount
        if not changed: return None
        return row_dict(connection.execute("SELECT * FROM journeys WHERE id=?", (journey_id,)).fetchone())


def acknowledge_journey_checkin(user_id: str, journey_id: str) -> dict[str, Any] | None:
    now = datetime.now(timezone.utc)
    with connect_product() as connection:
        row = connection.execute("SELECT checkin_interval_minutes FROM journeys WHERE id=? AND user_id=? AND status='ACTIVE'",
                                 (journey_id, user_id)).fetchone()
        if not row: return None
        interval = row["checkin_interval_minutes"]
        next_at = (now + timedelta(minutes=interval)).isoformat() if interval else None
        connection.execute("""UPDATE journeys SET last_checkin_at=?,next_checkin_at=?,checkin_status=?,updated_at=?
            WHERE id=? AND user_id=?""", (now.isoformat(), next_at, "SAFE" if interval else "OFF", now.isoformat(), journey_id, user_id))
        return row_dict(connection.execute("SELECT * FROM journeys WHERE id=?", (journey_id,)).fetchone())
