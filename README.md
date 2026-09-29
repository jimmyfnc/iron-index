# IronIndex

A colorful, standalone Android workout tracker. Built with Kotlin, Jetpack Compose
and Room. Works offline with no account required. Current version: **0.11.0**.

## Included

- Freestyle workout logging with automatic draft recovery.
- Custom exercises, reps, weight, checked sets, equipment, brand/model, optional muscle group.
- Reusable routines: create ahead of time or save from a live/completed workout.
- Workout history, repeat-session flow, and editing finished sessions (values, sets, exercises, date).
- Per-workout lb/kg units (switchable until a weight is entered; default follows your region), comma or dot decimals, and a starter exercise library.
- Dark ink, lime, lavender and coral interface.
- Drag-and-drop exercise ordering via Reorder, with accessible up/down alternatives.
- Equipment library: separate brand, model, optional gym/nickname, and a starting weight (bar, sled) with plate math or plates-only logging.
- Machine setup: named settings (seat height, cable attachment…) recorded per person on each exercise, prefilled from last time.
- Partner Manager with goals, notes, history and stats; independent sets and per-person exercise lists.
- Single-arm/single-leg logging with independent Left / Right values.
- Primary/secondary muscles and isolation notes; inline previous-session references.
- Progress charts, weekly/monthly analytics, machine-specific PRs and L/R comparisons.
- Backup & restore: export everything to a JSON file and import it on any phone; Android
  auto backup to your Google account and phone-to-phone transfer.
- Health Connect sharing (opt-in, write-only): finished sessions appear in Discipline, Samsung Health
  and other apps; sets and weights stay in IronIndex.

## Using the new features

- **Train → Equipment**: create and edit machine profiles. Distinct brand/model/location
  profiles get separate histories. Archive retired equipment without removing past sessions.
  Add a **starting weight** (45 lb bar, 55 lb power bar, 167 lb leg press sled) and choose
  how you log it: **Total weight** (type 225; the set shows "45 bar + 90 per side") or
  **Plates only** (type 180; it shows "+167 sled = 347 lb total"). Charts, PRs and volume
  always use totals. Changing it on a machine you've used asks how past weights were typed.
  **Setup settings** lists what you adjust each time (Seat height, Attachment, Pulley height);
  suggestions follow the machine type.
- **Setup line on each exercise**: shows the setup for the person you're logging. Before you
  set it today it shows their last one with its date: tap **Same** to keep it, or tap the line
  to change values or add a setting (added to the machine for next time).
- **Train → Exercises**: your exercise library with likely duplicates first ("Biceps Curl" and
  "Biceps curl"). **Merge** moves a custom exercise's sessions and routines onto the one you keep;
  sets and machines stay as logged. Built-ins can be merged into, not removed.
- **Train → Partners**: add profiles with an optional goal and notes. **History & stats**
  shows a partner's sessions, records and totals, and links to their Progress charts.
- **Inside a workout**: adding an exercise fills in each person's sets from last time on that machine
  (unchecked; converted if the unit or starting weight changed). The keyboard's Next goes from weight
  to reps and Done checks the set. **Partners** attaches people and optionally enables their sets;
  person chips switch logging and stay pinned at the top while you scroll. New exercises
  mirror to everyone. **Skip for …** hides an exercise for one person (Add brings it back
  with their targets). Adding an exercise while a partner chip is selected adds it only for them.
- **Exercise details button**: select equipment, muscles and side mode. Change side mode
  before entering values. Renaming asks whether it's the same exercise (one history) or
  a different one (its own history). Picked the wrong machine? **Choose equipment** moves
  the logged sets to the right one, weights as typed. **Edit** fixes the machine's name or
  starting weight without leaving the workout; name fixes show in past sessions too. If
  you switched machines partway through, add another entry instead.
- **Reorder**: hold and drag a handle in the order sheet, or use the arrows.
- **Exercise history**: see prior completed sessions for that person and machine.
- **Edit a finished session**: open it from History and tap **Edit session**. Fix values,
  check a forgotten set, add or remove sets and exercises, or **Change date**. **Done
  editing** marks it "edited"; **Discard changes** puts it back as it was. If no set is left
  checked, the original is kept.
- **Train → Backup**: **Export backup** saves one JSON file wherever you choose (Drive,
  Downloads, email). **Import backup** shows what will be added before writing anything;
  it merges by ID, never deletes, and importing the same file twice changes nothing.
  A home-screen reminder appears when sessions haven't been backed up for two weeks.
  **Share workouts with Health Connect** (same screen) saves each finished session's name, time
  and type to Health Connect after you allow it; edits and deletions follow. The card shows the
  last share and opens Health Connect if access needs allowing again.
- **Progress**: choose a person and a specific exercise/equipment/side series. Tap chart
  points or open the chart values list. Metric definitions are shown beside the results.

Partner profiles are local to this phone; cross-phone sync is not implemented. Routines
and repeated sessions keep your exercise targets but do not assume partner attendance,
and exclude exercises you skipped.

## Run

Open this folder in Android Studio and run the `app` configuration on Android 8+
(API 26+). Install SDK 36 and use Android Studio's bundled JDK (17 or newer).
Android Studio creates the ignored `local.properties` containing your SDK path.

PowerShell on this machine:

```powershell
$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'
./gradlew.bat assembleDebug testDebugUnitTest lintDebug
./gradlew.bat connectedDebugAndroidTest
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Preview and verification

[Home](docs/screenshots/home.png) · [Workout](docs/screenshots/workout.png) ·
[Numeric keyboard](docs/screenshots/keyboard.png)

0.2.0: [Partner + Left/Right logging](docs/screenshots/0.2.0/logging.png) ·
[Reordering](docs/screenshots/0.2.0/reorder.png) ·
[Progress](docs/screenshots/0.2.0/progress.png). These previews use synthetic test data.

0.11.0: [Likely duplicates](docs/screenshots/0.11.0/exercises-duplicates.png) ·
[Merge](docs/screenshots/0.11.0/merge-confirm.png).

0.10.0: [Prefilled from last time](docs/screenshots/0.10.0/prefilled-from-last-time.png).

0.9.0: [Health Connect card](docs/screenshots/0.9.0/health-connect-card.png) ·
[Entry in Health Connect](docs/screenshots/0.9.0/health-connect-entry.png) ·
[What's shared](docs/screenshots/0.9.0/health-connect-privacy.png).

0.8.1: [Small screen, before](docs/screenshots/0.8.1/before-small-text.png) ·
[after](docs/screenshots/0.8.1/after-small-text.png) (360dp, large text).

0.8.0: [Last time's setup](docs/screenshots/0.8.0/last-time-setup.png) ·
[Setup form](docs/screenshots/0.8.0/setup-form.png) · [Machine settings](docs/screenshots/0.8.0/machine-settings.png).

0.7.0: [Fix the machine](docs/screenshots/0.7.0/fix-machine.png) ·
[Moved sets](docs/screenshots/0.7.0/moved-sets.png) · [Edit the machine](docs/screenshots/0.7.0/edit-machine.png).

0.6.1: [Pinned person chips](docs/screenshots/0.6.1/pinned-people.png).

0.6.0: [Starting weight](docs/screenshots/0.6.0/starting-weight.png) ·
[Plate math](docs/screenshots/0.6.0/plate-math.png) ·
[Past weights](docs/screenshots/0.6.0/past-weights.png).

0.5.0: [Editing a session](docs/screenshots/0.5.0/editing-session.png) ·
[Change date](docs/screenshots/0.5.0/change-date.png).

0.4.0: [Backup & restore](docs/screenshots/0.4.0/backup-screen.png) ·
[Import preview](docs/screenshots/0.4.0/import-preview.png).

See
[verification notes](docs/VERIFICATION.md) for coverage and remaining checks.

## Project structure

- `Models.kt`: workout domain and validation.
- `WorkoutStore.kt`: Room persistence and serialized mutations.
- `IronIndexViewModel.kt`: screen actions and reactive state.
- `MainActivity.kt`: Compose screens and theme.
- `WorkoutUi.kt`, `ManagementUi.kt`, `ReorderUi.kt`: logging, catalogs and ordering.
- `Analytics.kt`, `AnalyticsUi.kt`: metric definitions and progress screens.
- `Backup.kt`: backup file format, validation and the import merge rules.
- `Load.kt`: starting weights, totals, plate math and re-basing typed weights.
- `Setup.kt`: machine setup settings, per-person values and "last time" lookups.
- `ExerciseMerge.kt`, `ExercisesUi.kt`: duplicate detection, merging, and the Exercises screen.
- `FitText.kt`: one-line labels that shrink to fit instead of wrapping on small screens or large text.
- `HealthSession.kt`, `HealthSync.kt`, `HealthPrivacyActivity.kt`: what is shared with Health Connect, the
  sync (only new/changed sessions; removals), and the explanation Health Connect links to.
- `app/schemas`: Room schema history; commit schema changes with migrations.
- `.githooks/`, `scripts/install-hooks.sh`: release hygiene and doc-drift checks.
- `docs/ROADMAP.md`, `docs/brainstorms/`, `docs/archive/`: backlog, live and shipped designs.
- `docs/PRODUCT.md`: first-version scope and future Discipline integration.

Data is stored on this device. Uninstalling clears it, so export a backup first (or rely
on Android auto backup when device backup is on). Cloud sync is not implemented. This is a development build, not a signed store release.
No Discipline credentials or source files are required. Source lives in the private
GitHub repo `jimmyfnc/iron-index`; releases are tagged `vX.Y.Z`.

## Contributing

Run `./scripts/install-hooks.sh` once per clone. The pre-commit hook blocks app changes
without a version bump and CHANGELOG entry, catches version drift in README/CHANGELOG,
destructive Room migrations, unexported schemas, and committed secrets. See AGENTS.md.

Upgrading from 0.1.0 preserves the local database through a tested Room migration.
Install the new APK over the existing installation; do not uninstall first. Existing
combined brand/model text is retained verbatim because it cannot be safely split.
