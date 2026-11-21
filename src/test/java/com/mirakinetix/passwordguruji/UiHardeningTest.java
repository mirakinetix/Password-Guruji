package com.mirakinetix.passwordguruji;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.prefs.Preferences;
import static org.junit.jupiter.api.Assertions.*;

@Tag("desktop")
class UiHardeningTest {
    @TempDir Path dir;
    interface Work { void run(PasswordGurujiApp app,JFrame frame) throws Exception; }
    void withApp(boolean existing,Work work) throws Exception {
        Path file=dir.resolve("ui-dummy.pgv");
        if(existing) new VaultStore(file).save("synthetic-long-password".toCharArray(),fixtures());
        Preferences prefs=Preferences.userRoot().node("password-guruji-tests/"+UUID.randomUUID());
        Throwable[] failure=new Throwable[1];
        SwingUtilities.invokeAndWait(()->{
            PasswordGurujiApp app=null;
            try {
                app=new PasswordGurujiApp(new String[0],prefs);
                set(app,"store",new VaultStore(file));DesktopTest.invoke(app,"launch");
                work.run(app,(JFrame)DesktopTest.field(app,"frame"));
            } catch(Throwable error) { failure[0]=error; }
            finally { if(app!=null) try { DesktopTest.invoke(app,"clearSensitiveState");((javax.swing.Timer)DesktopTest.field(app,"idleTimer")).stop();((JFrame)DesktopTest.field(app,"frame")).dispose(); }catch(Exception ignored){} }
        });
        prefs.removeNode();prefs.flush();
        if(failure[0]!=null) throw new AssertionError("UI regression",failure[0]);
    }
    static void set(Object object,String name,Object value) throws Exception { Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);field.set(object,value); }
    static Object call(Object app,String name) throws Exception { Method method=app.getClass().getDeclaredMethod(name);method.setAccessible(true);return method.invoke(app); }
    static void unlock(PasswordGurujiApp app) throws Exception {
        Container page=(Container)DesktopTest.field(app,"unlockPage");
        DesktopTest.descendants(page).stream().filter(c->c instanceof JPasswordField).forEach(c->((JPasswordField)c).setText("synthetic-long-password"));
        DesktopTest.click(page,"Unlock vault");assertEquals(false,DesktopTest.field(app,"locked"));
    }
    List<VaultEntry> fixtures() {
        return List.of(new VaultEntry("1","Alpha","u","dummy-a","","","Login","Work",true,false,List.of("needle"),Map.of(),List.of(),10),
            new VaultEntry("2","Beta","u","dummy-b","","","Secure note","Personal",false,false,List.of("tag"),Map.of(),List.of(),20),
            new VaultEntry("3","Deleted","u","dummy-c","","","Other","",false,true,List.of(),Map.of(),List.of(),30));
    }
    @Test void realCreationRejectsShortPasswordAndAcceptsFifteenOrMore() throws Exception {
        withApp(false,(app,frame)->{
            Container page=(Container)DesktopTest.field(app,"unlockPage");
            DesktopTest.descendants(page).stream().filter(c->c instanceof JPasswordField).forEach(c->((JPasswordField)c).setText("123456"));
            DesktopTest.click(page,"Create my vault");assertEquals(true,DesktopTest.field(app,"locked"));
            assertFalse(((VaultStore)DesktopTest.field(app,"store")).exists());
            DesktopTest.descendants(page).stream().filter(c->c instanceof JPasswordField).forEach(c->((JPasswordField)c).setText("synthetic-long-password"));
            DesktopTest.click(page,"Create my vault");assertEquals(false,DesktopTest.field(app,"locked"));
        });
    }
    @Test void filtersSortingFoldersTagsAndRecycleBinUseRealControls() throws Exception {
        withApp(true,(app,frame)->{
            unlock(app);
            DefaultListModel<?> model=(DefaultListModel<?>)DesktopTest.field(app,"listModel");
            assertEquals(2,model.size());
            JTextField search=(JTextField)DesktopTest.field(app,"searchField");search.setText("needle");assertEquals(1,model.size());
            search.setText("");JComboBox<?> folders=(JComboBox<?>)DesktopTest.field(app,"folderFilter");folders.setSelectedItem("Personal");assertEquals(1,model.size());
            assertEquals("Beta",((VaultEntry)model.get(0)).title());folders.setSelectedItem("All folders");
            JComboBox<?> category=(JComboBox<?>)DesktopTest.field(app,"categoryFilter");category.setSelectedItem("Favorites");assertEquals(1,model.size());
            category.setSelectedItem("Recycle bin");assertEquals("Deleted",((VaultEntry)model.get(0)).title());
            category.setSelectedItem("All records");((JComboBox<?>)DesktopTest.field(app,"sortFilter")).setSelectedItem("Recently updated");
            assertEquals("Beta",((VaultEntry)model.get(0)).title());
        });
    }
    @Test void lockingClosesAndClearsOwnedSecretDialogs() throws Exception {
        withApp(true,(app,frame)->{
            unlock(app);JDialog dialog=new JDialog(frame,"Synthetic dialog",true);
            JPasswordField secret=new JPasswordField("synthetic-secret");dialog.add(secret);dialog.pack();
            Throwable[] failure=new Throwable[1];
            javax.swing.Timer timer=new javax.swing.Timer(100,event->{try {DesktopTest.invoke(app,"lockVault");}catch(Throwable error){failure[0]=error;dialog.dispose();}});
            timer.setRepeats(false);timer.start();dialog.setVisible(true);
            if(failure[0]!=null) throw new AssertionError(failure[0]);
            assertFalse(dialog.isDisplayable());assertEquals(0,secret.getPassword().length);assertEquals(true,DesktopTest.field(app,"locked"));
        });
    }
    @SuppressWarnings("unchecked")
    @Test void failedSaveRollsBackInMemoryEditAndPreservesOtherWriter() throws Exception {
        withApp(true,(app,frame)->{
            unlock(app);VaultStore source=(VaultStore)DesktopTest.field(app,"store");VaultStore other=new VaultStore(source.path());
            other.load("synthetic-long-password".toCharArray());other.save("synthetic-long-password".toCharArray(),List.of(VaultEntry.create("external","","dummy","","","Login")));
            List<VaultEntry> entries=(List<VaultEntry>)DesktopTest.field(app,"entries");entries.add(VaultEntry.create("unsaved","","dummy","","","Login"));
            javax.swing.Timer dismiss=new javax.swing.Timer(100,event->{for(Window window:frame.getOwnedWindows())if(window instanceof Dialog dialog&&dialog.getTitle().equals("Password Guruji"))dialog.dispose();});
            dismiss.start();try { assertEquals(false,call(app,"saveVault")); }finally {dismiss.stop();}
            assertEquals(3,entries.size());assertTrue(entries.stream().noneMatch(e->e.title().equals("unsaved")));
            assertEquals("external",other.load("synthetic-long-password".toCharArray()).getFirst().title());
        });
    }
    @Test void importedMarkupDisplaysLiterallyWithoutInstallingAnHtmlView() throws Exception {
        try (var listener = new java.net.ServerSocket(0,1,java.net.InetAddress.getByName("127.0.0.1"))) {
            listener.setSoTimeout(400);
            String markup = "<html><a href='http://127.0.0.1:" + listener.getLocalPort() + "/'>東京</a></html>";
            Path csv = dir.resolve("markup.csv");
            VaultCsv.exportFile(csv,List.of(VaultEntry.create(markup,"","","","",markup,markup,false,
                    List.of(markup),Map.of(markup,"dummy"))));
            VaultEntry imported = VaultCsv.importFile(csv).getFirst();
            withApp(true,(app,frame)->{
                unlock(app);
                Method details = PasswordGurujiApp.class.getDeclaredMethod("showDetails",VaultEntry.class);
                details.setAccessible(true);
                details.invoke(app,imported);
                var labels = DesktopTest.descendants((Container)DesktopTest.field(app,"detailPanel")).stream()
                        .filter(JLabel.class::isInstance).map(JLabel.class::cast).toList();
                assertTrue(labels.stream().anyMatch(label -> markup.equals(label.getText())));
                assertTrue(labels.stream().anyMatch(label -> label.getText().contains("Tags: "+markup)));
                for (JLabel label : labels) assertNull(label.getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey));
                JComboBox<?> combo = (JComboBox<?>)DesktopTest.field(app,"folderFilter");
                @SuppressWarnings("unchecked")
                ListCellRenderer<Object> renderer = (ListCellRenderer<Object>)combo.getRenderer();
                JLabel folder = (JLabel)renderer.getListCellRendererComponent(new JList<>(),markup,0,false,false);
                assertEquals(markup,folder.getText());
                assertNull(folder.getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey));
            });
            assertThrows(java.net.SocketTimeoutException.class,listener::accept);
        }
    }

    @Test void cancelingBackupLeavesSourceBytesUnchanged() throws Exception {
        withApp(true,(app,frame)->{
            unlock(app);
            VaultStore source = (VaultStore)DesktopTest.field(app,"store");
            byte[] before = Files.readAllBytes(source.path());
            boolean[] canceled = {false};
            long deadline = System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
            javax.swing.Timer driver = new javax.swing.Timer(50,event->{
                for(Window window:frame.getOwnedWindows()) {
                    if(!window.isShowing()) continue;
                    for(Component component:DesktopTest.descendants(window)) {
                        if(component instanceof JFileChooser chooser) {
                            canceled[0]=true;
                            chooser.cancelSelection();
                            return;
                        }
                    }
                    if(System.nanoTime()>deadline) window.dispose();
                }
            });
            driver.start();
            try { DesktopTest.invoke(app,"createBackup"); }
            finally { driver.stop(); }
            assertTrue(canceled[0]);
            assertArrayEquals(before,Files.readAllBytes(source.path()));
        });
    }

    @Test void keyboardCopiesClearOnTimeoutLockAndShutdownWithoutClearingOtherContent() throws Exception {
        withApp(true,(app,frame)->{
            unlock(app);
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            UiHardeningTest.set(app,"clipboardClearSeconds",1);
            copyRevealedPassword(app);
            assertEquals("dummy-a",clipboard.getData(DataFlavor.stringFlavor));
            SecondaryLoop loop = Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
            javax.swing.Timer timer = new javax.swing.Timer(1600,event->loop.exit());
            timer.setRepeats(false);
            timer.start();
            try { assertTrue(loop.enter()); }
            finally { timer.stop(); }
            assertEquals("",clipboard.getData(DataFlavor.stringFlavor));

            UiHardeningTest.set(app,"clipboardClearSeconds",30);
            copyRevealedPassword(app);
            DesktopTest.invoke(app,"lockVault");
            assertEquals("",clipboard.getData(DataFlavor.stringFlavor));

            unlock(app);
            copyRevealedPassword(app);
            clipboard.setContents(new StringSelection("dummy-a"),null);
            DesktopTest.invoke(app,"lockVault");
            assertEquals("dummy-a",clipboard.getData(DataFlavor.stringFlavor));

            unlock(app);
            copyRevealedPassword(app);
            frame.dispatchEvent(new java.awt.event.WindowEvent(frame,java.awt.event.WindowEvent.WINDOW_CLOSING));
            assertFalse(frame.isDisplayable());
            assertEquals("",clipboard.getData(DataFlavor.stringFlavor));
        });
    }

    private static void copyRevealedPassword(PasswordGurujiApp app) throws Exception {
        JList<?> entries = (JList<?>)DesktopTest.field(app,"entryList");
        entries.setSelectedIndex(0);
        Container details = (Container)DesktopTest.field(app,"detailPanel");
        for(Component component:DesktopTest.descendants(details))
            if(component instanceof JToggleButton button && "Show".equals(button.getText())) button.doClick();
        JTextField field = DesktopTest.descendants(details).stream().filter(JTextField.class::isInstance)
                .map(JTextField.class::cast).filter(text -> "dummy-a".equals(text.getText())).findFirst().orElseThrow();
        field.selectAll();
        var stroke = KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_C,Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx());
        Object binding = field.getInputMap().get(stroke);
        assertNotNull(binding);
        Action copy = field.getActionMap().get(binding);
        assertNotNull(copy);
        copy.actionPerformed(new java.awt.event.ActionEvent(field,java.awt.event.ActionEvent.ACTION_PERFORMED,binding.toString()));
    }

    @Test void copyingIdenticalTextElsewhereDoesNotEraseIt() throws Exception {
        Clipboard clipboard=new Clipboard("same-text-owner-test");SecretClipboard owner=new SecretClipboard(clipboard);
        SwingUtilities.invokeAndWait(()->{owner.copy("identical dummy",1000);clipboard.setContents(new StringSelection("identical dummy"),null);owner.clear();});
        assertEquals("identical dummy",clipboard.getData(DataFlavor.stringFlavor));
    }
}
