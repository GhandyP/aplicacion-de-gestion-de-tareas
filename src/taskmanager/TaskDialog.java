package taskmanager;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Modal editor for all fourteen source fields. */
public final class TaskDialog extends JDialog {
    private static final String[] LABELS = {
            "Name", "Context", "Effort", "Due date", "Language", "Parent item",
            "Parent item 1", "Project", "Sub-item", "Type", "Importance", "Urgency", "Areas"
    };

    private final Task original;
    private final JTextField[] fields = new JTextField[LABELS.length];
    private final JCheckBox completed = new JCheckBox("Completed");
    private Task result;

    private TaskDialog(Window owner, Task original) {
        super(owner, original == null ? "Add Task" : "Edit Task", Dialog.ModalityType.APPLICATION_MODAL);
        this.original = original;
        buildUi();
    }

    public static Optional<Task> showDialog(Window owner, Task original) {
        TaskDialog dialog = new TaskDialog(owner, original);
        dialog.setVisible(true);
        return Optional.ofNullable(dialog.result);
    }

    private void buildUi() {
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.anchor = GridBagConstraints.NORTHWEST;
        labelConstraints.insets = new Insets(3, 3, 3, 8);
        GridBagConstraints fieldConstraints = new GridBagConstraints();
        fieldConstraints.gridx = 1;
        fieldConstraints.weightx = 1.0;
        fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
        fieldConstraints.insets = new Insets(3, 3, 3, 3);

        int fieldIndex = 0;
        for (int sourceIndex = 0; sourceIndex < Task.FIELD_COUNT; sourceIndex++) {
            if (sourceIndex == Task.COMPLETED) {
                continue;
            }
            String label = LABELS[fieldIndex];
            labelConstraints.gridx = 0;
            labelConstraints.gridy = fieldIndex;
            form.add(new JLabel(label + ":"), labelConstraints);
            JTextField field = new JTextField(32);
            fields[fieldIndex] = field;
            if (original != null) {
                field.setText(original.value(sourceIndex));
            }
            fieldConstraints.gridy = fieldIndex;
            form.add(field, fieldConstraints);
            fieldIndex++;
        }

        labelConstraints.gridx = 0;
        labelConstraints.gridy = fieldIndex;
        form.add(new JLabel("Status:"), labelConstraints);
        fieldConstraints.gridy = fieldIndex;
        completed.setSelected(original != null && original.isCompleted());
        form.add(completed, fieldConstraints);

        add(new JScrollPane(form), BorderLayout.CENTER);

        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(event -> dispose());
        JButton save = new JButton("Save");
        save.addActionListener(event -> saveResult());
        JPanel buttons = new JPanel();
        buttons.add(cancel);
        buttons.add(save);
        add(buttons, BorderLayout.SOUTH);

        getRootPane().setDefaultButton(save);
        setSize(600, 560);
        setMinimumSize(getSize());
        setLocationRelativeTo(getOwner());
    }

    private void saveResult() {
        String name = fields[0].getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Name is required.", "Cannot save task",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        String id = original == null ? "task-" + UUID.randomUUID() : original.id();
        List<String> values = List.of(
                fields[0].getText(),
                fields[1].getText(),
                fields[2].getText(),
                fields[3].getText(),
                completed.isSelected() ? "Yes" : "No",
                fields[4].getText(),
                fields[5].getText(),
                fields[6].getText(),
                fields[7].getText(),
                fields[8].getText(),
                fields[9].getText(),
                fields[10].getText(),
                fields[11].getText(),
                fields[12].getText());
        result = new Task(id, values);
        dispose();
    }
}
