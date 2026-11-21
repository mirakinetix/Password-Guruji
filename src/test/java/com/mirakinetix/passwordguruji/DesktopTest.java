package com.mirakinetix.passwordguruji;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.WindowEvent;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.prefs.Preferences;
import static org.junit.jupiter.api.Assertions.*;

@Tag("desktop")
class DesktopTest {
    @TempDir Path dir;
    static Object field(Object object, String name) throws Exception {
        Field f = object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object);
    }
    static void invoke(Object object, String name) throws Exception {
        Method m = object.getClass().getDeclaredMethod(name); m.setAccessible(true); m.invoke(object);
    }
    static List<Component> descendants(Container parent) {
        List<Component> result = new ArrayList<>();
        for (Component c : parent.getComponents()) { result.add(c); if (c instanceof Container child) result.addAll(descendants(child)); }
        return result;
    }
    static void click(Container parent, String text) {
        descendants(parent).stream().filter(c -> c instanceof JButton b && text.equals(b.getText()))
            .map(c -> (JButton)c).findFirst().orElseThrow().doClick();
    }
    @Test void createLockRejectWrongPasswordUnlockAndClose() throws Exception {
        assertFalse(GraphicsEnvironment.isHeadless(), "Desktop profile requires a display; use xvfb-run on Linux");
        Preferences prefs = Preferences.userRoot().node("password-guruji-tests/" + UUID.randomUUID());
        final Throwable[] failure = new Throwable[1];
        SwingUtilities.invokeAndWait(() -> {
            PasswordGurujiApp app = null;
            try {
                app = new PasswordGurujiApp(new String[]{"--sample-vault", dir.resolve("synthetic.pgv").toString()}, prefs);
                invoke(app, "launch"); JFrame frame = (JFrame)field(app, "frame");
                assertTrue(frame.isShowing());
                Container unlock = (Container)field(app, "unlockPage");
                descendants(unlock).stream().filter(c -> c instanceof JPasswordField).forEach(c -> ((JPasswordField)c).setText("dummy-test-123456"));
                click(unlock, "Create my vault"); assertEquals(false, field(app,"locked"));
                char[] held = (char[])field(app, "masterPassword");
                ((SecretClipboard)field(app,"secretClipboard")).copy("dummy-lock-secret",30000);
                invoke(app,"lockVault"); assertEquals(true,field(app,"locked")); assertNull(field(app,"masterPassword"));
                assertEquals("",Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor));
                assertArrayEquals(new char[held.length],held);
                unlock = (Container)field(app,"unlockPage");
                JPasswordField input = (JPasswordField)descendants(unlock).stream().filter(c -> c instanceof JPasswordField).findFirst().orElseThrow();
                input.setText("incorrect"); click(unlock,"Unlock vault"); assertEquals(true,field(app,"locked"));
                input.setText("dummy-test-123456"); click(unlock,"Unlock vault"); assertEquals(false,field(app,"locked"));
                Field activity=app.getClass().getDeclaredField("lastActivity"); activity.setAccessible(true); activity.setLong(app,0L);
                javax.swing.Timer idle=(javax.swing.Timer)field(app,"idleTimer");
                for(var listener:idle.getActionListeners()) listener.actionPerformed(new java.awt.event.ActionEvent(idle,0,"idle"));
                assertEquals(true,field(app,"locked"));
                unlock=(Container)field(app,"unlockPage");
                input=(JPasswordField)descendants(unlock).stream().filter(c -> c instanceof JPasswordField).findFirst().orElseThrow();
                input.setText("dummy-test-123456"); click(unlock,"Unlock vault");
                ((SecretClipboard)field(app,"secretClipboard")).copy("dummy-close-secret",30000);
                frame.dispatchEvent(new WindowEvent(frame,WindowEvent.WINDOW_CLOSING));
                assertFalse(frame.isDisplayable()); assertNull(field(app,"masterPassword"));
                assertEquals("",Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor));
                assertFalse(((javax.swing.Timer)field(app,"idleTimer")).isRunning());
            } catch(Throwable error) { failure[0]=error; }
            finally { if(app!=null) try { ((javax.swing.Timer)field(app,"idleTimer")).stop(); ((JFrame)field(app,"frame")).dispose(); } catch(Exception ignored) {} }
        });
        prefs.removeNode(); prefs.flush();
        if(failure[0]!=null) throw new AssertionError("Desktop lifecycle failed",failure[0]);
    }
    @Test void actualSystemClipboardClearsAfterTimeout() throws Exception {
        Clipboard clipboard=Toolkit.getDefaultToolkit().getSystemClipboard();
        SecretClipboard owner=new SecretClipboard(clipboard);
        SwingUtilities.invokeAndWait(() -> owner.copy("synthetic-clipboard-only",100));
        long deadline=System.nanoTime()+5_000_000_000L;
        while(System.nanoTime()<deadline && !"".equals(clipboard.getData(DataFlavor.stringFlavor))) Thread.sleep(50);
        assertEquals("",clipboard.getData(DataFlavor.stringFlavor));
    }
    @Test void clipboardDoesNotEraseNewUnrelatedContent() throws Exception {
        Clipboard clipboard=new Clipboard("isolated-test"); SecretClipboard owner=new SecretClipboard(clipboard);
        SwingUtilities.invokeAndWait(() -> { owner.copy("synthetic",1000); clipboard.setContents(new StringSelection("new unrelated text"),null); owner.clear(); });
        assertEquals("new unrelated text",clipboard.getData(DataFlavor.stringFlavor));
    }
    @Test void replacingOwnedClipboardTextCancelsPreviousTimer() throws Exception {
        Clipboard clipboard=new Clipboard("replacement-test"); SecretClipboard owner=new SecretClipboard(clipboard);
        SwingUtilities.invokeAndWait(() -> { owner.copy("old dummy",50); owner.copy("new dummy",2000); });
        Thread.sleep(150);
        assertEquals("new dummy",clipboard.getData(DataFlavor.stringFlavor));
        SwingUtilities.invokeAndWait(owner::clear);
        assertEquals("",clipboard.getData(DataFlavor.stringFlavor));
    }
}
