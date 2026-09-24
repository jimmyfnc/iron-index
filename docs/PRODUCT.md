# IronIndex — product overview

IronIndex is a standalone Android workout journal. The working name can change.
It lives independently of Discipline; neither app needs the other installed.

## The experience

- Start a freestyle workout immediately and build it at the gym.
- Add a known exercise or create a custom one without leaving the workout.
- Record reps and weight, then check off each completed set. Duplicate the last
  set's targets with Add set. Support zero external weight for bodyweight moves.
- Capture equipment type, machine brand/model, and an optional muscle-group label.
- Save a workout as a reusable routine during or after the session.
- Create/edit routines ahead of time, review completed sessions, correct them afterwards
  (values, sets, exercises, date) with discard, and repeat them.
- Automatically persist partial input and unfinished sessions on the device.
- Use lb or kg; each routine/session retains its own unit.
- Reorder exercises with drag handles in a compact sheet.
- Maintain equipment profiles with brand, model and optional gym/nickname.
- Attach local partners and independently log each person's sets. Exercises mirror to
  everyone by default; each person can skip entries or get partner-only exercises.
- Keep partner goals and notes, and review each partner's sessions, records and progress.
- Record Left / Right separately for single-arm or single-leg exercises.
- Capture primary/secondary muscles and targeting notes.
- Browse colored muscle tags and filter by broad groups or specific targets. Search
  includes secondary muscles and custom names; Arms includes Biceps and Triceps.
- Export a complete backup file and import it on any phone (merge by ID, never delete);
  Android auto backup covers the database and settings.
- Compare previous sessions and review progress charts, PRs, volume, frequency,
  muscle counts and matched-side discrepancies.

An exercise is a movement, independent of equipment. Workout entries connect an exercise
to the equipment used: the same Biceps curl ID can appear with dumbbells, cables, and
multiple machines, including within one workout. Equipment is reusable across exercises.
Routines preserve these pairings; previous-session references and progression remain
equipment-specific. Legacy default equipment is a compatibility hint, not a restriction.

## Technical choice

Native Kotlin + Compose aligns with the existing Discipline Android app, supports
native keyboards and accessibility, and avoids a web wrapper. Room is the local
source of truth, following Android's offline-first guidance:
https://developer.android.com/topic/architecture/data-layer/offline-first

Room stores each workout as a serializable aggregate in a single row, with independent
equipment and partner catalog tables added in schema 2.
Mutex-serialized read/modify/write operations prevent overlapping set edits from
overwriting each other. This deliberately small first version can normalize the
tables when the volume of history warrants indexed analytics queries. Current
analytics use pure, tested projections of completed sessions.

## Visual direction

Dark ink surfaces with bright lime calls to action, lavender supporting details,
and warm coral highlights. Rounded cards, bold native typography, generous touch
targets, and four bottom tabs: Train, Routines, History, Progress. No login or onboarding
blocks the first workout. No fabricated progress or sample sessions.

## Future Discipline integration

Workouts, exercises and sets have stable UUIDs, explicit weight units, and session
timestamps. Exercise details are snapshots so future edits cannot rewrite history.
Discipline's existing workout_sets have session/exercise IDs, reps, and weight,
but an adapter will need explicit unit conversion and additional equipment fields.

Before sync: agree on ownership, authentication, remote IDs, per-record modification
tracking, deletion tombstones, and a conflict policy. Current updatedAt is local
metadata, not a complete sync protocol. Do not connect either production database
until a migration and import/export contract have been reviewed.

## Later

1. Rest timer and a richer curated exercise library.
   Coach mode: separate concurrent workouts per client, started from any routine.
2. Larger-history analytics optimization.
3. Discipline sync, optional account and cloud backup.
4. Release signing, store assets, localization, and broader device testing.

See [analytics definitions](ANALYTICS.md) for calculation and migration semantics,
and [the field-test design](archive/2026-09-23-field-test-upgrade.md) for 0.2.0.
