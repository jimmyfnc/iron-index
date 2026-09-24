# Training data and metric definitions

## Identity and storage

Equipment and partners have their own Room tables and stable UUIDs. Workout entries
reference equipment and preserve descriptive snapshots. Workouts preserve partner
attendance snapshots. Retiring a profile archives it; history is not deleted.

The exercise catalog remains distinct from equipment. One exercise may be performed
on many machines; the equipment selection belongs to the workout entry. Comparisons
match exercise ID, equipment ID, exercise mode, side and participant. Differently
named custom exercise IDs are not automatically merged. Unspecified equipment is its
own comparison category.

Schema migration 1→2 retains legacy combined brand/model text in the brand field and
uses a deterministic ID for the original type/brand combination. It does not invent
model details or assume that unspecified machines are equivalent to newly identified
machines. Existing sessions, routines and drafts retain their identifiers and values.

## Inclusion and units

Charts and records use valid, checked sets from finished workouts. Drafts, templates
and unchecked sets are excluded. "You" excludes every partner's sets. Numeric loads
are converted to kg internally (1 lb = 0.45359237 kg) and converted back to the selected
display unit. Session history shows its originally recorded unit.

Each unilateral side is an independent set; completing Left does not complete Right.
No implicit doubling of weights or reps occurs. A shared round ID pairs sides for
comparison. Bilateral historical sets remain bilateral.

## Metrics

- **Weight:** highest completed-set load in each session for the selected series.
- **Reps:** highest completed-set rep count; this does not imply equal load.
- **Volume:** sum of recorded load × reps. Bodyweight-only entries recorded with zero
  external load contribute zero external-load volume but still count toward reps/sets.
- **Estimated 1RM:** best Epley estimate per session, load × (1 + reps / 30), limited
  here to 1–10 reps with positive load. A single rep uses its recorded load. Values
  are estimates, not actual max attempts. Missing estimates are omitted, not zeroed.
- **Performance:** best eligible estimated 1RM relative to the first eligible session
  in the same series, with that first session indexed to 100%.
- **Weekly/monthly volume:** 12 calendar periods, including zeros. Weeks begin Monday;
  periods use the device's timezone and workout start date.
- **Muscle sets:** primary and secondary counts separately for the current period.
  A set can target multiple muscles. A muscle listed in both roles counts only as
  primary. Counts should not be summed into a unique whole-body set total.
- **Frequency:** unique sessions and logged sets by exercise/equipment in the period.
- **PRs:** all-time best weight, reps and eligible estimated 1RM for each series;
  these three records can come from different sets.
- **Left/right discrepancy:** matched completed sides from the same session, exercise
  entry and round. Absolute volume difference divided by the larger side's volume.
  Both weight/reps are shown; unmatched sides are excluded. Zero load has no volume
  percentage. Only the 20 latest matched pairs are shown in the summary.

Chart points are spaced by session/period and labeled with dates. The exact values
list provides a non-visual alternative to tapping chart points.

## Sources and limits

Epley's equation and the limitations of estimating maxima from repetitions are
discussed in [Developing Accurate Repetition Prediction Equations for Trained Older
Adults with Osteopenia](https://pmc.ncbi.nlm.nih.gov/articles/PMC11435939/).
These estimates are descriptive analytics, not load recommendations.

Database migration follows [Android's Room migration guidance](https://developer.android.com/training/data-storage/room/migrating-db-versions).
No destructive migration fallback is enabled. Partner/device synchronization and
Discipline integration remain future work.
