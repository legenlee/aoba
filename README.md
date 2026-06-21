# Aoba

A multiplatform Minecraft mod targeting both **Fabric** and **NeoForge**, built with
[Architectury](https://docs.architectury.dev/) so the gameplay logic is written once
in a shared `common` module.

## Project structure

```
aoba/
├── common/      Loader-agnostic logic (Aoba.init / AobaClient.init, mixins, resources)
├── fabric/      Fabric entrypoints + fabric.mod.json
└── neoforge/    NeoForge entrypoint + META-INF/neoforge.mods.toml
```

The `common` module is bundled into each platform jar at build time, so each output is a
single, self-contained mod jar.

## Versions

| Component | Version |
| --- | --- |
| Minecraft | 26.2 |
| Java | 25 |
| Architectury Loom | 1.17-SNAPSHOT (`loom-no-remap`) |
| Architectury Plugin | 3.5.169 |
| Architectury API | 21.0.2 |
| Fabric Loader | 0.19.3 |
| Fabric API | 0.152.2+26.2 |
| NeoForge | 26.2.0.6-beta |

Versions are centralized in [`gradle.properties`](gradle.properties).

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

Both jars require the [Architectury API](https://modrinth.com/mod/architectury-api) mod to
be installed at runtime.

## Notes

Minecraft 26.2 ships **unobfuscated** (official class names are baked into the game jar,
and there is no separate Mojang mappings file or Fabric intermediary). Because of this the
build uses the `dev.architectury.loom-no-remap` plugin: there is no remap step, no
`mappings` dependency, and the `jar` task produces the final mod jar directly.

## Setup

For Fabric IDE setup, see the
[Fabric Documentation](https://docs.fabricmc.net/develop/getting-started/creating-a-project#setting-up).
For NeoForge, see the [NeoForge Documentation](https://docs.neoforged.net/).

## License

This project is licensed under the **GNU Lesser General Public License v3.0**
(`LGPL-3.0-only`). The full license text is in [`LICENSE`](LICENSE), which is
supplemented by the GNU GPL v3.0 in [`LICENSE.GPL`](LICENSE.GPL).
