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
 * Prepares the native process environment so GLFW's X11 input method (XIM) can connect,
 * using the Java 25 Foreign Function &amp; Memory API (no native build).
 *
 * <p>Two things are set up before {@code glfwInit}:
 * <ul>
 *   <li><b>C locale</b> — the JVM never calls {@code setlocale}, so the process runs in
 *       the {@code C} locale and {@code XOpenIM} fails. We set {@code LC_CTYPE} from the
 *       environment.</li>
 *   <li><b>XMODIFIERS</b> — XIM needs {@code @im=<module>} to know which input method to
 *       talk to. Some launchers (e.g. MultiMC) start the game with an empty
 *       {@code XMODIFIERS}; when that happens we fill it in (detecting fcitx vs ibus, or
 *       {@code -Daoba.imModule}) so {@code XSetLocaleModifiers("")} picks it up.</li>
 * </ul>
 */
public final class NativeLocale {
    /** glibc/Linux value of {@code LC_CTYPE}. */
    private static final int LC_CTYPE = 0;

    private static boolean prepared;

    private NativeLocale() {
    }

    /** Idempotently prepare the locale and XMODIFIERS for XIM. Linux only. */
    public static synchronized void prepare() {
        if (prepared) {
            return;
        }
        prepared = true;
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux")) {
            return;
        }
        try {
            Linker linker = Linker.nativeLinker();
            setCTypeFromEnvironment(linker);
            ensureInputMethodModifier(linker);
        } catch (Throwable t) {
            Aoba.LOGGER.warn("[ime] failed to prepare native locale/XMODIFIERS for XIM", t);
        }
    }

    private static void setCTypeFromEnvironment(Linker linker) throws Throwable {
        MethodHandle setlocale = linker.downcallHandle(
                linker.defaultLookup().find("setlocale").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment result = (MemorySegment) setlocale.invoke(LC_CTYPE, arena.allocateFrom(""));
            String locale = (result == null || result.address() == 0L)
                    ? "(setlocale returned null)"
                    : result.reinterpret(256).getString(0);
            Aoba.LOGGER.info("[ime] native LC_CTYPE = {}", locale);
        }
    }

    /** Input-method daemon process markers, in priority order, mapped to their XMODIFIERS module. */
    private static final String[][] KNOWN_INPUT_METHODS = {
            {"fcitx", "@im=fcitx"},
            {"ibus", "@im=ibus"},
            {"kime", "@im=kime"},
            {"nimf", "@im=nimf"},
            {"uim", "@im=uim"},
            {"scim", "@im=scim"},
    };

    private static void ensureInputMethodModifier(Linker linker) throws Throwable {
        String current = System.getenv("XMODIFIERS");
        if (current != null && !current.isEmpty()) {
            return; // the session already provides it (the normal case)
        }
        // Some launchers (e.g. MultiMC) start the game with XMODIFIERS empty. We never
        // guess blindly: only set it to an input method that is actually running.
        String module = inputMethodModule();
        if (module == null) {
            Aoba.LOGGER.info("[ime] XMODIFIERS is empty and no known input-method daemon was "
                    + "detected; set -Daoba.imModule=@im=<module> if your IME does not work");
            return;
        }
        MethodHandle setenv = linker.downcallHandle(
                linker.defaultLookup().find("setenv").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        try (Arena arena = Arena.ofConfined()) {
            setenv.invoke(arena.allocateFrom("XMODIFIERS"), arena.allocateFrom(module), 1);
            Aoba.LOGGER.info("[ime] XMODIFIERS was empty; set to '{}'", module);
        }
    }

    /**
     * Resolves the XMODIFIERS module: an explicit {@code -Daoba.imModule} override, otherwise
     * the module of the input-method daemon actually running on this machine, or {@code null}
     * if none of the recognized daemons are running.
     */
    private static String inputMethodModule() {
        String override = System.getProperty("aoba.imModule");
        if (override != null && !override.isEmpty()) {
            return override.startsWith("@im=") ? override : "@im=" + override;
        }
        try {
            var commands = ProcessHandle.allProcesses()
                    .map(process -> process.info().command().orElse(""))
                    .toList();
            for (String[] im : KNOWN_INPUT_METHODS) {
                for (String command : commands) {
                    if (command.contains(im[0])) {
                        return im[1];
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // process enumeration unavailable; treat as "unknown"
        }
        return null;
    }
}
