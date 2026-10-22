"""Authoritative road restrictions, evaluated before any preference/ML ranking."""


def restriction_reason(hazard: dict) -> str | None:
    if hazard.get("road_status") == "CLOSED":
        return "ROAD_CLOSED"
    if hazard.get("severity") == "CRITICAL" and hazard.get("road_status") == "UNSAFE":
        return "CRITICAL_UNSAFE"
    if hazard.get("category_key") == "FLOODING":
        if hazard.get("passability") == "IMPASSABLE" or hazard.get("road_status") == "UNSAFE":
            return "FLOOD_IMPASSABLE"
        if hazard.get("passability") != "PASSABLE":
            return "FLOOD_PASSABILITY_UNCONFIRMED"
    return None


def flood_label(hazard: dict) -> str | None:
    if hazard.get("category_key") != "FLOODING":
        return None
    reason = restriction_reason(hazard)
    if reason == "FLOOD_PASSABILITY_UNCONFIRMED":
        return "Waterlogging · passability unconfirmed"
    if reason:
        return "Flooding · road blocked"
    return "Waterlogging · passable with caution"
