# Safe-Navi — Tomorrow's Presentation Package

## What to carry

1. `Safe-Navi-30-Percent-Progress-Review.pptx` — the 11-slide presentation with speaker notes.
2. `Safe-Navi-30-Percent-Demo.apk` — install this on an Android 7.0+ phone or emulator.
3. `Safe-Navi-30-Percent-Source.zip` — clean source snapshot for submission or backup.
4. `../docs/PRESENTATION_SCRIPT.md` — the complete 8–10 minute speaking script.
5. `../docs/PROFESSOR_QA.md` — likely questions and concise answers.
6. `../docs/30_PERCENT_PROGRESS_REPORT.md` — detailed written explanation.

## Tonight

- Install the APK on the device that will be used tomorrow.
- Open **Citizen demo** and confirm that the map loads on the college/mobile network.
- Try one route in advance and record the source/destination that works reliably.
- Open **Government demo** and practise one verification action.
- Keep the PPTX, APK, and ZIP on both a USB drive and cloud storage.
- Keep phone hotspot access available because the live basemap/geocoder/router need internet.

## Recommended five-minute live demo

1. Open Citizen demo and point out the **synthetic demo** label.
2. Enter a source and destination, then select driving, walking, or cycling.
3. Switch between Fastest, Balanced, and Safest; explain that all geometry comes from the OpenStreetMap routing graph.
4. Tap a location/hazard and show risk, safety score, confidence, and reasons.
5. Submit one synthetic citizen report.
6. Switch to Government demo and verify, monitor, or resolve it.

## If the internet is unreliable

Do not spend the presentation waiting for a public service. Continue with the architecture, tests, and workflow slides. Say: “Public OpenStreetMap community services are used for light academic testing. Production would use hosted or self-hosted map infrastructure.” The APK and source remain valid; live address lookup and routing require internet.

## One-sentence status

“We have completed the first 30% of the full roadmap: a runnable Android feasibility prototype that combines real OpenStreetMap road routes, a verified synthetic-hazard lifecycle, and an explainable fastest/balanced/safest route comparison; secure persistence, city-scale data generation, validated ML, and production deployment are the next phases.”
