# Playtime Plan

## Current maintenance phase

The current phase restores reproducible clean checkout builds after the repository transfer.

### Scope

- Replace local LuckPerms and Open Parties and Claims JAR paths with canonical Maven coordinates.
- Preserve Minecraft 1.20.1, Forge 47.4.16, Java 17, and Playtime 1.2.1 behavior.
- Keep both integrations optional at runtime.

### Acceptance criteria

- No build dependency relies on a local `libs` directory.
- `gradlew.bat compileJava build --no-daemon` succeeds.
- The mod remains loadable when LuckPerms or Open Parties and Claims is absent.
- Every required deterministic pull request check passes.
