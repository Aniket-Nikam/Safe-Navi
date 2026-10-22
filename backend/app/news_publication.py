"""Explicit official verification bridges external intelligence into existing hazard routing."""
import hashlib
import uuid
from datetime import datetime, timedelta, timezone
from urllib.parse import urlsplit
from .product_database import connect_product, row_dict, utc_now


def publish_news(signal_id, actor, payload):
    if actor["role"]!="government": raise ValueError("Only safety officials can publish news hazards")
    if not payload.get("confirmed_current"): raise ValueError("Confirm the current incident and precise affected road first")
    with connect_product() as db:
        db.execute("BEGIN IMMEDIATE")
        signal=db.execute("SELECT * FROM news_signals WHERE id=?",(signal_id,)).fetchone()
        if signal is None: return None
        previous=db.execute("SELECT hazard_id FROM news_hazard_links WHERE signal_id=?",(signal_id,)).fetchone()
        if previous: return row_dict(db.execute("SELECT * FROM hazards WHERE id=?",(previous["hazard_id"],)).fetchone())
        if signal["review_status"]=="DISMISSED": raise ValueError("Reopen this dismissed signal for review before publishing")
        url=urlsplit(signal["source_url"])
        if url.scheme!="https" or not url.hostname or url.username: raise ValueError("A valid HTTPS source article is required")
        now=utc_now(); expiry=(datetime.now(timezone.utc)+timedelta(hours=payload["valid_hours"])).isoformat()
        hazard_id="haz_news_"+hashlib.sha256(signal_id.encode()).hexdigest()[:32]
        category={"TRAFFIC":"ROAD_INCIDENT","CIVIC":"CIVIC_ISSUE","OTHER":"CIVIC_ISSUE"}.get(signal["category"],signal["category"])
        note=payload["verification_note"].strip()
        reason=note+"\nVerified news source: "+signal["publisher"]+"\n"+signal["source_url"]
        created=db.execute("""INSERT OR IGNORE INTO hazards(id,report_id,title,category_key,description,latitude,longitude,address,
            severity,status,road_status,reason,verified_by,source,created_at,updated_at,valid_until,geometry_type,no_go_radius_m,passability)
            VALUES (?,NULL,?,?,?,?,?,?,?,'ACTIVE',?,?,?,'NEWS_VERIFIED',?,?,?,'POINT',?,?)""",
            (hazard_id,signal["headline"],category,signal["summary"],payload["latitude"],payload["longitude"],payload["address"],
             payload["severity"],payload["road_status"],reason,actor["id"],now,now,expiry,payload.get("no_go_radius_m"),payload.get("passability") or "UNKNOWN"))
        db.execute("INSERT OR IGNORE INTO news_hazard_links VALUES (?,?,?,?,?)",(signal_id,hazard_id,actor["id"],now,note))
        db.execute("UPDATE news_signals SET review_status='PUBLISHED',latitude=?,longitude=?,linked_hazard_id=? WHERE id=?",(payload["latitude"],payload["longitude"],hazard_id,signal_id))
        if created.rowcount:
            db.execute("INSERT INTO hazard_history VALUES (?,?,?,?,?,?,?,?,?)",("hst_"+uuid.uuid4().hex,hazard_id,None,actor["id"],"NEWS_HAZARD_VERIFIED",None,"ACTIVE",reason,now))
        return row_dict(db.execute("SELECT * FROM hazards WHERE id=?",(hazard_id,)).fetchone())
