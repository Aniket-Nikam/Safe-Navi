"""Prepare explicit cloud demo identities; never publishes hazards or changes existing users.

Public registration creates citizens only. A cloud owner must explicitly provision
the admin role; the new admin then uses the normal audited promotion API.
Passwords remain in a Git-ignored private handoff, not in this source or logs.
"""
import argparse
import json
import secrets
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASE = "https://safe-navi-api.onrender.com"
HANDOFF = ROOT / "deliverables/Safe-Navi-Presentation-Logins.private.json"
REHEARSAL = ROOT / "deliverables/Safe-Navi-Cloud-Rehearsal-Access.private.json"


def request(path, method="GET", payload=None, token=None):
    headers = {"Accept": "application/json", "User-Agent": "Safe-Navi presentation preparation"}
    if token:
        headers["Authorization"] = "Bearer " + token
    if payload is not None:
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(BASE + path, method=method, headers=headers,
                                 data=json.dumps(payload).encode() if payload is not None else None)
    try:
        with urllib.request.urlopen(req, timeout=120) as response:
            body = response.read()
            return response.status, json.loads(body) if "json" in response.headers.get("Content-Type", "") else body
    except urllib.error.HTTPError as error:
        return error.code, json.loads(error.read())


def login(account):
    code, value = request("/api/v1/auth/login", "POST", {
        "email": account["email"], "password": account["password"]})
    if code != 200:
        raise RuntimeError(f"Cloud login failed for {account['email']}: HTTP {code}")
    return value


def save(state):
    HANDOFF.write_text(json.dumps(state, indent=2), encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--verify-roles", action="store_true")
    parser.add_argument("--prepare-case", action="store_true", help="Prepare only the existing explicitly labelled rehearsal case")
    parser.add_argument("--show-logins", action="store_true", help="Only for the owner's explicit credential handoff")
    args = parser.parse_args()
    if HANDOFF.exists():
        state = json.loads(HANDOFF.read_text(encoding="utf-8"))
    else:
        citizens = json.loads(REHEARSAL.read_text(encoding="utf-8"))["accounts"]
        state = {"server": BASE, "accounts": [dict(a, expected_role="citizen") for a in citizens]}
        for label, name, role in (("admin", "Demo Administrator", "admin"),
                                  ("official", "Demo Safety Official", "government")):
            state["accounts"].append({"name": name, "email": f"{label}.presentation.20261022@safenavi.example",
                                      "password": "SN-" + secrets.token_urlsafe(12) + "!", "expected_role": role})
        save(state)  # Preserve generated passwords before any external write.
    code, health = request("/health")
    assert code == 200 and health.get("product_store") == "postgresql", "Not the persistent cloud backend"
    print("Cloud health:", json.dumps(health), flush=True)
    for account in state["accounts"]:
        if "id" not in account:
            code, result = request("/api/v1/auth/register", "POST", {
                k: account[k] for k in ("name", "email", "password")})
            if code != 201:
                raise RuntimeError(f"Registration failed for {account['email']}: HTTP {code}; existing accounts untouched")
            account["id"] = result["user"]["id"]
            save(state)
            request("/api/v1/auth/logout", "POST", token=result["access_token"])
        result = login(account)
        token = result["access_token"]
        account["verified_role"] = result["user"]["role"]
        account["verified_at"] = datetime.now(timezone.utc).isoformat()
        print("Login PASS", account["name"], account["email"], "role=" + account["verified_role"],
              "id=" + account["id"], flush=True)
        request("/api/v1/auth/logout", "POST", token=token)
        save(state)
    if args.verify_roles:
        admin = next(a for a in state["accounts"] if a["expected_role"] == "admin")
        official = next(a for a in state["accounts"] if a["expected_role"] == "government")
        admin_login = login(admin)
        assert admin_login["user"]["role"] == "admin", "Owner must provision the dedicated admin before promotion"
        token = admin_login["access_token"]
        official_login = login(official)
        request("/api/v1/auth/logout", "POST", token=official_login["access_token"])
        if official_login["user"]["role"] == "citizen":
            code, value = request(f"/api/v1/admin/accounts/{official['id']}/action", "POST", {
                "action": "promote_official", "reason": "Owner-authorized professor presentation: dedicated demo safety official"}, token)
            assert code == 200 and value["role"] == "government", "Audited official promotion failed"
            print("Official promotion PASS (audited admin API)", flush=True)
        for path in ("/api/v1/admin/accounts", "/api/v1/admin/app-settings", "/api/v1/admin/audit", "/api/v1/government/news/settings"):
            code, _ = request(path, token=token)
            assert code == 200, f"Admin read {path}: HTTP {code}"
            print("Admin read PASS", path, flush=True)
        code, _ = request("/api/v1/government/reports/presentation-no-such-report/triage", "POST", {"reason": "Permission check only; nonexistent case"}, token)
        assert code == 403, "Admin must not change government records"
        print("Admin government-write denial PASS HTTP 403", flush=True)
        request("/api/v1/auth/logout", "POST", token=token)
        official_login = login(official)
        token = official_login["access_token"]
        assert official_login["user"]["role"] == "government"
        for path in ("/api/v1/reports", "/api/v1/government/metrics", "/api/v1/government/news-signals"):
            code, value = request(path, token=token)
            assert code == 200, f"Official read {path}: HTTP {code}"
            print("Official read PASS", path, "items=" + str(len(value)), flush=True)
        code, _ = request("/api/v1/admin/accounts", token=token)
        assert code == 403, "Officials must not have admin access"
        print("Official admin-access denial PASS HTTP 403", flush=True)
        request("/api/v1/auth/logout", "POST", token=token)
        official["verified_role"] = "government"
        save(state)
    if args.prepare_case:
        official = next(a for a in state["accounts"] if a["expected_role"] == "government")
        citizen = state["accounts"][0]
        rehearsal = json.loads(REHEARSAL.read_text(encoding="utf-8"))
        report_id = rehearsal["records"]["report_id"]
        official_auth, citizen_auth = login(official), login(citizen)
        assert official_auth["user"]["role"] == "government"
        official_token, citizen_token = official_auth["access_token"], citizen_auth["access_token"]
        path = "/api/v1/reports/" + report_id
        code, case = request(path, token=official_token)
        assert code == 200 and case["title"].startswith("DEMO REHEARSAL")
        assert case["citizen_id"] == citizen["id"] and not case.get("linked_hazard_id")
        assert case["status"] not in ("VERIFIED", "REJECTED", "RESOLVED"), "Do not alter a concluded case"
        if not state.get("case_prepared"):
            actions = (
                (f"/api/v1/government/reports/{report_id}/triage", {"reason": "DEMO rehearsal only; review the sample evidence without publishing a hazard."}),
                (f"/api/v1/government/reports/{report_id}/assign", {"department": "Roads & Infrastructure", "assigned_to": official["name"], "due_hours": 48}),
                (f"/api/v1/government/reports/{report_id}/request-information", {"message": "DEMO rehearsal: please confirm this is a presentation-only upload test, not an actual incident. This case must remain unpublished."}),
            )
            for endpoint, payload in actions:
                code, _ = request(endpoint, "POST", payload, official_token)
                assert code == 200, f"Demo case preparation failed: HTTP {code}"
                print("Demo-only case action PASS", endpoint.rsplit("/", 1)[-1], flush=True)
            code, _ = request(path + "/messages", "POST", {
                "content": "DEMO reply: confirmed, this is only a presentation rehearsal. The attached Safe-Navi logo tests image upload, not evidence of danger. Please do not publish a hazard.",
                "evidence_id": rehearsal["records"]["evidence_id"], "client_request_id": "presentation-20261022-citizen-response"}, citizen_token)
            assert code == 201, "Citizen demo reply failed"
            state["case_prepared"] = True
            state["demo_report_id"] = report_id
            save(state)
        code, case = request(path, token=official_token)
        assert code == 200 and case["assigned_to"] == official["name"] and case["status"] == "UNDER_REVIEW"
        code, messages = request(path + "/messages", token=official_token)
        assert code == 200 and any(m["author_role"] == "government" for m in messages) and any(m["author_role"] == "citizen" for m in messages)
        code, image = request("/api/v1/evidence/" + rehearsal["records"]["evidence_id"], token=official_token)
        assert code == 200 and isinstance(image, bytes) and len(image) > 100
        code, hazards = request("/api/v1/hazards/map")
        assert code == 200 and all(h.get("report_id") != report_id for h in hazards.get("hazards", []))
        print("Presentation case PASS: assigned; official/citizen messages visible; image downloads; no demo hazard", flush=True)
        request("/api/v1/auth/logout", "POST", token=official_token)
        request("/api/v1/auth/logout", "POST", token=citizen_token)
    if args.show_logins:
        for a in state["accounts"]:
            print(json.dumps({k: a[k] for k in ("name", "email", "password", "verified_role")}))


if __name__ == "__main__":
    main()
