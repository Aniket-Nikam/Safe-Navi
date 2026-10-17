"""Cloud readiness check; initializes the dedicated app schema if necessary.

Never prints credentials, creates accounts, uploads evidence or changes case records.
"""
import argparse
import json
import os
import httpx


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--env-file", required=True)
    parser.add_argument("--origin", help="Optional deployed HTTPS origin, not a laptop address")
    options = parser.parse_args()
    from .environment import load_environment_file
    load_environment_file(options.env_file, override=True)
    from .cloud_configuration import validate_cloud_configuration
    from .cloud_database import database_url
    from .evidence_storage import backend_name
    from .product_database import connect_product
    if os.getenv("SAFE_NAVI_CLOUD_MODE", "").lower() != "true" or not database_url() or backend_name() != "supabase":
        raise SystemExit("Configure PostgreSQL and Supabase private evidence storage first")
    validate_cloud_configuration()
    with connect_product() as connection:
        assert connection.execute("SELECT 1").fetchone()[0] == 1
    result = {"database": "connected", "evidence_bucket": "private_verified"}
    if options.origin:
        from urllib.parse import urlsplit
        parsed = urlsplit(options.origin)
        if parsed.scheme != "https" or not parsed.hostname or parsed.path not in {"", "/"} or parsed.query or parsed.fragment or parsed.username is not None:
            raise SystemExit("Enter only the public HTTPS origin")
        with httpx.Client(timeout=120, follow_redirects=False) as client:
            response = client.get(options.origin.rstrip("/") + "/health")
            response.raise_for_status()
            health = response.json()
        if health.get("status") != "ok" or health.get("product_store") != "postgresql" or health.get("evidence_store") != "supabase":
            raise SystemExit("The deployed service is not configured for persistent cloud storage")
        result.update(public_health="ok", news_scheduler=health.get("news_scheduler"), risk_provider=health.get("risk_provider"))
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
