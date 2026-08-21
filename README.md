# Safe-Navi

Safe-Navi is a runnable 30% college-project milestone that combines real OpenStreetMap routing with a privacy-safe Mumbai–Navi Mumbai synthetic road-safety dataset. It supports citizen reporting, government verification, explainable point risk, and Fastest/Balanced/Safest route comparison.

The Android namespace and application ID are `com.safenavi.app`. The repository contains no previous project-name namespace or branding.

## What is genuinely integrated

- MapLibre renders OpenStreetMap-derived OpenFreeMap tiles.
- Nominatim resolves explicitly submitted source and destination names.
- Valhalla returns genuine driving, walking and cycling road routes.
- FastAPI queries an included SQLite spatial runtime derived from the completed synthetic dataset.
- Point scores and route exposure use the dataset's ten factors for the current time period.
- The map visibly reports `Dataset API`, time period, coverage percentage and leading factors.
- A purpose-built Safe-Navi interface uses a map-first hierarchy, route-and-shield identity, safety preference sheet and consistent city-teal/signal-lime design system.
- Light and dark themes share semantic surface, text, route-risk and status colors; the app follows Android night mode and also exposes a Dark Mode switch under Settings.
- If the local API is stopped, the Android app clearly labels and uses a controlled offline-hazard fallback.
- Connected account screens remain optional and now fail gracefully when Firebase is not configured instead of closing the app.
- Citizen reports and government-verified hazards remain separate domain concepts.
- Thirteen Android JVM tests and three backend tests cover the current milestone.

## Dataset boundary

The original analytics dataset contains 452,466 physical OSM road segments and 1,809,864 segment-time rows across morning peak, midday, evening peak and night. Its ten factors are traffic congestion, crime risk, lighting quality, population density, road condition, pedestrian activity, emergency access, flood risk, isolation and public-transport access.

The full 158 MB `road_segments.parquet` remains the street-level training and future PostGIS source. It is not copied into Git because it exceeds normal GitHub file limits. The repository includes:

- `backend/data/source/area_cells.csv.gz` — the complete 1 km area/time aggregation from that dataset;
- `backend/data/source/manifest.json` — provenance and complete road-level statistics;
- `backend/data/safe_navi_runtime.sqlite` — 6,508 indexed area/time rows used by the app today;
- `backend/scripts/build_runtime_db.py` — a standard-library reproducible database builder.

This is defensible for the 30% milestone: the application uses the completed dataset at city-wide area-cell resolution, while street-segment PostGIS lookup and ML remain explicitly scheduled work.

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

### 2. Run Android

1. Install Android Studio, Android SDK 34 and an Android 7.0+ emulator.
2. Open the repository root in Android Studio.
3. Copy `local.properties.example` to `local.properties` and correct `sdk.dir` if Android Studio does not create it.
4. Start an emulator. The default `RISK_API_BASE_URL=http://10.0.2.2:8000` reaches the host computer from the Android emulator.
5. Run the `app` configuration.
6. Choose **Enter citizen demo**, open **Map**, and use Vashi Railway Station → CBD Belapur.

For a physical phone, replace `10.0.2.2` in `local.properties` with the computer's LAN IP, keep both devices on the same network, and allow port 8000 through the local firewall.

### One-command helper

From the repository root:

```powershell
.\run_demo.ps1
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
```

SQLite provides a small, shareable runtime for the college demonstration. `backend/sql/postgresql_postgis_schema.sql` documents the production migration path for exact road-segment spatial intersections.

## Route ranking

FastAPI samples each real candidate route, looks up matching dataset cells, averages synthetic risk and factors, and calculates:

- Fastest: `travel_minutes`
- Balanced: `travel_minutes + 0.35 × risk_exposure`
- Safest: `travel_minutes + 1.00 × risk_exposure`

The response includes coverage ratio and the leading route factors. Missing API connectivity never masquerades as live dataset scoring; the UI says `offline fallback`.

## Optional configuration

```properties
RISK_API_BASE_URL=http://10.0.2.2:8000
NOMINATIM_BASE_URL=https://nominatim.openstreetmap.org
VALHALLA_BASE_URL=https://valhalla1.openstreetmap.de
GEMINI_API_KEY=
CLOUD_NAME=
UPLOAD_PRESET=
FIREBASE_DATABASE_URL=
```

No Google Maps key is required. Firebase, Cloudinary and Gemini are optional integrations and are not required for the dataset-backed safety demo.

## GitHub

The repository is GitHub-ready and excludes local secrets, virtual environments, build folders and the oversized Parquet file. See `docs/GITHUB_SETUP.md` for exact commands. To distribute the full Parquet dataset, use a GitHub Release asset, Git LFS, institutional storage or the documented generator rather than committing it to normal Git history.

## Limitations

- Synthetic factors only; no real-world predictive accuracy claim.
- Runtime SQLite uses 1 km aggregated cells, not exact street-segment intersection.
- Public OpenFreeMap, Nominatim and Valhalla services are for light demonstration use.
- Production government authorization still requires server-issued roles and deployed security rules.
- No trained ML model is claimed in this milestone.
- Safe-Navi is not an emergency service and never guarantees that a route or place is safe.
