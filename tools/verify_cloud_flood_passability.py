"""Owner-authorized verification of one existing, expiring presentation flood record.

No new incidents/users are created. Only the known Sanpada sample is updated, with
explicit simulation audit notes. A passable phase is restored to IMPASSABLE in finally.
Never print credentials or tokens. No production-world inspection is claimed.
"""
import argparse
import json
from datetime import datetime, timezone
from seed_private_presentation import ACCESS, STATE, ROOT, PROVENANCE, api, api_request, login, evaluate


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply",action="store_true")
    args=parser.parse_args()
    health=api("/health")
    assert health.get("flood_passability_policy")=="explicit_passable_or_excluded_v1", "Cloud deployment is not ready"
    state=json.loads(STATE.read_text(encoding="utf-8"))
    item=next(r for r in state["reports"] if r["key"]=="sanpada-drainage")
    records=api("/api/v1/hazards/map")["hazards"]
    record=next(h for h in records if h["id"]==item["hazard_id"])
    assert PROVENANCE in record["description"], "Only the owner's known sample can be changed"
    candidates=state["before_routes"][0]["candidates"]
    result={"server":state["server"],"checked_at":datetime.now(timezone.utc).isoformat(),"hazard_id":item["hazard_id"],"checks":[]}
    target=ROOT/"deliverables/Safe-Navi-Flood-Passability.private.json"
    def verify(phase):
        current=next(h for h in api("/api/v1/hazards/map")["hazards"] if h["id"]==item["hazard_id"])
        for profile in ("fastest","balanced","safest"):
            code,body=api_request("/api/v1/risk/route","POST",{"time_period":"midday","profile":profile,"routes":candidates})
            assert code==200 and body["risk_provider"]=="trained_ml"
            exclusions=[r for r in body["excluded_routes"] if any(h["id"]==item["hazard_id"] for h in r["blocked_by"])]
            assert bool(exclusions)==current["routing_blocked"], "Map/routing restriction mismatch"
            if current["routing_blocked"]:
                assert "road-2" not in {r["route_id"] for r in body["results"]}
                assert all(r["route_id"]!="road-2" for r in body["recommendations"].values())
            else:
                allowed=next(r for r in body["results"] if r["route_id"]=="road-2")
                assert any(h["id"]==item["hazard_id"] for h in allowed["affecting_hazards"])
                assert allowed["verified_hazard_exposure"]>0
            result["checks"].append(dict(phase=phase,profile=profile,passability=current["passability"],road_status=current["road_status"],
                map_label=current["passability_label"],blocked=current["routing_blocked"],distinct=body["distinct_recommended_routes"],evaluation=body))
        target.write_text(json.dumps(result,indent=2),encoding="utf-8")
        print("CLOUD FLOOD PASS",phase,"all 3 preferences; blocked",current["routing_blocked"],current["passability_label"],flush=True)
    verify("initial")
    if not args.apply:
        return
    account=next(a for a in json.loads(ACCESS.read_text(encoding="utf-8"))["accounts"] if a["expected_role"]=="government")
    auth=login(account);assert auth["user"]["role"]=="government"
    path="/api/v1/government/hazards/"+item["hazard_id"]
    def change(passability,road_status):
        api(path,"PATCH",dict(passability=passability,road_status=road_status,
            reason=PROVENANCE+" Controlled routing test: "+passability+" / "+road_status+". This is not a field inspection."),auth["access_token"])
    try:
        change("PASSABLE","CAUTION");verify("explicit_passable_caution")
        change("PASSABLE","CLOSED");verify("closure_overrides_passable")
        change("UNKNOWN","CAUTION");verify("unconfirmed_excluded")
    finally:
        try:
            change("IMPASSABLE","CAUTION")
            verify("final_impassable")
            print("FINAL SAMPLE STATE: IMPASSABLE; expiry unchanged",flush=True)
        finally:
            api_request("/api/v1/auth/logout","POST",token=auth["access_token"])


if __name__=="__main__":
    main()
