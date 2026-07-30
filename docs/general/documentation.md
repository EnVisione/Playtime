# Technical Documentation

## Platform

Playtime targets Minecraft 1.20.1 with Forge 47.4.16 and Java 17.

## Architecture

The server owns playtime records, rank evaluation, AFK state, cleanup, backups, and optional integration services. Network packets expose selected state to compatible clients while allowing the server to accept clients without the mod.

LuckPerms integration synchronizes rank groups and display metadata. Open Parties and Claims integration supports claim cleanup. Both API dependencies are compile only and are detected defensively at runtime.

## Verification

Run the complete build from a clean checkout.

```powershell
.\gradlew.bat compileJava build --no-daemon
```

```bash
./gradlew compileJava build --no-daemon
```

The pull request must also pass dependency review, CodeQL, secret scanning, and documentation checks.
