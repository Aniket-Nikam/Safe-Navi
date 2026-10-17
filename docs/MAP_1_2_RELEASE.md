# Map 1.2 (version code 12)

- Fresh installations default to https://safe-navi-api.onrender.com. `-PriskApiBaseUrl=...` explicitly selects a different build default; an ignored laptop IP in local.properties no longer ships accidentally.
- Existing saved server addresses are preserved, not silently migrated. On Map, tap Safety connection / Connect safety, choose Use cloud, then Test and save. Switching servers signs out; pending offline reports must first be uploaded to their original server. Laptop accounts and records do not automatically migrate to the cloud.
- Place selection uses a dedicated searchable bottom sheet, short names with locality rows, debounced/cancellable Photon suggestions, and explicit Nominatim Search. Suggestions are restricted to the Mumbai metropolitan region; incomplete OSM coverage remains possible. My location and Choose on map are available.
- Fastest, Balanced and Safest appear simultaneously. They rank one evaluated candidate set; identical physical routes retain identical travel time and risk. No artificial differences are introduced.
- Compact map controls keep route details/actions in a separate sheet. Endpoints are labelled A and B.
- Selected routes can display located explanations from the backend: government-verified hazards and up to three spatially separated elevated area estimates. Estimates are labelled synthetic-baseline/model estimates, not confirmed incidents or crime prevalence. Tap a pin for its reason. Existing full-geometry closure exclusions remain unchanged.
- Offline tile-download callbacks no longer target a destroyed settings view. Downloaded tiles are not a replacement for online search, live ML evaluation, or new safety updates.

Free cloud hosting can sleep. Test and save allows time to wake it; retry the map after a successful health check. Public search/routing providers also have availability and usage limits. This update does not provision cloud officials/admins, migrate local product data, or configure the external news scheduler.

## Verification on 17 October 2026

- Android build and 36 JVM unit tests passed.
- Two API-36.1 emulator layout tests passed: all three cards, A/B layers and selected-route risk marker layer, visible map space, and short-name/locality search rows.
- Opt-in read-only Android live smoke test passed: Photon returned SIES College in Nerul, Valhalla returned actual road candidates, and the deployed cloud backend evaluated them with `trained_ml`; the real map rendered A/B endpoints and comparison cards. For this tested morning route, the same physical road won all three preferences. That is not evidence of three different recommendations.
- Backend suite: 58 passed, 3 skipped (PostgreSQL-specific checks require an isolated PostgreSQL test URL). Existing closure-geometry tests passed alongside new risk-marker tests.
- Render deployed application commit `d2358d4` successfully. `/health` reports PostgreSQL, Supabase evidence and trained ML.
- APK: `deliverables/Safe-Navi-Map-1.2.apk`, version code 12. SHA-256: `51C54F326937B57309C46B373447789B4BD29A04553A5869B10B9BBE20E4CE25`.

These checks do not establish bug-free operation or full two-phone role/evidence acceptance on the cloud deployment. The first Espresso-based search assertion hit an old test-library/API-36 incompatibility; the final tests use direct view assertions and pass.
