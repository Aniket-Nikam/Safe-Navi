"""Run from Task Scheduler/cron to ingest configured, permitted HTTPS RSS feeds."""
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app.news_intelligence import ingest_configured_feeds


if __name__ == "__main__":
    result = ingest_configured_feeds()
    print(f"feeds={result['feeds']} inserted={result['inserted']} deduplicated={result['deduplicated']}")
    for error in result["errors"]:
        print(f"warning={error}")
