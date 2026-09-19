# Task Manager

A small Java Swing desktop application for managing the task inventory exported from Obsidian.

## Requirements

- OpenJDK 17 or newer
- Linux, macOS, or Windows with a graphical desktop for the Swing UI

Verified with OpenJDK 21.0.12.1 (`openjdk-21-jdk-headless` on Debian).

## Run the application

From this directory:

```bash
bash run-app.sh
```

`run-app.sh` is the only launcher. There used to be a duplicate `run.sh`; it was removed so there is
one place to change how the application starts. Use `run-tests.sh` for tests and `run-app.sh
--smoke-test` for the headless check below.

## Where files come from, and where they go

By default the application reads and writes:

| Purpose | Default location |
| --- | --- |
| Local task file (the app owns it) | `<this directory>/data/tasks.csv` |
| Export source to import from | `~/Documents/Obsidian Vault/GDT/01_Tareas` |
| Exports you ask for | `<this directory>/exports/` |

On a **first** launch the app searches the source root recursively for the largest filename ending in
`_all.csv`, imports that export, and writes the local file. Later launches load the local copy, so
normal editing never touches the Obsidian source. Use **Refresh / Import** only when you intend to
replace the local list with the current largest export.

If a first launch finds no export, it **does not** create an empty local file. An empty file would
make every later launch treat the local copy as authoritative and silently stop importing. Instead the
next launch imports automatically as soon as an export appears.

## Configuration

Both locations can be overridden with environment variables:

| Variable | Default | Notes |
| --- | --- | --- |
| `TASKMANAGER_DATA_DIR` | `<this directory>/data` | A relative path resolves against this directory |
| `TASKMANAGER_SOURCE_ROOT` | `~/Documents/Obsidian Vault/GDT/01_Tareas` | A leading `~/` expands to your home |

Blank values fall back to the defaults. This is how the smoke check runs against throwaway
directories, for example:

```bash
TASKMANAGER_DATA_DIR=$(mktemp -d) TASKMANAGER_SOURCE_ROOT=$(mktemp -d) bash run-app.sh --smoke-test
```

## Data safety

**Saves are atomic.** The task list is written to a temporary file next to the target and then renamed
over it, so an interrupted save cannot leave a truncated or half-written list. A save leaves no
`*.tmp` file behind. On filesystems that cannot rename atomically, the app falls back to a whole-file
replace: the write is then not atomic, but it is still never a truncating write.

Because the renamed file inherits the temporary file's mode, a saved `tasks.csv` is owner-only
(`0600`). That suits private task data.

**Replacing the list makes a backup first.** `Refresh / Import` is the one operation that discards
local edits, so before replacing, the previous file is copied to:

```text
tasks.csv.<yyyyMMdd-HHmmss>.bak
```

Two replacements inside the same second get `-2`, `-3` suffixes rather than overwriting each other.
Routine edits do not create backups; they would otherwise leave one beside every change.

To restore a backup, close the app and copy it over the local file:

```bash
cp data/tasks.csv.20260918-214800.bak data/tasks.csv
```

## When the app refuses to read a file

Malformed CSV is reported instead of silently repaired. The message names a physical position when the
problem is in the characters, or a row when it is in the shape of the data:

```text
Malformed CSV at line 2, column 4: unexpected text after a closing quote: 'x'
Malformed CSV at row 2: found 13 fields, expected 14 source fields with or without a leading id
```

The app accepts exactly fourteen source fields per row, or fifteen in the local file where the first
field is the task id. It will not pad a short row or truncate a long one, because both used to change
your data quietly. Duplicate task ids in the local file are reported with both rows instead of being
renamed.

An export whose column-name row was renamed still counts as a header: a row that names at least three
known columns is recognised as one.

Source discovery never aborts a launch. Entries under the source root that cannot be read are skipped
and listed, so a partly unreadable vault still yields the exports that are readable. A source root
that does not exist is silent, because that is a normal state before anything is configured; a root
that exists but cannot be read is reported, because calling it empty would be wrong.

## Headless smoke check

Use this when no graphical display is available:

```bash
bash run-app.sh --smoke-test
```

The smoke check validates source discovery, import, local persistence, fourteen-field preservation,
CSV quoting behavior, and that a run without a source leaves no local file behind.

## Tests

```bash
bash run-tests.sh
```

The runner is dependency-free: plain `javac` plus assertions under `-ea`. It prints
`ALL_TESTS_PASSED <count>` and names the test that failed. It currently covers 34 tests:

- CSV quoting, round-trip persistence, and malformed input reporting with positions
- source discovery: largest-file selection, determinism, and unreadable entries
- configured paths and their overrides, including the first-run import policy
- combined search and facet filtering, area token rules, and table ordering
- completion/reopening, postponement, and refusal to postpone an unparseable date
- atomic saves, backup naming and collision handling, and duplicate id detection
- Markdown dashboard output and the table projection

## Main actions

- Search and filter by status, importance, urgency, and area
- Add or edit tasks while preserving the fourteen source fields
- Mark tasks completed or reopen them
- Postpone a selected task by 1, 3, 7, or 14 days
- Delete tasks from the local copy after confirmation
- Save the local CSV
- Refresh/import from the Obsidian export after confirmation
- Export a clean CSV or an Obsidian-friendly Markdown dashboard

Exports are explicit. The app never writes to the canonical Obsidian notes automatically.
