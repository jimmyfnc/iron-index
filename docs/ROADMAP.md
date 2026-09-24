# IronIndex roadmap

Active ideas ranked by effort. Live designs go in `docs/brainstorms/`; shipped designs move
to `docs/archive/` (never delete). Add or remove a row whenever a brainstorm is added or shipped.

Effort: 🟢 small (hours) · 🟡 medium (a day or two) · 🔴 large (multi-day) · ⚫ needs a decision first

## Next

| Idea | Effort | Notes |
|---|---|---|
| Rest timer after checking a set | 🟡 | Per-exercise default, notification when done. |
| Backup export / import (JSON file) | 🟡 | Uninstalling currently loses all data. |
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
| Dependency updates flagged by lint (10 warnings) | 🟢 | Keep aligned with the Discipline stack. |
| Normalize Room tables when history grows | 🔴 | Current aggregate-per-row design is intentional for now. |
