"""Private, disposable cloud acceptance data. No public posts, reports or hazards."""
import base64
import hashlib
import json
from pathlib import Path
import secrets
import time
import urllib.error
import urllib.request

BASE="https://safe-navi-api.onrender.com"
OUT=Path(__file__).resolve().parents[1]/"tmp"/"acceptance-2026-10-21"/"cloud-functions.json"
results=[];accounts=[];evidence_id=None

def call(path,method="GET",payload=None,token=None,raw=None,content_type=None):
    body=json.dumps(payload).encode() if payload is not None else raw
    headers={"Accept":"application/json"}
    if body is not None:headers["Content-Type"]=content_type or "application/json"
    if token:headers["Authorization"]="Bearer "+token
    req=urllib.request.Request(BASE+path,data=body,headers=headers,method=method)
    try:
        with urllib.request.urlopen(req,timeout=90) as response:
            data=response.read();mime=response.headers.get("Content-Type","")
            return response.status,json.loads(data) if "json" in mime else data
    except urllib.error.HTTPError as error:
        data=error.read()
        try:data=json.loads(data)
        except ValueError:data=data.decode(errors="replace")
        return error.code,data

def check(name,condition,detail=""):
    value=dict(check=name,passed=bool(condition),detail=detail);results.append(value)
    print(("PASS" if condition else "FAIL"),name,detail,flush=True)
    if not condition:raise AssertionError(name+": "+detail)

def main():
    global evidence_id
    saved=contact=journey=None
    try:
        for i in range(2):
            password=secrets.token_urlsafe(24);email="qa-"+secrets.token_hex(8)+"@example.test"
            status,value=call("/api/v1/auth/register","POST",dict(name="Temporary private acceptance",email=email,password=password))
            check("cloud registration "+str(i+1),status==201,"HTTP "+str(status))
            accounts.append(dict(token=value["access_token"],password=password,email=email))
        owner,other=accounts
        token=owner["token"]
        check("session authenticated",call("/api/v1/me",token=token)[0]==200)
        check("citizen cannot read admin accounts",call("/api/v1/admin/accounts",token=token)[0]==403)
        check("citizen cannot enter official news queue",call("/api/v1/government/news-signals",token=token)[0]==403)
        status,saved=call("/api/v1/saved-places","POST",dict(label="QA only: Vashi",address="Vashi station - test fixture",latitude=19.0632,longitude=72.9988),token)
        check("saved destination create",status==201)
        check("saved destination persists",any(v["id"]==saved["id"] for v in call("/api/v1/saved-places",token=token)[1]))
        status,contact=call("/api/v1/emergency-contacts","POST",dict(name="QA contact - do not call",phone="+919000000000",relationship="Test only"),token)
        check("emergency contact create",status==201)
        check("emergency contact account isolation",not call("/api/v1/emergency-contacts",token=other["token"])[1])
        png=base64.b64decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a89sAAAAASUVORK5CYII=")
        boundary="qa-"+secrets.token_hex(12)
        body=("--"+boundary+'\r\nContent-Disposition: form-data; name="file"; filename="QA-only-one-pixel.png"\r\nContent-Type: image/png\r\n\r\n').encode()+png+("\r\n--"+boundary+"--\r\n").encode()
        status,image=call("/api/v1/evidence","POST",token=token,raw=body,content_type="multipart/form-data; boundary="+boundary)
        check("private Supabase photo upload",status==201,"HTTP "+str(status))
        evidence_id=image["id"]
        status,data=call("/api/v1/evidence/"+evidence_id,token=token)
        check("private Supabase photo download integrity",status==200 and isinstance(data,bytes) and hashlib.sha256(data).digest()==hashlib.sha256(png).digest())
        check("photo hidden from second account",call("/api/v1/evidence/"+evidence_id,token=other["token"])[0]==404)
        check("photo rejects anonymous access",call("/api/v1/evidence/"+evidence_id)[0]==401)
        status,journey=call("/api/v1/journeys","POST",dict(source_label="QA only: Vashi",destination_label="QA only: Nerul",
            destination_latitude=19.042644,destination_longitude=73.023428,travel_mode="DRIVING",route_profile="BALANCED",
            route_summary="Acceptance fixture - not a real journey",latitude=19.0632,longitude=72.9988),token)
        check("private journey creation",status==201)
        jid=journey["id"]
        check("journey update ownership",call("/api/v1/journeys/"+jid+"/location","PATCH",dict(latitude=19.06,longitude=73.0),other["token"])[0]==404)
        check("journey location update",call("/api/v1/journeys/"+jid+"/location","PATCH",dict(latitude=19.06,longitude=73.0),token)[0]==200)
        check("check-in schedule",call("/api/v1/journeys/"+jid+"/check-in/schedule","POST",dict(interval_minutes=15),token)[0]==200)
        check("check-in acknowledgement",call("/api/v1/journeys/"+jid+"/check-in","POST",{},token)[0]==200)
        status,shared=call("/api/v1/journeys/share/"+journey["share_token"])
        check("consented journey link hides identity",status==200 and "email" not in shared and "user_id" not in shared)
        status,answer=call("/api/v1/assistant/chat","POST",dict(message="Give two brief precautions for a flooded road. Do not claim a current incident.",history=[]),token)
        check("live Navi assistant",status==200 and bool(answer.get("answer")),"HTTP "+str(status))
        try:
            from websockets.sync.client import connect
            with connect("wss://safe-navi-api.onrender.com/api/v1/live",open_timeout=45) as socket:
                socket.send("acceptance check")
            check("live hazard-update socket handshake",True)
        except ImportError:
            results.append(dict(check="live hazard-update socket handshake",passed=None,detail="client dependency unavailable"))
        check("cloud community read",call("/api/v1/community/posts",token=token)[0]==200)
        check("cloud report history read",call("/api/v1/reports",token=token)[0]==200)
    finally:
        if accounts:
            owner=accounts[0];token=owner["token"]
            for value,path in ((saved,"/saved-places/"),(contact,"/emergency-contacts/")):
                if value and value.get("id"):print("CLEANUP",path,call("/api/v1"+path+value["id"],"DELETE",token=token)[0],flush=True)
            if journey and journey.get("id"):print("CLEANUP journey",call("/api/v1/journeys/"+journey["id"]+"/end","POST",{},token)[0],flush=True)
        for account in accounts:
            print("CLEANUP account",call("/api/v1/account","DELETE",dict(password=account["password"]),account["token"])[0],flush=True)
        OUT.parent.mkdir(parents=True,exist_ok=True)
        OUT.write_text(json.dumps(dict(results=results,retained_private_test_evidence_id=evidence_id,
            note="One private 68-byte fixture photo and an ended test journey remain as audit data; no public post, report or hazard created."),indent=2),encoding="utf-8")
        print("ACCEPTANCE_REPORT",OUT,flush=True)

if __name__=="__main__":main()
