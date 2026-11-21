package com.mirakinetix.passwordguruji;

import java.awt.datatransfer.*;
import javax.swing.Timer;

/** One owned clipboard item; accessed on the Swing event thread. */
final class SecretClipboard implements ClipboardOwner {
    private final Clipboard clipboard;
    private Transferable owned;
    private Timer timer;

    SecretClipboard(Clipboard clipboard) { this.clipboard = clipboard; }

    void copy(String value, int delayMillis) {
        Transferable next = new StringSelection(value);
        clipboard.setContents(next, this);
        if (timer != null) timer.stop();
        owned = next;
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
            if (previous.getTransferData(DataFlavor.stringFlavor).equals(clipboard.getData(DataFlavor.stringFlavor)))
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
