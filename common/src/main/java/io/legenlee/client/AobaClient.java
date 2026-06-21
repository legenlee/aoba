package io.legenlee.client;

public final class AobaClient {
	private AobaClient() {
	}

	// Loader-agnostic client init, called from each platform's client entrypoint.
	public static void init() {
		// This entrypoint is suitable for setting up client-specific logic, such as rendering.
	}
}
