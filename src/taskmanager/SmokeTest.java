package taskmanager;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Headless verification path for environments without an X display. */
public final class SmokeTest {
    private SmokeTest() {
    }

    public static String run(Path appDirectory) throws IOException {
        AppStartup.Prepared prepared = AppStartup.prepare(appDirectory);
        List<Task> tasks = prepared.repository().tasks();
        for (Task task : tasks) {
            if (task.values().size() != Task.FIELD_COUNT) {
                throw new IOException("Smoke check found a task without fourteen source fields: " + task.id());
            }
        }

        List<List<String>> sample = List.of(
                List.of("A, task", "line one\nline two with \"quotes\""),
                List.of("plain", ""));
        StringWriter writer = new StringWriter();
        CsvCodec.write(writer, sample);
        if (!CsvCodec.read(new StringReader(writer.toString())).equals(sample)) {
            throw new IOException("Smoke check CSV round-trip failed");
        }
        if (!Files.isRegularFile(prepared.localFile())) {
            throw new IOException("Smoke check local file was not created: " + prepared.localFile());
        }

        long completed = tasks.stream().filter(Task::isCompleted).count();
        long active = tasks.size() - completed;
        String source = prepared.importedSource().map(Path::toString).orElse("local copy or none");
        return "SMOKE_TEST_PASSED tasks=" + tasks.size()
                + " active=" + active
                + " completed=" + completed
                + " local=" + prepared.localFile()
                + " source=" + source;
    }
}
