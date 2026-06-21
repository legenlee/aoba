package io.legenlee.neoforge;

import io.legenlee.client.NativeLocale;

import net.neoforged.neoforgespi.earlywindow.GraphicsBootstrapper;

import org.lwjgl.glfw.GLFW;

/**
 * Sets up IME before NeoForge's early loading window initializes GLFW, and registers the
 * mod's mixin config.
 *
 * <p>NeoForge/FML opens a GLFW loading-screen window (and thus calls {@code glfwInit})
 * during mod loading, well before Minecraft's {@code GLX._initGlfw} runs. Because
 * {@code GLFW_X11_ONTHESPOT} is an init hint (only read at {@code glfwInit}), it must be
 * set here, before the early window.
 *
 * <p>Providing this early service also has a side effect: NeoForge's early discovery
 * claims the jar before the normal mod loader, so the jar is treated as a service-only
 * file ({@code languages []}) — the {@code @Mod} class is never constructed and the
 * mods.toml {@code [[mixins]]} entry is never read. Since this bootstrapper is the one
 * piece of our code that reliably runs, it also registers the client mixin config.
 */
public final class ImeGraphicsBootstrap implements GraphicsBootstrapper {

    @Override
    public String name() {
        return "aoba-ime";
    }

    @Override
    public void bootstrap(String[] arguments) {
        NativeLocale.prepare();
        boolean graphical = System.getenv("DISPLAY") != null || System.getenv("WAYLAND_DISPLAY") != null;
        if (graphical) {
            GLFW.glfwInitHint(GLFW.GLFW_X11_ONTHESPOT, GLFW.GLFW_TRUE);
        }
    }
}
