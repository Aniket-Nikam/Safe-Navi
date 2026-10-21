# Safe-Navi acceptance check — 21 October 2026

## Delivered build

- Android: `Safe-Navi-1.7.2-Demo-Verified.apk`, version code 19.
- APK size: 78,128,408 bytes (74.51 MiB). Debug/demo certificate verified with Android apksigner; not a Play Store release build.
- SHA-256: `05D5CB662C91559A41CDE0438189EA9A9EFFE65200D950B41C8914DEF80C1920`.
- Application/backend code commit: `5461df9b9c23d62fc641b336b79ebf8f043c4b5d`, pushed to GitHub and deployed on Render.
- Render deployment: `dep-dbcbd0bncjis738vf55g`, shown as “Deploy succeeded | Live”.
- Cloud origin: https://safe-navi-api.onrender.com.

## Repairs made in this check

1. Fixed a real map lifecycle crash: asynchronous hazard responses could redraw a MapLibre style after its Activity was destroyed. Added destroyed/finishing/loaded-style guards, cleared the style reference on destruction, guarded delayed camera work, and added a regression test.
2. Removed all six Android lint errors at five source locations by using the appropriate typed constants for font, orientation, visibility and line caps/joins.
3. Installed the missing Supabase pg_cron/pg_net schedule, using a scoped HMAC signing key in Vault. The active hourly job checks the persistent admin-selected 12/24-hour due time. It does not refresh every hour unless due.
4. Verified the first dynamic internet ingestion reached the persistent database: **148 articles**, successful fetch recorded at `2026-10-21T12:19:47.358730+00:00` (17:49:47 IST). These are review-only signals, not automatically published hazards.
5. The initial fetch exceeded pg_net's 120-second HTTP timeout while its backend work continued and completed. Replaced one database connection per article with bounded batches of 50 rows in one connection per feed. Added tests for chunking, duplicate handling, scheduling and refresh accounting, then deployed the fix. No news review status or government record was changed by this optimization.
6. Updated stale map-test assumptions to the current design: risk toggle opens the overlay directly; identical preference winners draw one slate-blue road; unset line offset means the MapLibre default zero. Assertions now verify these behaviours, rather than requiring fake parallel paths.
7. Made the opt-in live Android map probe explicitly select the cloud origin and restore the prior session/server afterwards. This prevents a local server setting from making a “cloud” test misleading.
8. Corrected the two-device guide's obsolete laptop-only networking and admin-as-official guidance.

## Verification results

| Check | Result | Scope/evidence |
|---|---|---|
| Full backend pytest suite | **71 passed, 3 skipped** | Isolated product/evidence fixtures; news publication→hazard→routing, roles, case chat, idempotency, evidence privacy, ranking, closures, schedules, batch ingestion |
| Android local unit tests | **43 passed** | Search/relevance, server migration, route ranking, hazard geometry/workflow, JSON display |
| Android instrumented tests | **23 passed** | API 36.1 emulator; live search/Valhalla/cloud trained ML, map rendering and A/B, shared geometry, route-risk toggle, lifecycle regression, search auto-selection, sessions/community, case/news UI, cloud migration, offline routing |
| Android build and lint | **Build succeeded; zero lint errors** | `testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest`; **495 lint warnings remain** |
| Live cloud private acceptance | **23 checks passed before and again after deployment** | Registration/session, role denials, saved place, contacts, private photo round-trip/integrity, cross-user and anonymous access denial, journey ownership/location/check-in/share privacy, Navi/Groq, map update WebSocket, community and report reads |
| Signed Supabase→Render tick after deployment | **HTTP 200, no timeout** | Correctly returned `skipped: not_due`; last successful ingestion and the next due time persisted |
| Live session repetition | Passed | Five authenticated reads each of `/me`, community and reports; second login retains valid sessions; wrong password rejected without destroying valid session |
| Real route examples | **20 evaluations, 10 location pairs** | Morning and midday, Photon coordinates + Valhalla roads + live `trained_ml` evaluation; two pairs had two distinct winners; none had three |
| Cloud persistence | Confirmed | Product PostgreSQL; evidence Supabase; risk database immutable SQLite; news articles and configuration retained across deployment |

The three skipped backend tests require an optional isolated loopback PostgreSQL migration/cron test database. They were not silently counted as passes. One Starlette/httpx deprecation warning remains. Android lint warnings were not represented as fixed or absent.

## Functional boundaries and honest limitations

- The model is active (`trained_ml`, exported linear regression). Its baseline uses synthetic area factors, not validated observations of actual crimes or a calibrated probability of victimisation.
- Fastest minimizes time; Balanced uses time + 0.35 × exposure; Safest minimizes assessed exposure, using time to break ties. Same road means same time and exposure, even when objective scores differ.
- Official closures/no-go geometry exclude intersecting candidate roads. A non-closed unsafe hazard adds risk; it does not automatically prohibit every nearby route. If all candidates are excluded, the app must not invent a safe road.
- Internet news classification is keyword-based. News is not automatically verified. An official must check the source, currentness, exact affected road, severity and road status before publication changes the shared map/routing.
- Automatic scheduling is now installed, and the first signed fetch completed. A later unattended 12-hour cycle has not yet elapsed during this check; future provider availability cannot be guaranteed.
- Current next due time is 22 October 2026 at 05:49:47 IST. The hourly job runs on the next hourly opportunity after it is due (up to about one additional hour), not at an exact second. Admin may change this interval.
- The live production map was not polluted with fictional hazards to manufacture route differences. News/report publication and routing changes were tested in isolated fixtures. No real emergency call was placed.
- Cloud tests prove API behaviour and a WebSocket handshake, not a full two-physical-phone radio/GPS/background-permission test. Perform the short phone rehearsal in the demo guide.
- A free Render instance can sleep; wake `/health` before the presentation. Public geocoding/routing providers and RSS sources can fail or rate-limit. GPS, Android photo permissions and dialler behaviour depend on the actual phone.
- This is not an assertion of universal “bug-free” operation. It records reproducible checks, repaired faults and known limits.

## Test-data hygiene

Private live acceptance tests used disposable citizen accounts, then deactivated them and removed test contacts/saved places. One tiny private PNG and an ended test journey per acceptance run remain as audit data because there is no evidence-delete endpoint. No public report, post or hazard was created by these cloud checks. Local UI fixtures are separate temporary databases and explicitly labelled fiction.

## Files to use

- `Safe-Navi-1.7.2-Demo-Verified.apk` — updated Android app.
- `Safe-Navi-Demo-Guide-2026-10-21.md` — station names, exact pins, measured route differences, rehearsal steps and factual narration.
- `cloud-news-schedule-2026-10-21.png` — scheduler/first-fetch database receipt.
- `deployment-1.7.2-live.png` — successful Render deployment receipt.
- `cloud-scheduler-http-200.png` — signed scheduler authentication/due-time HTTP receipt.
- Machine-readable route/private-cloud receipts: `tmp/acceptance-2026-10-21/` in the local workspace (not publicly committed).

Repository: https://github.com/Aniket-Nikam/Safe-Navi
