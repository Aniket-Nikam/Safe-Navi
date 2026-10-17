"""Tests never inherit real cloud credentials, feeds or product/evidence paths."""
import os
import uuid
from urllib.parse import urlsplit

import pytest
import json
import httpx
from app import evidence_storage as storage


@pytest.fixture
def private_storage(monkeypatch, safe_storage_environment):
    monkeypatch.setenv("SAFE_NAVI_EVIDENCE_BACKEND", "supabase")
    monkeypatch.setenv("SAFE_NAVI_SUPABASE_URL", "https://storage.example.test")
    monkeypatch.setenv("SAFE_NAVI_SUPABASE_SERVICE_KEY", "server-only-test-key")
    state = {"objects": {}, "public": False, "fail_upload": False, "requests": []}
    def handle(request):
        state["requests"].append(request)
        assert request.headers["apikey"] == "server-only-test-key"
        assert request.headers["authorization"] == "Bearer server-only-test-key"
        if "/bucket/" in request.url.path:
            return httpx.Response(200, json={"public": state["public"]})
        key = request.url.path.rsplit("/", 1)[-1]
        if request.method == "POST":
            if state["fail_upload"]: return httpx.Response(503)
            if key in state["objects"]: return httpx.Response(409)
            state["objects"][key] = request.content
            return httpx.Response(200, json={"Key": key})
        if request.method == "GET":
            return httpx.Response(200, content=state["objects"][key]) if key in state["objects"] else httpx.Response(404)
        if request.method == "DELETE":
            for prefix in json.loads(request.content)["prefixes"]:
                state["objects"].pop(prefix, None)
            return httpx.Response(200, json=[])
        raise AssertionError(request)
    monkeypatch.setattr(storage, "_client", lambda *args: httpx.Client(transport=httpx.MockTransport(handle)))
    return state


@pytest.fixture(autouse=True)
def safe_storage_environment(tmp_path, monkeypatch):
    monkeypatch.setenv("SAFE_NAVI_PRODUCT_DB", str(tmp_path / "product.sqlite"))
    monkeypatch.setenv("SAFE_NAVI_EVIDENCE_DIR", str(tmp_path / "evidence"))
    monkeypatch.setenv("SAFE_NAVI_EVIDENCE_BACKEND", "local")
    monkeypatch.setenv("SAFE_NAVI_CLOUD_MODE", "false")
    monkeypatch.setenv("SAFE_NAVI_NEWS_FEEDS", "")
    monkeypatch.setenv("SAFE_NAVI_NEWS_SCHEDULER", "internal")
    monkeypatch.setenv("SAFE_NAVI_SCHEDULER_KEY", "")
    monkeypatch.delenv("SAFE_NAVI_ML_ENDPOINT", raising=False)
    monkeypatch.delenv("SAFE_NAVI_SUPABASE_URL", raising=False)
    monkeypatch.delenv("SAFE_NAVI_SUPABASE_SERVICE_KEY", raising=False)
    monkeypatch.delenv("SAFE_NAVI_DATABASE_URL", raising=False)
    test_url = os.getenv("SAFE_NAVI_TEST_DATABASE_URL", "")
    schema = None
    if test_url:
        if urlsplit(test_url).hostname not in {"127.0.0.1", "localhost", "::1"}:
            pytest.fail("PostgreSQL tests require an isolated LOOPBACK database, never a hosted project")
        schema = "safenavi_test_" + uuid.uuid4().hex[:16]
        monkeypatch.setenv("SAFE_NAVI_DATABASE_URL", test_url)
        monkeypatch.setenv("SAFE_NAVI_DATABASE_SCHEMA", schema)
    yield
    if schema:
        import psycopg
        from psycopg import sql
        with psycopg.connect(test_url, autocommit=True) as connection:
            connection.execute(sql.SQL("DROP SCHEMA IF EXISTS {} CASCADE").format(sql.Identifier(schema)))
