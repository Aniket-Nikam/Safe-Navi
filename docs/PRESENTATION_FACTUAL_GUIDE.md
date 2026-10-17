# Safe-Navi: factual demonstration and viva guide

Prepared 9 October 2026 against the current local source and latest safety-fixes APK.
This describes implemented functionality, its boundaries, and reproducible demonstrations.
It does not certify universal correctness or real-world predictive validity.

## 1. The project in one sentence

Safe-Navi is an Android civic and personal-safety platform combining real road routing,
a teammate-trained risk model, citizen reports, official verification, evidence,
community discussions and journey-safety tools.

Presentation sentence: “We combine a synthetic risk baseline with authoritative,
government-verified hazards to compare real routes and connect citizens with officials.”

## 2. Runtime architecture

The Android app renders the interface and map. A Python FastAPI backend on the laptop
handles authentication, data, ML inference, evidence, news and permissions. SQLite stores
the product records; a separate indexed SQLite database supplies the risk dataset.
MapLibre/OpenFreeMap render the map, Nominatim/Photon search places, and Valhalla supplies
road-route candidates. Groq supplies Navi assistant answers through the backend.

Both demo phones must reach the same backend. On a phone hotspot, the phone hosts Wi-Fi
and the laptop joins it. The APK has a default address and a persistent Server connection
setting on login, Profile and the operations dashboard. It does not discover a changed
laptop IP automatically; use the launcher's new address and Test and save in the app.
The app checks /health before saving and signs out when the server address changes.
Run the desktop launcher, accept its firewall setup
if required, and check the launcher’s health URL on the phone. Keep the laptop on.
Public deployment over HTTPS is a future hosting step, not the current LAN deployment.

Technical stack: Android Java, Material Components, AndroidX, MapLibre, OkHttp,
WorkManager, Google Play Services location, FastAPI/Uvicorn, Python and SQLite.
Application ID: com.safenavi.app. Minimum Android version: Android 7 / API 24.
The distributed presentation APK is a debug build; its Gradle versionName is 1.0.

## 3. Roles and account boundaries

| Role | Implemented responsibility |
|---|---|
| Citizen | Report issues, attach evidence, reply to their own cases, community, maps, assistant, contacts and journeys |
| Safety official | Review/assign cases, request information, communicate, verify/reject, publish/change hazards and map geometry |
| Admin | Account access/official-role management, app settings, news settings and read-only case/map oversight |

Role checks are enforced by the server. Admins cannot modify official case decisions,
assignments, information requests, case conversations or hazards. Officials cannot use
admin account-management endpoints. Choosing a role card on the phone does not grant a role.
Citizens can register publicly when registration is enabled; official access can be
issued through the controlled backend utility or admin promotion.

Passwords are salted and hashed with PBKDF2-HMAC-SHA256 (310,000 iterations).
The backend issues bearer sessions, stored as token hashes on the server; their default
lifetime is 30 days. The Android app keeps its session locally. Suspension and role
changes revoke existing sessions. A promoted person signs in again using Official.
There is no implemented OTP/email-verification or emailed password-recovery workflow.

## 4. Home

Home shows account identity, a reference-point safety summary and shortcuts to Map,
Report, Community and Navi. The summary queries the backend and changes with the time
period and verified hazards. Its coordinate is currently a fixed Vashi reference point,
19.0657, 72.9986; it is not an automatically GPS-localised Home score. The greeting is
currently the fixed text “Good evening” plus the person’s name.

Demonstrate Home as a reference-area summary and action hub, not a live crime scanner.

## 5. Place search and map

Nominatim searches submitted text; a bounded Photon fuzzy-search fallback helps with
abbreviations such as SIES GST. Searches are restricted to the Mumbai–Navi Mumbai
metropolitan bounding box and explicit comma-separated locality requirements are checked.
The user confirms the returned address rather than silently accepting the first result.
Exact latitude/longitude inputs and map-pin source/destination selection are available.

Search depends on provider coverage. An unlisted building can be pinned manually.
For example, the provider finds SIES College, Nerul, but previously returned a different
Shubh Aangan outside Nahur; that conflicting locality result is now rejected.
Search does not use the ML model and is not global Google Places coverage.

Show source/destination, current-location permission, travel-mode selection, priority
selection, Compare, the route summary and expandable details. Place labels can be
shortened for display; the resolved coordinates are retained for subsequent comparison.

## 6. Real routes and navigation

Valhalla requests one primary route and up to two alternatives using driving, walking
or cycling costing. Provider availability and road layout determine how many alternatives
exist; three alternatives are not guaranteed. The app draws the returned road geometry.
It frames the route around the visible search/result panels.

The app compares/ranks candidates. Its Navigate action opens an installed navigation
application for the destination. Safe-Navi does not implement its own complete turn-by-turn
voice navigation engine, nor does its dataset provide live observed traffic speed.

## 7. What the ML actually does

The active model is the supplied best-performing Linear Regression pipeline, exported
from the trusted source artifact to backend/models/safety_score_linear_v1.json.
The backend evaluates 90 coefficients in Python. It is an inference integration;
the app does not retrain the model when somebody submits a report.

The original source represents 452,466 physical OSM road segments and 1,809,864
segment-time rows. Runtime scoring uses 6,508 indexed 1 km area/time cells.
The ten factors are traffic congestion, crime risk, lighting quality, population density,
road condition, pedestrian activity, emergency access, flood risk, isolation and public
transport access. These factors and the target are synthetic, not measured local conditions.

For a candidate, the backend samples the polyline for dataset lookup, averages matching
factors and supplies time, location and jurisdiction features. Missing road-specific
categorical attributes use documented defaults. This is not exact per-street inference
using every original road attribute. Complete geometry is separately used for hazard checks.

The supplied model card records held-out R² 0.9316, RMSE 1.4411, MAE 1.2478;
street-grouped five-fold CV R² 0.9325 ± 0.0033. R² is a regression metric, not “93%
crime-detection accuracy.” The displayed confidence is a coverage/applicability heuristic,
not the probability that a journey will be safe. Typical complete-coverage confidence
is about 84% under the current formula.

The model is active for online point and route scoring. If inference fails, the service
can use its deterministic baseline and report the fallback. Offline routing uses
conservative graph risk and cached restrictions, not the trained online predictor.

## 8. Ranking and authoritative restrictions

| Priority | Ranking score, lower is preferred |
|---|---|
| Fastest | Travel minutes |
| Balanced | Travel minutes + 0.35 × combined risk exposure |
| Safest | Travel minutes + 1.00 × combined risk exposure |

Safest is safety-weighted, not a promise to choose the absolute lowest-risk route
regardless of travel time. One route may win all three profiles.

Dataset/ML risk and verified hazard risk are combined as bounded risk exposure.
For two contributions d and h, the formula is 100 × [1 − (1 − d/100)(1 − h/100)].
This is a scoring formula, not a calibrated accident/crime probability.
Point safety score is 100 minus combined risk exposure; higher safety score is better.
The route panel shows risk exposure; lower exposure is better.

Verified LOW/MODERATE/HIGH UNSAFE warnings usually add a soft cost. They may change
Balanced/Safest, but Fastest can still choose that route. CLOSED, or CRITICAL plus UNSAFE,
is a mandatory restriction for every profile. Buffers can be 10–1000 metres; defaults
are 60 metres for CLOSED and 40 metres for CRITICAL/UNSAFE when no value is supplied.

Complete route segments are checked against point buffers, road corridors and polygon
interiors/boundaries. If all returned candidates are prohibited, the backend returns
NO_SAFE_ALTERNATIVE; the app reports that condition instead of recommending a blocked route.
It does not currently ask the online provider to generate unlimited new detours with
all closures embedded in the provider request.

## 9. Citizen report and evidence

Use Report: title, category, description, exact map pin or device location, optional
identity hiding and up to five files. Reverse geocoding supplies a readable address;
manual pins are useful when GPS is approximate. Accepted upload types are JPEG, PNG,
WebP, MP4 and WebM, with a 15 MB limit per file.

The report is stored as REPORTED. It is visible to its owner and authorised reviewers,
not a public verified map hazard. Duplicate suggestions let a citizen confirm an existing
nearby same-category report rather than creating another. Client request IDs make report
submission idempotent.

View submitted photographs through My reports → report → View evidence. Officials
have a direct View photographs action. Video files are stored, but the current viewer
does not provide in-app video playback. Type/size checks are not a malware-scanning claim.
Identity hiding affects operational report identity labels; the backend retains the
account association. Do not call this irreversible anonymisation.

## 10. Official workflow and state changes

Choose a report. Start review changes REPORTED to UNDER_REVIEW. Assign a department/team
and a resolution target; available SLA choices include 4, 12, 24, 48 and 72 hours.
Request info changes the case to REQUESTED_INFO and notifies the citizen. The citizen
can respond in the case conversation; a response returns it to UNDER_REVIEW.

An official can verify/publish with severity, road status and a reason, or reject it.
Verification saves a hazard, changes the report to VERIFIED, links the two records,
records the actor/reason/time in audit history and notifies the citizen. The dashboard
also exposes duplicates, confirmation counts, audit detail and operational metrics.
Filters include status, category, department, locality/ward text, severity, age and overdue SLA.

Publishing does not send a case to a real government department’s external system.
“Department/team assignment” means an operational field in this project’s backend.

## 11. Map updates and lifecycle

Active/monitored verified hazards appear on citizen maps with severity colours:
LOW green, MODERATE amber, HIGH orange, CRITICAL red. Tap a marker for details.
Officials can modify hazard lifecycle through the authorised map controls, and draft
road corridors or affected polygons using selected map points.

WebSocket events notify open maps of hazard changes; 30-second polling is the fallback.
A visible route is recalculated after a changed hazard snapshot. A resolved hazard
drops out of the live active layer after refresh. Offline clients cannot receive changes
until they reconnect. Audit history retains earlier decisions.

## 12. Private case conversation

Citizen: Report → My reports → selected report → Conversation.
Official: selected report → Citizen conversation. The citizen can send text and follow-up
photographs; the official can send text and inspect attached evidence.
Messages persist in the shared backend and are scoped to the case. Admins cannot send
case messages. This is a case conversation, not arbitrary citizen-to-citizen private DM.
Reopen/refresh to retrieve new messages; do not claim typing indicators, presence,
read receipts or instant WebSocket chat delivery.

## 13. Community discussion

Citizens create public-to-authenticated-members posts with category, text and optional
photograph. Helpful votes are per-account toggles. Discuss opens a scrollable sheet with
author names, relative timestamps and threaded replies to particular comments.
The composer shows the reply target and supports cancelling that target. Sending keeps
the sheet open, refreshes the thread and updates the post’s comment count.

Threads are persisted in SQLite and visible on another phone using the same backend.
Refresh retrieves another person’s reply. Parent comments must belong to the same post;
reply depth is bounded. Community material never changes routing automatically.
Do not claim the complete Instagram/YouTube feature set or end-to-end encrypted chat.

## 14. Admin console

Admin sees Administration controls: Manage accounts, App settings, Administration audit
and Change my password. For non-admin accounts, it supports suspending/restoring access,
promoting to safety official, returning to citizen and revoking all sessions.
Each account action requires an audit reason and invalidates existing sessions.
A suspended user cannot sign in. A promoted user must sign in through Official.
Accounts’ historical reports/evidence/hazards are retained.

Administrator accounts are protected from suspension/reassignment through this console;
an admin cannot promote themselves to official to bypass the case-editing restriction.
App settings persistently enable/disable new registrations and community posts/replies.
Pausing community posting does not remove existing content or stop reading it.
These settings do not give admins rights to alter official government records.

For demo, use a spare citizen account for promotion or suspension, then restore its
original state. Do not suspend the citizen/official accounts you still need for the case demo.

## 15. Dynamic news

Configured HTTPS RSS/Atom feeds are fetched from the internet. Headlines, summaries,
publisher links and publication metadata are parsed and deduplicated; keywords assign
categories and a small set of locality terms supply approximate location text.
This module is not another trained ML model or a crawler of the entire internet.
Historical fetched signals are retained so reviewers can inspect them.

Admin controls a persistent 12/24-hour interval and can use Check internet now.
The server checks whether ingestion is due approximately once a minute while running.
It cannot ingest while the laptop/backend is off. Publisher errors are surfaced in
ingestion results/logs; zero new articles can mean successful deduplication.

Use Manage news and schedule → Schedule to show last/next checks; Check internet now
to fetch again; select a signal → Open source to show publisher material.
Review actions include Relevant, Needs field check and Dismissed. News cannot create
public hazards or alter routes by itself; a separately verified hazard is required.

## 16. Navi assistant

Open it from Home’s assistant action or Community’s Ask AI shortcut. Android submits
the prompt through the authenticated backend to Groq. The key stays server-side.
The backend sends limited conversation context (up to ten previous turns), brief safety
instructions, the signed-in name and the count of active verified hazards.
It does not retrieve every report/article, inspect evidence photos or act as a fully
grounded map database assistant. Provider failures show an error; there is no offline LLM.
Conversation history is currently session/view memory, not a persistent AI chat archive.

Demo prompt: “What should I do if I encounter a flooded road?” Describe the response as
AI guidance, not verified local facts or emergency dispatch.

## 17. SOS and trusted contacts

Profile → Emergency contacts stores name, phone and relationship. Map → SOS opens
Call my emergency contact, Call emergency services (112), and Share my current location.
Choosing a saved contact requests phone permission; with permission it places a call,
otherwise it opens the dialer. 112 opens the dialer for user confirmation.
Location sharing opens Android’s share chooser using available device location.

Contact retrieval currently needs the backend. Calling needs a calling app and carrier
service. This is not automatic SMS escalation, ambulance dispatch or an emergency-control
centre integration. Rehearse with a consenting teammate’s number; do not call 112 for a demo.

## 18. Journeys, check-ins and saved places

After comparing a route, expand details: Save place stores a destination for reuse;
Share sends a textual route summary through Android; Navigate opens an external app.
Share journey creates a backend record and a consented tokenised viewer link.
Link holders can see last location, update time and journey/check-in state.
That link must reach the laptop API in this LAN deployment; it does not work globally
without a publicly reachable HTTPS host. Anyone with the link can view it.

With explicit consent and location permission, the foreground location service requests
updates around every 30 seconds and displays an ongoing Android notification. Device/OS
conditions affect delivery. End journey stops sharing and retains history; offline ending
queues server closure for reconnection. It is not silent permanent tracking or full GPS playback.

Profile → Safety check-ins requires an active shared journey. Intervals: 15, 30, 60 or
120 minutes. Android WorkManager shows a prompt, including while offline; I’m safe
queues an acknowledgement that sends after reconnection. Viewer pages show last response,
next due time or overdue state. WorkManager timing is OS-managed, not an exact alarm or
automatic call to authorities when someone misses a check-in.

For a short demo, show the scheduling and viewer state. To show an actual prompt,
schedule it ahead of time and allow for OS delay; do not promise a 15-minute prompt at
an exact second. Two phones can open a share link for evidence of cross-device persistence.

## 19. Offline functionality

Three separate systems are involved:

1. Downloadable base-map tiles: Profile → Offline maps downloads a bounded region at
   zoom levels 8–14. Finish the download beforehand and show status/size/readiness.
2. Bundled routing graph: Navi Mumbai road search and A* for all modes/priorities,
   using primitive in-memory adjacency arrays and a cached hazard snapshot. The graph
   has 175,327 nodes and 376,953 directed edges. Trained online ML/new hazards need connectivity.
3. Report/evidence queue: selected evidence is copied into app-private no-backup storage
   immediately. Limits are 15 MB/file and 100 MB total local evidence. SHA-256 checks
   detect changed files before uploading. WorkManager retries with the matching account;
   acknowledged evidence IDs and report request IDs are retained across retries.

Demo: while already signed in, disconnect the phone, select evidence/pin a report,
submit it and show QUEUED OFFLINE. Reconnect to the laptop backend, let the worker run
or reopen reports to schedule retries, then show the case and photographs on the official account.
Do not log out between queuing and upload; workers are account-scoped.

Offline map detail outside downloaded tiles can be missing. Routing graph coverage is
not all Mumbai/India. The last cached closure snapshot can be stale. App-private storage
is not an additional Keystore-encrypted evidence vault. Cloud/provider reliability is
separate from having downloaded the local routing graph.

## 20. Notifications and preferences

Profile → notification row opens the persisted safety inbox. Assignment, information
requests, decisions and certain hazard updates generate records. Android system alerts
use a background worker scheduled at a 15-minute cadence, subject to OS scheduling and
notification permission. Map hazard updates use the separate WebSocket/polling mechanism.
Do not describe every background alert as instant FCM push.

Profile exposes notification preference, light/dark mode, English/Hindi/Marathi language,
contacts, offline maps, journey history, check-ins, password change, account deletion and logout.
Language coverage is primarily navigation/map/settings; not every server/operational
message is translated. Citizen deletion deactivates/anonymises its account fields,
revokes sessions and preserves case records; it is not deletion of all historical evidence.

## 21. Security, audit and current boundaries

Role checks, bearer authentication, password hashing, per-account access, restricted
evidence downloads, file-size/type checks, audit history, session revocation and a
single-process rate limiter are implemented. The key is not in the APK. Debug LAN
traffic currently uses HTTP, so this is not an end-to-end encrypted/public-production service.

The source audit still identified two medium findings: out-of-coverage route scoring can
return a risk number with zero confidence; private evidence/log directories are currently
untracked and need Git exclusions before another broad push. Latest local features are
not all pushed to GitHub. These are not claimed fixed by the high-priority safety work.

Current verification: 29 backend tests, 26 Android JVM tests, six on-device routing tests, lint
and APK build passed. Device regressions include the formerly failing offline route,
all modes/profiles, polygon interiors and detouring around a cached point closure.
This does not establish perfect behavior on every phone or predictive safety accuracy.

## 22. Complete demonstration sequence

Rehearse the sequence; do not try every destructive account operation during the main case.

1. Start laptop backend and verify phone health connection; install the latest Safety-Fixes APK.
2. Citizen login → Home → map search and exact destination selection.
3. Compare real routes, expand ML details, explain time/coverage/factors and priorities.
4. Citizen submits a marked exercise report with photograph at an exact pin.
5. User 2 posts/replies/votes in Community; show a reply beneath a specific comment.
6. Official selects the case, views photographs, starts review, assigns and requests info.
7. Citizen reads the request and sends a case reply with a follow-up photograph.
8. Official reviews it, verifies/publishes with reason, severity and road status.
9. Citizen opens the marker; compare routes to show soft-risk changes or hard exclusion.
10. Official resolves the exercise hazard; show refresh and retained audit history.
11. Admin manages a spare account, shows audit/settings and news schedule/source article.
12. Citizen shows Navi guidance, SOS menu with a consenting contact, language/theme,
    saved place, consented journey viewer/check-in configuration and downloaded offline area.
13. Show pre-prepared offline report → reconnection/upload if time permits.

For a 10-minute live slot, prioritise steps 1–11 and use screenshots/video for timed
check-ins, offline download and additional preferences. A recorded extended demonstration
can cover every implemented feature. Never use real emergency services for the recording.

## 23. Tested three-profile exercise (9 AM–12 noon)

Input source 19.065700,72.998600 near Vashi; destination 19.042644,73.023428 at SIES Nerul.
Two explicitly labelled exercise hazards are required and have not been seeded automatically:

| Coordinates | Severity | Road state |
|---|---|---|
| 19.066713,73.019975 | HIGH | UNSAFE |
| 19.046360,73.012059 | LOW | CAUTION |

In the isolated morning test, Fastest chose A (16.6 min, risk 86.54), Balanced B
(20.2 min, risk 53.82), and Safest C (22.0 min, risk 49.29). The isolated midday test
also selected A/B/C. These are controlled scenario results from real provider geometry,
the actual ML predictor and simulated verified hazards; they are not measured hazards
or a promise of unchanged provider routes. Keep pins precise, rehearse and resolve the
exercise records afterwards. CLOSED is inappropriate for showing preference trade-offs.

For a natural no-exercise comparison, source 19.065700,72.998600 → Nerul Station
19.033000,73.016900 selected different Fastest/Balanced versus Safest during the tested
morning period; it did not yield three different recommendations throughout midday/night.

## 24. Claims to avoid, and viva answers

- “93% accurate crime prediction”: false. Say regression performance on synthetic targets.
- “AI searches all news in real time”: false. Say scheduled approved RSS/Atom detection.
- “Every accepted report immediately reroutes”: false. Review is not verification; severity/status determine soft versus hard influence.
- “Every UNSAFE road is blocked”: false. Mandatory exclusions are CLOSED or CRITICAL/UNSAFE.
- “All three profiles always select different roads”: false. They optimise different weighted objectives and can agree.
- “The home score is my precise GPS area”: false. Home currently uses a fixed Vashi reference.
- “Navigation is built-in voice turn-by-turn”: false. Navigate delegates to another app.
- “SOS dispatches an ambulance or sends automatic SMS”: false. It provides calling/dialer/share actions.
- “Navi reads every case, photo and live road”: false. It gets limited text context and hazard count.
- “Offline uses live ML and latest official information”: false. It uses conservative graph risk and cached restrictions.
- “Messages appear instantly like WhatsApp”: false. Shared storage is implemented; refresh retrieves community/case replies.
- “Government integration is deployed to actual offices”: false. Official workflow is within this application's backend.
- “Everything is encrypted end-to-end”: false. Access control is implemented; current LAN traffic is HTTP.
- “The app is bug-free and production-complete”: unsupported. State test coverage and remaining boundaries.

Why Linear Regression? It was the best supplied comparison on these near-linear synthetic
targets and is easy to explain/deploy; grouped CV is preferable to the leaked-street
single split. Why SQLite? It is suitable for the current one-host presentation; managed
PostgreSQL/PostGIS is the scale/exact-geometry migration. Why official verification?
It keeps unverified reports/news/community posts from manipulating navigation.
What is the strongest contribution? A traceable report–evidence–official-review–hazard–route
pipeline with clear role separation and support for offline submission and cached routing.
