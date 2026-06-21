package io.legenlee.client;

public final class AobaClient {
	private AobaClient() {
	}

	// Loader-agnostic client init, called from each platform's client entrypoint.
	public static void init() {
		// IME is enabled entirely by GlxImeMixin (native C locale for XIM + the X11
		// on-the-spot preedit hint); GLFW then feeds composition into Minecraft's own
		// preedit/charTyped pipeline. Nothing to wire up at runtime here.
	}
}
