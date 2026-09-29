# Accounts, cloud sync and the Discipline link — brainstorm

**Status:** idea, nothing built (2026-09-29). Supersedes the "Discipline sync / optional account"
line in [PRODUCT.md](../PRODUCT.md#future-discipline-integration); its sync prerequisites still apply.

## What the owner wants

- IronIndex stays local-first: no account needed, everything works offline (unchanged).
- **Optional Google sign-in** turns on cloud sync/backup across phones.
- **One identity across apps**: the same Google account is the same person in IronIndex and
  Discipline, so the two can recognise each other's data later.
- **Discipline knows you worked out** — from IronIndex, a Galaxy Watch (Samsung Health) or the phone.
- **Nothing that costs money to run.** It's personal today; the goal is to share the apps with
  other people and work out a money model (paid tier, paid extras) later. Running costs must not
  grow with users before there's revenue.

## Cost reality (checked 2026-09-29)

| Option | Cost to the developer | Catch |
|---|---|---|
| Local only + Android Auto Backup + JSON export (shipped 0.4.0) | Free | Restores on a new phone; no live sync between phones |
| **Google Drive app-data folder** (each user's own Drive) | Free — storage comes out of the *user's* Drive quota | Scope `drive.appdata` is *non-sensitive* (no heavy Google review). No server-side queries, no sharing between users |
| Supabase free plan (Discipline's project) | Free up to 500 MB DB, 50,000 monthly active users, 5 GB egress, 2 active projects | **Free projects pause after 1 week of inactivity** — fine for one person, unreliable for strangers. Pro is from $25/month |
| Health Connect (on-device) | Free | Sessions only (type, time, duration), not sets; Samsung Health must be allowed to write exercise |

## Recommended direction

1. **Now (free, shipped):** local database + Android Auto Backup + manual backup file.
2. **Free cloud sync:** Google sign-in (Credential Manager) + sync to the user's **Drive app-data
   folder**. Costs nothing to run however many people use it.
   - Reuse the backup format as the sync payload: upload the merged backup file, and on another
     phone merge it with the existing import rules (`planImport`: merge by ID, newer `updatedAt`
     wins, an unfinished copy never replaces a finished session). Most of the logic already exists.
   - Google setup: **same Google Cloud project as Discipline**, one more Android OAuth client for
     `com.ironindex.app` (package + SHA-1 of debug and release keys), same Web client ID.
     Identity = the Google account's stable `sub`, identical in both apps.
   - Verify: whether two apps in one Cloud project share or have separate app-data folders.
3. **Supabase only when a server is truly needed** (sharing a partner's history between phones,
   coach mode across devices, a web view) — likely a paid tier. Use Discipline's project with
   separate `ironindex_*` tables (one JSON row per workout, like Room today) and per-user access
   rules; sign in with the same Google ID token so the Supabase user matches Discipline's.
4. **Discipline link without any server:** IronIndex writes an `ExerciseSessionRecord` to
   **Health Connect** when a workout finishes (update it when a session is edited or moved,
   delete it when the session is deleted). Discipline reads all exercise sessions — IronIndex,
   Galaxy Watch via Samsung Health, other apps — for streaks, Home and its heckling. No shared
   database, and it works whether or not both apps are installed.

## Before any sync ships

- Deletion tombstones (ROADMAP maintenance row) so deletes propagate instead of coming back.
- `updatedAt` on catalog records (equipment, exercises, partners), not only workouts.
- A Room migration + MigrationTest for any schema change (never destructive).
- Play Store: privacy policy; Health Connect permission declaration; if accounts ever live on a
  server, in-app account deletion. With Drive-only sync, "delete my cloud data" = delete the
  app-data file.

## Open questions

- Money model: what (if anything) is paid — cross-device partner sharing, coaching, extras?
- Should **Discipline** also move its cloud backup from Supabase to Drive app data so it's free
  to run for other people? Same pausing problem applies to it once it's shared.
- Samsung Health → Health Connect exercise sync has had update regressions; test on the owner's
  phone and Galaxy Watch before relying on it.

## Sources

- Supabase pricing: https://supabase.com/pricing
- Drive scopes (`drive.appdata` recommended, non-sensitive): https://developers.google.com/workspace/drive/api/guides/api-specific-auth
- Samsung Health ↔ Health Connect: https://developer.samsung.com/health/health-connect-faq.html
