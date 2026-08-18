# Safe-Navi

Safe-Navi is a native Android proof of concept for community safety, verified civic hazards, explainable local risk, and safer navigation decisions. It is migrated from XavierProject and intentionally preserves the working Java/XML/Firebase foundation while separating citizen reports from government-published hazards.

The repository runs immediately in **synthetic demo mode** without Firebase credentials. All bundled locations and events are fictional demonstrations around Mumbai and Navi Mumbai; the app does not claim live coverage or real-world safety guarantees.

## What works

- Citizen and government demo entry points with no sign-up
- MapLibre/OpenStreetMap map with severity-coloured verified hazard markers and translucent risk zones
- Tap-anywhere local safety score, confidence, and human-readable reasons
- Citizen report submission into a shared in-memory demo repository
- Government review: verify/publish, downgrade to monitoring, resolve, plus domain support for reject/duplicate and immutable audit history
- Deterministic risk engine using severity, verification, distance bands, age, status, recurrence, and confirmations
- Address/coordinate source and destination entry, plus driving, walking, and cycling routes
- Real OpenStreetMap/Valhalla route alternatives ranked as fastest, balanced, or safest
- Migrated Firebase authentication, complaint, community, comment, profile, image-upload, and Gemini assistant screens for configured builds

## Quick start

1. Install Android Studio with Android SDK 34. Java 17 or Android Studio's bundled Java 21 is supported.
2. Clone this repository and open its root folder in Android Studio.
3. Copy `local.properties.example` to `local.properties` and correct `sdk.dir`.
4. Leave service keys blank for the credential-free demo, then run the `app` configuration on an Android 7.0+ emulator/device.
5. Choose **Citizen demo** to explore/report, or **Government demo** to review and publish the pending synthetic report.

Command-line verification on Windows:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

The APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

## Optional live services

Create `local.properties` from the example and add only the keys you need. Place the matching Firebase Android configuration at `app/google-services.json`. That file and `local.properties` are ignored by Git.

```properties
GEMINI_API_KEY=your_development_key
CLOUD_NAME=your_cloudinary_cloud
UPLOAD_PRESET=your_unsigned_upload_preset
FIREBASE_DATABASE_URL=https://YOUR_PROJECT-default-rtdb.asia-southeast1.firebasedatabase.app
NOMINATIM_BASE_URL=https://nominatim.openstreetmap.org
VALHALLA_BASE_URL=https://valhalla1.openstreetmap.de
```

No Google Maps key is required. A mobile client cannot truly protect a Gemini secret; production should proxy Gemini and Cloudinary signing through a server. Deploy `firebase/database.rules.json` only after configuring server-issued Firebase custom claims (`citizen`, `government`, or `admin`) and validating the rules in the Firebase Emulator Suite.

## Architecture

- `app/src/main/java/com/example/xavierproject/safety/model` — hazard, report, audit, score, and route types
- `.../safety/risk` — transparent deterministic risk calculation
- `.../safety/service` — role authorization, government lifecycle, duplicate detection, route evaluation
- `.../safety/data` — repository boundary and demo implementation
- `.../safety/demo` — clearly labelled synthetic fixtures and shared demo session
- `docs/IMPLEMENTATION_PLAN.md` — source audit, product decisions, data design, delivery sequence, and production boundary
- `firebase/database.rules.json` — deny-by-default authorization template

The legacy Java namespace remains `com.example.xavierproject` to avoid a risky package migration during the product refactor. The application name and product experience are Safe-Navi.

## Risk semantics

`risk = 100 × (1 - product(1 - contribution / 100))`

Each eligible hazard contribution is bounded and derived from configured severity, verified/unverified weighting, distance band, time decay, lifecycle status, recurrence, and report confirmations. The visible safety score is `100 - risk`. Confidence is shown separately and rises with corroborated government-verified evidence. A lack of records returns low confidence rather than pretending the area is proven safe.

This is an explainable rules engine, **not trained ML**. An ML model should only replace it after labelled, legally obtained and fairness-reviewed data exists; keep the same interface and compare both approaches offline first.

## Open map and route services

MapLibre renders OpenStreetMap-derived vector tiles using the OpenFreeMap Liberty style. Explicit address submissions use Nominatim; there is no type-ahead autocomplete, requests are rate-limited to at most one per second, and an identifying User-Agent is sent. Valhalla returns genuine `auto`, `pedestrian`, and `bicycle` routes and alternatives. Safe-Navi decodes their polylines, measures verified-hazard exposure along each candidate, then ranks them using the selected profile. The app never invents road geometry.

The bundled endpoints are community demo services suitable only for light testing. Before distribution, obtain a hosted provider or self-host tiles, Nominatim, and Valhalla; update the three URLs in local configuration and follow provider quotas, privacy terms, and attribution requirements.

## Tests and limitations

Local JUnit tests cover score behaviour, confidence, role enforcement, verification/audit lifecycle, rejection history, duplicate matching, route ranking, Nominatim parsing, Valhalla parsing, and polyline decoding. The synthetic repository resets with the app process. Live hazard persistence, push notifications, media moderation, backend-enforced custom claims, production-grade map service hosting, offline maps, accessibility audit, and instrumentation/Espresso coverage remain production-phase work.

Never use this prototype as an emergency service or as a guarantee that a place or route is safe.
