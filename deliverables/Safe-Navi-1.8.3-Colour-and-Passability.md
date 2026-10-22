# Safe Navi 1.8.3 — route colours and flood passability

Prepared 22 October 2026. Android versionCode 25, `1.8.3-flood-passability`.

## What changes

Fastest is cobalt blue (`#2563EB`), Balanced violet (`#7C3AED`) and Safest deep teal
(`#00796B`). Opaque white outlines separate routes from map streets. The selected
line is thicker and drawn above the others. Cards, legend and navigation share the
same palette. Identical recommended roads are still drawn once: the selected road
uses the active preference's colour, while unselected shared roads use neutral slate;
no coordinates are moved to manufacture parallel routes.

Official flood passability is now stored separately from severity and road status:

| Official state | Routing effect on the affected road |
| --- | --- |
| Not confirmed / legacy record without a decision | Excluded for every preference |
| Inspected: passable with caution | Allowed with the existing hazard-risk penalty |
| Impassable | Excluded for every preference |
| Road status CLOSED, even if marked passable | Excluded; the closure takes priority |
| Flood road status UNSAFE, even if marked passable | Excluded |

Exclusions use the actual road geometry and the hazard's affected geometry/buffer,
not an entire city. Default point-buffer radius is 60 m for a restricted flood. Roads
outside that footprint are not automatically closed. Accurate pins/affected-road
geometry and official evidence still matter. No flood depth is inferred by ML.

Map labels distinguish blocked flooding, unconfirmed waterlogging and explicitly
passable waterlogging. Restricted map pins are red even if the old severity is LOW.
Officials can set passability while reviewing a citizen report, publishing a flood
news item, or updating an existing map hazard. The latter includes an explicit road
status selector. Citizens/admins cannot update official hazards through the API.
Changes are audited. Existing records default to UNKNOWN, never silently PASSABLE.
Cached offline routing enforces the same restrictions and ignores expired/resolved
records. If no evaluated road remains usable, the app must show no confirmed
alternative rather than bypass the restriction through offline fallback.

## Presentation scenario and honest route counts

The existing, expiring Sanpada sample is used for cloud verification. Its title is
natural, but its description/audit notes retain simulation provenance. No actual
inspection, current real flood or live crime observation is claimed.

Earlier 20/25/28-minute screenshots were captured before the passability policy.
The 25-minute road comes within roughly 20 m of this sample's pin. When that sample
is unconfirmed/impassable, it must no longer be recommended. Fewer distinct roads
is the correct result: three preference buttons do not guarantee three usable roads.
Only an explicit official passable-with-caution scenario can restore that candidate;
never mark it passable merely to force a three-road demonstration.

## Official navigation

1. Sign in as the dedicated Safety Official; open Map and Crime & alerts.
2. Open the Sanpada waterlogging sample, then Update official hazard lifecycle.
3. Choose Keep active. Review the passability selector and road status separately.
4. Add an honest audit reason. For presentation changes, explicitly identify the
   simulated scenario; do not claim a field inspection occurred.
5. Save the audited update, refresh routes on the citizen phone, and inspect the
   changed map label and eligible roads. CLOSED always overrides PASSABLE.

The other route cases, pending reports, accounts, conversations and evidence were
not reset. Prepared sample hazards retain their 25 October expiry.

## Installation and verification

Install `Safe-Navi-1.8.3-Colour-and-Passability.apk` over the existing app without
uninstalling. A new APK is required for the colours, labels, official controls and
offline policy. The cloud backend policy is deployed separately on the existing
Render service; PostgreSQL migration adds the new field without dropping records.

- Backend regression suite: 90 passed, 3 environment-dependent skips.
- Android JVM suite: 82 passed, zero failures/errors.
- Cloud policy version: `explicit_passable_or_excluded_v1`.
- Render deployment: tested commit `fc0d26b`, deployed successfully through the dashboard.
- Cloud verification: five phases × three preferences passed. Initial UNKNOWN,
  explicit PASSABLE/CAUTION, PASSABLE/CLOSED, UNKNOWN/CAUTION and final IMPASSABLE
  were checked on the existing sample. The final state is IMPASSABLE, expiry unchanged.
- With that sample restricted, Vashi station → SIES GST has two recommended roads:
  Fastest 4.96 km / 20.4 min; Balanced and Safest share 6.28 km / 27.6 min. The old
  25-minute candidate is excluded. The explicit PASSABLE/CAUTION test restored the
  third eligible recommendation and retained its hazard penalty.
- Android UI: the final five-check rerun passed in 113.499 seconds, including real
  cloud routing, navigation, outline/source cleanup, shared roads and the actual
  official selector. The selected shared road matches the active preference colour.
- APK SHA-256: `9514B82AE8CD2B723195A4AFA26BC6E3FC8913D03737F833C2750272CAC39AD3`.
  The temporary emulator credential handoff was removed after the checks.

Raw cloud test log: `tmp/flood-passability-cloud-tests.log`. Backend regression log:
`tmp/flood-backend-final-tests.log`. Generated detailed cloud responses are private
and excluded from Git. No existing accounts or phone sessions were reset.

Model coefficients, preference weights, provider road geometry and ETAs are unchanged.
This feature adds authoritative eligibility constraints before ML/preference ranking.
