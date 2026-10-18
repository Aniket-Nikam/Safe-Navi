"""Explicit localhost-only emulator fixture. Never imports cloud credentials or writes production data."""
import os
import sys
import tempfile
from pathlib import Path


def main():
    folder=Path(tempfile.mkdtemp(prefix="safenavi-ui-workflow-"))
    os.environ.update(SAFE_NAVI_DATABASE_URL="",SAFE_NAVI_PRODUCT_DB=str(folder/"product.sqlite"),
        SAFE_NAVI_EVIDENCE_DIR=str(folder/"evidence"),SAFE_NAVI_EVIDENCE_BACKEND="local",
        SAFE_NAVI_CLOUD_MODE="false",SAFE_NAVI_NEWS_FEEDS="",SAFE_NAVI_NEWS_SCHEDULER="internal",
        GROQ_API_KEY="",SAFE_NAVI_ML_ENDPOINT="")
    sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
    from app.product_database import create_user,create_report,create_report_message,store_news_signal
    from app.main import app
    citizen=create_user("UI test citizen","citizen@ui.test","ui-workflow-password","citizen")
    official=create_user("UI test official","official@ui.test","ui-workflow-password","government")
    case=create_report(citizen["id"],dict(title="UI TEST ONLY: street lighting",category_key="POOR_LIGHTING",description="Isolated emulator fixture. This is not a real incident.",latitude=19.07,longitude=73,address="Test-only Vashi location",anonymous=False,evidence_ids=[],client_request_id="fixture-report"))
    create_report_message(case["id"],official,"Please send a clear photograph of the affected streetlight.",None,"fixture-message")
    store_news_signal(dict(headline="UI TEST ONLY: Vashi road disruption",publisher="example.test",source_url="https://example.test/fixture",summary="Isolated UI fixture. Not real news; never published to the live map.",published_at="",location_text="Vashi",category="TRAFFIC",confidence=.57))
    print("LOCAL_FIXTURE_READY",flush=True)
    import uvicorn
    uvicorn.run(app,host="127.0.0.1",port=8017)


if __name__=="__main__": main()
