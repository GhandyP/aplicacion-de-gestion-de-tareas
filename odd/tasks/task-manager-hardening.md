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
| T1 | Provision JDK 21 and capture a green baseline with `bash run-tests.sh` | environment blocker | pending | n/a (no repo change) |
| T2 | Configurable paths, hardened source discovery, consolidated run scripts | #6 hardcoded paths, #3 unchecked `IllegalStateException`, #8 duplicate scripts, `SmokeTest` in `src/` | pending | - |
| T3 | Strict `CsvCodec` with row diagnostics, 14-field and duplicate-ID validation | #7 liberal parser, missing validation, untested error paths | pending | - |
| T4 | Atomic writes and timestamped backups in save/import; drop dead ordinal counters | data-loss risk, #4 unused ordinals | pending | - |
| T5 | Validated dates in `TaskDates` (explicit failure instead of silent style drift) | invalid-date handling, untested date edges | pending | - |
| T6 | Visible failures: surface export/IO errors instead of swallowing them | #5 silent export failures | pending | - |
| T7 | Extract area parsing and sorting/ranking out of `MainFrame` into domain classes | #1 god class, #2 duplicated responsibility | pending | - |
| T8 | Close remaining test gaps: Markdown export, `TaskTableModel`, `AppStartup` branches | coverage gaps | pending | - |
| T9 | Document operation: env vars, backup/restore, malformed-CSV reporting, scripts | operational clarity | pending | - |

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
