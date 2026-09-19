package taskmanager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** Resolves where exports are written. */
public final class Exports {
    private static final String DEFAULT_DIRECTORY_NAME = "exports";

    /**
     * The directory to offer, and, when preparation failed, why the caller must say so.
     *
     * <p>The directory is returned either way: a missing default folder is a reason to warn, not a
     * reason to refuse an export, because the file chooser can write somewhere else.</p>
     */
    public record Prepared(Path directory, Optional<String> problem) {
        public Prepared {
            Objects.requireNonNull(directory, "directory");
            Objects.requireNonNull(problem, "problem");
        }

        public boolean hasProblem() {
            return problem.isPresent();
        }
    }

    private Exports() {
    }

    /**
     * Prepares the default export folder without ever throwing.
     *
     * <p>The previous behaviour caught the failure and discarded it, so an export dialog opened on a
     * location that did not exist with no explanation. Returning the problem instead of swallowing it
     * keeps the export unblocked while making the failure visible.</p>
     */
    public static Prepared prepareDefaultDirectory(Path appDirectory) {
        Objects.requireNonNull(appDirectory, "appDirectory");
        Path directory = appDirectory.resolve(DEFAULT_DIRECTORY_NAME);
        try {
            Files.createDirectories(directory);
            return new Prepared(directory, Optional.empty());
        } catch (IOException error) {
            return new Prepared(directory, Optional.of(
                    "Could not prepare the default export folder " + directory + ": " + error));
        }
    }
}
