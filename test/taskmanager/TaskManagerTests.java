package taskmanager;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Dependency-free behavioral tests. Run with: ./run-tests.sh */
public final class TaskManagerTests {
    private static int testsRun;

    private TaskManagerTests() {
    }

    public static void main(String[] args) throws Exception {
        runTest("quoted CSV parsing and round-trip", TaskManagerTests::testQuotedCsvParsingAndRoundTrip);
        runTest("largest source discovery is deterministic",
                TaskManagerTests::testLargestSourceDiscoveryIsDeterministic);
        runTest("task filtering by text and facets", TaskManagerTests::testTaskFilteringByTextAndFacets);
        runTest("completion and postponement behavior",
                TaskManagerTests::testCompletionAndPostponementBehavior);
        runTest("first run without a source creates no empty local file",
                TaskManagerTests::testFirstRunWithoutSourceDoesNotCreateEmptyLocalFile);
        runTest("configured paths override defaults", TaskManagerTests::testConfiguredPathsOverrideDefaults);
        runTest("source selection is pure and deterministic",
                TaskManagerTests::testSourceSelectionIsPureAndDeterministic);
        runTest("unreadable source is reported as a checked failure",
                TaskManagerTests::testUnreadableSourceIsReportedNotThrownUnchecked);
        runTest("prepared result carries the application directory",
                TaskManagerTests::testPreparedCarriesTheApplicationDirectory);
        runTest("strict CSV rejects trailing garbage",
                TaskManagerTests::testStrictCsvRejectsTrailingGarbage);
        runTest("CSV reports the unterminated quote position",
                TaskManagerTests::testCsvReportsUnterminatedQuotePosition);
        runTest("source import rejects the wrong field count",
                TaskManagerTests::testSourceImportRejectsWrongFieldCount);
        runTest("local file rejects duplicate ids", TaskManagerTests::testLocalFileRejectsDuplicateIds);
        runTest("local file rejects the wrong field count",
                TaskManagerTests::testLocalFileRejectsWrongFieldCount);
        runTest("saving replaces the file instead of truncating it",
                TaskManagerTests::testSaveReplacesTheFileInsteadOfTruncatingIt);
        runTest("import backs up the previous local file",
                TaskManagerTests::testImportBacksUpThePreviousLocalFile);
        runTest("import without previous data creates no backup",
                TaskManagerTests::testImportWithoutPreviousDataCreatesNoBackup);
        runTest("postpone rejects an unparseable date",
                TaskManagerTests::testPostponeRejectsAnUnparseableDate);
        runTest("postpone starts from today when the task has no date",
                TaskManagerTests::testPostponeUsesTodayWhenTheTaskHasNoDate);
        runTest("postpone rejects negative days", TaskManagerTests::testPostponeRejectsNegativeDays);
        runTest("export directory preparation succeeds", TaskManagerTests::testExportDirectoryPreparationSucceeds);
        runTest("export directory preparation reports failure",
                TaskManagerTests::testExportDirectoryPreparationReportsFailure);
        runTest("area tokens use one split rule", TaskManagerTests::testAreaTokensUseOneSplitRule);
        runTest("area facets are case-insensitive and sorted",
                TaskManagerTests::testAreaFacetsAreCaseInsensitiveAndSorted);
        runTest("hasArea keeps its existing rule", TaskManagerTests::testTaskHasAreaKeepsItsExistingRule);
        runTest("task order ranks completion, then urgency, then importance",
                TaskManagerTests::testTaskOrderRanksCompletionThenUrgencyThenImportance);
        runTest("task order places unreadable dates last and breaks ties by name",
                TaskManagerTests::testTaskOrderPlacesUnreadableDatesLastAndBreaksTiesByName);
        System.out.println("ALL_TESTS_PASSED " + testsRun);
    }

    @FunctionalInterface
    private interface TestBody {
        void run() throws Exception;
    }

    private static void runTest(String name, TestBody body) throws Exception {
        try {
            body.run();
            testsRun++;
        } catch (AssertionError failure) {
            throw new AssertionError("FAILED " + name + ": " + failure.getMessage(), failure);
        }
    }

    private static void testFirstRunWithoutSourceDoesNotCreateEmptyLocalFile() throws IOException {
        Path appDirectory = Files.createTempDirectory("task-startup-test");
        Path sourceRoot = Files.createTempDirectory("task-empty-source");
        try {
            AppConfig config = AppConfig.resolve(Map.of(AppConfig.SOURCE_ROOT_ENV, sourceRoot.toString()),
                    appDirectory, appDirectory);

            AppStartup.Prepared first = AppStartup.prepare(config);
            check(first.importedSource().isEmpty(), "no import happens without a source export");
            check(!Files.exists(first.localFile()),
                    "a first run without a source must not create an empty local file, because it"
                            + " silently disables auto-import on every later launch");
            check(first.status().contains(sourceRoot.toString()),
                    "the status reports which source root was searched");

            List<List<String>> exportRows = new ArrayList<>();
            exportRows.add(Task.SOURCE_HEADERS);
            exportRows.add(List.of("Imported later", "Work", "", "2026-09-20", "No", "English", "",
                    "", "Project", "", "Actionable", "High", "", "Planning"));
            StringWriter exportText = new StringWriter();
            CsvCodec.write(exportText, exportRows);
            Files.writeString(sourceRoot.resolve("vault_all.csv"), exportText.toString(),
                    StandardCharsets.UTF_8);

            AppStartup.Prepared second = AppStartup.prepare(config);
            check(second.importedSource().isPresent(),
                    "a later launch imports automatically once an export appears instead of staying blocked");
            check(second.repository().size() == 1, "the later export is imported");
            check(Files.isRegularFile(second.localFile()), "a successful import persists the local file");
        } finally {
            deleteTree(appDirectory);
            deleteTree(sourceRoot);
        }
    }

    private static void testPreparedCarriesTheApplicationDirectory() throws IOException {
        Path appDirectory = Files.createTempDirectory("task-prepared-test");
        Path dataDirectory = Files.createTempDirectory("task-prepared-data");
        try {
            AppConfig config = AppConfig.resolve(
                    Map.of(AppConfig.DATA_DIRECTORY_ENV, dataDirectory.toString(),
                            AppConfig.SOURCE_ROOT_ENV, dataDirectory.resolve("no-source").toString()),
                    appDirectory, appDirectory);
            AppStartup.Prepared prepared = AppStartup.prepare(config);

            check(prepared.localFile().equals(dataDirectory.resolve("tasks.csv")),
                    "the local file follows the configured data directory");
            check(prepared.appDirectory().equals(appDirectory.toAbsolutePath().normalize()),
                    "the application directory must not be derived from the local file path");
            check(prepared.sourceRoot().equals(dataDirectory.resolve("no-source")),
                    "the prepared result exposes the configured source root");
        } finally {
            deleteTree(appDirectory);
            deleteTree(dataDirectory);
        }
    }

    private static void testConfiguredPathsOverrideDefaults() throws IOException {
        Path appDirectory = Files.createTempDirectory("task-config-test");
        Path userHome = Files.createTempDirectory("task-home-test");
        Path elsewhere = Files.createTempDirectory("task-data-test");
        try {
            AppConfig defaults = AppConfig.resolve(Map.of(), appDirectory, userHome);
            check(defaults.appDirectory().equals(appDirectory.toAbsolutePath().normalize()),
                    "the application directory is normalized");
            check(defaults.dataDirectory().equals(appDirectory.resolve("data")),
                    "the default data directory is app/data");
            check(defaults.localFile().equals(appDirectory.resolve("data").resolve("tasks.csv")),
                    "the default local file is app/data/tasks.csv");
            check(defaults.sourceRoot().equals(userHome.resolve("Documents").resolve("Obsidian Vault")
                    .resolve("GDT").resolve("01_Tareas")), "the default source root lives under the user home");

            AppConfig relative = AppConfig.resolve(
                    Map.of(AppConfig.DATA_DIRECTORY_ENV, "local-data"), appDirectory, userHome);
            check(relative.dataDirectory().equals(appDirectory.resolve("local-data")),
                    "a relative data directory resolves against the application directory");

            AppConfig absolute = AppConfig.resolve(
                    Map.of(AppConfig.DATA_DIRECTORY_ENV, elsewhere.toString(),
                            AppConfig.SOURCE_ROOT_ENV, "~/Exports"),
                    appDirectory, userHome);
            check(absolute.dataDirectory().equals(elsewhere.toAbsolutePath().normalize()),
                    "an absolute data directory is used as given");
            check(absolute.localFile().equals(elsewhere.resolve("tasks.csv")),
                    "the local file follows the configured data directory");
            check(absolute.sourceRoot().equals(userHome.resolve("Exports")),
                    "a tilde source root expands to the user home");

            AppConfig blank = AppConfig.resolve(
                    Map.of(AppConfig.DATA_DIRECTORY_ENV, "   ", AppConfig.SOURCE_ROOT_ENV, ""),
                    appDirectory, userHome);
            check(blank.dataDirectory().equals(appDirectory.resolve("data")),
                    "a blank data directory falls back to the default");
            check(blank.sourceRoot().equals(defaults.sourceRoot()),
                    "a blank source root falls back to the default");
        } finally {
            deleteTree(appDirectory);
            deleteTree(userHome);
            deleteTree(elsewhere);
        }
    }

    private static void testSourceSelectionIsPureAndDeterministic() {
        check(SourceCsvFinder.selectLargest(List.of()).isEmpty(), "no candidates selects nothing");

        Path tieA = Path.of("ties", "a_all.csv");
        Path tieB = Path.of("ties", "b_all.csv");
        Path biggest = Path.of("other", "c_all.csv");
        List<SourceCsvFinder.Candidate> candidates = List.of(
                new SourceCsvFinder.Candidate(tieB, 10L),
                new SourceCsvFinder.Candidate(biggest, 99L),
                new SourceCsvFinder.Candidate(tieA, 10L));
        check(SourceCsvFinder.selectLargest(candidates).orElseThrow().equals(biggest),
                "the largest candidate wins regardless of input order");
        check(SourceCsvFinder.selectLargest(List.of(
                new SourceCsvFinder.Candidate(tieB, 10L),
                new SourceCsvFinder.Candidate(tieA, 10L))).orElseThrow().equals(tieA),
                "same-size candidates fall back to lexicographic order");
    }

    private static void testUnreadableSourceIsReportedNotThrownUnchecked() throws IOException {
        Path root = Files.createTempDirectory("task-unreadable-test");
        Path restricted = Files.createDirectory(root.resolve("restricted"));
        Path export = root.resolve("vault_all.csv");
        Files.writeString(export, "content", StandardCharsets.UTF_8);
        try {
            Files.setPosixFilePermissions(restricted, Set.of());
            if (Files.isReadable(restricted)) {
                System.out.println("SKIPPED unreadable source diagnostic: permissions not enforced for this user");
                return;
            }

            SourceCsvFinder.Discovery discovery = SourceCsvFinder.discover(root);
            check(discovery.selected().orElseThrow().equals(export.toAbsolutePath().normalize()),
                    "an unreadable folder must not stop discovery of readable exports");
            check(discovery.hasProblems(), "the unreadable folder is reported instead of failing silently");
            check(discovery.problems().stream().anyMatch(problem -> problem.contains("restricted")),
                    "the reported problem names the unreadable path, got: " + discovery.problems());
        } finally {
            Files.setPosixFilePermissions(restricted, PosixFilePermissions.fromString("rwx------"));
            deleteTree(root);
        }
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

            SourceCsvFinder.Discovery discovery = SourceCsvFinder.discover(root);
            check(discovery.selected().orElseThrow().equals(second), "largest *_all.csv selected");
            check(!discovery.hasProblems(), "a readable tree reports no problems: " + discovery.problems());

            Path tieRoot = Files.createDirectories(root.resolve("ties"));
            Path tieA = tieRoot.resolve("a_all.csv");
            Path tieB = tieRoot.resolve("b_all.csv");
            Files.writeString(tieA, "same", StandardCharsets.UTF_8);
            Files.writeString(tieB, "same", StandardCharsets.UTF_8);
            check(SourceCsvFinder.discover(tieRoot).selected().orElseThrow().equals(tieA),
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

    private static void testStrictCsvRejectsTrailingGarbage() throws IOException {
        List<List<String>> tolerated = CsvCodec.read(new StringReader("a,\"b\"  ,c\n"));
        check(tolerated.get(0).equals(List.of("a", "b", "c")),
                "whitespace between a closing quote and the delimiter is tolerated");

        try {
            CsvCodec.read(new StringReader("ok,fine\n\"a\"b,c\n"));
            check(false, "text after a closing quote must be rejected, not silently concatenated");
        } catch (CsvFormatException expected) {
            check(expected.line() == 2, "the failure reports the physical line, got " + expected.line());
            check(expected.column() == 4, "the failure reports the column, got " + expected.column());
        }
    }

    private static void testCsvReportsUnterminatedQuotePosition() throws IOException {
        try {
            CsvCodec.read(new StringReader("fine,ok\n\"unterminated\nnext,row\n"));
            check(false, "an unterminated quoted field must be rejected");
        } catch (CsvFormatException expected) {
            check(expected.line() == 2,
                    "the failure reports where the quoted field opened, got " + expected.line());
            check(expected.column() == 1,
                    "the failure reports the opening column, got " + expected.column());
        }
    }

    private static void testSourceImportRejectsWrongFieldCount() throws IOException {
        Path source = Files.createTempFile("task-source-fields", ".csv");
        try {
            List<List<String>> valid = new ArrayList<>();
            valid.add(Task.SOURCE_HEADERS);
            valid.add(fieldRow(Task.FIELD_COUNT));
            writeRows(source, valid);
            check(TaskRepository.importFrom(source).size() == 1, "a complete source row still imports");

            List<List<String>> shortRow = new ArrayList<>();
            shortRow.add(Task.SOURCE_HEADERS);
            shortRow.add(fieldRow(Task.FIELD_COUNT - 1));
            writeRows(source, shortRow);
            try {
                TaskRepository.importFrom(source);
                check(false, "a short source row must be rejected instead of silently padded");
            } catch (CsvFormatException expected) {
                check(expected.getMessage().contains("row 2"),
                        "the failure names the row, got: " + expected.getMessage());
            }

            List<List<String>> longRow = new ArrayList<>();
            longRow.add(Task.SOURCE_HEADERS);
            longRow.add(fieldRow(Task.FIELD_COUNT + 1));
            writeRows(source, longRow);
            try {
                TaskRepository.importFrom(source);
                check(false, "an over-long source row must be rejected instead of silently truncated");
            } catch (CsvFormatException expected) {
                check(expected.getMessage().contains(String.valueOf(Task.FIELD_COUNT + 1)),
                        "the failure reports the field count found, got: " + expected.getMessage());
            }
        } finally {
            Files.deleteIfExists(source);
        }
    }

    private static void testLocalFileRejectsDuplicateIds() throws IOException {
        Path localFile = Files.createTempFile("task-local-duplicates", ".csv");
        try {
            List<List<String>> rows = new ArrayList<>();
            rows.add(TaskRepository.LOCAL_HEADERS);
            rows.add(rowWithId("dup"));
            rows.add(rowWithId("dup"));
            writeRows(localFile, rows);

            try {
                new TaskRepository(localFile);
                check(false, "duplicate ids in the local file must be reported, not silently renamed");
            } catch (CsvFormatException expected) {
                check(expected.getMessage().contains("dup"),
                        "the failure names the duplicated id, got: " + expected.getMessage());
            }
        } finally {
            Files.deleteIfExists(localFile);
        }
    }

    private static void testLocalFileRejectsWrongFieldCount() throws IOException {
        Path localFile = Files.createTempFile("task-local-fields", ".csv");
        try {
            List<List<String>> rows = new ArrayList<>();
            rows.add(TaskRepository.LOCAL_HEADERS);
            rows.add(fieldRow(10));
            writeRows(localFile, rows);

            try {
                new TaskRepository(localFile);
                check(false, "a local row with the wrong field count must be reported");
            } catch (CsvFormatException expected) {
                check(expected.getMessage().contains("row 2"),
                        "the failure names the row, got: " + expected.getMessage());
            }
        } finally {
            Files.deleteIfExists(localFile);
        }
    }

    private static void testSaveReplacesTheFileInsteadOfTruncatingIt() throws IOException {
        Path dataDirectory = Files.createTempDirectory("task-atomic-test");
        try {
            Path localFile = dataDirectory.resolve("tasks.csv");
            TaskRepository repository = new TaskRepository(localFile);
            repository.add(task("1", "First", "Work", "", "No", "", "", ""));
            Object firstKey = Files.readAttributes(localFile, BasicFileAttributes.class).fileKey();

            repository.add(task("2", "Second", "Work", "", "No", "", "", ""));

            String after = Files.readString(localFile, StandardCharsets.UTF_8);
            Object secondKey = Files.readAttributes(localFile, BasicFileAttributes.class).fileKey();
            check(after.contains("First") && after.contains("Second"), "the second save keeps both tasks");
            check(firstKey != null && secondKey != null && !firstKey.equals(secondKey),
                    "saving must replace the file instead of truncating it in place, because a crash"
                            + " during a truncating write leaves an empty or half-written task list");
            check(listTempFiles(dataDirectory).isEmpty(),
                    "a routine save leaves no temporary file behind, got " + listTempFiles(dataDirectory));
            check(listBackups(dataDirectory).isEmpty(),
                    "a routine save does not spam backups, got " + listBackups(dataDirectory));
        } finally {
            deleteTree(dataDirectory);
        }
    }

    private static void testImportBacksUpThePreviousLocalFile() throws IOException {
        Path dataDirectory = Files.createTempDirectory("task-backup-test");
        try {
            Path localFile = dataDirectory.resolve("tasks.csv");
            TaskRepository repository = new TaskRepository(localFile);
            repository.add(task("1", "Survives the import", "Work", "", "No", "", "", ""));

            Path source = dataDirectory.resolve("vault_all.csv");
            List<List<String>> rows = new ArrayList<>();
            rows.add(Task.SOURCE_HEADERS);
            rows.add(fieldRow(Task.FIELD_COUNT));
            writeRows(source, rows);

            repository.replaceAll(TaskRepository.importFrom(source));

            List<Path> backups = listBackups(dataDirectory);
            check(backups.size() == 1, "replacing the local list creates exactly one backup, got " + backups);
            String backupName = backups.get(0).getFileName().toString();
            check(backupName.matches("tasks\\.csv\\.\\d{8}-\\d{6}\\.bak"),
                    "the backup is named after the replacement timestamp, got " + backupName);
            check(Files.readString(backups.get(0), StandardCharsets.UTF_8).contains("Survives the import"),
                    "the backup holds the previous local content");
            check(!Files.readString(localFile, StandardCharsets.UTF_8).contains("Survives the import"),
                    "the local file now holds the imported content");
        } finally {
            deleteTree(dataDirectory);
        }
    }

    private static void testImportWithoutPreviousDataCreatesNoBackup() throws IOException {
        Path dataDirectory = Files.createTempDirectory("task-nobackup-test");
        try {
            Path localFile = dataDirectory.resolve("tasks.csv");
            TaskRepository repository = new TaskRepository(localFile);
            Path source = dataDirectory.resolve("vault_all.csv");
            List<List<String>> rows = new ArrayList<>();
            rows.add(Task.SOURCE_HEADERS);
            rows.add(fieldRow(Task.FIELD_COUNT));
            writeRows(source, rows);

            repository.replaceAll(TaskRepository.importFrom(source));

            check(listBackups(dataDirectory).isEmpty(),
                    "a first import has no previous local file to back up, got " + listBackups(dataDirectory));
        } finally {
            deleteTree(dataDirectory);
        }
    }

    private static List<Path> listBackups(Path directory) throws IOException {
        return listBySuffix(directory, ".bak");
    }

    private static List<Path> listTempFiles(Path directory) throws IOException {
        return listBySuffix(directory, ".tmp");
    }

    private static List<Path> listBySuffix(Path directory, String suffix) throws IOException {
        try (var paths = Files.list(directory)) {
            return paths.filter(path -> path.getFileName().toString().endsWith(suffix)).sorted().toList();
        }
    }

    private static void testPostponeRejectsAnUnparseableDate() throws IOException {
        Path localFile = Files.createTempFile("task-postpone-invalid", ".csv");
        try {
            TaskRepository repository = new TaskRepository(localFile);
            repository.add(task("7", "Bad date", "Work", "sometime next week", "No", "High", "", "Planning"));

            Clock fixed = Clock.fixed(Instant.parse("2026-09-09T12:00:00Z"), ZoneOffset.UTC);
            try {
                repository.postponeDueDate("7", 3, fixed);
                check(false, "postponing an unparseable date must fail loudly instead of silently"
                        + " publishing today plus the shift as if it were the task's own date");
            } catch (IllegalArgumentException expected) {
                check(expected.getMessage().contains("sometime next week"),
                        "the failure names the offending value, got: " + expected.getMessage());
            }
            check(repository.findById("7").orElseThrow().dueDate().equals("sometime next week"),
                    "a refused postponement must leave the stored date untouched");
        } finally {
            Files.deleteIfExists(localFile);
        }
    }

    private static void testPostponeUsesTodayWhenTheTaskHasNoDate() {
        Clock fixed = Clock.fixed(Instant.parse("2026-09-09T12:00:00Z"), ZoneOffset.UTC);
        String postponed = TaskDates.postpone("", 3, fixed);
        check(TaskDates.parse(postponed).orElseThrow().equals(LocalDate.of(2026, 9, 12)),
                "a blank due date postpones from today, which is a convenience rather than a silent"
                        + " repair, so it must keep working; got " + postponed);
    }

    private static void testPostponeRejectsNegativeDays() throws IOException {
        Path localFile = Files.createTempFile("task-postpone-negative", ".csv");
        try {
            TaskRepository repository = new TaskRepository(localFile);
            repository.add(task("8", "Future", "Work", "2026-09-20", "No", "High", "", "Planning"));

            Clock fixed = Clock.fixed(Instant.parse("2026-09-09T12:00:00Z"), ZoneOffset.UTC);
            try {
                repository.postponeDueDate("8", -1, fixed);
                check(false, "a negative postponement must be refused");
            } catch (IllegalArgumentException expected) {
                check(expected.getMessage().contains("negative"),
                        "the failure explains the rule, got: " + expected.getMessage());
            }
            check(repository.findById("8").orElseThrow().dueDate().equals("2026-09-20"),
                    "a refused postponement must leave the stored date untouched");
        } finally {
            Files.deleteIfExists(localFile);
        }
    }

    private static void testExportDirectoryPreparationSucceeds() throws IOException {
        Path appDirectory = Files.createTempDirectory("task-export-ok");
        try {
            Exports.Prepared prepared = Exports.prepareDefaultDirectory(appDirectory);
            check(prepared.problem().isEmpty(), "a writable application directory reports no problem");
            check(prepared.directory().equals(appDirectory.resolve("exports")),
                    "the default export directory lives under the application directory");
            check(Files.isDirectory(prepared.directory()), "the default export directory is created");
        } finally {
            deleteTree(appDirectory);
        }
    }

    private static void testExportDirectoryPreparationReportsFailure() throws IOException {
        Path appDirectory = Files.createTempDirectory("task-export-blocked");
        try {
            Path blocked = appDirectory.resolve("exports");
            Files.writeString(blocked, "a plain file where the export folder should be",
                    StandardCharsets.UTF_8);

            Exports.Prepared prepared = Exports.prepareDefaultDirectory(appDirectory);
            check(prepared.problem().isPresent(),
                    "a folder that cannot be created must be reported instead of swallowed, because the"
                            + " file chooser then opens on a location that does not exist and the user is"
                            + " never told why");
            String message = prepared.problem().orElseThrow();
            check(message.contains("exports"), "the report names the folder, got: " + message);
            check(prepared.directory().equals(blocked),
                    "the requested directory is still returned so an export can fall back elsewhere");
        } finally {
            deleteTree(appDirectory);
        }
    }

    private static void testAreaTokensUseOneSplitRule() {
        List<String> tokens = AreaTokens.of("Work, Home; Errands|Planning\nIdeas");
        check(tokens.equals(List.of("Work", "Home", "Errands", "Planning", "Ideas")),
                "every documented separator splits the area field, got " + tokens);
        check(AreaTokens.of(" , ;; ").isEmpty(), "blank tokens are dropped instead of becoming facets");
        check(AreaTokens.of(null).isEmpty(), "a missing area field has no tokens");
        check(AreaTokens.of("  Home  ").equals(List.of("Home")), "tokens are trimmed");
    }

    private static void testAreaFacetsAreCaseInsensitiveAndSorted() {
        List<Task> tasks = List.of(
                task("1", "A", "", "", "No", "", "", "Work, Home"),
                task("2", "B", "", "", "No", "", "", "work, Errands"),
                task("3", "C", "", "", "No", "", "", ""));
        List<String> facets = AreaTokens.across(tasks);
        check(facets.equals(List.of("Errands", "Home", "Work")),
                "facets are deduplicated case-insensitively and sorted, got " + facets);
    }

    private static void testTaskHasAreaKeepsItsExistingRule() {
        Task task = task("1", "A", "", "", "No", "", "", "Work, Home");
        check(task.hasArea("home"), "an area token matches case-insensitively");
        check(task.hasArea(""), "an empty request matches every task");
        check(!task.hasArea("Hom"), "a partial token must not match, or the filter silently widens");
        check(!task.hasArea("Office"), "an unknown area does not match");
    }

    private static void testTaskOrderRanksCompletionThenUrgencyThenImportance() {
        Task completed = task("1", "done", "", "2026-01-01", "Yes", "muy importante", "muy urgente", "");
        Task low = task("2", "low", "", "2026-01-01", "No", "no importante", "poca urgencia", "");
        Task high = task("3", "high", "", "2026-01-01", "No", "muy importante", "muy urgente", "");

        List<Task> sorted = new ArrayList<>(List.of(completed, low, high));
        sorted.sort(TaskOrder.DEFAULT);
        check(ids(sorted).equals(List.of("3", "2", "1")),
                "active work first, most urgent before least, completed last; got " + ids(sorted));
    }

    private static void testTaskOrderPlacesUnreadableDatesLastAndBreaksTiesByName() {
        Task blank = task("1", "Zebra", "", "", "No", "", "", "");
        Task unreadable = task("2", "Alpha", "", "sometime", "No", "", "", "");
        Task dated = task("3", "Beta", "", "2026-01-01", "No", "", "", "");

        List<Task> sorted = new ArrayList<>(List.of(blank, unreadable, dated));
        sorted.sort(TaskOrder.DEFAULT);
        check(ids(sorted).equals(List.of("3", "2", "1")),
                "a real date sorts first and both unreadable dates fall to the end"
                        + " in name order; got " + ids(sorted));

        Task alpha = task("4", "alpha", "", "2026-01-01", "No", "", "", "");
        Task beta = task("5", "Beta", "", "2026-01-01", "No", "", "", "");
        List<Task> ties = new ArrayList<>(List.of(beta, alpha));
        ties.sort(TaskOrder.DEFAULT);
        check(ids(ties).equals(List.of("4", "5")),
                "equal keys break by name case-insensitively, got " + ids(ties));
    }

    private static List<String> ids(List<Task> tasks) {
        return tasks.stream().map(Task::id).toList();
    }

    private static List<String> fieldRow(int fieldCount) {
        List<String> row = new ArrayList<>(fieldCount);
        for (int index = 0; index < fieldCount; index++) {
            row.add("field" + (index + 1));
        }
        return row;
    }

    private static List<String> rowWithId(String id) {
        List<String> row = new ArrayList<>(fieldRow(Task.FIELD_COUNT));
        row.add(0, id);
        return row;
    }

    private static void writeRows(Path path, List<List<String>> rows) throws IOException {
        StringWriter text = new StringWriter();
        CsvCodec.write(text, rows);
        Files.writeString(path, text.toString(), StandardCharsets.UTF_8);
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
