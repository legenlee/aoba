package io.legenlee.neoforge;

import io.legenlee.Aoba;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/** Client-only mod entrypoint. The pre-glfwInit IME setup lives in the companion
 * bootstrap jar (GraphicsBootstrapper); the mixins are loaded from this jar's mods.toml. */
@Mod(value = Aoba.MOD_ID, dist = Dist.CLIENT)
public final class AobaNeoForge {

	public AobaNeoForge(IEventBus eventBus) {
		Aoba.init();
	}
}
