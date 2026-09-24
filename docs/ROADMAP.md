# IronIndex roadmap

Active ideas ranked by effort. Live designs go in `docs/brainstorms/`; shipped designs move
to `docs/archive/` (never delete). Add or remove a row whenever a brainstorm is added or shipped.

Effort: 🟢 small (hours) · 🟡 medium (a day or two) · 🔴 large (multi-day) · ⚫ needs a decision first

## Next

| Idea | Effort | Notes |
|---|---|---|
| Fix workout start: "Start routine"/"Repeat" silently resume an old draft; session date is draft creation, not first set | 🟢 | E2E review 2026-09-24. Offer Resume / Discard / Replace. `WorkoutStore.start`. |
| Change lb/kg inside a workout before any set is logged; accept comma decimals ("22,5") | 🟢 | E2E review. Comma-locale keyboards can't log decimals today. |
| Edit finished sessions (fix a typo or an unchecked set) | 🟡 | E2E review. Finished sessions are read-only now. |
| Faster logging: prefill from last session, keyboard Next/Done that checks the set, pinned person chips, compact cards | 🟡 | E2E review gym-friction list. |
| Exercise identity: "Replace exercise" vs "Edit library exercise" (renames currently rewrite history grouping) | 🟡 | E2E review. `editDetails` saves over the library ID. |
| Rest timer after checking a set | 🟡 | Per-exercise default, notification when done. |
| Backup export / import (JSON file) | 🟡 | Uninstalling currently loses all data; manifest disables Android backup. |
| Coach mode: concurrent workouts per client | 🔴 | Start any routine for a partner without being a participant yourself. See 0.3.0 notes in CHANGELOG. |
| Richer curated exercise library | 🟡 | More starters with primary/secondary muscles. |

## Later

| Idea | Effort | Notes |
|---|---|---|
| Discipline sync / optional account / cloud backup | ⚫ | Needs ownership, IDs, tombstones and conflict policy. See docs/PRODUCT.md. |
| Cross-phone partner sharing | ⚫ | Depends on sync decisions. |
| Release signing, store assets, localization | 🔴 | Before any public release. |
| Physical-device + TalkBack walkthrough | 🟢 | Tracked in docs/VERIFICATION.md. |

## Maintenance

| Task | Effort | Notes |
|---|---|---|
| Performance: decode history off the main thread, compute per-card history once, debounce keystroke writes | 🟡 | E2E review. Will lag as history grows. |
| Robustness: skip unreadable saved rows instead of crashing the flow | 🟢 | E2E review. `WorkoutStore` decode has no error handling. |
| Stabilize flaky WorkoutFlowTest picker/IME timing | 🟢 | Seen once in 0.3.1 verification. |
| Brief empty Partner Manager flash when restoring a profile screen | 🟢 | Partners flow starts empty before Room emits. |
| Accessibility: set fields labelled only "lb"/"reps" | 🟢 | Add set number and exercise name to descriptions. |
| Dependency updates flagged by lint (10 warnings) | 🟢 | Keep aligned with the Discipline stack. |
| Normalize Room tables when history grows | 🔴 | Current aggregate-per-row design is intentional for now. |
