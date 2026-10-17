# Safe-Navi: zero-cost deployment choices

Prepared 17 October 2026. Deployment files are included; **no public deployment or cloud account has been created**.

## Selected independent option: Render + Supabase

PostgreSQL product storage, private Supabase evidence storage, a due-aware external news scheduler and explicit migration tooling are now implemented. Follow [the Render + Supabase setup guide](RENDER_SUPABASE_SETUP.md). Free accounts and actual hosted acceptance tests are still required. This is the selected path for phones on different networks with the laptop off.

## Quick demonstration: free HTTPS tunnel, laptop stays on

1. Install Cloudflare's official `cloudflared` executable and make it available on PATH: https://developers.cloudflare.com/tunnel/get-started/quick-tunnels/
2. Run **Start Safe-Navi**, verify local `/health`, and keep the laptop awake with internet access.
3. From this repository run `powershell -ExecutionPolicy Bypass -File .\start_safenavi_public.ps1 -ConfirmPublicSharing`.
4. Enter the printed **HTTPS** `trycloudflare.com` origin in **Server connection → Test and save** on each phone. Phones can use different networks. Sign in again if requested. Do not append `/docs`.
5. Keep both server and tunnel running. Ctrl+C closes internet access. Restarting the tunnel changes its URL; update every phone. Re-share journeys after changing origin.

This is a temporary tunnel, **not independent cloud hosting**. Cloudflare offers no uptime SLA for Quick Tunnels and imposes limits; SSE is unsupported (Safe-Navi uses WebSockets and polling, not SSE). Test photo upload, login, map, news and conversations over the actual tunnel before presenting.

## Independent server: persistent VM in an eligible free tier

Oracle advertises Always Free compute/storage within specified allowances, subject to signup, card verification, regional capacity, eligibility, idle-resource reclamation and policy changes. Account creation, region choice, resource provisioning and DNS ownership must be completed by you. Do not upgrade to paid resources or accept charges merely to follow this guide. No provider offers a guaranteed unlimited free service.

On a suitable Linux VM with Docker Engine and Docker Compose installed:

1. Clone this repository at the version containing these changes. Point a hostname you control (or an eligible free DNS hostname) at the VM's public IP. Only allow ports 80/443 publicly; keep SSH restricted. Do **not** open 8000 publicly.
2. Copy `backend/.env.production.example` to `backend/.env.production`. Set `SAFE_NAVI_HOST` to the real hostname, configure fresh Groq credentials and HTTPS news feeds privately. Do not copy the laptop's secret file blindly. Caddy obtains HTTPS certificates after DNS and firewall setup succeed.
3. Run `docker compose --env-file backend/.env.production up -d --build`.
4. Run `docker compose --env-file backend/.env.production exec api python -m app.bootstrap_admin` to create a **new** private administrator. Citizens register normally; the administrator can promote an authorised account to official. Do not reuse chat-shared demonstration passwords for public deployment.
5. Verify `https://YOUR_HOST/health`, then put `https://YOUR_HOST` in Server connection on both phones. Check register/login, citizen photo report, official evidence access and hazard publication, three route preferences, community replies and news refresh. Confirm unauthorised role actions return 403.

The named `safenavi-data` volume contains the product database **and evidence**. The shipped image contains only the runtime risk dataset and JSON model, not laptop accounts, photographs, logs or secrets. Keep one API worker for SQLite and the in-process news scheduler. Back up `/data` safely (SQLite backup API or while the API is stopped) and test restoration. Never use `docker compose down -v` unless intentionally deleting stored cases/photos. Existing laptop records require a deliberate, backed-up SQLite/evidence migration; this configuration starts fresh by default.

### Important limitations and deployment checks

- Render's free service uses ephemeral storage: never use local SQLite product records or uploads there. The supplied `render.yaml` requires PostgreSQL and private Supabase storage; see the selected setup guide above. The immutable risk dataset can safely remain in the image.
- The deployment configuration is prepared, **not cloud-tested on a provisioned account**. Availability, DNS, certificates, backup/recovery, provider egress, photo sizes and real-device behaviour still require deployment acceptance testing.
- Rate limiting currently keys on the connected peer. Behind Caddy/tunnels that can mean one shared proxy bucket. Uvicorn deliberately ignores arbitrary forwarded headers. Configure a trusted proxy boundary and per-account/distributed limits before broad public use; do not simply trust all forwarded headers.
- API docs and public verified-map information are reachable publicly; private account/report/evidence routes remain authenticated. Review public content and consent, rotate the Groq key shared in chat and all shared demonstration credentials before public exposure.
- The app's dynamic endpoint supports HTTPS; no APK rebuild is necessary for a new origin. An Android update is required to get the new map interface.

Sources: https://render.com/docs/free · https://developers.cloudflare.com/tunnel/get-started/quick-tunnels/ · https://www.oracle.com/cloud/free/ · https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm · https://www.oracle.com/cloud/free/faq/
