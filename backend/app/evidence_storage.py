"""Private evidence bytes, either on local disk or in a private Supabase bucket.

The Android client always uses the existing authenticated evidence endpoint. No bucket
URL, service credential or signed download token is returned to it.
"""
from __future__ import annotations

import hashlib
import os
import re
from pathlib import Path
from urllib.parse import quote, urlsplit

import httpx

MAX_EVIDENCE_BYTES = 15 * 1024 * 1024


class EvidenceStorageError(RuntimeError):
    pass


def _client(timeout=45):
    return httpx.Client(timeout=timeout, follow_redirects=False)


def backend_name() -> str:
    name = os.getenv("SAFE_NAVI_EVIDENCE_BACKEND", "local").strip().lower()
    if name not in {"local", "supabase"}:
        raise ValueError("Unsupported evidence backend")
    return name


def validate_key(key: str) -> str:
    if not re.fullmatch(r"evd_[a-f0-9]{32}\.(jpg|png|webp|mp4|webm)", key):
        raise EvidenceStorageError("Invalid evidence storage key")
    return key


def local_directory() -> Path:
    from .product_database import product_db_path
    path = Path(os.getenv("SAFE_NAVI_EVIDENCE_DIR", str(product_db_path().parent / "evidence"))).resolve()
    path.mkdir(parents=True, exist_ok=True)
    return path


def _settings():
    origin = os.getenv("SAFE_NAVI_SUPABASE_URL", "").rstrip("/")
    key = os.getenv("SAFE_NAVI_SUPABASE_SERVICE_KEY", "").strip()
    bucket = os.getenv("SAFE_NAVI_SUPABASE_BUCKET", "safenavi-evidence").strip()
    parsed = urlsplit(origin)
    if (parsed.scheme != "https" or not parsed.hostname or parsed.path not in {"", "/"}
            or parsed.username or parsed.password or parsed.query or parsed.fragment or not key
            or not re.fullmatch(r"[a-z0-9][a-z0-9_-]{0,62}", bucket)):
        raise EvidenceStorageError("Configure the private Supabase evidence bucket on the server")
    return origin, {"apikey": key, "Authorization": f"Bearer {key}"}, bucket


def _check_bucket(client, origin, headers, bucket):
    response = client.get(f"{origin}/storage/v1/bucket/{bucket}", headers=headers)
    if not response.is_success:
        raise EvidenceStorageError("Cannot access the private evidence bucket")
    try:
        value = response.json()
    except ValueError as error:
        raise EvidenceStorageError("Unreadable evidence bucket configuration") from error
    if value.get("public") is not False:
        raise EvidenceStorageError("Evidence storage must be a PRIVATE bucket; refusing public storage")


def verify_private_bucket() -> None:
    if backend_name() == "local":
        return
    origin, headers, bucket = _settings()
    try:
        with _client(20) as client:
            _check_bucket(client, origin, headers, bucket)
    except httpx.HTTPError as error:
        raise EvidenceStorageError("Evidence storage is unavailable") from error


def put(key: str, content: bytes, media_type: str) -> None:
    validate_key(key)
    if not content or len(content) > MAX_EVIDENCE_BYTES:
        raise EvidenceStorageError("Invalid evidence size")
    if backend_name() == "local":
        # A new random evidence ID must never overwrite an existing object.
        with (local_directory() / key).open("xb") as target:
            target.write(content)
        return
    origin, headers, bucket = _settings()
    try:
        with _client() as client:
            _check_bucket(client, origin, headers, bucket)
            response = client.post(f"{origin}/storage/v1/object/{bucket}/{quote(key, safe='')}",
                                   headers={**headers, "Content-Type": media_type, "x-upsert": "false"}, content=content)
            if not response.is_success:
                raise EvidenceStorageError("Evidence upload was not acknowledged by persistent storage")
    except httpx.HTTPError as error:
        raise EvidenceStorageError("Evidence upload is unavailable; please retry") from error


def read(key: str, *, expected_size: int, expected_sha256: str) -> bytes:
    validate_key(key)
    if expected_size <= 0 or expected_size > MAX_EVIDENCE_BYTES:
        raise EvidenceStorageError("Invalid evidence metadata")
    if backend_name() == "local":
        content = (local_directory() / key).read_bytes()
    else:
        origin, headers, bucket = _settings()
        try:
            with _client() as client:
                _check_bucket(client, origin, headers, bucket)
                with client.stream("GET", f"{origin}/storage/v1/object/authenticated/{bucket}/{quote(key, safe='')}", headers=headers) as response:
                    if not response.is_success:
                        raise EvidenceStorageError("Stored evidence is currently unavailable")
                    chunks, total = [], 0
                    for chunk in response.iter_bytes():
                        total += len(chunk)
                        if total > expected_size:
                            raise EvidenceStorageError("Stored evidence exceeds its recorded size")
                        chunks.append(chunk)
                    content = b"".join(chunks)
        except httpx.HTTPError as error:
            raise EvidenceStorageError("Evidence download is unavailable; please retry") from error
    if len(content) != expected_size or hashlib.sha256(content).hexdigest() != expected_sha256:
        raise EvidenceStorageError("Stored evidence failed its integrity check")
    return content


def delete(key: str) -> None:
    validate_key(key)
    if backend_name() == "local":
        (local_directory() / key).unlink(missing_ok=True)
        return
    origin, headers, bucket = _settings()
    try:
        with _client(20) as client:
            response = client.request("DELETE", f"{origin}/storage/v1/object/{bucket}", headers=headers, json={"prefixes": [key]})
            if not response.is_success:
                raise EvidenceStorageError("Could not remove unreferenced evidence")
    except httpx.HTTPError as error:
        raise EvidenceStorageError("Evidence cleanup is unavailable") from error
