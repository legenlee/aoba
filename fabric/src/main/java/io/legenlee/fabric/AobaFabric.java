package io.legenlee.fabric;

import io.legenlee.Aoba;

import net.fabricmc.api.ModInitializer;

public final class AobaFabric implements ModInitializer {
	@Override
	public void onInitialize() {
		// Delegate to the common, loader-agnostic init.
		Aoba.init();
	}
}
