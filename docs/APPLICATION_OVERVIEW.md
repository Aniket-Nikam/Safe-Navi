# Safe-Navi: factual product overview

## Product purpose

Safe-Navi is an Android safety and civic-response platform for citizens and authorised government staff. It combines real road-route candidates, a trained route-risk model, government-verified hazards, community reporting, evidence handling and consent-based journey safety tools. It is safety guidance, not an emergency-dispatch service and not a guarantee that a route is safe.

## 1. Account and role flow

Citizens register and sign in through the Android app. Passwords are hashed by the backend, bearer sessions are persisted, and citizen data is scoped to the signed-in account. Government accounts are created by an operator rather than through public self-registration. Role checks protect government decisions, operational queues, evidence and moderation actions.

After citizen login, the main app exposes Home, Map, Report, Community, Navi assistant and Settings/Profile functions. Government users enter the operational dashboard and can also open the map in an authorised editing mode.

## 2. Map and route recommendation

MapLibre renders OpenStreetMap-derived OpenFreeMap tiles. Nominatim geocodes explicitly submitted source and destination text. Valhalla supplies actual driving, walking and cycling candidate geometry. The app never draws a straight line and calls it a road route.

For each candidate, the backend samples its geometry, retrieves the matching 1 km area/time cells and runs the bundled teammate-trained linear model. The response identifies the model version, confidence, coverage, leading factor contributions and the risk components used. The profiles rank candidates as follows:

- Fastest: travel time only, after mandatory closures have been removed.
- Balanced: travel minutes + 0.35 × total risk exposure.
- Safest: travel minutes + 1.00 × total risk exposure.

Total exposure combines the ML dataset prediction with nearby active, government-verified hazards. Multiple contributions are combined probabilistically so they do not simply exceed 100.

## 3. What happens when a citizen reports an issue

The citizen enters a title, category and description and pins the exact location by tapping the map or using device location. Reverse geocoding records a readable address. Up to five JPEG, PNG, WebP, MP4 or WebM evidence files can be included, subject to server validation and a 15 MB per-file limit.

Before submission, the backend can suggest a nearby report in the same category. The citizen may confirm that issue or deliberately submit a new one. A client request ID makes retrying idempotent.

A new citizen report has no effect on public route advice. This separation prevents unverified or malicious reports from changing navigation.

## 4. Offline evidence and later upload

When evidence is selected for a report, it is copied into Android app-private no-backup storage immediately. The offline store is capped at 100 MB, records SHA-256 hashes and preserves the report request ID and evidence IDs. WorkManager retries authenticated uploads when connectivity returns and checks the stored hashes before sending. This supports creation of a complete report while offline without silently discarding the evidence.

The current threat boundary is Android app-private storage and device protection. Additional Keystore-backed file encryption is a documented production-hardening option, not a claimed current feature.

## 5. Government review and two-way communication

The operational dashboard provides a selectable and filterable report queue, assignment to a department/team, SLA due times, duplicate indicators, confirmation counts and a complete audit timeline.

An official can request more information. The citizen receives an in-app notification and can open the report's private conversation, reply with text and attach a new photograph. The official can read and answer the same thread and preview authenticated photographs from the operational evidence record. A citizen answer changes `REQUESTED_INFO` back to `UNDER_REVIEW`, records the transition and notifies authorised staff.

Community posts are a separate public, authenticated channel. Citizens can publish text with an optional photograph, reply to posts and cast one helpful vote per account. Community material is never presented as government verification and never changes routing by itself.

## 6. Verification, severity and map changes

After field review, an authorised official can reject the report or publish a verified hazard with:

- severity: LOW, MODERATE, HIGH or CRITICAL;
- road state: SAFE, CAUTION, UNSAFE, WORK_REQUIRED or CLOSED;
- a verification reason;
- optional road-line or affected-area geometry;
- an optional 10–1000 metre no-go radius.

Verified active or monitored hazards appear on citizen maps. Point markers are green for LOW, amber for MODERATE, orange for HIGH and red for CRITICAL. Road and polygon geometry is also rendered, so the warning is not limited to a single pin.

Hazard influence is distance-aware up to 750 metres. Its base contribution is the severity weight plus the road-state weight, reduced by distance; monitored hazards use a 0.65 status multiplier. A CLOSED road, or a CRITICAL + UNSAFE hazard, is a hard constraint inside its no-go radius and is removed from every route profile. Other verified hazards are soft exposure costs and can change which alternative is recommended under Balanced or Safest.

The backend pushes hazard changes over WebSocket. An open map refreshes immediately and re-ranks a visible route; 30-second polling is retained as a fallback. Resolving a hazard removes its immediate influence after refresh. Offline routing uses only the last successfully cached verified-hazard snapshot and labels itself conservative/offline.

## 7. ML model and dataset boundary

The integrated artifact is `backend/models/safety_score_linear_v1.json`, a non-executable JSON export of the best supplied trained pipeline. Its recorded held-out R² is 0.9316 and its street-grouped five-fold CV R² is 0.9325 ± 0.0033. The backend uses it for point and route inference and can fall back to a deterministic baseline if model inference fails. An explicitly configured HTTPS model service can override the bundled predictor.

The source dataset represents 452,466 physical OSM road segments and 1,809,864 segment-time observations. The app runtime currently queries 6,508 indexed 1 km area/time aggregates. The targets and safety factors are synthetic: these metrics validate integration and consistency, not real-world predictive validity. Real deployment requires authorised observational labels, temporal validation, bias/fairness review, calibration and monitored retraining.

## 8. Additional safety features

- Navi assistant: Groq-backed through the server, with no API key in the APK and explicit failure when it is not configured.
- Safety inbox and Android notifications for official updates and critical hazards.
- Trusted emergency contacts and an emergency toolkit for 112, location sharing and calling a trusted contact.
- Saved places and route-summary sharing.
- Consent-based live journey links, optional user-visible foreground location service and journey history.
- Scheduled 15/30/60/120-minute safety check-ins, offline prompts and queued acknowledgements.
- English, Hindi and Marathi per-app language selection for the primary experience.
- Downloadable bounded Navi Mumbai map tiles plus a bundled Navi Mumbai road graph for on-device search and A* routing.
- Dynamic review-only HTTPS RSS news detection. An administrator selects a persistent 12- or 24-hour internet-check interval and can trigger an immediate check. News cannot publish hazards or affect routes without a separate verified report.

## 9. Data, security and trust controls

The backend validates file media types and sizes, stores evidence under generated identifiers and checks authorisation on download. Citizens can access public community images and evidence attached to their own cases; government/admin roles can access operational evidence. Server-side role checks, not hidden buttons, protect official actions.

Report decisions and hazard lifecycle updates are audited. Unverified reports and news signals are kept out of route scoring. Groq credentials remain server-side. External ML overrides require HTTPS except for loopback development.

For public deployment, the local SQLite product database should be migrated to managed PostgreSQL/PostGIS, the API must use production HTTPS, secrets should use a secret manager, rate limiting and monitoring should be added, and privacy/retention policies need formal approval.

## 10. Verified build status

The current build is covered by fourteen Android JVM tests, three Android instrumentation tests and seventeen backend integration tests. The verification commands are documented in the repository README. Successful automated checks establish that the tested workflows compile and behave as specified; they do not constitute a claim that any non-trivial application can be proven free of every possible defect, device-specific issue or external-service outage.
