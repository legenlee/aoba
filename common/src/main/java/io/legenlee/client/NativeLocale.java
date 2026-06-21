package io.legenlee.client;

import io.legenlee.Aoba;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.Locale;

/**
 * Sets the native C locale ({@code LC_CTYPE}) from the environment via libc
 * {@code setlocale()}, using the Java 25 Foreign Function &amp; Memory API.
 *
 * <p>The JVM never calls {@code setlocale()}, so a Minecraft process runs with the
 * C/POSIX locale regardless of {@code $LANG}. GLFW's X11 backend opens its input
 * method ({@code XOpenIM}) against the current C locale, and XIM only initializes
 * for a real UTF-8 locale — so without this, IME silently stays disabled when
 * Minecraft runs as an X11 / XWayland client.
 */
public final class NativeLocale {
    /** glibc/Linux value of {@code LC_CTYPE}. */
    private static final int LC_CTYPE = 0;

    private static boolean applied;

    private NativeLocale() {
    }

    /** Idempotently set {@code LC_CTYPE} from {@code $LC_*}/{@code $LANG}. Linux only. */
    public static synchronized void setCTypeFromEnvironment() {
        if (applied) {
            return;
        }
        applied = true;
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux")) {
            return;
        }
        try {
            Linker linker = Linker.nativeLinker();
            MethodHandle setlocale = linker.downcallHandle(
                    linker.defaultLookup().find("setlocale").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment fromEnvironment = arena.allocateFrom("");
                MemorySegment result = (MemorySegment) setlocale.invoke(LC_CTYPE, fromEnvironment);
                String locale = (result == null || result.address() == 0L)
                        ? "(setlocale returned null)"
                        : result.reinterpret(256).getString(0);
                Aoba.LOGGER.info("[ime] native LC_CTYPE = {}", locale);
            }
        } catch (Throwable t) {
            Aoba.LOGGER.warn("[ime] failed to set native locale for XIM", t);
        }
    }
}
