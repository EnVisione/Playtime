# Playtime Mod — Documentation

> **Minecraft 1.20.1 · Forge 47.4.16 · Mod ID: `playtime`**
>
> Server-side playtime tracking with rank progression, LuckPerms integration, OpenPAC claim cleanup, and rotating backups.

---

## Table of Contents

1. [Overview](#overview)
2. [Installation](#installation)
3. [Getting Started](#getting-started)
4. [Commands](#commands)
   - [Player Commands](#player-commands)
   - [Admin Commands](#admin-commands)
5. [Rank System](#rank-system)
   - [Default Ranks](#default-ranks)
   - [Customising Ranks](#customising-ranks)
6. [AFK Detection](#afk-detection)
7. [Configuration Reference](#configuration-reference)
8. [Data Storage](#data-storage)
9. [Backups](#backups)
10. [Integrations](#integrations)
    - [LuckPerms](#luckperms)
    - [OpenPAC / OpacFixes](#openpac--opacfixes)
11. [Claim Cleanup](#claim-cleanup)
12. [Migration from KubeJS](#migration-from-kubejs)
13. [Architecture](#architecture)
14. [Building from Source](#building-from-source)
15. [Troubleshooting](#troubleshooting)

---

## Overview

The Playtime mod is a **server-side only** Forge mod that tracks active playtime per player, manages an automatic rank progression system, and integrates with LuckPerms for group syncing and OpenPAC for claim management.

**Key features:**

- Per-player playtime tracking (AFK-aware — idle time is not counted)
- 16-tier rank progression system with configurable thresholds
- Claim and forceload limits that scale with rank (optional — can be disabled)
- Per-rank LuckPerms sync control (`syncWithLuckPerms` flag per rank)
- Rank descriptions and hover text for rich in-game display
- Modular inactivity actions — run any commands on player inactivity (not limited to claim wipe)
- Rank-up announcements with titles, sounds, and server broadcasts
- Interactive admin rank list with hover details and click-to-edit
- Rotating hourly, daily, and weekly data backups
- Full admin command suite for managing player data
- Import tool for migrating from the legacy KubeJS system
- All ranks configurable via an external JSON file — no code changes needed

**Design principles:**

- **UUID-first** — Player records are keyed by UUID, not by username. Names are stored as mutable display data.
- **Config-first** — Ranks, thresholds, messages, AFK settings, and integration toggles are all externally configurable.
- **Optional integrations** — The mod works standalone. LuckPerms and OpenPAC are detected at runtime and used only if present and enabled.
- **Wipe-safe** — The data loader refuses to overwrite the database if the file is corrupt or unreadable.

---

## Installation

1. Requires **Minecraft 1.20.1** with **Forge 47.x** (47.4.16 recommended).
2. Place the `playtime-1.0.jar` file in the server's `mods/` folder.
3. Start the server. The mod will create its config file and default data directories automatically.
4. **(Optional)** Install [LuckPerms](https://luckperms.net/) for automatic group syncing.
5. **(Optional)** Install [Open Parties and Claims](https://www.curseforge.com/minecraft/mc-mods/open-parties-and-claims) + [OpacFixes](https://github.com/enviouse/OpacFixes) for claim cleanup support.

> **Client-side:** The mod is server-side only. Clients do **not** need to install it. The mod sets `displayTest="IGNORE_SERVER_VERSION"` so clients can connect without it.

---

## Getting Started

On first server start, the mod creates:

| Path | Purpose |
|------|---------|
| `config/playtime-common.toml` | Forge config — AFK, saving, backups, integrations, rank-up effects |
| `<world>/playtime/ranks.json` | Rank definitions (16 defaults written on first load) |
| `<world>/playtime/players.json` | Player data (empty on first load) |
| `<world>/playtime/backups/` | Backup directory (created when first backup runs) |
| `<world>/playtime/backup_timestamps.json` | Backup scheduler state |

When a player joins for the first time:

1. A `PlayerRecord` is created with their UUID and username.
2. They are assigned the lowest rank (Beginner by default).
3. If LuckPerms is present, the corresponding LP group is added.
4. If OpenPAC is present, their default claim colour is set.
5. A welcome broadcast is sent to the server (configurable).

---

## Commands

### Player Commands

#### `/playtime`

Shows your personal playtime stats:

```
━━━━━━━━━━ Playtime Stats ━━━━━━━━━━
Total Playtime: 48h 12m 3s [Active]
Current Rank: §b Engineer
  ➤ 110 claims, 0 forceloads, 13d max inactivity
Next Rank: §e Specialist (11h 47m 57s remaining)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

- Shows total tracked time (including current session).
- Shows `[Active]` or `[AFK - Not Tracking]` status.
- Shows current rank with benefits (claims, forceloads, inactivity limit).
- Shows next rank and time remaining, or "Max rank achieved!" if topped out.

#### `/playtime top [page]`

Paginated server leaderboard (configurable page size, default 10 per page):

```
━━━━━━━━━ Top Playtime (Page 1/3) ━━━━━━━━━
1. Steve §c§l§n[Starseeker] - 512h 3m 0s
2. Alex  §b§l§n[Galaxytamer] - 301h 15m 22s
...
[← Prev] Page 1/3 [Next →]
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

Clickable `[← Prev]` and `[Next →]` buttons appear when there are multiple pages.

#### `/ranks [page]`

Paginated list of all visible ranks with thresholds, claims, forceloads, and inactivity limits (configurable page size, default 16 per page):

```
━━━━━━━━━━━ Ranks (Page 1/2) ━━━━━━━━━━━
(Playtime — Claims, Forceloads)
- §7§o Beginner - 1h - 4 claims, 0 forceloads, 1d inactivity
- §f Gatherer  - 3h - 9 claims, 0 forceloads, 3d inactivity
...
[← Prev] Page 1/2 [Next →]
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

Rank colours come from LuckPerms group prefixes when available, otherwise from the rank's configured `fallbackColor`.

Clickable `[← Prev]` and `[Next →]` navigation buttons appear when there are multiple pages.

> **Note:** The `/claims` command has been removed — claim and forceload limits are shown directly in `/ranks` for each rank.

---

### Admin Commands

All admin commands require the configured permission level (default: **op level 2**).

#### `/playtimeadmin list <player>`

Shows detailed info for a player:

```
━━━━ Playtime for Steve ━━━━
Total Playtime: 48h 12m 3s
Current Rank: §b Engineer
First Join: 2025-01-15 14:22:31
Last Seen: 2026-03-12 09:15:44
UUID: 12345678-1234-1234-1234-123456789abc
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

#### `/playtimeadmin add <player> <time>`

Adds time to a player's total. Triggers rank-up if threshold crossed.

```
/playtimeadmin add Steve 2h30m
→ Added 2h 30m 0s to Steve's playtime (total: 50h 42m 3s)

/playtimeadmin add Steve 5       ← plain number = hours (backward compat)
→ Added 5h 0m 0s to Steve's playtime (total: 55h 42m 3s)
```

#### `/playtimeadmin remove <player> <time>`

Removes time from a player's total (floor at 0). May trigger rank-down.

```
/playtimeadmin remove Steve 10h
→ Removed 10h 0m 0s from Steve's playtime (total: 45h 42m 3s)
```

#### `/playtimeadmin set <player> <time>`

Sets a player's total playtime to an exact value.

```
/playtimeadmin set Steve 100h
→ Set Steve's playtime to 100h 0m 0s
```

#### `/playtimeadmin reset <player>`

Completely resets a player — removes all rank groups from LuckPerms, deletes their record from the database.

```
/playtimeadmin reset Steve
→ Reset Steve's playtime and rank data.
```

#### `/playtimeadmin rank set <player> <rankId>`

Sets a player's rank to a specific rank. Also sets their playtime to match the rank's threshold.

```
/playtimeadmin rank set Steve engineer
→ Set Steve's rank to Engineer (playtime set to 48h 0m 0s)
```

#### `/playtimeadmin rank sync`

Resyncs all player ranks with LuckPerms. Removes incorrect groups and adds the correct one for each player based on their current playtime.

```
/playtimeadmin rank sync
→ Resynced ranks for 142 players. (Ranks with syncWithLuckPerms=false were skipped for LP sync)
```

> **Note:** Ranks with `syncWithLuckPerms: false` will be skipped during LP group sync. The rank is still assigned in the playtime system, but no LuckPerms group change is made for those ranks.

#### `/playtimeadmin rank list`

Lists all configured ranks (including hidden ones) with their sort order, visibility, LP sync status, ID, threshold, claims, forceloads, and inactivity settings. Rank names are displayed with their configured colours (including hex).

**Interactive:** Each rank row has `[D]` and `[H]` buttons. Click `[D]` to edit the description (auto-fills the current value in your chat input). Click `[H]` to edit the hover text (also auto-fills). Hover over any rank to see full details. Click the rank itself to view `/playtimeadmin rank info`.

```
/playtimeadmin rank list
→ ━━━━━━━━━ All Ranks (16) ━━━━━━━━━
→ ([D] edit description, [H] edit hover text. Hover for details.)
→ #0 [✓] [⟳] [D] [H] Beginner (id: beginner) 1h | 4c 0fl | 1d
→ #1 [✓] [⟳] [D] [H] Gatherer (id: gatherer) 3h | 9c 0fl | 3d
→ ...
```

#### `/playtimeadmin rank info <rankId>`

Shows all details for a specific rank including its colour preview, description, hover text, LP sync status, and inactivity actions.

```
/playtimeadmin rank info engineer
→ ━━━━━━━━━ Rank: Engineer ━━━━━━━━━
→ ID: engineer
→ Display Name: Engineer
→ Visible: yes
→ Threshold: 48h (3456000 ticks)
→ Claims: 110
→ Forceloads: 0
→ Inactivity Limit: 13 days
→ LuckPerms Group: Engineer
→ LP Sync: Enabled
→ Fallback Color: §b
→ Sort Order: 6
→ Description: (none)
→ Hover Text: (none)
→ Inactivity Actions:
→   #0 13d → /openpac-wipe {uuid}
```

#### `/playtimeadmin rank add <id> <displayName> <hours> [claims] [forceloads] [inactivityDays] [color]`

Creates a new rank. Optional parameters default to: claims=0, forceloads=0, inactivityDays=7, color=§f. The rank is automatically given the next available sort order and its LuckPerms group name defaults to the display name.

Supports hex colours using the `&#RRGGBB` format (compatible with Better-Forge-Chat).

```
/playtimeadmin rank add veteran Veteran 400 700 8 60 &#FFD700
→ Created rank 'Veteran' (id: veteran, 400h, order: 16)

/playtimeadmin rank add newbie Newbie 0 2 0 1
→ Created rank 'Newbie' (id: newbie, 0h, order: 17)
```

#### `/playtimeadmin rank remove <rankId>`

Removes a rank from the configuration. Players currently at that rank will be recalculated on their next activity or when `/playtimeadmin rank sync` is run.

```
/playtimeadmin rank remove veteran
→ Removed rank 'Veteran' (id: veteran). Players at this rank will be recalculated on next activity or rank sync.
```

#### `/playtimeadmin rank edit <rankId> <field> <value>`

Edits a single field of an existing rank. Changes are saved immediately to `ranks.json`.

**Editable fields:** `displayName`, `visible`, `hours`, `claims`, `forceloads`, `inactivityDays`, `luckpermsGroup`, `fallbackColor`, `sortOrder`, `syncWithLuckPerms`, `description`, `hoverText`

> For `description` and `hoverText`, use `none` or `clear` as the value to remove them.

```
/playtimeadmin rank edit beginner displayName Newcomer
→ Updated rank 'Newcomer': displayName = Newcomer

/playtimeadmin rank edit engineer fallbackColor &#00BFFF
→ Updated rank 'Engineer': fallbackColor = &#00BFFF

/playtimeadmin rank edit scout claims 25
→ Updated rank 'Scout': claims = 25

/playtimeadmin rank edit starseeker visible false
→ Updated rank 'Starseeker': visible = false

/playtimeadmin rank edit beginner syncWithLuckPerms false
→ Updated rank 'Beginner': syncWithLuckPerms = false

/playtimeadmin rank edit beginner description Starter rank - ability to use /help, /commands
→ Updated rank 'Beginner': description = Starter rank - ability to use /help, /commands
```

#### `/playtimeadmin rank setdesc <rankId> <text>`

Sets the description for a rank. When set, the description replaces the claims/forceloads/inactivity line in `/ranks`.

```
/playtimeadmin rank setdesc beginner Starter rank - ability to use /help, /commands
→ Set description for 'Beginner': Starter rank - ability to use /help, /commands
```

#### `/playtimeadmin rank sethover <rankId> <text>`

Sets the hover text for a rank. When set, hovering over the rank in `/ranks` shows this text. Use `\n` for line breaks.

```
/playtimeadmin rank sethover beginner Welcome to the server!\nYou can use /help and /commands
→ Set hover text for 'Beginner': Welcome to the server!\nYou can use /help and /commands
```

#### `/playtimeadmin rank edithover <rankId> <text>`

Alias for `sethover` — replaces the hover text for a rank.

#### `/playtimeadmin rank inactivity <rankId> add <command> <time>`

Adds a modular inactivity action to a rank. When a player at this rank has been inactive for the specified time, the command is executed by the server. Supports placeholders: `{uuid}`, `{player}`, `{rank}`.

```
/playtimeadmin rank inactivity engineer add "/openpac-wipe {uuid}" 13d
→ Added inactivity action to 'Engineer': /openpac-wipe {uuid} after 13 days

/playtimeadmin rank inactivity beginner add "/mail send {player} You've been inactive!" 7d
→ Added inactivity action to 'Beginner': /mail send {player} You've been inactive! after 7 days
```

> When a rank has inactivity actions configured, they **replace** the legacy `inactivityDays` OPAC wipe behavior. If no actions are configured, the legacy behavior is used as a fallback.

#### `/playtimeadmin rank inactivity <rankId> remove <index>`

Removes an inactivity action by its index (0-based).

```
/playtimeadmin rank inactivity engineer remove 0
→ Removed inactivity action #0 from 'Engineer': /openpac-wipe {uuid} (13d)
```

#### `/playtimeadmin rank inactivity <rankId> list`

Lists all inactivity actions for a rank. Each action is clickable to remove.

```
/playtimeadmin rank inactivity engineer list
→ ━━━━━ Inactivity Actions: Engineer ━━━━━
→   #0 13d → /openpac-wipe {uuid}
→   #1 7d → /mail send {player} Warning: inactive!
→ [+ Add Action]
```

#### `/playtimeadmin rank gradient <rankId> <color1> <color2> [color3...]`

Sets a rank's fallback color to an auto-generated gradient specification. The gradient is dynamically applied to the rank's display name whenever it's rendered. Accepts 2 or more hex colors for multi-stop gradients.

```
/playtimeadmin rank gradient engineer #AD3080 #C3D1BB
→ Set gradient for 'Engineer' → gradient:#AD3080-#C3D1BB
→ Preview: E̲n̲g̲i̲n̲e̲e̲r̲  (each letter a different color)

/playtimeadmin rank gradient starseeker #FF0000 #FFFF00 #00FF00
→ Set gradient for 'Starseeker' → gradient:#FF0000-#FFFF00-#00FF00
→ Preview: S̲t̲a̲r̲s̲e̲e̲k̲e̲r̲  (red → yellow → green)
```

The gradient spec format `gradient:#RRGGBB-#RRGGBB` is stored in `fallbackColor`. It automatically recalculates when the display name changes.

You can also add formatting (bold, underline, etc.) by editing the fallback color directly:
```
/playtimeadmin rank edit starseeker fallbackColor gradient:#FF0000-#00FF00§l§n
```

#### `/playtimeadmin rank prebake <rankId> <color1> <color2> [color3...]`

Like `gradient`, but generates a **pre-baked** per-character gradient string (each character has its own `&#RRGGBB` prefix). This format is compatible with Better-Forge-Chat and other mods.

```
/playtimeadmin rank prebake playtime #AD3080 #C3D1BB
→ Set pre-baked gradient for 'Playtime'
→ Raw: &#AD3080P&#9C80A9l&#8ACFD2a&#649CA2y&#3E6A72t&#183742i&#6E847Fm&#C3D1BBe
→ Preview: Playtime  (each letter colored)
```

> **Gradient vs Prebake:** Use `gradient` if your rank name might change (spec re-applies to any text). Use `prebake` for maximum compatibility with chat formatting mods (the colors are baked into the string).

#### Colour Format Reference

| Format | Example | Description |
|--------|---------|-------------|
| `§a` | `§a` | Legacy Minecraft colour code |
| `&#RRGGBB` | `&#FF5500` | Single hex colour |
| `&#RRGGBBX&#RRGBBBY...` | `&#AD3080P&#9C80A9l...` | Pre-baked per-character gradient |
| `gradient:#RRGGBB-#RRGGBB` | `gradient:#AD3080-#C3D1BB` | 2-stop gradient spec |
| `gradient:#RRGGBB-#RRGGBB-#RRGGBB` | `gradient:#FF0000-#FFFF00-#00FF00` | Multi-stop gradient spec |
| `gradient:..§l§n` | `gradient:#FF0000-#00FF00§l§n` | Gradient + bold + underline |
| `§6§l§n` | `§6§l§n` | Legacy combo (gold + bold + underline) |

#### `/playtimeadmin cleanup [dryrun]`

Runs the inactivity-based claim cleanup manually. With `dryrun`, reports what would be wiped without actually removing claims.

```
/playtimeadmin cleanup
→ [ClaimCleanup] Starting claim cleanup check...
→ [ClaimCleanup] Complete. Processed=3, already=12, skipped=0

/playtimeadmin cleanup dryrun
→ [ClaimCleanup] Starting claim cleanup check (DRY RUN)...
→ [ClaimCleanup] (dry run) Would wipe claims for OldPlayer
→ [ClaimCleanup] Complete. Processed=3, already=12, skipped=0
```

#### `/playtimeadmin backup now`

Creates a manual backup snapshot immediately.

```
/playtimeadmin backup now
→ Manual backup created successfully.
```

#### `/playtimeadmin reload`

Reloads the `ranks.json` file without restarting the server.

```
/playtimeadmin reload
→ Reloaded rank definitions (16 ranks).
```

#### `/playtimeadmin import <filepath>`

Imports player data from the legacy KubeJS `playtime_data.json` file. Players with placeholder UUIDs (`--`) are skipped. Existing players in the database are not overwritten.

```
/playtimeadmin import kubejs/data/playtime/playtime_data.json
→ Imported 87 player records from KubeJS data.
```

---

### Time Format

All time arguments in admin commands support these formats:

| Input | Meaning |
|-------|---------|
| `1h30m` | 1 hour 30 minutes |
| `2d4h` | 2 days 4 hours |
| `45m` | 45 minutes |
| `90s` | 90 seconds |
| `1d2h30m10s` | 1 day 2 hours 30 minutes 10 seconds |
| `5` | 5 hours (plain number = hours, backward compatible) |
| `2.5` | 2 hours 30 minutes (decimal hours) |

---

## Rank System

### Default Ranks

The mod ships with 16 default ranks matching the original KubeJS system:

| # | Rank | Hours | Claims | Forceloads | Inactivity | Colour |
|---|------|-------|--------|------------|------------|--------|
| 1 | Beginner | 1 | 4 | 0 | 1 day | §7§o (grey italic) |
| 2 | Gatherer | 3 | 9 | 0 | 3 days | §f (white) |
| 3 | Scout | 8 | 18 | 0 | 5 days | §a (green) |
| 4 | Explorer | 16 | 32 | 0 | 7 days | §2 (dark green) |
| 5 | Technician | 24 | 50 | 0 | 9 days | §9 (blue) |
| 6 | Mechanic | 36 | 75 | 0 | 11 days | §3 (dark aqua) |
| 7 | Engineer | 48 | 110 | 0 | 13 days | §b (aqua) |
| 8 | Specialist | 60 | 160 | 1 | 15 days | §e (yellow) |
| 9 | Commander | 72 | 220 | 2 | 17 days | §6 (gold) |
| 10 | Aviator | 84 | 280 | 3 | 18 days | §c (red) |
| 11 | Astronaut | 96 | 325 | 4 | 20 days | §4 (dark red) |
| 12 | Cosmonaut | 120 | 400 | 5 | 22 days | §1 (dark blue) |
| 13 | Orbiteer | 150 | 500 | 6 | 24 days | §8 (dark grey) |
| 14 | Solarfarer | 200 | 600 | 7 | 30 days | §6§l§n (gold bold underline) |
| 15 | Galaxytamer | 300 | 675 | 8 | 90 days | §b§l§n (aqua bold underline) |
| 16 | Starseeker | 500 | 750 | 9 | Never | §c§l§n (red bold underline) |

### Customising Ranks

Ranks are defined in `<world>/playtime/ranks.json`. You can edit this file directly and run `/playtimeadmin reload`, or manage ranks entirely in-game using the `/playtimeadmin rank` commands.

**There is no limit on the number of ranks.** You can have 16, 48, 100, or more — the system handles any number.

#### In-Game Rank Management

| Command | Description |
|---------|-------------|
| `/playtimeadmin rank list` | List all ranks with details |
| `/playtimeadmin rank info <id>` | View full details of a rank |
| `/playtimeadmin rank add <id> <name> <hours> [claims] [forceloads] [inactivityDays] [color]` | Create a new rank |
| `/playtimeadmin rank remove <id>` | Delete a rank |
| `/playtimeadmin rank edit <id> <field> <value>` | Edit any field of a rank |
| `/playtimeadmin reload` | Reload ranks.json from disk |

All changes via commands are saved to `ranks.json` automatically.

Each rank entry has these fields:

```json
{
  "id": "engineer",
  "displayName": "Engineer",
  "visible": true,
  "thresholdTicks": 3456000,
  "claims": 110,
  "forceloads": 0,
  "inactivityDays": 13,
  "luckpermsGroup": "Engineer",
  "fallbackColor": "§b",
  "sortOrder": 6,
  "syncWithLuckPerms": true,
  "description": null,
  "hoverText": null,
  "inactivityActions": [
    { "command": "/openpac-wipe {uuid}", "delayDays": 13 }
  ]
}
```

| Field | Type | Description |
|-------|------|-------------|
| `id` | string | Unique identifier (lowercase recommended). Used as the internal key. |
| `displayName` | string | Human-readable name shown in chat and commands. |
| `visible` | boolean | Whether the rank appears in `/ranks` and `/claims`. Hidden ranks still function. |
| `thresholdTicks` | long | Minimum playtime in ticks to earn this rank. 1 hour = 72,000 ticks. |
| `claims` | int | Maximum claims a player at this rank is allowed. |
| `forceloads` | int | Maximum forceloaded chunks allowed. |
| `inactivityDays` | int | Days of inactivity before claims are wiped (legacy fallback). `-1` = never wiped. |
| `luckpermsGroup` | string | LuckPerms group name to sync when a player reaches this rank. |
| `fallbackColor` | string | `§`-code or `&#RRGGBB` hex colour string used when LuckPerms prefix is unavailable. Hex format is compatible with Better-Forge-Chat. |
| `sortOrder` | int | Controls display order. Lower = earlier. Must be unique across ranks. |
| `syncWithLuckPerms` | boolean | Whether this rank should sync with LuckPerms groups. `null`/missing = `true`. |
| `description` | string | Custom description shown in `/ranks` instead of claims/forceloads. `null` = use default. |
| `hoverText` | string | Text shown when hovering over the rank in chat. Supports `\n` for line breaks. |
| `inactivityActions` | array | List of `{command, delayDays}` objects. When present, replaces legacy inactivityDays behavior. |

**Tips:**

- Set `visible: false` for staff-only or hidden milestone ranks.
- Set `inactivityDays: -1` to make a rank immune to claim cleanup.
- The `thresholdTicks` value must increase with `sortOrder` for progression to work correctly.
- Conversion: **hours × 72,000 = ticks**.

---

## AFK Detection

The mod tracks player activity by monitoring camera rotation (yaw and pitch). If a player's camera has not moved more than the configured threshold for the configured timeout period, they are marked as **AFK** and playtime tracking pauses.

**How it works:**

1. Every `afk.checkInterval` ticks (default: 20 = once per second), each online player's camera angles are sampled.
2. If the yaw or pitch has moved ≥ `afk.lookThresholdDegrees` (default: 2.0°) since the last sample, the player is considered **active** and session ticks are accumulated.
3. If the camera has not moved for `afk.timeoutTicks` consecutive ticks (default: 6000 = 5 minutes), the player enters AFK state.
4. While AFK, an action bar message is displayed periodically: `⚠ AFK detected — Playtime tracking paused`.
5. When the player moves their camera again, tracking resumes and a message is displayed: `✓ Playtime tracking resumed!`.

> **Note:** Players who idle with an AFK macro (anti-AFK farms) are still detected because the system requires genuine camera rotation exceeding the threshold.

---

## Configuration Reference

The Forge config file is located at `config/playtime-common.toml`. All values can be changed while the server is running — they take effect on next config reload.

### Features

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `features.claimsEnabled` | boolean | `true` | Enable the claims system. When false, `/claims` is hidden and claims are not shown in `/ranks`. |
| `features.forceloadsEnabled` | boolean | `true` | Enable the forceload system. When false, forceloads are not shown in `/ranks`. |

### AFK Detection

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `afk.timeoutTicks` | int | `6000` | Ticks of no camera movement before AFK. 6000 = 5 minutes. |
| `afk.checkInterval` | int | `20` | Ticks between AFK checks. 20 = once per second. |
| `afk.lookThresholdDegrees` | double | `2.0` | Minimum degrees of camera rotation to count as active. |
| `afk.notifyInterval` | int | `6000` | Ticks between repeated AFK notifications. |

### Saving

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `saving.intervalTicks` | int | `6000` | Ticks between periodic data saves to disk. |
| `saving.flushIntervalTicks` | int | `600` | Ticks between flushing session time into totals. |

### Backups

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `backup.enabled` | boolean | `true` | Enable the rotating backup system. |
| `backup.checkIntervalTicks` | int | `12000` | Ticks between backup eligibility checks. |

### Cleanup

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `cleanup.enabled` | boolean | `true` | Enable automatic inactivity claim cleanup. |
| `cleanup.delayTicks` | int | `6000` | Ticks after server start before first cleanup. |

### Integrations

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `integration.luckpermsEnabled` | boolean | `true` | Enable LuckPerms group syncing. |
| `integration.luckpermsForceSync` | boolean | `true` | Run `/lp sync` after rank changes for tab reload. |
| `integration.opacEnabled` | boolean | `true` | Enable OpenPAC integration. |
| `integration.defaultClaimColorHex` | string | `"7F7F7F"` | Default OPAC claim colour for first-join players. |

### Rank-up Effects

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `rankup.broadcast` | boolean | `true` | Broadcast rank-up messages to all players. |
| `rankup.sound` | string | `"minecraft:entity.player.levelup"` | Sound resource to play on rank-up. |
| `rankup.soundVolume` | double | `1.0` | Volume of the rank-up sound. |
| `rankup.soundPitch` | double | `1.2` | Pitch of the rank-up sound. |
| `rankup.titleFadeIn` | int | `10` | Title fade-in ticks. |
| `rankup.titleStay` | int | `60` | Title stay ticks. |
| `rankup.titleFadeOut` | int | `20` | Title fade-out ticks. |

### Commands

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `commands.adminPermissionLevel` | int | `2` | Permission level for `/playtimeadmin`. 2 = op. |
| `commands.ranksPageSize` | int | `16` | Number of ranks to show per page in `/ranks`. |
| `commands.topPageSize` | int | `10` | Number of players to show per page in `/playtime top`. |

### First Join

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `firstJoin.broadcast` | boolean | `true` | Broadcast a welcome message on first player join. |

---

## Data Storage

Player data is stored as a JSON array in `<world>/playtime/players.json`.

Each record looks like:

```json
{
  "uuid": "12345678-1234-1234-1234-123456789abc",
  "lastUsername": "Steve",
  "totalPlaytimeTicks": 3456000,
  "currentRankId": "engineer",
  "firstJoinEpochMs": 1705312951000,
  "lastSeenEpochMs": 1710234944000,
  "claimsWipedAtMs": 0,
  "claimsWipeLastSeenMs": 0,
  "dataVersion": 1
}
```

**Wipe-safe loading:** If the file cannot be parsed (corrupt, empty, null), the mod refuses to overwrite it and disables write operations until the issue is resolved manually. This prevents data loss from crashes during saves.

**Periodic saving:** Session data is flushed to player totals every `saving.flushIntervalTicks` (default: 30 seconds), and the full database is written to disk every `saving.intervalTicks` (default: 5 minutes). All remaining data is force-saved on server shutdown.

---

## Backups

The mod creates rotating backups of `players.json`:

| Backup | Interval | File |
|--------|----------|------|
| Hourly | Every 1 hour | `<world>/playtime/backups/backup-hourly.json` |
| Daily | Every 24 hours | `<world>/playtime/backups/backup-daily.json` |
| Weekly | Every 7 days | `<world>/playtime/backups/backup-weekly.json` |
| Manual | On demand | `<world>/playtime/backups/backup-manual-<timestamp>.json` |

Backup timestamps are persisted to `<world>/playtime/backup_timestamps.json` so they survive server restarts. If the server has been off for a while, any overdue backups are created immediately on start.

To create a manual backup: `/playtimeadmin backup now`.

To restore from a backup: stop the server, copy the backup file over `players.json`, and restart.

---

## Integrations

### LuckPerms

When `integration.luckpermsEnabled` is `true` and LuckPerms is installed, the mod:

1. **Syncs groups on rank change** — When a player earns a new rank, the old LP group (defined in the rank's `luckpermsGroup` field) is removed and the new one is added using the LuckPerms Java API (`InheritanceNode`).
2. **Reads group prefixes** — When displaying ranks in `/ranks`, `/claims`, and `/playtime`, the mod checks the LP group for a configured prefix. If found, it's used as the display colour. Otherwise, the rank's `fallbackColor` is used.
3. **Force sync** — When `integration.luckpermsForceSync` is `true`, the mod runs `lp sync` after rank changes to refresh tab list display.
4. **Bulk resync** — `/playtimeadmin rank sync` recalculates all players and corrects any LP group mismatches.

**If LuckPerms is not installed:** The mod detects the absence at startup and silently disables LP features. All rank calculations, tracking, and commands continue to work using fallback colours.

### OpenPAC / OpacFixes

When `integration.opacEnabled` is `true` and OpenPAC is installed, the mod:

1. **Sets default claim colour** — On first join, runs `openpac player-config set claims.color <hex>`.
2. **Wipes claims for inactive players** — The cleanup service iterates OPAC's claim data (dimension → region → chunk), finds chunks owned by the inactive player's UUID, and calls `claims.unclaim()` on each.
3. **Supports dry runs** — `/playtimeadmin cleanup dryrun` counts claims without removing them.

**If OpenPAC is not installed:** Claim-related features are silently skipped.

---

## Claim Cleanup

The inactivity cleanup system wipes claims for players who have been offline longer than their rank allows.

**How it works:**

1. On server start (after a configurable delay), or when `/playtimeadmin cleanup` is run manually, the cleanup service scans all player records.
2. For each player, it looks up their current rank's `inactivityDays` value.
3. If a player has been offline for ≥ `inactivityDays`, their claims are wiped via the OpenPAC API.
4. Ranks with `inactivityDays: -1` (like Starseeker) are **immune** — their claims are never wiped.
5. After wiping, the record stores `claimsWipedAtMs` and `claimsWipeLastSeenMs` to avoid re-processing the same player until they log in again.

**Safeguards:**

- Won't run if OpenPAC is not installed or disabled in config.
- Won't run if player data failed to load.
- Supports `dryrun` mode for testing.
- Each player is only processed once per absence period.

---

## Migration from KubeJS

To migrate from the old KubeJS playtime system:

1. Locate your old data file, typically at `kubejs/data/playtime/playtime_data.json`.
2. Install the Playtime mod and start the server to initialise the data directories.
3. Run: `/playtimeadmin import kubejs/data/playtime/playtime_data.json`
4. The importer reads the old name-keyed JSON format and converts each entry to a UUID-keyed `PlayerRecord`.

**What gets imported:**

- `totalPlaytime` → `totalPlaytimeTicks`
- `firstJoin` → `firstJoinEpochMs`
- `lastSeen` → `lastSeenEpochMs`
- `uuid` → used as the record key
- `currentRank` → mapped to lowercase rank ID

**What gets skipped:**

- Entries with placeholder UUID (`--` or empty) — these players never had their UUID resolved.
- Entries that already exist in the mod's database (no duplicates).

After import, you can verify with `/playtimeadmin list <player>` and remove the old KubeJS scripts.

---

## Architecture

```
com.enviouse.playtime
├── Playtime.java                       Main @Mod entry point & service orchestrator
├── Config.java                         ForgeConfigSpec (all TOML config fields)
│
├── config/
│   └── RankConfig.java                 Loads/saves ranks.json, writes defaults on first run
│
├── data/
│   ├── RankDefinition.java             Rank POJO (id, thresholds, claims, forceloads, etc.)
│   ├── InactivityAction.java           Modular inactivity command (command + delayDays)
│   ├── PlayerRecord.java              Player POJO (UUID-keyed, ticks, rank, timestamps)
│   ├── PlayerDataRepository.java      Repository interface (abstraction for future SQLite)
│   └── JsonPlayerDataRepository.java  JSON-file-backed implementation
│
├── service/
│   ├── SessionTracker.java            AFK detection & session tick tracking
│   ├── RankEngine.java                Rank calculation, progression, rank-up effects
│   ├── BackupService.java             Rotating hourly/daily/weekly backup system
│   └── CleanupService.java            Inactivity-based claim wipe
│
├── integration/
│   ├── LuckPermsService.java          Optional LuckPerms API bridge
│   └── OpacBridge.java                Optional OpenPAC API bridge
│
├── command/
│   ├── CommandRegistration.java       Registers all commands via RegisterCommandsEvent
│   ├── PlaytimeCommand.java           /playtime, /playtime top
│   ├── RanksCommand.java              /ranks
│   └── PlaytimeAdminCommand.java      /playtimeadmin (full admin suite)
│
├── util/
│   ├── TimeParser.java                Parses "1h30m" / "2d4h" into ticks
│   └── ColorUtil.java                 Hex (&#RRGGBB) + legacy §-code → styled Component
│
└── migration/
    └── KubeJsImporter.java            Imports legacy KubeJS playtime_data.json
```

**Lifecycle:**

1. **Mod construction** — Registers Forge config and event bus listeners.
2. **Server started** — Loads rank config, player data, initialises integrations and services.
3. **Server tick** — Drives AFK checks, session flush, periodic saves, backup checks, and scheduled cleanup.
4. **Player join** — Creates or updates player record, verifies rank, applies first-join effects.
5. **Player leave** — Flushes remaining session ticks, updates last-seen timestamp.
6. **Server stopping** — Flushes all sessions, force-saves data, cleans up references.

---

## Building from Source

**Requirements:** Java 17 toolchain.

```bash
# Build the mod jar
./gradlew build
# Output: build/libs/playtime-1.0.jar

# Compile only (fast check)
./gradlew compileJava

# Run a dev client
./gradlew runClient

# Run a dev server
./gradlew runServer
```

> The Gradle daemon is disabled (`org.gradle.daemon=false`) — every build uses a fresh JVM.

**Compile-time dependencies** (in `libs/`, not bundled in the output JAR):

| File | Purpose |
|------|---------|
| `LuckPerms.jar` | LuckPerms API for group sync |
| `opac.jar` | OpenPAC API for claim management |
| `opacfixes-1.0.jar` | OpacFixes API types |

---

## Troubleshooting

### "Playtime system not ready (data failed to load)"

The `players.json` file could not be parsed. Check the server log for the specific error. Common causes:
- File is empty or contains invalid JSON.
- File permissions prevent reading.

**Fix:** Restore from a backup in `<world>/playtime/backups/` or fix the JSON manually, then restart.

### LuckPerms groups not syncing

1. Check that `integration.luckpermsEnabled = true` in the config.
2. Verify LuckPerms is installed and loaded (check `plugins` or `mods` list).
3. Check server logs for `[Playtime] LuckPerms API found and bound.` at startup.
4. Ensure the rank's `luckpermsGroup` matches an existing LP group name (case-insensitive).
5. Run `/playtimeadmin rank sync` to force-resync all players.

### Claims not being cleaned up

1. Check that `cleanup.enabled = true` and `integration.opacEnabled = true`.
2. Verify OpenPAC is installed.
3. Ensure the rank's `inactivityDays` is not `-1` (immune).
4. Run `/playtimeadmin cleanup dryrun` to test without removing claims.
5. Check server logs for `[ClaimCleanup]` messages.

### Rank-up effects not appearing

1. Check that `rankup.broadcast = true`.
2. Verify the sound resource path is valid (e.g. `minecraft:entity.player.levelup`).
3. The player must be **online** when the rank-up occurs for titles and sounds to play.

### AFK detection too sensitive / not sensitive enough

Adjust in `config/playtime-common.toml`:
- Increase `afk.timeoutTicks` to require longer idle before AFK (e.g. `12000` = 10 minutes).
- Increase `afk.lookThresholdDegrees` to require larger camera movements (e.g. `5.0`).
- Decrease `afk.checkInterval` for more frequent checks (e.g. `10` = twice per second).

