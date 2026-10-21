"""Check route action persistence against the deployed cloud using retained DEMO citizens.

No admin access, real incident, emergency call, fabricated hazard or location telemetry.
Temporary saved place is deleted; simulated journey is ended in finally.
"""
import json
import cloud_rehearsal as qa


def main():
    state=json.loads(qa.ACCESS.read_text(encoding="utf-8"))
    account=state["accounts"][0]
    health=qa.ok("/health")
    qa.check("Route actions: production dependencies",health.get("product_store")=="postgresql"
             and health.get("evidence_store")=="supabase" and health.get("risk_provider")=="trained_ml",
             "Deployed PostgreSQL, Supabase storage and trained model")
    session=qa.ok("/api/v1/auth/login","POST",dict(email=account["email"],password=account["password"]))
    token=session["access_token"]
    qa.ok("/api/v1/me",token=token)
    saved=journey=None
    try:
        saved=qa.ok("/api/v1/saved-places","POST",dict(label="DEMO route-action check",
            address="SIES GST, Nerul",latitude=19.042644,longitude=73.023428),token,201)
        places=qa.ok("/api/v1/saved-places",token=token)
        qa.check("Route actions: saved pin round trip",any(p["id"]==saved["id"] and abs(p["latitude"]-19.042644)<1e-7
            and abs(p["longitude"]-73.023428)<1e-7 for p in places),"Saved exact destination pin, not a new geocoded guess")
        journey=qa.ok("/api/v1/journeys","POST",dict(source_label="DEMO Vashi Station",
            destination_label="DEMO SIES GST Nerul",destination_latitude=19.042644,destination_longitude=73.023428,
            travel_mode="DRIVING",route_profile="BALANCED",route_summary="DEMO button regression, not real travel. 5.1 km / 20 min example."),token,201)
        qa.check("Route actions: create without GPS",bool(journey.get("share_token")),"Consented link can be created without waiting for a GPS task")
        code,share=qa.call("/api/v1/journeys/share/"+journey["share_token"])
        qa.check("Route actions: journey link response",code==200 and share.get("status")=="ACTIVE","Live cloud share endpoint, no mock server")
        code,html=qa.call("/api/v1/journeys/share/"+journey["share_token"]+"/view")
        qa.check("Route actions: phone share page",code==200 and b"SAFE-NAVI" in html,"Share web page returns populated HTML")
        qa.ok("/api/v1/journeys/"+journey["id"]+"/end","POST",{},token)
        entries=qa.ok("/api/v1/journeys",token=token)
        qa.check("Route actions: end persists",any(j["id"]==journey["id"] and j["status"]=="COMPLETED" for j in entries),"DEMO simulation ended; no live tracking left active")
        journey=None
    finally:
        if journey:qa.call("/api/v1/journeys/"+journey["id"]+"/end","POST",{},token)
        if saved:qa.ok("/api/v1/saved-places/"+saved["id"],"DELETE",{},token)
        qa.ok("/api/v1/auth/logout","POST",{},token)
    print("Route action cloud regression complete. No active simulated journey remains.",flush=True)


if __name__=="__main__":main()
