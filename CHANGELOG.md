# Changelog

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
