package taskmanager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * The single rule for reading the free-text area field.
 *
 * <p>This rule used to exist twice: once in the Swing layer to build the facet list and once inside
 * {@link Task} to answer a filter. Two copies of one rule drift apart, so both callers share this.</p>
 */
public final class AreaTokens {
    private static final String SEPARATORS = "[,;|\\n]";

    private AreaTokens() {
    }

    /** Splits a raw area field into trimmed, non-blank tokens, in field order. */
    public static List<String> of(String rawAreas) {
        if (rawAreas == null || rawAreas.isBlank()) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        for (String token : rawAreas.split(SEPARATORS)) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) {
                tokens.add(trimmed);
            }
        }
        return List.copyOf(tokens);
    }

    /** Every distinct token across the given tasks, deduplicated case-insensitively and sorted. */
    public static List<String> across(Collection<Task> tasks) {
        Objects.requireNonNull(tasks, "tasks");
        Set<String> values = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Task task : tasks) {
            if (task != null) {
                values.addAll(of(task.areas()));
            }
        }
        return List.copyOf(values);
    }

    /**
     * Whether an area field holds the requested area.
     *
     * <p>An empty request matches everything, which is how the facet filter expresses "All". The
     * final comparison is not redundant: it keeps matching a request that repeats the whole raw field,
     * which is what the rule did when it lived inside {@link Task}.</p>
     */
    public static boolean contains(String rawAreas, String requestedArea) {
        String requested = requestedArea == null ? "" : requestedArea.trim();
        if (requested.isEmpty()) {
            return true;
        }
        for (String token : of(rawAreas)) {
            if (token.equalsIgnoreCase(requested)) {
                return true;
            }
        }
        String raw = rawAreas == null ? "" : rawAreas.trim();
        return raw.equalsIgnoreCase(requested);
    }
}
