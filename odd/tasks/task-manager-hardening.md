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
| T5 | Validated dates in `TaskDates` (explicit failure instead of silent style drift) | invalid-date handling, untested date edges | done | `db361b4` |
| T6 | Visible failures: surface export/IO errors instead of swallowing them | #5 silent export failures | done | `795abaa` |
| T7 | Extract area parsing and sorting/ranking out of `MainFrame` into domain classes | #1 god class, #2 duplicated responsibility | done (part of the T7+T8 candidate) | `09d44a0` |
| T8 | Close remaining test gaps: Markdown export, `TaskTableModel`, `AppStartup` branches | coverage gaps, plus the advisory findings `R2-DuplicateDefaultSourceRoot`, `R3-unknown-header`, `R1/R4-BackupTimestampCollision`, `R3/R4-AtomicMovePortability`, and `R2-crlf-position-accounting` | done | `818f866` + `95181dd` |
| T9 | Document operation: env vars, backup/restore, malformed-CSV reporting, scripts | operational clarity, plus advisory `R4-removed-launcher` | done | `a016615` |
| T10 | Close the two findings the ninth review surfaced | `R3-generated-explicit-id-collision`, `R4-silent-unreadable-root` | done | `265cd2a` |

T10 was not in the original plan. It exists because fixing T8 uncovered two findings that the recycled list had been hiding, one of them a defect this feature introduced in T3. T11 exists for the same reason one round later: the tenth review found four issues, three of them in code written during T9's neighbours.

| T11 | Close the four findings the tenth review surfaced | `R4-replace-failure-memory-drift`, `R4-nonatomic-fallback`, `R3-001`, `R2-unreadable-root-check-order` | done | `f1ae113` |

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
| Docs `4f250ac` (tree `48dfe85c`) | `review-460f776439bc33c5` | high | risk, resilience, readability, reliability | approved, authority burned; all four reviewers admitted cleanly |
| Docs `544bb9b` (tree `a14ef425`) | `review-06e2e0c62457f94d` | high | risk, resilience, readability, reliability | approved, authority burned; consent granted on the first attempt |
| T5 `db361b4` + docs (tree `45827613`) | `review-910ae6667ad23b3b` | high | risk, resilience, readability, reliability | approved, authority burned; **no finding on the TaskDates change** |
| T6 `795abaa` + docs (tree `ab641184`) | `review-d8f187a2bb36a1ac` | high | risk, resilience, readability, reliability | approved, authority burned; **no finding on the Exports change** |
| T7 `09d44a0` + docs (tree `cb131460`) | `review-97ffc9dc88d33a5f` | high | risk, resilience, readability, reliability | approved, authority burned; **no finding on the extraction** |
| T10 `265cd2a` + docs (tree `f0631ddc`) | `review-511720b9e63cc94b` | high | risk, resilience, readability, reliability | approved, authority burned; **four findings, three on recently written code** |

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

### Findings from the fourth review (all advisory, none blocking)

Eight entries that reduce to the same four known issues, plus one new one on code written during T3:

- `R2-crlf-position-accounting` (`CsvCodec.java:94-102`, new): inside a quoted field a carriage return advances the column counter instead of the line counter, so a CRLF line break embedded in a quoted field leaves every later reported position off by one line. The positions the codec promises are therefore wrong for CRLF input that contains quoted multi-line fields. Scheduled into T8, and it is a defect in this feature's own diff.

The other seven entries are repetitions of `R1/R4-BackupTimestampCollision`, `R3/R4-AtomicMovePortability`, `R3-UnknownSourceHeader`, and `R2-DuplicateDefaultSourceRoot`, all already scheduled into T8.

## Consent failures: corrected diagnosis

**Correction (2026-09-18, after the T4 candidate was reviewed successfully):** the failures described below were NOT a broken native layer. The decisive evidence is `native_invocation_attempted: false` in the final `consent-binding-stale` response: the native side was never called, so nothing about the review authority store was at fault. The consent binding has a ten-minute life and the facade reports it expired before the START call can answer it. This candidate also took three attempts: attempts one and two returned `consent-binding-stale` even though they were issued about thirty seconds apart, and both reported `expired after 10 minutes`, which is impossible for a binding minted moments earlier. That rules out turn duration as the mechanism: the binding the facade reads was minted earlier than the START call. The reliable remedy is a human at the host consent panel, not a faster retry. The third attempt succeeded exactly when the user was watching, with no lock cleanup and no configuration change. The stale `REVIEW-MAINTENANCE.lock` and the `cancelled` status call remain unexplained, but they are not the blocker they were assumed to be.

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

## Review cost trend, and why it matters here

The provider projects the **accumulated range** from `3a04c77`, not the increment, so every new candidate re-reviews all previously reviewed code. Four consecutive reviews of this branch cost four model runs each over prompts that grew from ~53 KB to ~90 KB, and the last two reviews returned the same five advisory findings with nothing new. The churn is real and it is structural, not accidental: as long as the branch keeps growing in single-commit steps, each step pays for the whole history. The practical implication is not to skip the gate but to reduce the number of candidates: land the remaining units and let T8 address the accumulated findings, instead of expecting new findings from re-reviewing unchanged code.

## T5 evidence

- `bash run-tests.sh` -> `ALL_TESTS_PASSED 20` (was 17), exit 0.
- Smoke green in three scenarios: no source, first import, and reload of the app-written file.
- Commit `db361b4`, 76 added lines.

### The defect, and the line that made it silent

```java
LocalDate base = parse(current).orElseGet(() -> LocalDate.now(clock));
```

Any unparseable due date became today, and the result was formatted and stored. The user saw a postpone succeed and a plausible date appear; the original value was gone. The red test proved it by asserting that the refusal message names the offending value while the test run showed the postpone "succeeding".

### The line drawn deliberately

A **blank** due date keeps shifting from today. That is a convenience for a task with no date yet, not a repair of corrupted data, so tightening it would have broken a legitimate flow. Only a non-blank, unparseable date is refused. Two tests pin both halves so neither can drift.

### Skipped review, declared

Candidate `sha256:def774b2…` (the documentation commit `1e924d7`) was **not** sent to review. Ground: the increment since the previously burned review (`544bb9b`) is one file, `odd/tasks/task-manager-hardening.md`, plus five lines, verified with `git diff --stat`, and `git diff 544bb9b..HEAD -- src/ test/` is empty. My contract allows skipping the preflight for a trivial passive documentation-only edit, independently of any user disposition. The code inside the provider's accumulated range is byte-identical to what was reviewed and burned twice already (targets `1712d0c9` and `92f90641`), with an identical finding set. Recording it here so the skip is auditable and reversible.

### Findings from the sixth review: the new code passed clean

Nine entries, all reducing to the same five known issues already scheduled into T8. What matters is the absence: **not one finding landed on the `TaskDates` change**. The four lenses reviewed the silence-versus-error distinction and the two tests pinning it, and had nothing to say. The accumulated trend is now unambiguous, and the evidence is three reviews deep:

| Review | Increment | New findings | Prompt size |
|---|---|---|---|
| fourth | docs only | one (CRLF accounting) | ~88 KB |
| fifth | docs only | none | ~90 KB |
| sixth | T5 code + docs | none, and none on the new code | ~98 KB |
| seventh | T6 code + docs | none, and none on the new code | ~106 KB |
| eighth | T7 extraction + docs | none, and none on the new code | ~123 KB |
| ninth | T8 five fixes + coverage | **two new, and the five recycled ones are gone** | ~135 KB |

The ninth review is the proof the unit was worth doing: eight entries collapsed to three, the five issues that had been recycled for six rounds no longer appear, and the two that replaced them are specific and new. The prediction made before the review ("the list should change because those five no longer exist") held.

Repeated review of unchanged code produces no information and keeps costing four model runs; new code does get real coverage when it is reviewed. That is the argument for fewer, larger candidates rather than per-commit ones.

By the seventh review the picture is sharper than "reviews cost a lot". Four consecutive reviews returned no finding on the newest code, and the same five issues keep reappearing because nothing has addressed them yet. The remaining work is concentrated in exactly those five items, which T8 owns. So the useful move is to land T7 together with T8 and review the result once, instead of paying for the accumulated range four more times to rediscover the same list.

The eighth review confirmed it once more and raised the price: five consecutive reviews with no finding on the newest code, the same five issues, and a prompt that reached 123 KB. Every one of those five issues is a line in T8.

## T6 evidence

- `bash run-tests.sh` -> `ALL_TESTS_PASSED 22` (was 20), exit 0. Smoke green with no source and after an import.
- Commit `795abaa`, 94 added lines.

### Scope was exactly one site

A search for discarded failures across `src/taskmanager` found `MainFrame.java:421` as the only silent catch in the whole source. Every other one either calls `showError`, sets the status line, or collects a problem. So this unit did not need a sweep; it needed that one site fixed and the fix to be testable.

### Why not just throw

The original comment had a point: refusing to export because the default folder is missing would be worse than exporting somewhere else. So the fix keeps the export unblocked and returns the reason instead of discarding it, and the caller shows it in the status line and the dialog title. The test pins both halves: a writable application directory reports nothing, and a folder blocked by an ordinary file names the path and still returns it for fallback.

### Skipped review, declared

Candidate `sha256:683fce61…` (documentation commit `3dc9f22`) was not sent to review. Ground: `git diff db361b4..HEAD` is one file, `odd/tasks/task-manager-hardening.md`, plus 36 lines, and the same diff for `src/` and `test/` is empty. Same auditable basis as the previous skip.

## T7 evidence

- `bash run-tests.sh` -> `ALL_TESTS_PASSED 27` (was 22), exit 0. Smoke green with no source and after an import.
- Commit `09d44a0`, 195 added and 63 removed lines. `MainFrame` drops from 437 to 390 lines.
- Skipped review, declared: candidate `sha256:79c64bc2…` (documentation commit `6fc7e91`) was not sent to review; `git diff e47a6ab..HEAD` is one markdown file plus four lines, and the same diff for `src/` and `test/` is empty.

### What was actually duplicated

The rule for reading the area field lived in two places: `MainFrame.areaValues` split on `[,;|\n]` to build the facet list, and `Task.hasArea` split on the same pattern to answer a filter. Two copies of one rule are a drift waiting to happen, so both now call `AreaTokens`.

`Task.hasArea` kept its behaviour exactly, **including the part that looks redundant**: after comparing token by token it also compares the whole raw field, so a request that repeats the whole field still matches. That branch is unreachable from the facet combo, which only ever offers single tokens, so the honest move was to preserve it and say so rather than silently drop behaviour in a refactor. A test pins it.

### Why the ordering mattered

The comparator and its ranking vocabulary were inside the window class: `muy urgente`, `media`, `poca` for urgency, and `muy importante`, `importante`, `no importante` for importance. Those are domain rules about the export's Spanish wording, and no test could reach them without a display. They now live in `TaskOrder` with the ranking order preserved, including the detail that `muy importante` is tested before `no importante`. The tie-break chain is unchanged: completion, urgency, importance, due date with unreadable dates last, then name case-insensitively.

### Honest limit

This is a structural extraction; it deliberately changes no behaviour. Two tests describe the ranking as it was, so a later behaviour change has to be deliberate rather than accidental.

## T8 evidence

- `bash run-tests.sh` -> `ALL_TESTS_PASSED 32` (was 27 before the unit), exit 0. Smoke green with no source and after an import.
- Commits `818f866` (the five findings) and `95181dd` (coverage), 173 added lines together.

### The five findings, and what each one actually was

| Finding | What was wrong | Test |
|---|---|---|
| Backup timestamp collision | seconds-resolution names meant two replacements in one second overwrote the earlier backup, losing it silently. The red test showed exactly one backup where two were expected. | yes |
| `ATOMIC_MOVE` portability | the move can throw on a filesystem that cannot rename atomically. Now falls back to a whole-file replace. | **no deterministic test** |
| Unknown source header | a column-name row was recognised only by its first cell, so a renamed first column turned the header into a task, since a header row has exactly fourteen fields. | yes |
| Dead `defaultRoot` | no callers left after configuration took ownership of the default location. | n/a (deletion, compile-verified) |
| CRLF position accounting | a carriage return inside a quoted field advanced the column instead of the line, so a CRLF in a quoted multi-line field left every later position one line off. Field content unchanged. | yes |

**Stated rather than implied:** the `ATOMIC_MOVE` fallback branch cannot be exercised on this machine's filesystem, so it ships without a deterministic test. Its observable contract (a save replaces the file and leaves no temporary behind) stays covered by the atomicity test.

### Coverage added

`MarkdownExporter` and `TaskTableModel` had no tests at all. The two new tests are characterisation tests: they passed on the first run because they describe behaviour that already worked. That is the honest description of them, and it is why they are not presented as red-then-green.

## Two new findings, and where they go

The ninth review replaced the recycled list with two findings that are specific enough to act on:

1. `R3-generated-explicit-id-collision` (`TaskRepository.java:280`) — **introduced by T3**. Duplicate detection uses one set for explicit ids and another for generated ones, so a generated id equal to an explicit id is not caught and two tasks can share an id. That is a real defect in this feature's own diff, found only after the five loud issues stopped drowning the review.
2. `R4-silent-unreadable-root` (`SourceCsvFinder.java:64-66`) — `discover` returns "nothing found" both for a root that is genuinely absent and for a root that exists but cannot be read, so an unreadable vault is reported as an empty vault. The same class of silent failure T2 went after, one level up.

Both are code changes, so they get a small unit (T10) rather than being folded into the documentation unit. `R4-removed-launcher` remains a documentation item in T9.

## The tenth review, and what it means for convergence

Four findings, three of them about code written in the last two units:

1. `R4-replace-failure-memory-drift` (`TaskRepository.java:131-134`) — **the serious one.** `replaceAll` backs up, mutates the in-memory list, and only then saves. If the save throws, memory holds the imported list while the file still holds the old one, so the window shows tasks that were never persisted. That is a real defect in the design T4 introduced, and it is the kind of thing only a fresh reader notices.
2. `R4-nonatomic-fallback` (`TaskRepository.java:157-166`) — the `ATOMIC_MOVE` fallback shipped in T8 means the atomicity guarantee is conditional. True as written, and worth stating in the code rather than only in a commit message.
3. `R3-001` (`TaskRepository.java:158`) — the same region seen by another lens.
4. `R2-unreadable-root-check-order` (`SourceCsvFinder.java:59-61`) — the check order from T10. `Files.isDirectory` answers false on some permission errors, so the "not a directory" message can be wrong when the truth is "cannot read".

### The convergence question, stated plainly

Two consecutive reviews have produced findings on code written for this feature (T3's id collision, then these). That is the loop working, not failing: a review that finds nothing on new code is rare, and each fix is genuinely smaller and more specific than the last. But it means this program does not converge to zero findings in one pass, and every further round costs four model runs over a prompt that is now ~142 KB.

The honest options are to keep iterating while the findings stay real and small, or to stop with these four recorded as known and accepted debt. That is a user decision, not an agent one, and it is recorded here as open.

## T10 evidence

- `bash run-tests.sh` -> `ALL_TESTS_PASSED 34` (was 32), exit 0. Smoke green in three scenarios.
- Commit `265cd2a`, 80 added lines.

### Duplicate ids, the defect this feature introduced

Duplicate detection had grown two structures: a map for explicit ids and a set for generated ones. An id that a **generated** row had already taken was therefore invisible to the explicit check, so two tasks could share an id and every lookup by id could return the wrong task. The red test proved it by loading a file that did exactly that and watching the repository accept it.

Every id now goes through one set of taken ids plus the row that first used it. A collision between two *generated* ids is still renamed rather than fatal, because two identical legacy rows are legitimate; only an explicit duplicate is an error. Both halves are tested.

### The unreadable root

`discover` answered "nothing found" for three different situations: no root configured, a root that is not a directory, and a root that exists but cannot be read. Only the first is a normal state that deserves silence. The three are now distinct outcomes, and the message says which one happened. Note that `Files.isDirectory` reports false on a permission error, so a separate readability check is needed after it to produce the right message.

## T9 evidence

- Commit `a016615`, README rewritten with 91 added and 12 removed lines. Doc-only, so no review: verified with `git diff --stat` that no file under `src/` or `test/` changed.
- The README had drifted into being false, which is its own kind of defect: it claimed OpenJDK 25, listed four tests, and described the pre-hardening application. It now documents the single launcher and why `run.sh` was removed (the `R4-removed-launcher` finding that had been carried since T2), the two environment variables and their defaults, atomic saves and the owner-only mode, when backups are taken and how to restore one, the shape of a malformed-CSV message, and how discovery distinguishes a missing root from an unreadable one.
- Both documented commands were run exactly as written before committing: the environment-variable smoke example and the test runner.

## T11 evidence

- `bash run-tests.sh` -> `ALL_TESTS_PASSED 35` (was 34), exit 0. Smoke green in three scenarios.
- Commit `f1ae113`, 108 added and 30 removed lines.

### The memory drift, fixed beyond what was reported

The review caught the drift in `replaceAll`, where the whole list is discarded. The same window existed in `add`, `update`, `delete`, `markCompleted` and `postponeDueDate`: all of them changed memory and only then wrote the file. Fixing one and leaving five would have been answering the report rather than the defect, so every mutator now builds the list it intends to have, writes it, and adopts it only after the write returned. `markCompleted` and `postponeDueDate` collapsed into delegations to `update`, and the now-unused `updateWithoutSave` is gone.

### What the red test does and does not prove

The test forces a save failure deterministically by removing write permission from the data directory. Worth stating precisely: it fails on the mutators that mutated before writing, and it does **not** reproduce the `replaceAll` drift, because in that path the backup copy fails first and the mutation never happens. The `replaceAll` drift needs a failure after the backup, such as a full disk or a failed rename, which cannot be forced portably. The fix covers both paths by construction, and the test proves the invariant for the paths it can reach.

### The other two

`save()` now documents the guarantee instead of only commenting on it: the rename is atomic where the filesystem supports it, and the fallback is a whole-file replace that is still never a truncating write. Source discovery no longer says a root "is not a directory" when all it knows is that the path could not be inspected.

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
