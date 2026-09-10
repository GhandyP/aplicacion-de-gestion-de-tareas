package taskmanager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Pure filtering operations used by the table and easy to test independently. */
public final class TaskFilter {
    private TaskFilter() {
    }

    public enum Status {
        ALL,
        ACTIVE,
        COMPLETED
    }

    public record Criteria(String text, Status status, String importance, String urgency, String area) {
        public Criteria {
            text = text == null ? "" : text;
            status = status == null ? Status.ALL : status;
            importance = importance == null ? "" : importance;
            urgency = urgency == null ? "" : urgency;
            area = area == null ? "" : area;
        }
    }

    public static List<Task> apply(Collection<Task> tasks, Criteria criteria) {
        Objects.requireNonNull(tasks, "tasks");
        Criteria actual = criteria == null ? new Criteria("", Status.ALL, "", "", "") : criteria;
        String search = actual.text().trim().toLowerCase(Locale.ROOT);
        String importance = actual.importance().trim();
        String urgency = actual.urgency().trim();
        String area = actual.area().trim();

        List<Task> result = new ArrayList<>();
        for (Task task : tasks) {
            if (task == null) {
                continue;
            }
            if (actual.status() == Status.ACTIVE && task.isCompleted()) {
                continue;
            }
            if (actual.status() == Status.COMPLETED && !task.isCompleted()) {
                continue;
            }
            if (!search.isEmpty() && !task.searchableText().toLowerCase(Locale.ROOT).contains(search)) {
                continue;
            }
            if (!importance.isEmpty() && !task.importance().trim().equalsIgnoreCase(importance)) {
                continue;
            }
            if (!urgency.isEmpty() && !task.urgency().trim().equalsIgnoreCase(urgency)) {
                continue;
            }
            if (!area.isEmpty() && !task.hasArea(area)) {
                continue;
            }
            result.add(task);
        }
        return result;
    }
}
