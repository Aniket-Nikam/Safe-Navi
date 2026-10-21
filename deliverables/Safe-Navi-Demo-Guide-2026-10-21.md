# Safe-Navi: demonstration guide for 22 October 2026

## Install and prepare

Install **Safe-Navi-1.7.2-Demo-Verified.apk** over the existing app. It is a signed Android debug/demo build, not a Play Store production release. Keep drafts and app data; do not uninstall merely to update.

1. Both phones need internet, but may use different networks. The laptop is not required for the cloud demo.
2. Before presenting, open https://safe-navi-api.onrender.com/health and wait for `status: ok`. A free Render instance can take 50 seconds or more to wake after inactivity.
3. In the app, confirm Server connection is `https://safe-navi-api.onrender.com`. If a previous local address was deliberately saved after migration, change it here.
4. Sign in to the cloud account for each role. Local-only accounts and sessions are not automatically transferred. Keep GPS permission enabled for “My location”; manual start/end searches do not require GPS.
5. Rehearse on the actual phone, especially photo selection and location permissions. Do not place real emergency calls during the presentation.

## Verified locations for visible path differences

Tested on 21 October against real Photon search, Valhalla driving routes and the live trained-ML backend. The values below are a receipt, not guaranteed future travel times or safety guarantees. Use **Drive**, not Walk. Select the exact station results/localities or use the coordinates if a similarly named result appears.

### Recommended: Thane Railway Station → CBD Belapur Railway Station

- A: **Thane Railway Station, Kopri, Thane** — `19.1846144, 72.9710278`.
- B: **C.B.D.Belapur Railway Station, Belapur East, Navi Mumbai** — `19.0209123, 73.0398886`.

| Preference | Evaluated road | Distance | Time | Exposure 7–11 a.m. | Exposure 11 a.m.–5 p.m. |
|---|---|---:|---:|---:|---:|
| Fastest | Road 2 | 24.758 km | 69.7 min | 42.75 | 38.81 |
| Balanced | Road 2 | 24.758 km | 69.7 min | 42.75 | 38.81 |
| Safest | Road 3 | 33.322 km | 90.6 min | 40.81 | 36.55 |

Show both recommended road geometries, then tap Safest and Fastest. Explain the approximately 21-minute detour and lower assessed exposure. The shared Fastest/Balanced road is deliberately drawn once in slate blue; the distinct Safest road is green. The app must not invent parallel roads to imply a difference.

### Backup: Airoli Railway Station → CBD Belapur Railway Station

- A: **Airoli Railway Station, Airoli Station Road** — `19.1583445, 72.9985308`.
- B: the same exact CBD Belapur station above.
- Fastest and Balanced: 17.033 km, 44.8 min; exposure 44.17 morning / 40.38 midday.
- Safest: 21.229 km, 60.8 min; exposure 43.92 morning / 40.11 midday.
- This exposure difference is small. Use Thane → CBD Belapur as the stronger illustration.

**Do not promise three distinct paths.** Ten pairs were tested at both morning and midday: two pairs produced two distinct winners; none produced three. Vashi Station → CBD Belapur and Nahur → SIES did not give distinct preference winners in this check. Balanced can legitimately equal Fastest. Recheck after a new official hazard is published because the evaluated conditions change.

## Suggested demonstration sequence

1. Citizen login and Home: explain the shared cloud backend, not direct phone-to-phone communication.
2. Map: search the recommended station pair, show A/B, simultaneous recommendations, then enable Route risks. Open an estimate and distinguish it from an official alert. No pin is not proof of safety.
3. Report: show title, category, description, exact pin and a photo. For a real operational submission, use a genuine issue. Use an isolated fixture for a staged incident rather than publishing fiction on the shared map.
4. Community on two accounts: publish a harmless clearly labelled demo conversation, open its discussion, reply and vote. Refresh the other phone. Community content is not an official hazard.
5. Official: inspect the case photo, assign a department, request information and open the private case conversation. Citizen replies with a follow-up photograph; official reads it. Admin is not an operational case officer.
6. Official hazard: only verify a genuinely checked incident. Show severity, road status and reason. Closed routes intersecting its no-go geometry are excluded; unsafe areas increase assessed exposure. A report alone does not affect routes.
7. News: show the current internet-source queue and source links. Admin controls the 12/24-hour interval. Official must verify the current incident and exact affected road before publishing it. Unverified headlines are not automatically mapped or treated as road closures.
8. Admin: show account access/role management and app/news settings; explain that admin cannot publish government hazards or conduct private case conversations.
9. Navi and emergency toolkit: ask a safety question, show emergency contacts, dialler and location-sharing options. Do not call a real emergency number for the demonstration.

## Accurate wording for judges

“Valhalla supplies real road alternatives. Our exported linear model estimates area-based exposure, and the app ranks the same candidates for time, a time–risk trade-off, or lowest assessed exposure. Officially verified hazards can add risk or exclude a closed road. The model uses synthetic baseline factors; these scores are not measured crime probabilities or safety guarantees.”

“News is fetched dynamically on an admin-controlled schedule. Classification is keyword-based, not a separately trained news neural network. An official reviews its source, time and precise location before it influences routing.”

Repository: https://github.com/Aniket-Nikam/Safe-Navi
