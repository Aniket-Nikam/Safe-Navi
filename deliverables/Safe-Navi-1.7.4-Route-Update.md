# Safe-Navi 1.7.4: simpler route information

APK: `Safe-Navi-1.7.4-Simple-Routes.apk` (version code 21, cloud default).

Install this APK as an update over the previous Safe-Navi debug-signed build. A cloud deployment cannot change the screens of an older installed APK. Do not uninstall just to update: uninstalling removes device-local drafts and settings.

## What changed

- Distance in kilometres and estimated travel time stay visible on the map, including when route-risk colours are enabled. Each preference also shows its own distance and time instead of a raw risk number.
- Route details open with a short journey summary and useful actions. Model coefficients, provider names and repeated area explanations are no longer the default content.
- Safety provenance remains accessible under **About route safety > Show technical details**. Estimated areas remain explicitly separate from official alerts.
- Sharing a route sends a short, readable summary with distance and time.
- **Open directions** now transfers the chosen A, B and travel mode to Google Maps or a browser, with confirmation. This replaces the package-visibility precheck that could incorrectly report that no app was installed. Another maps app may choose a different road; Safe-Navi does not claim to transfer its ranked road geometry or provide its own turn-by-turn navigation.
- Save destination has a labelled editable name, visible saving state, duplicate-tap protection, success confirmation and retryable errors. Loading a saved destination preserves its exact pin and recalculates the route instead of leaving stale route results.
- Saved places and journey sharing ask for sign-in if needed. Journey creation no longer waits for a GPS callback that might fail silently. Creating a link, optionally enabling location, sharing the link and ending the journey are separate, explicit steps.
- Journey requests show progress and errors. Missing links and denied location permission no longer silently do nothing. Sharing-app launch failures are handled.

The trained model, risk weights, route-ranking objectives, official verification permissions and closure exclusions were not changed to manufacture different routes. Three preferences can legitimately recommend the same road.

## Verification log

Checks performed on 21 October 2026, IST. Scope is this route-screen/action update, not a claim that every application feature has been retested.

| Check | Result |
|---|---|
| Android app and instrumented-test APK build | PASS |
| JVM tests | PASS: 49 tests, zero failures/errors, including 6 new consumer-summary tests |
| Android lint | PASS: zero errors, 492 warnings still present |
| APK signature | PASS: one signer, v2 signature verifies |
| Live Render health | PASS: HTTP 200; trained model, PostgreSQL product store and Supabase evidence store reported |
| Cloud sign-in and authenticated profile | PASS using existing dedicated DEMO citizen |
| Save and reload exact destination coordinates | PASS against the deployed backend |
| Create a journey without GPS | PASS: HTTP 201 with a private share token |
| Journey share response and phone web page | PASS: HTTP 200 |
| End journey and read persisted status | PASS: COMPLETED |
| Test cleanup | PASS: temporary saved place deleted; simulated journey ended; test session logged out |
| Android touch/UI regression suite | PASS: 6 tests using actual coordinate taps, after recovering the emulator System UI |
| Android search through live routing and cloud ML to visible results | PASS in light and dark modes: Thane Railway Station to CBD Belapur; no offline fallback; distance/time stay visible when the risk overlay is toggled |

No real incident, crime, official closure or location telemetry was fabricated. No emergency call or notification was sent. Existing clearly labelled community DEMO content was not changed.

The first lint attempt exhausted the build tool's configured metaspace. Rerunning with sufficient build-tool memory completed successfully; no product code was changed to bypass lint. The API 36 emulator also exposed an old Espresso input-injection incompatibility, so the new regression tests use Android's public UiAutomation touch API instead.

Raw cloud receipts and account credentials remain local and excluded from Git. This document contains no account passwords, private journey links or case identifiers.

The six UI checks cover short details plus opt-in diagnostics, the save-place and journey sign-in prompts, the Android route-share intent, the confirmed directions intent with both endpoints, and closing details while keeping distance visible. External share and directions intents are intercepted by the test: no message is sent and no third-party navigation journey is started. Successful authenticated save/journey persistence is tested separately against production, not claimed as full touch-to-cloud coverage.

Physical-phone GPS accuracy, background-location delivery and other installed apps' actual handling of share/directions intents still require a phone check. Those are not covered by an emulator intent interception or an API test. The route ranking and permission boundaries are unchanged; this is not a fresh audit of all news, official and admin workflows.

### Visual follow-up logged, not marked fixed

In the final long-trip dark-mode screenshot, the A marker is not clearly visible and the destination marker is close to SOS. The endpoint coordinates pass the viewport check, but that is not proof of unobstructed marker visibility. Marker rendering/overlay clearance needs its own regression check. This update does not claim to fix that separate map issue.

## Teammate's Hindi message

“Kilometres nahi dikha raha hai kitna dur hai and all” means: **“It isn't showing the distance in kilometres, how far away it is, and so on.”**

## Artifact identity

- APK size: 78,134,158 bytes (about 74.5 MiB).
- SHA-256: `42241122205CDAB90337837AA274C43CDF0E3A9B604FAED0A21E848F820CA8EC`.
- Backend default: https://safe-navi-api.onrender.com

Implementation references: [Android guidance on launching external activities without a package-visibility precheck](https://developer.android.com/training/package-visibility/use-cases), [Google Maps universal directions URLs](https://developers.google.com/maps/documentation/urls/get-started).
