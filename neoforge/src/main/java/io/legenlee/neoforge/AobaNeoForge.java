package io.legenlee.neoforge;

import io.legenlee.Aoba;
import io.legenlee.client.AobaClient;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/** Client-only mod: the IME hooks are pointless on a dedicated server. */
@Mod(value = Aoba.MOD_ID, dist = Dist.CLIENT)
public final class AobaNeoForge {
	public AobaNeoForge(IEventBus eventBus) {
		Aoba.init();
		AobaClient.init();
	}
}
