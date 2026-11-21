package com.mirakinetix.passwordguruji;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.prefs.Preferences;
import static org.junit.jupiter.api.Assertions.*;

@Tag("desktop")
class RecordDialogSafetyTest {
    @TempDir Path directory;

    @ParameterizedTest(name = "Editor clears retained fields; invalid authenticator = {0}")
    @ValueSource(booleans = {false, true})
    void closingEditorClearsRetainedComponents(boolean invalidAuthenticator) throws Exception {
        assertFalse(GraphicsEnvironment.isHeadless(), "Desktop test requires a display");
        Preferences preferences = Preferences.userRoot().node("password-guruji-tests/" + UUID.randomUUID());
        Throwable[] failure = new Throwable[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                PasswordGurujiApp app = null;
                javax.swing.Timer driver = null;
                try {
                    app = new PasswordGurujiApp(new String[]{"--sample-vault", directory.resolve("synthetic.pgv").toString()}, preferences);
                    DesktopTest.invoke(app, "launch");
                    Container unlock = (Container)DesktopTest.field(app, "unlockPage");
                    DesktopTest.descendants(unlock).stream().filter(c -> c instanceof JPasswordField)
                            .forEach(c -> ((JPasswordField)c).setText("synthetic-password-123456"));
                    DesktopTest.click(unlock, "Create my vault");
                    assertEquals(false, DesktopTest.field(app, "locked"));
                    JFrame frame = (JFrame)DesktopTest.field(app, "frame");
                    List<JTextComponent> retained = new ArrayList<>();
                    boolean[] editorHandled = {false}, warningHandled = {false};
                    long deadline = System.nanoTime() + 10_000_000_000L;
                    driver = new javax.swing.Timer(60, event -> {
                        try {
                            if (System.nanoTime() > deadline) throw new AssertionError("Record modal did not complete within ten seconds");
                            for (Window window : frame.getOwnedWindows()) {
                                if (!(window instanceof JDialog dialog) || !dialog.isShowing()) continue;
                                if ("Edit vault record".equals(dialog.getTitle()) && !editorHandled[0]) {
                                    for (Component component : DesktopTest.descendants(dialog)) {
                                        if (component instanceof JTextComponent text) retained.add(text);
                                    }
                                    assertTrue(retained.size() >= 8, "Expected every populated record text field");
                                    assertTrue(retained.stream().anyMatch(c -> c instanceof JPasswordField && c.getDocument().getLength() > 0));
                                    assertTrue(retained.stream().allMatch(c -> c.getDocument().getLength() > 0));
                                    editorHandled[0] = true;
                                    DesktopTest.click(dialog, invalidAuthenticator ? "OK" : "Cancel");
                                } else if ("Password Guruji".equals(dialog.getTitle()) && invalidAuthenticator) {
                                    JOptionPane pane = (JOptionPane)DesktopTest.descendants(dialog).stream()
                                            .filter(c -> c instanceof JOptionPane).findFirst().orElseThrow();
                                    assertTrue(String.valueOf(pane.getMessage()).contains("valid Base32"));
                                    warningHandled[0] = true;
                                    DesktopTest.click(dialog, "OK");
                                }
                            }
                        } catch (Throwable error) {
                            failure[0] = error;
                            ((javax.swing.Timer)event.getSource()).stop();
                            for (Window window : frame.getOwnedWindows()) window.dispose();
                        }
                    });
                    driver.start();
                    VaultEntry synthetic = VaultEntry.create("Dummy title", "Dummy user", "dummy-secret", "https://example.invalid",
                            "Dummy confidential notes", "Login", "Dummy folder", false, List.of("dummy-tag"),
                            Map.of("TOTP secret", "invalid!base32"));
                    Method editor = PasswordGurujiApp.class.getDeclaredMethod("editEntry", VaultEntry.class);
                    editor.setAccessible(true);
                    editor.invoke(app, synthetic);
                    assertTrue(editorHandled[0]);
                    assertEquals(invalidAuthenticator, warningHandled[0]);
                    for (JTextComponent component : retained)
                        assertEquals(0, component.getDocument().getLength(), "Closed editor retained " + component.getClass().getSimpleName());
                    assertTrue(((List<?>)DesktopTest.field(app, "entries")).isEmpty(), "Rejected/canceled record must not be stored");
                } catch (Throwable error) { if (failure[0] == null) failure[0] = error; }
                finally {
                    if (driver != null) driver.stop();
                    if (app != null) try {
                        DesktopTest.invoke(app, "lockVault");
                        ((javax.swing.Timer)DesktopTest.field(app, "idleTimer")).stop();
                        ((JFrame)DesktopTest.field(app, "frame")).dispose();
                    } catch (Exception cleanup) { if (failure[0] == null) failure[0] = cleanup; }
                }
            });
        } finally { preferences.removeNode(); preferences.flush(); }
        if (failure[0] != null) throw new AssertionError("Record dialog cleanup failed", failure[0]);
    }
}
