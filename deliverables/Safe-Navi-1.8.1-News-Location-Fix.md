# Safe-Navi 1.8.1 — news location and login fixes

## Changes

- News publication now looks for a single supported locality in the headline, then the article's locality hint/summary. A Juhu headline takes priority over an older, broad Mumbai hint.
- The app asks the real Photon map provider for that area and automatically selects a matching locality result. No incident coordinates are invented or hard-coded in application code.
- Officials can keep the suggested area after checking it against the source, change it through place search, or tap the preview map to move the pin.
- The label, visible marker and publication coordinates use the same selection state. A pin selected before the map style finishes loading is redrawn when it becomes ready.
- Selecting a pin clears a previous missing-pin validation error. A late automatic search result cannot overwrite a manual selection.
- Changing the location resets the verification checkbox. Publishing still requires a verification note and explicit current-incident/location confirmation. Closure radii are validated as finite numbers from 10 to 500 metres.
- Login no longer displays the backend URL. The existing Server connection action remains available for troubleshooting.
- App version: `1.8.1-news-location`, version code `23`, same application ID/signing identity for an in-place update.

## Important boundary

An automatically suggested locality pin is approximate, not proof of an exact crime scene or road closure. An official must check the source and selected point before publishing. City-only, conflicting, multiple-area or unsupported-area stories do not receive an arbitrary Mumbai-centre pin. The app clearly asks for a location in those cases; provider outages also retain manual map selection.

This location helper uses a supported Mumbai-region locality catalogue and geocoding, not a newly trained news model. It does not broaden the existing Mumbai/Navi Mumbai place-search coverage. No routing-model weights, route-ranking algorithm, authentication roles or backend publication rules were changed.

## Verification

- JVM: 74 tests, zero failures (14 new locality/selection regression cases).
- Android emulator: eight news, login, report-draft and private-conversation checks passed against a disposable localhost backend. Includes a real MapLibre touch after an earlier validation error, late-response protection, an automatic pin surviving style loading, and publication with matching latitude/longitude/address returned by the backend.
- Android emulator: six existing route-action tests passed (sharing, navigation handoff, signed-out save/share handling, concise details and distance preservation).
- Backend: seven existing news/chat integration tests passed, including official-only publication, hazard exposure, closure exclusions, expiry and private case-message isolation.
- Android build and lint completed successfully: zero lint errors, 516 warnings remain across the app.
- Real geocoder read check: Juhu returned an actual Juhu/Mumbai area result before similarly named businesses/landmarks.
- Deployed cloud read check: healthy, PostgreSQL product store, Supabase evidence store, trained-ML risk provider.

Publication regression checks used an isolated local backend and controlled place suggestions, not a real news story published to the public cloud map. No new live hazard, closure or citizen case was created for this update. These checks do not claim physical-phone GPS field testing.

## Install and demonstrate

Install `Safe-Navi-1.8.1-News-Location.apk` over the existing app; do not uninstall it first. The cloud connection, accounts and drafts remain in place. A new APK is required to receive these client-side fixes; no backend change is required.

APK size: 77,968,313 bytes. SHA-256: `AD4D641B33D1903C3B80CB8E256D05E031FBBB5B0EA9DEA631F21B99DC3FF84B`. APK v2 signature verified with the same signing certificate as the preceding 1.8 build.

As Safety Official: News intelligence → select an unpublished story with a supported locality → Verify location & publish hazard. Wait for the suggested area, optionally move the pin, then verify the actual incident/location. Do not publish a fictional rehearsal case or a stale article just to demonstrate route changes.
