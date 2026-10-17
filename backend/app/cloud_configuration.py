"""Fail closed on ephemeral deployments rather than silently storing citizen data locally."""
import os

from .cloud_database import database_url
from .evidence_storage import backend_name, verify_private_bucket


def scheduler_mode():
    mode = os.getenv("SAFE_NAVI_NEWS_SCHEDULER", "internal").strip().lower()
    if mode not in {"internal", "external"}:
        raise ValueError("SAFE_NAVI_NEWS_SCHEDULER must be internal or external")
    return mode


def validate_cloud_configuration():
    mode = scheduler_mode()
    if os.getenv("SAFE_NAVI_CLOUD_MODE", "false").lower() != "true":
        return
    if not database_url() or backend_name() != "supabase":
        raise ValueError("Cloud mode requires PostgreSQL and private Supabase evidence storage; local persistence is forbidden")
    if mode != "external" or len(os.getenv("SAFE_NAVI_SCHEDULER_KEY", "")) < 32:
        raise ValueError("Cloud mode requires the external scheduler and a secret key of at least 32 characters")
    from .product_database import ensure_schema
    ensure_schema()
    verify_private_bucket()
