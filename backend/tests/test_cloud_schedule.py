import hashlib
import hmac
import time
from datetime import datetime, timedelta, timezone

import pytest
from fastapi.testclient import TestClient
from app import main
from app import product_database as products
from app.cloud_configuration import validate_cloud_configuration
from app.cloud_database import ProductRow, translate_sql, schema_name


def signed_headers(secret, timestamp=None):
    tick = str(int(time.time()) if timestamp is None else timestamp)
    signature = hmac.new(secret.encode(), (tick + "\nPOST\n/api/v1/internal/news/tick").encode(), hashlib.sha256).hexdigest()
    return {"X-SafeNavi-Scheduler-Timestamp": tick, "X-SafeNavi-Scheduler-Signature": signature}


def test_scheduler_requires_fresh_signature_and_only_checks_due_work(monkeypatch):
    client = TestClient(main.app); path = "/api/v1/internal/news/tick"
    assert client.post(path).status_code == 503
    secret = "private-test-scheduler-key-32-characters"
    monkeypatch.setenv("SAFE_NAVI_SCHEDULER_KEY", secret)
    assert client.post(path).status_code == 403
    assert client.post(path, headers=signed_headers("wrong-key")).status_code == 403
    assert client.post(path, headers=signed_headers(secret, int(time.time()) - 600)).status_code == 403
    assert client.post(path, headers={"X-SafeNavi-Scheduler-Timestamp": "abc"}).status_code == 403
    calls = []
    def ingest(force): calls.append(force); return {"inserted": 0, "skipped": "not_due"}
    monkeypatch.setattr(main, "ingest_configured_feeds", ingest)
    assert client.post(path, headers=signed_headers(secret)).status_code == 200
    assert calls == [False]


def test_news_lease_serializes_triggers_and_respects_admin_interval():
    token, reason = products.claim_news_ingestion(False)
    assert token and reason is None
    assert products.claim_news_ingestion(True) == (None, "already_running")
    assert "ingestion_lease_token" not in products.news_configuration()
    products.finish_news_ingestion("wrong-token", True)
    assert products.claim_news_ingestion(False)[1] == "already_running"
    products.finish_news_ingestion(token, True)
    assert products.claim_news_ingestion(False)[1] == "not_due"
    token2, _ = products.claim_news_ingestion(True)
    assert token2
    products.finish_news_ingestion(token2, False)


def test_expired_news_lease_recovers_and_old_worker_cannot_release_new_claim():
    old, _ = products.claim_news_ingestion(True)
    with products.connect_product() as connection:
        connection.execute("UPDATE news_configuration SET ingestion_lease_until=?", ((datetime.now(timezone.utc) - timedelta(minutes=1)).isoformat(),))
    new, _ = products.claim_news_ingestion(True)
    assert new and new != old
    products.finish_news_ingestion(old, True)
    assert products.claim_news_ingestion(True)[1] == "already_running"
    assert products.news_configuration()["last_ingested_at"] is None
    products.finish_news_ingestion(new, False)


def test_cloud_mode_cannot_silently_write_to_ephemeral_disk(monkeypatch):
    monkeypatch.setenv("SAFE_NAVI_CLOUD_MODE", "true")
    monkeypatch.delenv("SAFE_NAVI_DATABASE_URL", raising=False)
    with pytest.raises(ValueError, match="PostgreSQL"):
        validate_cloud_configuration()


def test_postgres_rows_preserve_mapping_sequence_and_numeric_flag_contract():
    row = ProductRow(["key", "value"], ["enabled", True])
    assert row[0] == "enabled" and row["value"] == 1
    assert dict(row) == {"key": "enabled", "value": 1}
    assert dict([row]) == {"enabled": 1}


def test_postgres_translation_keeps_literals_out_of_placeholder_and_keyword_rewrites():
    assert translate_sql("SELECT 'LIKE ? REAL', ?") == "SELECT 'LIKE ? REAL', %s"
    assert translate_sql("SELECT * FROM users WHERE email=? COLLATE NOCASE") == "SELECT * FROM users WHERE LOWER(email)=LOWER(%s)"
    assert "DOUBLE PRECISION" in translate_sql("CREATE TABLE example(latitude REAL)")
    assert translate_sql("INSERT OR IGNORE INTO example VALUES (?)").endswith("VALUES (%s) ON CONFLICT DO NOTHING")


@pytest.mark.parametrize("name", ["public", "auth", "storage", "vault", "pg_temp", "safenavi;drop_schema"])
def test_cloud_schema_is_restricted_to_dedicated_application_namespace(monkeypatch, name):
    monkeypatch.setenv("SAFE_NAVI_DATABASE_SCHEMA", name)
    with pytest.raises(ValueError): schema_name()
