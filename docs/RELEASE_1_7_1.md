# Safe-Navi 1.7.1 - automatic hosted-server upgrade

Cloud builds now perform a one-time startup migration of saved server overrides to
https://safe-navi-api.onrender.com. No manual server entry is required when updating
an older laptop-connected installation.

- Existing sessions already on the hosted backend remain signed in.
- Laptop-only authentication is not valid on the hosted database. Its token and
  active journey are removed, while the email is retained for sign-in.
- Accounts and records on either backend are not deleted or automatically copied.
- Backend-scoped drafts and local photographs remain on the device.
- Pending offline reports are stamped with their original backend before migration.
  They cannot silently upload to a different server or cloud account.
- Explicit later server overrides and intentionally configured LAN builds still work.
- Map, ML, news and report functionality is unchanged from 1.7. The 1.7 workflow
  video remains applicable and uses explicitly labelled isolated demo data.

Verification: Android unit suite, device migration tests (fresh install, laptop
upgrade, existing cloud login, retained local draft/evidence reference and
backend-isolated upload queue), plus existing login/community UI regression.
