# Production-boundary completion status

The presentation build is functional, but “production complete” has a stricter meaning.
This register prevents local implementation, external infrastructure and scientific
validation from being incorrectly treated as the same kind of work.

| Boundary | Current factual status | Completion requirement |
|---|---|---|
| Groq assistant | Complete and live locally using a Git-ignored server-side key and a supported model | Move key to the deployment host's secret manager and rotate any key shared in chat |
| RSS news intelligence | Complete and dynamic with three HTTPS Mumbai/Navi Mumbai feeds, persistent admin-selected 12/24-hour detection and immediate checks; review-only policy enforced | Confirm publisher terms and maintain an approved feed list |
| Two-phone communication | Application/API complete; LAN build targets the current PC | One administrator-level Private/LocalSubnet firewall rule, or deploy the API over HTTPS |
| Online maps/geocoding/routing | Integrated through external providers | Production quotas/SLA or self-hosted services |
| Product database | SQLite is complete for one-host presentation use | Managed PostgreSQL/PostGIS, backups and migration for multi-instance deployment |
| Exact road-level scoring | Bundled app uses 1 km area/time cells | Import the full Parquet geometry into PostGIS and validate segment matching |
| Real-world ML validity | Not complete; supplied targets are synthetic | Authorised observational labels, temporal/geographic evaluation, fairness and calibration review |
| Offline coverage | Complete for the bundled Navi Mumbai graph and user-downloaded tile region | Build and license additional regional graphs/tiles as required |
| Evidence protection | Authenticated, app-private, no-backup, hash-checked | Formal threat model and optional Keystore-backed encryption for the target devices |
| Video evidence | Secure upload/download exists | Add a policy-approved in-app player or case-system handoff if required |
| Messaging | Persistent community and case messaging is complete | Add presence/typing/push delivery only if real-time social chat becomes a requirement |
| Localisation | English/Hindi/Marathi primary resources exist | Translate and review every dynamic operational/server message with human language QA |
| Administrator role | Protected admin role shares the government operations console | Build user/role/region administration if required by the deploying authority |
| Release hardening | Debug presentation APK is verified | Signed release, minification rules, Play policy review, device matrix and penetration testing |
| Reliability | Automated backend/JVM/device suites pass | Hosted monitoring, alerting, backups, disaster recovery and load testing |
