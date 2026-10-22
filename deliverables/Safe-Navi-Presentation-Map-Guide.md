# Presentation map — cloud setup and tested routes

Prepared 22 October 2026 for the owner's explicitly non-production academic presentation project.

## Ready on the existing cloud

Ten sample citizen reports were submitted through the two existing citizen accounts,
reviewed/assigned through the existing Safety Official account and published through
the normal API. Two existing source-linked news records were reviewed and published
as presentation replays. All twelve appear in the cloud map response.

Titles use natural incident wording. Descriptions and official audit reasons retain
simulation provenance: these are not independently observed/field-verified incidents.
The original rehearsal conversation/report was not altered or published.

This does not create a separate tenant or hidden mode. Any client connected to the
existing service can read the active map layer. Do not advertise it as live public
safety information or use these sample alerts for actual travel decisions.

## Where to look

| Area / corridor | Sample report |
| --- | --- |
| Juhu | Pickpocketing concern |
| Sion station approach | Harassment concern |
| Mulund station approach | Streetlights not working |
| Chembur station approach | Roadside obstruction |
| Ghatkopar–Chembur corridor | Repair work and temporary road closure |
| Vashi–Nerul corridor | Phone-snatching concern on a selected road |
| Vashi station approach | Pickpocketing concern |
| Sanpada station approach | Waterlogging |
| Nerul station approach | Poor lighting |
| CBD Belapur approach | Roadwork |

The source-linked replays use the [Juhu article](https://www.hindustantimes.com/entertainment/if-tiger-isn-t-safe-none-of-us-are-bollywood-fears-underworld-s-return-after-juhu-attack-101792436061979.html)
and the [coastal-road construction article](https://indianexpress.com/article/cities/mumbai/mumbai-coastal-road-bmc-notice-lt-illegal-debris-dumping-10919803/).
Their source links and original publication timestamps remain intact. Source-locality
pins and simulated severity do not establish a current threat or real road closure.

## Best example: Vashi Railway Station → SIES GST, Nerul

Choose **Drive** and use these exact endpoints:

- A: Vashi Railway Station — `19.0637159, 73.0002509`.
- B: SIES GST, Nerul campus — `19.042644, 73.023428`.

The real road provider supplied three different road geometries. The deployed backend
used `trained_ml` and the published sample hazard layer, with unchanged ranking rules.

| Preference | Distance | Estimated time | Morning risk score | Midday risk score |
| --- | ---: | ---: | ---: | ---: |
| Fastest | 4.96 km | 20.4 min | 89.39 | 88.66 |
| Balanced | 8.07 km | 24.6 min | 66.26 | 63.97 |
| Safest | 6.28 km | 27.6 min | 59.94 | 57.29 |

Morning is the app's 7–11 a.m. period; midday is 11 a.m.–5 p.m. Both checks produced
three distinct recommendations. Times are provider estimates, not measured live traffic.
Different entrance pins, upstream road changes or later hazard edits can change results.
If search chooses a different college/parking result, move B to the exact campus pin.

This demonstrates a trade-off: the shortest-time road has greater sample exposure;
Balanced accepts a detour to reduce it; Safest chooses the lowest assessed exposure.
Distance alone does not determine travel time.

## Additional comparisons

- Vashi Railway Station → CBD Belapur Railway Station: two distinct recommended roads
  in both morning and midday checks; before the samples, all three preferences selected
  one road for the captured candidates.
- Ghatkopar Railway Station → Chembur station approach: one captured road alternative
  intersects the temporary sample closure and is excluded; two recommended roads remain.
  Exact tested A: `19.0861295,72.90902`; B: `19.06298,72.9010082` (the provider labels B
  Chembur Railway Station Post Office / station approach, not an exact train-platform pin).
- Mulund Railway Station → Bhandup Railway Station: two distinct recommendations;
  sample exposure changed, but the winning roads did not change in this check. Do not
  claim that every added alert must force a different road.

## What was verified

- All ten new reports have final VERIFIED workflow status and linked backend hazards.
- Public map response includes all ten report markers and both source-linked news replays.
- Four actual provider route sets were evaluated before/after seeding; three sets changed
  at least one recommendation. No ETA, model output or preference score was fabricated.
- All four route sets were also checked during the morning period. Model provider
  remained `trained_ml` and available alternatives remained routable.
- The selected closure excluded its intersecting road while leaving alternatives.
- Both news replay IDs appeared in `affecting_hazards` on real provider road routes near
  their pins. Their verified-hazard exposure was nonzero; they are not map-only markers.
- The Vashi area-crime endpoint returned theft/burglary and robbery/snatching labels
  from the published sample text. These are descriptive labels, not crime statistics.
- The Android emulator loaded all twelve records from the actual cloud and rendered
  the map labels and the populated Crime & alerts list. This was not a localhost fixture.
- A second Android cloud test used the exact endpoint pins above and verified three
  different rendered route layers, all three visible preference cards, the active
  trained model and no offline fallback. This checks the pin-to-routing/rendering
  flow; it does not re-test every free-text search spelling.

Hazard effects come from the existing exposure/closure rules combined with the model's
baseline. Publishing these samples does not retrain the ML model or validate its
synthetic estimates against real crime outcomes.

## Expiry and phone steps

The report hazards expire automatically on **25 October 2026, approximately 9:22 a.m. IST**.
The two news replay hazards expire a few minutes later. Expiry hides them from active
map/routing queries; historical reports/audit records remain.

No new application build is needed for this data. In the existing 1.8.1 app:

1. Confirm the normal cloud connection and sign in with either existing citizen login.
2. Reopen **Map** and allow alerts to refresh. **Crime & alerts** should list 12 active records.
3. Tap any marker/list item to inspect its reason and, for news, its source.
4. Plan the Vashi → SIES route in Drive mode; compare Fastest, Balanced and Safest.
5. As Safety Official, use **Filter reports → Status → VERIFIED** to inspect the
   accepted reports and their assignment/audit; the default queue only shows pending
   cases. Under News intelligence, the two selected records are published replays.

If an already displayed route is cached, use **Route details → Refresh routes** after
loading the new alerts. Explain to the judges that these are presentation scenarios exercising the
real workflow and routing engine, not claims of real citizen adoption or current crimes.
