# IronIndex field-test upgrade

## Scope

Reorder exercises by dragging; identify equipment by type/brand/model and optional
gym or nickname; manage and attach local training partners; log unilateral left/right
sets independently; record primary/secondary muscles and specific targeting notes;
show equipment/person-specific history; provide charts and training analytics.

## Decisions

- Keep the native offline app and existing visual palette.
- Equipment is an independent catalog entity with a stable UUID. Workout entries
  retain its ID and a snapshot; archived machines remain in history. A completed
  entry cannot silently switch machines—add another entry instead.
- Local partner profiles are the default. Attach multiple people, optionally enable
  their set logging, and switch person chips inside the workout. Do not mix partner
  sets into the user's totals. New sessions/routines do not assume prior attendance.
- Preserve bilateral sets as recorded. Unilateral rounds contain separate left and
  right entries with a shared round ID. No implicit doubling, copying one side's
  completion, or converting existing weights to left/right values.
- Match history and PRs by exercise ID, equipment ID, exercise mode, side, and person.
- Graph best weight, best reps, estimated 1RM, volume, and estimated-strength index.
  Normalize weight units before aggregation. Do not treat missing estimates as zero.
- Show weekly/monthly volume, frequency, primary and secondary muscle counts, records,
  progression, and matched-round left/right comparisons. Define each metric in the UI.
- Drag in a compact reorder sheet with auto-scroll and accessible up/down buttons;
  keep text entry and workout scrolling free of drag gestures.

## Existing data

Room schema 1 → 2 adds equipment and partner tables without dropping workouts.
Migration links old equipment text to deterministic equipment IDs. Keep the old
combined brand/model string intact rather than guessing a split. Existing IDs, weights,
reps, completion flags, units, and timestamps remain unchanged. Missing side means
bilateral and missing participant means the app's owner.

## Alternatives considered

Keeping equipment in names would make comparisons fragile. A full cloud/team system
would add accounts and sync before the logging flow is settled. Independent local
catalogs and snapshots meet this release's needs while preserving stable references
for future Discipline synchronization.

## Defaults awaiting user preference

Local partner entry on one phone and optional gym/nickname identity. Cross-phone sync
is not part of this implementation unless explicitly requested.

## Validation

Domain tests for units, identities, estimates, muscle counts, L/R pairing, and order;
schema migration instrumentation test; actual long-press drag and participant input
UI tests; existing start/save/resume/finish/repeat regression flow; emulator review.
