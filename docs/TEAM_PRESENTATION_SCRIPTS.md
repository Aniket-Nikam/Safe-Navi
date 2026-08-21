# Safe-Navi team presentation scripts

These scripts are written for an 8–10 minute college review. Replace `Member 1`, `Member 2`, and so on with your names before rehearsing. The person controlling the laptop should open the presentation, start the demo with `run_demo.ps1`, and keep `http://127.0.0.1:8000/docs` open in a browser tab before the presentation begins.

## Common pre-presentation checklist

1. Start Android Studio and boot the emulator.
2. From the Safe-Navi project folder, run `.\run_demo.ps1` in PowerShell.
3. Confirm `http://127.0.0.1:8000/health` returns `status: ok`.
4. Keep these tabs ready: PowerPoint, Android emulator, FastAPI `/docs`, and the project README.
5. In the emulator, keep Safe-Navi on the entry screen.
6. Use `Vashi Railway Station` to `CBD Belapur`, `Drive`, and `Balanced` for the main route demonstration.
7. Do not call the synthetic values real crime or safety measurements.

---

# Version A: three teammates

## Member 1 — Problem, objective and current milestone (about 2.5 minutes)

### Slide 1: Introduction

“Good morning. Our project is called Safe-Navi. It is a community-safety and intelligent-navigation platform designed as a feasibility prototype for Mumbai and Navi Mumbai.

Most navigation applications primarily optimize distance and travel time. However, two routes with similar travel time may have very different contextual conditions. One may have better lighting, better emergency access and more pedestrian activity, while another may be isolated, flood-prone or poorly maintained.

Our project investigates whether safety-related context can be added as an explainable routing objective alongside time and distance.”

### Slide 2: Academic boundary

“The most important limitation is that our current safety data is synthetic. We are not claiming that any real road or neighbourhood is safe or unsafe. The project demonstrates how such a system could work if validated real data were available.

We also do not claim that we have already trained an accurate artificial-intelligence model. At this stage, we use a transparent weighted baseline. This gives us something testable and explainable that future machine-learning models can be compared against.”

### Slide 3: What 30% completion means

“Our 30% milestone is not simply a collection of screens. It proves the core technical workflow.

We completed the synthetic dataset, a spatial database, a FastAPI backend, an Android application, real OpenStreetMap routing, three route priorities, citizen reporting, government verification concepts, tests and documentation.

The application now genuinely queries a dataset-derived database. The score visible in the map is returned by the backend and is not hardcoded in the Android layout.”

### Handoff

“I will now hand over to Member 2, who will explain how the dataset, spatial database and route-scoring architecture work.”

## Member 2 — Dataset, backend and technical architecture (about 3 minutes)

### Dataset

“We created a privacy-safe synthetic dataset covering Mumbai and Navi Mumbai using OpenStreetMap road geometry as the public geographic foundation.

The complete analytics dataset contains 452,466 physical road segments. Each road is represented across four time periods: morning peak, midday, evening peak and night. This produces 1,809,864 segment-time records.

Every record contains ten synthetic factors: traffic congestion, crime risk, lighting quality, population density, road condition, pedestrian activity, emergency access, flood risk, isolation and public-transport access.

The full road-level dataset is stored as Parquet because it is compact and appropriate for future GeoPandas, analytics and machine-learning work.”

### Runtime database

“The full Parquet file is approximately 158 megabytes, so we do not load it into the mobile application or commit it to normal GitHub history.

For the current demonstration, we generated a 2.2-megabyte SQLite runtime database. It contains 1,627 geographic cells across four time periods, giving 6,508 rows. Each row contains all ten factors.

We added an SQLite RTree spatial index. This allows the backend to efficiently locate the dataset cell containing a latitude and longitude.”

### Backend and route scoring

“The backend uses FastAPI. It provides a health endpoint, dataset statistics, point-risk lookup and route-risk evaluation.

The route workflow is as follows. Nominatim converts the submitted source and destination names into coordinates. Valhalla returns real driving, walking or cycling routes from the OpenStreetMap road network. The Android application sends candidate route geometry to FastAPI. The backend samples up to 80 points, matches those points to SQLite cells, averages risk and factor exposure, and returns coverage and explanations.

Fastest uses travel time only. Balanced uses travel minutes plus 0.35 multiplied by risk exposure. Safest uses travel minutes plus risk exposure. These weights are configuration choices for the prototype, not scientifically validated constants.”

### Technology choices

“MapLibre renders the map. OpenFreeMap provides the current map style. Nominatim handles geocoding. Valhalla handles routing. FastAPI exposes the dataset service. SQLite and RTree provide the current portable spatial runtime. PostgreSQL with PostGIS is the planned street-level production database.”

### Handoff

“Member 3 will now demonstrate the working application, verification evidence and future scope.”

## Member 3 — Live application demonstration, verification and future scope (about 4 minutes)

### Entry and dashboard

“This is the redesigned Safe-Navi entry screen. It states the project scope, ten factors, four time windows and three route modes. It also visibly says that this is a synthetic-data prototype.

I will enter using the controlled citizen persona, which requires no private account or external Firebase configuration.”

Select **Explore as citizen**.

“The citizen dashboard shows a safety score, risk level, confidence and a human-readable reason. Risk and confidence are separate because a high score based on weak evidence should not be presented in the same way as a high-confidence result.”

### Map and route demonstration

Open **Map**.

“The map uses real OpenStreetMap geography. The floating area-score card is retrieved from our FastAPI dataset service.

For the route demonstration, I will use Vashi Railway Station as the source and CBD Belapur as the destination. The application supports driving, walking and cycling.”

Select **Drive**, **Balanced**, and press **Compare route options**.

“Valhalla has returned real road alternatives. Our backend has evaluated the route against the synthetic safety layer.

The result shows distance, estimated time, number of alternatives, synthetic exposure, time period, coverage and the strongest averaged factors. One hundred percent coverage means that every sampled route point matched a dataset cell. It does not mean one hundred percent safety.

Changing from Fastest to Balanced or Safest changes how strongly risk influences route ranking. The application never guarantees that a route is safe.”

### Citizen and government workflow

“A citizen can also submit a report. Reports begin as unverified observations and do not immediately receive the same authority as an official hazard.

The controlled government persona demonstrates reviewing, verifying, rejecting or marking a report as a duplicate. This separation is important for preventing unverified submissions from directly controlling route recommendations.”

### Testing

“The milestone currently passes 13 Android JVM tests, three FastAPI tests, Android lint, APK assembly, SQLite row-count checks and emulator route verification. We also tested the application with the local API running and confirmed that it clearly labels its controlled offline fallback when the API is unavailable.”

### Future scope

“Our next milestone is to import all street segments into PostgreSQL/PostGIS and intersect each route with exact roads rather than one-kilometre cells.

After that, we plan secure persistent reporting, server-issued government roles, audit history and saved routes. We will establish machine-learning baselines using spatial data splits, calibration and bias evaluation. The final application can then be rebuilt in Flutter for Android and iOS.

SOS, time-limited location sharing and the administrative analytics dashboard are later phases because they require stronger privacy, authorization and operational reliability.”

### Closing

“In conclusion, our current milestone proves that the idea is buildable: real road routes can be combined with an explainable, privacy-safe synthetic safety layer. The next challenge is not adding more screens; it is increasing spatial precision, validating the methodology and introducing secure real-world data governance. Thank you.”

---

# Version B: four teammates

## Member 1 — Problem statement, users and responsible scope (about 2 minutes)

“Good morning. Our project is Safe-Navi, a community-safety and intelligent-navigation prototype for Mumbai and Navi Mumbai.

Traditional navigation normally asks which route is fastest or shortest. Safe-Navi investigates a different question: if two valid routes are available, can we also explain the safety-related context of each option?

The intended users are citizens planning routes, government personnel reviewing reports and, in a later phase, administrators monitoring data quality and system operations.

Our current data is entirely synthetic. Therefore, the prototype demonstrates technical feasibility and not the actual safety of Mumbai roads. It is not an emergency service and never guarantees that a route is safe.

For the 30% milestone, we completed the dataset, database, backend, Android interface, OpenStreetMap routing, explainable scoring, role-based demonstration flows and tests.”

### Handoff

“Member 2 will explain how we constructed and operationalized the dataset.”

## Member 2 — Dataset and database engineering (about 2.5 minutes)

“The public geographic foundation is OpenStreetMap road geometry for Mumbai and Navi Mumbai. We generated privacy-safe synthetic values rather than collecting private victim or individual-location information.

The analytics dataset contains 452,466 physical road segments and 1,809,864 segment-time rows. The four periods are morning peak, midday, evening peak and night.

The ten factors are traffic congestion, crime risk, lighting quality, population density, road condition, pedestrian activity, emergency access, flood risk, isolation and public-transport access.

Parquet stores the full street-level table for future GeoPandas analytics, PostGIS import and machine-learning experiments. It is approximately 158 megabytes, so it is distributed separately rather than placed in normal Git history.

For the runnable demonstration, we generated a portable SQLite database containing the complete one-kilometre aggregation: 1,627 geographic cells multiplied by four periods, producing 6,508 rows. An RTree index finds the cell containing a requested coordinate.

This is an honest engineering trade-off. SQLite lets every teammate run the project without installing a database server. The limitation is that the current score is cell-level rather than an exact road-segment intersection.”

### Handoff

“Member 3 will explain the API, map stack and routing algorithm.”

## Member 3 — Backend, OpenStreetMap stack and algorithm (about 2.5 minutes)

“The backend is built with FastAPI. It exposes health, dataset statistics, point-risk and route-risk endpoints, and automatically provides interactive API documentation.

The mapping stack is provider-neutral. MapLibre renders the map. OpenFreeMap supplies the current visual style. Nominatim converts submitted place names into coordinates. Valhalla calculates real driving, walking and cycling routes from the OpenStreetMap network.

After Valhalla returns alternatives, the Android application sends route coordinates to FastAPI. The backend samples the geometry, performs indexed SQLite lookups for the current time period, averages synthetic exposure and reports how much of the route was covered.

Fastest ranks travel time only. Balanced uses travel minutes plus 0.35 times synthetic risk exposure. Safest uses travel minutes plus the full risk exposure value.

This is deliberately an explainable weighted baseline. We are not calling it trained AI. Future machine-learning models must outperform this baseline and must be evaluated using spatial splits, calibration and bias checks.”

### Handoff

“Member 4 will now demonstrate the application and present testing evidence and future scope.”

## Member 4 — Live demo, testing and roadmap (about 3 minutes)

“The redesigned entry screen clearly identifies this as a synthetic-data prototype. I will select Explore as citizen.”

Open the citizen dashboard.

“The dashboard presents safety score, risk level, confidence and explanation separately. I will now open the safety map.”

Enter or retain:

- Source: `Vashi Railway Station`
- Destination: `CBD Belapur`
- Mode: `Drive`
- Preference: `Balanced`

Press **Compare route options**.

“The displayed geometry is a real OpenStreetMap route returned by Valhalla. The safety layer is returned by our own FastAPI service.

The result shows route distance, time, alternatives, synthetic exposure, current time period, dataset coverage and the leading factors. Coverage describes the proportion of sampled points matched to dataset cells; it is not a safety percentage.

The citizen can submit a report, while the government demonstration can review and verify it. Reports and official hazards remain separate entities.”

“The current build passes 13 Android tests, three backend tests, Android lint and APK assembly. We also installed and exercised the complete workflow in an emulator.”

“Our next phase is exact street-level scoring with PostgreSQL/PostGIS, followed by persistent secure reports, verified roles and audit history. We will then evaluate ML baselines and rebuild the finalized client in Flutter for Android and iOS. SOS, time-limited location sharing and the full admin dashboard are intentionally later phases because they require production-grade privacy and reliability.”

### Closing

“Safe-Navi currently proves the architecture: public road routing, an explainable synthetic risk layer and a usable route-comparison experience can operate together. Our future work is focused on precision, validation, security and responsible deployment. Thank you.”

---

# Live-demo failure backup

If public routing or geocoding is temporarily unavailable, say:

“The external public OpenStreetMap service is currently unavailable. The application has correctly identified the missing service instead of presenting fabricated live data. We will show the already captured verified route state and demonstrate our local FastAPI endpoints, which remain available.”

Then show `deliverables/Safe-Navi-Redesign-Route.png` and open `http://127.0.0.1:8000/docs`.

If the local backend is not running, say:

“The application visibly labels its controlled offline fallback. We do not allow fallback values to masquerade as dataset API results. We can restore the dataset service by running `.\run_demo.ps1`.”

# Suggested question ownership

## Three-person team

- Member 1: problem, ethics, limitations and user roles
- Member 2: dataset, database, APIs and algorithm
- Member 3: Android demonstration, testing and roadmap

## Four-person team

- Member 1: problem, scope, users and responsible claims
- Member 2: dataset generation, factors, Parquet and SQLite
- Member 3: FastAPI, OSM stack, spatial lookup and route formulas
- Member 4: UI, live demonstration, tests and future phases
