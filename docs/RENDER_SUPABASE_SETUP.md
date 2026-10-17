# Independent cloud hosting: Render + Supabase

Prepared 17 October 2026. Code and local regression tests are prepared; **this is not a deployed service yet**. Account creation, credentials, a GitHub version containing these files and live acceptance testing are still required. Do not enter payment details or accept a paid upgrade merely to follow this guide.

## What changes for the phones

The Android APK remains installed on each phone. All phones use the same public HTTPS backend origin, even on different mobile networks. Render runs the Python API and the supplied JSON ML model; Supabase persists accounts, reports, conversations, news and verified hazards in PostgreSQL, and photographs/videos in a private Storage bucket. The laptop can be off after deployment. The shipped risk SQLite database is an immutable model-input dataset, not the writable account/case database.

Free hosting is not guaranteed always-on: Render can sleep and take about a minute to wake; Supabase free projects can pause after inactivity. Quotas and provider policies apply. The app now allows longer connection timeouts but cannot eliminate provider outages or pauses. For a presentation, open `/health` and test both phones before starting.

## 1. Create your free accounts

1. Create a Render account: https://dashboard.render.com/register
2. Create a Supabase account: https://supabase.com/dashboard/sign-up
3. GitHub sign-in is convenient. Keep passwords, database URIs and service keys private; do not paste them into chat, screenshots, commits or the APK.

## 2. Prepare Supabase

1. Create a dedicated **Free** project, preferably near Render Singapore. Save its strong database password privately and wait until the project is ready.
2. In Storage, create a **private** bucket named `safenavi-evidence`. Do not enable public access or anonymous upload policies. Configure a 15 MB per-file limit and allow `image/jpeg`, `image/png`, `image/webp`, `video/mp4`, `video/webm`.
3. Obtain the project HTTPS URL and the server-only **legacy `service_role` JWT** from project API-key settings. This implementation expects that JWT, not an `anon`/publishable key. Never distribute it to phones. The backend checks that the bucket is private before using it.
4. In the database connection panel, select the **Session pooler**, port **5432**, with IPv4 support. Copy the actual URI provided for your project; do not guess its hostname. Percent-encode special characters in the password when placing it in a URI, and append `?sslmode=require` if absent. Use it as `SAFE_NAVI_DATABASE_URL`.
5. Do not expose the `safenavi` schema through the Data API. The backend creates this dedicated schema and revokes access from `PUBLIC`, `anon` and `authenticated`; Android talks only to the backend. Use a dedicated project because the server's database credential is privileged.

## 3. Deploy the backend on Render

The GitHub branch must contain `render.yaml`, `Dockerfile`, cloud modules and updated requirements. Local uncommitted files are not available to Render. Review changes and secrets before any Git push.

1. In Render, create a **Blueprint** from your Safe-Navi GitHub repository and the branch containing these changes. Select the free web service; do not add a paid disk or Render database.
2. Enter the private environment values requested by the Blueprint:

   | Variable | Value |
   | --- | --- |
   | `SAFE_NAVI_DATABASE_URL` | Supabase Session-pooler URI with SSL |
   | `SAFE_NAVI_SUPABASE_URL` | Your project HTTPS origin |
   | `SAFE_NAVI_SUPABASE_SERVICE_KEY` | Server-only legacy `service_role` JWT |
   | `GROQ_API_KEY` | A fresh, rotated Groq key; leave empty only if you accept assistant unavailability |

3. Keep the Blueprint defaults: cloud mode true, schema `safenavi`, Supabase evidence backend, bucket `safenavi-evidence`, external news scheduler. Render generates `SAFE_NAVI_SCHEDULER_KEY`; retain that value privately for step 5. Never reuse a key previously shared in chat.
4. Two Mumbai news RSS feeds are preconfigured in `SAFE_NAVI_NEWS_FEEDS`. These are external publishers and can change or fail. News is collected dynamically, deduplicated and reviewed; it does **not** automatically become a verified routing hazard.
5. Wait for deployment. Open `https://YOUR-SERVICE.onrender.com/health`. Confirm `status=ok`, `product_store=postgresql`, `evidence_store=supabase`, `news_scheduler=external`. Invalid persistent-storage configuration intentionally fails startup instead of silently saving records on Render's disposable disk.

The image includes only application code, model and immutable risk data. It does not include laptop product records, evidence, secret files or logs. Keep one API worker: product transactions are serialized with a database advisory lock for existing workflow consistency. This is a modest single-instance deployment, not a high-throughput scalability claim.

## 4. Create the first administrator, or deliberately migrate

Choose **one** approach before populating the new database.

### Start fresh (recommended for public access)

On the laptop, from the repository's `backend` directory, copy `.env.render.example` to the ignored `.env.render` file and fill its private values using a local editor. Include Render's generated scheduler key. Do not commit this file.

```powershell
.\venv\Scripts\python.exe -m pip install -r requirements.txt
.\venv\Scripts\python.exe -m app.cloud_check --env-file .env.render --origin https://YOUR-SERVICE.onrender.com
.\venv\Scripts\python.exe -m app.bootstrap_admin --env-file .env.render
```

The check can initialize the dedicated schema and checks database/private-bucket connectivity; it does not upload cases or create accounts. The admin tool prompts privately for a new password of at least 12 characters and refuses to create another admin if one exists. Citizens register normally; the administrator can promote an authorized citizen account to safety official. Admins still cannot change official case decisions or hazard records.

### Optional migration of existing laptop records

This publishes existing account/case/photo data to your cloud project. Obtain appropriate consent, back up the source, finish pending phone uploads, end active journeys and stop local writes for the cutover. Rotate shared presentation passwords before internet exposure. Do not create cloud accounts first: migration refuses a populated target.

```powershell
# Read-only preflight. Replace paths with the actual laptop product database/evidence directory.
.\venv\Scripts\python.exe -m app.migrate_cloud --source C:\ABSOLUTE\product.sqlite --evidence-dir C:\ABSOLUTE\evidence
# Explicit authorization to upload records and files to your configured cloud project:
.\venv\Scripts\python.exe -m app.migrate_cloud --source C:\ABSOLUTE\product.sqlite --evidence-dir C:\ABSOLUTE\evidence --env-file .env.render --apply
```

Preflight upgrades only a temporary snapshot, verifies evidence size/hash and reports counts. Apply copies records transactionally after uploading private objects and verifies row counts. Source files are not modified. Sessions are not migrated: everyone signs in again. Existing journey share links are revoked in the copied records, active journeys are ended and check-ins disabled; LAN sharing consent is not automatically extended to public internet links. A failed/interrupted migration may leave unreferenced private objects requiring reconciliation; it is not an automatic continuous-sync system. Preserve the original laptop installation as a rollback copy, but do not let users write to both servers and expect them to synchronize.

## 5. Schedule dynamic news checks while Render sleeps

An in-process timer cannot reliably run on a sleeping free web service. This deployment uses Supabase's database scheduler to wake Render only when a persisted news check is due.

1. Enable `pg_cron`, `pg_net` and `pgcrypto` in your dedicated Supabase project's database extensions. The supplied SQL expects `extensions.hmac` from pgcrypto.
2. In Supabase Vault, create two secrets:
   - `safenavi_backend_origin`: your Render HTTPS origin, without `/health` or another path.
   - `safenavi_scheduler_key`: the exact generated Render `SAFE_NAVI_SCHEDULER_KEY` (at least 32 characters).
3. After backend health succeeds, run `deploy/supabase_news_schedule.sql` in the Supabase SQL Editor. Keep schema `safenavi` unless you also intentionally adapt the scheduler SQL.
4. The SQL checks hourly in UTC whether the admin-selected 12/24-hour interval is due. A short-lived HMAC signature authenticates the request; neither the signing secret nor the service-role key is sent in queued HTTP headers. Persistent leases prevent simultaneous refresh workers.
5. As Admin, open news settings, choose 12 or 24 hours and use **Check internet now** for initial verification. Inspect source links, collected times and errors. As Official, review a signal before publishing a verified hazard.
6. Inspect `cron.job`, `cron.job_run_details` **and** `net._http_response` in SQL Editor. A successful cron execution only means it queued an HTTP request, not that news downloaded successfully. Check the HTTP status and the application's last successful refresh. Expect scheduling up to an hour after the due time, plus cold start/network delays; it is periodic collection, not second-by-second emergency detection. Failed feeds are retried on later due checks.

## 6. Connect and test the phones

1. Install `deliverables/Safe-Navi-Cloud-Ready.apk`. This is a debug-signed project APK, not a Play Store production release.
2. Before switching servers, finish all queued offline actions against the old server. The app blocks a cloud-origin change while pending actions remain to avoid attaching old-server evidence IDs to a different database. Do not uninstall to discard pending work.
3. Open **Server connection**, enter only `https://YOUR-SERVICE.onrender.com`, then **Test and save**. Endpoint changes sign out the old session, stop local journey tracking and clear the old server's derived hazard cache. Sign in/register against the new server.
4. On two phones using different networks, verify registration/login, forum posts and threaded replies, private case messages and photographs, official evidence access, official hazard publication and map refresh, route alternatives with clear A/B points, assistant and scheduled/manual news checks.
5. Verify access boundaries: unrelated citizens cannot read private evidence/cases; admin cannot change government decisions; official cannot change admin-only account settings. Confirm real changes remain after a Render restart/redeploy and evidence still opens.
6. Restart the phone app and test reconnection after Render sleeps. Test offline evidence capture and later upload against the **same** cloud origin. Free-tier wake-up latency and unavailable upstream map/search/routing services must show explicit errors, not fabricated results.

## Validation and remaining limits

- Local Android build, lint and 34 JVM tests passed; lint has no errors but existing warnings remain. Earlier map device checks passed; the newest cloud timeout/server-switch changes still need testing on actual cloud-connected phones.
- Backend tests after the dependency upgrades: **58 passed with a real isolated local PostgreSQL server; 55 passed and 3 PostgreSQL-only tests skipped with SQLite**. There is one test-client deprecation warning. Private-object HTTP requests and scheduler extensions use test stand-ins. These do not replace actual Supabase Storage, hosted extensions, Render image-build and cross-network phone acceptance tests.
- The old upload/file-serving dependency advisories were addressed by pinned upgrades. A 17 October 2026 OSV check found no listed advisories in the checked installed FastAPI, Starlette, multipart, HTTP/database and Pydantic packages. This is not a penetration test or a guarantee of no vulnerabilities.
- Provider quotas, project pausing, backup/restore testing, bucket usage and orphan-object reconciliation need owner monitoring. Keep private database backups plus evidence exports and test restoration; do not assume free-tier automated backups are sufficient.
- Current peer-based rate limits can group users behind a proxy. Review trusted-proxy handling and stronger per-account/distributed limits before broad public rollout. Keep API secrets server-only and restrict dashboard access. Never blindly trust forwarded IP headers.
- ML scores are based on the supplied model and baseline dataset, not complete real-time crime coverage. Area crime labels summarize relevant verified reports, not official crime-frequency statistics. Same-route preference ties remain truthful; no different ETA/risk is invented for demonstration.

## Provider references

- Render free-service sleep and ephemeral disk: https://render.com/docs/free
- Render Blueprint configuration: https://render.com/docs/blueprint-spec
- Supabase database/pooler connections: https://supabase.com/docs/guides/database/connecting-to-postgres
- Private Storage buckets: https://supabase.com/docs/guides/storage/buckets/fundamentals
- Supabase free quotas and project pausing: https://supabase.com/pricing
- Database HTTP requests: https://supabase.com/docs/guides/database/extensions/pg_net
- Why queued request headers must not contain service keys: https://supabase.com/docs/guides/troubleshooting/database-roles-can-read-request-headers-queued-by-pg_net-ad6357
