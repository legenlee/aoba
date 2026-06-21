package io.legenlee.ime;

/**
 * JNI surface for the native Wayland {@code text-input-v3} bridge
 * ({@code libaoba_ime.so}). All {@code n*} methods are implemented in
 * {@code aoba_ime.c}; the {@code on*} methods are invoked from native code and
 * fan out to {@link ImeBridge}.
 *
 * <p>Threading: every native method here must be called on the main (render)
 * thread, the same thread that owns GLFW's {@code wl_display}. Native callbacks
 * therefore also arrive on the main thread (during {@link #dispatch()}), which
 * makes it safe to touch Minecraft state directly from {@link ImeBridge}.
 */
public final class WaylandIme {
    private static boolean available;

    private WaylandIme() {
    }

    /**
     * Loads the native library and binds to the compositor's text-input-v3.
     *
     * @param display GLFW's {@code wl_display} pointer (GLFWNativeWayland.glfwGetWaylandDisplay())
     * @param surface GLFW window's {@code wl_surface} pointer (glfwGetWaylandWindow(window))
     * @return {@code true} if the IME bridge is active
     */
    public static synchronized boolean init(long display, long surface) {
        if (available) {
            return true;
        }
        if (!NativeLoader.load()) {
            return false;
        }
        available = nInit(display, surface);
        return available;
    }

    public static boolean isAvailable() {
        return available;
    }

    /** Enable IME for a focused text field; rectangle is the caret in window pixels. */
    public static void enable(int x, int y, int w, int h) {
        if (available) {
            nEnable(x, y, w, h);
        }
    }

    /** Update the caret rectangle (candidate-window placement) while focused. */
    public static void setCursorRect(int x, int y, int w, int h) {
        if (available) {
            nSetCursorRect(x, y, w, h);
        }
    }

    /** Disable IME (no text field focused). */
    public static void disable() {
        if (available) {
            nDisable();
        }
    }

    /** Drain pending compositor events. Call once per frame after glfwPollEvents. */
    public static void dispatch() {
        if (available) {
            nDispatch();
        }
    }

    public static synchronized void destroy() {
        if (available) {
            nDestroy();
            available = false;
        }
    }

    // ------------------------------------------------------------------
    // Invoked from native code (signatures must match aoba_ime.c).
    // ------------------------------------------------------------------

    @SuppressWarnings("unused")
    static void onCommit(String text) {
        ImeBridge.dispatchCommit(text);
    }

    @SuppressWarnings("unused")
    static void onPreedit(String text, int cursorBegin, int cursorEnd) {
        ImeBridge.dispatchPreedit(text, cursorBegin, cursorEnd);
    }

    @SuppressWarnings("unused")
    static void onEnter() {
        ImeBridge.dispatchFocus(true);
    }

    @SuppressWarnings("unused")
    static void onLeave() {
        ImeBridge.dispatchFocus(false);
    }

    private static native boolean nInit(long display, long surface);

    private static native void nEnable(int x, int y, int w, int h);

    private static native void nSetCursorRect(int x, int y, int w, int h);

    private static native void nDisable();

    private static native void nDispatch();

    private static native void nDestroy();
}
