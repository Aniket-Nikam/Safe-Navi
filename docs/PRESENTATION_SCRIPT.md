# Safe-Navi — Presentation Script (8–10 minutes)

## Opening — 40 seconds

“Good morning. Our project is Safe-Navi, a community-safety and intelligent-navigation platform for Mumbai and Navi Mumbai. A normal navigation system mainly recommends the fastest route. Our question is: if verified information about flooding, poor lighting, unsafe structures, or similar hazards is available, can a navigation system also explain and recommend a safer alternative? Today we are presenting the first 30% of the complete roadmap: a runnable technical prototype using privacy-safe synthetic safety data and real OpenStreetMap road routes.”

## Problem and solution — 50 seconds

“The problem has two parts. First, the fastest route may not match a traveller's safety preference. Second, citizen safety reports may be unverified and disconnected from navigation. Our proposed loop is citizen report, government verification, structured hazard, explainable risk calculation, and route comparison. We keep raw citizen reports separate from verified hazards so that an unverified claim does not automatically become an official map warning.”

## Why this is the 30% milestone — 55 seconds

“We are measuring progress against the full system: secure backend integration, trained and evaluated ML, production map infrastructure, admin operations, privacy controls, and Android/iOS delivery. We have completed a city-scale synthetic dataset with 452,466 physical road segments and 1,809,864 rows across four time periods, plus the runnable feasibility layer and core interaction flow. The dataset and app are currently separate: the mobile demo uses controlled synthetic scenarios, while serving Parquet-derived road risk to the app is the next integration step.”

## Dataset milestone — 50 seconds

“Our OpenStreetMap-grounded dataset covers every eligible mapped road segment inside the documented Mumbai and Navi Mumbai polygons. It contains exactly ten synthetic factors: traffic, crime risk, lighting, population density, road condition, pedestrian activity, emergency access, flood risk, isolation, and public-transport access. Automated checks passed for nulls, value ranges, duplicate keys, geometry, time-period completeness, spatial splits, and label balance. These are simulated factors—not observed safety measurements—so the dataset demonstrates a complete training and evaluation pipeline, not real neighbourhood safety.”

## What works now — 70 seconds

“The current Android prototype has citizen and government demo modes. A citizen can inspect a safety score, see controlled synthetic hazards, enter source and destination, choose driving, walking, or cycling, and compare fastest, balanced, and safest routes. These routes are generated from the real OpenStreetMap road graph through Valhalla; the app does not draw artificial straight lines. A citizen can also submit a synthetic report. In the government view, that report can be verified and published, retained for monitoring, or resolved. The city-scale Parquet data is not yet queried by this screen, and we state that openly.”

## Technology and rationale — 65 seconds

“We retained the existing Java and XML Android foundation because it let us validate the concept quickly without a risky rewrite. MapLibre renders the map, OpenStreetMap and OpenFreeMap provide geographic context, Nominatim resolves submitted addresses, and Valhalla calculates multimodal routes. OkHttp handles network requests. Firebase components remain as a foundation for authentication and persistence, but production government authority will require server-enforced roles. JUnit tests isolate the route, risk, and workflow logic.”

## Risk model — 70 seconds

“We intentionally did not pretend that a machine-learning model exists. There is no legally obtained, labelled real safety dataset yet. Instead, the current prototype uses a deterministic and explainable weighted model. A hazard contribution considers severity, verification status, distance from the route, age, lifecycle status, recurrence, confirmations, density, category, and reliability. Contributions are bounded and combined into a zero-to-one-hundred risk value. Safety is one hundred minus risk, while confidence is calculated separately. Therefore, missing data means low confidence, not guaranteed safety.”

## Demonstration — 2 to 3 minutes

“I will enter a source and destination and select one transport mode. The app obtains alternative road routes. In Fastest mode it prioritizes travel time. Balanced combines time and exposure. Safest gives more weight to avoiding active verified hazards. I will select a point to show the local score and explanation. Next I will submit a synthetic citizen report. I will switch to the government demo and verify or monitor it. When a hazard is resolved, it remains in the audit history but no longer contributes to immediate navigation risk.”

If the internet is slow, say: “The map-routing layer uses public community services, so I also have the built APK and verified test/build results. Production deployment would use a contracted or self-hosted service.”

## Testing — 35 seconds

“Eleven JVM tests pass. They check risk behaviour and confidence, citizen versus government permissions, verification and resolution history, duplicate matching, profile-based route ranking, Nominatim parsing, Valhalla parsing, and polyline decoding. Android lint, unit tests, and debug APK assembly also pass.”

## Limitations — 40 seconds

“All safety records shown today are synthetic examples. They must not be interpreted as crime or safety claims about real neighbourhoods. The demo repository is temporary, production roles are not yet deployed, and public geocoding and routing endpoints are only for light testing. Safe-Navi is not an emergency service and never guarantees safety.”

## Roadmap — 55 seconds

“The next phase is secure persistence, backend-enforced roles, a FastAPI or spatial service that exposes the completed Parquet-derived road risks, moderation, alerts, and device-level tests. After that, we will train baseline models using the supplied geographic split and compare them with the transparent rule engine for calibration, bias, and false reassurance. The final phase includes self-hosted map infrastructure, accessibility, privacy review, the government dashboard, and then Flutter for Android and iOS if the validated prototype supports that investment.”

## Closing — 25 seconds

“The result of the first 30% is proof that the end-to-end idea is buildable: real road routing can be combined with a verified-hazard lifecycle and an explainable safety objective. For the next review, we want to present persistent workflows, a documented synthetic-data generator, and a scientifically defensible baseline evaluation. We welcome your feedback on the risk factors, validation design, and project scope.”
