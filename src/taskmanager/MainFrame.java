package taskmanager;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/** Main Swing window for local task management. */
public final class MainFrame extends JFrame {
    private static final String ALL = "All";

    private final Path appDirectory;
    private final AppStartup.Prepared prepared;
    private final TaskRepository repository;
    private final TaskTableModel tableModel = new TaskTableModel();
    private final JTable table = new JTable(tableModel);
    private final JTextField searchField = new JTextField(24);
    private final JComboBox<String> statusFilter = new JComboBox<>(new String[]{ALL, "Active", "Completed"});
    private final JComboBox<String> importanceFilter = new JComboBox<>();
    private final JComboBox<String> urgencyFilter = new JComboBox<>();
    private final JComboBox<String> areaFilter = new JComboBox<>();
    private final JLabel summaryLabel = new JLabel();
    private final JLabel statusLabel = new JLabel();

    public MainFrame(AppStartup.Prepared prepared) {
        super("Task Manager");
        this.prepared = prepared;
        this.repository = prepared.repository();
        this.appDirectory = prepared.appDirectory();
        buildUi();
        rebuildFacetFilters();
        setStatus(prepared.status());
        refreshTable();
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new java.awt.Dimension(1120, 650));
        setSize(1400, 800);
        setLocationByPlatform(true);
    }

    private void buildUi() {
        setLayout(new BorderLayout(8, 8));
        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        filters.add(new JLabel("Search:"));
        filters.add(searchField);
        filters.add(new JLabel("Status:"));
        filters.add(statusFilter);
        filters.add(new JLabel("Importance:"));
        filters.add(importanceFilter);
        filters.add(new JLabel("Urgency:"));
        filters.add(urgencyFilter);
        filters.add(new JLabel("Area:"));
        filters.add(areaFilter);
        add(filters, BorderLayout.NORTH);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(false);
        table.setFillsViewportHeight(true);
        table.setRowHeight(24);
        table.getColumnModel().getColumn(0).setPreferredWidth(360);
        table.getColumnModel().getColumn(5).setPreferredWidth(150);
        table.getColumnModel().getColumn(6).setPreferredWidth(180);
        table.getColumnModel().getColumn(7).setPreferredWidth(180);
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent event) {
                if (event.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(event)) {
                    editSelectedTask();
                }
            }
        });
        add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(6, 6));
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        actions.add(button("Add", event -> addTask()));
        actions.add(button("Edit", event -> editSelectedTask()));
        actions.add(button("Complete / Reopen", event -> toggleSelectedTask()));
        actions.add(button("Postpone", event -> postponeSelectedTask()));
        actions.add(button("Delete", event -> deleteSelectedTask()));
        actions.add(button("Save", event -> saveRepository()));
        actions.add(button("Refresh / Import", event -> importFromObsidian()));
        actions.add(button("Export CSV", event -> exportCsv()));
        actions.add(button("Export Markdown", event -> exportMarkdown()));
        bottom.add(actions, BorderLayout.NORTH);

        JPanel info = new JPanel(new GridLayout(2, 1));
        summaryLabel.setHorizontalAlignment(SwingConstants.LEFT);
        statusLabel.setHorizontalAlignment(SwingConstants.LEFT);
        info.add(summaryLabel);
        info.add(statusLabel);
        bottom.add(info, BorderLayout.SOUTH);
        add(bottom, BorderLayout.SOUTH);

        DocumentListener searchListener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                refreshTable();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                refreshTable();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                refreshTable();
            }
        };
        searchField.getDocument().addDocumentListener(searchListener);
        statusFilter.addActionListener(event -> refreshTable());
        importanceFilter.addActionListener(event -> refreshTable());
        urgencyFilter.addActionListener(event -> refreshTable());
        areaFilter.addActionListener(event -> refreshTable());
    }

    private JButton button(String label, java.awt.event.ActionListener listener) {
        JButton button = new JButton(label);
        button.addActionListener(listener);
        return button;
    }

    private void rebuildFacetFilters() {
        replaceOptions(importanceFilter, valuesOf(repository.tasks(), Task::importance));
        replaceOptions(urgencyFilter, valuesOf(repository.tasks(), Task::urgency));
        replaceOptions(areaFilter, areaValues(repository.tasks()));
    }

    private void replaceOptions(JComboBox<String> combo, Collection<String> values) {
        combo.removeAllItems();
        combo.addItem(ALL);
        for (String value : values) {
            if (!value.isBlank()) {
                combo.addItem(value);
            }
        }
    }

    private Set<String> valuesOf(Collection<Task> tasks, java.util.function.Function<Task, String> extractor) {
        Set<String> values = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Task task : tasks) {
            String value = extractor.apply(task);
            if (value != null && !value.isBlank()) {
                values.add(value.trim());
            }
        }
        return values;
    }

    private Set<String> areaValues(Collection<Task> tasks) {
        Set<String> values = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Task task : tasks) {
            for (String token : task.areas().split("[,;|\\n]")) {
                if (!token.isBlank()) {
                    values.add(token.trim());
                }
            }
        }
        return values;
    }

    private void refreshTable() {
        TaskFilter.Criteria criteria = new TaskFilter.Criteria(
                searchField.getText(),
                selectedStatus(),
                selectedFacet(importanceFilter),
                selectedFacet(urgencyFilter),
                selectedFacet(areaFilter));
        List<Task> visible = TaskFilter.apply(repository.tasks(), criteria);
        visible.sort(taskComparator());
        tableModel.setTasks(visible);
        long active = repository.tasks().stream().filter(task -> !task.isCompleted()).count();
        long completed = repository.tasks().size() - active;
        summaryLabel.setText("Total: " + repository.size() + "   Active: " + active
                + "   Completed: " + completed + "   Visible: " + visible.size());
    }

    private TaskFilter.Status selectedStatus() {
        return switch (String.valueOf(statusFilter.getSelectedItem())) {
            case "Active" -> TaskFilter.Status.ACTIVE;
            case "Completed" -> TaskFilter.Status.COMPLETED;
            default -> TaskFilter.Status.ALL;
        };
    }

    private String selectedFacet(JComboBox<String> combo) {
        String value = String.valueOf(combo.getSelectedItem());
        return ALL.equals(value) ? "" : value;
    }

    private Comparator<Task> taskComparator() {
        return Comparator.comparing(Task::isCompleted)
                .thenComparingInt(task -> urgencyRank(task.urgency()))
                .thenComparingInt(task -> importanceRank(task.importance()))
                .thenComparing(task -> TaskDates.parse(task.dueDate()).orElse(LocalDate.MAX))
                .thenComparing(Task::name, String.CASE_INSENSITIVE_ORDER);
    }

    private int urgencyRank(String value) {
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

    private int importanceRank(String value) {
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

    private Task selectedTask() {
        return tableModel.getTaskAt(table.getSelectedRow());
    }

    private void addTask() {
        TaskDialog.showDialog(this, null).ifPresent(task -> {
            try {
                repository.add(task);
                setStatus("Task added and saved locally.");
                refreshTable();
            } catch (IOException | RuntimeException error) {
                showError("Could not add task", error);
            }
        });
    }

    private void editSelectedTask() {
        Task selected = selectedTask();
        if (selected == null) {
            showInfo("Select a task first.");
            return;
        }
        TaskDialog.showDialog(this, selected).ifPresent(task -> {
            try {
                repository.update(task);
                setStatus("Task updated and saved locally.");
                refreshTable();
            } catch (IOException | RuntimeException error) {
                showError("Could not update task", error);
            }
        });
    }

    private void toggleSelectedTask() {
        Task selected = selectedTask();
        if (selected == null) {
            showInfo("Select a task first.");
            return;
        }
        try {
            repository.markCompleted(selected.id(), !selected.isCompleted());
            setStatus(selected.isCompleted() ? "Task reopened." : "Task marked completed.");
            refreshTable();
        } catch (IOException | RuntimeException error) {
            showError("Could not change task status", error);
        }
    }

    private void postponeSelectedTask() {
        Task selected = selectedTask();
        if (selected == null) {
            showInfo("Select a task first.");
            return;
        }
        String choice = (String) JOptionPane.showInputDialog(this, "Postpone by:", "Postpone task",
                JOptionPane.PLAIN_MESSAGE, null,
                new String[]{"1 day", "3 days", "7 days", "14 days", "Cancel"}, "3 days");
        if (choice == null || choice.equals("Cancel")) {
            return;
        }
        int days = Integer.parseInt(choice.split(" ")[0]);
        try {
            repository.postponeDueDate(selected.id(), days, Clock.systemDefaultZone());
            setStatus("Task postponed by " + days + " days.");
            refreshTable();
        } catch (IOException | RuntimeException error) {
            showError("Could not postpone task", error);
        }
    }

    private void deleteSelectedTask() {
        Task selected = selectedTask();
        if (selected == null) {
            showInfo("Select a task first.");
            return;
        }
        int choice = JOptionPane.showConfirmDialog(this,
                "Delete this task locally?\n\n" + selected.name(), "Confirm deletion",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            repository.delete(selected.id());
            setStatus("Task deleted from the local copy.");
            refreshTable();
        } catch (IOException | RuntimeException error) {
            showError("Could not delete task", error);
        }
    }

    private void saveRepository() {
        try {
            repository.save();
            setStatus("Saved local data to " + repository.localFile());
        } catch (IOException error) {
            showError("Could not save local data", error);
        }
    }

    private void importFromObsidian() {
        try {
            SourceCsvFinder.Discovery discovery = SourceCsvFinder.discover(prepared.sourceRoot());
            Optional<Path> source = discovery.selected();
            String problems = discovery.hasProblems()
                    ? "\n\nSome folders could not be read and were skipped:\n"
                            + String.join("\n", discovery.problems())
                    : "";
            if (source.isEmpty()) {
                showInfo("No *_all.csv export was found under:\n" + prepared.sourceRoot() + problems);
                return;
            }
            List<Task> imported = TaskRepository.importFrom(source.get());
            int choice = JOptionPane.showConfirmDialog(this,
                    "Replace the local task list with " + imported.size() + " tasks from:\n"
                            + source.get() + "?\n\nLocal edits not exported will be lost." + problems,
                    "Refresh from Obsidian", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (choice != JOptionPane.YES_OPTION) {
                return;
            }
            repository.replaceAll(imported);
            rebuildFacetFilters();
            setStatus("Imported " + imported.size() + " tasks from Obsidian source CSV.");
            refreshTable();
        } catch (IOException | RuntimeException error) {
            showError("Could not import Obsidian CSV", error);
        }
    }

    private void exportCsv() {
        JFileChooser chooser = exportChooser("tasks-export.csv");
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            Path output = chooser.getSelectedFile().toPath();
            CsvCodec.write(output, TaskRepository.sourceRows(repository.tasks()));
            setStatus("Exported CSV to " + output);
        } catch (IOException error) {
            showError("Could not export CSV", error);
        }
    }

    private void exportMarkdown() {
        JFileChooser chooser = exportChooser("task-dashboard.md");
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            Path output = chooser.getSelectedFile().toPath();
            MarkdownExporter.write(output, repository.tasks());
            setStatus("Exported Markdown dashboard to " + output);
        } catch (IOException error) {
            showError("Could not export Markdown", error);
        }
    }

    private JFileChooser exportChooser(String defaultName) {
        Exports.Prepared prepared = Exports.prepareDefaultDirectory(appDirectory);
        prepared.problem().ifPresent(this::setStatus);
        Path exportDirectory = prepared.directory();
        JFileChooser chooser = new JFileChooser(exportDirectory.toFile());
        chooser.setDialogTitle(prepared.hasProblem()
                ? "Choose where to export (the default folder is unavailable)"
                : "Choose where to export");
        chooser.setSelectedFile(new File(exportDirectory.toFile(), defaultName));
        return chooser;
    }

    private void setStatus(String message) {
        statusLabel.setText(message);
    }

    private void showInfo(String message) {
        JOptionPane.showMessageDialog(this, message, "Task Manager", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showError(String title, Exception error) {
        String detail = error.getMessage() == null ? error.toString() : error.getMessage();
        statusLabel.setText(title + ": " + detail);
        JOptionPane.showMessageDialog(this, detail, title, JOptionPane.ERROR_MESSAGE);
    }
}
