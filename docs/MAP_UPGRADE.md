# Map upgrade — 17 October 2026

## What changed

- Fastest, Balanced and Safest recommendations are shown simultaneously, with time to one decimal place, risk exposure and a separately labelled preference score. Tap any card to highlight that recommendation without fetching a different set of roads.
- All eligible road alternatives remain on the map. Blue denotes Fastest, green Balanced and orange Safest; shared paths overlap. Routes excluded by authoritative closures are not displayed as navigable alternatives.
- A and B markers identify the resolved start and destination; search fields use the same labels. Camera bounds include every allowed alternative and both endpoints, with padding for map controls.
- **Area crime info** checks government-verified active crime reports within 3 km of the current map centre. It displays report-derived crime labels, counts and nearby report titles. Pan/zoom the map, then open it to inspect another area.
- The area safety badge now refreshes for the map centre. An unavailable score clears the previous numeric score instead of leaving stale information visible.
- Android sends complete route geometry for closure checks. The backend samples only dataset/ML inputs, preserving the established 100-point inference representation and 80-point dataset lookup pipeline. This prevents simplification from cutting across bends or skipping closed road sections.
- New `/api/v1/risk/area-crime` endpoint and additive route response fields: `recommendations`, `distinct_recommended_routes`, `ranking_formula`. Existing `results` and profile request contract remain supported.
- Server-origin validation uses the built-in URI parser, avoiding eager OkHttp/Okio class initialisation during application startup. Validation still rejects public cleartext HTTP, embedded credentials, unexpected paths and invalid ports; loopback-looking DNS names are also rejected.

## Demonstration language — important

The three profiles use **minutes + weight × risk exposure**, with weights 0, 0.35 and 1. Their objective scores differ when risk is nonzero even if the same physical road is selected. A score is **not** travel time, a probability, or a percentage of safety. Never invent different ETA/risk values to imply a difference where none exists. If all profiles select the same road, the app explicitly says so. Real route variation depends on the roads returned by the provider and the currently verified conditions.

Crime labels are keyword-derived from the title, description and reason of verified **CRIME-category** hazards. They are not separately reviewed legal findings. A record can match more than one label; otherwise its type is unspecified. Counts describe available active records, **not** which crime statistically occurs most often. Resolved/expired and unverified citizen reports are not included. There is no historical crime-frequency dataset in the supplied ML model; its general crime-risk factor is synthetic. No matching records means **unknown**, not crime-free.

The underlying verified hazards already affect route exposure or exclusion according to severity/road status. These new descriptive labels do not add another arbitrary routing penalty. News remains review-only until an official separately verifies and publishes a located hazard.

## Installation and backend

Install the map-upgrade APK over the existing app without clearing data. Restart **Start Safe-Navi** to load the updated backend; the crime endpoint requires the updated server. In **Server connection → Test and save**, use the current reachable laptop origin or your deployed HTTPS origin. An old running backend will not acquire new endpoints until restarted.

For hosting choices, persistence, credential rotation and deployment instructions, see [FREE_HOSTING.md](FREE_HOSTING.md). Deployment configuration being present does **not** mean cloud deployment has occurred.

The automated rendering test uses isolated artificial road geometry only for assertions; it does not seed demonstration incidents or change live report records.

## Verification status

- Backend regression suite: 33 passed, using isolated test databases.
- Android JVM suite: 33 passed, including full closure-geometry payload preservation, three-profile trade-offs, same-road consistency and server-origin validation.
- Android device suite: all 7 passed on the API 36.1 emulator, at 720×1600 / 320 dpi (360 dp width), font scale 1.3. Includes the three-card rendering/selection/A/B-marker check, offline search, cross-city offline route, all nine mode/profile combinations, polygon exclusions and point-closure rerouting. Android packages were AOT-precompiled before this successful run; earlier cold emulator instrumentation starts hit startup ANRs. Origin validation was subsequently changed to avoid eagerly loading OkHttp during Application.onCreate. This is not a measured real-phone cold-start guarantee.
- Final APK assembly and Android lint passed with 0 lint errors; 412 project lint warnings remain. No claim that all warnings or all possible bugs have been eliminated.
- Independent cloud deployment and real-phone acceptance still need verification; passing these automated tests is not proof of end-to-end cloud availability. No public service was deployed during this change.
