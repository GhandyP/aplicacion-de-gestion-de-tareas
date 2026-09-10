package taskmanager;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Small RFC 4180-compatible CSV reader/writer with UTF-8 path helpers. */
public final class CsvCodec {
    private CsvCodec() {
    }

    public static List<List<String>> read(Reader reader) throws IOException {
        Objects.requireNonNull(reader, "reader");
        StringBuilder input = new StringBuilder();
        char[] buffer = new char[8192];
        int count;
        while ((count = reader.read(buffer)) != -1) {
            input.append(buffer, 0, count);
        }
        return parse(input.toString());
    }

    public static List<List<String>> read(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return read(reader);
        }
    }

    public static void write(Writer writer, List<? extends List<String>> rows) throws IOException {
        Objects.requireNonNull(writer, "writer");
        Objects.requireNonNull(rows, "rows");
        for (List<String> row : rows) {
            if (row == null || row.isEmpty()) {
                writer.write("\n");
                continue;
            }
            for (int column = 0; column < row.size(); column++) {
                if (column > 0) {
                    writer.write(',');
                }
                writeField(writer, row.get(column));
            }
            writer.write('\n');
        }
        writer.flush();
    }

    public static void write(Path path, List<? extends List<String>> rows) throws IOException {
        Objects.requireNonNull(path, "path");
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            write(writer, rows);
        }
    }

    private static List<List<String>> parse(String rawInput) throws IOException {
        String input = rawInput;
        if (!input.isEmpty() && input.charAt(0) == '\ufeff') {
            input = input.substring(1);
        }

        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean afterClosingQuote = false;
        boolean recordStarted = false;

        for (int index = 0; index < input.length(); index++) {
            char current = input.charAt(index);
            if (inQuotes) {
                recordStarted = true;
                if (current == '"') {
                    if (index + 1 < input.length() && input.charAt(index + 1) == '"') {
                        field.append('"');
                        index++;
                    } else {
                        inQuotes = false;
                        afterClosingQuote = true;
                    }
                } else {
                    field.append(current);
                }
                continue;
            }

            if (afterClosingQuote) {
                if (current == ',') {
                    row.add(field.toString());
                    field.setLength(0);
                    afterClosingQuote = false;
                    recordStarted = true;
                } else if (current == '\n' || current == '\r') {
                    row.add(field.toString());
                    addRow(rows, row);
                    row = new ArrayList<>();
                    field.setLength(0);
                    afterClosingQuote = false;
                    recordStarted = false;
                    if (current == '\r' && index + 1 < input.length() && input.charAt(index + 1) == '\n') {
                        index++;
                    }
                } else {
                    // Be liberal about whitespace or malformed text after a closing quote.
                    field.append(current);
                    afterClosingQuote = false;
                    recordStarted = true;
                }
                continue;
            }

            if (current == ',' ) {
                row.add(field.toString());
                field.setLength(0);
                recordStarted = true;
            } else if (current == '\n' || current == '\r') {
                row.add(field.toString());
                addRow(rows, row);
                row = new ArrayList<>();
                field.setLength(0);
                recordStarted = false;
                if (current == '\r' && index + 1 < input.length() && input.charAt(index + 1) == '\n') {
                    index++;
                }
            } else if (current == '"' && field.length() == 0) {
                inQuotes = true;
                recordStarted = true;
            } else {
                field.append(current);
                recordStarted = true;
            }
        }

        if (inQuotes) {
            throw new IOException("Unterminated quoted CSV field");
        }
        if (recordStarted || !row.isEmpty() || field.length() > 0 || afterClosingQuote) {
            row.add(field.toString());
            addRow(rows, row);
        }
        return rows;
    }

    private static void addRow(List<List<String>> rows, List<String> row) {
        rows.add(List.copyOf(row));
    }

    private static void writeField(Writer writer, String value) throws IOException {
        String safe = value == null ? "" : value;
        boolean quote = safe.indexOf(',') >= 0
                || safe.indexOf('"') >= 0
                || safe.indexOf('\n') >= 0
                || safe.indexOf('\r') >= 0
                || (safe.length() > 0 && (Character.isWhitespace(safe.charAt(0))
                || Character.isWhitespace(safe.charAt(safe.length() - 1))));
        if (!quote) {
            writer.write(safe);
            return;
        }
        writer.write('"');
        for (int index = 0; index < safe.length(); index++) {
            char current = safe.charAt(index);
            if (current == '"') {
                writer.write("\"\"");
            } else {
                writer.write(current);
            }
        }
        writer.write('"');
    }
}
