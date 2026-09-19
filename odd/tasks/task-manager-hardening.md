# Feature: task-manager-hardening

Status: in progress
Branch: `feat/task-manager-hardening`
Created: 2026-09-17

## Goal

Turn the single-commit Java Swing task manager into a data-safe, testable application:
no silent data loss, no swallowed errors, no untestable UI-embedded logic. Behavior
improvements are sequenced behind data robustness so that convenience never sits on top
of fragile persistence.

## Non-goals

- No network sync, no multi-user, no database migration.
- No rewrite to another UI toolkit.
- No Maven/Gradle, no third-party dependencies: `javac` + `bash run-tests.sh` stays the harness.
- No automatic writes to the canonical Obsidian notes. Exports stay explicit.
- No push, no pull request, no merge: those remain the user's decisions.

## Decisions (user-approved 2026-09-17)

| # | Decision | Choice |
|---|---|---|
| D1 | JDK provisioning | `sudo apt-get install openjdk-21-jdk-headless` (Debian 13, JDK 21 satisfies the 17+ requirement) |
| D2 | Sequencing | Chained work units, one commit per unit, reviewable in isolation |
| D3 | Direction | Data robustness first (D3a); UX productivity deferred (D3b, not scheduled here) |
| D4 | Test style | Dependency-free plain asserts on the existing runner, `-ea` enabled |

## Verification

- Runner: `bash run-tests.sh` (compiles `src/taskmanager/*.java` and `test/taskmanager/TaskManagerTests.java`, then runs `taskmanager.TaskManagerTests` with `-ea`).
- Headless smoke: `bash run-app.sh --smoke-test`.
- Strict TDD is enabled globally: every unit starts with a failing test, then the implementation.

## Work units

| ID | Unit | Fragility addressed | Status | Commit |
|---|---|---|---|---|
| T1 | Provision JDK 21 and capture a green baseline with `bash run-tests.sh` | environment blocker | done | see Baseline evidence |
| T2 | Configurable paths, hardened source discovery, consolidated run scripts | #6 hardcoded paths, #3 unchecked `IllegalStateException`, #8 duplicate scripts, `SmokeTest` in `src/` | done | `52614d0` |
| T3 | Strict `CsvCodec` with row diagnostics, 14-field and duplicate-ID validation | #7 liberal parser, missing validation, untested error paths | done | `5db0cb5` |
| T4 | Atomic writes and timestamped backups in save/import; drop dead ordinal counters | data-loss risk, #4 unused ordinals | done | `7501f87` |
| T5 | Validated dates in `TaskDates` (explicit failure instead of silent style drift) | invalid-date handling, untested date edges | pending | - |
| T6 | Visible failures: surface export/IO errors instead of swallowing them | #5 silent export failures | pending | - |
| T7 | Extract area parsing and sorting/ranking out of `MainFrame` into domain classes | #1 god class, #2 duplicated responsibility | pending | - |
| T8 | Close remaining test gaps: Markdown export, `TaskTableModel`, `AppStartup` branches | coverage gaps, plus the advisory findings `R2-DuplicateDefaultSourceRoot`, `R3-unknown-header`, `R1/R4-BackupTimestampCollision`, and `R3/R4-AtomicMovePortability` | pending | - |
| T9 | Document operation: env vars, backup/restore, malformed-CSV reporting, scripts | operational clarity, plus advisory `R4-removed-launcher` | pending | - |

## Baseline evidence (T1)

- `java -version` / `javac -version`: OpenJDK `21.0.12.1+1-1-deb13u1` (Debian), from `openjdk-21-jdk-headless`.
- `bash run-tests.sh` -> `ALL_TESTS_PASSED 4`, exit 0.
- `bash run-app.sh --smoke-test` -> `SMOKE_TEST_PASSED tasks=0 active=0 completed=0`, exit 0.
- See "T2 observation": the smoke output above reflects an existing empty `data/tasks.csv`, so smoke mode is currently stateful and non-deterministic across machines. T2 makes it explicit.

## T2 evidence

- `bash run-tests.sh` -> `ALL_TESTS_PASSED 9` (was 4), exit 0. The unreadable-folder test really exercised the diagnostic path (it printed no SKIPPED note).
- End-to-end smoke proof of the fixed trap, using `TASKMANAGER_DATA_DIR` / `TASKMANAGER_SOURCE_ROOT`:
  1. Clean data directory, no source -> `SMOKE_TEST_PASSED tasks=0`, and **no** `tasks.csv` created.
  2. Export appears in the source root -> next launch imports automatically, `tasks=1`, local file persisted.
  3. Repository defaults (existing local file) -> still loads and passes.
- Commit `52614d0`, ~408 added lines: at the 400-line review-workload mark for one unit. Reported to the user; not split further because the pieces are one behavior change.

### Findings discovered during T2 that were not in the original reconnaissance

1. **Unreadable folder escaped unchecked.** The original comparator wrapped `Files.size` failures in `IllegalStateException`, but the real traversal risk is different: an unreadable *directory* makes `Files.walk` throw `UncheckedIOException`, which escapes any caller catching `IOException`. Fixed by using `Files.walkFileTree` with `visitFileFailed`/`postVisitDirectory`, which continues past unreadable entries and reports them.
2. **`MainFrame` derived the application directory from the local file path** (`prepared.localFile().getParent().getParent()`, assuming exactly `<app>/data/tasks.csv`). Making the data directory configurable would have silently pointed `exports/` at the wrong place. Fixed by carrying `appDirectory` on `Prepared`.
3. **`Files.size` does not fail on a file without read permission** (`stat` only needs the parent directory to be searchable). The first version of the test encoding that assumption failed for the right reason and was replaced with the traversal case above.
4. **Smoke mode was stateful**: its outcome depended on whether a local `data/tasks.csv` already existed, so the first-run path was never exercised in this repository. Configuration overrides now make that path deterministic.
5. **The test runner asserted a hardcoded count** (`ALL_TESTS_PASSED 4`). Replaced with a real counter plus per-test failure names, so adding a test can no longer leave a lying total.

### Resolved decision

- `SmokeTest` stays in `src/taskmanager/`: `--smoke-test` is a shipped headless mode of `Main`, not test scaffolding, and moving it would force the run script to compile test sources. Rationale recorded here instead of moving the file.

## Review history

| Candidate | Lineage | Tier | Lenses | Outcome |
|---|---|---|---|---|
| Plan commit `5f6f36e` | `review-1720b97ec6592817` | medium | reliability | approved, authority burned |
| T2 + docs (`e6e0567`, `52614d0`, `b1cbd69`) | `review-8e1a13981d69edba` | high | risk, resilience, readability, reliability | approved, authority burned |
| Review record `3aea4e4` (doc-only increment) | `review-1bdd65b875ce3f69` (no lineage created) | n/a | n/a | **left unreviewed by explicit user decision** |
| T3 + T4 + docs (through `177676b`, tree `01f83a17`) | `review-b1178646de5500fd` | high | risk, resilience, readability, reliability | approved, authority burned |

Consent for the T2 candidate needed two extra START attempts: the first two returned `consent-binding-stale` with `lineage_created: false`, and the third succeeded once the human answered the host prompt. Restarting START twice with different bindings is the point at which retrying stops being useful; the human had to resolve it.

### Advisory findings from the T2 review (non-blocking, informational)

The closure states that none of these opened a correction, and that they are later work rather than a reason to re-run review on that candidate.

1. `R2-DuplicateDefaultSourceRoot` (readability, WARNING, `AppConfig.java:17`): the default source root path is now written twice, once in `AppConfig` and once in `SourceCsvFinder.defaultRoot(Path userHome)`. Worse, after T2 that finder method has no callers at all, so it is duplicate dead code. Scheduled into T8: delete `SourceCsvFinder.defaultRoot` so path defaults live only in `AppConfig`.
2. `R4-removed-launcher` (resilience, WARNING, `run.sh:1-6`): the duplicate launcher was deleted, which is correct, but nothing tells a user who was invoking it. Scheduled into T9: document that `run-app.sh` is the only launcher.

### Findings from the third review (all advisory, none blocking)

Eight entries, which reduce to four distinct issues. Two are defects in code written during T4, and they are good catches:

1. `R1/R4-BackupTimestampCollision` (`TaskRepository.java:174-175`): the backup name uses a seconds-resolution timestamp, so two replacements inside the same second overwrite each other and a backup is silently lost. Scheduled into T8.
2. `R3/R4-AtomicMovePortability` (`TaskRepository.java:148-158`): `ATOMIC_MOVE` can fail on filesystems without atomic rename support. Scheduled into T8, where the fix is a documented fallback rather than a silent retry.
3. `R3-unknown-header` (`TaskRepository.java:184-188`): already found during T3, scheduled into T8.
4. `R2-DuplicateDefaultSourceRoot` (`SourceCsvFinder.java:100-106`): already found by the earlier review, scheduled into T8.

### One partial admission, and how it was recovered

The four-lens group was submitted once: three reviewers were admitted, and the `review-reliability` reviewer was refused at admission with `binding_mismatch`, because its result echoed a different artifact subject than the binding's `subject_hash`. The refusal did not consume the lens slot and preserved the rejected payload under `.git/gentle-ai/rejected-results/`. Per the refusal, the refused bytes were never resubmitted: a fresh STATUS was taken, it reoffered exactly that one slot, and only that slot was re-run. It was admitted and closed the review as approved.

## Consent failures: corrected diagnosis

**Correction (2026-09-18, after the T4 candidate was reviewed successfully):** the failures described below were NOT a broken native layer. The decisive evidence is `native_invocation_attempted: false` in the final `consent-binding-stale` response: the native side was never called, so nothing about the review authority store was at fault. The consent binding has a ten-minute life and is created before the model turn that answers it, so a long turn reliably produces `consent-binding-stale`. Issuing START again immediately, in a short turn, succeeds: that is how this candidate obtained its lineage, with no lock cleanup and no configuration change. The stale `REVIEW-MAINTENANCE.lock` and the `cancelled` status call remain unexplained, but they are not the blocker they were assumed to be.

### Superseded diagnosis (kept for the record)

The third candidate could not be reviewed, and **not** because of its content:

1. First START returned `consent-binding-stale` (`4af99601-2327-45eb-9bc9-8b1c5f82877a`), `lineage_created: false`.
2. Second START returned `native-status-unavailable` with `error_code: cancelled` and `inventory_complete: false`.
3. Nothing was mutated by either attempt (`mutation_performed: false`), and the repository stayed clean.

Evidence found while diagnosing (circumstantial, causality not proven):

- `.git/gentle-ai/REVIEW-MAINTENANCE.lock` exists, is 0 bytes, and its mtime is ~17 hours older than the diagnosis time.
- No process holds it (`lsof` shows no holder) and no `gentle-ai` process is running.
- `.git/gentle-ai/review-transactions/v2/LOCK` also exists.
- `gentle-ai review reclaim`, the route the facade maps for lock recovery, requires explicit `--lineage`, `--actor`, and `--reason`. It quarantines an incomplete store entry; it is not a lock-deleting command. No lineage was created, so there were no native values to supply and none were invented.

**Disposition (user decision, 2026-09-18):** this candidate is left unreviewed. The real increment over the previously burned review is a single Markdown file, and the code inside the provider's accumulated range was already reviewed and burned as target `0e77d556`.

**Follow-up, not scheduled:** investigate the stale maintenance lock before the next candidate that contains code, since every later unit will hit the same gate.

## T3 evidence

- `bash run-tests.sh` -> `ALL_TESTS_PASSED 14` (was 9), exit 0.
- Three smoke scenarios, using temporary data and source directories:
  1. No source -> `tasks=0`, no local file created.
  2. Export imported -> `tasks=2`; a second launch reloads the local file the app itself wrote, so the strict rules accept the app's own id + 14 field format.
  3. A hand-broken local file now fails with a diagnostic naming the row: `Malformed CSV at row 2: found 3 fields, expected 14 source fields with or without a leading id`. Before T3 that row was silently padded to fourteen fields.
- Commit `5db0cb5`, 237 added lines.

### What was actually silent before T3

- `CsvCodec` had a branch commented "Be liberal about whitespace or malformed text after a closing quote" that appended whatever followed the closing quote, so `"abc"def` parsed as `abcdef` with no error. Whitespace between a closing quote and the delimiter is still tolerated on purpose (hand-edited exports pad there); any other trailing text is now a positioned failure.
- `normalizeSourceValues` padded short rows and truncated long ones in **both** the source-import and the local-load paths, so a 13-field row became a valid-looking task with an empty last field, and a 15-field source row lost a field without warning.
- The local file accepted any row with fifteen **or more** fields and ignored the extras.

### New finding, scheduled into T8

Header detection is name-based only: `TaskRepository.isHeader` matches a first cell of `id` or `nombre`. A source export whose header row starts with any other name is therefore not a header at all, and because a header row has exactly fourteen fields it passes the new field-count check and is imported as a task. The fix is to recognise any of the fourteen known header names, not just the first, and to cover it with a test.

## T4 evidence

- `bash run-tests.sh` -> `ALL_TESTS_PASSED 17` (was 14), exit 0.
- The atomicity test is real: it compares `BasicFileAttributes.fileKey()` across two saves, so it fails against the old in-place truncating write and passes only when the file is genuinely replaced.
- Four smoke scenarios with temporary directories: no source -> no file; import -> local file created with no backup, because there was nothing to back up; reload of the app-written file; and no `.tmp` residue left behind.
- Commits `7501f87` (code) and the documentation commit that follows.

### Visible side effect worth knowing

The renamed file inherits the temporary file's mode, so the local task file is now `-rw-------` (0600) instead of `-rw-rw-r--`. For private task data this is an improvement rather than a regression, but it is a real change to what is on disk and is recorded so nobody is surprised by it.

### Backup policy, decided here

Only `replaceAll` backs up (that is the `Refresh / Import` path, the one operation that discards local edits). Backing up on every `save()` was rejected: `save()` runs on every add, edit, completion, and delete, so it would drop a backup beside every single interaction and turn the data directory into noise. Retention is therefore unbounded but rare-by-construction; T9 documents the recovery procedure.

## Review gate: third candidate also unreviewed

| Candidate | Result |
|---|---|
| T3 (`5db0cb5`, `b13a147`) | `native-operation-failed`, `lineage_created: false`, `mutation_performed: false`, `next_action: resolve-native-operation-failure` |

The provider itself asks for a native operation failure to be resolved. That is not something a caller can supply: there are no exact native values to provide, and inventing a lineage, actor, or reason for `review reclaim` is explicitly out of bounds. So the candidate stayed unreviewed, and the user was told the gate was broken rather than that the candidate was judged unnecessary. Three candidates have now failed in three different ways (`consent-binding-stale`, `native-status-unavailable`, `native-operation-failed`), all with `lineage_created: false` and no mutation.

## Known fragilities from reconnaissance

1. `MainFrame` is a 437-line god class: window construction, filtering, sorting, persistence actions, dialogs, exports (`MainFrame.java:71-428`).
2. Area-token parsing and sorting live in `MainFrame` (`:184-252`) while `TaskFilter` already owns filter semantics (`TaskFilter.java:36-66`).
3. `run.sh` and `run-app.sh` are byte-identical.
4. `TaskRepository` carries unused `ordinal` increments in import/replace (`:106-120`).
5. Export directory creation errors are swallowed (`MainFrame.java:401-407`).
6. `SourceCsvFinder` raises unchecked `IllegalStateException` (`:45-56`).
7. `CsvCodec` is liberal after a closing quote, accepting malformed input (`:87-101`).
8. Paths are pinned to `user.dir` and `user.home` (`Main.java:15-16`, `SourceCsvFinder.java:36-39`).

## Accepted exceptions

- T1 closes without a commit: it changes the machine, not the repository (D1). Its evidence is the baseline test output.

## Risks

- The Swing UI cannot be exercised automatically on this box; UI units are verified through extracted domain logic plus the headless smoke check.
- `data/` and `exports/` are gitignored because they hold private task data; tests must never depend on real user data.
