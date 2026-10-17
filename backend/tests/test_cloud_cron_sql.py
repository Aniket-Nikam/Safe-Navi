"""Validate deployment SQL/HMAC on real Postgres with pg_cron/pg_net stand-ins.

This does not claim that hosted Supabase extensions or Render are already deployed.
"""
import os
from pathlib import Path

import pytest
from fastapi.testclient import TestClient
from app import main
from app import product_database as products


@pytest.mark.skipif(not os.getenv("SAFE_NAVI_TEST_DATABASE_URL"), reason="Requires isolated loopback PostgreSQL")
def test_scheduler_sql_queues_scoped_signature_and_respects_persistent_due_time(monkeypatch):
    import psycopg
    products.ensure_schema()
    secret = "isolated-cron-test-signing-key-32-characters"
    monkeypatch.setenv("SAFE_NAVI_SCHEDULER_KEY", secret)
    url, schema = os.environ["SAFE_NAVI_TEST_DATABASE_URL"], os.environ["SAFE_NAVI_DATABASE_SCHEMA"]
    with psycopg.connect(url, autocommit=True) as connection:
        connection.execute("CREATE SCHEMA IF NOT EXISTS extensions")
        connection.execute("CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA extensions")
        connection.execute("CREATE SCHEMA IF NOT EXISTS cron; CREATE SCHEMA IF NOT EXISTS net; CREATE SCHEMA IF NOT EXISTS vault")
        connection.execute("CREATE TABLE IF NOT EXISTS vault.decrypted_secrets(name TEXT PRIMARY KEY,decrypted_secret TEXT)")
        connection.execute("INSERT INTO vault.decrypted_secrets VALUES ('safenavi_backend_origin','https://fixture.onrender.com'),"
                           "('safenavi_scheduler_key',%s) ON CONFLICT(name) DO UPDATE SET decrypted_secret=excluded.decrypted_secret", (secret,))
        connection.execute("CREATE TABLE IF NOT EXISTS cron.jobs(jobname TEXT PRIMARY KEY,schedule TEXT,command TEXT)")
        connection.execute("CREATE TABLE IF NOT EXISTS net.requests(url TEXT,headers JSONB,body JSONB,timeout_ms INT)")
        connection.execute("TRUNCATE net.requests")
        connection.execute("""CREATE OR REPLACE FUNCTION cron.schedule(TEXT,TEXT,TEXT) RETURNS BIGINT LANGUAGE SQL AS $$
            WITH saved AS (INSERT INTO cron.jobs VALUES($1,$2,$3) ON CONFLICT(jobname)
                DO UPDATE SET schedule=excluded.schedule,command=excluded.command RETURNING 1)
            SELECT 1::bigint FROM saved $$""")
        connection.execute("""CREATE OR REPLACE FUNCTION net.http_post(url TEXT,headers JSONB,body JSONB,timeout_milliseconds INT)
            RETURNS BIGINT LANGUAGE SQL AS $$
            WITH queued AS (INSERT INTO net.requests VALUES($1,$2,$3,$4) RETURNING 1)
            SELECT 1::bigint FROM queued $$""")
        deployment = Path(__file__).resolve().parents[2] / "deploy" / "supabase_news_schedule.sql"
        script = deployment.read_text().replace("safenavi.news_configuration", f'"{schema}".news_configuration')
        connection.execute(script); connection.execute(script)
        assert connection.execute("SELECT COUNT(*) FROM cron.jobs").fetchone()[0] == 1
        command = connection.execute("SELECT command FROM cron.jobs").fetchone()[0]
        connection.execute(command)
        request = connection.execute("SELECT url,headers,timeout_ms FROM net.requests").fetchone()
        assert request[0] == "https://fixture.onrender.com/api/v1/internal/news/tick"
        assert secret not in str(request[1])
        assert request[2] == 120000
        assert TestClient(main.app).post("/api/v1/internal/news/tick", headers=request[1]).status_code == 200
        products.record_news_ingestion()
        connection.execute(command)
        assert connection.execute("SELECT COUNT(*) FROM net.requests").fetchone()[0] == 1
