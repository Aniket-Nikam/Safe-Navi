# Safe-Navi cloud-readiness handoff

17 October 2026. **Prepared, not publicly deployed.** Create your free Render and Supabase accounts before publishing.

- APK: `Safe-Navi-Cloud-Ready.apk`, SHA256 `DEF3C4482C7B21AFCA5D0C0AA33917804A341B4BA17C13C5D33392D8DB2BD2F6`.
- Full setup: [Render + Supabase](../docs/RENDER_SUPABASE_SETUP.md).
- Android: assembleDebug, lintDebug and 34 JVM tests passed. Lint has no errors; existing warnings remain. This is a debug-signed APK.
- The app supports a shared HTTPS origin, longer free-tier wake-up timeouts and safer server switching. Finish pending offline uploads on the old server before switching.
- Persistent cloud product records use PostgreSQL; private photos/videos use Supabase Storage. A due-aware external scheduler supports the admin's 12/24-hour news interval while Render sleeps. Laptop SQLite mode is retained.
- Backend regression suite after dependency updates: PostgreSQL 58 passed; SQLite 55 passed / 3 PostgreSQL-only skipped. One test-client deprecation warning remains. Actual hosted acceptance is pending.
- Existing laptop records/photos have not been uploaded or changed by a cloud migration. An explicit preflight/apply tool is provided, with old sessions and journey sharing disabled in migrated copies.
- No public URL, cloud account, bucket, secrets, first cloud administrator or GitHub deployment has been created automatically. Cloud Storage and actual hosted scheduler extensions still require live testing, including two phones on different networks.
- Free tiers can sleep, pause or hit quotas. Rotate chat-shared keys/passwords before public exposure. A passing test suite is not a guarantee of zero bugs or production-scale security.

Older progress packages in this folder are historical artifacts, not the current cloud-ready APK or current feature guide.
