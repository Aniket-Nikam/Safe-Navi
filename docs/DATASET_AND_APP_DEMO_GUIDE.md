# Safe-Navi — Exact Demonstration Plan for Tomorrow

## Recommended duration: 8–10 minutes

The strongest presentation is a two-part demonstration:

1. **Data milestone:** prove that the city-scale synthetic dataset exists, is documented, and passes QA.
2. **Application milestone:** prove that the mobile workflow and OpenStreetMap routing work, while stating that full Parquet-to-app integration is the next phase.

Do not claim that the Android route shown today is already reading all 1.8 million Parquet rows.

## Before presenting

- Start the Android emulator or install the APK on a phone.
- Open Citizen Demo and load the Map once while internet is available.
- Keep `coverage_preview.png`, `sample_10000_rows.csv`, `manifest.json`, and `QUALITY_REPORT.json` open or easily accessible.
- Use the default Vashi Railway Station to CBD Belapur route because it has already been tested.
- Keep the PowerPoint, APK, source ZIP, and dataset demo pack on a USB drive.

## Part 1 — Introduce the project (1 minute)

Say:

“Safe-Navi explores whether navigation can consider explainable safety information in addition to travel time. Citizens submit reports, an authorized user verifies them, and verified hazards can influence fastest, balanced, or safest route recommendations.”

Clarify immediately:

“The safety factors are synthetic and the road geometry is OpenStreetMap-derived. We are demonstrating technical feasibility, not making real safety claims about Mumbai.”

## Part 2 — Demonstrate the dataset (2 minutes)

### Show the coverage image

Open `coverage_preview.png` and point out Mumbai and the documented Navi Mumbai corridor.

Say:

“The dataset contains every eligible mapped OpenStreetMap road segment inside these polygons as of the source snapshot. It cannot include roads that were unmapped, private, newly built, or incorrectly tagged.”

### Show the manifest

Point to these verified values:

- 452,466 physical road segments;
- 263,055 Mumbai segments;
- 189,411 Navi Mumbai segments;
- 1,809,864 road-time rows;
- 12,124.31 km of road;
- 79,320 unique OSM way IDs;
- 3,869 unique street names;
- 719 locality labels;
- four time periods;
- exactly ten factors.

### Show the sample CSV

Open `sample_10000_rows.csv`, not the full Parquet file. Show:

- `segment_uid`, `osm_way_id`, `street_name`, and `area_name`;
- road attributes such as class, lanes, length and geometry;
- the ten factor columns;
- `time_period`;
- `synthetic_safety_risk_score` and `synthetic_risk_label`;
- `ml_split`.

Say:

“The supplied split uses 2 km spatial blocks. Nearby segments and all four rows for the same physical segment remain in one split, reducing geographic leakage.”

### Show the quality report

Point out that QA passed for:

- no null factor values;
- all factors within 0–100;
- no duplicate segment-period keys;
- valid geometries;
- positive segment lengths;
- all four periods for every segment;
- spatially consistent train/validation/test split;
- balanced synthetic target classes.

## Part 3 — Demonstrate the Android application (3–4 minutes)

### Citizen Home

1. Open **Enter citizen demo**.
2. Point out the visible **Synthetic demo data** label.
3. Explain the displayed risk, safety score, confidence and reason.

Say:

“Risk and confidence are separate. Limited evidence must not be presented as proof that an area is safe.”

### OpenStreetMap routing

1. Open **Map**.
2. Keep the default source: Vashi Railway Station.
3. Keep the default destination: CBD Belapur.
4. Select **Drive**.
5. Select **Balanced**.
6. Press **Find safety-aware routes**.

Point out:

- the OpenStreetMap-derived basemap;
- real Valhalla road geometry;
- route distance and duration;
- number of alternatives;
- synthetic verified-hazard exposure;
- Fastest, Balanced and Safest buttons;
- Walk and Cycle modes.

Say:

“The road route is real OpenStreetMap geometry. The safety exposure shown in this mobile milestone comes from controlled synthetic hazard scenarios. The full road-segment Parquet dataset will be served through a backend in the next integration phase.”

### Citizen-to-government workflow

1. Return to Home.
2. Open **Report danger** and show the reporting screen.
3. Return to the welcome screen.
4. Enter **Government demo**.
5. Show a pending report and one verification, monitoring, or resolution action.

Explain that citizen reports and verified hazards are separate so an unverified report cannot automatically become an official route warning.

## Part 4 — Explain why Parquet is not inside the APK (45 seconds)

Say:

“The complete Parquet file is approximately 158 MB and contains 1.8 million rows. It is designed for analytics, model training, and indexed spatial lookup. Shipping and scanning it directly inside every phone would increase the APK size and produce inefficient queries. The planned architecture stores it behind FastAPI with PostgreSQL/PostGIS, or produces compact indexed regional extracts. The phone sends route geometry and time period; the service returns the relevant segment risks and explanation.”

## Part 5 — Future work (1 minute)

Present the next sequence:

1. load Parquet/GeoPackage data into PostgreSQL/PostGIS;
2. expose nearby-segment and route-risk endpoints through FastAPI;
3. connect the Android route evaluator to those endpoints;
4. persist reports and enforce government roles server-side;
5. train baseline models using the supplied spatial split;
6. compare ML with the explainable deterministic baseline;
7. evaluate calibration, geographic bias and false reassurance;
8. migrate the validated client to Flutter for Android and iOS;
9. add SOS/location sharing only after privacy and threat modelling.

## Final sentence

“Our 30% milestone therefore consists of a quality-checked city-scale synthetic dataset and a runnable Android proof of concept. We have proved the dataset pipeline and product workflow independently; the next milestone connects them through a secure spatial backend and evaluates the predictive approach scientifically.”
