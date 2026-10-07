# Safe-Navi

> **Runnable product build:** Safe-Navi uses one authenticated backend for citizen,
> community, assistant, government operations, evidence, notifications, news signals,
> verified hazards and audit history. Deployment-scale upgrades are tracked separately.

Safe-Navi is a map-first Android safety platform that combines OpenStreetMap routing, a baseline Mumbai–Navi Mumbai road-risk dataset, persistent citizen reporting, authorized government verification, and explainable Fastest/Balanced/Safest route comparison.

The Android namespace and application ID are `com.safenavi.app`. The repository contains no previous project-name namespace or branding.

## What is genuinely integrated

- MapLibre renders OpenStreetMap-derived OpenFreeMap tiles.
- Nominatim resolves explicitly submitted source and destination names.
- Valhalla returns genuine driving, walking and cycling road routes.
- FastAPI queries an included SQLite spatial runtime derived from the completed synthetic dataset.
- Point scores and route exposure use the teammate-trained 90-feature linear regression model against the dataset's current-period factor aggregates.
- The map visibly reports the trained model version, coverage-adjusted confidence, time period and leading signed factor contributions.
- A purpose-built Safe-Navi interface uses a map-first hierarchy, route-and-shield identity, safety preference sheet and consistent city-teal/signal-lime design system.
- Light and dark themes share semantic surface, text, route-risk and status colors; the app follows Android night mode and also exposes a Dark Mode switch under Settings.
- Per-app English, Hindi and Marathi locale switching is available without changing the phone language; primary navigation, map controls and safety settings are localized through Android resources.
- Citizens can download a bounded Navi Mumbai MapLibre region (zoom 8–14) for offline base-map viewing, inspect its size/readiness and remove it.
- A bundled Navi Mumbai road graph provides genuine on-device place search and driving, walking and cycling A* routing. Fastest/Balanced/Safest remain available offline and use conservative road risk plus the last verified closure snapshot.
- Network failures are explicit; unavailable services never fabricate live incidents, AI answers or route-risk data.
- Account sessions persist, map screens stay portrait-stable, and stale network responses cannot replace a newer route or point-risk request.
- Map input is empty by default and resolves user-selected places through Nominatim; Valhalla remains the route provider.
- Citizen reports and government-verified hazards remain separate domain concepts.
- The citizen Community is backed by persistent API posts, authenticated photograph attachments, replies and per-account helpful votes; community content is visibly separated from verified alerts.
- Navi, the in-app safety assistant, uses Groq through the backend so the provider key is never embedded in the APK. It carries limited conversation context and is instructed not to invent incidents or replace emergency services.
- Citizen reports support reverse-geocoded locations, anonymous identity, and up to five secure image/video evidence files.
- Each report has a private citizen–official conversation. Citizens can answer information requests with text and a new photograph; officials can reply, preview authenticated evidence, and return the case to review without leaving the app.
- Government operations include a complete selectable queue, department/team assignment, SLA targets, information requests, duplicate suggestions, evidence/confirmation counts and preview, audit history, verified-hazard lifecycle changes, and metrics.
- Persisted safety-inbox notifications cover assignment, information requests, review and hazard lifecycle changes.
- Android background safety sync raises system notifications for unread official updates and newly published critical hazards, with a user-controlled notification switch.
- Saved places, trusted emergency contacts, journey history and consent-based live-journey sharing are persisted per citizen account. With explicit consent, a user-visible Android foreground service keeps the journey current after the map is closed; ending it offline queues authenticated server closure for reconnection.
- The map includes an emergency toolkit for dialing 112, sharing current location, and calling a trusted contact.
- Government staff can map multi-point road corridors or affected polygons. These geometries render on citizen maps and their full segments or boundaries contribute to route exposure.
- Government staff can mark a verified road `CLOSED`, or `CRITICAL` and `UNSAFE`, with a configurable 10–1000 m exclusion buffer. Every route profile removes candidates entering that official no-go zone; if every alternative is blocked the app reports that no safe alternative is available instead of silently recommending one.
- Citizen reports and selected evidence can be created fully offline. Evidence is copied immediately into app-private no-backup storage, capped at 15 MB per file and 100 MB total, SHA-256 checked before upload, and retried with authenticated WorkManager jobs. Uploaded evidence IDs and report request IDs survive retries without duplicate evidence or reports.
- Active shared journeys support 15/30/60/120-minute safety check-ins. Prompts still appear offline, acknowledgements wait for connectivity, and trusted viewers see the last response, next due time or an overdue warning on the consented share page.
- A WebSocket map channel pushes verified hazard changes to active citizen maps immediately; bounded polling remains as a resilience fallback.
- HTTPS RSS feeds configured by the operator can be ingested, deduplicated, classified and reviewed; news signals can never create public hazards automatically.
- Configured news feeds are ingested on startup and then on a bounded schedule; government staff can also trigger ingestion manually.
- The supplied best-performing model is bundled as a non-executable JSON coefficient artifact. It achieved held-out R² 0.9316 and street-grouped five-fold CV R² 0.9325 ± 0.0033. An optional HTTPS model service can still override it; failures fall back to this bundled trained model and then to the deterministic baseline.
- Fourteen Android JVM tests, three on-device instrumentation tests and seventeen backend integration tests cover the current build.

## Dataset boundary

The original analytics dataset contains 452,466 physical OSM road segments and 1,809,864 segment-time rows across morning peak, midday, evening peak and night. Its ten factors are traffic congestion, crime risk, lighting quality, population density, road condition, pedestrian activity, emergency access, flood risk, isolation and public-transport access.

The full 158 MB `road_segments.parquet` remains the street-level training and future PostGIS source. It is not copied into Git because it exceeds normal GitHub file limits. The repository includes:

- `backend/data/source/area_cells.csv.gz` — the complete 1 km area/time aggregation from that dataset;
- `backend/data/source/manifest.json` — provenance and complete road-level statistics;
- `backend/data/safe_navi_runtime.sqlite` — 6,508 indexed area/time rows used by the app today;
- `backend/scripts/build_runtime_db.py` — a standard-library reproducible database builder.
- `app/src/main/assets/offline/navi_mumbai_graph.sqlite` — 175,327 nodes and 376,953 directed edges used for on-device routing and search;
- `backend/scripts/build_offline_graph.py` — the reproducible Parquet-to-Android graph builder.
- `backend/models/safety_score_linear_v1.json` — the integrated 90-feature teammate model in safe production inference format;
- `backend/models/MODEL_CARD.md` — selection evidence, hashes, evaluation caveats and deployment behavior;
- `backend/scripts/export_linear_model.py` — reproducible trusted-pickle-to-JSON exporter.

The application applies the trained linear model to city-wide area-cell aggregates. Since linear prediction commutes with numeric averaging, the ten factor inputs remain mathematically consistent at route level; unavailable per-road categorical metadata uses explicit unknown/default categories. Street-segment PostGIS lookup remains a deployment upgrade.

All safety factors and targets are synthetic. They are not observed crime, lighting, population, traffic or safety measurements and cannot describe the actual safety of any neighbourhood.

## Quick start on Windows

### 1. Start the dataset API

```powershell
cd backend
py -3.11 -m venv venv
.\venv\Scripts\python.exe -m pip install -r requirements.txt
.\venv\Scripts\python.exe -m uvicorn app.main:app --host 0.0.0.0 --port 8000
```

Verify `http://127.0.0.1:8000/health` or open the interactive API at `http://127.0.0.1:8000/docs`.

To enable the Navi assistant, create a Groq API key and set it only in the backend shell before starting Uvicorn:

```powershell
$env:GROQ_API_KEY="your-server-side-key"
$env:GROQ_MODEL="openai/gpt-oss-20b" # optional production model override
```

If the key is absent, the assistant returns an explicit configuration error; the app never fabricates a local AI response.

For persistent local development, copy `backend/.env.local.example` to
`backend/.env.local`. That file is ignored by Git and is loaded without overriding
environment variables supplied by a production host. `SAFE_NAVI_NEWS_FEEDS` accepts a
comma-separated list of permitted HTTPS RSS or Atom URLs; configured signals are
deduplicated and remain in the government review queue until explicitly reviewed.

The backend now also creates `backend/data/safe_navi_product.sqlite` on first use
and provides persistent APIs for account registration/login, citizen reports,
government triage/decisions, verified map hazards, audit history, and hazard updates.
Create the first authorized account interactively:

```powershell
cd backend
.\venv\Scripts\python.exe scripts\create_user.py --name "Government Reviewer" --email reviewer@example.org --role government
```

The live API contract is available in `/docs`. Only government-verified active or
monitored hazards are exposed by `/api/v1/hazards/map` and added to point/route risk.
Raw citizen reports never change public routing.

### 2. Run Android

1. Install Android Studio, Android SDK 34 and an Android 7.0+ emulator.
2. Open the repository root in Android Studio.
3. Copy `local.properties.example` to `local.properties` and correct `sdk.dir` if Android Studio does not create it.
4. Start an emulator. The default `RISK_API_BASE_URL=http://10.0.2.2:8000` reaches the host computer from the Android emulator.
5. Run the `app` configuration.
6. Register a citizen account, open **Map**, and compare a route.

For a physical phone, replace `10.0.2.2` in `local.properties` with the computer's LAN IP, keep both devices on the same network, and allow port 8000 through the local firewall.

### One-command helper

From the repository root:

```powershell
.\run_local.ps1
```

It validates the database, creates/updates the backend environment, starts FastAPI, builds the APK, and installs/launches it when an Android device or emulator is connected.

## Verification

```powershell
cd backend
.\venv\Scripts\python.exe -m pytest -q

cd ..
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

The APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

## Runtime architecture

```text
OSM/Geofabrik snapshot
        │
        ├── road_segments.parquet (street-level analytics / future ML + PostGIS)
        │
        └── area_cells.csv.gz
                 │ reproducible builder
                 ▼
         SQLite + RTree index
                 │
              FastAPI
                 │ point/route risk JSON
                 ▼
Android app ── Nominatim + Valhalla + MapLibre
      │
      └── bundled SQLite road graph ── offline search + A* routing
```

SQLite provides the current local runtime. `backend/sql/postgresql_postgis_schema.sql` documents the production migration path for exact road-segment spatial intersections.

## Route ranking

FastAPI samples each real candidate route, looks up matching dataset cells, averages synthetic risk and factors, and calculates:

- Fastest: `travel_minutes`
- Balanced: `travel_minutes + 0.35 × risk_exposure`
- Safest: `travel_minutes + 1.00 × risk_exposure`

The response includes coverage ratio and the leading route factors. Missing API connectivity never masquerades as live dataset scoring; the UI says `offline fallback`.

Official closure enforcement happens before ranking. A route intersecting a verified
no-go buffer is excluded even when Fastest is selected. Offline A* applies the last
successfully retrieved verified-hazard snapshot and identifies its result as conservative
offline intelligence rather than a live government feed.

## Optional configuration

```properties
RISK_API_BASE_URL=http://10.0.2.2:8000
NOMINATIM_BASE_URL=https://nominatim.openstreetmap.org
VALHALLA_BASE_URL=https://valhalla1.openstreetmap.de
```

Backend environment variables include `GROQ_API_KEY`, optional `GROQ_MODEL`,
comma-separated permitted `SAFE_NAVI_NEWS_FEEDS`, `SAFE_NAVI_CORS_ORIGINS`,
`SAFE_NAVI_PRODUCT_DB`, `SAFE_NAVI_EVIDENCE_DIR`, optional `SAFE_NAVI_ML_ENDPOINT`,
and optional `SAFE_NAVI_LOCAL_MODEL`. The persistent live-news interval is selected by
an administrator in the application and is restricted to 12 or 24 hours.
The bundled trained model is active by default. An external ML endpoint must use HTTPS
(loopback HTTP is accepted for development), and each
prediction must return `route_id`, `risk_exposure` from 0–100, `confidence` from 0–1,
`factor_contributions`, and `model_version`. If it fails validation or times out, the
bundled model takes over. No provider secret is embedded
in the APK. No Google Maps, Firebase, Cloudinary, Gemini, CameraX or Unirest dependency
remains in the Android product.

## GitHub

The repository is GitHub-ready and excludes local secrets, virtual environments, build folders and the oversized Parquet file. See `docs/GITHUB_SETUP.md` for exact commands. To distribute the full Parquet dataset, use a GitHub Release asset, Git LFS, institutional storage or the documented generator rather than committing it to normal Git history.

## Limitations

- Synthetic factors only; no real-world predictive accuracy claim.
- Runtime SQLite uses 1 km aggregated cells, not exact street-segment intersection.
- Public OpenFreeMap, Nominatim and Valhalla endpoints must be replaced with supported hosted or self-hosted services before public scale.
- Government roles are server-issued and deny-by-default; deployment still needs HTTPS, managed secrets, monitoring and controlled staff onboarding.
- The integrated model was trained on synthetic targets. Its strong test/CV metrics validate the software pipeline, not real-world safety prediction; deployment requires authorized observational data, temporal validation, fairness review and monitored retraining.
- The compact API runtime has the ten trained factor inputs but not every original per-road categorical attribute, so those low-impact fields use documented unknown/default categories until the street-segment PostGIS source is deployed.
- Background journey updates use a persistent, user-visible foreground-service notification and explicit per-journey consent. Public deployment still requires an HTTPS API, Android background-location policy review and battery/device testing.
- Offline search and routing cover the bundled Navi Mumbai graph only. Cached map tiles must be downloaded separately, and newly verified hazards cannot arrive until connectivity returns; offline routing deliberately uses the last verified snapshot.
- The bundled routing graph adds roughly 60 MB to the uncompressed Android assets. A production release should evaluate Play Asset Delivery or bounded downloadable graph packs.
- Hindi and Marathi cover the product’s core navigation, map and settings surfaces; remaining long-form operational copy should be fully localized and professionally reviewed before public deployment.
- App-private offline evidence benefits from Android filesystem/device protection and is excluded from backup, but a production threat model should decide whether an additional Keystore-backed file-encryption layer is required for target devices.
- Safe-Navi is not an emergency service and never guarantees that a route or place is safe.
