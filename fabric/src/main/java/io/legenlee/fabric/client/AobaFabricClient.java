package io.legenlee.fabric.client;

import io.legenlee.client.AobaClient;

import net.fabricmc.api.ClientModInitializer;

public final class AobaFabricClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		AobaClient.init();
	}
}
