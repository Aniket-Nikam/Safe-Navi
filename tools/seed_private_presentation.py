"""Owner-authorized sample data for the explicitly non-production presentation build.

Natural titles; simulation provenance remains in descriptions and official audit notes.
Never claim an observed incident, police confirmation, field inspection or user adoption.
All generated hazards expire. Credentials/tokens are never printed or committed.
"""
import argparse
import json
import math
import time
import urllib.parse
from datetime import datetime, timedelta, timezone
from pathlib import Path

from prepare_presentation_accounts import request as api_request, login
from verify_demo_routes import request as public_request, place, decode, BASE

ROOT = Path(__file__).resolve().parents[1]
STATE = ROOT / "deliverables/Safe-Navi-Map-Presentation.private.json"
ACCESS = ROOT / "deliverables/Safe-Navi-Presentation-Logins.private.json"
PROVENANCE = ("Simulation provenance: owner-authorized sample data for a private academic presentation. "
              "This is not an observed or independently verified real-world incident. "
              "Do not use this record for actual travel or public safety decisions.")
PAIRS = [("Vashi Railway Station", "SIES GST, Nerul"),
         ("Vashi Railway Station", "CBD Belapur Railway Station"),
         ("Ghatkopar Railway Station", "Chembur Railway Station"),
         ("Mulund Railway Station", "Bhandup Railway Station")]


def save(state):
    STATE.write_text(json.dumps(state, indent=2), encoding="utf-8")


def api(path, method="GET", payload=None, token=None, expected=200):
    code, value = api_request(path, method, payload, token)
    if code != expected:
        raise RuntimeError(f"{method} {path.split('?')[0]}: HTTP {code}; {str(value)[:180]}")
    return value


def roads(first, second):
    raw = public_request("https://valhalla1.openstreetmap.de/route", {
        "locations": [{"lat": first["latitude"], "lon": first["longitude"]},
                      {"lat": second["latitude"], "lon": second["longitude"]}],
        "costing": "auto", "alternates": 2, "directions_type": "none", "units": "kilometers"})
    trips = [raw["trip"]] + [v["trip"] for v in raw.get("alternates", []) if "trip" in v]
    return [{"route_id": f"road-{i+1}", "travel_minutes": t["summary"]["time"] / 60,
             "distance_meters": t["summary"]["length"] * 1000,
             "coordinates": [p for leg in t["legs"] for p in decode(leg["shape"])]}
            for i, t in enumerate(trips)]


def evaluate(candidates, period="midday"):
    code, value = api_request("/api/v1/risk/route", "POST", {
        "time_period": period, "profile": "balanced", "routes": candidates})
    return {"http_status": code, **value}


def distance_to_path(point, coordinates):
    # Local equirectangular segment projection; only used to choose test scenarios,
    # never to override production geometry checks or model scores.
    best = float("inf")
    lat, lon = point["latitude"], point["longitude"]
    for a, b in zip(coordinates, coordinates[1:]):
        ax, ay = (a["longitude"]-lon)*105000, (a["latitude"]-lat)*111000
        bx, by = (b["longitude"]-lon)*105000, (b["latitude"]-lat)*111000
        dx, dy = bx-ax, by-ay
        t = min(1, max(0, -(ax*dx+ay*dy)/(dx*dx+dy*dy))) if dx or dy else 0
        best = min(best, math.hypot(ax+t*dx, ay+t*dy))
    return best


def unique_point(candidates):
    fastest = min(candidates, key=lambda c: c["travel_minutes"])
    others = [c for c in candidates if c is not fastest]
    if not others:
        return None
    points = fastest["coordinates"]
    options = points[max(1, len(points)//5):max(2, len(points)*4//5):max(1, len(points)//160)]
    scored = [(min(distance_to_path(p, c["coordinates"]) for c in others), p) for p in options]
    gap, point = max(scored, key=lambda x: x[0])
    return dict(point, separation_m=round(gap, 1), fastest_route=fastest["route_id"])


def scenarios(state):
    p = state["places"]
    navi = state["before_routes"][0]["unique_point"]
    mumbai = state["before_routes"][2]["unique_point"]
    assert navi and navi["separation_m"] > 200 and mumbai and mumbai["separation_m"] > 150
    values = [
        ("navi-corridor", "Phone snatching concern on the Vashi–Nerul corridor", "CRIME", "HIGH", "UNSAFE", navi,
         "Pinned road segment on the Vashi–Nerul corridor", "Travellers describe a phone-snatching concern on an isolated section. Prefer a busier alternative when available."),
        ("mumbai-closure", "Repair work and temporary road closure", "ROAD_INCIDENT", "HIGH", "CLOSED", mumbai,
         "Pinned road segment between Ghatkopar and Chembur", "Road repair work obstructs the selected carriageway. Use a road alternative that avoids this location."),
        ("juhu-theft", "Pickpocketing concern around Juhu", "CRIME", "MODERATE", "CAUTION", p["Juhu"],
         "Juhu, Mumbai", "A pickpocketing concern is reported around the busy approach. Keep belongings secure."),
        ("sion-harassment", "Harassment concern near Sion station approach", "CRIME", "MODERATE", "CAUTION", p["Sion Railway Station"],
         "Sion Railway Station approach, Mumbai", "Travellers describe harassment near the station approach. Prefer busy and well-lit paths."),
        ("mulund-lighting", "Streetlights not working near Mulund station", "POOR_LIGHTING", "MODERATE", "CAUTION", p["Mulund Railway Station"],
         "Mulund Railway Station approach, Mumbai", "Poor lighting reduces visibility on the station approach, especially after dark."),
        ("chembur-obstruction", "Roadside obstruction near Chembur station", "ROAD_INCIDENT", "LOW", "WORK_REQUIRED", p["Chembur Railway Station"],
         "Chembur Railway Station approach, Mumbai", "A roadside obstruction narrows the walking and vehicle approach; additional caution is needed."),
        ("vashi-theft", "Pickpocketing concern near Vashi station", "CRIME", "LOW", "CAUTION", p["Vashi Railway Station"],
         "Vashi Railway Station approach, Navi Mumbai", "Crowding and a theft concern are reported around the station approach. Keep bags and phones secure."),
        ("sanpada-drainage", "Waterlogging near Sanpada station approach", "FLOODING", "LOW", "CAUTION", p["Sanpada Railway Station"],
         "Sanpada Railway Station approach, Navi Mumbai", "Standing water around the approach reduces visibility of the road surface. Avoid entering deep water."),
        ("nerul-lighting", "Poor lighting on the Nerul station approach", "POOR_LIGHTING", "LOW", "CAUTION", p["Nerul Railway Station"],
         "Nerul Railway Station approach, Navi Mumbai", "Intermittent streetlights leave a poorly lit section on the approach."),
        ("belapur-roadwork", "Roadwork narrows the CBD Belapur approach", "ROAD_INCIDENT", "LOW", "WORK_REQUIRED", p["CBD Belapur Railway Station"],
         "CBD Belapur Railway Station approach, Navi Mumbai", "Repair materials narrow the approach; proceed carefully and allow space for pedestrians."),
    ]
    return [dict(key=k, title=t, category_key=c, severity=s, road_status=r,
                 latitude=point["latitude"], longitude=point["longitude"], address=a,
                 description=description + "\n\n" + PROVENANCE) for k,t,c,s,r,point,a,description in values]


def publish_samples(state, accounts, official_token):
    citizens = [a for a in accounts if a["expected_role"] == "citizen"][:2]
    citizen_tokens = [login(a)["access_token"] for a in citizens]
    expiry = state.setdefault("expires_at", (datetime.now(timezone.utc)+timedelta(hours=72)).isoformat())
    save(state)
    try:
        for i, item in enumerate(scenarios(state)):
            row = next((r for r in state["reports"] if r["key"] == item["key"]), None)
            if row and row.get("hazard_id"):
                print("ALREADY PREPARED", item["title"], flush=True)
                continue
            if row is None:
                row = {"key": item["key"], "title": item["title"], "owner": citizens[i % 2]["name"]}
                state["reports"].append(row)
                save(state)
            payload = {k: item[k] for k in ("title", "category_key", "description", "latitude", "longitude", "address")}
            payload.update(evidence_ids=[], client_request_id=state["run_id"]+"-"+item["key"])
            report = api("/api/v1/reports", "POST", payload, citizen_tokens[i % 2], 201)
            row["report_id"] = report["id"]
            save(state)
            if report.get("linked_hazard_id"):
                row["hazard_id"] = report["linked_hazard_id"]
                save(state)
                continue
            path = "/api/v1/government/reports/"+report["id"]
            api(path+"/triage", "POST", {"reason": PROVENANCE}, official_token)
            api(path+"/assign", "POST", {"department": "Police" if item["category_key"] == "CRIME" else "Roads & Infrastructure",
                "assigned_to": "Demo Safety Official", "due_hours": 48}, official_token)
            decision = {"decision": "verify", "severity": item["severity"], "road_status": item["road_status"],
                        "hazard_status": "ACTIVE", "valid_until": expiry,
                        "reason": PROVENANCE + " Official workflow acceptance for the presentation scenario, not field verification."}
            if item["road_status"] == "CLOSED":
                decision["no_go_radius_m"] = 40
            result = api(path+"/decision", "POST", decision, official_token)
            row.update(hazard_id=result["hazard"]["id"], latitude=item["latitude"], longitude=item["longitude"],
                       road_status=item["road_status"], severity=item["severity"], expires_at=expiry)
            save(state)
            print("PUBLISHED SAMPLE", item["title"], item["road_status"], flush=True)
    finally:
        for token in citizen_tokens:
            api_request("/api/v1/auth/logout", "POST", token=token)


def publish_news_replays(state, official_token):
    # These are presentation replays, not a claim of current field-confirmed hazards.
    # Original article text/date/source remain untouched. Notes disclose replay and approximate locality.
    choices = [("after-juhu-attack", "Juhu", "HIGH", "CAUTION"),
               ("mumbai-coastal-road-bmc-notice", "Priyadarshini Park Mumbai", "MODERATE", "WORK_REQUIRED")]
    for source_part, locality, severity, road_status in choices:
        existing = next((n for n in state["news"] if n["key"] == source_part), None)
        if existing and existing.get("hazard_id"):
            continue
        signal = next((n for n in state["news_candidates"] if source_part in n["source_url"]), None)
        if not signal:
            raise RuntimeError("Selected source is not in the captured news queue: "+source_part)
        point = state["places"].get(locality)
        if not point:
            point = place(locality)
            state["places"][locality] = point
            save(state)
        before = api("/api/v1/risk/point?"+urllib.parse.urlencode({"latitude":point["latitude"],"longitude":point["longitude"],"time_period":"midday"}))
        api("/api/v1/government/news-signals/"+signal["id"]+"/review", "POST", {"review_status":"RELEVANT"}, official_token)
        payload = {"latitude":point["latitude"],"longitude":point["longitude"],"address":point["address"],
                   "severity":severity,"road_status":road_status,"valid_hours":72,"confirmed_current":True,
                   "verification_note": PROVENANCE + " Presentation replay of the linked source article. The pin is an approximate article locality. Current real-world danger or a road closure is not asserted; the confirmation applies only to this private simulation."}
        hazard = api("/api/v1/government/news-signals/"+signal["id"]+"/publish", "POST", payload, official_token)
        after = api("/api/v1/risk/point?"+urllib.parse.urlencode({"latitude":point["latitude"],"longitude":point["longitude"],"time_period":"midday"}))
        state["news"].append(dict(key=source_part, signal_id=signal["id"], hazard_id=hazard["id"],
            source_url=signal["source_url"], source_published_at=signal["published_at"], latitude=point["latitude"],
            longitude=point["longitude"], road_status=road_status, expires_at=hazard["valid_until"], before_point=before, after_point=after))
        save(state)
        print("PUBLISHED SOURCE REPLAY", locality, road_status, flush=True)


def verify_results(state, official_token):
    current = api("/api/v1/hazards/map")
    by_id = {h["id"]: h for h in current["hazards"]}
    seeded = state["reports"] + state["news"]
    assert all(row.get("hazard_id") in by_id for row in seeded), "Some sample marker is missing from the cloud map"
    state["map_count"] = current["count"]
    state["after_routes"] = []
    for case in state["before_routes"]:
        result = evaluate(case["candidates"])
        assert result["http_status"] == 200 and result["risk_provider"] == "trained_ml", "An example lost all available routes or ML"
        row = dict(source=case["source"], destination=case["destination"], evaluation=result)
        state["after_routes"].append(row)
        changed = any(case["evaluation"]["recommendations"][p]["route_id"] != result["recommendations"][p]["route_id"] for p in ("fastest","balanced","safest"))
        row["recommendation_changed"] = changed
        print("ROUTE CHECK", case["source"], "->", case["destination"], "changed", changed,
              "excluded",len(result["excluded_routes"]),"distinct",result["distinct_recommended_routes"], flush=True)
        save(state)
    closure_id = next(r["hazard_id"] for r in state["reports"] if r["key"] == "mumbai-closure")
    mumbai = state["after_routes"][2]["evaluation"]
    assert any(any(h["id"] == closure_id for h in r["blocked_by"]) for r in mumbai["excluded_routes"]), "Closure did not exclude an intersecting route"
    assert any(r["recommendation_changed"] for r in state["after_routes"]), "No recommendation changed"
    for row in state["reports"]:
        record = api("/api/v1/reports/"+row["report_id"], token=official_token)
        assert record["status"] == "VERIFIED" and record["linked_hazard_id"] == row["hazard_id"]
    # Prove news affects the same real-road scoring endpoint, not only its map marker.
    for n in state["news"]:
        start = dict(latitude=n["latitude"],longitude=n["longitude"])
        end = dict(latitude=n["latitude"]+.008,longitude=n["longitude"]+.008)
        candidates = roads(start,end)
        result = evaluate(candidates)
        assert result["http_status"] == 200 and any(n["hazard_id"] == h["id"] for r in result["results"] for h in r["affecting_hazards"])
        n["nearby_route_test"] = result
        print("NEWS ROUTE EFFECT PASS", n["key"], flush=True)
    point = state["places"]["Vashi Railway Station"]
    labels = api("/api/v1/risk/area-crime?"+urllib.parse.urlencode({"latitude":point["latitude"],"longitude":point["longitude"],"radius_m":3000}))
    assert labels["verified_record_count"] > 0 and labels["types"], "Crime categories not present"
    state["crime_labels"] = labels
    state["verified_at"] = datetime.now(timezone.utc).isoformat()
    save(state)
    print("VERIFIED",len(state["reports"]),"sample reports",len(state["news"]),"source replays; cloud map count",current["count"], flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true")
    parser.add_argument("--private-presentation", action="store_true", help="Explicit owner confirmation: this is not a public production service")
    parser.add_argument("--verify-morning", action="store_true", help="Read-only route check for the 7–11 a.m. period")
    parser.add_argument("--ui-handoff", action="store_true", help="Generate a minimal ignored credential handoff for the owned emulator")
    args = parser.parse_args()
    accounts = json.loads(ACCESS.read_text(encoding="utf-8"))["accounts"]
    if args.ui_handoff:
        state = json.loads(STATE.read_text(encoding="utf-8"))
        data = {"accounts": [next(a for a in accounts if a["expected_role"] == role) for role in ("citizen", "government")],
                "presentation_flood_hazard": next(r["hazard_id"] for r in state["reports"] if r["key"] == "sanpada-drainage"),
                "presentation_hazards": [r["hazard_id"] for r in state["reports"]+state["news"]+state.get("extra_reports", [])]}
        target = ROOT / "tmp/presentation-ui-handoff.private.json"
        target.parent.mkdir(exist_ok=True)
        target.write_text(json.dumps(data), encoding="utf-8")
        print("Minimal private emulator handoff prepared; no tokens retained")
        return
    official = next(a for a in accounts if a["expected_role"] == "government")
    auth = login(official)
    assert auth["user"]["role"] == "government"
    token = auth["access_token"]
    state = json.loads(STATE.read_text(encoding="utf-8")) if STATE.exists() else {
        "server": BASE, "provenance": PROVENANCE, "run_id": "private-presentation-20261022", "reports": [], "news": []}
    try:
        health = api("/health")
        assert health["product_store"] == "postgresql" and health["risk_provider"] == "trained_ml"
        print("Cloud healthy; PostgreSQL + trained ML", flush=True)
        state["health"] = health
        if "places" not in state:
            previous = json.loads((ROOT / "deliverables/Safe-Navi-Verified-Route-List.json").read_text(encoding="utf-8"))
            state["places"] = previous["places"]
            state["places"]["Juhu"] = place("Juhu Mumbai")
            save(state)
        if "before_routes" not in state:
            state["before_hazards"] = api("/api/v1/hazards/map")
            state["before_routes"] = []
            for first, second in PAIRS:
                candidates = roads(state["places"][first], state["places"][second])
                value = evaluate(candidates)
                assert value["http_status"] == 200 and value["risk_provider"] == "trained_ml"
                point = unique_point(candidates)
                state["before_routes"].append(dict(source=first, destination=second, candidates=candidates,
                                                   evaluation=value, unique_point=point))
                print("BASELINE", first, "->", second, "roads", len(candidates),
                      "distinct winners", value["distinct_recommended_routes"], "unique gap", point and point["separation_m"], flush=True)
            save(state)
        news = api("/api/v1/government/news-signals", token=token)
        state["news_candidates"] = [n for n in news if not n.get("linked_hazard_id") and n.get("category") in ("CRIME", "TRAFFIC", "FLOODING", "CIVIC")]
        save(state)
        if not args.apply and not args.verify_morning:
            print("NEWS CANDIDATES", flush=True)
            for n in state["news_candidates"][:18]:
                print(json.dumps({k: n.get(k) for k in ("id", "headline", "location_text", "category", "published_at", "source_url")}), flush=True)
        if args.verify_morning:
            state["morning_routes"] = []
            for case in state["before_routes"]:
                result = evaluate(case["candidates"], "morning_peak")
                assert result["http_status"] == 200 and result["risk_provider"] == "trained_ml"
                state["morning_routes"].append(dict(source=case["source"],destination=case["destination"],evaluation=result))
                print("MORNING",case["source"],"->",case["destination"],"distinct",result["distinct_recommended_routes"],flush=True)
                save(state)
        if args.apply:
            assert args.private_presentation, "Explicit non-production presentation authorization is required"
            publish_samples(state, accounts, token)
            publish_news_replays(state, token)
            verify_results(state, token)
    finally:
        api_request("/api/v1/auth/logout", "POST", token=token)


if __name__ == "__main__":
    main()
