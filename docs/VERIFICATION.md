# IronIndex verification

## 0.3.4 - 2026-09-24

- assembleDebug, all 33 unit tests, lintDebug (zero errors, same 10 dependency warnings and one hint) and all 12 connected tests passed on emulator-5554.
- New unit coverage: malformed JSON and unknown enum values are skipped and reported while valid rows load. The extracted earlier-session helpers match the previous behavior.
- The existing UI tests still find weight/reps fields by label with the added accessibility descriptions.

## 0.3.3 - 2026-09-24

- assembleDebug, all 31 unit tests, lintDebug (zero errors, same 10 dependency warnings and one hint) and all 12 connected tests passed on emulator-5554.
- New UI test: renaming Bench press inside a workout keeps Save disabled until a choice is made. "Different exercise" gives the entry a new ID while the library Bench press and past sessions keep their name and ID. The unit test covers case/space-only edits not prompting.

## 0.3.2 - 2026-09-24

- assembleDebug, all 30 unit tests, lintDebug (zero errors, same 10 dependency warnings and one hint) and all 11 connected tests passed on emulator-5554.
- New coverage: an empty draft is replaced by a routine start, a draft with exercises is kept until "Discard and start", and the session date stamps once at the first checked set (store test). The UI test covers the Resume / Discard dialog, the in-workout kg switch, "22,5" saved as 22.5, and chips hiding once a weight exists. Unit tests cover comma parsing, the unit lock, start stamping and the regional default unit.
- Fixed the flaky picker step in WorkoutFlowTest by dismissing the keyboard before tapping the result.

## 0.3.1 - 2026-09-24

- Fixes from a code review of 0.3.0. assembleDebug, all 27 unit tests, lintDebug (zero errors, same 10 dependency warnings and one hint) and all 9 connected tests passed on emulator-5554.
- New unit coverage: late-attached partners don't inherit partner-only/skipped work, detaching removes orphan entries, hidden values don't block detach, include requires a tracked partner, skip/re-add restores targets.
- The partner androidTest now also checks Back from Progress returns to the profile and that sessions opened from a profile select that partner.
- WorkoutFlowTest.logSaveResumeFinishAndRepeatWorkout timed out once in a full run, then passed twice alone and again in a full rerun. It's a flaky IME/picker timing issue, tracked in ROADMAP.
- Manual emulator check: skipping hid the exercise for you, and "Add … for you" restored the entered 185 lb.
- Note: connectedDebugAndroidTest uninstalls the app from the emulator afterward, clearing emulator data (never a phone's).

## 0.3.0 - 2026-09-24

- assembleDebug, all 23 unit tests, and lintDebug passed (zero errors; 10 dependency warnings and one hint, unchanged).
- All 9 connected tests passed on emulator-5554 (API 36.1), including a new partner flow: goal/notes shown on the chip view, skip and re-add per person with persistence checks, and a profile with history.
- New unit coverage: mirrored versus partner-only entries, skip/include, completed-set guard, mode-change rebuild, repeat filtering, legacy JSON defaults, per-person summaries.
- Manual emulator run on a fresh install: created a partner with goal/notes with the keyboard open, attached them with tracking, added a partner-only exercise, logged and finished, opened the profile, reopened the session with the partner selected, and opened Progress preselected. Fixed two issues from this run: the finished-session header showed your totals on a partner's view, and counts read "1 sets".
- Screenshots: screenshots/0.3.0/ (synthetic emulator data). VersionName 0.3.0, versionCode 5. No Room schema change. Physical phone not connected.

## 0.2.2 - 2026-09-23

- assembleDebug and all 15 unit tests passed. lintDebug: zero errors, 10 dependency warnings and one hint.
- All eight Android instrumented tests passed on emulator-5554, including muscle-chip selection, switching/clearing filters, broad-group search and empty results.
- Unit coverage verifies secondary/custom targets, case/whitespace normalization, broad versus specific filtering and legacy muscle labels.
- Visually checked the colorful tags on the emulator. Screenshots: screenshots/0.2.2/muscle-tags.png and arms-filter.png.
- VersionName 0.2.2, versionCode 4. Existing saved workout snapshots are unchanged. Installed on emulator only; physical phone unavailable.


## 0.2.1 - 2026-09-23

- assembleDebug, testDebugUnitTest (12 tests), and lintDebug passed. Lint reports zero errors, 10 dependency warnings and one hint.
- All seven connected Android tests passed on emulator-5554 (API 36.1).
- New UI regression adds the same Biceps curl exercise with dumbbells and cables, confirms shared exercise identity and distinct equipment references, and saves/reloads/repeats a routine preserving both pairings.
- Existing logging lifecycle now exercises the explicit equipment selection step.
- VersionName 0.2.1, versionCode 3. No database migration needed; existing workout entries already reference independent exercise and equipment identities.
- Physical phone was not connected; this build has not been installed on the phone.


## 0.2.0 — 2026-09-23

- `assembleDebug`: passed; versionName 0.2.0, versionCode 2.
- `testDebugUnitTest`: 12 tests passed, including equipment/person separation, lb/kg
  conversion, eligible estimates, calendar buckets, primary/secondary attribution,
  L/R pairing, repeat identity and reorder preservation.
- `connectedDebugAndroidTest`: six tests passed on the Android 16 / API 36.1 emulator:
  actual long-press drag; independent partner/L/R logging; equipment catalog and chart
  UI; schema 1→2 migration; persistence/concurrent edits; original logging lifecycle.
- UI tests use isolated databases and do not clear a user's workout database.
- Migration fixture checks the original workout/set IDs, weight, reps, completion,
  dates, units, bilateral/self defaults, and preservation of combined brand/model text.
- Final APK build and lint also passed after visual refinements. Lint has no errors;
  remaining warnings concern available dependency updates.
- Visually inspected logging, reorder, weekly volume, muscle counts, and progression
  with synthetic emulator-only data. Simplified metadata/history layout to keep the
  paired L/R inputs close together. No sample data ships in the APK.
- Screenshots: `docs/screenshots/0.2.0/`. They contain synthetic preview workouts.
- No physical phone was connected during this upgrade; no phone installation occurred.

The app retains offline/local partner profiles. Cross-phone sharing, cloud backup,
Discipline sync, and broader physical-device/TalkBack testing remain future work.

## 0.1.0 baseline

Verified locally on 2026-09-22.

## Automated

- `assembleDebug`: passed; installable debug APK produced.
- `testDebugUnitTest`: four tests passed (numeric validation, repeat-session identity
  and equipment preservation, completed-set volume, partial-input serialization).
- `connectedDebugAndroidTest`: two tests passed on Medium Phone API 36.1 / Android 16.
  - UI: start, add exercise, enter weight/reps, complete set, leave/resume, save routine,
    finish, view history, repeat with targets retained and completion cleared.
  - Persistence: reopen database, preserve equipment, serialize 12 concurrent edits,
    resume existing workout, repeat while retaining its weight unit. Uses an isolated
    temporary database so UI test sessions cannot affect the assertions.
- `lintDebug`: zero errors; 11 dependency update warnings and one hint. Runtime
  dependency versions intentionally align with the existing Discipline stack.
- After the final status-bar contrast correction, APK build and lint passed again.

## Manual emulator checks

- Create custom Machine press, Plate-loaded chest press equipment, Hammer Strength brand.
- Enter 90 lb × 12 reps using the numeric keyboard and mark the set complete.
- Force-stop and replace the APK, relaunch, resume: values, equipment, brand, and
  completion persisted. Assertions checked the accessibility hierarchy.
- Inspected actual screenshots of the home, session, and keyboard screens.
- Inspected session layout at 375dp width with 1.3× and 2× font scale and a landscape
  viewport; restored emulator size, density, and font scale afterward.
- Corrected system bars to keep icons visible against the dark background.

Screenshots are in `docs/screenshots/`. The workout shown is test data on the emulator;
the app does not seed completed workouts or fake progress on a fresh install.

## Remaining before a public release

Physical-device testing, TalkBack walkthrough, full tablet coverage, localization,
backup/export, release signing and store preparation. Cloud/Discipline sync is deferred.
Dark mode is the current design; there is no separate light theme yet.
