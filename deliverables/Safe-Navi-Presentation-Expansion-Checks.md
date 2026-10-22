# Presentation expansion — verification log

Date: 22 October 2026. Target: the existing HTTPS Render API with PostgreSQL/Supabase,
not localhost. Scope: additional owner-authorized, non-production presentation scenarios.
This is not an exhaustive retest of every application feature or proof of real incidents.

## Cloud results

| Check | Result |
| --- | --- |
| Persistent cloud backend and trained model provider | PASS: PostgreSQL and `trained_ml` |
| Ten additional citizen submissions and official decisions | PASS: all VERIFIED, each linked to its backend hazard |
| Existing published records preserved | PASS |
| Active map records | PASS: 20 sample-report hazards + 2 source-linked news replays = 22 |
| New hazards in point-risk lookup | PASS: all 10 IDs present with nonzero verified-hazard contribution |
| Powai and Taloja live-review cases | PASS: both REPORTED, no linked hazards; not published to the map |
| Existing four route sets, morning and midday | PASS: eight cloud evaluations, model provider retained |
| Vashi Railway Station → SIES GST, Nerul | PASS: three distinct recommendations in both periods |
| Fresh provider roads for Vashi → SIES | PASS: three distinct cloud recommendations |
| Ghatkopar → Chembur sample closure | PASS: intersecting road excluded, other roads remain available |
| Mumbai Central → Worli Sea Face | PASS: added alert IDs enter real-road exposure scoring |
| Airoli → Ghansoli | PASS: added alert IDs enter real-road exposure scoring |

The last two checks establish scoring influence, not a guarantee of a different
winning road on every query. No model parameters, scores, road geometries or ETAs
were overridden to produce a result. The new locations are at least 1.5 km away
from all three captured Vashi–SIES roads.

## Android evidence

The first two cloud-connected Android tests passed their data/rendering assertions
in 89.85 seconds. Manual image inspection then caught an emulator **System UI isn't
responding** dialog covering the screenshots, so those images were rejected. This
was a system-window problem; the underlying app assertions did not detect it.

The screenshot helper now checks that the active accessibility window belongs to
Safe Navi. Another system/app window therefore fails evidence capture. A clean
rerun initially failed that new guard and also hit transient emulator DNS failure
on the second test (37.239 seconds). The boot-time system dialog was closed on the
owned emulator, DNS resolution was checked, and the device was allowed to settle.

**Final run: PASS — 2 tests, 39.287 seconds**, with the overlay guard enabled.
All four replacement images were then visually inspected: populated 22-alert map,
populated alerts list, three-road comparison and selected-route actions. No loading,
blank or system-warning screen was accepted. The original app APK's SHA-256 remains
`AD4D641B33D1903C3B80CB8E256D05E031FBBB5B0EA9DEA631F21B99DC3FF84B`.

The test cleanup now logs out only after a new test session was actually created;
a failed setup cannot log out the previously saved emulator session. Temporary
credential handoffs were removed from the emulator and host, its notification setting
was restored, and the emulator was stopped after verification.

Local raw logs: `tmp/presentation-expansion-api-tests.log`,
`tmp/presentation-expansion-android-tests.log` (initial run),
`tmp/presentation-expansion-android-final-tests.log` (rejected retry), and
`tmp/presentation-expansion-android-clean-tests.log` (accepted final run).

## Data and operational notes

- Five additional Mumbai areas: Andheri, Bandra, Dadar, Mumbai Central, Worli.
- Five additional Navi Mumbai areas: Airoli, Ghansoli, Kopar Khairane, Kharghar, Seawoods.
- A geocoder mismatch (Dadar returned for the longer Mumbai Central query) was
  corrected before publishing. Duplicate area pins are rejected by the helper.
- Natural titles do not imply observed incidents. Descriptions and official reasons
  retain explicit simulation provenance. No fake photographs were attached.
- The 20 prepared report hazards expire on 25 October, approximately 9:22 a.m. IST;
  the two news replays expire a few minutes later. Pending reports are not auto-expired.
- The current citizen-report publication screen has no expiry control. A pending
  case manually published during the presentation needs separate cleanup afterward.
- Test logins revoke only their own sessions; phone sessions are not reset.
- This update changes presentation data and verification tools only. The installed
  1.8.1 application does not need replacement; reopen Map and refresh routes.
- Minor existing UI copy remains: the route sheet says "Tap a red pin" although
  alert pins use severity colors. Tap the relevant colored alert pin or alerts-list
  entry. This data-only update does not change that app wording.
- Credentials, tokens, generated private state and temporary emulator handoffs
  are excluded from Git. The original unrelated rehearsal case remains unchanged.

Reproducible API helper: `tools/expand_private_presentation.py --verify-only`.
Its default mode is preflight only. External sample writes require both `--apply`
and `--private-presentation` and the owner's existing private presentation handoff.
