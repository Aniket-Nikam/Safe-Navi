# Safe-Navi two-device demonstration

## What works between devices

Safe-Navi is server-mediated. Two Android phones do not connect directly to each other;
both connect to the same FastAPI backend and product database.

- Two citizen phones can exchange community posts, photographs, comments and helpful votes.
- A citizen phone and a government-official phone can exchange private report messages and
  citizen follow-up photographs.
- Verified hazards created on the official phone are pushed to active maps and cause
  route refresh/re-ranking on the citizen phone.
- This is persistent application communication, not an arbitrary person-to-person DM system.

## Current cloud demonstration (Android 1.7.2)

Use `https://safe-navi-api.onrender.com`. The laptop does not have to run, and the
phones may use different Wi-Fi or mobile-data networks. Both must have internet.
The app's Server connection setting must show this HTTPS origin, not an old laptop IP.
After a long idle period the free cloud service may need 50 seconds or more to wake.
Open `https://safe-navi-api.onrender.com/health` before the demonstration and wait for
`"status":"ok"`. Do not repeatedly tap Login while the service is waking.

## Optional isolated local demonstration

Use this only for explicitly labelled fixture data; never publish fictional hazards
on the shared cloud map. Set the app's Server connection to the actual laptop LAN IP
with port 8000. Both phones must use the same trusted Wi-Fi as that laptop.

Start the backend from the repository root with `run_local.ps1`, or run Uvicorn from the
backend directory with host `0.0.0.0` and port `8000`.

Windows Firewall must permit inbound TCP 8000 for the Private profile and local subnet.
Run PowerShell as Administrator once:

```powershell
New-NetFirewallRule -DisplayName "Safe-Navi Demo API" `
  -Direction Inbound -Action Allow -Protocol TCP -LocalPort 8000 `
  -Profile Private -RemoteAddress LocalSubnet
```

Do not create an Any/Any public-network firewall rule. For an internet-hosted demo, use
an HTTPS deployment and set `RISK_API_BASE_URL` to that origin instead of exposing the
development server directly.

## Demonstration A: community communication

1. Install the same debug APK on Phone A and Phone B.
2. Register a different citizen account on each phone.
3. On Phone A, publish a community update with a photograph.
4. On Phone B, open Community and refresh; open the post, reply and vote.
5. Refresh Phone A to show the persisted reply and vote.

## Demonstration B: citizen-to-official case conversation

1. Create a controlled government account with `backend/scripts/create_user.py`.
2. Sign in as a citizen on Phone A and as the government user on Phone B.
3. Phone A submits a geolocated report with evidence.
4. Phone B opens the operational queue, assigns it and requests more information.
5. Phone A opens Report history, reads the request and responds with a new photograph.
6. Phone B opens the operational record, reads the response and previews the evidence.
7. Phone B verifies the report with severity, road status and a no-go radius.
8. Phone A opens the map and demonstrates the new marker and route re-ranking.

## Networking check

For the cloud demo, open `https://safe-navi-api.onrender.com/health` in each phone's
browser. Local Wi-Fi isolation and Windows Firewall rules apply only to the optional
laptop server, not the cloud service. Account credentials belong to the selected
server; a laptop-only account is not automatically a cloud account.
