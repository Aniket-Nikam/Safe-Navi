# Safe-Navi — Likely Professor Questions and Answers

## Is this really AI or machine learning?

Not yet. The current 30% milestone uses an explainable deterministic risk engine. This is intentional: training an ML model on invented labels and calling it accurate would be misleading. The current engine creates a baseline and a stable interface. A later ML model must be trained and evaluated on a documented privacy-safe dataset and compared with this baseline.

## If the safety data is synthetic, what does the project prove?

It proves software and workflow feasibility: real road routes can be obtained, hazards can be modelled and verified, route exposure can be calculated, and different objectives can rank alternatives. It does not prove real-world predictive accuracy. That becomes a separate research question for later phases.

## Are you claiming to cover every street of Mumbai?

The dataset covers every eligible road-like OpenStreetMap way in the downloaded source snapshot inside the documented Mumbai and Navi Mumbai coverage polygons: 452,466 physical segments and 12,124.31 km. It cannot guarantee roads that were unmapped, private, newly built, outside the polygons, or incorrectly tagged. The Android app currently uses a small controlled hazard subset; full dataset runtime integration is next.

## Is the full Parquet dataset used by the Android app?

Not yet. The full `road_segments.parquet` file is approximately 158 MB and is intended for training, analytics, and server-side spatial lookup. Embedding and scanning it directly on a phone would make the APK unnecessarily large and inefficient. The current app uses controlled synthetic hazards to prove the interface and workflow. The next phase exposes Parquet-derived segment risk through FastAPI/PostGIS or a compact indexed mobile export.

## Why not use Google Maps?

The prototype uses MapLibre and OpenStreetMap-derived services to avoid requiring a Google Maps key and to keep the architecture provider-neutral. A production system could use a hosted OpenStreetMap provider, self-hosted infrastructure, or another licensed provider after cost, quota, privacy, and accuracy evaluation.

## Why Android Java now and Flutter later?

An existing Java/XML foundation could be reused to validate the difficult domain logic quickly. Rewriting immediately would spend time on framework migration rather than testing the research idea. Flutter is planned after the workflows and data contracts stabilize, allowing one Android/iOS client.

## How is the safest route calculated?

Valhalla first returns real alternative geometries. Safe-Navi measures each route's proximity to active hazards and derives exposure. Fastest uses travel time, Balanced combines travel and exposure, and Safest gives higher weight to exposure avoidance. It does not invent road geometry.

## What are the ten factors?

Category, severity, verification, spatial exposure, report age, lifecycle status, recurrence, confirmations, nearby hazard density, and source reliability/confidence. Travel time is then combined with hazard exposure when ranking routes.

## How do you avoid false reports?

Citizen reports and verified hazards are separate. Reports require review before becoming official hazards. The model includes role checks, verification status, duplicate matching, lifecycle history, and confirmations. Production will add trusted backend roles, evidence rules, rate limiting, and moderation.

## What happens when there is no data?

The app does not treat missing records as proof of safety. Risk and confidence are separate, so a high displayed safety value can still be accompanied by low confidence and an explanation that evidence is limited.

## What about bias?

Synthetic data prevents current records from stigmatizing real locations, but later models can still inherit sampling and reporting bias. Evaluation must include geographic splits, calibration, subgroup/area error analysis, ablation studies, and human review. Sensitive personal attributes should not be collected merely to improve the score.

## Can this be used during an emergency?

No. The prototype is not an emergency service and never guarantees safety. SOS and location sharing are deferred because they require consent, retention, abuse prevention, reliability, and threat modelling.

## Why are public map services not enough for production?

Public endpoints have usage policies, quotas, availability limitations, and privacy implications. They are appropriate for light testing only. Production should use a provider agreement or self-host tiles, geocoding, and routing with monitoring and capacity planning.

## What will you show at the next review?

Persistent reports and hazards, server-enforced roles, a reproducible and documented synthetic-data generator, richer moderation and notification workflows, Android instrumentation tests, and a baseline evaluation protocol for comparing the deterministic engine with ML candidates.
