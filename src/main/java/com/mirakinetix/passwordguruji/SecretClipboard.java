package com.mirakinetix.passwordguruji;

import java.awt.datatransfer.*;
import javax.swing.Timer;

/** One owned clipboard item; accessed on the Swing event thread. */
final class SecretClipboard implements ClipboardOwner {
    private static final DataFlavor TOKEN = new DataFlavor("text/x-password-guruji-owner;class=java.lang.String", "Password Guruji ownership token");
    private String token;
    private final Clipboard clipboard;
    private Transferable owned;
    private Timer timer;

    SecretClipboard(Clipboard clipboard) { this.clipboard = clipboard; }

    void copy(String value, int delayMillis) {
        String nextToken=java.util.UUID.randomUUID().toString();
        StringSelection text=new StringSelection(value);
        Transferable next=new Transferable() {
            public DataFlavor[] getTransferDataFlavors() { DataFlavor[] original=text.getTransferDataFlavors(); DataFlavor[] flavors=java.util.Arrays.copyOf(original,original.length+1); flavors[original.length]=TOKEN; return flavors; }
            public boolean isDataFlavorSupported(DataFlavor flavor) { return TOKEN.equals(flavor)||text.isDataFlavorSupported(flavor); }
            public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException,java.io.IOException {
                return TOKEN.equals(flavor)?nextToken:text.getTransferData(flavor);
            }
        };
        clipboard.setContents(next, this);
        if (timer != null) timer.stop();
        owned = next;token=nextToken;
        timer = new Timer(delayMillis, event -> clear());
        timer.setRepeats(false);
        timer.start();
    }

    void clear() {
        if (timer != null) { timer.stop(); timer = null; }
        Transferable previous = owned;
        owned = null;
        if (previous == null) return;
        try {
            if (token != null && token.equals(clipboard.getData(TOKEN)))
                clipboard.setContents(new StringSelection(""), null);
        } catch (IllegalStateException busy) {
            // Retry without blocking the event thread when another process holds the clipboard.
            owned = previous;
            timer = new Timer(250, event -> clear());
            timer.setRepeats(false);
            timer.start();
        } catch (UnsupportedFlavorException | java.io.IOException unavailable) {
            // The clipboard no longer contains our text.
        }
    }

    @Override public void lostOwnership(Clipboard source, Transferable contents) {
        javax.swing.SwingUtilities.invokeLater(() -> {
            if (owned == contents) {
                owned = null;
                if (timer != null) { timer.stop(); timer = null; }
            }
        });
    }
}
