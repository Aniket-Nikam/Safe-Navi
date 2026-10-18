"""Descriptive labels from verified record text; not crime-frequency statistics."""
import re

CRIME_TERMS = {
    "Theft / burglary": r"\b(theft|stolen|steal|burglary|pickpocket\w*)\b",
    "Robbery / snatching": r"\b(robbery|robbed|snatching|mugging)\b",
    "Assault / violence": r"\b(assault\w*|attack\w*|violence|murder|stabbing)\b",
    "Harassment": r"\b(harass\w*|stalking|molest\w*)\b",
    "Fraud / scam": r"\b(fraud\w*|scam\w*|cheating)\b",
    "Vandalism": r"\b(vandalis\w*|vandaliz\w*)\b",
}

def crime_labels(hazard: dict) -> list[str]:
    if "CRIME" not in str(hazard.get("category_key", "")).upper(): return []
    text=" ".join(str(hazard.get(key) or "") for key in ("title","description","reason")).lower()
    return [name for name,pattern in CRIME_TERMS.items() if re.search(pattern,text)] or ["Crime type unspecified"]
