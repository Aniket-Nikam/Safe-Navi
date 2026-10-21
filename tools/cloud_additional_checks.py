"""Additional real-cloud account and simulated-journey checks; no emergency calls."""
import json
import secrets
import cloud_rehearsal as qa


def main():
    qa.state=json.loads(qa.ACCESS.read_text(encoding="utf-8"))
    first,second=qa.state["accounts"]
    def login(account):
        return qa.ok("/api/v1/auth/login","POST",dict(email=account["email"],password=account["password"]))["access_token"]
    a,b=login(first),login(second)
    journey=None
    try:
        journey=qa.ok("/api/v1/journeys","POST",dict(source_label="DEMO simulated start — Thane station",
            destination_label="DEMO simulated destination — CBD Belapur",
            destination_latitude=19.0209123,destination_longitude=73.0398886,travel_mode="DRIVING",
            route_profile="BALANCED",route_summary="DEMO simulation, not physical travel or GPS telemetry",
            latitude=19.1846144,longitude=72.9710278),a,201)
        path="/api/v1/journeys/"+journey["id"]
        qa.state["records"]["journey_id"]=journey["id"]
        qa.state["simulated_journey_share_url"]=qa.BASE+"/api/v1/journeys/share/"+journey["share_token"]+"/view"
        qa.save()
        qa.check("Journey owner isolation",qa.call(path+"/location","PATCH",dict(latitude=19.18,longitude=72.97),b)[0]==404,"Other citizen cannot update")
        qa.ok(path+"/location","PATCH",dict(latitude=19.18,longitude=72.97),a)
        qa.ok(path+"/check-in/schedule","POST",dict(interval_minutes=15),a)
        qa.ok(path+"/check-in","POST",{},a)
        code,share=qa.call("/api/v1/journeys/share/"+journey["share_token"])
        qa.check("Consented share privacy",code==200 and "email" not in share and "user_id" not in share,"Latest simulated position without account identity")
        code,html=qa.call("/api/v1/journeys/share/"+journey["share_token"]+"/view")
        qa.check("Phone share web view",code==200 and b"SAFE-NAVI" in html,"HTML share page loads; private link in access file")
        qa.ok(path+"/end","POST",{},a)
        qa.check("End journey persists",any(v["id"]==journey["id"] and v["status"]=="COMPLETED" and v["checkin_status"]=="OFF" for v in qa.ok("/api/v1/journeys",token=a)),"COMPLETED, check-in OFF; no pending simulated emergency")
        journey=None
        password=secrets.token_urlsafe(18);new_password=secrets.token_urlsafe(18)
        account=dict(name="Disposable QA account settings",email="qa-settings-"+secrets.token_hex(7)+"@safenavi.example",password=password)
        created=qa.ok("/api/v1/auth/register","POST",account,status=201)
        token=created["access_token"]
        try:
            qa.ok("/api/v1/account/password","POST",dict(current_password=password,new_password=new_password),token)
            qa.check("Old password rejected",qa.call("/api/v1/auth/login","POST",dict(email=account["email"],password=password))[0]==401,"Only disposable QA account changed")
            account["password"]=new_password;token=login(account)
            qa.check("New password works",qa.call("/api/v1/me",token=token)[0]==200,"Cloud password change verified")
        finally:
            qa.ok("/api/v1/account","DELETE",dict(password=account["password"]),token)
        qa.check("Account deactivation revokes access",qa.call("/api/v1/me",token=token)[0]==401,"Disposable account only; retained demo citizens unchanged")
    finally:
        if journey:
            # Idempotent end, even if an assertion interrupted the script.
            qa.call("/api/v1/journeys/"+journey["id"]+"/end","POST",{},a)
        qa.save()
        (qa.OUT/"Safe-Navi-Cloud-Additional-Checks.json").write_text(json.dumps(qa.events,indent=2),encoding="utf-8")


if __name__=="__main__":main()
