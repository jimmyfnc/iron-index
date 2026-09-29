# Changelog

## 0.9.0 - 2026-09-29

- **Share workouts with Health Connect.** Turn it on in Train → Backup and each finished session is saved to Health Connect, so apps like Discipline and Samsung Health can see you trained. It's off until you switch it on and allow it.
- Only the outline is shared: the session's name, start and end time, and "strength training", plus a note like "5 exercises · 18 sets". Your sets, weights, machines, setup and partners stay in IronIndex, and IronIndex never reads anything from Health Connect.
- Edit a session or move its date and the shared copy updates; delete it and it's removed. Sessions where only partners logged sets aren't shared.
- The card shows when it last shared, or why it couldn't, and has a button to open Health Connect if you need to allow it again.

## 0.8.1 - 2026-09-29

- **Words no longer break in half on smaller phones or with bigger text.** The home screen's Partners / Equipment / Backup buttons, the lb / reps boxes on each set, text-box labels and stat numbers stay on one line and shrink slightly to fit instead of wrapping ("Equipme / nt").
- The weight and reps boxes are a little slimmer inside, so the number has room beside the checkbox.
- Setup lines wrap only between whole pieces, so a date never splits across lines.
- In exercise details, Edit moves under Choose equipment on narrow screens instead of being cut off at the edge.
- Picking a machine no longer pops the keyboard up over the form.

## 0.8.0 - 2026-09-28

- **Machine setup, remembered.** Give a machine its settings, such as **Seat height** on a chest press or **Attachment** and **Pulley height** on a cable stack (suggested from the machine type), in Train → Equipment, or add one straight from a workout.
- Each exercise card shows a **Setup** line for whoever you're logging. If you haven't set it yet today, it shows what that person used last time on this machine for this exercise, with the date. Tap **Same** to keep it, or tap the line to change it. Past values and common attachments (rope, V-bar, straight bar…) are one tap away.
- Everyone keeps their own setup: Alex's seat height never overwrites yours. Setup is saved with each session, shows in History, and travels in backups.

## 0.7.0 - 2026-09-28

- **Picked the wrong machine? Fix it after logging.** In a workout's exercise details, **Choose equipment** now works even after you've checked sets. Your logged sets move to the right machine with the weights you typed, and totals, PRs and history follow that machine. If its starting weight changes what your numbers mean, a note tells you. (Switched machines partway through? Add another entry instead.)
- **Edit a machine without leaving your workout.** Exercise details has an **Edit** button next to the machine: fix its name, brand, model or gym, or set its starting weight, right there.
- Fixing a machine's name now shows everywhere it's used, past sessions included. Past sessions still keep the starting weight they were logged with unless you choose otherwise.
- Training with partners? The details screen says whose logged sets move with the machine, since everyone shares the entry.
- Choosing a machine can no longer undo a starting weight you edited moments before, and adding a starter exercise no longer resets its machine's starting weight.

## 0.6.1 - 2026-09-28

- **Switch people from anywhere.** When partners are tracking sets, the You / partner chips stay pinned to the top of the workout as you scroll, so you can hand off between people without scrolling back up.

## 0.6.0 - 2026-09-24

- **Starting weights on equipment.** Give a bar, sled or carriage its starting weight (quick picks: 45, 55, 35, 25 lb; 20, 15 kg). Equipment can now be edited, not just added or archived.
- **Choose how you log it.** *Total weight* (default for bars): type 225 and the set shows "45 bar + 90 per side (45 + 45)". *Plates only* (suggested for leg press, hack squat, Smith, sled machines): type 180 and it shows "+167 sled = 347 lb total".
- Charts, PRs, volume, estimated 1RM and history use the total, so your numbers mean the same thing whichever way you log. What you typed is kept.
- Changing a machine's starting weight or logging style after you've used it asks how past weights were typed. *Plates only* counts the sled in them too; *Keep totals* leaves history exactly as logged. Routines and the workout in progress adjust their numbers to keep the same totals.
- Starting a routine picks up the machine's current setup, and backups keep each session's load settings.

## 0.5.0 - 2026-09-24

- **Edit finished sessions.** Open a session from History and tap **Edit session** to fix a mistyped weight, check a set you forgot, add or remove sets and exercises, or move it to the right day with **Change date** (time of day and duration kept, never in the future).
- **Done editing** marks the session "edited". **Discard changes** puts its sets, exercises and date back exactly as they were. Leaving a session with no checked sets keeps the original, so history can't be emptied by accident. An edit interrupted by the app closing is wrapped up safely on the next launch.
- Correcting a checked set no longer unchecks it, whether mid-workout or in edit mode. A set that's briefly blank while you retype just doesn't count until it's valid again.
- The backup reminder counts sessions you've edited or moved since your last backup.

## 0.4.0 - 2026-09-24

- **Backup & restore** (Train → Backup). Export saves every session, routine, partner, machine and custom exercise to one JSON file wherever you choose: Drive, Downloads, email.
- Import shows exactly what it will add before writing anything. It merges by ID and never deletes, so importing the same file twice changes nothing. A workout edited more recently in the file replaces the older copy, but an unfinished copy never replaces a finished session.
- Machines and custom exercises that already exist on the phone are linked, not duplicated, so their history stays in one chart.
- An unfinished workout in a backup is restored only onto an empty phone, so a draft you discarded can't come back. The preview warns that anything deleted since the backup will return.
- Damaged, foreign, oversized (15 MB+) or newer-version files are rejected with a clear message and nothing is written. Files saved by desktop editors with a byte-order mark are accepted.
- Android auto backup is now on for your workouts and settings: it saves to your Google account and moves to a new phone when device backup is enabled.
- A home-screen reminder appears when finished sessions haven't been backed up for two weeks.

## 0.3.4 - 2026-09-24

- A damaged or unreadable saved record is skipped instead of crashing the app; everything else still loads.
- Typing in a workout stays quick as history grows: saved data is decoded off the main thread, and "Last time" / history are built once per person from finished sessions instead of per card on every keystroke.
- Screen readers announce each weight and reps field with its exercise, set number and side (for example "Bench press set 2 Left weight in kg").

## 0.3.3 - 2026-09-24

- Renaming an exercise inside a workout now asks whether it's the same movement or a different one. "Different exercise" starts its own history instead of renaming the library exercise and merging both histories into one chart and PR list. Capitalization and spacing fixes don't ask.

## 0.3.2 - 2026-09-24

- Starting a routine or repeating a session while another workout is in progress now asks: resume it, or discard it and start the new one. An empty draft is replaced automatically instead of hijacking the start.
- A session's date is set when you check your first set, so a draft started days earlier no longer lands on the wrong day in History, Progress and "Last time".
- Switch lb/kg inside a workout until any weight is entered. New installs default to kg outside the US, Liberia and Myanmar.
- Weights typed with a decimal comma ("22,5") are accepted.
- History cards count partner sets ("3 sets + 2 partner"), and the home card no longer offers to resume an empty draft.

## 0.3.1 - 2026-09-24

- Partner-only and skipped exercises no longer appear for partners who join later or rejoin.
- Detaching a partner removes exercises nobody else is doing instead of leaving empty entries in history.
- Skipping an exercise now just hides it for that person; adding it back restores their routine targets. No confirmation needed.
- Back from Progress returns to the partner profile it was opened from.
- Screen readers announce which exercise each "Add for …" button restores; profile stats wrap at large text sizes.
- The partner editor keeps your typing when the phone rotates.
- Wording: "1 exercise", and the finish dialog only mentions partner sets when partners are tracked.

## 0.3.0 - 2026-09-24

- Per-person workouts: new exercises still mirror to everyone, but any card can be skipped for the person in view, and adding while a partner chip is selected creates a partner-only exercise. Skipped entries collapse to an "Add for …" row.
- Partner details: optional goal/focus and notes (injuries, cues). Shown above the sets while logging for that person.
- Partner profiles: sessions, sets, volume, last trained, recent records and a session list. Sessions open with that partner selected; Progress charts open preselected to them.
- Finished-session headers show the selected person's sets and volume. Counts use singular/plural wording.
- Repeat/routine copies drop exercises you skipped. Stored as optional JSON fields; no Room schema change or migration.

## 0.2.2 - 2026-09-23

- Colored muscle tags on exercise results and workout cards, with explicit secondary labels.
- Tappable muscle filters and search across primary/secondary targets, broad groups, and custom muscle names.
- Starter curls and pushdowns use Biceps and Triceps; Arms still finds both. Existing workout snapshots stay unchanged.


## 0.2.1 — 2026-09-23

- Choose equipment explicitly after selecting an exercise. One exercise can be logged with dumbbells, cables, or any machine without creating duplicate exercises.
- Previously used equipment appears first; routines retain each entry’s equipment and histories remain equipment-specific.
- Exercise search describes movements and muscles instead of implying a single machine defines the exercise.


## 0.2.0 — 2026-09-23

- Drag exercise ordering with auto-scroll and accessible up/down controls.
- Independent equipment catalog with brand, model and optional gym/nickname identity.
- Partner Manager, multi-partner attendance, and optional independent set tracking.
- Single-arm/single-leg modes with separate left/right inputs and completion.
- Primary and secondary muscles plus specific targeting notes.
- In-workout prior-session references and equipment/person-specific exercise history.
- Progress charts for weight, reps, estimated 1RM, volume and relative performance.
- Weekly/monthly volume, muscle counts, frequency, equipment-specific PRs and paired
  left/right comparisons, with explicit counting and calculation rules.
- Non-destructive Room 1→2 migration preserving existing workout data and equipment text.
- Expanded migration, gesture, participant, catalog, analytics and regression tests.

## 0.1.0 — 2026-09-22

- Initial standalone Android workout tracker.
- Offline workout drafts, set logging, equipment details, routine creation and history.
- Vibrant native Compose interface and future Discipline integration notes.
