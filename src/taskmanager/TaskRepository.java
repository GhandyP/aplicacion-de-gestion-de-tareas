package taskmanager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Local CSV persistence boundary. Obsidian files are read-only import sources. */
public final class TaskRepository {
    public static final List<String> LOCAL_HEADERS;

    static {
        ArrayList<String> headers = new ArrayList<>();
        headers.add("id");
        headers.addAll(Task.SOURCE_HEADERS);
        LOCAL_HEADERS = List.copyOf(headers);
    }

    private final Path localFile;
    private final List<Task> tasks = new ArrayList<>();

    public TaskRepository(Path localFile) throws IOException {
        this.localFile = Objects.requireNonNull(localFile, "localFile").toAbsolutePath().normalize();
        loadExisting();
    }

    public Path localFile() {
        return localFile;
    }

    public synchronized List<Task> tasks() {
        return List.copyOf(tasks);
    }

    public synchronized int size() {
        return tasks.size();
    }

    public synchronized boolean isEmpty() {
        return tasks.isEmpty();
    }

    public synchronized Optional<Task> findById(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return tasks.stream().filter(task -> task.id().equals(id)).findFirst();
    }

    public synchronized void add(Task task) throws IOException {
        Objects.requireNonNull(task, "task");
        if (findById(task.id()).isPresent()) {
            throw new IllegalArgumentException("A task with id already exists: " + task.id());
        }
        tasks.add(task);
        save();
    }

    public synchronized void update(Task replacement) throws IOException {
        Objects.requireNonNull(replacement, "replacement");
        for (int index = 0; index < tasks.size(); index++) {
            if (tasks.get(index).id().equals(replacement.id())) {
                tasks.set(index, replacement);
                save();
                return;
            }
        }
        throw new IllegalArgumentException("Unknown task id: " + replacement.id());
    }

    public synchronized boolean delete(String id) throws IOException {
        boolean removed = tasks.removeIf(task -> task.id().equals(id));
        if (removed) {
            save();
        }
        return removed;
    }

    public synchronized void markCompleted(String id, boolean completed) throws IOException {
        Task task = requireTask(id);
        updateWithoutSave(task.withCompleted(completed));
        save();
    }

    public synchronized void postponeDueDate(String id, int days, Clock clock) throws IOException {
        Objects.requireNonNull(clock, "clock");
        Task task = requireTask(id);
        updateWithoutSave(task.withDueDate(TaskDates.postpone(task.dueDate(), days, clock)));
        save();
    }

    public synchronized void replaceAll(Collection<Task> replacements) throws IOException {
        Objects.requireNonNull(replacements, "replacements");
        List<Task> normalized = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        int ordinal = 1;
        for (Task candidate : replacements) {
            if (candidate == null) {
                continue;
            }
            String id = candidate.id();
            if (!ids.add(id)) {
                id = uniqueId(id, ids);
                candidate = new Task(id, candidate.values());
            }
            normalized.add(candidate);
            ordinal++;
        }
        tasks.clear();
        tasks.addAll(normalized);
        save();
    }

    public synchronized void save() throws IOException {
        Path parent = localFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        List<List<String>> rows = new ArrayList<>();
        rows.add(LOCAL_HEADERS);
        for (Task task : tasks) {
            ArrayList<String> row = new ArrayList<>(Task.FIELD_COUNT + 1);
            row.add(task.id());
            row.addAll(task.values());
            rows.add(row);
        }
        try (var writer = Files.newBufferedWriter(localFile, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE)) {
            CsvCodec.write(writer, rows);
        }
    }

    /** Reads a source export and creates deterministic local task ids without changing the source. */
    public static List<Task> importFrom(Path sourceFile) throws IOException {
        Objects.requireNonNull(sourceFile, "sourceFile");
        List<List<String>> rows = CsvCodec.read(sourceFile);
        List<Task> imported = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        Map<String, Integer> occurrences = new HashMap<>();
        int ordinal = 1;
        int rowIndex = 0;
        for (List<String> row : rows) {
            rowIndex++;
            if (isHeader(row) || isBlankRow(row)) {
                continue;
            }
            if (row.size() != Task.FIELD_COUNT) {
                throw CsvFormatException.atRow(rowIndex, "found " + row.size() + " fields, expected "
                        + Task.FIELD_COUNT + " source fields");
            }
            List<String> values = normalizeSourceValues(row);
            String signature = String.join("\u001f", values);
            int occurrence = occurrences.merge(signature, 1, Integer::sum);
            String id = Task.generatedId(values, occurrence);
            if (!ids.add(id)) {
                id = uniqueId(id, ids);
            }
            imported.add(new Task(id, values));
            ordinal++;
        }
        return imported;
    }

    public static List<List<String>> sourceRows(Collection<Task> tasks) {
        Objects.requireNonNull(tasks, "tasks");
        List<List<String>> rows = new ArrayList<>();
        rows.add(Task.SOURCE_HEADERS);
        for (Task task : tasks) {
            if (task != null) {
                rows.add(task.values());
            }
        }
        return rows;
    }

    public static List<List<String>> localRows(Collection<Task> tasks) {
        Objects.requireNonNull(tasks, "tasks");
        List<List<String>> rows = new ArrayList<>();
        rows.add(LOCAL_HEADERS);
        for (Task task : tasks) {
            if (task != null) {
                ArrayList<String> row = new ArrayList<>();
                row.add(task.id());
                row.addAll(task.values());
                rows.add(row);
            }
        }
        return rows;
    }

    private void loadExisting() throws IOException {
        if (!Files.isRegularFile(localFile) || Files.size(localFile) == 0) {
            return;
        }
        List<List<String>> rows = CsvCodec.read(localFile);
        Set<String> ids = new HashSet<>();
        Map<String, Integer> explicitIds = new HashMap<>();
        int ordinal = 1;
        int rowIndex = 0;
        for (List<String> row : rows) {
            rowIndex++;
            if (isHeader(row) || isBlankRow(row)) {
                continue;
            }
            String id;
            List<String> values;
            if (row.size() == Task.FIELD_COUNT + 1) {
                id = row.get(0).trim();
                values = normalizeSourceValues(row.subList(1, row.size()));
            } else if (row.size() == Task.FIELD_COUNT) {
                id = "";
                values = normalizeSourceValues(row);
            } else {
                throw CsvFormatException.atRow(rowIndex, "found " + row.size() + " fields, expected "
                        + Task.FIELD_COUNT + " source fields with or without a leading id");
            }
            if (id.isBlank()) {
                id = uniqueId(Task.generatedId(values, ordinal), ids);
            } else {
                Integer firstRow = explicitIds.putIfAbsent(id, rowIndex);
                if (firstRow != null) {
                    throw CsvFormatException.atRow(rowIndex,
                            "duplicate task id '" + id + "' (already used in row " + firstRow + ")");
                }
                ids.add(id);
            }
            tasks.add(new Task(id, values));
            ordinal++;
        }
    }

    private synchronized Task requireTask(String id) {
        return findById(id).orElseThrow(() -> new IllegalArgumentException("Unknown task id: " + id));
    }

    private void updateWithoutSave(Task replacement) {
        for (int index = 0; index < tasks.size(); index++) {
            if (tasks.get(index).id().equals(replacement.id())) {
                tasks.set(index, replacement);
                return;
            }
        }
        throw new IllegalArgumentException("Unknown task id: " + replacement.id());
    }

    private static List<String> normalizeSourceValues(List<String> row) {
        ArrayList<String> values = new ArrayList<>(Task.FIELD_COUNT);
        for (int index = 0; index < Task.FIELD_COUNT; index++) {
            values.add(index < row.size() && row.get(index) != null ? row.get(index) : "");
        }
        return values;
    }

    private static boolean isHeader(List<String> row) {
        if (row == null || row.isEmpty()) {
            return false;
        }
        String first = row.get(0) == null ? "" : row.get(0).stripLeading();
        return first.equalsIgnoreCase("id") || first.equalsIgnoreCase("nombre");
    }

    private static boolean isBlankRow(List<String> row) {
        return row == null || row.isEmpty() || row.stream().allMatch(value -> value == null || value.isBlank());
    }

    private static String uniqueId(String base, Set<String> ids) {
        String candidate = base;
        int suffix = 2;
        while (!ids.add(candidate)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }
}
