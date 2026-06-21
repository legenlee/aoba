package io.legenlee.client.mixin;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.IMEPreeditOverlay;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.input.KeyEvent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Stops a text field from acting on keys while an IME composition is in progress.
 *
 * <p>With the X11 on-the-spot input style, GLFW does not always filter the keys the input
 * method consumes (e.g. Backspace, which edits the preedit), so the key also reaches the
 * widget's {@code keyPressed} and mutates the committed text — pressing Backspace mid-
 * composition both shrinks the preedit <em>and</em> deletes a committed character.
 *
 * <p>While a composition is active ({@code preeditOverlay != null}) those keys belong to
 * the input method, so we swallow {@code keyPressed}. The preedit is still updated through
 * the separate preedit callback and committed text still arrives via {@code charTyped}.
 *
 * <p>Covers every vanilla text input: {@link EditBox} (chat, anvil, command block, server
 * address, world name, creative search, ...), {@link MultiLineEditBox} (book editing) and
 * {@link AbstractSignEditScreen} (signs) — they all carry the same {@code preeditOverlay}.
 */
// remap = false: Minecraft 26.2 ships unobfuscated, so @Shadow/@Inject names are the
// real names and there is no refmap to remap against.
@Mixin(value = {EditBox.class, MultiLineEditBox.class, AbstractSignEditScreen.class}, remap = false)
public class PreeditKeyGuardMixin {

    @Shadow(remap = false)
    private IMEPreeditOverlay preeditOverlay;

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void aoba$swallowWhileComposing(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (preeditOverlay != null) {
            cir.setReturnValue(true);
        }
    }
}
