package taskmanager;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Finds the canonical, largest recursively-exported task CSV.
 *
 * <p>Discovery never aborts a launch and never throws for a partially readable tree: unreadable
 * entries are reported as problems so the caller can surface them instead of failing silently.</p>
 */
public final class SourceCsvFinder {
    /** A discovered export together with the size observed while listing it. */
    public record Candidate(Path path, long size) {
        public Candidate {
            Objects.requireNonNull(path, "path");
            if (size < 0) {
                throw new IllegalArgumentException("size must not be negative");
            }
        }
    }

    /** The outcome of a scan: at most one selected export, plus everything that could not be read. */
    public record Discovery(Optional<Path> selected, List<String> problems) {
        public Discovery {
            Objects.requireNonNull(selected, "selected");
            problems = List.copyOf(Objects.requireNonNull(problems, "problems"));
        }

        public boolean hasProblems() {
            return !problems.isEmpty();
        }
    }

    private static final Comparator<Candidate> LARGEST_THEN_LEXICOGRAPHIC =
            Comparator.comparingLong(Candidate::size).reversed()
                    .thenComparing(candidate -> candidate.path().toString());

    private SourceCsvFinder() {
    }

    /**
     * Selects the canonical export from already-listed candidates.
     *
     * <p>Pure: it performs no file system access, so selection cannot fail with an I/O error.</p>
     */
    public static Optional<Path> selectLargest(List<Candidate> candidates) {
        Objects.requireNonNull(candidates, "candidates");
        return candidates.stream()
                .filter(Objects::nonNull)
                .min(LARGEST_THEN_LEXICOGRAPHIC)
                .map(Candidate::path);
    }

    /** Scans {@code root} for candidate exports, reporting anything that could not be read. */
    public static Discovery discover(Path root) {
        Objects.requireNonNull(root, "root");
        if (!Files.isDirectory(root)) {
            return new Discovery(Optional.empty(), List.of());
        }

        List<Candidate> candidates = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    if (attributes.isRegularFile() && isCanonicalName(file)) {
                        candidates.add(new Candidate(file.toAbsolutePath().normalize(), attributes.size()));
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException error) {
                    problems.add("Cannot read " + file + ": " + error);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path directory, IOException error) {
                    if (error != null) {
                        problems.add("Cannot list " + directory + ": " + error);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException error) {
            problems.add("Cannot scan " + root + ": " + error);
        }

        return new Discovery(selectLargest(candidates), problems);
    }

    /** The default search root: {@code <userHome>/Documents/Obsidian Vault/GDT/01_Tareas}. */
    public static Path defaultRoot(Path userHome) {
        return Objects.requireNonNull(userHome, "userHome")
                .resolve("Documents")
                .resolve("Obsidian Vault")
                .resolve("GDT")
                .resolve("01_Tareas");
    }

    private static boolean isCanonicalName(Path path) {
        Path fileName = path.getFileName();
        return fileName != null && fileName.toString().toLowerCase(Locale.ROOT).endsWith("_all.csv");
    }
}
