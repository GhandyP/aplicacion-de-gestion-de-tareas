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

    public record Prepared(Path appDirectory, TaskRepository repository, Path localFile, Path sourceRoot,
                           Optional<Path> importedSource, String status) {
    }

    /**
     * Prepares the repository for one launch.
     *
     * <p>A first run without a discoverable export leaves no local file behind on purpose: creating an
     * empty one would make every later launch believe the local copy is authoritative and silently
     * disable auto-import.</p>
     */
    public static Prepared prepare(AppConfig config) throws IOException {
        Path appDirectory = config.appDirectory();
        Path localFile = config.localFile();
        Path sourceRoot = config.sourceRoot();
        boolean firstRun = !Files.exists(localFile);
        TaskRepository repository = new TaskRepository(localFile);

        if (!firstRun) {
            return new Prepared(appDirectory, repository, localFile, sourceRoot, Optional.empty(),
                    "Loaded " + repository.size() + " local tasks from " + localFile);
        }

        SourceCsvFinder.Discovery discovery = SourceCsvFinder.discover(sourceRoot);
        Optional<Path> source = discovery.selected();
        String problems = discovery.hasProblems()
                ? " Unreadable entries were skipped: " + String.join(" ", discovery.problems())
                : "";
        if (source.isPresent()) {
            List<Task> imported = TaskRepository.importFrom(source.get());
            repository.replaceAll(imported);
            return new Prepared(appDirectory, repository, localFile, sourceRoot, source,
                    "Imported " + imported.size() + " tasks from " + source.get() + "." + problems);
        }

        return new Prepared(appDirectory, repository, localFile, sourceRoot, Optional.empty(),
                "No *_all.csv export found under " + sourceRoot
                        + ". No local file was created: the next launch imports automatically once an"
                        + " export appears there. Use Refresh / Import to import one now." + problems);
    }
}
