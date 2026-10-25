package com.mirakinetix.passwordguruji;

import javax.crypto.AEADBadTagException;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.security.GeneralSecurityException;
import java.text.DateFormat;
import java.util.*;
import java.util.List;
import java.util.prefs.Preferences;

public final class PasswordGurujiApp {
    // Temporary local-testing threshold. Raise to 15 after local testing is complete.
    private static final int MIN_MASTER_PASSWORD_LENGTH = 6;
    private static final Color BG = new Color(30, 30, 30), PANEL = new Color(37, 37, 38);
    private static final Color PANEL_2 = new Color(60, 60, 60), BORDER = new Color(69, 69, 69);
    private static final Color TEXT = new Color(204, 204, 204), MUTED = new Color(157, 157, 157);
    private static final Color ACCENT = new Color(79, 193, 255), ACTION = new Color(14, 99, 156), SELECTED = new Color(9, 71, 113), DANGER = new Color(244, 135, 113);
    private static final String[] CATEGORIES = {"Login", "Secure note", "Identity", "Payment", "Recovery code", "License", "Other"};

    private final JFrame frame = new JFrame("Password Guruji");
    private final JPanel pagePanel = new JPanel(new CardLayout());
    private final JPanel vaultPage = new JPanel(new BorderLayout());
    private final JPanel detailPanel = new JPanel(new BorderLayout());
    private final DefaultListModel<VaultEntry> listModel = new DefaultListModel<>();
    private final JList<VaultEntry> entryList = new JList<>(listModel);
    private final JTextField searchField = new JTextField();
    private final JComboBox<String> categoryFilter = new JComboBox<>();
    private final JComboBox<String> folderFilter = new JComboBox<>();
    private final JComboBox<String> sortFilter = new JComboBox<>(new String[]{"Name A–Z", "Recently updated", "Oldest updated"});
    private final JLabel listSummary = new JLabel("0 records");
    private final Preferences preferences = Preferences.userNodeForPackage(PasswordGurujiApp.class);
    private VaultStore store = new VaultStore(VaultStore.defaultPath());
    private final List<VaultEntry> entries = new ArrayList<>();
    private char[] masterPassword;
    private JTextArea vaultPathText;
    private boolean locked = true;
    private long lastActivity = System.currentTimeMillis();
    private final javax.swing.Timer idleTimer;
    private javax.swing.Timer totpTimer;
    private int lockAfterMinutes;
    private int clipboardClearSeconds;
    private JPanel unlockPage;

    public static void main(String[] args) { SwingUtilities.invokeLater(() -> new PasswordGurujiApp().launch()); }

    private PasswordGurujiApp() {
        lockAfterMinutes = preferences.getInt("lockAfterMinutes", 5);
        clipboardClearSeconds = Math.max(5, Math.min(120, preferences.getInt("clipboardClearSeconds", 30)));
        idleTimer = new javax.swing.Timer(1000, e -> {
            if (!locked && lockAfterMinutes > 0 && System.currentTimeMillis() - lastActivity > lockAfterMinutes * 60_000L) lockVault();
        });
        idleTimer.start();
        Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
            if (event instanceof InputEvent && !locked) lastActivity = System.currentTimeMillis();
        }, AWTEvent.KEY_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);
    }

    private void launch() {
        try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); } catch (Exception ignored) {}
        java.net.URL appIcon = PasswordGurujiApp.class.getResource("/com/mirakinetix/passwordguruji/brand/mirakinetix-app-icon.png");
        if (appIcon != null) frame.setIconImage(Toolkit.getDefaultToolkit().getImage(appIcon));
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.setMinimumSize(new Dimension(980, 650)); frame.setSize(1220, 800); frame.setLocationRelativeTo(null);
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                if (!saveVault()) return;
                clearSensitiveState(); idleTimer.stop(); frame.dispose();
            }
        });
        unlockPage = buildUnlockPage();
        pagePanel.add(unlockPage, "unlock");
        pagePanel.add(buildVaultPage(), "vault");
        JPanel appRoot = new JPanel(new BorderLayout());
        appRoot.setBackground(BG);
        appRoot.add(pagePanel, BorderLayout.CENTER);
        appRoot.add(buildAppFooter(), BorderLayout.SOUTH);
        frame.setContentPane(appRoot);
        frame.setVisible(true);
        showUnlock();
    }

    private JPanel buildUnlockPage() {
        JPanel outer = new JPanel(new GridBagLayout()); outer.setBackground(BG);
        JPanel card = new JPanel(); card.setBackground(PANEL); card.setBorder(new EmptyBorder(30, 36, 28, 36));
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.add(leftAligned(label("MIRAKINETIX", 12, ACCENT, Font.BOLD)));
        card.add(Box.createVerticalStrut(9)); card.add(leftAligned(label("Password Guruji", 29, TEXT, Font.BOLD)));
        card.add(Box.createVerticalStrut(6)); card.add(leftAligned(label("Your private vault stays on this device.", 14, MUTED, Font.PLAIN)));
        vaultPathText = new JTextArea("Offline by design · No account · No network connection\nVault file:\n" + store.path());
        JTextArea intro = vaultPathText;
        intro.setEditable(false); intro.setFocusable(false); intro.setLineWrap(true); intro.setWrapStyleWord(false);
        intro.setForeground(MUTED); intro.setBackground(PANEL); intro.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        intro.setAlignmentX(Component.LEFT_ALIGNMENT);
        intro.setBorder(new EmptyBorder(14, 0, 14, 0)); card.add(intro);

        boolean creating = !store.exists();
        JPasswordField password = new JPasswordField(28), confirm = new JPasswordField(28);
        styleField(password); styleField(confirm);
        password.setAlignmentX(Component.LEFT_ALIGNMENT); confirm.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel status = leftAligned(label("", 12, MUTED, Font.PLAIN));
        card.add(leftAligned(label("Master password", 12, MUTED, Font.BOLD)));
        card.add(Box.createVerticalStrut(6)); card.add(password);
        if (creating) {
            card.add(Box.createVerticalStrut(10)); card.add(leftAligned(label("Confirm master password", 12, MUTED, Font.BOLD)));
            card.add(Box.createVerticalStrut(6)); card.add(confirm);
        }
        card.add(Box.createVerticalStrut(15));
        JButton primary = primaryButton(creating ? "Create my vault" : "Unlock vault");
        primary.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(primary); card.add(Box.createVerticalStrut(10)); card.add(status);
        card.add(Box.createVerticalStrut(6));
        card.add(leftAligned(label("For local testing, use at least 6 characters. You can raise this to 15 later.", 11, MUTED, Font.PLAIN)));
        card.add(Box.createVerticalStrut(4));
        card.add(leftAligned(label("Your master password cannot be recovered. Keep encrypted backups.", 11, MUTED, Font.PLAIN)));
        card.add(Box.createVerticalStrut(12));
        JPanel pathActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)); pathActions.setOpaque(false);
        pathActions.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton choose = primaryButton(creating ? "Choose vault location" : "Open another vault");
        pathActions.add(choose); card.add(pathActions);
        primary.addActionListener(e -> {
            char[] pass = password.getPassword(), confirmPass = confirm.getPassword();
            password.setText(""); confirm.setText("");
            if (creating && pass.length < MIN_MASTER_PASSWORD_LENGTH) {
                setStatus(status, "Use at least " + MIN_MASTER_PASSWORD_LENGTH + " characters.", true);
                wipe(pass); wipe(confirmPass); return;
            }
            if (creating && !Arrays.equals(pass, confirmPass)) {
                setStatus(status, "The two master passwords do not match.", true);
                wipe(pass); wipe(confirmPass); return;
            }
            try {
                if (creating) {
                    store.create(pass); masterPassword = pass; entries.clear();
                } else {
                    List<VaultEntry> loaded = store.load(pass);
                    masterPassword = pass; entries.clear(); entries.addAll(loaded);
                }
                wipe(confirmPass); enterVault();
            } catch (AEADBadTagException wrongPassword) {
                wipe(pass); wipe(confirmPass); setStatus(status, "That password did not unlock the vault.", true);
            } catch (IOException | GeneralSecurityException ex) {
                wipe(pass); wipe(confirmPass); showError("Could not open the vault", ex);
            }
        });
        password.addActionListener(e -> primary.doClick()); confirm.addActionListener(e -> primary.doClick());
        choose.addActionListener(e -> {
            Path previous=store.path();
            selectVaultLocation(creating,status);
            if(!previous.equals(store.path())){
                pagePanel.remove(unlockPage);
                unlockPage=buildUnlockPage();
                pagePanel.add(unlockPage,"unlock");
                showUnlock();
                pagePanel.revalidate();pagePanel.repaint();
            }
        });
        outer.add(card); return outer;
    }

    private JPanel buildVaultPage() {
        vaultPage.setBackground(BG);
        JPanel header = new JPanel(new BorderLayout()); header.setBackground(BG);
        header.setBorder(new EmptyBorder(16, 20, 12, 20));
        JPanel brand = new JPanel(); brand.setOpaque(false); brand.setLayout(new BoxLayout(brand, BoxLayout.Y_AXIS));
        brand.add(label("MIRAKINETIX", 10, ACCENT, Font.BOLD)); brand.add(Box.createVerticalStrut(3));
        brand.add(label("Password Guruji", 22, TEXT, Font.BOLD));
        header.add(brand, BorderLayout.WEST);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 7, 0)); actions.setOpaque(false);
        JButton generator = subtleButton("Generate"), health = subtleButton("Health");
        JButton backup = subtleButton("Backup / restore"), importExport = subtleButton("Import / export");
        JButton settings = subtleButton("Settings"), lock = primaryButton("Lock");
        for (JButton b : List.of(generator, health, backup, importExport, settings, lock)) actions.add(b);
        header.add(actions, BorderLayout.EAST);

        JPanel body = new JPanel(new BorderLayout(14, 0)); body.setBackground(BG);
        body.setBorder(new EmptyBorder(0, 18, 18, 18));
        JPanel sidebar = new JPanel(new BorderLayout(0, 10)); sidebar.setPreferredSize(new Dimension(325, 0));
        sidebar.setBackground(PANEL); sidebar.setBorder(new EmptyBorder(14, 13, 12, 13));
        JButton add = primaryButton("+  New entry");
        styleField(searchField);
        searchField.setToolTipText("Search names, usernames, websites, notes, folders, tags, and custom-field names. Password values are excluded.");
        categoryFilter.setModel(new DefaultComboBoxModel<>(new String[]{"All records","Favorites","Login","Secure note","Identity","Payment","Recovery code","License","Other","Recycle bin"}));
        folderFilter.addItem("All folders");
        styleCombo(categoryFilter); styleCombo(folderFilter); styleCombo(sortFilter);
        JPanel controls = new JPanel(); controls.setOpaque(false); controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        controls.add(label("YOUR VAULT", 11, MUTED, Font.BOLD)); controls.add(Box.createVerticalStrut(8));
        controls.add(add); controls.add(Box.createVerticalStrut(9)); controls.add(searchField);
        JPanel filters = new JPanel(new GridLayout(0,1,6,5)); filters.setOpaque(false);
        filters.add(categoryFilter); filters.add(folderFilter); filters.add(sortFilter);
        controls.add(Box.createVerticalStrut(8)); controls.add(filters);
        sidebar.add(controls, BorderLayout.NORTH);

        entryList.setBackground(PANEL); entryList.setForeground(TEXT); entryList.setSelectionBackground(SELECTED);
        entryList.setSelectionForeground(TEXT); entryList.setFixedCellHeight(58); entryList.setCellRenderer(new EntryRenderer());
        JScrollPane listScroll = new JScrollPane(entryList); listScroll.setBorder(BorderFactory.createEmptyBorder());
        listScroll.getViewport().setBackground(PANEL); sidebar.add(listScroll, BorderLayout.CENTER);
        listSummary.setForeground(MUTED); listSummary.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        sidebar.add(listSummary, BorderLayout.SOUTH);

        detailPanel.setBackground(PANEL); detailPanel.setBorder(new EmptyBorder(20, 22, 20, 22));
        body.add(sidebar, BorderLayout.WEST); body.add(detailPanel, BorderLayout.CENTER);
        vaultPage.add(header, BorderLayout.NORTH); vaultPage.add(body, BorderLayout.CENTER);

        add.addActionListener(e -> editEntry(null));
        generator.addActionListener(e -> showGenerator());
        health.addActionListener(e -> showHealthReport());
        backup.addActionListener(e -> backupOrRestore());
        importExport.addActionListener(e -> importOrExport());
        settings.addActionListener(e -> showSettings());
        lock.addActionListener(e -> lockVault());
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { refreshList(); }
            public void removeUpdate(DocumentEvent e) { refreshList(); }
            public void changedUpdate(DocumentEvent e) { refreshList(); }
        });
        categoryFilter.addActionListener(e -> refreshList());
        folderFilter.addActionListener(e -> refreshList());
        sortFilter.addActionListener(e -> refreshList());
        entryList.addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) showDetails(entryList.getSelectedValue()); });
        entryList.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && entryList.getSelectedValue() != null) editEntry(entryList.getSelectedValue());
            }
        });
        return vaultPage;
    }

    private void enterVault() {
        locked = false; lastActivity = System.currentTimeMillis();
        ((CardLayout)pagePanel.getLayout()).show(pagePanel, "vault");
        frame.setTitle("Password Guruji · Offline vault");
        refreshFolders(); refreshList(); if (entryList.getSelectedValue() == null) showDetails(null);
    }

    private void showUnlock() {
        locked = true; ((CardLayout)pagePanel.getLayout()).show(pagePanel, "unlock");
        frame.setTitle(store.exists() ? "Password Guruji · Locked" : "Password Guruji · Create your vault");
    }

    private void refreshFolders() {
        String previous = Objects.toString(folderFilter.getSelectedItem(), "All folders");
        folderFilter.removeAllItems(); folderFilter.addItem("All folders");
        entries.stream().map(VaultEntry::folder).filter(s -> !s.isBlank()).distinct().sorted(String.CASE_INSENSITIVE_ORDER).forEach(folderFilter::addItem);
        folderFilter.setSelectedItem(previous);
    }

    private void refreshList() {
        String query = searchField.getText().trim().toLowerCase(Locale.ROOT);
        String cat = Objects.toString(categoryFilter.getSelectedItem(), "All records");
        String folder = Objects.toString(folderFilter.getSelectedItem(), "All folders");
        String sort = Objects.toString(sortFilter.getSelectedItem(), "Name A–Z");
        String selectedId = entryList.getSelectedValue() == null ? null : entryList.getSelectedValue().id();
        Comparator<VaultEntry> ordering = switch (sort) {
            case "Recently updated" -> Comparator.comparingLong(VaultEntry::updatedAt).reversed()
                    .thenComparing(VaultEntry::title, String.CASE_INSENSITIVE_ORDER);
            case "Oldest updated" -> Comparator.comparingLong(VaultEntry::updatedAt)
                    .thenComparing(VaultEntry::title, String.CASE_INSENSITIVE_ORDER);
            default -> Comparator.comparing(VaultEntry::favorite).reversed()
                    .thenComparing(VaultEntry::title, String.CASE_INSENSITIVE_ORDER);
        };
        listModel.clear();
        entries.stream()
                .filter(e -> cat.equals("Recycle bin") ? e.archived() : !e.archived() && (cat.equals("All records") || (cat.equals("Favorites") ? e.favorite() : e.category().equals(cat))))
                .filter(e -> folder.equals("All folders") || e.folder().equals(folder))
                .filter(e -> query.isEmpty() || searchable(e).contains(query))
                .sorted(ordering)
                .forEach(listModel::addElement);
        listSummary.setText(listModel.size() + (listModel.size() == 1 ? " record" : " records"));
        if (selectedId != null) {
            for (int i=0; i<listModel.size(); i++) if (listModel.get(i).id().equals(selectedId)) { entryList.setSelectedIndex(i); return; }
        }
        if (listModel.size() > 0 && entryList.getSelectedIndex() < 0) entryList.setSelectedIndex(0);
        else if (listModel.isEmpty()) showDetails(null);
    }

    private String searchable(VaultEntry e) {
        String text=e.title()+" "+e.username()+" "+e.url()+" "+e.notes()+" "+e.category()+" "+
                e.folder()+" "+String.join(" ",e.tags())+" "+String.join(" ",e.customFields().keySet());
        return text.toLowerCase(Locale.ROOT);
    }
    private void showDetails(VaultEntry entry) {
        stopTotpTimer(); detailPanel.removeAll();
        if (entry == null) {
            JPanel empty = new JPanel(); empty.setOpaque(false); empty.setLayout(new BoxLayout(empty, BoxLayout.Y_AXIS));
            empty.add(Box.createVerticalGlue());
            JLabel title = label(entries.isEmpty() ? "Your vault is ready" : "Select a record", 23, TEXT, Font.BOLD);
            title.setAlignmentX(Component.LEFT_ALIGNMENT); empty.add(title); empty.add(Box.createVerticalStrut(8));
            JLabel message = label(entries.isEmpty() ? "Add a login or secure note to get started." : "Choose a record from the list.", 14, MUTED, Font.PLAIN);
            message.setAlignmentX(Component.LEFT_ALIGNMENT); empty.add(message); empty.add(Box.createVerticalGlue());
            detailPanel.add(empty, BorderLayout.CENTER);
        } else {
            JPanel top = new JPanel(new BorderLayout()); top.setOpaque(false);
            JPanel heading = new JPanel(); heading.setOpaque(false); heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
            heading.add(label(entry.title(), 24, TEXT, Font.BOLD));
            String sub = entry.category() + (entry.folder().isBlank() ? "" : "  ·  " + entry.folder()) +
                    "  ·  Updated " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(entry.updatedAt());
            heading.add(Box.createVerticalStrut(5)); heading.add(label(sub, 12, MUTED, Font.PLAIN));
            if (!entry.tags().isEmpty()) heading.add(label("Tags: " + String.join(", ", entry.tags()), 11, MUTED, Font.PLAIN));
            top.add(heading, BorderLayout.WEST);
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0)); buttons.setOpaque(false);
            JToggleButton favorite = subtleToggle(entry.favorite() ? "★ Favorite" : "☆ Favorite"); favorite.setSelected(entry.favorite());
            JButton history = subtleButton("History");
            buttons.add(history);
            if (entry.archived()) {
                JButton restore = subtleButton("Restore"), delete = subtleButton("Delete permanently");
                buttons.add(restore); buttons.add(delete);
                restore.addActionListener(e -> restoreEntry(entry));
                delete.addActionListener(e -> deleteEntry(entry));
            } else {
                JButton edit = subtleButton("Edit"), archive = subtleButton("Move to recycle bin");
                buttons.add(favorite); buttons.add(edit); buttons.add(archive);
                favorite.addActionListener(e -> {
                    int ix=findById(entry.id()); if(ix>=0){ entries.set(ix,entry.withOrganization(entry.folder(),favorite.isSelected(),entry.tags())); saveVault(); refreshList(); }
                });
                edit.addActionListener(e -> editEntry(entry));
                archive.addActionListener(e -> deleteEntry(entry));
            }
            top.add(buttons, BorderLayout.EAST);
            detailPanel.add(top, BorderLayout.NORTH);
            JPanel fields = new JPanel(new GridBagLayout()); fields.setOpaque(false);
            GridBagConstraints c = new GridBagConstraints(); c.gridx=0; c.weightx=1; c.fill=GridBagConstraints.HORIZONTAL; c.anchor=GridBagConstraints.WEST;
            int row=0;
            if (!entry.username().isBlank()) addDetailField(fields,c,row++,"USERNAME",entry.username(),true);
            if (!entry.password().isBlank()) addDetailField(fields,c,row++,"PASSWORD",entry.password(),true);
            if (!entry.url().isBlank()) addDetailField(fields,c,row++,"WEBSITE",entry.url(),false);
            for (Map.Entry<String,String> custom : entry.customFields().entrySet()) {
                if (custom.getKey().equalsIgnoreCase("TOTP secret")) continue;
                addDetailField(fields,c,row++,custom.getKey(),custom.getValue(),true);
            }
            if (!entry.notes().isBlank()) addDetailField(fields,c,row++,"NOTES",entry.notes(),false);
            String authenticatorSecret = totpSecret(entry);
            if (authenticatorSecret != null) addTotpField(fields,c,row++,authenticatorSecret);
            JScrollPane scroll = new JScrollPane(fields); scroll.setBorder(BorderFactory.createEmptyBorder());
            scroll.getViewport().setOpaque(false); scroll.setOpaque(false); detailPanel.add(scroll, BorderLayout.CENTER);
            history.addActionListener(e -> showHistory(entry));
        }
        detailPanel.revalidate(); detailPanel.repaint();
    }

    private void addDetailField(JPanel panel, GridBagConstraints c, int row, String name, String value, boolean secret) {
        JPanel box = new JPanel(new BorderLayout(8,6)); box.setOpaque(false); box.setBorder(new EmptyBorder(12,0,4,0));
        box.add(label(name.toUpperCase(Locale.ROOT),10,MUTED,Font.BOLD),BorderLayout.NORTH);
        JTextField shown=new JTextField(secret && !safe(value).isEmpty() ? "••••••••••••" : safe(value)); shown.setEditable(false); styleField(shown);
        JPanel controls=new JPanel(new BorderLayout(6,0)); controls.setOpaque(false); controls.add(shown,BorderLayout.CENTER);
        if(secret){
            JToggleButton reveal=subtleToggle("Show"); controls.add(reveal,BorderLayout.WEST);
            reveal.addActionListener(e->{shown.setText(reveal.isSelected()?safe(value):"••••••••••••");reveal.setText(reveal.isSelected()?"Hide":"Show");});
        }
        JButton copy=subtleButton("Copy"); copy.addActionListener(e->copyToClipboard(safe(value))); controls.add(copy,BorderLayout.EAST);
        box.add(controls,BorderLayout.CENTER); c.gridy=row; c.insets=new Insets(0,0,2,0); panel.add(box,c);
    }

    private String totpSecret(VaultEntry entry) {        return entry.customFields().entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase("TOTP secret")).map(Map.Entry::getValue).filter(v -> !v.isBlank()).findFirst().orElse(null);    }    private void addTotpField(JPanel panel, GridBagConstraints c, int row, String secret) {
        JPanel box=new JPanel(new BorderLayout(8,6)); box.setOpaque(false); box.setBorder(new EmptyBorder(12,0,4,0));
        box.add(label("AUTHENTICATOR CODE",10,MUTED,Font.BOLD),BorderLayout.NORTH);
        JPanel values=new JPanel(new BorderLayout(8,0)); values.setOpaque(false);
        JLabel code=label("------",20,ACCENT,Font.BOLD); values.add(code,BorderLayout.CENTER);
        JButton copy=subtleButton("Copy code"); copy.addActionListener(e->{try{copyToClipboard(Totp.currentCode(secret));}catch(Exception ex){showError("Invalid authenticator secret",ex);}});
        values.add(copy,BorderLayout.EAST); box.add(values,BorderLayout.CENTER);
        JLabel timer=label("",11,MUTED,Font.PLAIN); box.add(timer,BorderLayout.SOUTH);
        c.gridy=row; c.insets=new Insets(0,0,2,0); panel.add(box,c);
        Runnable update=()->{try{code.setText(Totp.currentCode(secret));timer.setText("Refreshes in "+Totp.secondsRemaining()+" seconds");}catch(Exception ex){code.setText("Invalid secret");timer.setText("");}};
        update.run(); totpTimer=new javax.swing.Timer(1000,e->update.run()); totpTimer.start();
    }

    private void stopTotpTimer(){if(totpTimer!=null){totpTimer.stop();totpTimer=null;}}

    private void editEntry(VaultEntry existing) {
        JTextField title=new JTextField(existing==null?"":existing.title(),28);
        JComboBox<String> category=new JComboBox<>(CATEGORIES); if(existing!=null)category.setSelectedItem(existing.category());
        JTextField username=new JTextField(existing==null?"":existing.username(),28);
        JPasswordField password=new JPasswordField(existing==null?"":existing.password(),28);
        JTextField url=new JTextField(existing==null?"":existing.url(),28);
        JTextField folder=new JTextField(existing==null?"":existing.folder(),28);
        JTextField tags=new JTextField(existing==null?"":String.join(", ",existing.tags()),28);
        JCheckBox favorite=new JCheckBox("Favorite",existing!=null&&existing.favorite()); favorite.setOpaque(false); favorite.setForeground(TEXT);
        JTextArea notes=new JTextArea(existing==null?"":existing.notes(),4,28); notes.setLineWrap(true); notes.setWrapStyleWord(true); styleArea(notes);
        JTextArea custom=new JTextArea(existing==null?"":customText(existing.customFields()),5,28); custom.setLineWrap(true); custom.setWrapStyleWord(true); styleArea(custom);
        JPanel form=new JPanel(new GridBagLayout()); form.setBackground(PANEL);
        GridBagConstraints c=new GridBagConstraints(); c.insets=new Insets(4,5,4,5); c.anchor=GridBagConstraints.WEST;
        addFormRow(form,c,0,"Name",title); addFormRow(form,c,1,"Record type",category);
        addFormRow(form,c,2,"Username / value",username); addFormRow(form,c,3,"Password / secret",password);
        addFormRow(form,c,4,"Website",url); addFormRow(form,c,5,"Folder",folder);
        addFormRow(form,c,6,"Tags (comma separated)",tags); addFormRow(form,c,7,"Other fields (one name=value per line)",new JScrollPane(custom));
        addFormRow(form,c,8,"Notes",new JScrollPane(notes)); addFormRow(form,c,9,"",favorite);
        JScrollPane scroller=new JScrollPane(form); scroller.setPreferredSize(new Dimension(620,470)); scroller.getViewport().setBackground(PANEL);
        int answer=JOptionPane.showConfirmDialog(frame,scroller,existing==null?"New vault record":"Edit vault record",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE);
        if(answer==JOptionPane.OK_OPTION){
            String name=title.getText().trim();
            if(name.isEmpty()){JOptionPane.showMessageDialog(frame,"Enter a name for this record.","Name required",JOptionPane.WARNING_MESSAGE);}
            else{
                String secret=new String(password.getPassword());
                List<String>tagList=Arrays.stream(tags.getText().split(",")).map(String::trim).filter(s->!s.isEmpty()).distinct().limit(128).toList();
                Map<String,String> fields=parseCustom(custom.getText());
                String totpValue=fields.entrySet().stream().filter(f->f.getKey().equalsIgnoreCase("TOTP secret")).map(Map.Entry::getValue).findFirst().orElse("");
                if(!totpValue.isBlank()){try{Totp.currentCode(totpValue);}catch(Exception invalid){warn("The authenticator secret must be a valid Base32 value.");return;}}
                String kind=Objects.toString(category.getSelectedItem(),"Other");
                VaultEntry next;
                if(existing==null) next=VaultEntry.create(name,username.getText(),secret,url.getText(),notes.getText(),
                        kind,folder.getText().trim(),favorite.isSelected(),tagList,fields);
                else next=existing.revised(name,username.getText(),secret,url.getText(),notes.getText(),kind,
                        folder.getText().trim(),favorite.isSelected(),tagList,fields);                int ix=existing==null?-1:findById(existing.id());
                if(ix<0)entries.add(next);else entries.set(ix,next);
                if(saveVault()){refreshFolders();refreshList();selectById(next.id());}
            }
        }
        password.setText("");
    }

    private String customText(Map<String,String> fields) {
        StringBuilder out=new StringBuilder();
        fields.forEach((k,v)->{if(out.length()>0)out.append('\n');out.append(k).append('=').append(v);});
        return out.toString();
    }

    private Map<String,String> parseCustom(String text) {
        Map<String,String> fields=new LinkedHashMap<>();
        for(String line:text.split("\\R")){
            int split=line.indexOf('=');
            if(split>0){String key=line.substring(0,split).trim(),value=line.substring(split+1);if(!key.isEmpty())fields.put(key,value);}
        }
        return fields;
    }

    private void addFormRow(JPanel panel,GridBagConstraints c,int row,String name,Component field){
        c.gridy=row;c.gridx=0;c.weightx=0;c.fill=GridBagConstraints.NONE;
        panel.add(label(name,11,MUTED,Font.PLAIN),c);
        c.gridx=1;c.weightx=1;c.fill=GridBagConstraints.HORIZONTAL;panel.add(field,c);
    }

    private void selectVaultLocation(boolean creating, JLabel status) {
        JFileChooser chooser = new JFileChooser();
        if (creating) {
            chooser.setDialogTitle("Choose where to create your vault");
            chooser.setSelectedFile(store.path().toFile());
            if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) return;
            Path selected=chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
            if(Files.exists(selected)){setStatus(status,"That file already exists. Choose another location.",true);return;}
            store=new VaultStore(selected);
            setStatus(status,"Vault will be created at: "+selected,false);
        } else {
            chooser.setDialogTitle("Open an existing Password Guruji vault");
            chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Password Guruji vault (*.pgv)","pgv"));
            if(chooser.showOpenDialog(frame)!=JFileChooser.APPROVE_OPTION)return;
            store=new VaultStore(chooser.getSelectedFile().toPath());
            setStatus(status,"Selected vault: "+store.path(),false);
        }
    }

    private void deleteEntry(VaultEntry entry) {
        boolean permanently = entry.archived();
        String prompt = permanently
                ? "Permanently delete “" + entry.title() + "”? This cannot be undone."
                : "Move “" + entry.title() + "” to the recycle bin? You can restore it later.";
        int answer = JOptionPane.showConfirmDialog(frame, prompt,
                permanently ? "Delete permanently" : "Move to recycle bin",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) return;
        int index = findById(entry.id());
        if (index < 0) return;
        if (permanently) entries.remove(index);
        else entries.set(index, entry.withArchived(true));
        if (!saveVault()) {
            if (permanently) entries.add(Math.min(index, entries.size()), entry);
            else entries.set(index, entry);
            return;
        }
        refreshFolders();
        refreshList();
        if (entries.isEmpty()) showDetails(null);
    }

    private void restoreEntry(VaultEntry entry) {
        int index = findById(entry.id());
        if (index < 0 || !entry.archived()) return;
        entries.set(index, entry.withArchived(false));
        if (!saveVault()) {
            entries.set(index, entry);
            return;
        }
        categoryFilter.setSelectedItem("All records");
        refreshFolders();
        refreshList();
        selectById(entry.id());
    }

    private void showHistory(VaultEntry entry) {
        if(entry.history().isEmpty()){
            JOptionPane.showMessageDialog(frame,"No previous versions are available for this record.","Record history",JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        List<VaultEntry.Revision> revisions=new ArrayList<>(entry.history());
        Collections.reverse(revisions);
        JList<VaultEntry.Revision> choices=new JList<>(revisions.toArray(VaultEntry.Revision[]::new));
        choices.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        choices.setCellRenderer(new DefaultListCellRenderer(){
            @Override public Component getListCellRendererComponent(JList<?> list,Object value,int index,boolean selected,boolean focus){
                JLabel item=(JLabel)super.getListCellRendererComponent(list,value,index,selected,focus);
                VaultEntry.Revision revision=(VaultEntry.Revision)value;
                item.setText(DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(revision.timestamp())+
                        "  ·  "+(revision.password().isBlank()?"No password snapshot":"Password snapshot saved"));
                item.setBorder(new EmptyBorder(8,8,8,8));return item;
            }
        });
        choices.setSelectedIndex(0);
        JScrollPane scroll=new JScrollPane(choices);scroll.setPreferredSize(new Dimension(530,260));
        JPanel panel=new JPanel(new BorderLayout(0,8));panel.add(new JLabel("Select a previous snapshot. Restoring creates a new history version."),BorderLayout.NORTH);
        panel.add(scroll,BorderLayout.CENTER);
        int answer=JOptionPane.showConfirmDialog(frame,panel,"Record history · "+entry.title(),JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE);
        if(answer==JOptionPane.OK_OPTION&&choices.getSelectedValue()!=null){
            int confirm=JOptionPane.showConfirmDialog(frame,"Restore the selected snapshot for this record?","Restore record version",JOptionPane.YES_NO_OPTION);
            if(confirm==JOptionPane.YES_OPTION){
                int index=findById(entry.id());
                if(index>=0){entries.set(index,entry.restoreRevision(choices.getSelectedValue()));if(saveVault()){refreshList();selectById(entry.id());}}
            }
        }
    }
    private void showGenerator() {
        JSpinner length=new JSpinner(new SpinnerNumberModel(20,8,128,1));
        JCheckBox upper=new JCheckBox("Uppercase",true),lower=new JCheckBox("Lowercase",true);
        JCheckBox digits=new JCheckBox("Numbers",true),symbols=new JCheckBox("Symbols",true);
        JCheckBox phraseMode=new JCheckBox("Use passphrase");
        JCheckBox excludeAmbiguous=new JCheckBox("Exclude ambiguous characters",true);
        JTextField excludedCharacters=new JTextField(16);styleField(excludedCharacters);
        JSpinner wordCount=new JSpinner(new SpinnerNumberModel(8,6,12,1));
        JTextField separator=new JTextField("-",5);
        JPasswordField output=new JPasswordField(30);styleField(output);
        JLabel estimate=label("",11,MUTED,Font.PLAIN);
        JPanel options=new JPanel(new GridLayout(0,2,7,5));options.setBackground(PANEL);
        options.add(label("Password length",12,MUTED,Font.PLAIN));options.add(length);
        options.add(upper);options.add(lower);options.add(digits);options.add(symbols);
        options.add(phraseMode);options.add(excludeAmbiguous);
        options.add(label("Additional characters to exclude",12,MUTED,Font.PLAIN));options.add(excludedCharacters);
        options.add(label("Passphrase words",12,MUTED,Font.PLAIN));options.add(wordCount);
        options.add(label("Word separator",12,MUTED,Font.PLAIN));options.add(separator);
        JButton generate=primaryButton("Generate"),reveal=subtleButton("Show"),copy=subtleButton("Copy"),save=subtleButton("Save as record");
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.LEFT,6,0));actions.setOpaque(false);
        actions.add(generate);actions.add(reveal);actions.add(copy);actions.add(save);
        JPanel content=new JPanel(new BorderLayout(0,10));content.setBackground(PANEL);content.setBorder(new EmptyBorder(8,8,8,8));
        content.add(options,BorderLayout.NORTH);content.add(output,BorderLayout.CENTER);
        JPanel lowerPanel=new JPanel(new BorderLayout());lowerPanel.setOpaque(false);lowerPanel.add(estimate,BorderLayout.NORTH);lowerPanel.add(actions,BorderLayout.SOUTH);
        content.add(lowerPanel,BorderLayout.SOUTH);
        final boolean[] shown={false};
        Runnable update=()->{
            try{
                String exclusions=excludedCharacters.getText();
                if(excludeAmbiguous.isSelected())exclusions+="O0Il1|'"+(char)34+(char)92;
                String generated=phraseMode.isSelected()?PasswordGenerator.passphrase((Integer)wordCount.getValue(),separator.getText())
                        :PasswordGenerator.generate((Integer)length.getValue(),upper.isSelected(),lower.isSelected(),digits.isSelected(),symbols.isSelected(),exclusions);
                output.setText(generated);
                int bits=phraseMode.isSelected()?(int)Math.floor((Integer)wordCount.getValue()*(Math.log(PasswordGenerator.wordCount())/Math.log(2)))
                        :(int)Math.floor((Integer)length.getValue()*(Math.log(alphabetSize(upper.isSelected(),lower.isSelected(),digits.isSelected(),symbols.isSelected(),exclusions))/Math.log(2)));
                estimate.setText("Approximate random-choice strength: about "+bits+" bits (depends on these generator options).");
                shown[0]=false;output.setEchoChar('•');reveal.setText("Show");
            }catch(IllegalArgumentException ex){output.setText("");estimate.setText(ex.getMessage());}
        };
        generate.addActionListener(e->update.run());
        reveal.addActionListener(e->{shown[0]=!shown[0];output.setEchoChar(shown[0]?(char)0:'•');reveal.setText(shown[0]?"Hide":"Show");});
        copy.addActionListener(e->{char[] chars=output.getPassword();try{copyToClipboard(new String(chars));}finally{wipe(chars);}});
        save.addActionListener(e->{
            char[] chars=output.getPassword();
            try{
                if(chars.length==0)return;
                String name=JOptionPane.showInputDialog(frame,"Name this record:","New password");
                if(name!=null&&!name.isBlank()){
                    entries.add(VaultEntry.create(name.trim(),"",new String(chars),"","","Login"));
                    if(saveVault()){refreshFolders();refreshList();selectByTitle(name.trim());}
                }
            }finally{wipe(chars);}
        });
        phraseMode.addActionListener(e->{boolean phrase=phraseMode.isSelected();length.setEnabled(!phrase);upper.setEnabled(!phrase);lower.setEnabled(!phrase);digits.setEnabled(!phrase);symbols.setEnabled(!phrase);excludeAmbiguous.setEnabled(!phrase);excludedCharacters.setEnabled(!phrase);wordCount.setEnabled(phrase);separator.setEnabled(phrase);update.run();});
        wordCount.setEnabled(false);separator.setEnabled(false);update.run();
        JOptionPane.showMessageDialog(frame,content,"Offline password generator",JOptionPane.PLAIN_MESSAGE);
        char[] chars=output.getPassword();wipe(chars);output.setText("");
    }

    private int alphabetSize(boolean upper,boolean lower,boolean digits,boolean symbols,String excluded){
        return PasswordGenerator.characterPoolSize(upper,lower,digits,symbols,excluded);
    }

    private void showHealthReport() {
        PasswordHealth.Report report=PasswordHealth.analyze(entries);
        StringBuilder text=new StringBuilder("Local vault check\n\nRecords scanned: ").append(report.records())
                .append("\nPotentially weak passwords: ").append(report.weakCount())
                .append("\nCredentials reused in multiple records: ").append(report.reusedGroups().size())
                .append("\n\nThis check runs locally. It does not contact breach databases or the internet.");
        if(!report.weakEntries().isEmpty()){
            text.append("\n\nReview these records for length or common-pattern concerns:\n");
            report.weakEntries().stream().limit(30).forEach(name->text.append("• ").append(name).append('\n'));
        }
        if(!report.reusedGroups().isEmpty()){
            text.append("\nReused password groups:\n");
            report.reusedGroups().stream().limit(20).forEach(names->text.append("• ").append(String.join(", ",names)).append('\n'));
        }
        JTextArea area=new JTextArea(text.toString(),18,56);area.setEditable(false);area.setLineWrap(true);area.setWrapStyleWord(true);
        area.setBackground(PANEL);area.setForeground(TEXT);area.setBorder(new EmptyBorder(10,10,10,10));
        JOptionPane.showMessageDialog(frame,new JScrollPane(area),"Password health · Local only",JOptionPane.INFORMATION_MESSAGE);
    }

    private void backupOrRestore() {
        Object[] options={"Create encrypted backup","Restore from encrypted backup","Cancel"};
        int choice=JOptionPane.showOptionDialog(frame,"Backups remain encrypted and can be stored on removable media.",
                "Backup and restore",JOptionPane.DEFAULT_OPTION,JOptionPane.PLAIN_MESSAGE,null,options,options[0]);
        if(choice==0)createBackup();else if(choice==1)restoreBackup();
    }

    private void createBackup() {
        JFileChooser chooser=new JFileChooser();chooser.setDialogTitle("Save encrypted vault backup");
        chooser.setSelectedFile(new File("Password-Guruji-Backup.pgv"));
        if(chooser.showSaveDialog(frame)!=JFileChooser.APPROVE_OPTION)return;
        Path destination=chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
        if(destination.equals(store.path())){warn("Choose a different file for the backup.");return;}
        if(!saveVault())return;
        try{
            if(Files.exists(destination)){
                int replace=JOptionPane.showConfirmDialog(frame,"Replace the selected backup file?","Replace backup",JOptionPane.YES_NO_OPTION);
                if(replace!=JOptionPane.YES_OPTION)return;
            }
            Files.copy(store.path(),destination,StandardCopyOption.REPLACE_EXISTING);
            JOptionPane.showMessageDialog(frame,"Encrypted backup saved. Store it somewhere separate from this computer.");
        }catch(IOException ex){showError("Could not create the backup",ex);}
    }

    private void restoreBackup() {
        JFileChooser chooser=new JFileChooser();chooser.setDialogTitle("Choose an encrypted vault backup");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Password Guruji vault (*.pgv)","pgv"));
        if(chooser.showOpenDialog(frame)!=JFileChooser.APPROVE_OPTION)return;
        Path backup=chooser.getSelectedFile().toPath();
        if(backup.toAbsolutePath().normalize().equals(store.path())){warn("The selected file is already the active vault.");return;}
        char[] backupPassword=masterPassword.clone();
        List<VaultEntry> restored;
        try{
            restored=new VaultStore(backup).load(backupPassword);
        }catch(AEADBadTagException wrongKey){
            wipe(backupPassword);
            JPasswordField input=new JPasswordField(24);
            int answer=JOptionPane.showConfirmDialog(frame,input,"Enter the backup's master password",JOptionPane.OK_CANCEL_OPTION);
            if(answer!=JOptionPane.OK_OPTION)return;
            backupPassword=input.getPassword();
            try{restored=new VaultStore(backup).load(backupPassword);}
            catch(Exception ex){wipe(backupPassword);showError("Could not unlock that backup",ex);return;}
        }catch(Exception ex){wipe(backupPassword);showError("Could not read that backup",ex);return;}
        finally{if(backupPassword!=null)wipe(backupPassword);}
        int confirm=JOptionPane.showConfirmDialog(frame,
                "Replace the active vault with "+restored.size()+" records from this backup? The backup will be re-encrypted with the current master password.",
                "Restore encrypted backup",JOptionPane.YES_NO_OPTION,JOptionPane.WARNING_MESSAGE);
        if(confirm==JOptionPane.YES_OPTION){
            List<VaultEntry> old=new ArrayList<>(entries);entries.clear();entries.addAll(restored);
            if(saveVault()){refreshFolders();refreshList();showDetails(null);}else{entries.clear();entries.addAll(old);}
        }
    }

    private void importOrExport() {
        Object[] options={"Import Guruji CSV","Export plaintext CSV","Cancel"};
        int choice=JOptionPane.showOptionDialog(frame,"CSV files contain readable passwords. Keep exports protected and delete them when no longer needed.",
                "Import and export",JOptionPane.DEFAULT_OPTION,JOptionPane.WARNING_MESSAGE,null,options,options[0]);
        if(choice==0)importCsv();else if(choice==1)exportCsv();
    }

    private void importCsv() {
        int warning=JOptionPane.showConfirmDialog(frame,"Only import a Password Guruji CSV export that you trust. Imported passwords will be added to this encrypted vault.",
                "Import CSV",JOptionPane.OK_CANCEL_OPTION,JOptionPane.WARNING_MESSAGE);
        if(warning!=JOptionPane.OK_OPTION)return;
        JFileChooser chooser=new JFileChooser();chooser.setDialogTitle("Import Password Guruji CSV");
        if(chooser.showOpenDialog(frame)!=JFileChooser.APPROVE_OPTION)return;
        try{
            List<VaultEntry> imported=VaultCsv.importFile(chooser.getSelectedFile().toPath());
            if(imported.isEmpty()){warn("No records were found in that file.");return;}
            int merge=JOptionPane.showConfirmDialog(frame,"Add "+imported.size()+" imported records to this vault?",
                    "Confirm import",JOptionPane.YES_NO_OPTION);
            if(merge==JOptionPane.YES_OPTION){entries.addAll(imported);if(saveVault()){refreshFolders();refreshList();}}
        }catch(IOException ex){showError("Could not import CSV",ex);}
    }

    private void exportCsv() {
        int warning=JOptionPane.showConfirmDialog(frame,
                "This creates a plaintext CSV containing your passwords and notes. Anyone who can read that file can read your secrets. Continue?",
                "Plaintext export warning",JOptionPane.YES_NO_OPTION,JOptionPane.WARNING_MESSAGE);
        if(warning!=JOptionPane.YES_OPTION)return;
        JFileChooser chooser=new JFileChooser();chooser.setDialogTitle("Export plaintext CSV");
        chooser.setSelectedFile(new File("Password-Guruji-Export.csv"));
        if(chooser.showSaveDialog(frame)!=JFileChooser.APPROVE_OPTION)return;
        Path path=chooser.getSelectedFile().toPath();
        if(Files.exists(path)&&JOptionPane.showConfirmDialog(frame,"Replace this file?","Confirm export",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;
        try{VaultCsv.exportFile(path,entries);JOptionPane.showMessageDialog(frame,"CSV exported. Protect the file; it is not encrypted.");}
        catch(IOException ex){showError("Could not export CSV",ex);}
    }

    private void showSettings() {
        Object[] values={"1 minute","5 minutes","10 minutes","Never"};
        String current=lockAfterMinutes==0?"Never":lockAfterMinutes+" minute"+(lockAfterMinutes==1?"":"s");
        Object selection=JOptionPane.showInputDialog(frame,"Lock the vault after inactivity:","Settings",
                JOptionPane.PLAIN_MESSAGE,null,values,current);
        if(selection==null)return;
        lockAfterMinutes=switch(selection.toString()){case "1 minute"->1;case "10 minutes"->10;case "Never"->0;default->5;};
        preferences.putInt("lockAfterMinutes",lockAfterMinutes);
        Object[] clipboardOptions={"15 seconds","30 seconds","60 seconds"};
        Object currentClipboard=clipboardClearSeconds+" seconds";
        Object clipboardSelection=JOptionPane.showInputDialog(frame,"Clear copied secrets after:","Clipboard",
                JOptionPane.PLAIN_MESSAGE,null,clipboardOptions,currentClipboard);
        if(clipboardSelection!=null){
            clipboardClearSeconds=switch(clipboardSelection.toString()){case "15 seconds"->15;case "60 seconds"->60;default->30;};
            preferences.putInt("clipboardClearSeconds",clipboardClearSeconds);
        }
        Object[] tools={"Change master password","Open vault folder","Cancel"};
        int choice=JOptionPane.showOptionDialog(frame,"Vault: "+store.path(),"Vault settings",JOptionPane.DEFAULT_OPTION,
                JOptionPane.PLAIN_MESSAGE,null,tools,tools[0]);
        if(choice==0)changeMasterPassword();
        else if(choice==1)openVaultFolder();
    }

    private void changeMasterPassword() {
        JPasswordField next=new JPasswordField(24),confirm=new JPasswordField(24);
        JPanel panel=new JPanel(new GridLayout(0,1,5,5));panel.add(new JLabel("New master password (minimum "+MIN_MASTER_PASSWORD_LENGTH+" characters)"));panel.add(next);
        panel.add(new JLabel("Confirm new master password"));panel.add(confirm);
        int answer=JOptionPane.showConfirmDialog(frame,panel,"Change master password",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE);
        if(answer!=JOptionPane.OK_OPTION)return;
        char[] a=next.getPassword(),b=confirm.getPassword();next.setText("");confirm.setText("");
        try{
            if(a.length<MIN_MASTER_PASSWORD_LENGTH){warn("Use at least "+MIN_MASTER_PASSWORD_LENGTH+" characters.");return;}
            if(!Arrays.equals(a,b)){warn("The two passwords do not match.");return;}
            try{store.save(a,entries);wipe(masterPassword);masterPassword=a;a=null;JOptionPane.showMessageDialog(frame,"Master password changed.");}
            catch(IOException|GeneralSecurityException ex){showError("Could not change the master password",ex);}
        }finally{wipe(a);wipe(b);}
    }

    private void openVaultFolder() {
        try{
            Path parent=store.path().getParent();
            if(parent!=null&&Desktop.isDesktopSupported())Desktop.getDesktop().open(parent.toFile());
            else warn("Vault folder: "+store.path());
        }catch(IOException ex){showError("Could not open the vault folder",ex);}
    }

    private void lockVault() {
        if(!saveVault()){lastActivity=System.currentTimeMillis();return;}
        clearSensitiveState();showUnlock();
    }

    private void clearSensitiveState() {
        stopTotpTimer();wipe(masterPassword);masterPassword=null;entries.clear();listModel.clear();
        entryList.clearSelection();searchField.setText("");detailPanel.removeAll();
        detailPanel.revalidate();detailPanel.repaint();locked=true;
    }

    private boolean saveVault() {
        if(masterPassword==null||locked)return true;
        try{store.save(masterPassword,entries);return true;}
        catch(IOException|GeneralSecurityException ex){showError("Could not save the vault",ex);return false;}
    }

    private void copyToClipboard(String value) {
        if(value==null||value.isEmpty())return;
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(value),null);
        listSummary.setText("Copied · clipboard clears in "+clipboardClearSeconds+" seconds");
        javax.swing.Timer clear=new javax.swing.Timer(clipboardClearSeconds*1000,e->{
            try{
                String current=Toolkit.getDefaultToolkit().getSystemClipboard().getData(java.awt.datatransfer.DataFlavor.stringFlavor).toString();
                if(value.equals(current))Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(""),null);
            }catch(Exception ignored){}
        });
        clear.setRepeats(false);clear.start();
    }

    private void selectById(String id) {
        for(int i=0;i<listModel.size();i++)if(listModel.get(i).id().equals(id)){entryList.setSelectedIndex(i);return;}
    }
    private void selectByTitle(String title) {
        for(int i=0;i<listModel.size();i++)if(listModel.get(i).title().equals(title)){entryList.setSelectedIndex(i);return;}
    }
    private int findById(String id) {
        for(int i=0;i<entries.size();i++)if(entries.get(i).id().equals(id))return i;
        return -1;
    }

    private void showError(String title,Exception ex) {
        JOptionPane.showMessageDialog(frame,title+".\n"+safe(ex.getMessage()),"Password Guruji",JOptionPane.ERROR_MESSAGE);
    }
    private void warn(String message) { JOptionPane.showMessageDialog(frame,message,"Password Guruji",JOptionPane.WARNING_MESSAGE); }
    private void setStatus(JLabel label,String message,boolean error) { label.setForeground(error?DANGER:MUTED);label.setText(message); }
    private static String safe(String s){return s==null?"":s;}
    private static void wipe(char[] chars){if(chars!=null)Arrays.fill(chars,'\0');}

    private JPanel buildAppFooter() {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.CENTER, 9, 6));
        footer.setBackground(PANEL);
        footer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER));
        footer.add(label("MIRAKINETIX", 10, ACCENT, Font.BOLD));
        JLabel separator = label("·", 12, MUTED, Font.PLAIN);
        footer.add(separator);
        footer.add(externalLinkButton("LinkedIn", "https://www.linkedin.com/in/devdutt-parab"));
        JLabel linkSeparator = label("·", 12, MUTED, Font.PLAIN);
        footer.add(linkSeparator);
        footer.add(externalLinkButton("dparab.me", "https://dparab.me"));
        return footer;
    }
    private JButton externalLinkButton(String text, String address) {
        JButton link = new JButton("<html><u>" + text + "</u></html>");
        link.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        link.setForeground(ACCENT);
        link.setContentAreaFilled(false);
        link.setBorderPainted(false);
        link.setFocusPainted(true);
        link.setOpaque(false);
        link.setBorder(new EmptyBorder(3, 0, 3, 0));
        link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        link.setToolTipText("Opens in your browser only when clicked.");
        link.getAccessibleContext().setAccessibleName(text + " link");
        link.addActionListener(e -> {
            try {
                if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    throw new IOException("This desktop does not provide a browser action.");
                }
                Desktop.getDesktop().browse(java.net.URI.create(address));
            } catch (Exception ex) {
                showError("Could not open " + text, ex);
            }
        });
        return link;
    }
    private static <T extends JComponent> T leftAligned(T component) {
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        return component;
    }

    private JLabel label(String text,int size,Color color,int style){
        JLabel value=new JLabel(text);value.setFont(new Font(Font.SANS_SERIF,style,size));value.setForeground(color);return value;
    }
    private JButton primaryButton(String text){JButton b=new JButton(text);styleButton(b,true);return b;}
    private JButton subtleButton(String text){JButton b=new JButton(text);styleButton(b,false);return b;}
    private JToggleButton subtleToggle(String text){JToggleButton b=new JToggleButton(text);styleButton(b,false);return b;}
    private void styleButton(AbstractButton b, boolean primary) {
        if (b instanceof JToggleButton) {
            b.setUI(new javax.swing.plaf.basic.BasicToggleButtonUI());
            UIManager.put("ToggleButton.disabledText", MUTED);
        } else {
            b.setUI(new javax.swing.plaf.basic.BasicButtonUI());
            UIManager.put("Button.disabledText", MUTED);
        }
        b.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        b.setForeground(primary ? Color.WHITE : TEXT);
        b.setBackground(primary ? ACTION : PANEL_2);
        b.setContentAreaFilled(true); b.setBorderPainted(true); b.setFocusPainted(false); b.setOpaque(true);
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(primary ? ACTION : BORDER, 1, true),
                new EmptyBorder(8, 11, 8, 11)));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }
    private void styleField(JComponent c){
        c.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,13));c.setForeground(TEXT);c.setBackground(PANEL_2);
        c.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER),new EmptyBorder(7,9,7,9)));
        if(c instanceof JTextField f)f.setCaretColor(TEXT);
    }
    private void styleCombo(JComboBox<?> combo) {
        combo.setUI(new javax.swing.plaf.basic.BasicComboBoxUI() {
            @Override protected JButton createArrowButton() {
                JButton arrow = new JButton("▾");
                arrow.setUI(new javax.swing.plaf.basic.BasicButtonUI());
                arrow.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
                arrow.setForeground(TEXT); arrow.setBackground(PANEL_2);
                arrow.setContentAreaFilled(true); arrow.setOpaque(true); arrow.setFocusable(false);
                arrow.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, BORDER));
                return arrow;
            }
        });
        combo.setForeground(TEXT); combo.setBackground(PANEL_2);
        combo.setBorder(BorderFactory.createLineBorder(BORDER));
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                                     boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                setOpaque(true); setBackground(selected ? SELECTED : PANEL_2);
                setForeground(TEXT);
                setBorder(new EmptyBorder(5, 8, 5, 8));
                return this;
            }
        });
    }
    private void styleArea(JTextArea a){
        a.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,13));a.setForeground(TEXT);a.setBackground(PANEL_2);
        a.setCaretColor(TEXT);a.setBorder(new EmptyBorder(7,9,7,9));
    }
    private String escape(String value){return safe(value).replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");}

    private final class EntryRenderer extends DefaultListCellRenderer {
        @Override public Component getListCellRendererComponent(JList<?> list,Object value,int index,boolean selected,boolean focus){
            JLabel item=(JLabel)super.getListCellRendererComponent(list,value,index,selected,focus);
            VaultEntry entry=(VaultEntry)value;
            String marker=entry.archived()?"[Archived] ":entry.favorite()?"★ ":"";
            item.setText("<html><b>"+marker+escape(entry.title())+"</b><br><span style='color:#97a5bb'>"+
                    escape(entry.username().isBlank()?entry.category():entry.username())+"</span></html>");
            item.setBorder(new EmptyBorder(8,10,8,8));item.setBackground(selected?PANEL_2:PANEL);item.setForeground(TEXT);return item;
        }
    }
}
