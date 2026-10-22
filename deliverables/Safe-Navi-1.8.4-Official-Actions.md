# Safe Navi 1.8.4 — official report actions

22 October 2026. Version code 26. Includes all 1.8.3 route-colour and flood-passability changes.

## Corrected flow

An already-VERIFIED report previously retained a publish-looking, disabled button.
It now has an enabled **View published hazard on map** action that opens its linked
hazard. This does not republish the report or bypass the backend's final-decision guard.

**Browse published hazards** opens the active cloud hazard list directly from the
official dashboard. Select an item to focus its map pin. Admins retain read-only
map access; hazard updates remain official-only on the server.

Pending REPORTED / UNDER_REVIEW / REQUESTED_INFO reports still open the verification
form. For a new publication choose an awaiting-decision report in the queue; use
Filter reports to select REPORTED if necessary. Already-final rejected/duplicate
reports are not republished.

A failed secondary operation no longer disables all actions on a loaded report.
The error is shown while preserving the selected report and queue.

No account, report, hazard status, session on another device or expiry was reset.
No new incident was fabricated or published as part of this dashboard fix.

## Verification

- Build and APK signature verified; same signing identity as the earlier APK.
- All 82 Android unit tests passed (zero failures/errors).
- Real-cloud native UI regression passed in 18.495 seconds: published report's
  button dispatches its linked map focus, a pending report opens the publish form,
  and the populated published-hazard browser opens. This test cancels publication;
  it does not submit a new official decision or change cloud incidents.
- Cloud has 20 VERIFIED reports and three awaiting decisions at verification time.
- SHA-256: `81D903C658001F727EE71851FDBC8136DAC52AAE9C24D6203F4A8F1DE263D2D0`.
- Temporary test-login handoff removed after testing. Existing phone sessions retained.
