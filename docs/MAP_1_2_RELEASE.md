# Map 1.2 (version code 12)

- Fresh installations default to https://safe-navi-api.onrender.com. `-PriskApiBaseUrl=...` explicitly selects a different build default; an ignored laptop IP in local.properties no longer ships accidentally.
- Existing saved server addresses are preserved, not silently migrated. On Map, tap Safety connection / Connect safety, choose Use cloud, then Test and save. Switching servers signs out; pending offline reports must first be uploaded to their original server. Laptop accounts and records do not automatically migrate to the cloud.
- Place selection uses a dedicated searchable bottom sheet, short names with locality rows, debounced/cancellable Photon suggestions, and explicit Nominatim Search. Suggestions are restricted to the Mumbai metropolitan region; incomplete OSM coverage remains possible. My location and Choose on map are available.
- Fastest, Balanced and Safest appear simultaneously. They rank one evaluated candidate set; identical physical routes retain identical travel time and risk. No artificial differences are introduced.
- Compact map controls keep route details/actions in a separate sheet. Endpoints are labelled A and B.
- Selected routes can display located explanations from the backend: government-verified hazards and up to three spatially separated elevated area estimates. Estimates are labelled synthetic-baseline/model estimates, not confirmed incidents or crime prevalence. Tap a pin for its reason. Existing full-geometry closure exclusions remain unchanged.
- Offline tile-download callbacks no longer target a destroyed settings view. Downloaded tiles are not a replacement for online search, live ML evaluation, or new safety updates.

Free cloud hosting can sleep. Test and save allows time to wake it; retry the map after a successful health check. Public search/routing providers also have availability and usage limits. This update does not provision cloud officials/admins, migrate local product data, or configure the external news scheduler.
