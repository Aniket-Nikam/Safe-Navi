import hashlib
import os
import sqlite3
from pathlib import Path

import pytest
from app import product_database as products
from app.migrate_cloud import source_snapshot, preflight, apply_migration


def source_project(tmp_path, monkeypatch):
    source, evidence = tmp_path / "source.sqlite", tmp_path / "source-evidence"
    with monkeypatch.context() as settings:
        settings.delenv("SAFE_NAVI_DATABASE_URL", raising=False)
        settings.setenv("SAFE_NAVI_PRODUCT_DB", str(source))
        settings.setenv("SAFE_NAVI_EVIDENCE_DIR", str(evidence))
        settings.setenv("SAFE_NAVI_EVIDENCE_BACKEND", "local")
        owner = products.create_user("Migration owner", "migration@source.test", "migration-test-password")
        official = products.create_user("Migration official", "official@source.test", "migration-test-password", "government")
        old_token, _ = products.login(owner["email"], "migration-test-password")
        attachment = products.store_evidence(owner["id"], "fixture.jpg", "image/jpeg", b"migration-fixture-bytes")
        report = products.create_report(owner["id"], dict(title="Migration case", category_key="POOR_LIGHTING",
            description="Fixture case only", latitude=19.0657, longitude=72.9986, address="Test location",
            evidence_ids=[attachment["id"]]))
        products.set_under_review(report["id"], official["id"], "Fixture review")
        published = products.decide_report(report["id"], official["id"], dict(decision="VERIFY", severity="HIGH",
            road_status="UNSAFE", reason="Fixture verification"))
        post = products.create_community_post(owner["id"], "general", "Migration test discussion")
        root = products.create_community_comment(post["id"], owner["id"], "Root")
        products.create_community_comment(post["id"], owner["id"], "Reply", root["id"])
        journey = products.create_journey(owner["id"], dict(source_label="A", destination_label="B",
            travel_mode="DRIVING", route_profile="BALANCED", route_summary="Migration fixture"))
    return source, evidence, owner, old_token, attachment, report, published, journey


def test_migration_preflight_is_read_only_and_detects_missing_evidence(tmp_path, monkeypatch):
    source, evidence, *_ = source_project(tmp_path, monkeypatch)
    before = hashlib.sha256(source.read_bytes()).hexdigest()
    with source_snapshot(source) as snapshot:
        summary = preflight(snapshot, evidence)
        assert summary["rows"]["users"] == 2
        assert summary["rows"]["community_comments"] == 2
        assert summary["evidence_bytes"] == len(b"migration-fixture-bytes")
        assert summary["source_modified"] is False
        with pytest.raises(ValueError, match="Missing source evidence"):
            preflight(snapshot, tmp_path / "missing-folder")
    assert hashlib.sha256(source.read_bytes()).hexdigest() == before


@pytest.mark.skipif(not os.getenv("SAFE_NAVI_TEST_DATABASE_URL"), reason="Requires isolated loopback PostgreSQL")
def test_atomic_cloud_migration_keeps_cases_threads_and_photos_but_revokes_sharing(tmp_path, monkeypatch, private_storage):
    source, evidence, owner, old_token, attachment, report, published, journey = source_project(tmp_path, monkeypatch)
    before = hashlib.sha256(source.read_bytes()).hexdigest()
    with source_snapshot(source) as snapshot:
        result = apply_migration(snapshot, evidence)
    assert result["applied"] is True
    assert hashlib.sha256(source.read_bytes()).hexdigest() == before
    assert products.user_for_token(old_token) is None
    with products.connect_product() as connection:
        assert connection.execute("SELECT COUNT(*) FROM sessions").fetchone()[0] == 0
        migrated = connection.execute("SELECT * FROM journeys WHERE id=?", (journey["id"],)).fetchone()
        assert migrated["share_token"] is None
        assert migrated["status"] == "COMPLETED"
        assert connection.execute("SELECT COUNT(*) FROM hazard_history").fetchone()[0] > 0
        row = connection.execute("SELECT * FROM report_evidence WHERE id=?", (attachment["id"],)).fetchone()
    assert private_storage["objects"][row["storage_key"]] == b"migration-fixture-bytes"
    token, migrated_owner = products.login(owner["email"], "migration-test-password")
    assert token and products.report_detail(report["id"], migrated_owner)["status"] == "VERIFIED"
    assert len(products.active_hazards()) == 1
    assert products.list_community_posts(owner["id"])[0]["comments_count"] == 2
    with source_snapshot(source) as snapshot:
        with pytest.raises(ValueError, match="not empty"):
            apply_migration(snapshot, evidence)


@pytest.mark.skipif(not os.getenv("SAFE_NAVI_TEST_DATABASE_URL"), reason="Requires isolated loopback PostgreSQL")
def test_private_cloud_tables_are_not_accessible_to_supabase_public_roles(monkeypatch):
    import psycopg
    from psycopg import sql
    products.create_user("Private", "private@database.test", "private-test-password")
    url = os.environ["SAFE_NAVI_TEST_DATABASE_URL"]
    with psycopg.connect(url, autocommit=True) as connection:
        for role in ("anon", "authenticated"):
            if not connection.execute("SELECT 1 FROM pg_roles WHERE rolname=%s", (role,)).fetchone():
                connection.execute(sql.SQL("CREATE ROLE {} NOLOGIN").format(sql.Identifier(role)))
    # Re-apply grants after introducing the Supabase-equivalent test roles.
    from app.cloud_database import PostgresProductConnection
    with PostgresProductConnection() as connection:
        connection.protect_schema()
    for role in ("anon", "authenticated"):
        with pytest.raises(psycopg.errors.InsufficientPrivilege):
            with psycopg.connect(url) as connection:
                connection.execute(sql.SQL("SET LOCAL ROLE {}").format(sql.Identifier(role)))
                connection.execute(sql.SQL("SELECT * FROM {}.users").format(sql.Identifier(os.environ["SAFE_NAVI_DATABASE_SCHEMA"])))
