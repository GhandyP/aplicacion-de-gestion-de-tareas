package taskmanager;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.Locale;

/**
 * The ordering the task table shows, kept out of the Swing layer.
 *
 * <p>The ranking vocabulary came from the export's Spanish wording, and it used to live inside the
 * window class where no test could reach it without a display.</p>
 */
public final class TaskOrder {
    /** Active work first, then the most urgent, then the most important, then the earliest date. */
    public static final Comparator<Task> DEFAULT = Comparator
            .comparing(Task::isCompleted)
            .thenComparingInt(task -> urgencyRank(task.urgency()))
            .thenComparingInt(task -> importanceRank(task.importance()))
            .thenComparing(task -> TaskDates.parse(task.dueDate()).orElse(LocalDate.MAX))
            .thenComparing(Task::name, String.CASE_INSENSITIVE_ORDER);

    private TaskOrder() {
    }

    /** Ranks the urgency vocabulary; anything unrecognised sorts last. */
    public static int urgencyRank(String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
        if (normalized.contains("muy urgente")) {
            return 0;
        }
        if (normalized.contains("media")) {
            return 1;
        }
        if (normalized.contains("poca")) {
            return 2;
        }
        return 3;
    }

    /** Ranks the importance vocabulary; anything unrecognised sorts last. */
    public static int importanceRank(String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
        if (normalized.contains("muy importante")) {
            return 0;
        }
        if (normalized.equals("importante")) {
            return 1;
        }
        if (normalized.contains("no importante")) {
            return 2;
        }
        return 3;
    }
}
