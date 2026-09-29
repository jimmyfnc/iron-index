# IronIndex roadmap

Active ideas ranked by effort. Live designs go in `docs/brainstorms/`; shipped designs move
to `docs/archive/` (never delete). Add or remove a row whenever a brainstorm is added or shipped.

Effort: 🟢 small (hours) · 🟡 medium (a day or two) · 🔴 large (multi-day) · ⚫ needs a decision first

## Next

| Idea | Effort | Notes |
|---|---|---|
| Faster logging: prefill from last session, keyboard Next/Done that checks the set, pinned person chips, compact cards | 🟡 | E2E review gym-friction list. |
| Merge duplicate custom exercises | 🟡 | Rename-vs-separate shipped in 0.3.3; merging existing duplicates is still manual. |
| Rest timer after checking a set | 🟡 | Per-exercise default, notification when done. |
| Write finished workouts to Health Connect | 🟡 | So Discipline (and Samsung Health) see sessions; update on edit/move, delete on delete. See [accounts & sync brainstorm](brainstorms/2026-09-29-accounts-sync-and-discipline-link-brainstorm.md). |
| Coach mode: concurrent workouts per client | 🔴 | Start any routine for a partner without being a participant yourself. See 0.3.0 notes in CHANGELOG. |
| Richer curated exercise library | 🟡 | More starters with primary/secondary muscles. |

## Later

| Idea | Effort | Notes |
|---|---|---|
| Optional Google sign-in + free cloud sync | ⚫ | Drive app-data folder first (free to run), Supabase only for server features; shared Google identity with Discipline. See [accounts & sync brainstorm](brainstorms/2026-09-29-accounts-sync-and-discipline-link-brainstorm.md). |
| Cross-phone partner sharing | ⚫ | Depends on sync decisions. |
| Release signing, store assets, localization | 🔴 | Before any public release. |
| Physical-device + TalkBack walkthrough | 🟢 | Tracked in docs/VERIFICATION.md. |

## Maintenance

| Task | Effort | Notes |
|---|---|---|
| Performance: debounce keystroke writes; normalize tables if history gets very large | 🟡 | Off-main decode and once-per-person history shipped in 0.3.4. |
| Brief empty Partner Manager flash when restoring a profile screen | 🟢 | Partners flow starts empty before Room emits. |
| Dependency updates flagged by lint (10 warnings) | 🟢 | Keep aligned with the Discipline stack. |
| Deletion tombstones so older backups can't restore deleted items | 🟡 | Needs a Room migration + MigrationTest. 0.4.0 warns in the import preview. |
| Normalize Room tables when history grows | 🔴 | Current aggregate-per-row design is intentional for now. |
