"""Read-only search for demonstrable real routes using current public providers."""
import json
import time
import urllib.parse
import urllib.request
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/"tmp"/"acceptance-2026-10-21"
BASE="https://safe-navi-api.onrender.com"
HEADERS={"User-Agent":"Safe-Navi/1.7.2 acceptance (github.com/Aniket-Nikam/Safe-Navi)",
         "Accept":"application/json","X-Client-Id":"github.com/Aniket-Nikam/Safe-Navi"}

def request(url,payload=None):
    data=None if payload is None else json.dumps(payload).encode()
    headers={**HEADERS,**({"Content-Type":"application/json"} if data else {})}
    with urllib.request.urlopen(urllib.request.Request(url,data=data,headers=headers),timeout=100) as response:
        return json.load(response)

def decode(shape):
    index=lat=lon=0; points=[]
    while index<len(shape):
        values=[]
        for _ in range(2):
            value=shift=0
            while True:
                char=ord(shape[index])-63;index+=1;value|=(char&31)<<shift;shift+=5
                if char<32:break
            values.append(~(value>>1) if value&1 else value>>1)
        lat+=values[0];lon+=values[1]
        points.append(dict(latitude=lat/1e6,longitude=lon/1e6))
    return points

def place(query):
    url="https://photon.komoot.io/api/?"+urllib.parse.urlencode(dict(q=query,limit=8,
        bbox="72.65,18.85,73.35,19.55",lat=19.05,lon=73.0,lang="en"))
    features=request(url)["features"]
    valid=[f for f in features if 72.65<=f["geometry"]["coordinates"][0]<=73.35
           and 18.85<=f["geometry"]["coordinates"][1]<=19.55]
    if not valid:raise ValueError("No in-region place match")
    def score(feature):
        p=feature["properties"];name=p.get("name","").lower()
        terms=query.lower().split()
        return (p.get("osm_value")=="station",sum(t in name for t in terms),
                not any(x in name for x in ("parking","bus stop","toilet")))
    match=max(valid,key=score);p=match["properties"];lon,lat=match["geometry"]["coordinates"]
    return dict(query=query,name=p.get("name"),address=", ".join(str(p[k]) for k in
        ("name","street","district","city","state","country") if p.get(k)),latitude=lat,longitude=lon)

def main():
    OUT.mkdir(parents=True,exist_ok=True)
    queries=["Vashi Railway Station","CBD Belapur Railway Station","Airoli Railway Station",
             "Thane Railway Station","Nerul Railway Station","Nahur Railway Station","Kharghar Railway Station"]
    places={}
    for q in queries:
        try:places[q]=place(q);print("PLACE",q,places[q]["address"],flush=True)
        except Exception as error:print("PLACE_ERROR",q,type(error).__name__,flush=True)
        time.sleep(1)
    places["SIES GST, Nerul"]=dict(query="SIES GST, Nerul",name="SIES GST, Nerul",
        address="Previously verified exact college pin",latitude=19.042644,longitude=73.023428)
    pairs=[("Vashi Railway Station","CBD Belapur Railway Station"),
           ("Nahur Railway Station","SIES GST, Nerul"),
           ("Airoli Railway Station","CBD Belapur Railway Station"),
           ("Thane Railway Station","Nerul Railway Station"),
           ("Vashi Railway Station","Kharghar Railway Station"),
           ("Thane Railway Station","CBD Belapur Railway Station"),
           ("Airoli Railway Station","SIES GST, Nerul"),
           ("Kharghar Railway Station","Vashi Railway Station"),
           ("Nerul Railway Station","Thane Railway Station"),
           ("CBD Belapur Railway Station","Nahur Railway Station")]
    report=dict(checked_at=time.strftime("%Y-%m-%dT%H:%M:%S%z"),places=places,
                health=request(BASE+"/health"),cases=[])
    hazards=request(BASE+"/api/v1/hazards/map")
    report["active_hazard_count"]=hazards.get("count",len(hazards.get("hazards",[])))
    for first,second in pairs:
        if first not in places or second not in places:continue
        a,b=places[first],places[second]
        try:
            raw=request("https://valhalla1.openstreetmap.de/route",dict(
                locations=[dict(lat=a["latitude"],lon=a["longitude"]),dict(lat=b["latitude"],lon=b["longitude"])],
                costing="auto",alternates=2,directions_type="none",directions_options=dict(units="kilometers")))
            trips=[raw["trip"]]+[v["trip"] for v in raw.get("alternates",[]) if "trip" in v]
            candidates=[]
            for i,trip in enumerate(trips):
                coordinates=[]
                for leg in trip["legs"]:coordinates.extend(decode(leg["shape"]))
                candidates.append(dict(route_id=f"road-{i+1}",travel_minutes=trip["summary"]["time"]/60,
                    distance_meters=trip["summary"]["length"]*1000,coordinates=coordinates))
            for period in ("morning_peak","midday"):
                evaluated=request(BASE+"/api/v1/risk/route",dict(time_period=period,profile="balanced",routes=candidates))
                case=dict(source=first,destination=second,time_period=period,
                    distinct=evaluated["distinct_recommended_routes"],provider=evaluated["risk_provider"],
                    recommendations=evaluated["recommendations"],candidate_count=len(candidates))
                report["cases"].append(case)
                print("ROUTE",first,"->",second,period,"DISTINCT",case["distinct"],
                      json.dumps(case["recommendations"]),flush=True)
                (OUT/"verified-routes.json").write_text(json.dumps(report,indent=2),encoding="utf-8")
            time.sleep(1)
        except Exception as error:print("ROUTE_ERROR",first,second,type(error).__name__,str(error),flush=True)
    (OUT/"verified-routes.json").write_text(json.dumps(report,indent=2),encoding="utf-8")
    print("ROUTE_REPORT",OUT/"verified-routes.json",flush=True)

if __name__=="__main__":main()
