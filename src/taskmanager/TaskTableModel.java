package taskmanager;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Table projection containing the fields useful for daily task work. */
public final class TaskTableModel extends AbstractTableModel {
    private static final String[] COLUMNS = {
            "Name", "Status", "Due date", "Importance", "Urgency", "Context", "Project", "Areas"
    };

    private final List<Task> rows = new ArrayList<>();

    public void setTasks(Collection<Task> tasks) {
        rows.clear();
        if (tasks != null) {
            rows.addAll(tasks);
        }
        fireTableDataChanged();
    }

    public List<Task> tasks() {
        return List.copyOf(rows);
    }

    public Task getTaskAt(int row) {
        if (row < 0 || row >= rows.size()) {
            return null;
        }
        return rows.get(row);
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return String.class;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        Task task = rows.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> task.name();
            case 1 -> task.isCompleted() ? "Completed" : "Active";
            case 2 -> task.dueDate();
            case 3 -> task.importance();
            case 4 -> task.urgency();
            case 5 -> task.context();
            case 6 -> task.project();
            case 7 -> task.areas();
            default -> throw new IndexOutOfBoundsException("Column: " + columnIndex);
        };
    }
}
