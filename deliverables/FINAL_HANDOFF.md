# Safe-Navi 30% milestone handoff

## What is ready

- Android application ID: `com.safenavi.app`
- OpenStreetMap-derived map tiles, geocoding and real route alternatives
- Fastest, Balanced and Safest route modes
- FastAPI dataset service with interactive documentation
- SQLite/RTree runtime containing 6,508 Mumbai–Navi Mumbai area/time rows
- Ten synthetic risk factors and four time periods
- Dataset-backed point and route scoring with visible coverage and factor explanations
- Original Safe-Navi visual system: route-and-shield mark, map-first planner, safety preference sheet and accessible high-contrast palette
- Clearly labelled offline controlled-hazard fallback
- Citizen reporting and government verification demonstration flows
- 13 Android JVM tests, Android lint and APK assembly passing
- 3 FastAPI tests passing

## Fastest way to run

Install Android Studio with an emulator, then run from the repository root:

```powershell
.\run_demo.ps1
```

The helper starts the local dataset API, builds the APK, and installs/launches it when an emulator is connected. Open `http://127.0.0.1:8000/docs` to demonstrate the API independently.

Recommended route: **Vashi Railway Station → CBD Belapur**. Compare Fastest, Balanced and Safest and point out the `Dataset API`, time period, coverage and top-factor labels.

## Academic boundary

The app currently queries the complete 1 km area-cell aggregation derived from the synthetic dataset. The full 452,466-road, 1,809,864-row Parquet file is retained for analytics, model training and the next PostGIS phase; it is not committed to ordinary Git because it exceeds GitHub's normal per-file limit. The prototype demonstrates system feasibility, not real-world predictive accuracy or guaranteed route safety.

## Files to share

- `Safe-Navi-30-Percent-Final.apk` — installable debug APK
- `Safe-Navi-30-Percent-Final-Source.zip` — clean tagged source snapshot
- `Safe-Navi-30-Percent-Progress-Review.pptx` — professor presentation
- `README.md` — architecture, limitations and setup
- `docs/PRESENTATION_SCRIPT.md` — live speaking guide
- `docs/PROFESSOR_QA.md` — defensible answers

## Next milestone

Import the full street-level Parquet data into PostgreSQL/PostGIS, intersect route geometry with exact road segments, add authenticated persistent report storage, and establish ML baselines and validation metrics without claiming synthetic labels are real observations.
