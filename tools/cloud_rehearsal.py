"""Real cloud rehearsal. Retain explicitly labelled DEMO content for phone inspection.

Never publish a fabricated hazard or imply independent user adoption. No local backend.
Credentials go only to the Git-ignored private handoff file.
"""
import hashlib
import json
from pathlib import Path
import secrets
import time
from datetime import datetime, timezone, timedelta
import urllib.error
import urllib.request

ROOT=Path(__file__).resolve().parents[1]
BASE="https://safe-navi-api.onrender.com"
OUT=ROOT/"deliverables"
ACCESS=OUT/"Safe-Navi-Cloud-Rehearsal-Access.private.json"
LOG=OUT/"Safe-Navi-Cloud-Feature-Log.jsonl"
RECEIPT=OUT/"Safe-Navi-Cloud-Rehearsal.json"
events=[]; state={}


def record(feature,result,detail,phone=""):
    item=dict(time_ist=datetime.now(timezone(timedelta(hours=5,minutes=30))).isoformat(timespec="seconds"),
              feature=feature,result=result,detail=detail,phone=phone)
    events.append(item)
    with LOG.open("a",encoding="utf-8") as file:file.write(json.dumps(item,ensure_ascii=False)+"\n")
    print(result,feature,detail,flush=True)


def check(feature,ok,detail,phone=""):
    record(feature,"PASS" if ok else "FAIL",detail,phone)
    if not ok:raise AssertionError(feature+": "+detail)


def call(path,method="GET",payload=None,token=None,raw=None,content_type=None):
    headers={"Accept":"application/json","User-Agent":"Safe-Navi cloud rehearsal"}
    if token:headers["Authorization"]="Bearer "+token
    data=json.dumps(payload).encode() if payload is not None else raw
    if data is not None:headers["Content-Type"]=content_type or "application/json"
    req=urllib.request.Request(BASE+path,data=data,headers=headers,method=method)
    started=time.monotonic()
    try:
        with urllib.request.urlopen(req,timeout=125) as response:
            body=response.read();code=response.status
            value=json.loads(body) if "json" in response.headers.get("Content-Type","") else body
    except urllib.error.HTTPError as error:
        code=error.code
        try:value=json.loads(error.read())
        except ValueError:value={}
    safe_path="/api/v1/journeys/share/[private-link]" if "/journeys/share/" in path else path
    record("HTTP "+method+" "+safe_path,"OBSERVED",f"HTTP {code}; {time.monotonic()-started:.2f}s")
    return code,value


def save():
    ACCESS.write_text(json.dumps(state,indent=2),encoding="utf-8")


def ok(path,method="GET",payload=None,token=None,status=200):
    code,value=call(path,method,payload,token)
    check(method+" "+path,code==status,"HTTP "+str(code))
    return value


def main():
    global state
    OUT.mkdir(exist_ok=True)
    state=json.loads(ACCESS.read_text(encoding="utf-8")) if ACCESS.exists() else dict(
        server=BASE,run_id="demo-"+secrets.token_hex(6),accounts=[],records={})
    run=state["run_id"]; records=state["records"]
    try:
        health=ok("/health")
        check("Cloud stores / ML",health.get("risk_provider")=="trained_ml" and health.get("product_store")=="postgresql"
              and health.get("evidence_store")=="supabase",json.dumps(health),BASE+"/health")
        state["model_metadata"]=ok("/api/v1/risk/provider")
        for name in ("Demo Anaya","Demo Rohan"):
            if any(a["name"]==name for a in state["accounts"]):continue
            email=name.lower().replace(" ",".")+"."+run+"@safenavi.example"
            password=secrets.token_urlsafe(18)
            account=ok("/api/v1/auth/register","POST",dict(name=name,email=email,password=password),status=201)
            state["accounts"].append(dict(name=name,email=email,password=password,id=account["user"]["id"]))
            save()
        first,second=state["accounts"]; tokens=[]
        for account in state["accounts"]:
            result=ok("/api/v1/auth/login","POST",dict(email=account["email"],password=account["password"]))
            tokens.append(result["access_token"])
            check("Session "+account["name"],ok("/api/v1/me",token=tokens[-1])["id"]==account["id"],"Cloud identity matches")
        a,b=tokens
        for path in ("/api/v1/admin/accounts","/api/v1/admin/app-settings","/api/v1/admin/audit",
                     "/api/v1/government/news-signals","/api/v1/government/metrics"):
            check("Citizen role protection "+path,call(path,token=a)[0]==403,"HTTP 403")
        check("Anonymous community privacy",call("/api/v1/community/posts")[0]==401,"Sign-in required")
        image=(ROOT/"app/src/main/res/drawable/ic_icon.jpg").read_bytes()
        if "evidence_id" not in records:
            boundary="demo-"+secrets.token_hex(8)
            body=("--"+boundary+'\r\nContent-Disposition: form-data; name="file"; filename="DEMO-app-logo-not-incident.jpg"\r\nContent-Type: image/jpeg\r\n\r\n').encode()+image+("\r\n--"+boundary+"--\r\n").encode()
            code,evidence=call("/api/v1/evidence","POST",token=a,raw=body,content_type="multipart/form-data; boundary="+boundary)
            check("Supabase image upload",code==201,"App-logo JPEG, not an incident photograph")
            records["evidence_id"]=evidence["id"];save()
            check("Unshared evidence hidden from B",call("/api/v1/evidence/"+evidence["id"],token=b)[0]==404,"Private until explicitly shared")
        evidence=records["evidence_id"];code,download=call("/api/v1/evidence/"+evidence,token=a)
        check("Photo integrity",code==200 and hashlib.sha256(download).digest()==hashlib.sha256(image).digest(),"JPEG bytes match")
        check("Anonymous image privacy",call("/api/v1/evidence/"+evidence)[0]==401,"HTTP 401")
        content="DEMO REHEARSAL — seeded demonstration, not independent user-adoption evidence. We are comparing Thane Railway Station (Kopri) to CBD Belapur station. Fastest, Balanced and Safest are three objectives; they need not select three different roads. Which trade-off would you choose? The app-logo image tests sharing; it is not an incident photograph."
        payload=dict(category="question",content=content,evidence_id=evidence,client_request_id=run+"-post-a")
        post=ok("/api/v1/community/posts","POST",payload,a,201);records["post_id"]=post["id"];save()
        repeat=ok("/api/v1/community/posts","POST",payload,a,201)
        check("Post retry idempotency",repeat["id"]==post["id"],"One post despite retry")
        path="/api/v1/community/posts/"+post["id"]
        payload=dict(content="DEMO participant: I would inspect Fastest first for an appointment, then compare the assessed exposure. A lower estimate is not a guarantee of safety.",client_request_id=run+"-comment-b")
        comment=ok(path+"/comments","POST",payload,b,201)
        repeat=ok(path+"/comments","POST",payload,b,201)
        check("Comment retry idempotency",repeat["id"]==comment["id"],"One comment despite retry")
        ok(path+"/comments","POST",dict(content="DEMO reply: agreed. Route risks shows the explanation; official alerts are separate from baseline estimates.",parent_id=comment["id"],client_request_id=run+"-reply-a"),a,201)
        comments=ok(path+"/comments",token=a)
        check("Cross-account nested discussion",any(v.get("parent_id")==comment["id"] for v in comments),"Reply persisted","Community → DEMO REHEARSAL → Discuss")
        records["comments"]=[v["id"] for v in comments]
        for token in (a,b):
            feed=ok("/api/v1/community/posts",token=token);item=next(v for v in feed if v["id"]==post["id"])
            if not item.get("upvoted"):ok(path+"/vote","POST",{},token)
        code,download=call("/api/v1/evidence/"+evidence,token=b)
        check("Shared image visible to second citizen",code==200 and hashlib.sha256(download).digest()==hashlib.sha256(image).digest(),"Shared JPEG returned","Community → photograph")
        search=ok("/api/v1/community/posts?q=DEMO%20REHEARSAL",token=b)
        check("Community search",any(v["id"]==post["id"] for v in search),"Post found")
        payload=dict(title="DEMO REHEARSAL — case and evidence review",category_key="OTHER",
            description="DEMO ONLY. A software rehearsal, not an unsafe-area report. Do not verify or publish a hazard. The app-logo attachment tests photo access. An official can request demo information or reject the rehearsal case without changing public map conditions.",
            latitude=19.0637159,longitude=73.0002509,address="DEMO ONLY — Vashi station pin, not an incident",
            evidence_ids=[evidence],client_request_id=run+"-report-a")
        case=ok("/api/v1/reports","POST",payload,a,201);records["report_id"]=case["id"];save()
        repeat=ok("/api/v1/reports","POST",payload,a,201)
        check("Report retry idempotency",repeat["id"]==case["id"],"One report despite retry")
        path="/api/v1/reports/"+case["id"]
        ok(path,token=a)
        check("Report ownership",call(path,token=b)[0]==404,"Other citizen cannot read this case")
        payload=dict(content="DEMO follow-up: this logo is an upload test, not evidence of danger. Please keep the rehearsal case unverified.",evidence_id=evidence,client_request_id=run+"-message-a")
        message=ok(path+"/messages","POST",payload,a,201)
        repeat=ok(path+"/messages","POST",payload,a,201)
        check("Case message retry idempotency",repeat["id"]==message["id"],"No duplicate message")
        thread=ok(path+"/messages",token=a)
        check("Private case / photo persistence",any(v["id"]==message["id"] and v.get("evidence_id")==evidence for v in thread),"Message and photograph linked","Demo Anaya → Report → History → DEMO → Conversation")
        check("Private conversation ownership",call(path+"/messages",token=b)[0]==404,"Second citizen denied")
        hazards=ok("/api/v1/hazards/map")
        check("Unverified report cannot change shared map",all(v.get("report_id")!=case["id"] for v in hazards.get("hazards",[])),"No fictional hazard published")
        for label,path,payload in (
            ("saved_place","/api/v1/saved-places",dict(label="DEMO · CBD Belapur station",address="CBD Belapur Railway Station, Belapur East",latitude=19.0209123,longitude=73.0398886)),
            ("contact","/api/v1/emergency-contacts",dict(name="DEMO — DO NOT CALL",phone="+0000000000",relationship="Invalid test number"))):
            if label not in records:records[label]=ok(path,"POST",payload,a,201)["id"];save()
            check(label+" persistence",any(v["id"]==records[label] for v in ok(path,token=a)),"Visible in demo profile","Demo Anaya → Profile")
            check(label+" isolation",all(v["id"]!=records[label] for v in ok(path,token=b)),"Not visible to other citizen")
            temporary=ok(path,"POST",payload,a,201);ok(path+"/"+temporary["id"],"DELETE",token=a)
        ok("/api/v1/notifications",token=a)
        answer=ok("/api/v1/assistant/chat","POST",dict(message="Briefly explain estimated risk versus an official closure. Do not claim a current incident.",history=[]),a)
        check("Live Navi",bool(answer.get("answer")),"Cloud assistant answered")
        state["assistant_example"]=answer.get("answer")
        from websockets.sync.client import connect
        with connect(BASE.replace("https://","wss://")+"/api/v1/live",open_timeout=40) as socket:socket.send("DEMO handshake")
        record("Hazard-update WebSocket","PASS","TLS handshake; no fabricated broadcast")
        ok("/api/v1/auth/logout","POST",{},a)
        check("Logout revokes session",call("/api/v1/me",token=a)[0]==401,"Old token invalid")
        ok("/api/v1/auth/login","POST",dict(email=first["email"],password=first["password"]))
        check("Other citizen session unaffected",call("/api/v1/me",token=b)[0]==200,"B remains valid")
        record("Official/admin positive workflows","NEEDS_LOGIN","Current privileged cloud login required. No privilege bypass or promotion performed.")
        record("Official hazard/news publication","NOT_RUN_ON_PUBLIC_MAP","No fabricated crime, news or closure was published.")
        record("GPS / dialler / background / offline","MANUAL_PHONE","Actual-phone hardware/OS/offline behaviour cannot be established by hosted API calls.")
        record("Independent adoption","NOT_CLAIMED","Seeded accounts/activity explicitly labelled DEMO; teammates can add their real replies.")
        record("Phone handoff","READY","Demo accounts, threaded conversation, photo, unverified case, saved place and invalid demo contact retained.")
    except Exception as error:
        record("Rehearsal interrupted","FAIL",type(error).__name__+": "+str(error)[:250]);raise
    finally:
        save()
        RECEIPT.write_text(json.dumps(dict(server=BASE,run_id=state["run_id"],records=state["records"],events=events),indent=2),encoding="utf-8")


if __name__=="__main__":main()
