"""Review-only RSS intelligence. Signals never create hazards automatically."""
from __future__ import annotations

from datetime import datetime, timezone
import hashlib
import os
import re
from urllib.parse import urlparse
from urllib.request import Request, urlopen
import xml.etree.ElementTree as ET

from .product_database import record_news_ingestion, store_news_signal


CATEGORY_TERMS = {
    "FLOODING": ("flood", "waterlogging", "heavy rain", "inundat"),
    "CRIME": ("crime", "robbery", "assault", "theft", "police"),
    "TRAFFIC": ("traffic", "collision", "accident", "road closed", "congestion"),
    "CIVIC": ("pothole", "streetlight", "garbage", "drain", "construction"),
}
KNOWN_LOCATIONS = ("Navi Mumbai", "Vashi", "Belapur", "Nerul", "Airoli", "Kharghar", "Panvel", "Thane")


def configured_feeds() -> list[str]:
    return [item.strip() for item in os.getenv("SAFE_NAVI_NEWS_FEEDS", "").split(",") if item.strip()]


def classify(headline: str, summary: str) -> tuple[str, float]:
    text = f"{headline} {summary}".lower()
    scores = {category: sum(term in text for term in terms) for category, terms in CATEGORY_TERMS.items()}
    category, matches = max(scores.items(), key=lambda item: item[1])
    return (category if matches else "OTHER", min(0.92, 0.45 + matches * 0.12) if matches else 0.30)


def extract_location(text: str) -> str | None:
    lowered = text.lower()
    return next((name for name in KNOWN_LOCATIONS if name.lower() in lowered), None)


def _text(node: ET.Element | None) -> str:
    return "" if node is None or node.text is None else re.sub(r"<[^>]+>", " ", node.text).strip()


def parse_feed(content: bytes, publisher: str) -> list[dict]:
    root = ET.fromstring(content)
    items = root.findall(".//item")
    if not items:
        items = root.findall(".//{http://www.w3.org/2005/Atom}entry")
    values: list[dict] = []
    for item in items[:100]:
        atom = item.tag.endswith("entry")
        prefix = "{http://www.w3.org/2005/Atom}" if atom else ""
        headline = _text(item.find(prefix + "title"))
        summary = _text(item.find(prefix + ("summary" if atom else "description")))
        link_node = item.find(prefix + "link")
        source_url = (link_node.attrib.get("href") if atom and link_node is not None else _text(link_node)) or ""
        published = _text(item.find(prefix + ("updated" if atom else "pubDate")))
        if not headline or not source_url: continue
        category, confidence = classify(headline, summary)
        values.append({"publisher": publisher, "source_url": source_url, "headline": headline[:500],
                       "summary": summary[:3000], "published_at": published or datetime.now(timezone.utc).isoformat(),
                       "location_text": extract_location(f"{headline} {summary}"), "category": category,
                       "confidence": confidence})
    return values


def ingest_configured_feeds() -> dict:
    feeds = configured_feeds()
    inserted = 0; skipped = 0; errors: list[str] = []
    for url in feeds:
        parsed = urlparse(url)
        if parsed.scheme != "https" or not parsed.hostname:
            errors.append(f"Rejected non-HTTPS feed: {url}"); continue
        try:
            request = Request(url, headers={"User-Agent": "Safe-Navi/1.0 (+safety intelligence; review-only)"})
            with urlopen(request, timeout=12) as response:
                content = response.read(2_000_001)
            if len(content) > 2_000_000: raise ValueError("feed exceeds 2 MB")
            publisher = parsed.hostname.removeprefix("www.")
            for signal in parse_feed(content, publisher):
                if store_news_signal(signal): inserted += 1
                else: skipped += 1
        except Exception as error:
            errors.append(f"{parsed.hostname}: {type(error).__name__}")
    schedule = record_news_ingestion() if feeds and len(errors) < len(feeds) else None
    return {"feeds": len(feeds), "inserted": inserted, "deduplicated": skipped, "errors": errors,
            "last_ingested_at": schedule.get("last_ingested_at") if schedule else None,
            "next_ingestion_at": schedule.get("next_ingestion_at") if schedule else None,
            "policy": "Signals require government review and never create hazards automatically."}
