# Safe-Navi — cloud feature testing log

This log comes from the deployed HTTPS service and persistent Supabase data, not a local TestClient or fixture backend.
Detailed timestamped requests/assertions: Safe-Navi-Cloud-Feature-Log.jsonl. Credentials are in a separate Git-ignored private file.

## Feature status

| Feature | Result | Phone inspection / limitation |
|---|---|---|
| Cloud health, model, PostgreSQL, Supabase | PASS | Cloud health endpoint; active trained_ml metadata retained |
| Citizen registration, login, session identity | PASS | Demo Anaya / Demo Rohan can sign in from separate phones/networks |
| Logout/session isolation | PASS | Logout invalidates only that session; second citizen remains valid |
| Community post and shared photo | PASS API | Community → DEMO REHEARSAL; seeded activity labelled DEMO |
| Comments, nested replies, helpful votes, search | PASS API | Open the demo discussion on either phone |
| Retry/idempotency for posts/comments/reports/messages | PASS API | Retried writes returned the same record IDs |
| Private evidence before/after sharing | PASS API | Owner byte integrity; unshared image denied to B; shared image allowed; anonymous denied |
| Report submission, owner detail/history | PASS API | Demo Anaya → Report → History → DEMO case |
| Private case message and image | PASS API | Report → case → Conversation; another citizen receives 404 |
| Unverified report cannot change public map | PASS | Demo case deliberately not verified; not a real hazard |
| Saved place / contact CRUD and isolation | PASS API | Demo Anaya profile contains a saved station and explicitly invalid DO NOT CALL contact |
| Journey start/location/check-in/share/end | PASS API | Simulated journey share URL in private access file; COMPLETED and check-in OFF |
| Account password change / deactivation | PASS API | Disposable third account only; retained demo accounts unchanged |
| Navi assistant | PASS API | Live Groq-backed answer retained in private access file; ask again in the app |
| Hazard update socket | PASS handshake | No fake hazard broadcast; not a claim of private chat WebSocket delivery |
| News schedule | CONFIGURED / prior fetch confirmed | Health retains successful fetch; the later unattended cycle has not yet elapsed |
| News classification and source links | NEEDS OFFICIAL LOGIN | Citizen endpoint denial verified; current official screen not re-tested in this run |
| Official assignment / info request / reply / verification | NEEDS OFFICIAL LOGIN | Demo case is available for a genuine official to inspect; do not publish fiction |
| Admin role/account/app-setting changes | NEEDS ADMIN LOGIN | Citizen receives 403; privileged positive actions not counted as passed |
| Source and destination remain visible; best-match for both | FIX IMPLEMENTED / SCREEN CHECK BLOCKED | APK 1.7.3 built; emulator OS startup failure prevents completed new screen test |
| Real road alternatives and live ML ranking | PASS API | See verified route list; no fabricated differences |
| GPS, physical phone photo picker, dialler, background alerts | MANUAL PHONE | Not established by cloud API tests; never place an emergency call as a test |
| Offline mode | NOT A CLOUD FEATURE | Previous isolated tests are not being represented as cloud proof |
| Independent user adoption | NOT CLAIMED | Teammates can contribute actual replies; seeded demo accounts are labelled |

## Bugs, test failures and remaining work

- Fixed code: endpointEditor started hidden and was hidden again after selection/comparison; now A and B stay visible.
- Fixed code: unresolved source path forced a selection sheet; both endpoints now commit the ranked best match.
- Fixed lifecycle hygiene: opening another place search dismisses the previous sheet; Activity destruction dismisses it too.
- Corrected test harness: journey end status is COMPLETED, not ENDED. The corrected live test passed. The original FAIL remains in the raw log.
- Corrected route-probe harness: Windows console could not print an arrow; UTF-8 output enabled and a complete probe rerun succeeded.
- Test environment: Android package/activity services failed; after recovery, instrumentation hit BIND APPLICATION ANR before tests. No new UI-test pass is claimed.
- Access needed: current cloud official/admin credentials. Do not bypass permissions to obtain a passing test.
- Remaining codebase lint warnings: 495; lint errors: zero in this build. Build and 43 JVM tests passed, but these local code checks are supplemental, not cloud end-to-end proof.

## Complete deployed API inventory

PASS_API means a matching successful request was observed; DENIAL_ONLY means only a permission/validation boundary was tested; NOT_TESTED means no positive test in this run.
This inventory prevents the claim that every deployed operation was successfully tested when privileged access or a safe real incident is unavailable.

| Method | Endpoint | Evidence status |
|---|---|---|
| GET | /api/v1/admin/accounts | DENIAL_ONLY |
| POST | /api/v1/admin/accounts/{account_id}/action | NOT_TESTED |
| GET | /api/v1/admin/app-settings | DENIAL_ONLY |
| PATCH | /api/v1/admin/app-settings | NOT_TESTED |
| GET | /api/v1/admin/audit | DENIAL_ONLY |
| POST | /api/v1/auth/register | PASS_API |
| POST | /api/v1/auth/login | PASS_API |
| POST | /api/v1/auth/logout | PASS_API |
| POST | /api/v1/account/password | PASS_API |
| DELETE | /api/v1/account | PASS_API |
| GET | /api/v1/me | PASS_API |
| POST | /api/v1/reports | PASS_API |
| GET | /api/v1/reports | NOT_TESTED |
| POST | /api/v1/evidence | PASS_API |
| GET | /api/v1/evidence/{evidence_id} | PASS_API |
| GET | /api/v1/reports/duplicates/suggest | NOT_TESTED |
| GET | /api/v1/reports/{report_id} | PASS_API |
| GET | /api/v1/reports/{report_id}/messages | PASS_API |
| POST | /api/v1/reports/{report_id}/messages | PASS_API |
| POST | /api/v1/reports/{report_id}/confirm | NOT_TESTED |
| POST | /api/v1/government/reports/{report_id}/triage | NOT_TESTED |
| POST | /api/v1/government/reports/{report_id}/assign | NOT_TESTED |
| POST | /api/v1/government/reports/{report_id}/request-information | NOT_TESTED |
| GET | /api/v1/government/reports/{report_id}/duplicates | NOT_TESTED |
| GET | /api/v1/government/metrics | DENIAL_ONLY |
| POST | /api/v1/government/news/ingest | NOT_TESTED |
| GET | /api/v1/government/news/settings | NOT_TESTED |
| PATCH | /api/v1/government/news/settings | NOT_TESTED |
| GET | /api/v1/government/news-signals | DENIAL_ONLY |
| POST | /api/v1/government/news-signals/{signal_id}/review | NOT_TESTED |
| POST | /api/v1/government/news-signals/{signal_id}/publish | NOT_TESTED |
| POST | /api/v1/government/reports/{report_id}/decision | NOT_TESTED |
| PATCH | /api/v1/government/hazards/{hazard_id} | NOT_TESTED |
| GET | /api/v1/hazards/map | PASS_API |
| GET | /api/v1/government/hazards/{hazard_id}/history | NOT_TESTED |
| GET | /api/v1/notifications | PASS_API |
| POST | /api/v1/notifications/{notification_id}/read | NOT_TESTED |
| GET | /api/v1/saved-places | PASS_API |
| POST | /api/v1/saved-places | PASS_API |
| DELETE | /api/v1/saved-places/{place_id} | PASS_API |
| GET | /api/v1/emergency-contacts | PASS_API |
| POST | /api/v1/emergency-contacts | PASS_API |
| DELETE | /api/v1/emergency-contacts/{contact_id} | PASS_API |
| GET | /api/v1/journeys | PASS_API |
| POST | /api/v1/journeys | PASS_API |
| PATCH | /api/v1/journeys/{journey_id}/location | PASS_API |
| POST | /api/v1/journeys/{journey_id}/end | PASS_API |
| POST | /api/v1/journeys/{journey_id}/check-in/schedule | PASS_API |
| POST | /api/v1/journeys/{journey_id}/check-in | PASS_API |
| GET | /api/v1/journeys/share/{share_token} | PASS_API |
| GET | /api/v1/journeys/share/{share_token}/view | NOT_TESTED |
| GET | /api/v1/community/posts | PASS_API |
| POST | /api/v1/community/posts | PASS_API |
| POST | /api/v1/community/posts/{post_id}/vote | PASS_API |
| GET | /api/v1/community/posts/{post_id}/comments | PASS_API |
| POST | /api/v1/community/posts/{post_id}/comments | PASS_API |
| POST | /api/v1/assistant/chat | PASS_API |
| GET | /health | PASS_API |
| POST | /api/v1/internal/news/tick | NOT_TESTED |
| GET | /api/v1/dataset/stats | NOT_TESTED |
| GET | /api/v1/risk/provider | PASS_API |
| GET | /api/v1/risk/point | NOT_TESTED |
| GET | /api/v1/risk/area-crime | NOT_TESTED |
| POST | /api/v1/risk/route | PASS_API |

## ML and route findings

Live driving evaluations: 32 across 16 pairs, morning_peak and midday. Three distinct preference winners: 0.
The model's baseline factors are synthetic area estimates, not measured crime probability. Same candidate road has the same time/exposure under every preference. Ranking objectives differ, but that does not guarantee different winners.
Search caution: the Chembur station query matched Chembur Railway Station Post Office. Its exact matched pin/address is recorded; it is not recommended as a station-to-station demo. Prefer Thane→CBD Belapur or Vashi→SIES GST.

## Handoff

Install Safe-Navi-1.7.3-Cloud-Rehearsal.apk over the existing application. Do not uninstall merely to update.
Use https://safe-navi-api.onrender.com. A laptop server/same Wi-Fi is not required. Warm /health before presenting.
Private cloud access: Safe-Navi-Cloud-Rehearsal-Access.private.json (do not commit/share publicly).
Cloud tests created one labelled post, two threaded comments, two votes, one linked app-logo photo, and one unverified demo report/message. Simulated journeys were ended; the account-settings test citizen was deactivated.
No real emergency call, unrelated account mutation, fictional verified crime, or public closure was made.