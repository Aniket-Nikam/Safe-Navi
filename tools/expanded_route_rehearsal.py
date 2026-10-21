"""Read-only live-provider search for honest demonstrations; never changes risk scores."""
import hashlib
import json
import time
import sys
from pathlib import Path
from datetime import datetime, timezone, timedelta
from verify_demo_routes import request, place, decode, BASE

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/"deliverables"
PAIRS=[
    ("Thane Railway Station","CBD Belapur Railway Station"),
    ("Airoli Railway Station","CBD Belapur Railway Station"),
    ("Vashi Railway Station","Juinagar Railway Station"),
    ("Sanpada Railway Station","Nerul Railway Station"),
    ("Seawoods Railway Station","CBD Belapur Railway Station"),
    ("Thane Railway Station","Airoli Railway Station"),
    ("Mulund Railway Station","Bhandup Railway Station"),
    ("Ghatkopar Railway Station","Chembur Railway Station"),
    ("Kurla Railway Station","Sion Railway Station"),
    ("Dadar Railway Station","Sion Railway Station"),
    ("Vikhroli Railway Station","Powai"),
    ("Chembur Railway Station","Kurla Railway Station"),
    ("Kharghar Railway Station","Taloja"),
    ("Vashi Railway Station","SIES GST, Nerul"),
    ("Nerul Railway Station","Sanpada Railway Station"),
    ("CBD Belapur Railway Station","Seawoods Railway Station"),
]


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    places={};cases=[];failures=[]
    # Reuse today's verified station coordinates; only new places need a fresh query.
    previous=ROOT/"tmp/acceptance-2026-10-21/verified-routes.json"
    if previous.exists():places.update(json.loads(previous.read_text())["places"])
    for query in sorted({q for pair in PAIRS for q in pair}):
        if query in places:continue
        try:
            places[query]=place(query); print("PLACE",query,places[query]["address"],flush=True)
        except Exception as error:failures.append(dict(place=query,error=type(error).__name__))
        time.sleep(1.1)
    report=dict(checked_at_ist=datetime.now(timezone(timedelta(hours=5,minutes=30))).isoformat(),
                provider_metadata=request(BASE+"/api/v1/risk/provider"),health=request(BASE+"/health"),
                places=places,cases=cases,failures=failures)
    for first,second in PAIRS:
        if first not in places or second not in places:continue
        a,b=places[first],places[second]
        try:
            raw=request("https://valhalla1.openstreetmap.de/route",dict(locations=[
                dict(lat=a["latitude"],lon=a["longitude"]),dict(lat=b["latitude"],lon=b["longitude"])],
                costing="auto",alternates=2,directions_type="none",directions_options=dict(units="kilometers")))
            trips=[raw["trip"]]+[v["trip"] for v in raw.get("alternates",[]) if "trip" in v]
            candidates=[]
            for i,trip in enumerate(trips):
                shape=[]
                for leg in trip["legs"]:shape.extend(decode(leg["shape"]))
                candidates.append(dict(route_id="road-"+str(i+1),travel_minutes=trip["summary"]["time"]/60,
                    distance_meters=trip["summary"]["length"]*1000,coordinates=shape))
            for period in ("morning_peak","midday"):
                value=request(BASE+"/api/v1/risk/route",dict(time_period=period,profile="balanced",routes=candidates))
                if value.get("risk_provider")!="trained_ml":raise AssertionError("Cloud did not use trained ML")
                cases.append(dict(source=first,destination=second,time_period=period,
                    distinct=value["distinct_recommended_routes"],risk_provider=value["risk_provider"],
                    model_version=value.get("model_version"),coverage_ratio=value.get("coverage_ratio"),
                    recommendations=value["recommendations"],candidate_count=len(candidates),
                    geometry_hashes={c["route_id"]:hashlib.sha256(json.dumps(c["coordinates"]).encode()).hexdigest() for c in candidates}))
                print("ROUTE",first,"→",second,period,"distinct",cases[-1]["distinct"],flush=True)
            (OUT/"Safe-Navi-Verified-Route-List.json").write_text(json.dumps(report,indent=2),encoding="utf-8")
        except Exception as error:
            failures.append(dict(source=first,destination=second,error=type(error).__name__,detail=str(error)[:150]))
            print("ROUTE ERROR",first,second,type(error).__name__,flush=True)
        time.sleep(1.1)
    (OUT/"Safe-Navi-Verified-Route-List.json").write_text(json.dumps(report,indent=2),encoding="utf-8")
    rows=["# Live-cloud route demonstration list","",report["checked_at_ist"],"",
          "Real Valhalla driving candidates, ranked by the deployed trained model. Estimates are synthetic-area exposure, not observed crime probabilities.",
          "Both endpoints must use the exact pins below. Provider data and official hazards can change the result.",
          "7–11 a.m. uses morning_peak; 11 a.m.–5 p.m. uses midday. Same road = same time and exposure. No artificial route differences were introduced.","",
          "| Source → destination | Period | Distinct winners | Fastest (min / risk) | Balanced (min / risk) | Safest (min / risk) |",
          "|---|---|---:|---|---|---|"]
    for case in sorted(cases,key=lambda c:-c["distinct"]):
        if case["distinct"]<2:continue
        values=[f'{case["source"]} → {case["destination"]}',case["time_period"],str(case["distinct"])]
        for p in ("fastest","balanced","safest"):
            r=case["recommendations"][p];values.append(f'{r["travel_minutes"]:.1f} / {r["risk_exposure"]:.2f} ({r["route_id"]})')
        rows.append("| "+" | ".join(values)+" |")
    rows+=["","## Exact pins",""]
    for key,p in places.items():
        if any(c["distinct"]>1 and key in (c["source"],c["destination"]) for c in cases):
            rows.append(f'- {key}: {p["latitude"]}, {p["longitude"]} — {p["address"]}')
    three=sum(c["distinct"]==3 for c in cases)
    rows+=["",f"Evaluated {len(cases)} time-period cases. Three distinct winners: {three}. Failed provider cases: {len(failures)}.",
           "If none has three winners, do not promise three different paths to judges. The objectives can legitimately agree; inspect objective scores and model factors instead."]
    (OUT/"Safe-Navi-Verified-Route-List.md").write_text("\n".join(rows),encoding="utf-8")


if __name__=="__main__":main()
