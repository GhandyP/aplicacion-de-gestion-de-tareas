package taskmanager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** Applies the first-run import policy shared by the GUI and smoke mode. */
public final class AppStartup {
    private AppStartup() {
    }

    public record Prepared(TaskRepository repository, Path localFile, Path sourceRoot,
                           Optional<Path> importedSource, String status) {
    }

    public static Prepared prepare(Path appDirectory) throws IOException {
        Path applicationDirectory = appDirectory.toAbsolutePath().normalize();
        Path localFile = applicationDirectory.resolve("data").resolve("tasks.csv");
        Path sourceRoot = SourceCsvFinder.defaultRoot();
        boolean firstRun = !Files.exists(localFile);
        TaskRepository repository = new TaskRepository(localFile);

        if (!firstRun) {
            return new Prepared(repository, localFile, sourceRoot, Optional.empty(),
                    "Loaded " + repository.size() + " local tasks from " + localFile);
        }

        Optional<Path> source = SourceCsvFinder.findLargest(sourceRoot);
        if (source.isPresent()) {
            List<Task> imported = TaskRepository.importFrom(source.get());
            repository.replaceAll(imported);
            return new Prepared(repository, localFile, sourceRoot, source,
                    "Imported " + imported.size() + " tasks from " + source.get());
        }

        repository.save();
        return new Prepared(repository, localFile, sourceRoot, Optional.empty(),
                "No *_all.csv export found under " + sourceRoot
                        + ". Use Refresh / Import after placing an export there.");
    }
}
