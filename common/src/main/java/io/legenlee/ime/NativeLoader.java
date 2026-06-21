package io.legenlee.ime;

import io.legenlee.Aoba;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/**
 * Extracts the bundled {@code libaoba_ime.so} from the mod jar to a temp file
 * and loads it. The native bridge is Linux/Wayland-only, so this is a no-op on
 * any other OS.
 */
final class NativeLoader {
    private static final String LIB_NAME = "aoba_ime";

    private static Boolean loaded;

    private NativeLoader() {
    }

    static synchronized boolean load() {
        if (loaded != null) {
            return loaded;
        }
        loaded = doLoad();
        return loaded;
    }

    private static boolean doLoad() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (!os.contains("linux")) {
            Aoba.LOGGER.info("[ime] not Linux, Wayland IME bridge disabled");
            return false;
        }

        String arch = normalizeArch(System.getProperty("os.arch", ""));
        if (arch == null) {
            Aoba.LOGGER.warn("[ime] unsupported arch '{}', IME bridge disabled",
                    System.getProperty("os.arch"));
            return false;
        }

        String resource = "/natives/linux/" + arch + "/lib" + LIB_NAME + ".so";
        try (InputStream in = NativeLoader.class.getResourceAsStream(resource)) {
            if (in == null) {
                Aoba.LOGGER.warn("[ime] native library {} not found in jar", resource);
                return false;
            }
            Path tmp = Files.createTempFile("lib" + LIB_NAME, ".so");
            tmp.toFile().deleteOnExit();
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            System.load(tmp.toAbsolutePath().toString());
            Aoba.LOGGER.info("[ime] loaded {}", resource);
            return true;
        } catch (IOException | UnsatisfiedLinkError e) {
            Aoba.LOGGER.warn("[ime] failed to load native IME bridge", e);
            return false;
        }
    }

    private static String normalizeArch(String arch) {
        return switch (arch.toLowerCase(Locale.ROOT)) {
            case "amd64", "x86_64" -> "x86_64";
            case "aarch64", "arm64" -> "aarch64";
            default -> null;
        };
    }
}
