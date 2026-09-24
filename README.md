# IronIndex

A colorful, standalone Android workout tracker. Built with Kotlin, Jetpack Compose
and Room. Works offline with no account required. Current version: **0.3.0**.

## Included

- Freestyle workout logging with automatic draft recovery.
- Custom exercises, reps, weight, checked sets, equipment, brand/model, optional muscle group.
- Reusable routines: create ahead of time or save from a live/completed workout.
- Workout history and repeat-session flow.
- Per-workout lb/kg units and starter exercise library.
- Dark ink, lime, lavender and coral interface.
- Drag-and-drop exercise ordering via Reorder, with accessible up/down alternatives.
- Equipment library: separate brand, model, and optional gym/nickname.
- Partner Manager with goals, notes, history and stats; independent sets and per-person exercise lists.
- Single-arm/single-leg logging with independent Left / Right values.
- Primary/secondary muscles and isolation notes; inline previous-session references.
- Progress charts, weekly/monthly analytics, machine-specific PRs and L/R comparisons.

## Using the new features

- **Train → Equipment**: create machine profiles. Distinct brand/model/location profiles
  get separate histories. Archive retired equipment without removing past sessions.
- **Train → Partners**: add profiles with an optional goal and notes. **History & stats**
  shows a partner's sessions, records and totals, and links to their Progress charts.
- **Inside a workout**: **Partners** attaches people and optionally enables their sets;
  person chips switch logging. New exercises mirror to everyone. **Skip for …** removes
  one person from an exercise; adding while a partner chip is selected adds it only for them.
- **Exercise details button**: select equipment, muscles and side mode. Change side mode
  before entering values. To switch machines after completing sets, add another entry.
- **Reorder**: hold and drag a handle in the order sheet, or use the arrows.
- **Exercise history**: see prior completed sessions for that person and machine.
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

See
[verification notes](docs/VERIFICATION.md) for coverage and remaining checks.

## Project structure

- `Models.kt`: workout domain and validation.
- `WorkoutStore.kt`: Room persistence and serialized mutations.
- `IronIndexViewModel.kt`: screen actions and reactive state.
- `MainActivity.kt`: Compose screens and theme.
- `WorkoutUi.kt`, `ManagementUi.kt`, `ReorderUi.kt`: logging, catalogs and ordering.
- `Analytics.kt`, `AnalyticsUi.kt`: metric definitions and progress screens.
- `app/schemas`: Room schema history; commit schema changes with migrations.
- `.githooks/`, `scripts/install-hooks.sh`: release hygiene and doc-drift checks.
- `docs/ROADMAP.md`, `docs/brainstorms/`, `docs/archive/`: backlog, live and shipped designs.
- `docs/PRODUCT.md`: first-version scope and future Discipline integration.

Data is stored only on this device. Uninstalling clears it; backup export and cloud
sync are not implemented yet. This is a development build, not a signed store release.
No Discipline credentials or source files are required. Source lives in the private
GitHub repo `jimmyfnc/iron-index`; releases are tagged `vX.Y.Z`.

## Contributing

Run `./scripts/install-hooks.sh` once per clone. The pre-commit hook blocks app changes
without a version bump and CHANGELOG entry, catches version drift in README/CHANGELOG,
destructive Room migrations, unexported schemas, and committed secrets. See AGENTS.md.

Upgrading from 0.1.0 preserves the local database through a tested Room migration.
Install the new APK over the existing installation; do not uninstall first. Existing
combined brand/model text is retained verbatim because it cannot be safely split.
