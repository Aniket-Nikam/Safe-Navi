"""Expand the owner's non-production presentation scenarios, preserving provenance.

Default is a read-only cloud/geocoding preflight. Writes require both explicit flags.
Only dedicated presentation accounts are used; pending cases never become hazards.
"""
import argparse
import json
import time
import urllib.parse
from datetime import datetime, timezone

from seed_private_presentation import (
    ACCESS, STATE, PROVENANCE, api, api_request, login, save, place,
    distance_to_path, evaluate, roads,
)


# Pins come from the actual place provider, not invented incident coordinates.
# Category/severity describe simulated scenarios, not independently observed events.
APPROVED = [
    ("andheri-lighting", "Andheri Railway Station", "Streetlights not working near Andheri station", "POOR_LIGHTING", "MODERATE", "CAUTION", "A poorly lit section makes the station approach harder to see after dark."),
    ("bandra-theft", "Bandra Railway Station", "Bag theft concern near Bandra station approach", "CRIME", "MODERATE", "CAUTION", "Keep bags and valuables secure around the crowded station approach."),
    ("dadar-pickpocketing", "Dadar Railway Station", "Pickpocketing concern near Dadar station", "CRIME", "MODERATE", "CAUTION", "Crowding on the station approach creates a pickpocketing concern. Keep belongings close."),
    ("central-obstruction", "Mumbai Central Railway Station", "Roadside obstruction near Mumbai Central", "ROAD_INCIDENT", "LOW", "WORK_REQUIRED", "Materials narrow the station approach. Leave room for people walking past."),
    ("worli-pavement", "Worli Sea Face", "Damaged pavement along Worli Sea Face", "ROAD_INCIDENT", "LOW", "WORK_REQUIRED", "An uneven pavement section needs repair. Watch your footing on this approach."),
    ("airoli-harassment", "Airoli Railway Station", "Harassment concern near Airoli station approach", "CRIME", "MODERATE", "CAUTION", "Prefer the busier, well-lit station approach and seek help if someone is following you."),
    ("ghansoli-drainage", "Ghansoli Railway Station", "Waterlogging near Ghansoli station approach", "FLOODING", "LOW", "CAUTION", "Standing water makes the road surface difficult to see. Avoid deep water."),
    ("kopar-snatching", "Kopar Khairane Railway Station", "Phone snatching concern near Kopar Khairane", "CRIME", "MODERATE", "CAUTION", "Keep phones secure on the station approach and prefer a busy route."),
    ("kharghar-roadwork", "Kharghar Railway Station", "Roadwork narrows the Kharghar station approach", "ROAD_INCIDENT", "LOW", "WORK_REQUIRED", "Repair materials reduce space on the approach. Slow down and allow pedestrians through."),
    ("seawoods-lighting", "Seawoods Railway Station", "Poor lighting near Seawoods station approach", "POOR_LIGHTING", "LOW", "CAUTION", "Intermittent streetlights reduce visibility on part of the approach after dark."),
]
PENDING = [
    ("powai-pavement", "Powai", "Damaged pavement near Powai", "ROAD_INCIDENT", "LOW", "WORK_REQUIRED", "An uneven pavement section needs attention. Please review the location and condition."),
    ("taloja-lighting", "Taloja", "Poor lighting near Taloja", "POOR_LIGHTING", "LOW", "CAUTION", "A poorly lit approach needs inspection. Please check the streetlights after dark."),
]


def items(state, definitions):
    result = []
    for key, locality, title, category, severity, status, description in definitions:
        point = state["places"][locality]
        result.append(dict(key=key, locality=locality, title=title, category_key=category,
                           severity=severity, road_status=status,
                           latitude=point["latitude"], longitude=point["longitude"],
                           address=point["address"], description=description+"\n\n"+PROVENANCE))
    return result


def preflight(state):
    health = api("/health")
    assert health["product_store"] == "postgresql" and health["risk_provider"] == "trained_ml"
    assert datetime.fromisoformat(state["expires_at"]) > datetime.now(timezone.utc), "Original presentation period has expired"
    for definition in APPROVED + PENDING:
        locality = definition[1]
        cached = state["places"].get(locality)
        # Photon interprets the longer name as Dadar Central; the actual station
        # is indexed as Mumbai Central. Do not retain that known wrong result.
        wrong_central = locality == "Mumbai Central Railway Station" and cached and cached.get("name") != "Mumbai Central"
        if not cached or wrong_central:
            query = "Mumbai Central" if locality == "Mumbai Central Railway Station" else locality + (" Mumbai" if locality in (
                "Andheri Railway Station", "Bandra Railway Station", "Mumbai Central Railway Station", "Worli Sea Face") else " Navi Mumbai")
            state["places"][locality] = place(query)
            save(state)
            time.sleep(1)
        point = state["places"][locality]
        gap = min(distance_to_path(point, c["coordinates"]) for c in state["before_routes"][0]["candidates"])
        assert gap > 1500, f"{locality} is too close to the existing three-road presentation corridor"
        assert 18.85 <= point["latitude"] <= 19.55 and 72.65 <= point["longitude"] <= 73.35
        print("PLACE", locality, "=>", point["name"], point["latitude"], point["longitude"], "corridor gap", round(gap), "m", flush=True)
    pins = [(state["places"][d[1]]["latitude"], state["places"][d[1]]["longitude"]) for d in APPROVED]
    assert len(set(pins)) == len(APPROVED), "Two distinct presentation areas resolved to the same pin"
    current = api("/api/v1/hazards/map")
    assert all(r["hazard_id"] in {h["id"] for h in current["hazards"]} for r in state["reports"] + state["news"])
    print("PREFLIGHT PASS: cloud persistent + trained ML; existing markers retained", flush=True)


def point_risk(item):
    return api("/api/v1/risk/point?"+urllib.parse.urlencode({
        "latitude": item["latitude"], "longitude": item["longitude"], "time_period": "midday"}))


def prepare(state, accounts, official_token):
    citizens = [a for a in accounts if a["expected_role"] == "citizen"][:2]
    assert len(citizens) == 2
    sessions = []
    try:
        for account in citizens:
            auth = login(account)
            assert auth["user"]["role"] == "citizen"
            sessions.append(auth["access_token"])
        for group, definitions in (("extra_reports", APPROVED), ("pending_reports", PENDING)):
            rows = state.setdefault(group, [])
            for index, item in enumerate(items(state, definitions)):
                row = next((r for r in rows if r["key"] == item["key"]), None)
                if row is None:
                    row = {k: item[k] for k in ("key", "locality", "title", "latitude", "longitude", "severity", "road_status")}
                    row["owner"] = citizens[index % 2]["name"]
                    rows.append(row)
                    save(state)
                if "before_point" not in row:
                    row["before_point"] = point_risk(item)
                    save(state)
                if not row.get("report_id"):
                    payload = {k: item[k] for k in ("title", "category_key", "description", "latitude", "longitude", "address")}
                    payload.update(evidence_ids=[], client_request_id=state["run_id"]+"-extra-"+item["key"])
                    report = api("/api/v1/reports", "POST", payload, sessions[index % 2], 201)
                    row["report_id"] = report["id"]
                    save(state)
                report = api("/api/v1/reports/"+row["report_id"], token=official_token)
                if group == "pending_reports":
                    assert report["status"] == "REPORTED" and not report.get("linked_hazard_id"), "Pending case was already acted upon; do not reset it"
                    row["status"] = report["status"]
                    save(state)
                    print("PENDING FOR LIVE REVIEW", item["title"], flush=True)
                    continue
                if report.get("linked_hazard_id"):
                    row["hazard_id"] = report["linked_hazard_id"]
                else:
                    path = "/api/v1/government/reports/"+row["report_id"]
                    if report["status"] == "REPORTED":
                        api(path+"/triage", "POST", {"reason": PROVENANCE}, official_token)
                    assert report["status"] in ("REPORTED", "UNDER_REVIEW"), "Unexpected final report state; do not overwrite"
                    api(path+"/assign", "POST", {"department": "Police" if item["category_key"] == "CRIME" else "Roads & Infrastructure",
                        "assigned_to": "Presentation Safety Official", "due_hours": 48}, official_token)
                    result = api(path+"/decision", "POST", {"decision": "verify", "severity": item["severity"],
                        "road_status": item["road_status"], "hazard_status": "ACTIVE", "valid_until": state["expires_at"],
                        "reason": PROVENANCE+" Official workflow acceptance for the presentation scenario, not field verification."}, official_token)
                    row["hazard_id"] = result["hazard"]["id"]
                row["expires_at"] = state["expires_at"]
                save(state)
                print("APPROVED SAMPLE", item["title"], flush=True)
                time.sleep(.5)
    finally:
        for token in sessions:
            api_request("/api/v1/auth/logout", "POST", token=token)


def verify(state, official_token):
    current = api("/api/v1/hazards/map")
    ids = {h["id"] for h in current["hazards"]}
    flood_id=next(r["hazard_id"] for r in state["reports"] if r["key"]=="sanpada-drainage")
    flood_record=next(h for h in current["hazards"] if h["id"]==flood_id)
    expected_vashi_roads=2 if flood_record.get("routing_blocked") else 3
    all_published = state["reports"] + state["news"] + state["extra_reports"]
    assert len(state["extra_reports"]) == 10 and len(state["pending_reports"]) == 2
    assert all(r["hazard_id"] in ids for r in all_published)
    for row in state["extra_reports"]:
        report = api("/api/v1/reports/"+row["report_id"], token=official_token)
        assert report["status"] == "VERIFIED" and report["linked_hazard_id"] == row["hazard_id"]
        assert PROVENANCE in report["description"]
        after = point_risk(row)
        assert any(h["id"] == row["hazard_id"] for h in after["verified_hazards"]), "Map pin is not entering the risk lookup"
        assert after["verified_hazard_risk"] > 0
        row["after_point"] = after
        print("MAP + POINT RISK PASS", row["locality"], flush=True)
    for row in state["pending_reports"]:
        report = api("/api/v1/reports/"+row["report_id"], token=official_token)
        assert report["status"] == "REPORTED" and not report.get("linked_hazard_id")
        assert not any(h.get("report_id") == row["report_id"] for h in current["hazards"])
    state["expansion_checks"] = []
    closure_id = next(r["hazard_id"] for r in state["reports"] if r["key"] == "mumbai-closure")
    for index, case in enumerate(state["before_routes"]):
        for period in ("morning_peak", "midday"):
            result = evaluate(case["candidates"], period)
            assert result["http_status"] == 200 and result["risk_provider"] == "trained_ml"
            if index == 0:
                assert result["distinct_recommended_routes"] == expected_vashi_roads, "Route count must respect the active flood restriction"
            if index == 2:
                assert any(any(h["id"] == closure_id for h in r["blocked_by"]) for r in result["excluded_routes"])
            state["expansion_checks"].append(dict(source=case["source"], destination=case["destination"], period=period, evaluation=result))
            save(state)
            print("ROUTE PASS", case["source"], "->", case["destination"], period, result["distinct_recommended_routes"], "roads", flush=True)
    # Also ask the road provider again; preserved candidates alone cannot prove fresh upstream availability.
    fresh = roads(state["places"]["Vashi Railway Station"], state["places"]["SIES GST, Nerul"])
    state["expansion_fresh_route"] = evaluate(fresh)
    assert state["expansion_fresh_route"]["http_status"] == 200 and state["expansion_fresh_route"]["distinct_recommended_routes"] == expected_vashi_roads
    state["expansion_local_route_checks"] = []
    for first, second in (("Mumbai Central Railway Station", "Worli Sea Face"),
                          ("Airoli Railway Station", "Ghansoli Railway Station")):
        result = evaluate(roads(state["places"][first], state["places"][second]))
        assert result["http_status"] == 200 and result["risk_provider"] == "trained_ml"
        new_ids = {r["hazard_id"] for r in state["extra_reports"]}
        affecting = {h["id"] for route in result["results"] for h in route["affecting_hazards"]}
        assert new_ids & affecting, "New records must enter real-road scoring, not only the map"
        state["expansion_local_route_checks"].append(dict(source=first, destination=second, evaluation=result))
        print("NEW AREA ROUTE EFFECT PASS", first, "->", second, flush=True)
    state["map_count"] = current["count"]
    state["expansion_verified_at"] = datetime.now(timezone.utc).isoformat()
    save(state)
    print("VERIFIED", len(all_published), "published presentation markers;", len(state["pending_reports"]), "pending cases; cloud total", current["count"], flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true")
    parser.add_argument("--private-presentation", action="store_true")
    parser.add_argument("--verify-only", action="store_true")
    args = parser.parse_args()
    state = json.loads(STATE.read_text(encoding="utf-8"))
    preflight(state)
    if not args.apply and not args.verify_only:
        return
    assert not args.apply or args.private_presentation, "Explicit non-production presentation authorization required"
    accounts = json.loads(ACCESS.read_text(encoding="utf-8"))["accounts"]
    auth = login(next(a for a in accounts if a["expected_role"] == "government"))
    assert auth["user"]["role"] == "government"
    try:
        if args.apply:
            prepare(state, accounts, auth["access_token"])
        verify(state, auth["access_token"])
    finally:
        api_request("/api/v1/auth/logout", "POST", token=auth["access_token"])


if __name__ == "__main__":
    main()
