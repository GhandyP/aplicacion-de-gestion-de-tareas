package taskmanager;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.nio.file.Path;

/** Application entry point. */
public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        Path appDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        if (hasArgument(args, "--smoke-test")) {
            try {
                System.out.println(SmokeTest.run(appDirectory));
            } catch (IOException | RuntimeException error) {
                error.printStackTrace(System.err);
                System.exit(1);
            }
            return;
        }

        try {
            AppStartup.Prepared prepared = AppStartup.prepare(appDirectory);
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            SwingUtilities.invokeLater(() -> new MainFrame(prepared).setVisible(true));
        } catch (Exception error) {
            error.printStackTrace(System.err);
            if (!GraphicsEnvironment.isHeadless()) {
                JOptionPane.showMessageDialog(null, error.getMessage(), "Task Manager startup error",
                        JOptionPane.ERROR_MESSAGE);
            }
            System.exit(1);
        }
    }

    private static boolean hasArgument(String[] args, String expected) {
        if (args == null) {
            return false;
        }
        for (String argument : args) {
            if (expected.equals(argument)) {
                return true;
            }
        }
        return false;
    }
}
