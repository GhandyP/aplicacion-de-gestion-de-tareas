package taskmanager;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/** Dependency-free behavioral tests. Run with: ./run-tests.sh */
public final class TaskManagerTests {
    private TaskManagerTests() {
    }

    public static void main(String[] args) throws Exception {
        testQuotedCsvParsingAndRoundTrip();
        testLargestSourceDiscoveryIsDeterministic();
        testTaskFilteringByTextAndFacets();
        testCompletionAndPostponementBehavior();
        System.out.println("ALL_TESTS_PASSED 4");
    }

    private static void testQuotedCsvParsingAndRoundTrip() throws IOException {
        String csv = "Name,Notes,Status\n"
                + "\"A, task\",\"line one\nline two with \"\"quotes\"\"\",\"No\"\n";

        List<List<String>> parsed = CsvCodec.read(new StringReader(csv));
        check(parsed.size() == 2, "quoted CSV row count");
        check(parsed.get(1).get(0).equals("A, task"), "quoted comma preserved");
        check(parsed.get(1).get(1).equals("line one\nline two with \"quotes\""),
                "quoted newline and quotes preserved");

        StringWriter output = new StringWriter();
        CsvCodec.write(output, parsed);
        List<List<String>> roundTrip = CsvCodec.read(new StringReader(output.toString()));
        check(roundTrip.equals(parsed), "CSV round-trip preserves values");
    }

    private static void testLargestSourceDiscoveryIsDeterministic() throws IOException {
        Path root = Files.createTempDirectory("task-source-test");
        try {
            Path first = Files.createDirectories(root.resolve("a")).resolve("first_all.csv");
            Path second = Files.createDirectories(root.resolve("z")).resolve("second_all.csv");
            Path ignored = root.resolve("not_an_export.csv");
            Files.writeString(first, "small", StandardCharsets.UTF_8);
            Files.writeString(second, "this is the largest export", StandardCharsets.UTF_8);
            Files.writeString(ignored, "this should not be selected", StandardCharsets.UTF_8);

            Path selected = SourceCsvFinder.findLargest(root).orElseThrow();
            check(selected.equals(second), "largest *_all.csv selected");

            Path tieRoot = Files.createDirectories(root.resolve("ties"));
            Path tieA = tieRoot.resolve("a_all.csv");
            Path tieB = tieRoot.resolve("b_all.csv");
            Files.writeString(tieA, "same", StandardCharsets.UTF_8);
            Files.writeString(tieB, "same", StandardCharsets.UTF_8);
            check(SourceCsvFinder.findLargest(tieRoot).orElseThrow().equals(tieA),
                    "same-size source selection is deterministic");
        } finally {
            deleteTree(root);
        }
    }

    private static void testTaskFilteringByTextAndFacets() {
        List<Task> tasks = List.of(
                task("1", "Write launch report", "Work", "2026-09-15", "No",
                        "High", "Muy urgente", "Planning"),
                task("2", "Buy groceries", "Home", "2026-09-16", "Yes",
                        "", "Poca urgencia", "Errands"),
                task("3", "Plan research sprint", "Work", "", "No",
                        "Important", "media urgencia", "Research"));

        TaskFilter.Criteria criteria = new TaskFilter.Criteria(
                "report", TaskFilter.Status.ACTIVE, "High", "Muy urgente", "Planning");
        List<Task> filtered = TaskFilter.apply(tasks, criteria);
        check(filtered.size() == 1 && filtered.get(0).id().equals("1"),
                "text and facet filters combine");

        TaskFilter.Criteria areaCriteria = new TaskFilter.Criteria(
                "", TaskFilter.Status.ALL, "", "", "Research");
        check(TaskFilter.apply(tasks, areaCriteria).size() == 1,
                "area filter matches area token");
    }

    private static void testCompletionAndPostponementBehavior() throws IOException {
        Path localFile = Files.createTempFile("task-repository-test", ".csv");
        try {
            TaskRepository repository = new TaskRepository(localFile);
            repository.add(task("42", "Review milestone", "Work", "September 10, 2026", "No",
                    "High", "", "Planning"));

            repository.markCompleted("42", true);
            check(repository.findById("42").orElseThrow().isCompleted(),
                    "mark complete changes status");
            repository.markCompleted("42", false);
            check(!repository.findById("42").orElseThrow().isCompleted(),
                    "reopen changes status");

            Clock fixed = Clock.fixed(Instant.parse("2026-09-09T12:00:00Z"), ZoneOffset.UTC);
            repository.postponeDueDate("42", 3, fixed);
            check(repository.findById("42").orElseThrow().dueDate().equals("September 13, 2026"),
                    "postpone preserves readable date style and adds days");
        } finally {
            Files.deleteIfExists(localFile);
        }
    }

    private static Task task(String id, String name, String context, String dueDate,
                             String completed, String importance, String urgency, String areas) {
        List<String> values = new ArrayList<>(List.of(
                name, context, "", dueDate, completed, "English", "", "", "Project",
                "", "Actionable", importance, urgency, areas));
        return new Task(id, values);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths.sorted((left, right) -> right.compareTo(left))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException error) {
                            throw new RuntimeException(error);
                        }
                    });
        }
    }
}
