# Safe-Navi 1.4 — cases and verified news

## User-visible changes

- Reports save unsent text, selected location, category, identity preference and retained evidence references in an account-and-server-scoped private device draft. Explicit discard removes unsent attachments. A preview precedes submission.
- Report history searches both server records and device-queued reports, distinguishes their delivery state, and exposes evidence, status timeline and the case conversation. Failed queue persistence no longer clears a form.
- Case conversations use a full-height scrolling message list, earlier-message pagination, foreground incremental refresh, selectable text, retained photo/text drafts and explicit retry. Composer contents clear only after server acknowledgement. Stable request IDs prevent duplicate case messages on retry; conflicting reuse is rejected.
- Citizens and officials can share authenticated case photographs. An unrelated citizen cannot read them. Administrators remain unable to send case messages or publish government hazards.
- Navi retains recent conversation history and unfinished questions privately on the device. Failed questions are restored for retry instead of appearing as successful assistant answers.
- Officials have a searchable/filterable news workbench with source links and transparent classification information. Admins additionally control ingestion and the 12/24-hour interval. Internet-check results show missing feeds, skips and partial failures rather than implying success.

## News → map → route workflow

1. Configured HTTPS RSS feeds supply external signals. Existing records remain stored for review; this is not an instantaneous incident-detection service.
2. Current news classification is **keyword-based**, not a newly trained news ML model. The route risk model remains the supplied trained model. A locality match is only a hint, not an exact incident coordinate or proof of crime prevalence.
3. An authorised safety official reads the source, chooses an exact affected-road pin, supplies address, severity, road status, verification note and current-incident confirmation, then publishes a time-limited hazard. The UI offers 3/6/12/24 hours; API validation allows 1–72 hours. Optional exclusion radius is 10–500 metres.
4. Backend publication is transactional and retry-safe per article. It records a linked `NEWS_VERIFIED` hazard, provenance, verifier, note, expiry and audit event. Repeated publication does not create another hazard or silently extend its expiry.
5. Public hazard responses include article URL, publisher and publication date. The map can focus a published hazard and show its source details. Live hazard events and periodic refresh update connected maps.
6. Existing route evaluation combines verified hazard exposure with model-based baseline risk. An unsafe/caution signal can change ranking without necessarily selecting another road. CLOSED roads, and existing critical-unsafe exclusion rules, reject crossing candidates. If all candidates are excluded, the app must not invent a safe route.
7. Expired hazards cease affecting server map/routing. The offline closure cache also filters known expiry timestamps. Offline data remains a stale snapshot, not a guarantee that no new hazards exist. Dismissing a published article cannot silently undo a government road restriction; officials use the hazard lifecycle.

## Reproduction / verification

- Backend SQLite: `backend/venv/Scripts/python.exe -m pytest -q -x` — 65 passed, 3 PostgreSQL-only tests skipped.
- Isolated loopback PostgreSQL 18: same suite with `SAFE_NAVI_TEST_DATABASE_URL` — 68 passed. Tests isolate their own temporary schemas and never use hosted databases.
- Android JVM suite: 40 tests passed; `testDebugUnitTest assembleDebug assembleDebugAndroidTest` succeeds.
- Emulator: four opt-in `CasesNewsInstrumentedTest` checks pass: case draft recreation + acknowledged send, report draft/location recreation, exact-road-pin enforcement, and account/server draft isolation. `backend/tests/run_ui_fixture.py` is an explicit localhost-only test fixture; it is not application demo data or production news.
- Integration tests exercise official-only publication, public provenance, closure exclusion, severity exposure, expiry, idempotent messages, cursor boundaries and photograph authorisation.

## Boundaries

Case chat refreshes every five seconds while open; it is not a new WebSocket messenger, E2EE system, background auto-send or read-receipt service. Drafts use private no-backup app storage, not a claim of cryptographic encryption. Actual cloud accounts/photographs and two physical phones were not used for write tests. Free Render hosting can cold-start. A saved interval alone does not install an external scheduler: the Supabase job in `deploy/supabase_news_schedule.sql` must be configured and independently verified for unattended checks while Render sleeps. No fake public reports or news hazards were created to demonstrate this release.
