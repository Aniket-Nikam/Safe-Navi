# Safe-Navi 1.8 navigation preview

Version code 22. Android update; no backend schema or hosting change is required.

## Demonstrate it indoors

1. Install `Safe-Navi-1.8-Navigation-Preview.apk` over the previous debug-signed Safe-Navi application. Do not uninstall just to update, since uninstalling removes local drafts/settings.
2. Open Map. Select **Vashi Railway Station** as A and **SIES GST, Nerul** as B. Plan and select a route preference.
3. Open **Route details > Start navigation > Demo preview (no GPS)**.
4. The screen explicitly says **DEMO PREVIEW / SIMULATED LOCATION**. The blue arrow travels along the selected route. The next instruction, distance to that turn, remaining distance and estimated remaining time update.
5. Use **Pause / Resume**, **Next turn**, and **5x / 20x / 60x speed** to control the demonstration. Next turn skips forward along the real route; it does not alter or invent a road.
6. Use **Overview** to inspect the whole route and **Recenter** to follow the arrow. **Voice off / on** uses the device's English text-to-speech engine when installed and supported.
7. Tap **Exit** to return to the planner.

The simulation is not physical travel, live GPS or real-time traffic. It never injects a location into Android, publishes a hazard, starts a shared journey, or uploads a simulated position to the backend. It is a preview of the selected route, not evidence of real-world navigation accuracy.

## Foreground location option

Choose **Start navigation > Live location (screen open)** and read the confirmation. Precise location permission and device location services are required. The navigation screen requests fresh location updates only while in the foreground. Stale/low-accuracy fixes do not advance route progress. Leaving the screen stops its location updates.

This is a demonstration prototype, not road-tested driving guidance. There is no live traffic, automatic rerouting, lane guidance, background navigation, or navigation-accuracy guarantee. When off route, it warns and preserves the selected route; return to the planner to choose another route. Obey road signs and do not operate the phone while driving.

Live position display is separate from consented journey sharing. This update does not silently start, end or alter an existing shared journey.

## Implementation

- Valhalla requests now ask for English turn instructions, retaining maneuver geometry indices across route legs.
- Instructions stay attached to each `RouteCandidate`. Cloud risk evaluation and existing route ranking retain that candidate; navigation does not silently substitute a newly calculated route.
- MapLibre displays the selected road, A/B endpoint markers, a directional arrow and any verified alert pins contained in the selected route snapshot. Alert pins open the official explanation.
- `NavigationProgress` computes distance along the geometry, upcoming instruction, remaining distance/time and approximate on-route GPS matching. It does not predict traffic.
- A private cache file transfers the selected geometry/instructions between activities without oversized Android Intent transactions. It is removed when navigation finishes normally; stale snapshots are eligible for cleanup on a subsequent launch.
- Android's Fused Location Provider supplies foreground GPS fixes. Android TextToSpeech supplies optional spoken guidance.
- The external Google Maps directions option remains available in the navigation chooser.
- Routes without maneuver data, including the existing offline fallback, explicitly say that turn instructions are unavailable; they do not invent directions. Internet/provider availability is needed for initial online routes and uncached map tiles.

No new paid SDK, subscription, credentials or external service account was added. The trained model, ranking weights, verified-hazard permissions and closure exclusions were not changed.

## Verification

Final run: 22 October 2026, IST. Rehearsal targeted the existing public routing provider and deployed Safe-Navi API, not a local mock backend. The controlled cloud test used the midday time-period input; it does not claim current live traffic.

| Check | Result |
|---|---|
| Debug APK and Android test APK build | PASS |
| JVM regression tests | PASS: 60 tests, zero failures/errors, including 10 navigation-progress tests and maneuver parsing |
| Android lint | PASS: zero errors; 510 warnings remain |
| Android device regression | PASS: 9 tests, zero failures |
| Public Valhalla instructions | PASS: demo route returned real English maneuvers for its road alternatives |
| Deployed risk evaluation | PASS: real candidates evaluated by `trained_ml`; selected candidate retains its instructions |
| Planner > Start navigation > Demo preview | PASS: opens the in-app navigation activity, not an external maps app |
| Simulation controls | PASS: advances along selected geometry; pause/resume, next turn, speed, overview and exit respond |
| Overview presentation | PASS: both endpoints project between the instruction card and bottom controls; final screenshots inspected |
| Foreground-only callback guard | PASS using injected test locations; a callback while backgrounded does not advance progress |
| Private snapshot path validation | PASS: traversal path rejected |
| Existing route actions | PASS: sharing, optional external directions, sign-in prompts and closing details |
| Upgrade install on emulator | PASS without uninstalling the previous application |

The GPS check injects test locations into the navigation callback. It is **not** a physical-phone GPS accuracy, road test or full location-acquisition test. Audibility of voice guidance depends on the device's installed speech engine and voice data and was not verified on a phone. The emulator is configured without audio output.

During visual review the overview initially retained the following camera's heading/tilt and did not frame the road cleanly. It was corrected to a north-up, flat overview and retested with explicit endpoint visibility assertions. Map attribution and compass controls were moved outside the overlay cards. Android 11+ speech-engine discovery is declared in the manifest, with an on-screen fallback if speech cannot play.

No incident, hazard, crime, account or shared journey was created by these navigation tests. Backend permissions, data and ranking logic were not changed. Raw test logs and APKs remain local; the repository receives source and this sanitized guide only.

## What to say to judges

“This preview simulates movement along our selected road route so that we can demonstrate navigation indoors. The road geometry and turn instructions come from the routing provider. The simulation is clearly labelled and is not presented as live GPS or traffic.”

Older recordings that show only external Google Maps directions do not demonstrate this new in-app feature. Use this updated APK for the navigation demonstration.

## APK identity

- File: `Safe-Navi-1.8-Navigation-Preview.apk`
- Version: `1.8-navigation-preview`, code `22`
- Size: 78,170,536 bytes (about 74.5 MiB)
- SHA-256: `09E3993C5A41063545CE0581362793616467E2E697979139905289D2AF73918D`
- Default backend: https://safe-navi-api.onrender.com

References: [Valhalla maneuver and instruction API](https://valhalla.github.io/valhalla/api/route/api-reference/), [Android foreground location updates](https://developer.android.com/develop/sensors-and-location/location/request-updates), [Android TextToSpeech setup](https://developer.android.com/reference/android/speech/tts/TextToSpeech).
