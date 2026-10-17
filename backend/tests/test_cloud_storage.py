import json
import sqlite3

import httpx
import pytest
from fastapi.testclient import TestClient

from app.main import app
from app import evidence_storage as storage
from app import product_database as products




def account(client, email="owner@cloud.test"):
    value = client.post("/api/v1/auth/register", json={"name": "Cloud tester", "email": email,
        "password": "cloud-test-password"}).json()
    return value, {"Authorization": "Bearer " + value["access_token"]}


def upload(client, headers):
    return client.post("/api/v1/evidence", headers=headers, files={"file": ("road photo.jpg", b"fixture-photo-bytes", "image/jpeg")})


def test_private_cloud_upload_download_and_authorization_survive_new_api_client(private_storage, tmp_path):
    client = TestClient(app)
    owner, headers = account(client)
    response = upload(client, headers)
    assert response.status_code == 201
    assert "server-only-test-key" not in response.text
    evidence_id = response.json()["id"]
    assert not (tmp_path / "evidence").exists()
    assert "path" not in products.evidence_record(evidence_id, owner["user"])
    assert TestClient(app).get(f"/api/v1/evidence/{evidence_id}", headers=headers).content == b"fixture-photo-bytes"
    _, stranger = account(client, "stranger@cloud.test")
    before = len(private_storage["requests"])
    assert client.get(f"/api/v1/evidence/{evidence_id}", headers=stranger).status_code == 404
    assert client.get(f"/api/v1/evidence/{evidence_id}").status_code == 401
    assert len(private_storage["requests"]) == before


def test_public_bucket_is_rejected_before_upload(private_storage):
    private_storage["public"] = True
    client = TestClient(app); _, headers = account(client)
    assert upload(client, headers).status_code == 503
    assert private_storage["objects"] == {}
    with products.connect_product() as connection:
        assert connection.execute("SELECT COUNT(*) FROM report_evidence").fetchone()[0] == 0


def test_failed_storage_upload_does_not_create_dangling_metadata(private_storage):
    private_storage["fail_upload"] = True
    client = TestClient(app); _, headers = account(client)
    assert upload(client, headers).status_code == 503
    with products.connect_product() as connection:
        assert connection.execute("SELECT COUNT(*) FROM report_evidence").fetchone()[0] == 0


def test_corrupt_cloud_evidence_fails_integrity_check(private_storage):
    client = TestClient(app); _, headers = account(client)
    evidence_id = upload(client, headers).json()["id"]
    key = next(iter(private_storage["objects"]))
    private_storage["objects"][key] = b"tampered"
    response = client.get(f"/api/v1/evidence/{evidence_id}", headers=headers)
    assert response.status_code == 503
    assert "tampered" not in response.text


def test_definite_database_rejection_cleans_uploaded_object(private_storage, monkeypatch):
    owner = products.create_user("Owner", "owner@cleanup.test", "cloud-test-password")
    def fail(): raise sqlite3.IntegrityError("Simulated definite rejection")
    monkeypatch.setattr(products, "connect_product", fail)
    with pytest.raises(sqlite3.IntegrityError):
        products.store_evidence(owner["id"], "photo.jpg", "image/jpeg", b"fixture")
    assert private_storage["objects"] == {}


def test_ambiguous_commit_failure_never_deletes_potentially_referenced_object(private_storage, monkeypatch):
    owner = products.create_user("Owner", "owner@commit.test", "cloud-test-password")
    def fail(): raise RuntimeError("Simulated lost commit acknowledgement")
    monkeypatch.setattr(products, "connect_product", fail)
    with pytest.raises(RuntimeError):
        products.store_evidence(owner["id"], "photo.jpg", "image/jpeg", b"fixture")
    assert len(private_storage["objects"]) == 1


@pytest.mark.parametrize("key", ["../photo.jpg", "evd_bad.jpg", "https://elsewhere.test/photo.jpg"])
def test_storage_keys_cannot_escape_bucket_or_local_directory(key):
    with pytest.raises(storage.EvidenceStorageError): storage.validate_key(key)
