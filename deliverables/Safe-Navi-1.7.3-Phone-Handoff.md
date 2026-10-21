# Safe-Navi 1.7.3 — phone rehearsal handoff

## Install this build

Install Safe-Navi-1.7.3-Cloud-Rehearsal.apk over the existing application; do not uninstall just to update. Version code: 20. Android debug/demo certificate verified.

SHA-256: B91F775DAD0F1C9CF22582D5FFBC047756DB729CDCAEEB9F21C53E8AC43D09AD.

The requested map fixes are implemented:

- A and B are visible on opening the map and remain visible after choosing places and comparing routes.
- Submit either source or destination with **Go** to use the ranked best match; a second source-only list selection is no longer forced. You can still tap a different result or choose a precise map pin.
- Selecting a destination no longer silently replaces a missing source with an automatic GPS prompt. Choose the starting point visibly, or select My location yourself.

Build, 43 JVM tests and lint passed (zero lint errors; 495 warnings remain). **The new screen tests did not complete:** the test emulator's package/activity services failed and its test process hit BIND APPLICATION ANR before even a minimal test could start. This is logged, not counted as a pass. Please do the short actual-phone check below.

## Check from your Android phone

1. Open https://safe-navi-api.onrender.com/health and wait for status: ok. Free hosting may need time to wake.
2. Confirm the app's Server connection is https://safe-navi-api.onrender.com. Laptop hosting and same-Wi-Fi are not required.
3. Open Map: confirm both source and destination fields are visible. Enter Vashi Railway Station as A and SIES GST, Nerul as B. Tap Go in each search without selecting a list row. Confirm both chosen names stay visible.
4. If the college search picks a different SIES campus, use the exact college pin below. “Best match” does not mean an address provider contains every building/entrance.
5. Compare routes, open Route risks, inspect an explanation and check that A/B markers appear. Tell me if a field disappears or the result never finishes; include the exact names you entered.

## Live-cloud route list

The cloud model returned trained_ml for all 32 completed route evaluations across 16 driving pairs. Eight pairs had two preference winners. **None had three distinct winners.** Balanced matched Fastest in these examples; no times, risks or roads were altered to manufacture a third route.

These are measured provider travel estimates, not live-traffic guarantees. Scores use synthetic baseline area factors, not measured crime probabilities. Recheck after new official hazards appear.

| Source → destination | Fastest / Balanced | Safest | Morning exposure (fast / safe) |
|---|---:|---:|---:|
| Thane Railway Station, Kopri → CBD Belapur station, Belapur East | 69.7 min | 90.6 min | 42.75 / 40.81 |
| Vashi Railway Station → SIES GST, Nerul | 20.4 min | 24.6 min | 46.69 / 46.09 |
| Airoli Railway Station → CBD Belapur station | 44.8 min | 60.8 min | 44.17 / 43.92 |
| Mulund Railway Station → Bhandup Railway Station | 26.4 min | 26.9 min | 41.50 / 41.05 |
| Kurla Railway Station → Sion Railway Station | 26.0 min | 27.6 min | 45.92 / 45.69 |
| Dadar Central Railway Station → Sion Railway Station | 14.5 min | 15.0 min | 45.40 / 45.37 |
| Kharghar Railway Station → Taloja | 28.9 min | 31.5 min | 45.28 / 45.16 |

Use Thane → CBD Belapur for the clearest measured risk/time contrast. Use Vashi → SIES for the college example. Very small exposure differences should not be presented as statistically meaningful real-world safety improvements.

Exact pins:

- Thane, Kopri: 19.1846144, 72.9710278
- CBD Belapur station: 19.0209123, 73.0398886
- Vashi station: 19.0637159, 73.0002509
- SIES GST, Nerul: 19.042644, 73.023428
- Airoli station: 19.1583445, 72.9985308

For the 9–12 demonstration: 9–11 uses morning_peak, 11–12 uses midday. The same distinct winners were found in both periods. Full metrics, exact pins and matched place names are in Safe-Navi-Verified-Route-List.md and its JSON receipt.

Search warning: one extra Chembur query resolved to the railway-station post office, not the station itself. That matched address is recorded in the full list; it is excluded from the recommended station pairs above.

## Engagement you can demonstrate

Use the two retained citizen logins in **Safe-Navi-Cloud-Rehearsal-Access.private.json**. Do not upload that file to your report or GitHub—it contains passwords.

- Either account → Community → **DEMO REHEARSAL** → photograph, two helpful votes and a threaded conversation.
- Demo Anaya → Report → History → **DEMO REHEARSAL — case and evidence review** → Conversation. There is a private message and an attached app-logo photograph.
- Demo Rohan cannot read Anaya's private case. This was checked against the real cloud endpoint.
- Demo Anaya → Profile → saved station and clearly invalid **DO NOT CALL** demo contact.
- The private access file contains a simulated journey share URL. The journey was completed and its check-in disabled after the test.
- Your teammates may sign in and add their own real replies. Seeded demonstration activity must not be presented as independent user-adoption evidence.

The case is not a real incident and must not be verified/published as a hazard. Its app-logo attachment tests file sharing; it is not a fabricated crime photograph.

## Remaining verification

Current official/admin cloud access is required for positive official review, assignment, request-information/reply, genuine news publication and admin account/app-setting tests. Those operations are marked NEEDS_LOGIN/NOT_TESTED, not passed.

No real emergency calls were made. Actual phone GPS, photo-picker permissions, background delivery and offline behaviour need a device rehearsal. The cloud endpoint inventory explicitly lists all untested operations.

Logs: Safe-Navi-Cloud-Feature-Log.md, raw timestamped JSONL, and Safe-Navi-Cloud-Endpoint-Coverage.json.

Repository: https://github.com/Aniket-Nikam/Safe-Navi
