package io.legenlee.ime;

/**
 * Loader-agnostic seam between the native Wayland IME bridge and the Minecraft
 * client integration. The client layer (Fabric/NeoForge mixins) registers a
 * {@link Handler}; the native bridge pushes events here via {@link WaylandIme}.
 *
 * <p>All callbacks run on the main render thread (see {@link WaylandIme}), so a
 * handler may touch client state directly without extra synchronization.
 */
public final class ImeBridge {

    /** Receives composed text from the active input method. */
    public interface Handler {
        /** Final committed text (e.g. a finished Hangul/Kanji string). */
        void commit(String text);

        /**
         * In-progress composition to display (underlined). Empty text clears it.
         *
         * @param cursorBegin caret start within {@code text}, in bytes (-1 if hidden)
         * @param cursorEnd   caret end within {@code text}, in bytes
         */
        void preedit(String text, int cursorBegin, int cursorEnd);

        /** Compositor focus for the IME entered/left our surface. */
        void focusChanged(boolean focused);
    }

    private static volatile Handler handler;

    private ImeBridge() {
    }

    public static void setHandler(Handler h) {
        handler = h;
    }

    static void dispatchCommit(String text) {
        Handler h = handler;
        if (h != null && text != null && !text.isEmpty()) {
            h.commit(text);
        }
    }

    static void dispatchPreedit(String text, int cursorBegin, int cursorEnd) {
        Handler h = handler;
        if (h != null) {
            h.preedit(text == null ? "" : text, cursorBegin, cursorEnd);
        }
    }

    static void dispatchFocus(boolean focused) {
        Handler h = handler;
        if (h != null) {
            h.focusChanged(focused);
        }
    }
}
