# IronIndex

Standalone Kotlin / Jetpack Compose Android app. Read README.md and docs/PRODUCT.md.

- Work fully offline; persist draft edits and preserve history snapshots.
- Keep set entry fast, accessible, and usable with the keyboard open.
- Do not alter Discipline or connect its database as part of routine changes here.
- Preserve session units and identifiers. Never enable destructive Room migrations.
- Update CHANGELOG.md and versionName/versionCode for subsequent releases.
- Verify changes with assembleDebug, testDebugUnitTest, lintDebug; use
  connectedDebugAndroidTest for persistence/UI changes when an emulator is available.
- Keep credentials, local.properties, signing keys, and build outputs out of Git.

## Releases and docs (enforced by `.githooks/pre-commit`)

Run `./scripts/install-hooks.sh` once per clone. Every user-visible change ships as its own
version; never batch fixes into a later release.

1. Bump `versionName` (feature → minor, fix/polish → patch; `0.x.y` until launch) and
   increment `versionCode` in `app/build.gradle.kts`.
2. Add `## x.y.z - YYYY-MM-DD` at the top of CHANGELOG.md, written for the lifter.
3. Update README.md "Current version" and any feature docs (README usage, docs/PRODUCT.md).
4. Record build/test/emulator results in docs/VERIFICATION.md under the new version.
5. Room version bump → export schema JSON, add `MIGRATION_a_b`, and a MigrationTest.
6. Shipped roadmap item → remove its row in docs/ROADMAP.md and move its brainstorm to
   docs/archive/. New brainstorm → add a ROADMAP row in the same commit.
7. Commit, then tag and push: `git tag -a vX.Y.Z -m "vX.Y.Z — summary" && git push --follow-tags`.

Use `--no-verify` only for emergencies, and say why in the commit message.
