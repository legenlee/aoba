package io.legenlee.neoforge;

import io.legenlee.Aoba;
import io.legenlee.client.AobaClient;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

@Mod(Aoba.MOD_ID)
public final class AobaNeoForge {
	public AobaNeoForge(IEventBus eventBus) {
		// Delegate to the common, loader-agnostic init.
		Aoba.init();

		if (FMLEnvironment.getDist() == Dist.CLIENT) {
			AobaClient.init();
		}
	}
}
