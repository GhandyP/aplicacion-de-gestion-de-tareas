package taskmanager;

import java.io.IOException;

/**
 * A CSV input that cannot be parsed, or whose rows do not match the expected shape.
 *
 * <p>The position is physical (line and column) when the codec fails while scanning characters, and
 * logical (a 1-based row index) when a repository rejects an already parsed row. Exactly one of the
 * two is meaningful for a given failure; the other is reported as 0.</p>
 */
public final class CsvFormatException extends IOException {
    private static final long serialVersionUID = 1L;

    private final int line;
    private final int column;
    private final int row;

    private CsvFormatException(String message, int line, int column, int row) {
        super(message);
        this.line = line;
        this.column = column;
        this.row = row;
    }

    /** Builds a failure at a physical position in the input. */
    public static CsvFormatException atPosition(int line, int column, String detail) {
        return new CsvFormatException(
                "Malformed CSV at line " + line + ", column " + column + ": " + detail,
                line, column, 0);
    }

    /** Builds a failure for a parsed row, identified by its 1-based index. */
    public static CsvFormatException atRow(int row, String detail) {
        return new CsvFormatException("Malformed CSV at row " + row + ": " + detail, 0, 0, row);
    }

    /** Physical line of the failure, or 0 when the failure is row-scoped. */
    public int line() {
        return line;
    }

    /** Physical column of the failure, or 0 when the failure is row-scoped. */
    public int column() {
        return column;
    }

    /** 1-based row index of the failure, or 0 when the failure is position-scoped. */
    public int row() {
        return row;
    }
}
