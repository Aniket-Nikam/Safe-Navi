# Map 1.3

Version code 13. Install as an update; no uninstall or account/data reset is required.

## Interaction

- Type a place and press Go / keyboard Search to choose the highest-ranked available match. Tapping a specific suggestion still overrides it. Ranking favours name relevance, then proximity; campus parking/canteen/toilet results are penalised unless explicitly requested. Ambiguous names are not guaranteed to represent the user's intent; locality-qualified queries remain supported.
- Closing search cancels it. Typing alone never silently commits a destination. My location is handled as a location action rather than a geocoding query.
- The default map has one compact destination bar, a small route control and SOS. The endpoint editor opens with the route icon. The Routes button opens Fastest/Balanced/Safest together; explanations and sharing/navigation remain one further action away.
- Area estimates, connection controls, crime reports, filters and saved places remain in Map tools. Critical offline status remains visible.
- Route risks is an opt-in overlay. Tapping an exposure section or marker opens details in a dismissible bottom sheet, not a permanent map-blocking card.

## Explanation coverage

- Previous display logic picked only three elevated area estimates. This cap was not a full-road assessment, and the selected points could cluster near the destination.
- The new display partitions the entire original road geometry into approximately 500 m intervals, up to 128 sections per candidate. It preserves road bends and marks sections without area-cell data as unknown. Lower estimated exposure is not labelled safe.
- Predictions are cached per matched area cell within a route. Missing data does not trigger fabricated inference. The legend states the dataset is synthetic area-cell data and gives length-weighted assessed/unassessed coverage.
- Representative elevated markers are selected across journey eighths (at most eight estimates), rather than only from the highest-scoring cluster. Continuous section colouring remains available even when labels collide.
- Government-verified hazards remain separate records. Existing full-geometry closure exclusions, route inference representation, weighting and physical ETA/risk values are unchanged.
- This is a display/interaction improvement, not an expansion of dataset coverage or a guarantee of road safety. The route-wide score and local section estimates serve different purposes and need not be identical.

## Deployment

The backend application update was deployed successfully on Render from commit `614e2f3`. The same existing PostgreSQL/Supabase configuration is retained. Cloud account provisioning, migration and other acceptance limitations documented in CLOUD_DEPLOYMENT_STATUS.md are not resolved by this map update.

## Verification, 17 October 2026

- Android build and 39 JVM tests passed.
- Three API-36.1 emulator checks passed: collapsed map/on-demand comparison, locality search rows, and Go selecting the campus rather than its parking lot without tapping a result.
- Real Photon -> Valhalla -> deployed trained-ML service -> MapLibre checks passed twice, in light and dark mode, using Nahur Railway Station to the SIES College result in Nerul. The backend returned nonempty whole-route sections. Normal and opt-in exposure-layer screenshots were inspected.
- Backend: 60 passed, 3 PostgreSQL-environment-specific tests skipped. Closure geometry and existing workflow regression tests passed. The removed three-marker helper tests were replaced by coverage, continuity, unknown-data and representative-marker tests.
- APK `deliverables/Safe-Navi-Map-1.3.apk`, SHA-256 `40A1A7C0B82BE900F195F437E797FCDB509C4DB2B92922CDF5DDDF42857B5BFB`.
- This is not a claim of zero bugs or comprehensive real-phone acceptance for unrelated features.
