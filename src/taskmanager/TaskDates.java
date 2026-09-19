package taskmanager;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Date parsing and formatting rules shared by persistence and the UI. */
final class TaskDates {
    private static final Locale ENGLISH = Locale.ENGLISH;
    private static final DateTimeFormatter LONG_DATE =
            DateTimeFormatter.ofPattern("MMMM d, uuuu", ENGLISH).withResolverStyle(ResolverStyle.SMART);
    private static final DateTimeFormatter SHORT_DATE =
            DateTimeFormatter.ofPattern("MMM d, uuuu", ENGLISH).withResolverStyle(ResolverStyle.SMART);
    private static final DateTimeFormatter SLASH_DATE =
            DateTimeFormatter.ofPattern("M/d/uuuu", ENGLISH).withResolverStyle(ResolverStyle.SMART);
    private static final List<DateTimeFormatter> FORMATTERS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            LONG_DATE,
            SHORT_DATE,
            SLASH_DATE,
            DateTimeFormatter.ofPattern("MM/dd/uuuu", ENGLISH).withResolverStyle(ResolverStyle.SMART));

    private TaskDates() {
    }

    static Optional<LocalDate> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String value = raw.trim();
        for (DateTimeFormatter formatter : FORMATTERS) {
            try {
                return Optional.of(LocalDate.parse(value, formatter));
            } catch (DateTimeParseException ignored) {
                // Try the next format used by the export.
            }
        }
        int separator = value.indexOf('T');
        if (separator > 0) {
            try {
                return Optional.of(LocalDate.parse(value.substring(0, separator), DateTimeFormatter.ISO_LOCAL_DATE));
            } catch (DateTimeParseException ignored) {
                // Fall through to an empty result.
            }
        }
        return Optional.empty();
    }

    static String format(LocalDate date, String original) {
        String source = original == null ? "" : original.trim();
        if (source.matches("\\d{4}-\\d{2}-\\d{2}.*")) {
            return date.format(DateTimeFormatter.ISO_LOCAL_DATE);
        }
        if (source.matches("\\d{1,2}/\\d{1,2}/\\d{4}")) {
            return date.format(SLASH_DATE);
        }
        // Everything else, including a blank original, is published in the long readable form.
        return date.format(LONG_DATE);
    }

    /**
     * Shifts a due date, keeping the style of the original.
     *
     * <p>A blank original means "no due date yet", so shifting starts from today. A non-blank
     * original that is not a recognizable date is an error: falling back to today would publish a
     * plausible-looking date that has nothing to do with the task.</p>
     *
     * @throws IllegalArgumentException if the shift is negative or the original is unparseable
     */
    static String postpone(String current, int days, Clock clock) {
        if (days < 0) {
            throw new IllegalArgumentException("Postponement days must not be negative, got " + days);
        }
        String source = current == null ? "" : current.trim();
        LocalDate base;
        if (source.isEmpty()) {
            base = LocalDate.now(clock);
        } else {
            base = parse(source).orElseThrow(() -> new IllegalArgumentException(
                    "Cannot postpone a task whose due date is not a recognizable date: '" + source + "'"));
        }
        return format(base.plusDays(days), current);
    }
}
