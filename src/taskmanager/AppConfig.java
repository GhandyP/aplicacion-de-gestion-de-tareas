package taskmanager;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/**
 * Resolves where the application reads and writes its data.
 *
 * <p>Both locations can be overridden so a run never depends on the current working directory or on
 * a hardcoded Obsidian vault location.</p>
 */
public record AppConfig(Path appDirectory, Path dataDirectory, Path sourceRoot) {
    public static final String DATA_DIRECTORY_ENV = "TASKMANAGER_DATA_DIR";
    public static final String SOURCE_ROOT_ENV = "TASKMANAGER_SOURCE_ROOT";

    private static final Path DEFAULT_DATA_DIRECTORY_NAME = Path.of("data");
    private static final Path DEFAULT_SOURCE_ROOT = Path.of("Documents", "Obsidian Vault", "GDT", "01_Tareas");

    public AppConfig {
        Objects.requireNonNull(appDirectory, "appDirectory");
        Objects.requireNonNull(dataDirectory, "dataDirectory");
        Objects.requireNonNull(sourceRoot, "sourceRoot");
    }

    /** The local task file the application owns and rewrites. */
    public Path localFile() {
        return dataDirectory.resolve("tasks.csv");
    }

    /** Resolves the configuration of the current process from its environment. */
    public static AppConfig forCurrentProcess(Path appDirectory) {
        return resolve(System.getenv(), appDirectory, Path.of(System.getProperty("user.home")));
    }

    /**
     * Resolves a configuration from explicit inputs.
     *
     * <p>Blank or absent overrides fall back to the defaults. Relative overrides resolve against the
     * application directory, and a leading {@code ~} expands to the supplied user home.</p>
     */
    public static AppConfig resolve(Map<String, String> environment, Path appDirectory, Path userHome) {
        Objects.requireNonNull(environment, "environment");
        Path base = normalize(Objects.requireNonNull(appDirectory, "appDirectory"));
        Path home = normalize(Objects.requireNonNull(userHome, "userHome"));

        Path dataDirectory = override(environment.get(DATA_DIRECTORY_ENV), base, home);
        if (dataDirectory == null) {
            dataDirectory = normalize(base.resolve(DEFAULT_DATA_DIRECTORY_NAME));
        }

        Path sourceRoot = override(environment.get(SOURCE_ROOT_ENV), base, home);
        if (sourceRoot == null) {
            sourceRoot = normalize(home.resolve(DEFAULT_SOURCE_ROOT));
        }

        return new AppConfig(base, dataDirectory, sourceRoot);
    }

    private static Path override(String rawValue, Path base, Path home) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        String value = rawValue.trim();
        Path candidate;
        if (value.equals("~")) {
            candidate = home;
        } else if (value.startsWith("~/") || value.startsWith("~\\")) {
            candidate = home.resolve(value.substring(2));
        } else {
            candidate = Path.of(value);
        }
        if (!candidate.isAbsolute()) {
            candidate = base.resolve(candidate);
        }
        return normalize(candidate);
    }

    private static Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }
}
