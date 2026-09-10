# Task Manager

A small Java Swing desktop application for managing the task inventory exported from Obsidian.

## Requirements

- OpenJDK 17 or newer
- Linux, macOS, or Windows with a graphical desktop for the Swing UI

The current environment was verified with OpenJDK 25.0.4.

## Run the application

From this directory:

```bash
bash run-app.sh
```

The first launch searches recursively under:

```text
~/Documents/Obsidian Vault/GDT/01_Tareas
```

for the largest filename ending in `_all.csv`. That export is imported into the application-local file:

```text
data/tasks.csv
```

Later launches use the local copy. This prevents normal editing from changing the Obsidian source. Use **Refresh / Import** only when you intentionally want to replace the local list with the current largest source export.

## Headless smoke check

Use this when no graphical display is available:

```bash
bash run-app.sh --smoke-test
```

The smoke check validates source discovery, import, local persistence, fourteen-field preservation, and CSV quoting behavior.

## Tests

```bash
bash run-tests.sh
```

The test runner is dependency-free and covers:

- CSV commas, newlines, escaped quotes, and round-trip persistence
- deterministic largest-source discovery
- combined search and facet filtering
- completion/reopening and due-date postponement

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
