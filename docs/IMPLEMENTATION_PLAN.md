# Safe-Navi Implementation Plan

## Source assessment

SafeNavi is a Java/XML Android application with Firebase Authentication and Realtime Database, Google Maps/Location, Cloudinary image upload, Gemini over OkHttp, and RecyclerView-based community features.

### Working components to reuse

- Email/password and Google authentication.
- Citizen, government employee, and administrator entry flows.
- Citizen report form with description, image, map, and device location.
- Firebase-backed report history and government complaint review views.
- Google Maps marker display.
- Community posts, comments, upvotes, bookmarks, filtering, and sharing.
- Gemini chatbot UI and network integration.
- Settings, splash, adapters, models, and XML resources.

### Gaps to correct

- Database URLs and calls are duplicated inside UI classes.
- Important writes bypass repositories and workflow services.
- Client-visible role fields are insufficient for production authorization.
- Government status updates overwrite data without complete audit history.
- Reports and verified hazards are not separate domain concepts.
- The map has report pins but no generic point, road, segment, building, or area hazard model.
- No centralized risk engine, duplicate detector, confidence model, or route safety evaluator exists.
- Several live features require external Firebase, Maps, Gemini, and Cloudinary configuration.

## Completed migration

The Android namespace and application ID are now `com.safenavi.app`. User-facing identity, labels, theme and documentation use Safe-Navi consistently, and the previous package/branding is absent from the repository. Existing screens remain intact while the new business rules live in model, data, risk and service packages.

## Target architecture

Java/XML Activities and Fragments call repositories and domain services. Repositories provide Firebase and synthetic-demo implementations. Domain services contain SafetyRiskEngine, HazardWorkflowService, DuplicateReportDetector, RouteSafetyEvaluator, and RoleAuthorizer. Pure Java rules remain independently unit-testable.

## Safety data model

- CitizenReport: unverified submission and review outcome.
- Hazard: structured official record created through authorization.
- HazardLocation: POINT, BUILDING, ROAD, ROAD_SEGMENT, or AREA.
- HazardCategory: stable extensible key and display name.
- HazardSeverity: LOW, MODERATE, HIGH, CRITICAL.
- HazardStatus: REPORTED, UNDER_REVIEW, VERIFIED, ACTIVE, MONITORING, RESOLVED, CLOSED, REJECTED, DUPLICATE.
- HazardHistory: append-only government audit event.
- SafetyScore: risk, safety score, level, confidence, and explanations.
- RouteSafetyResult: candidate route exposure and profile score.

Versioned Firebase paths will be safe_navi/users, reports, hazards, hazard_history, communities, notifications, and saved_locations. Legacy data remains readable during migration. Production deployment must enforce government operations in Firebase Security Rules and preferably trusted custom claims or a backend.

## Citizen workflow

Citizen authenticates, sees a calculated location summary, views official hazards separately from reports, submits a location-aware report, tracks its review state, and uses communities and the safety assistant. Synthetic and official information are visibly distinguished.

## Government workflow

An authorized employee reviews a report and verifies, rejects, marks duplicate, or retains it for review. Verification requires geometry type, category, severity, reason, status, and location. It creates an official hazard and audit entry. Later changes append history. Resolved hazards stop affecting immediate navigation but remain for analytics.

## Map strategy

Google Maps has been replaced by MapLibre Native and an OpenStreetMap-derived OpenFreeMap style. Point hazards render as severity-coloured markers plus translucent risk zones; polylines and polygons remain supported by the provider-neutral model. Explicit Nominatim lookup resolves source and destination while respecting its no-autocomplete and one-request-per-second public policy. Valhalla supplies real auto, pedestrian, and bicycle route alternatives. Public community services are for light demonstration only and must be replaced by hosted or self-hosted infrastructure before distribution.

## Deterministic risk engine

SafetyRiskEngine is the interface and RuleBasedSafetyRiskEngine the initial implementation. Risk is 0 to 100 and considers severity, government verification, distance decay, time decay, lifecycle status, recurrence, confirmations, and density. Configuration is centralized. Verified records carry much greater authority than raw reports. Risk and confidence remain separate, and every score provides human-readable contributions.

Future MLSafetyRiskEngine will implement the same interface. No fake ML model will be introduced.

## Safe routing

RouteSafetyEvaluator accepts real Valhalla candidate geometry and calculates hazard exposure along each route. Fastest ranks only travel time, Balanced combines travel and safety, and Safest gives hazard exposure greater weight. All alternatives remain real routes returned from the OpenStreetMap routing graph.

## Synthetic demo data

SyntheticDemoData provides labelled Mumbai/Navi Mumbai scenarios: high flooding on a road, a critical unsafe building under monitoring, moderate poor lighting on a road segment, pending reports, and full lifecycle history. It is isolated from production data and exercises citizen, government, map, risk, audit, and analytics flows.

## Future ML pipeline

Verified hazards retain structured geometry, category, severity, status, timestamps, resolution duration, confirmations, recurrence, source, verifier identifier, and reliability metadata. Unnecessary personal data is excluded. Later exports can create de-identified training rows.

## Required configuration

The project uses AndroidX, Material, Firebase, MapLibre Native, OpenFreeMap, Nominatim, Valhalla, device location, OkHttp, Glide, CameraX, and Cloudinary integration. Map display and demo routing need no Google key. Optional live features require GEMINI_API_KEY, CLOUD_NAME, UPLOAD_PRESET, FIREBASE_DATABASE_URL, plus app/google-services.json. NOMINATIM_BASE_URL and VALHALLA_BASE_URL can point to hosted or self-hosted production services.

## Testing

- Pure JVM tests for severity, distance, time decay, resolved hazards, multiple hazards, history, confidence, route profiles, duplicates, and workflow transitions.
- Permission tests proving citizens cannot perform government actions.
- Workflow tests for verify, reject, duplicate, update, resolve, and close.
- Android navigation and state tests when an emulator is configured.
- Regular assembleDebug and testDebugUnitTest builds.

## Implementation sequence

1. Copy SafeNavi into Safe-Navi without touching SafeNavi history.
2. Rebrand the product.
3. Add models, repositories, risk engine, workflow, authorization, duplicate detection, and synthetic data.
4. Establish citizen Home, Map, Report, Community, and Profile navigation.
5. Build the map safety experience and government review/hazard lifecycle.
6. Ground the assistant in available Safe-Navi data.
7. Add tests, Firebase rules template, README, and build instructions.
8. Build and fix compilation failures.

## Production boundary

The repository can provide a functional synthetic workflow and Firebase client integration. Production government authorization cannot rely on hidden Android controls or writable role fields; it requires deployed Firebase Security Rules/custom claims or a trusted backend controlled by the owner.
