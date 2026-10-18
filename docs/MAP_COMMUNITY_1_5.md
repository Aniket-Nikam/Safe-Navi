# Safe-Navi 1.5: Map, Community and Session Reliability

## What changed

- The map renders all three recommendations together: **Fastest** red, **Balanced** yellow and **Safest** green. If their recommendation is the same physical road, offset coloured bands make that fact visible without claiming that three distinct roads exist.
- The compact route card keeps the map readable. Route actions and explanation are in an on-demand bottom sheet.
- A, B and verified alert pins remain visible. The **Crime & alerts** control lists active verified reports and news hazards; map labels show a reported crime type when the verified record contains one.
- Crime labels are descriptive terms extracted from verified CRIME reports or verified crime-news records. They are not historic crime-frequency statistics and no label is shown when no verified record is available.
- News intake remains review-only: an official can check due configured feeds, inspect the source, pin the actual affected road, confirm current verification and publish a time-limited hazard. Only that published hazard can change route exposure or exclude a closed road.
- Community now opens with a small composer; drafts are private to the selected account and server. Post/reply retries use a stable request ID so a timeout cannot produce duplicates. A discussion refreshes every five seconds while open.
- A 401 response marks only the matching server/session as requiring sign-in and opens a recovery login flow. Drafts are retained; ordinary network errors do not sign the user out.

## Operational truth

The cloud backend returns no crime or news marker until a report/news signal has been verified and published by a government account. The hosted app checks configured HTTPS feeds while it is running, honours the administrator’s 12/24-hour interval, and exposes its last ingestion timestamp in the health response. A free Render web service may sleep while idle, so the next request wakes it and triggers any due check; a continuously guaranteed schedule would require an external scheduler.

## Verification

- Android unit tests: 40 passed.
- Android local workflow/instrumentation checks: 10 passed.
- SQLite backend suite: 67 passed, 3 intentionally skipped.
- Local PostgreSQL backend suite: 70 passed.

All test records used for Android checks are localhost-only fixtures. They are never written to the hosted Supabase/PostgreSQL project.
