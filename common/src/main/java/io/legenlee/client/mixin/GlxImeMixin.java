package io.legenlee.client.mixin;

import io.legenlee.client.NativeLocale;

import com.mojang.blaze3d.platform.GLX;

import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.LongSupplier;

/**
 * Enables IME for Minecraft running as an X11 / XWayland client.
 *
 * <p>Before {@code glfwInit()} we set the native C locale from the environment
 * ({@link NativeLocale}) so GLFW's XIM input method can initialize, and request the
 * X11 "on-the-spot" preedit style so GLFW delivers composition to Minecraft's preedit
 * callback (drawn in-game by {@code IMEPreeditOverlay}) instead of the input method
 * drawing its own popup. GLFW 3.5 then feeds XIM preedit/commit into Minecraft's
 * existing IME pipeline.
 *
 * <p>On Fabric this {@code _initGlfw} is the first {@code glfwInit}, so the init hint
 * applies. On NeoForge, FML's early loading window calls {@code glfwInit} first, so
 * {@code ImeGraphicsBootstrap} (a {@code GraphicsBootstrapper}) sets the hint earlier.
 */
@Mixin(value = GLX.class, remap = false)
public class GlxImeMixin {

    @Inject(method = "_initGlfw", at = @At("HEAD"))
    private static void aoba$setupIme(CallbackInfoReturnable<LongSupplier> cir) {
        NativeLocale.prepare();
        GLFW.glfwInitHint(GLFW.GLFW_X11_ONTHESPOT, GLFW.GLFW_TRUE);
    }
}
