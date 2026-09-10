package taskmanager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/** Finds the canonical, largest recursively-exported task CSV. */
public final class SourceCsvFinder {
    private SourceCsvFinder() {
    }

    public static Optional<Path> findLargest(Path root) throws IOException {
        Objects.requireNonNull(root, "root");
        if (!Files.isDirectory(root)) {
            return Optional.empty();
        }

        List<Path> candidates = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                    .filter(SourceCsvFinder::isCanonicalName)
                    .map(path -> path.toAbsolutePath().normalize())
                    .forEach(candidates::add);
        }
        candidates.sort(new LargestThenLexicographicComparator());
        return candidates.isEmpty() ? Optional.empty() : Optional.of(candidates.get(0));
    }

    public static Path defaultRoot() {
        return Path.of(System.getProperty("user.home"), "Documents", "Obsidian Vault",
                "GDT", "01_Tareas");
    }

    private static boolean isCanonicalName(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith("_all.csv");
    }

    private static final class LargestThenLexicographicComparator implements Comparator<Path> {
        @Override
        public int compare(Path left, Path right) {
            try {
                int bySize = Long.compare(Files.size(right), Files.size(left));
                if (bySize != 0) {
                    return bySize;
                }
                return left.toString().compareTo(right.toString());
            } catch (IOException error) {
                throw new IllegalStateException("Cannot inspect source CSV size", error);
            }
        }
    }
}
