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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class PasswordGurujiApp {
    private static final Color BG = new Color(13, 18, 29);
    private static final Color PANEL = new Color(22, 29, 43);
    private static final Color PANEL_2 = new Color(29, 38, 55);
    private static final Color BORDER = new Color(49, 61, 82);
    private static final Color TEXT = new Color(235, 240, 249);
    private static final Color MUTED = new Color(151, 165, 187);
    private static final Color ACCENT = new Color(97, 186, 167);
    private static final Color DANGER = new Color(238, 126, 126);
    private static final int CLIPBOARD_CLEAR_MS = 30_000;

    private final JFrame frame = new JFrame("Password Guruji");
    private final VaultStore store = new VaultStore(VaultStore.defaultPath());
    private final List<VaultEntry> entries = new ArrayList<>();
    private final DefaultListModel<VaultEntry> listModel = new DefaultListModel<>();
    private final JList<VaultEntry> entryList = new JList<>(listModel);
    private final JTextField searchField = new JTextField();
    private final JPanel detailPanel = new JPanel(new BorderLayout());
    private final JPanel pagePanel = new JPanel(new CardLayout());
    private char[] masterPassword;
    private boolean locked = true;
    private long lastActivity = System.currentTimeMillis();
    private final Timer idleTimer;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new PasswordGurujiApp().launch());
    }

    private PasswordGurujiApp() {
        idleTimer = new Timer(1000, e -> {
            if (!locked && System.currentTimeMillis() - lastActivity > 5 * 60_000L) lockVault();
        });
        idleTimer.start();
        Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
            if (event instanceof InputEvent && !locked) lastActivity = System.currentTimeMillis();
        }, AWTEvent.KEY_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);
    }

    private void launch() {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.setMinimumSize(new Dimension(900, 620));
        frame.setSize(1180, 760);
        frame.setLocationRelativeTo(null);
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                if (!saveVault()) { lastActivity = System.currentTimeMillis(); return; }
                lockMemory();
                idleTimer.stop();
                frame.dispose();
            }
        });
        pagePanel.add(buildUnlockPage(), "unlock");
        pagePanel.add(buildVaultPage(), "vault");
        frame.setContentPane(pagePanel);
        frame.setVisible(true);
        showUnlock();
    }

    private JPanel buildUnlockPage() {
        JPanel wrapper = new JPanel(new GridBagLayout());
        wrapper.setBackground(BG);
        JPanel card = new JPanel();
        card.setBackground(PANEL);
        card.setBorder(new EmptyBorder(32, 38, 32, 38));
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.add(label("MIRAKINETIX", 12, ACCENT, Font.BOLD));
        card.add(Box.createVerticalStrut(10));
        card.add(label("Password Guruji", 30, TEXT, Font.BOLD));
        card.add(Box.createVerticalStrut(7));
        card.add(label("Your private vault stays on this device.", 14, MUTED, Font.PLAIN));

        JTextArea intro = new JTextArea("Offline by design · No account · No network connection\nVault file: " + store.path());
        intro.setEditable(false); intro.setFocusable(false); intro.setLineWrap(true); intro.setWrapStyleWord(true);
        intro.setForeground(MUTED); intro.setBackground(PANEL); intro.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        intro.setBorder(new EmptyBorder(16, 0, 16, 0)); card.add(intro);

        boolean creating = !store.exists();
        JPasswordField password = new JPasswordField(28);
        styleField(password);
        JPasswordField confirm = new JPasswordField(28);
        styleField(confirm);
        card.add(label("Master password", 12, MUTED, Font.BOLD));
        card.add(Box.createVerticalStrut(7)); card.add(password);
        if (creating) {
            card.add(Box.createVerticalStrut(12));
            card.add(label("Confirm master password", 12, MUTED, Font.BOLD));
            card.add(Box.createVerticalStrut(7)); card.add(confirm);
        }
        card.add(Box.createVerticalStrut(16));
        JButton primary = primaryButton(creating ? "Create my vault" : "Unlock vault");
        card.add(primary);
        card.add(Box.createVerticalStrut(12));
        JLabel status = label("", 12, MUTED, Font.PLAIN);
        card.add(status);
        card.add(Box.createVerticalStrut(8));
        card.add(label("Your master password cannot be recovered. Keep an encrypted backup.", 11, MUTED, Font.PLAIN));

        primary.addActionListener(e -> {
            char[] pass = password.getPassword();
            char[] confirmPass = confirm.getPassword();
            password.setText(""); confirm.setText("");
            if (creating && pass.length < 15) {
                status.setForeground(DANGER); status.setText("Use at least 15 characters for your master password.");
                wipe(pass); wipe(confirmPass); return;
            }
            if (creating && !java.util.Arrays.equals(pass, confirmPass)) {
                status.setForeground(DANGER); status.setText("The two master passwords do not match.");
                wipe(pass); wipe(confirmPass); return;
            }
            try {
                if (creating) {
                    store.create(pass);
                    masterPassword = pass;
                    entries.clear();
                } else {
                    List<VaultEntry> loaded = store.load(pass);
                    masterPassword = pass;
                    entries.clear(); entries.addAll(loaded);
                }
                wipe(confirmPass);
                enterVault();
            } catch (AEADBadTagException wrongPassword) {
                wipe(pass); wipe(confirmPass);
                status.setForeground(DANGER); status.setText("That master password did not unlock this vault.");
            } catch (IOException | GeneralSecurityException ex) {
                wipe(pass); wipe(confirmPass);
                showError("Could not open the vault", ex);
            }
        });
        password.addActionListener(e -> primary.doClick());
        confirm.addActionListener(e -> primary.doClick());
        wrapper.add(card);
        return wrapper;
    }

    private JPanel buildVaultPage() {
        JPanel page = new JPanel(new BorderLayout());
        page.setBackground(BG);
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(BG);
        header.setBorder(new EmptyBorder(18, 22, 14, 22));
        JPanel brand = new JPanel();
        brand.setOpaque(false); brand.setLayout(new BoxLayout(brand, BoxLayout.Y_AXIS));
        brand.add(label("MIRAKINETIX", 10, ACCENT, Font.BOLD));
        brand.add(Box.createVerticalStrut(4));
        brand.add(label("Password Guruji", 22, TEXT, Font.BOLD));
        header.add(brand, BorderLayout.WEST);

        JPanel headerActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        headerActions.setOpaque(false);
        JButton generate = subtleButton("Generate");
        JButton backup = subtleButton("Encrypted backup");
        JButton lock = subtleButton("Lock");
        headerActions.add(generate); headerActions.add(backup); headerActions.add(lock);
        header.add(headerActions, BorderLayout.EAST);

        JPanel body = new JPanel(new BorderLayout(14, 0));
        body.setBorder(new EmptyBorder(0, 18, 18, 18));
        body.setBackground(BG);
        JPanel sidebar = new JPanel(new BorderLayout(0, 12));
        sidebar.setPreferredSize(new Dimension(320, 0));
        sidebar.setBackground(PANEL);
        sidebar.setBorder(new EmptyBorder(16, 14, 14, 14));
        JButton add = primaryButton("+  New entry");
        JLabel heading = label("YOUR RECORDS", 11, MUTED, Font.BOLD);
        styleField(searchField);
        JPanel sidebarTop = new JPanel(new BorderLayout(0, 12));
        sidebarTop.setOpaque(false);
        sidebarTop.add(heading, BorderLayout.NORTH);
        sidebarTop.add(add, BorderLayout.CENTER);
        sidebarTop.add(searchField, BorderLayout.SOUTH);
        sidebar.add(sidebarTop, BorderLayout.NORTH);

        entryList.setBackground(PANEL);
        entryList.setForeground(TEXT);
        entryList.setSelectionBackground(PANEL_2);
        entryList.setSelectionForeground(TEXT);
        entryList.setFixedCellHeight(58);
        entryList.setCellRenderer(new EntryRenderer());
        JScrollPane listScroll = new JScrollPane(entryList);
        listScroll.setBorder(BorderFactory.createEmptyBorder());
        listScroll.getViewport().setBackground(PANEL);
        sidebar.add(listScroll, BorderLayout.CENTER);

        detailPanel.setBackground(PANEL);
        detailPanel.setBorder(new EmptyBorder(22, 24, 22, 24));
        body.add(sidebar, BorderLayout.WEST);
        body.add(detailPanel, BorderLayout.CENTER);
        page.add(header, BorderLayout.NORTH);
        page.add(body, BorderLayout.CENTER);

        add.addActionListener(e -> editEntry(null));
        generate.addActionListener(e -> showGenerator());
        backup.addActionListener(e -> backupVault());
        lock.addActionListener(e -> lockVault());
        searchField.putClientProperty("JTextField.placeholderText", "Search your vault");
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { refreshList(); }
            public void removeUpdate(DocumentEvent e) { refreshList(); }
            public void changedUpdate(DocumentEvent e) { refreshList(); }
        });
        entryList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) showDetails(entryList.getSelectedValue());
        });
        entryList.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && entryList.getSelectedValue() != null) editEntry(entryList.getSelectedValue());
            }
        });
        showDetails(null);
        return page;
    }

    private void enterVault() {
        locked = false;
        lastActivity = System.currentTimeMillis();
        refreshList();
        showDetails(null);
        ((CardLayout) pagePanel.getLayout()).show(pagePanel, "vault");
        frame.setTitle("Password Guruji · Offline vault");
    }

    private void showUnlock() {
        locked = true;
        ((CardLayout) pagePanel.getLayout()).show(pagePanel, "unlock");
        frame.setTitle("Password Guruji · Locked");
    }

    private void lockVault() {
        if (!saveVault()) { lastActivity = System.currentTimeMillis(); return; }
        lockMemory();
        showUnlock();
    }

    private void lockMemory() {
        wipe(masterPassword);
        masterPassword = null;
        entries.clear();
        listModel.clear();
        locked = true;
    }

    private void refreshList() {
        String query = searchField.getText().trim().toLowerCase(Locale.ROOT);
        String selectedId = entryList.getSelectedValue() == null ? null : entryList.getSelectedValue().id();
        listModel.clear();
        entries.stream()
                .filter(e -> query.isEmpty() || (e.title() + " " + e.username() + " " + e.url() + " " + e.category() + " " + e.notes())
                        .toLowerCase(Locale.ROOT).contains(query))
                .sorted(Comparator.comparing(VaultEntry::title, String.CASE_INSENSITIVE_ORDER))
                .forEach(listModel::addElement);
        if (selectedId != null) {
            for (int i = 0; i < listModel.size(); i++) {
                if (listModel.get(i).id().equals(selectedId)) { entryList.setSelectedIndex(i); return; }
            }
        }
        if (!listModel.isEmpty() && entryList.getSelectedIndex() < 0) entryList.setSelectedIndex(0);
    }

    private void showDetails(VaultEntry entry) {
        detailPanel.removeAll();
        if (entry == null) {
            JPanel welcome = new JPanel();
            welcome.setOpaque(false); welcome.setLayout(new BoxLayout(welcome, BoxLayout.Y_AXIS));
            welcome.add(Box.createVerticalGlue());
            JLabel title = label(entries.isEmpty() ? "Your vault is ready" : "Select an entry", 24, TEXT, Font.BOLD);
            title.setAlignmentX(Component.LEFT_ALIGNMENT);
            welcome.add(title); welcome.add(Box.createVerticalStrut(8));
            JLabel caption = label(entries.isEmpty() ? "Add a login or secure note to get started."
                    : "Choose a record from the list to view its details.", 14, MUTED, Font.PLAIN);
            caption.setAlignmentX(Component.LEFT_ALIGNMENT);
            welcome.add(caption); welcome.add(Box.createVerticalGlue());
            detailPanel.add(welcome, BorderLayout.CENTER);
        } else {
            JPanel top = new JPanel(new BorderLayout()); top.setOpaque(false);
            JPanel titleBox = new JPanel(); titleBox.setOpaque(false); titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));
            titleBox.add(label(entry.title(), 25, TEXT, Font.BOLD));
            titleBox.add(Box.createVerticalStrut(5));
            titleBox.add(label(entry.category() + "  ·  Updated " +
                    DateFormat.getDateInstance(DateFormat.MEDIUM).format(entry.updatedAt()), 12, MUTED, Font.PLAIN));
            top.add(titleBox, BorderLayout.WEST);
            JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0)); actions.setOpaque(false);
            JButton edit = subtleButton("Edit"), delete = subtleButton("Delete");
            actions.add(edit); actions.add(delete); top.add(actions, BorderLayout.EAST);
            detailPanel.add(top, BorderLayout.NORTH);

            JPanel fields = new JPanel(new GridBagLayout()); fields.setOpaque(false);
            GridBagConstraints c = new GridBagConstraints();
            c.gridx = 0; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.anchor = GridBagConstraints.WEST;
            addDetailField(fields, c, 0, "USERNAME", entry.username(), false);
            addDetailField(fields, c, 1, "PASSWORD", entry.password(), true);
            addDetailField(fields, c, 2, "WEBSITE", entry.url(), false);
            addDetailField(fields, c, 3, "NOTES", entry.notes(), false);
            JScrollPane scroll = new JScrollPane(fields); scroll.setBorder(BorderFactory.createEmptyBorder());
            scroll.getViewport().setOpaque(false); scroll.setOpaque(false);
            detailPanel.add(scroll, BorderLayout.CENTER);
            edit.addActionListener(e -> editEntry(entry));
            delete.addActionListener(e -> deleteEntry(entry));
        }
        detailPanel.revalidate(); detailPanel.repaint();
    }

    private void addDetailField(JPanel panel, GridBagConstraints c, int row, String name, String value, boolean secret) {
        JPanel box = new JPanel(new BorderLayout(8, 6));
        box.setOpaque(false); box.setBorder(new EmptyBorder(16, 0, 5, 0));
        box.add(label(name, 10, MUTED, Font.BOLD), BorderLayout.NORTH);
        JTextField shown = new JTextField(secret && !safe(value).isEmpty() ? "••••••••••••" : safe(value));
        shown.setEditable(false); styleField(shown);
        JPanel rowPanel = new JPanel(new BorderLayout(8, 0)); rowPanel.setOpaque(false);
        rowPanel.add(shown, BorderLayout.CENTER);
        if (secret) {
            JToggleButton reveal = subtleToggle("Show");
            rowPanel.add(reveal, BorderLayout.WEST);
            reveal.addActionListener(e -> {
                shown.setText(reveal.isSelected() ? safe(value) : "••••••••••••");
                reveal.setText(reveal.isSelected() ? "Hide" : "Show");
            });
        }
        if (secret || "USERNAME".equals(name)) {
            JButton copy = subtleButton("Copy");
            copy.addActionListener(e -> copyToClipboard(safe(value)));
            rowPanel.add(copy, BorderLayout.EAST);
        }
        box.add(rowPanel, BorderLayout.CENTER);
        c.gridy = row; c.insets = new Insets(0, 0, 3, 0); panel.add(box, c);
    }

    private void editEntry(VaultEntry existing) {
        JTextField title = new JTextField(existing == null ? "" : existing.title(), 28);
        JComboBox<String> category = new JComboBox<>(new String[]{"Login", "Secure note", "Identity", "Payment"});
        if (existing != null) category.setSelectedItem(existing.category());
        JTextField username = new JTextField(existing == null ? "" : existing.username(), 28);
        JPasswordField password = new JPasswordField(existing == null ? "" : existing.password(), 28);
        JTextField url = new JTextField(existing == null ? "" : existing.url(), 28);
        JTextArea notes = new JTextArea(existing == null ? "" : existing.notes(), 5, 28);
        notes.setLineWrap(true); notes.setWrapStyleWord(true); styleArea(notes);
        JPanel form = new JPanel(new GridBagLayout()); form.setBackground(PANEL);
        GridBagConstraints c = new GridBagConstraints(); c.insets = new Insets(6, 6, 6, 6); c.anchor = GridBagConstraints.WEST;
        addFormRow(form, c, 0, "Name", title);
        addFormRow(form, c, 1, "Record type", category);
        addFormRow(form, c, 2, "Username / value", username);
        addFormRow(form, c, 3, "Password / secret", password);
        addFormRow(form, c, 4, "Website", url);
        addFormRow(form, c, 5, "Notes", new JScrollPane(notes));
        int answer = JOptionPane.showConfirmDialog(frame, form, existing == null ? "New vault entry" : "Edit vault entry",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (answer == JOptionPane.OK_OPTION) {
            String name = title.getText().trim();
            if (name.isEmpty()) JOptionPane.showMessageDialog(frame, "Enter a name for this entry.", "Name required", JOptionPane.WARNING_MESSAGE);
            else {
                String secret = new String(password.getPassword());
                if (existing == null) entries.add(VaultEntry.create(name, username.getText(), secret, url.getText(), notes.getText(), (String) category.getSelectedItem()));
                else {
                    int index = findById(existing.id());
                    if (index >= 0) entries.set(index, existing.updated(name, username.getText(), secret, url.getText(), notes.getText(), (String) category.getSelectedItem()));
                }
                if (saveVault()) { refreshList(); selectById(existing == null ? null : existing.id(), name); }
            }
        }
        password.setText("");
    }

    private void addFormRow(JPanel panel, GridBagConstraints c, int row, String name, Component field) {
        c.gridy = row; c.gridx = 0; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        panel.add(label(name, 12, MUTED, Font.PLAIN), c);
        c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL; panel.add(field, c);
    }

    private void deleteEntry(VaultEntry entry) {
        int answer = JOptionPane.showConfirmDialog(frame, "Delete “" + entry.title() + "” from this vault?",
                "Delete entry", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer == JOptionPane.YES_OPTION) {
            entries.removeIf(e -> e.id().equals(entry.id()));
            if (saveVault()) { refreshList(); if (entries.isEmpty()) showDetails(null); }
        }
    }

    private void showGenerator() {
        JSpinner length = new JSpinner(new SpinnerNumberModel(20, 8, 128, 1));
        JCheckBox upper = new JCheckBox("Uppercase", true), lower = new JCheckBox("Lowercase", true);
        JCheckBox digits = new JCheckBox("Numbers", true), symbols = new JCheckBox("Symbols", true);
        JCheckBox usePhrase = new JCheckBox("Use passphrase");
        JSpinner words = new JSpinner(new SpinnerNumberModel(4, 3, 12, 1));
        JTextField separator = new JTextField("-", 5), output = new JTextField(30);
        output.setEditable(false); styleField(output);
        JPanel options = new JPanel(new GridLayout(0, 2, 8, 5)); options.setBackground(PANEL);
        options.add(label("Password length", 12, MUTED, Font.PLAIN)); options.add(length);
        options.add(upper); options.add(lower); options.add(digits); options.add(symbols);
        options.add(usePhrase); options.add(new JLabel());
        options.add(label("Passphrase words", 12, MUTED, Font.PLAIN)); options.add(words);
        options.add(label("Word separator", 12, MUTED, Font.PLAIN)); options.add(separator);
        JButton generate = primaryButton("Generate"), copy = subtleButton("Copy"), save = subtleButton("Save as entry");
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT)); actions.setOpaque(false);
        actions.add(generate); actions.add(copy); actions.add(save);
        JPanel content = new JPanel(new BorderLayout(0, 12)); content.setBackground(PANEL); content.setBorder(new EmptyBorder(8, 8, 8, 8));
        content.add(options, BorderLayout.NORTH); content.add(output, BorderLayout.CENTER); content.add(actions, BorderLayout.SOUTH);
        Runnable create = () -> {
            try {
                output.setText(usePhrase.isSelected() ? PasswordGenerator.passphrase((Integer) words.getValue(), separator.getText())
                        : PasswordGenerator.generate((Integer) length.getValue(), upper.isSelected(), lower.isSelected(), digits.isSelected(), symbols.isSelected()));
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(frame, ex.getMessage(), "Generator options", JOptionPane.WARNING_MESSAGE);
            }
        };
        generate.addActionListener(e -> create.run());
        copy.addActionListener(e -> copyToClipboard(output.getText()));
        save.addActionListener(e -> {
            String value = output.getText();
            if (value.isEmpty()) return;
            String name = JOptionPane.showInputDialog(frame, "Name this entry:", "New password");
            if (name != null && !name.isBlank()) {
                entries.add(VaultEntry.create(name.trim(), "", value, "", "", "Login"));
                if (saveVault()) { refreshList(); selectById(null, name.trim()); }
            }
        });
        usePhrase.addActionListener(e -> {
            boolean phrase = usePhrase.isSelected();
            length.setEnabled(!phrase); upper.setEnabled(!phrase); lower.setEnabled(!phrase);
            digits.setEnabled(!phrase); symbols.setEnabled(!phrase); words.setEnabled(phrase); separator.setEnabled(phrase);
        });
        words.setEnabled(false); separator.setEnabled(false); create.run();
        JOptionPane.showMessageDialog(frame, content, "Offline password generator", JOptionPane.PLAIN_MESSAGE);
    }

    private void backupVault() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save encrypted vault backup");
        chooser.setSelectedFile(new File("Password-Guruji-Backup.pgv"));
        if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) return;
        Path destination = chooser.getSelectedFile().toPath().toAbsolutePath();
        if (destination.equals(store.path().toAbsolutePath())) {
            JOptionPane.showMessageDialog(frame, "Choose a different file for the backup.", "Choose another file", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!saveVault()) { lastActivity = System.currentTimeMillis(); return; }
        try {
            Files.copy(store.path(), destination, StandardCopyOption.REPLACE_EXISTING);
            JOptionPane.showMessageDialog(frame, "Encrypted backup saved. Keep it somewhere separate from this computer.");
        } catch (IOException ex) { showError("Could not create the backup", ex); }
    }

    private void copyToClipboard(String value) {
        if (value == null || value.isEmpty()) return;
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(value), null);
        Timer clear = new Timer(CLIPBOARD_CLEAR_MS, e -> {
            try {
                String current = Toolkit.getDefaultToolkit().getSystemClipboard()
                        .getData(java.awt.datatransfer.DataFlavor.stringFlavor).toString();
                if (value.equals(current)) Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(""), null);
            } catch (Exception ignored) {}
        });
        clear.setRepeats(false); clear.start();
    }

    private boolean saveVault() {
        if (masterPassword == null || locked) return true;
        try { store.save(masterPassword, entries); return true; }
        catch (IOException | GeneralSecurityException ex) { showError("Could not save the vault", ex); return false; }
    }

    private void selectById(String id, String title) {
        for (int i=0; i<listModel.size(); i++) {
            VaultEntry entry = listModel.get(i);
            if ((id != null && entry.id().equals(id)) || (id == null && entry.title().equals(title))) {
                entryList.setSelectedIndex(i); return;
            }
        }
    }

    private int findById(String id) {
        for (int i=0; i<entries.size(); i++) if (entries.get(i).id().equals(id)) return i;
        return -1;
    }

    private void showError(String title, Exception error) {
        JOptionPane.showMessageDialog(frame, title + ".\n" + safe(error.getMessage()), "Password Guruji", JOptionPane.ERROR_MESSAGE);
    }

    private static String safe(String value) { return value == null ? "" : value; }
    private static void wipe(char[] value) { if (value != null) java.util.Arrays.fill(value, '\0'); }

    private JLabel label(String text, int size, Color color, int style) {
        JLabel label = new JLabel(text);
        label.setFont(new Font(Font.SANS_SERIF, style, size)); label.setForeground(color); return label;
    }

    private JButton primaryButton(String text) { JButton button = new JButton(text); styleButton(button, true); return button; }
    private JButton subtleButton(String text) { JButton button = new JButton(text); styleButton(button, false); return button; }
    private JToggleButton subtleToggle(String text) { JToggleButton button = new JToggleButton(text); styleButton(button, false); return button; }

    private void styleButton(AbstractButton button, boolean primary) {
        button.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        button.setForeground(primary ? new Color(9, 25, 28) : TEXT);
        button.setBackground(primary ? ACCENT : PANEL_2);
        button.setFocusPainted(false); button.setOpaque(true);
        button.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(primary ? ACCENT : BORDER, 1, true),
                new EmptyBorder(8, 12, 8, 12)));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private void styleField(JComponent component) {
        component.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        component.setForeground(TEXT); component.setBackground(PANEL_2);
        component.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER), new EmptyBorder(8, 9, 8, 9)));
        if (component instanceof JTextField field) field.setCaretColor(TEXT);
    }

    private void styleArea(JTextArea area) {
        area.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        area.setForeground(TEXT); area.setBackground(PANEL_2); area.setCaretColor(TEXT);
        area.setBorder(new EmptyBorder(8, 9, 8, 9));
    }

    private final class EntryRenderer extends DefaultListCellRenderer {
        @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
            JLabel item = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focus);
            VaultEntry entry = (VaultEntry) value;
            item.setText("<html><b>" + escape(entry.title()) + "</b><br><span style='color:#97a5bb'>" +
                    escape(entry.username().isBlank() ? entry.category() : entry.username()) + "</span></html>");
            item.setBorder(new EmptyBorder(8, 10, 8, 8));
            item.setBackground(selected ? PANEL_2 : PANEL); item.setForeground(TEXT);
            return item;
        }
    }

    private String escape(String value) {
        return safe(value).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
