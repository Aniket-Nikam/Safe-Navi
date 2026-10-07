# Safe-Navi Product Roadmap

## Product promise

Safe-Navi helps a person choose a route using travel time, the trained road-risk
dataset, and current verified civic or crime hazards. It must explain why a route
was recommended and must never treat an unverified report or scraped article as
an official safety fact.

## Authoritative workflow

1. A citizen signs in and submits an issue with category, description, location,
   optional evidence, and consented metadata.
2. The backend stores the report as `REPORTED`. It is visible to the reporter and
   the government review queue, but it does not yet change public routing.
3. A government employee triages the report as `UNDER_REVIEW`, then verifies,
   rejects, or marks it as a duplicate. Every action is appended to an audit log.
4. Verification creates a structured hazard with severity, geometry, road status,
   reason, validity window, and accountable reviewer.
5. Active or monitored hazards appear on the citizen map and are combined with
   the trained dataset model during point and route scoring.
6. Government updates such as `SAFE`, `UNSAFE`, `WORK_REQUIRED`, `RESOLVED`, or
   `CLOSED` are persisted, audited, and reflected on the map immediately.
7. Citizens can track their report and receive a notification when its state
   changes. Resolved information remains available for analytics but no longer
   penalizes immediate routing.

## Risk composition

The routing API owns a stable `RiskProvider` boundary:

- `TrainedMLRiskProvider`: bundled, validated linear model selected by held-out and street-grouped CV performance.
- `DatasetRiskProvider`: deterministic fallback when the model artifact cannot be validated.
- `VerifiedHazardProvider`: current government-verified, time-bounded hazards.
- `NewsSignalProvider`: geocoded and deduplicated public-source signals. News is
  labelled unverified until a reviewer promotes it to a hazard.
- `ExternalMLRiskProvider`: optional HTTPS override implementing the validated HTTP contract; failures return to the bundled model.

The API returns the dataset contribution, verified-hazard contribution, combined
score, confidence, coverage, and human-readable reasons. The client renders these
facts; it does not secretly recalculate authority-sensitive decisions.

## Map-first experience

### Citizen map

- Current position, destination search, and route alternatives.
- Fastest, balanced, and safest recommendations for driving, walking, and cycling.
- Filterable hazard layers for civic, crime, flood, lighting, road, and transport issues.
- Tap a hazard for status, evidence summary, source, last update, and government reason.
- Long-press to start a report at the selected location.
- Live route recalculation when an active hazard changes.
- Clear distinction between dataset estimates, verified hazards, and pending reports.

### Government operations map

- Spatial review queue, clustering, duplicate suggestions, and evidence panel.
- Draw/edit point, road corridor, or affected-area geometry from the operations map.
- Verify, reject, merge duplicate, request information, change road status, and resolve.
- Bulk filters by ward, category, severity, age, SLA, and assignment.
- Immutable audit timeline and operational metrics.

## News and external signals

Use permitted RSS feeds and publisher APIs before HTML scraping. Each item stores
publisher, URL, publication time, extracted location, category, confidence, content
hash, and licensing metadata. Deduplication and geocoding run before review. A news
signal can raise reviewer priority but cannot directly mark a road unsafe.

## Delivery sequence

### Phase 1 — persistent product spine

- Local/server accounts and bearer sessions.
- Persistent reports, hazards, government actions, and audit history.
- Public map-hazard endpoint.
- Route and point risk composed from dataset plus verified hazards.
- Automated API tests and administrator bootstrap command.

### Phase 2 — Android vertical slice

- Replace demo sessions and Firebase-direct writes with the authenticated API.
- Real citizen report form using device/map-selected location and evidence upload.
- Real government queue and map review tools.
- WebSocket refresh, background system alerts, persisted report-status notifications, and resilient polling.
- Remove legacy/demo-only screens after feature parity.

### Phase 3 — operational services

- PostgreSQL/PostGIS, object storage, background jobs, notification delivery,
  rate limiting, observability, backups, and CI/CD.
- Hosted/self-hosted tiles, geocoding, and routing under appropriate service terms.
- Source governance and deployment operations for the implemented scheduled news ingestion and review UI.

### Phase 4 — quality and research

- Espresso journeys, accessibility, low-connectivity behaviour, threat modelling,
  privacy/retention controls, load testing, and signed releases.
- The teammate model, model comparison and street-grouped cross-validation are integrated. Before public deployment, extend evaluation with strict geographic/time splits, calibration, bias checks, ablations and authorized real-world outcomes.

## Definition of done

The product is complete only when a fresh installation can create an account,
submit and track a real report, review it through an authorized government account,
show the resulting hazard on another device, change route ranking accordingly,
survive process/server restarts, explain every risk contribution, and pass automated
API, unit, UI, security, accessibility, backup/restore, and release checks.
