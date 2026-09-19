package taskmanager;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Immutable task backed by the fourteen fields of the Obsidian export. */
public final class Task {
    public static final int FIELD_COUNT = 14;
    public static final int NAME = 0;
    public static final int CONTEXT = 1;
    public static final int EFFORT = 2;
    public static final int DUE_DATE = 3;
    public static final int COMPLETED = 4;
    public static final int LANGUAGE = 5;
    public static final int PARENT_ITEM = 6;
    public static final int PARENT_ITEM_1 = 7;
    public static final int PROJECT = 8;
    public static final int SUB_ITEM = 9;
    public static final int TYPE = 10;
    public static final int IMPORTANCE = 11;
    public static final int URGENCY = 12;
    public static final int AREAS = 13;

    public static final List<String> SOURCE_HEADERS = List.of(
            "Nombre", "Contexto", "Esfuerzo", "Fecha", "Hecho", "Language",
            "Parent item", "Parent item 1", "Proyecto", "Sub-item", "Tipo",
            "importancia", "urgencia", "Áreas");

    private final String id;
    private final List<String> values;

    public Task(String id, List<String> values) {
        this.id = requireId(id);
        Objects.requireNonNull(values, "values");
        ArrayList<String> normalized = new ArrayList<>(FIELD_COUNT);
        for (int index = 0; index < FIELD_COUNT; index++) {
            normalized.add(index < values.size() && values.get(index) != null ? values.get(index) : "");
        }
        this.values = Collections.unmodifiableList(normalized);
    }

    public Task(String id, String... values) {
        this(id, List.of(values));
    }

    public String id() {
        return id;
    }

    public String getId() {
        return id();
    }

    public List<String> values() {
        return values;
    }

    public List<String> sourceValues() {
        return values();
    }

    public String value(int index) {
        if (index < 0 || index >= FIELD_COUNT) {
            throw new IndexOutOfBoundsException("Task field index: " + index);
        }
        return values.get(index);
    }

    public String name() {
        return value(NAME);
    }

    public String getName() {
        return name();
    }

    public String context() {
        return value(CONTEXT);
    }

    public String getContext() {
        return context();
    }

    public String effort() {
        return value(EFFORT);
    }

    public String getEffort() {
        return effort();
    }

    public String dueDate() {
        return value(DUE_DATE);
    }

    public String getDueDate() {
        return dueDate();
    }

    public String completedValue() {
        return value(COMPLETED);
    }

    public String getCompleted() {
        return completedValue();
    }

    public String language() {
        return value(LANGUAGE);
    }

    public String getLanguage() {
        return language();
    }

    public String parentItem() {
        return value(PARENT_ITEM);
    }

    public String getParentItem() {
        return parentItem();
    }

    public String parentItem1() {
        return value(PARENT_ITEM_1);
    }

    public String getParentItem1() {
        return parentItem1();
    }

    public String project() {
        return value(PROJECT);
    }

    public String getProject() {
        return project();
    }

    public String subItem() {
        return value(SUB_ITEM);
    }

    public String getSubItem() {
        return subItem();
    }

    public String type() {
        return value(TYPE);
    }

    public String getType() {
        return type();
    }

    public String importance() {
        return value(IMPORTANCE);
    }

    public String getImportance() {
        return importance();
    }

    public String urgency() {
        return value(URGENCY);
    }

    public String getUrgency() {
        return urgency();
    }

    public String areas() {
        return value(AREAS);
    }

    public String getAreas() {
        return areas();
    }

    public boolean isCompleted() {
        String normalized = completedValue().trim().toLowerCase(Locale.ROOT);
        return normalized.equals("yes") || normalized.equals("true") || normalized.equals("1")
                || normalized.equals("done") || normalized.equals("completed")
                || normalized.equals("sí") || normalized.equals("si");
    }

    public boolean hasArea(String requestedArea) {
        return AreaTokens.contains(areas(), requestedArea);
    }

    public String searchableText() {
        return String.join("\n", values);
    }

    public Task withCompleted(boolean completed) {
        return withField(COMPLETED, completed ? "Yes" : "No");
    }

    public Task withDueDate(String dueDate) {
        return withField(DUE_DATE, dueDate);
    }

    public Task withField(int index, String replacement) {
        ArrayList<String> copy = new ArrayList<>(values);
        copy.set(index, replacement == null ? "" : replacement);
        return new Task(id, copy);
    }

    public Task withValues(List<String> replacements) {
        return new Task(id, replacements);
    }

    public static String generatedId(List<String> values, int occurrence) {
        String seed = String.join("\u001f", values) + "\u001e" + occurrence;
        return "task-" + UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Task task)) {
            return false;
        }
        return id.equals(task.id) && values.equals(task.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, values);
    }

    @Override
    public String toString() {
        return "Task{" + id + ", " + name() + "}";
    }

    private static String requireId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Task id must not be blank");
        }
        return id;
    }
}
