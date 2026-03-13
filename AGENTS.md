# AGENTS.md — Playtime Mod

## ⚠️ PRIORITY RULES — Follow at ALL Times

### Rule 1 — GitHub Workflow
- **Commit every change** to GitHub with a detailed commit message explaining:
  - What was changed and in which file(s)
  - Why the change was made
  - Any side effects or dependencies introduced
- After committing, **push immediately**. Never amend, rebase, or force-push existing commits — only add new ones and push forward.

### Rule 2 — Project Identity
- This is a **Minecraft 1.20.1 Forge mod** (`forge_version=47.4.16`) for an **integrated playtime tracking system**.
- Mod ID: `playtime` | Group: `com.enviouse.playtime`
- Do not change the Minecraft version, Forge version, or mod ID without explicit instruction.

---

## Architecture Overview

| File | Role |
|---|---|
| `src/main/java/com/enviouse/playtime/Playtime.java` | Main mod entry point — `@Mod("playtime")`. Registers blocks, items, creative tabs via `DeferredRegister`, wires event buses. |
| `src/main/java/com/enviouse/playtime/Config.java` | Forge config (`ForgeConfigSpec`). All config values live here; loaded on `ModConfigEvent`. |
| `src/main/resources/META-INF/mods.toml` | Mod metadata — tokens are substituted from `gradle.properties` at build time (e.g. `${mod_version}`). |
| `src/main/resources/playtime.mixins.json` | Mixin config — Mixins are supported via MixinGradle (`org.spongepowered.mixin` plugin). |
| `gradle.properties` | Single source of truth for all version numbers and mod metadata strings. Edit here first. |

## Developer Workflows

```bash
# Build the mod jar
./gradlew build          # output: build/libs/playtime-<version>.jar

# Run the game client in dev
./gradlew runClient

# Run a dedicated server in dev
./gradlew runServer

# Regenerate IDE run configs after changing mappings
./gradlew --refresh-dependencies
```

> Java 17 toolchain is required (`java.toolchain.languageVersion = 17`).  
> Gradle daemon is disabled (`org.gradle.daemon=false`) — every build spawns a fresh JVM.

## Key Conventions

- **Registration**: Always use `DeferredRegister` + `RegistryObject`. Never call `ForgeRegistries` directly for registration.
- **Event buses**: Forge game events → `MinecraftForge.EVENT_BUS`. Mod lifecycle events → `modEventBus` (FMLJavaModLoadingContext). Getting this wrong causes silent failures.
- **Config**: Add new config fields to `Config.java` using `ForgeConfigSpec.Builder`. Expose the baked value as a `public static` field populated in `onLoad`.
- **Mixins**: Mixin classes go in the package declared in `playtime.mixins.json`. Annotate with `@Mixin` and register in that JSON file.
- **Mappings**: Using `official` Mojang mappings (see `gradle.properties`). All Minecraft symbol names match Mojang's published names.

