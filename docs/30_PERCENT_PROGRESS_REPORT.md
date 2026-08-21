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

The current milestone uses clearly labelled fictional scenarios around Mumbai and Navi Mumbai. The records are designed to exercise the complete workflow rather than claim true crime or safety conditions. Each hazard can carry ten modelling dimensions:

1. category/type;
2. severity;
3. government verification state;
4. spatial distance or route exposure;
5. report age/time decay;
6. lifecycle status;
7. recurrence;
8. citizen confirmations;
9. nearby hazard density; and
10. source reliability/confidence.

Road geometry is public OpenStreetMap-derived data; safety labels are synthetic. No private victim, offender, or individual movement data is used.

## 7. Why this is called 30%, despite being runnable

The denominator is the complete proposed system, not merely an Android screen. This first milestone proves technical feasibility and the core flow. The remaining work is more demanding because it includes secure persistence, data governance, model validation, ML experimentation, operational infrastructure, and cross-platform delivery.

| Full-roadmap area | Current state |
|---|---|
| Problem definition and architecture | Completed for prototype |
| Android user experience | Core demonstration completed |
| Open map and multimodal routing | Working prototype |
| Explainable risk and report lifecycle | Working with synthetic data |
| Persistent secure backend | Partial foundation only |
| Large synthetic/public feature pipeline | Not yet productionized |
| Trained and evaluated ML model | Not started; deliberately not faked |
| Moderation, alerts, offline use, accessibility | Future work |
| Flutter Android/iOS application | Future phase after validation |
| Production deployment and real authority partnerships | Future work |

## 8. Next phases

### Phase 2 — approximately 30% to 55%

- Persist reports, hazards, history, and saved routes.
- Enforce roles using Firebase custom claims/security rules or a FastAPI backend.
- Expand the synthetic data generator and document data lineage.
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

The milestone should be evaluated on whether it proves the idea is technically buildable. The demonstration shows that Safe-Navi can obtain real road routes, attach explainable synthetic risk, recommend alternatives under three route preferences, and connect citizen reporting with government verification. The next academic decision is how to validate the data pipeline and compare deterministic and ML methods without presenting synthetic estimates as real public-safety facts.
