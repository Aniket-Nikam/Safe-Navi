# Safe-Navi — 30% Progress Report

## 1. Project statement

Safe-Navi is a community-safety and intelligent-navigation prototype for Mumbai and Navi Mumbai. Unlike a conventional navigation app that mainly minimizes time or distance, Safe-Navi demonstrates how verified civic hazards and explainable risk can also influence a route recommendation.

This submission is presented as **30% of the complete proposed research/product roadmap**. It is already a runnable engineering prototype, but it is not presented as a production safety system. The current data is privacy-safe and synthetic, the risk engine is rule-based, and public OpenStreetMap services are used only for a light academic demonstration.

## 2. Problem being addressed

The fastest route is not always the route a person would prefer. A traveller may want to avoid a poorly lit road, recurring flooding, an unsafe building, or another verified civic hazard. Existing reports are often unstructured, hard to verify, or disconnected from navigation.

Safe-Navi therefore combines three ideas:

1. citizens submit location-aware reports;
2. an authorized government user verifies, monitors, resolves, or rejects them; and
3. navigation compares real road routes by both travel cost and hazard exposure.

## 3. Work completed in this 30% milestone

### A. Runnable Android foundation

- Native Android application built with Java and XML.
- Citizen and government demonstration entry points.
- Credential-free synthetic demo mode, so the presentation does not depend on private keys.
- Existing Firebase/community/profile/authentication foundation preserved for later integration.

### B. Open map and real road routing

- MapLibre renders an OpenStreetMap-derived map.
- Nominatim converts submitted addresses into coordinates.
- Valhalla returns genuine road-network routes rather than invented straight lines.
- Driving, walking, and cycling modes are supported.
- Alternative routes can be ranked as Fastest, Balanced, or Safest.

### C. Explainable safety model

- Synthetic hazards represent examples such as flooding, poor lighting, and unsafe structures.
- Hazards have category, severity, location/geometry, lifecycle status, timestamp, recurrence, confirmations, and verification state.
- Risk considers severity, verification authority, distance, age, status, recurrence, confirmations, and local density.
- The app shows risk, safety score, confidence, and human-readable reasons separately.
- Resolved hazards remain in history but stop affecting immediate navigation.

### D. Citizen and government workflow

- A citizen can inspect local risk and submit a synthetic report.
- A government demo user can verify and publish, downgrade to monitoring, or resolve a report/hazard.
- Domain support also exists for rejection, duplicate detection, authorization, and append-only audit history.

### E. Verification completed

- Eleven local JVM tests pass.
- Tests cover risk behaviour, confidence, roles, lifecycle transitions, duplicate matching, route ranking, geocoding parsing, route parsing, and encoded-polyline decoding.
- Android lint, unit-test, and debug APK build tasks pass.

### F. City-scale synthetic dataset completed

- OpenStreetMap-grounded coverage contains 452,466 physical road segments: 263,055 in Mumbai and 189,411 in the documented Navi Mumbai coverage envelope.
- Four time periods produce 1,809,864 segment-time rows: morning peak, midday, evening peak, and night.
- Every row has exactly ten synthetic model factors and a synthetic continuous/categorical target.
- A 2 km spatial-block `train`/`validation`/`test` split is supplied to reduce geographic leakage.
- Automated QA passed: no null or out-of-range factors, no duplicate segment-period keys, valid road geometries, positive lengths, all four periods per segment, and balanced risk labels.
- The complete analytics table is stored separately as `road_segments.parquet` (approximately 158 MB), with CSV, GeoPackage, sample, manifest, quality report, data dictionary, and reproducible generator files.
- The full table is not embedded inside the Android APK. The app now calls FastAPI, which queries a 2.2 MB SQLite/RTree runtime containing all 6,508 one-kilometre area/time aggregates derived from the dataset. Exact street-segment PostGIS lookup remains the next resolution upgrade.

## 4. Technology used and why

| Technology | Use in Safe-Navi | Reason for choosing it |
|---|---|---|
| Java + XML Android | Mobile user interface and application logic | Existing project foundation was reusable, stable, and suitable for an Android college demonstration. |
| MapLibre Native | Interactive mobile map rendering | Open-source, provider-neutral map renderer; removes dependency on a Google Maps key. |
| OpenStreetMap / OpenFreeMap | Basemap and public road/map context | Open geographic ecosystem with appropriate attribution and no proprietary map lock-in for the prototype. |
| Nominatim | Source/destination address search | Converts a submitted place name into coordinates; used without autocomplete and with rate limiting for policy compliance. |
| Valhalla | Driving, walking, cycling route alternatives | Calculates real routes over an OpenStreetMap road graph and supports multiple costing profiles. |
| OkHttp | Network requests | Lightweight, established Android HTTP client already used in the project. |
| Firebase foundation | Authentication, database, storage, community features | Suitable for rapid prototyping, but secure roles and production workflows still require server-enforced rules/custom claims. |
| JUnit | Pure logic tests | Allows repeatable testing of risk and workflow rules without a device. |
| Deterministic weighted risk model | Current safety score and route ranking | Explainable and defensible when no legally obtained labelled training dataset exists. |

## 5. How route selection works

1. The user submits a source, destination, and travel mode.
2. Nominatim resolves text locations into latitude/longitude.
3. Valhalla returns one or more real candidate routes for the selected mode.
4. Safe-Navi decodes each route geometry and samples its proximity to active verified hazards.
5. Each candidate receives travel-time and hazard-exposure values.
6. **Fastest** prioritizes time, **Balanced** combines time and exposure, and **Safest** gives greater weight to hazard avoidance.
7. The selected route and explanation are displayed on the map.

The underlying risk calculation combines bounded hazard contributions:

`risk = 100 × (1 - product(1 - contribution / 100))`

The displayed safety score is `100 - risk`. Confidence is independent: limited evidence does not automatically mean that an area is safe.

## 6. What the synthetic dataset represents

The separate Mumbai–Navi Mumbai road dataset covers every eligible road-like OpenStreetMap way in its documented source snapshot and coverage polygons. “Every street” therefore means every eligible street mapped in that snapshot; it cannot guarantee unmapped, private, newly built, or incorrectly tagged streets. The dataset contains 12,124.31 km of physical road segments, 79,320 unique OSM way IDs, 3,869 unique street names, and 719 locality labels.

Each segment-time row contains ten synthetic factors:

1. traffic congestion;
2. crime risk;
3. lighting quality;
4. population density;
5. road condition;
6. pedestrian activity;
7. emergency access;
8. flood risk;
9. isolation; and
10. public-transport access.

The factor values and target labels are deterministic, spatially correlated simulations—not observed measurements. OpenStreetMap supplies road geometry and selected tags only. The Android workflow additionally uses a small set of fictional hazard scenarios to demonstrate verification, lifecycle and route-exposure behaviour. No private victim, offender, or individual movement data is used.

## 7. Why this is called 30%, despite being runnable

The denominator is the complete proposed system, not merely an Android screen. This first milestone proves technical feasibility and the core flow. The remaining work is more demanding because it includes secure persistence, data governance, model validation, ML experimentation, operational infrastructure, and cross-platform delivery.

| Full-roadmap area | Current state |
|---|---|
| Problem definition and architecture | Completed for prototype |
| Android user experience | Core demonstration completed |
| Open map and multimodal routing | Working prototype |
| Explainable risk and report lifecycle | Working with synthetic data |
| Persistent secure backend | Partial foundation only |
| City-scale synthetic/public feature pipeline | Dataset, SQLite runtime and FastAPI/Android integration completed at 1 km resolution |
| Trained and evaluated ML model | Not started; deliberately not faked |
| Moderation, alerts, offline use, accessibility | Future work |
| Flutter Android/iOS application | Future phase after validation |
| Production deployment and real authority partnerships | Future work |

## 8. Next phases

### Phase 2 — approximately 30% to 55%

- Persist reports, hazards, history, and saved routes.
- Enforce roles using Firebase custom claims/security rules or a FastAPI backend.
- Upgrade the working FastAPI/SQLite area-cell service to exact road-segment intersection using PostgreSQL/PostGIS and the full Parquet source.
- Preserve and publish the existing manifest, data dictionary, generator, OSM attribution, and quality checks.
- Add report evidence, moderation, notification, and map-filter workflows.
- Add Android instrumentation tests and conduct a usability study.

### Phase 3 — approximately 55% to 80%

- Build a reproducible feature-engineering pipeline using public geography and privacy-safe synthetic labels.
- Establish train/validation/test splits by time and geography.
- Train baseline models and compare them with the deterministic engine.
- Evaluate calibration, false reassurance, geographic bias, explainability, and ablation results.
- Self-host or procure map, geocoding, and routing infrastructure.

### Phase 4 — approximately 80% to 100%

- Build the finalized Flutter application for Android and iOS if the validated prototype warrants migration.
- Add SOS/location sharing only with explicit consent, retention controls, and threat modelling.
- Build the production government/admin dashboard.
- Add monitoring, incident response, accessibility, offline behaviour, privacy review, and deployment documentation.
- Pilot only with an appropriate institution/authority; never claim a guarantee of safety.

## 9. Current limitations

- Safety values are synthetic and cannot describe the actual safety of Mumbai or Navi Mumbai.
- There is no trained AI/ML model in this milestone.
- The demo uses public community map services that are unsuitable for heavy production traffic.
- The in-memory demo repository resets when the process stops.
- Production authorization, moderation, push notifications, and end-to-end device tests are incomplete.
- Safe-Navi is not an emergency service and does not guarantee that a route or area is safe.

## 10. Tomorrow's presentation outcome

The milestone should be evaluated on whether it proves the idea is technically buildable. The work now includes a quality-checked city-scale synthetic road dataset, an indexed SQLite runtime, a FastAPI risk service, and a runnable Android prototype. The live application obtains real road routes, queries dataset-derived point and route risk for the current time period, reports spatial coverage and leading factors, recommends alternatives under three route preferences, and connects citizen reporting with government verification. The next academic decision is how to upgrade from 1 km aggregates to exact PostGIS road segments and compare deterministic and ML methods without presenting synthetic estimates as real public-safety facts.
