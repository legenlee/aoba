package io.legenlee.neoforge;

import io.legenlee.client.NativeLocale;

import net.neoforged.neoforgespi.earlywindow.GraphicsBootstrapper;

import org.lwjgl.glfw.GLFW;

/**
 * Applies the IME init hints before NeoForge's early loading window initializes GLFW.
 *
 * <p>NeoForge/FML opens a GLFW loading-screen window (and thus calls {@code glfwInit})
 * during mod loading, well before Minecraft's {@code GLX._initGlfw} runs. Because
 * {@code GLFW_X11_ONTHESPOT} is an init hint (only read at {@code glfwInit}), setting it
 * from a {@code GLX._initGlfw} mixin is too late on NeoForge. {@code GraphicsBootstrapper}
 * runs before the early window, so we set it here. (On Fabric there is no early window,
 * so {@code GlxImeMixin} handles it.)
 */
public final class ImeGraphicsBootstrap implements GraphicsBootstrapper {

    @Override
    public String name() {
        return "aoba-ime";
    }

    @Override
    public void bootstrap(String[] arguments) {
        NativeLocale.setCTypeFromEnvironment();
        boolean graphical = System.getenv("DISPLAY") != null || System.getenv("WAYLAND_DISPLAY") != null;
        if (graphical) {
            GLFW.glfwInitHint(GLFW.GLFW_X11_ONTHESPOT, GLFW.GLFW_TRUE);
        }
    }
}
