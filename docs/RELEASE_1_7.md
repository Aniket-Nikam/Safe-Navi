# Safe-Navi 1.7: risk-first routing and verified news

- Fastest minimises travel minutes, using risk to break equal-time ties.
- Balanced minimises minutes + 0.35 * assessed exposure.
- Safest minimises assessed exposure, using travel time to break equal-risk ties.
- All preferences use the same eligible candidate roads and physical risk/ETA values.
- Shared winners are drawn once in slate blue. Distinct winners use red, yellow and green.
- Authoritative closures and critical unsafe buffers are excluded before ranking.
- Real RSS stories remain unverified until an official confirms the exact affected road.
- News review displays pending, live-risk, live-restriction and inactive routing states.
- Fixed duplicate SQL column names hiding published news hazard links; older links remain supported.
- Publication confirmation opens the saved hazard on the map.

## Verified demonstration

Isolated local database, fictional clearly labelled news incident, real Valhalla roads,
and the actual bundled trained model. No real public news record was falsely verified.

Vashi Railway Station (19.0632, 72.9988) to SIES GST, Nerul (19.042644, 73.023428),
driving, morning_peak:

| Preference | Minutes | Assessed exposure after demo publication |
| --- | ---: | ---: |
| Fastest | 21.41 | 89.71 |
| Balanced | 25.63 | 61.88 |
| Safest | 28.63 | 58.98 |

Fastest-road exposure before publication was 58.84. The news test asserts that exposure
increases and Safest chooses a different candidate. These results are a repeatable
test of the pipeline, not a statement about real crime on those roads.

## Scope and limits

The ML artifact is the supplied linear regression model with a synthetic risk target.
News classification remains keyword-based, not a trained news model.
Risk estimates are not safety guarantees. Candidates may legitimately share a winner.
No data does not mean safe; baseline coverage is Mumbai and Navi Mumbai.
Offline routing uses a cached graph and risk snapshot, not live cloud ML or news.

## Checks

- Backend full regression: 69 passed, 3 skipped (optional external-database cases).
- Public policy and route regression: 9 passed.
- Android unit tests and debug APK build pass.
- Real-road map and official-news emulator tests pass.
- New video uses actual app captures with isolated demonstration accounts and incidents.
