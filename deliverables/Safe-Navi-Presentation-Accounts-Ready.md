# Presentation accounts — cloud preparation

Prepared 22 October 2026 (India time) against https://safe-navi-api.onrender.com.

Login secrets are deliberately excluded from this document and Git. The owner has
the four credentials in the chat and the ignored `Safe-Navi-Presentation-Logins.private.json` handoff.

## Accounts and permissions

| Login option | Display name | Prepared use |
| --- | --- | --- |
| Admin | Demo Administrator | Account management, app settings, audit and news schedule |
| Safety Official | Demo Safety Official | Case review, evidence, assignment and private case conversation |
| Citizen | Demo Anaya | Existing sample post, photograph, report and official conversation |
| Citizen | Demo Rohan | Community discussion, nested replies and helpful votes |

The project owner explicitly approved the privileged identities. Only the newly
registered presentation admin was provisioned through the authenticated Supabase
owner workspace, with a provisioning audit explaining that mechanism. That admin
promoted the new official through the normal audited account-action API. Existing
accounts and their passwords were not changed. No infrastructure secret was used
as an application password.

## Checks completed on the deployed backend

- All four accounts: successful login and logout; expected cloud roles confirmed.
- Admin: accounts, app settings, audit and news settings return HTTP 200.
- Admin: attempt to triage a nonexistent government case returns HTTP 403 before any write.
- Official: reports, operational metrics and news signals return HTTP 200; 148 news signals available at this check.
- Official: admin accounts endpoint returns HTTP 403.
- Existing explicitly labelled DEMO REHEARSAL case: triaged and assigned to Demo Safety Official in Roads & Infrastructure.
- Official requested clarification; Demo Anaya replied with the existing logo upload and confirmed it is not a real incident.
- Case remains UNDER_REVIEW, with official/citizen messages readable and its photograph downloading successfully for the official.
- Public hazard map contains no hazard linked to that rehearsal case.
- Cloud health confirms PostgreSQL product persistence, Supabase evidence storage and trained ML risk provider.

These are cloud API/database checks, not a new physical-phone UI or GPS field test.
No build or reinstall is needed just for these new server-side accounts.

## Short presentation sequence

1. Confirm Server connection is the cloud URL above. Sign out before switching roles.
2. As Demo Anaya, show Community and Report → History → DEMO REHEARSAL → Conversation.
3. As Demo Rohan, open the same community discussion and show the replies/votes.
4. As Safety Official, select the DEMO REHEARSAL case; show its assignment, photograph, conversation and case audit. It is deliberately unpublished.
5. As Admin, show Accounts, app settings, audit and news/schedule controls. Avoid suspending the accounts needed for the demonstration or disabling app-wide features.
6. Return to a citizen account for the map and foreground navigation/demo preview.

The sample photograph is an upload-test logo, not incident evidence. Explain that
the activity was seeded for rehearsal, not independent user adoption. Never publish
this fictional case as a real hazard. Use genuine verified information for publication.
Rotate the shared demo passwords and revoke unused sessions after the presentation.
