"""Distance-based display coverage. Does not change trained route inference or closure checks."""
from math import radians, sin, cos, atan2, sqrt


def distance(a, b):
    p, q = radians(a[0]), radians(b[0])
    h = sin((q-p)/2)**2 + cos(p)*cos(q)*sin(radians(b[1]-a[1])/2)**2
    return 6371000 * 2 * atan2(sqrt(h), sqrt(max(0, 1-h)))


def partition(coordinates, maximum=128):
    """Preserve every bend, splitting into roughly 500 m intervals, bounded by maximum."""
    road = [(p.latitude, p.longitude) for p in coordinates]
    total = sum(distance(a, b) for a, b in zip(road, road[1:]))
    step = max(500.0, total / maximum + .01)
    chunks, shape, accumulated = [], [road[0]], 0.0
    for end in road[1:]:
        start = shape[-1]
        remaining = distance(start, end)
        while accumulated + remaining >= step and remaining > .01:
            ratio = (step - accumulated) / remaining
            boundary = (start[0] + ratio*(end[0]-start[0]), start[1] + ratio*(end[1]-start[1]))
            shape.append(boundary); chunks.append((shape, step))
            shape, accumulated, start = [boundary], 0.0, boundary
            remaining = distance(start, end)
        shape.append(end); accumulated += remaining
    if accumulated > .01 or not chunks:
        chunks.append((shape, accumulated))
    return chunks


def explain(coordinates, period, lookup, predict):
    sections, cache, offset = [], {}, 0.0
    for index, (shape, length) in enumerate(partition(coordinates)):
        remaining = length / 2
        center = shape[0]
        for a, b in zip(shape, shape[1:]):
            size = distance(a, b)
            if size >= remaining and size > 0:
                ratio = remaining / size
                center = (a[0]+ratio*(b[0]-a[0]), a[1]+ratio*(b[1]-a[1])); break
            remaining -= size
        cell = lookup(center[0], center[1], period)
        risk, source, area, reason = None, "unknown", "Outside dataset coverage", "No area-cell data here. Unassessed does not mean safe."
        if cell:
            key = cell.get("area_cell_id") or center
            if key not in cache:
                prediction, _, _ = predict(factors=cell["factors"], time_period=period,
                        jurisdiction=cell["jurisdiction"], latitude=center[0], longitude=center[1])
                # Reuse a cell's prediction so display density does not create artificial variation.
                cache[key] = prediction
            prediction = cache[key]
            risk = prediction["risk_exposure"] if prediction else cell["risk_score"]
            source = "trained_ml_area_estimate" if prediction else "synthetic_baseline_estimate"
            area = cell["area_name"]
            factors = prediction.get("factor_contributions", {}) if prediction else cell["factors"]
            leading = sorted(((k,v) for k,v in factors.items() if v > 0), key=lambda item:item[1], reverse=True)[:3]
            labels = ", ".join(f"{k.replace('_index','').replace('_',' ')} {v:+.1f}" for k,v in leading)
            reason = "Synthetic area estimate, not a verified incident or guarantee of safety. " + ("Leading factors: " + labels if labels else "Area-cell baseline.")
        band = "unknown" if risk is None else "high" if risk >= 70 else "elevated" if risk >= 50 else "moderate" if risk >= 35 else "lower"
        sections.append(dict(id=f"section_{index}", coordinates=[dict(latitude=p[0],longitude=p[1]) for p in shape],
                latitude=center[0], longitude=center[1], distance_from_start_m=round(offset,1),
                length_m=round(length,1), risk_exposure=None if risk is None else round(risk,2),
                band=band, source=source, area_name=area, reason=reason, verified=False))
        offset += length
    return sections


def markers(sections, official):
    """One representative elevated estimate per journey eighth, not just the highest cluster."""
    total = sum(s["length_m"] for s in sections)
    bins = {}
    for section in sections:
        if section["risk_exposure"] is None or section["risk_exposure"] < 50:
            continue
        bucket = min(7, int(section["distance_from_start_m"] / max(total, 1) * 8))
        if bucket not in bins or section["risk_exposure"] > bins[bucket]["risk_exposure"]:
            bins[bucket] = section
    output = list(official)
    seen = set()
    for section in bins.values():
        # Keep markers legible; the continuous layer still shows every section.
        key = (section["area_name"], round(section["latitude"], 2), round(section["longitude"], 2))
        if key in seen: continue
        seen.add(key)
        output.append(dict(id="estimate_"+section["id"], latitude=section["latitude"], longitude=section["longitude"],
            title="Estimated exposure · "+section["area_name"], reason=section["reason"], verified=False,
            source=section["source"], risk_exposure=section["risk_exposure"]))
    return output
