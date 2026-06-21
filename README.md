# Aoba

A **client-side** Minecraft mod that enables **IME input** (Korean / Japanese / Chinese
composition) when the game runs as an **X11 / XWayland** client on Linux, for both
**Fabric** and **NeoForge**.

By default, Minecraft on Linux only accepts direct ASCII input — the input method's
composition (preedit) never reaches the game. Aoba fixes that so you can type CJK text
in chat, sign edits, world/server name fields, command blocks, and so on, with the
composition shown **in-game** (underlined preedit) rather than in a separate IME popup.

## How it works

Minecraft 26.2 / GLFW 3.5 already have a working IME pipeline (`GLFWPreeditCallback` →
`charTyped`/`preeditUpdated` → `IMEPreeditOverlay`). Two things stop it on Linux, and Aoba
addresses both **before `glfwInit`**:

1. **C locale** — the JVM never calls `setlocale`, so the process runs in the `C` locale
   and GLFW's `XOpenIM` (XIM) fails to initialize. Aoba calls `setlocale(LC_CTYPE, "")`
   via the Java 25 Foreign Function & Memory API (no native build).
2. **On-the-spot preedit** — GLFW defaults to the root-window XIM style, so the input
   method draws its own popup. Aoba sets the `GLFW_X11_ONTHESPOT` init hint so GLFW
   delivers composition to Minecraft's preedit callback instead.

On **Fabric**, this is done from a mixin on `GLX._initGlfw` (the first `glfwInit`). On
**NeoForge**, FML opens an early loading-screen window that calls `glfwInit` first, so the
hint is applied earlier via a `GraphicsBootstrapper` SPI.

## Requirements

- Linux with an input method running (e.g. **IBus** or **Fcitx5**) and its **XIM** server
  available to X clients (`XMODIFIERS=@im=ibus`, a UTF-8 `LANG`, etc.).
- Minecraft running on **X11 / XWayland** (the default backend).
- The [Architectury API](https://modrinth.com/mod/architectury-api) mod installed.

The `common` module is bundled into each platform jar at build time, so each output is a
single, self-contained mod jar.

## Building

```bash
# Build both platform jars
./gradlew build

# Or build a single platform
./gradlew :fabric:build
./gradlew :neoforge:build
```

Outputs:

```
fabric/build/libs/aoba-fabric-<version>.jar
neoforge/build/libs/aoba-neoforge-<version>.jar
```

## License

This project is licensed under the **GNU Lesser General Public License v3.0**
(`LGPL-3.0-only`). The full license text is in [`LICENSE`](LICENSE), which is
supplemented by the GNU GPL v3.0 in [`LICENSE.GPL`](LICENSE.GPL).
