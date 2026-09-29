# IronIndex verification

## 0.8.1 - 2026-09-29

- assembleDebug, all 70 unit tests, lintDebug (zero errors, same 10 dependency warnings and one hint) and all 33 connected tests passed on emulator-5554 with ANDROID_SERIAL pinned (the owner's phone was attached and untouched by tests).
- New connected tests (SmallScreenTest) render the app at 360dp with 1.3× text and at 340dp with normal text, and check that the home buttons, the plates lb / lb / reps labels and a two-digit set number stay on one line without clipping. A deliberate check with the old plain "Equipment" label failed it (2 lines), so the test catches the original bug. A first attempt at 320dp showed the set row can't fit there at all; 340–360dp is the supported floor.
- Audit on the emulator at 360dp and 1.3× text across Home, Routines, History, Progress, Partners, partner profile, Equipment, Backup, the workout and its dialogs. Before: "Partn/ers", "Equip/ment", "Back/up", "rep/s", "plate/s lb", "1,080./0", clipped Edit button and a split setup date. After: all on one line or wrapping between whole pieces.
- Keyboard: after choosing a machine through the picker's search, the keyboard stays closed (mInputShown=false); tapping the exercise name right after opening details still opens it.
- The owner reported the phone's "Equipment" button wrapping (384dp, normal text) and the picker popping the keyboard (a swipe then typed into the exercise name, cancelled without saving while fixing their data).
- A code review found the first focus fix running in the wrong window, long "Add …" setup prompts made unbreakable, two-digit set numbers, the field offset not scaling with text size, red value text unlike stock fields, and "Routines" squeezed beside "Your next session". All fixed before release.

## 0.8.0 - 2026-09-28

- assembleDebug, all 70 unit tests, lintDebug (zero errors, same 10 dependency warnings and one hint) and all 31 connected tests passed on emulator-5554 with ANDROID_SERIAL pinned (no phone attached). The first full run had one failure: an older partner test scrolled back to the title to reach the You chip, which failed once the taller card pushed it away; the chips are pinned since 0.6.1, so the step now taps them directly.
- Unit coverage (SetupTest): each person keeps their own values and blanks are dropped; last setup is that person's most recent recorded one; machine setting order with recorded extras; values recorded under another capitalization still show; re-saving unchanged setup leaves the entry untouched; type suggestions (cable, seated, leg press; none for dumbbells, barbell, Smith, T-bar); routines/repeats clear setup; detaching a partner removes theirs; older saved rows decode with no setup.
- Connected coverage: a card shows "Setup · last time" from a finished session, Same records it, the form switches Rope → V-bar via a chip and adds Pulley height 12, which is saved on the entry and added to the machine; Alex's setup stays empty.
- No Room schema change: setup and settings live in the existing JSON payloads (defaults keep older rows and backups readable).
- Follow-up (test-only, 2026-09-29): the connected UI tests wrapped the app in the default light Material colors, so while they ran on the emulator some cards showed dark text on dark surfaces. The shipped app always uses its own dark scheme (light and dark system mode screenshots were identical). Tests now use IronIndexColors; all 31 connected tests passed again with the emulator in light mode after a cold restart (one earlier run aborted with the emulator's "System has crashed").
- Manual emulator run: imported a seeded backup with last week's lat pulldown (you: Wide bar, Alex: V-bar). Each person saw their own "last time" setup; changed yours to Rope + Pulley height 12 in the form; Same for Alex kept V-bar; the Equipment screen then listed "Setup · Attachment · Pulley height", and its editor shows the settings chips.
- A code review found values hidden (and erased on save) when a setting's capitalization differed, removed settings coming back from a save, snapshot rewrites for name-only setting edits, a false "edited" stamp on re-saving the same setup, a promise to save settings to a machine not in the catalog, Seat height suggested for Smith/T-bar, a disabled-button announcement on read-only rows and no keyboard action to add a setting. All fixed with tests before release.

## 0.7.0 - 2026-09-28

- assembleDebug, all 62 unit tests, lintDebug (zero errors, same 10 dependency warnings and one hint) and all 30 connected tests passed on emulator-5554 with ANDROID_SERIAL pinned (no phone attached).
- Unit coverage (LoadTest): a relabelled snapshot keeps the starting weight and logging style it was logged with; machine usage counts only workouts with typed weights.
- Connected coverage: a rename reaches a finished session's snapshot while its 167 lb plates-only load and 3,470 lb volume stay; switching a checked 180 × 10 set to another machine keeps it checked and typed, recomputes volume with the new sled and shows the notice, without touching the old machine; a picker's older copy never overwrites a newer starting weight; discarding a session edit keeps a machine rename made during it. UI: from inside a workout, Choose equipment after a checked set moves it ("Logged sets (you: 1) will move to …"), then Edit adds a model and the card shows the new name.
- Manual emulator run: imported a seeded backup, moved a checked 180 lb leg press set from the Hammer Strength (167 lb sled, plates only) to the Life Fitness leg press; the set stayed checked at 180, the hint changed to "Weight as logged" and the "Weights kept as typed" notice appeared. Edit → Model "Signature" updated both the dialog and the card immediately.
- A code review found Discard restoring the old machine snapshot for the session being edited, a stale picker copy able to undo a just-saved starting weight (also closes an older path where re-adding a starter exercise could reset its machine), partner sets moving without saying so, the picked machine lost on rotation, name clashes closing the form, and "this machine" wording for Unspecified. All fixed with tests before release.

## 0.6.1 - 2026-09-28

- assembleDebug, all 60 unit tests, lintDebug (zero errors, same 10 dependency warnings and one hint) and all 25 connected tests passed on emulator-5554 with ANDROID_SERIAL pinned (no phone attached). The first full connected run had one timing failure in the existing plates-only logging test (a checkbox tapped before its value landed); it passed on a class rerun and in a second full run.
- New connected test: in a ten-exercise workout with a tracked partner, scrolled to the bottom, the You / Alex chips are still displayed and switching shows that person's cards in place.
- Manual emulator run: imported a seeded backup through the file picker, scrolled a long pair workout; the "Logging · You · Alex" bar stayed pinned under the status bar, tapping Alex switched every card without moving the scroll position, and the bar stayed visible with the number keyboard open while typing.

## 0.6.0 - 2026-09-24

- assembleDebug, all 60 unit tests, lintDebug (zero errors, same 10 dependency warnings and one hint) and all 24 connected tests passed on emulator-5554 with ANDROID_SERIAL pinned. The phone was separately upgraded to the finished 0.5.0 build from a clean v0.5.0 worktree at the owner's request; no tests ran on it.
- Unit coverage (LoadTest): plates-only totals in volume, analytics and PRs; total mode and plain equipment unchanged; lb/kg conversion; hint wording including plate breakdown, below-bar and unilateral cases; type-based suggestions; legacy equipment JSON; re-basing keeps every target's total; unusable starting weights ignored; import keeps logged load settings when linking to a phone machine.
- Connected coverage: the editor suggests plates only for a leg press and saves 167 lb; logging 180 shows "+167 sled = 347 lb total", stores "180", and counts 3,470 lb volume. Store tests: Keep totals leaves history and converts routines (347 → 180); Plates only counts the sled in past logs; starting a routine converts older targets; editing a machine into another's name is refused; exercise details never rewrite the catalog from a snapshot.
- Manual emulator run: 45 lb bar preset and plate math for a 225 bench ("45 bar + 90 per side (45 + 45)"); a leg press logged at 180, then a 167 lb sled added in the editor; the "How were past weights typed?" prompt appeared; Plates only made the past session 3,470 lb.
- A code review found a stale snapshot overwriting the catalog, routine targets being reinterpreted, backup import changing load modes, rename collisions merging machines, drafts not picking up edits, false PRs after adding a starting weight, a possible crash on zero starting weights, per-side wording and accessibility. All fixed with tests before release.

## 0.5.0 - 2026-09-24

- assembleDebug, all 50 unit tests, lintDebug (zero errors, same 10 dependency warnings and one hint) and all 18 connected tests passed on emulator-5554, with ANDROID_SERIAL pinned while the physical phone was attached (the phone stayed on 0.4.0 and was untouched). The emulator was cold-restarted after one "System has crashed" run abort (a host/emulator issue; a clean rerun passed).
- New connected tests (EditSessionTest): backspace-and-retype on a checked set, then checking a forgotten set, Done → "edited" and dates unchanged; Discard restores the stored session exactly and the on-screen field reloads; system Back with no checked sets keeps the original.
- New unit tests: movedTo keeps time of day and duration and is capped at now; content comparison ignores bookkeeping; a checked-but-invalid set doesn't count. Also fixed a pre-existing flaky serialization test to use the storage encoder.
- Manual emulator run with real keyboard input found two races the harness didn't hit: typing through blank unchecked the set, and Discard left the field showing the discarded value. Both were fixed by making the checkbox user-only (sets count only when checked and valid) and syncing unfocused fields from the saved value, then rechecked on the emulator (225 kept checked; Discard shows 25).
- A code review found stale fields after Discard, the reminder undercount, future start times, unfinalized edits after process death, overpromising Discard wording and swallowed errors. All fixed.

## 0.4.0 - 2026-09-24

- assembleDebug, all 46 unit tests, lintDebug (zero errors, same 10 dependency warnings and one hint) and all 15 connected tests passed on emulator-5554 only. A physical phone connected during verification, so later commands were pinned with ANDROID_SERIAL and nothing was installed on it.
- Unit coverage: round trip; import into an empty phone and re-import changes nothing; newer-wins workouts; an unfinished copy never replaces a finished session; phone-wins catalog; drafts restored only onto an empty phone; equipment/exercise identity linking (including defaultEquipmentId) with idempotent re-import; damaged/foreign/future/ID-less files rejected; BOM accepted; older-shaped backup parsed; size limit.
- Connected coverage: export via the ViewModel, then import into a second database through the real UI (preview, Import, "Already up to date" on repeat) with exact equality of workouts, partner, equipment and custom exercise. Also: re-planning at apply time (a draft started after the preview keeps a single draft), and a damaged file shows an error and writes nothing.
- Manual emulator run: exported through the Android file picker to Downloads (951 bytes), **uninstalled the app**, reinstalled, imported through the picker, and the session and partner were restored. Importing onto a phone with data shows the deleted-items warning.
- Android Auto Backup: `bmgr backupnow` with the local transport succeeded; `pm clear` then `bmgr restore` brought the session back. The emulator transport was reset to Google's afterwards.
- A code review found an unfinished copy replacing a finished session, identity duplicates, discarded drafts returning, the memory ceiling, "wt" provider support and a misleading reminder after a restore. All were fixed before release, with tests. Deletions coming back from older backups is disclosed in the preview; tombstones would need a Room migration and are tracked in ROADMAP.

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
