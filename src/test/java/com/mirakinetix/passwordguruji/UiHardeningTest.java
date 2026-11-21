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
    @Test void copyingIdenticalTextElsewhereDoesNotEraseIt() throws Exception {
        Clipboard clipboard=new Clipboard("same-text-owner-test");SecretClipboard owner=new SecretClipboard(clipboard);
        SwingUtilities.invokeAndWait(()->{owner.copy("identical dummy",1000);clipboard.setContents(new StringSelection("identical dummy"),null);owner.clear();});
        assertEquals("identical dummy",clipboard.getData(DataFlavor.stringFlavor));
    }
}
