# 🕐 Playtime — Integrated Playtime Tracking & Rank Progression

**Track active playtime. Reward loyal players. Automate everything.**

> **Summary (250 chars):**
> Server-side playtime tracker with 33-rank progression, AFK detection, custom GUI, LuckPerms sync, OpenPAC claim cleanup, display ranks, gradient colors, rotating backups, and full admin tools. Forge 1.20.1.

---

## 📖 What Is Playtime?

**Playtime** is a powerful **server-side only** Forge mod that tracks every player's active playtime and automatically progresses them through a fully customizable rank system. It's AFK-aware, so idle time doesn't count — only real gameplay earns ranks.

Whether you're running a small community server or a massive network, Playtime gives you the tools to **reward dedication**, **manage claims**, and **keep your server clean** — all without touching a single config file manually (though you can if you want to!).

---

## ✨ Key Features

### 🎖️ 33-Rank Progression System
Progress through **8 themed phases** — from humble Beginner to the almighty **Singularity**. Each rank unlocks more claim chunks, forceloaded chunks, and comes with its own unique color and icon.

| Phase | Theme | Ranks | Style |
|-------|-------|-------|-------|
| 🌱 Phase 1 | The Grounded | Starter → **Settler** | Survival & Settlement |
| 🔮 Phase 2 | The Arcane | Apprentice → **Wizard** | Magic & Mysticism |
| ⚙️ Phase 3 | The Industrial | Tinker → **Steamlord** | Steampunk Era |
| 🔧 Phase 4 | The Technological | Technician → **Commander** | Modern Engineering |
| 🚀 Phase 5 | The Ascent | Aviator → **Orbiteer** | Atmosphere & Early Space |
| 🪐 Phase 6 | The Interplanetary | Spacefarer → **Starseeker** | Deep Space & Colonization |
| 🌌 Phase 7 | The Cosmic Manipulators | Riftshaper → **Eclipsebringer** | Bending Physics |
| ⭐ Phase 8 | The Absolute | Ascendant → **Singularity** | God-Tier |

> 💡 **Fully customizable!** Add, remove, or edit any rank in-game or via JSON. There's no limit — have 16 ranks or 200!

---

### 🎨 Rich Color System
Ranks aren't just names — they're **visual experiences**.

- 🟠 **Single hex colors** — `&#FFA500` for a clean, bold look
- 🌈 **Multi-stop gradients** — `gradient:#DDA0DD-#00CED1-#FFD700` for stunning rank titles
- ✏️ **Formatting** — Bold, underline, italic, and more via `§l`, `§n`, etc.
- 🔥 **Pre-baked per-character gradients** — Full compatibility with [Better-Forge-Chat](https://www.curseforge.com/minecraft/mc-mods/better-forge-chat-reforged-reworked)

Phase finals use gradients composed of their phase's 3 preceding colors — Phases 1–2 are underlined, Phases 3+ are bold, and Phase 8 is all gradient + bold. 🔥

---

### 🖥️ Custom In-Game GUI
Open the Playtime GUI to see everything at a glance:

- 📊 **Your stats** — Total playtime, current rank, next rank, and time remaining
- 📋 **Player list** — See all players, their ranks, and playtime with search & sorting
- 🏅 **Rank grid** — Visual grid of all ranks with icons, colors, and progress indicators
- 🏷️ **Display Rank selector** — Pick any rank you've earned as your display title
- 🔍 **Player detail popups** — Click any player to see their full profile
- 📖 **Rank detail popups** — Click any rank for thresholds, benefits, and descriptions

---

### 🏷️ Display Ranks
Let players **show off** their favorite earned rank! Once you've reached a rank, you can set it as your display rank — it appears as an underlined, colored suffix next to your name in chat.

- Works seamlessly with **LuckPerms suffixes** and **Better-Forge-Chat**
- Gradient ranks show per-character colored text in chat
- Set via the GUI popup or `/playtime displayrank set <name>`
- Admins can set display ranks for any player

---

### 😴 Smart AFK Detection
Playtime only counts when you're **actually playing**. The multi-signal activity detection system watches for:

- 🎥 Camera rotation
- 🚶 Position movement
- 🔄 Hotbar slot changes
- 🏃 Sprint toggles
- ⚔️ Interactions (block break/place, attacks, chat)

Players must produce **multiple distinct signal types** to be considered active — simple AFK macros won't fool it! When AFK is detected, an action bar message appears and tracking pauses until real activity resumes.

---

### 🔗 LuckPerms Integration
Seamlessly sync ranks with LuckPerms groups:

- ✅ **Auto group sync** — Rank up → old LP group removed, new one added
- 🎨 **Prefix colors** — Reads LP group prefixes for display colors
- 🔄 **Bulk resync** — `/playtimeadmin rank sync` fixes any mismatches
- ⚙️ **Per-rank control** — Toggle `syncWithLuckPerms` per rank for fine-grained control
- 🏷️ **Display rank suffixes** — Colored, formatted suffixes set automatically via LP API

> 💡 **No LuckPerms? No problem.** The mod works standalone with its own integrated chat formatter and fallback colors.

---

### 🗺️ OpenPAC Claim Management
Integrate with [Open Parties and Claims](https://www.curseforge.com/minecraft/mc-mods/open-parties-and-claims) for automatic claim management:

- 📏 **Claim limits by rank** — Higher ranks get more claims and forceloads
- 🧹 **Inactivity cleanup** — Automatically wipe claims from inactive players
- ⏱️ **Per-rank inactivity thresholds** — Each rank has its own timeout (1 day → never)
- 🛡️ **Immunity** — Top ranks can be made immune to cleanup (`inactivityDays: -1`)
- 🧪 **Dry run mode** — Test cleanup without actually removing anything
- 🔧 **Modular inactivity actions** — Run any commands on inactivity, not just claim wipes

---

### 💾 Data Safety & Backups
Your player data is precious. Playtime protects it:

- 🔄 **Rotating backups** — Hourly, daily, and weekly automatic snapshots
- 📸 **Manual backups** — `/playtimeadmin backup now` anytime
- 🛡️ **Wipe-safe loading** — If `players.json` is corrupt, the mod refuses to overwrite it
- 💾 **Periodic saves** — Session data flushed every 30s, full save every 5 minutes
- 🔒 **UUID-keyed records** — Player identity is never lost, even if usernames change

---

### 🛠️ Full Admin Command Suite
Manage everything without leaving the game:

| Command | Description |
|---------|-------------|
| `/playtimeadmin list <player>` | 📋 View detailed player info |
| `/playtimeadmin add/remove/set <player> <time>` | ⏱️ Modify player playtime |
| `/playtimeadmin reset <player>` | 🗑️ Full player data reset |
| `/playtimeadmin rank set <player> <rank>` | 🎖️ Force-set a player's rank |
| `/playtimeadmin rank sync` | 🔄 Bulk resync all players with LP |
| `/playtimeadmin rank add/remove/edit` | ✏️ Full rank CRUD in-game |
| `/playtimeadmin rank list` | 📜 Interactive rank list with edit buttons |
| `/playtimeadmin rank gradient <id> <colors...>` | 🌈 Set gradient colors for a rank |
| `/playtimeadmin setdisplayrank <player> <rank>` | 🏷️ Set display rank for any player |
| `/playtimeadmin cleanup [dryrun]` | 🧹 Run inactivity cleanup |
| `/playtimeadmin backup now` | 💾 Create manual backup |
| `/playtimeadmin reload` | 🔄 Hot-reload rank config |
| `/playtimeadmin import <file>` | 📥 Import from legacy KubeJS data |

**Flexible time formats:** `1h30m`, `2d4h`, `45m`, `90s`, or even decimal hours like `2.5`.

---

### 📢 Rank-Up Effects
Make rank-ups feel **epic**:

- 🎉 **Server broadcast** — Everyone knows when someone ranks up
- 🎵 **Custom sound** — Configurable sound effect on rank-up
- 📝 **Title display** — Big on-screen title with configurable fade timings
- ⚡ **Custom commands** — Run any commands on rank-up (rewards, effects, etc.)

---

## 📋 Player Commands

| Command | What It Does |
|---------|-------------|
| `/playtime` | 📊 View your playtime stats, current rank, and progress |
| `/playtime top [page]` | 🏆 Server-wide playtime leaderboard |
| `/playtime displayrank set <name>` | 🏷️ Set your display rank |
| `/playtime displayrank clear` | ❌ Clear your display rank |
| `/ranks [page]` | 📜 Browse all ranks with thresholds and rewards |

---

## ⚙️ Configuration

Everything is configurable via `config/playtime.toml`:

- 😴 **AFK settings** — Timeout, sensitivity, signal thresholds
- 💾 **Save intervals** — How often data is flushed and written
- 🔄 **Backup schedule** — Enable/disable, check intervals
- 🧹 **Cleanup settings** — Enable/disable, startup delay
- 🔗 **Integrations** — Toggle LuckPerms and OpenPAC independently
- 🎉 **Rank-up effects** — Sound, volume, pitch, title timings, broadcasts
- 🛡️ **Permissions** — Admin command permission level
- 📄 **Page sizes** — Customize `/ranks` and `/playtime top` pagination
- 💬 **Chat formatting** — Full control over the integrated chat formatter

Ranks are configured in `<world>/playtime/ranks.json` — editable in-game or by hand.

---

## 📦 Installation

1. Requires **Minecraft 1.20.1** with **Forge 47.x**
2. Drop `playtime-1.0.jar` into your server's `mods/` folder
3. Start the server — config and data directories are created automatically
4. *(Optional)* Install [LuckPerms](https://luckperms.net/) for group syncing
5. *(Optional)* Install [Open Parties and Claims](https://www.curseforge.com/minecraft/mc-mods/open-parties-and-claims) for claim management

> 🖥️ **Server-side only!** Clients do NOT need to install this mod. Players can connect without it.

---

## 🔧 Compatibility

| Mod | Integration |
|-----|-------------|
| ✅ [LuckPerms](https://luckperms.net/) | Group sync, prefix colors, display rank suffixes |
| ✅ [Open Parties and Claims](https://www.curseforge.com/minecraft/mc-mods/open-parties-and-claims) | Claim limits, inactivity cleanup |
| ✅ [Better Forge Chat Reforged Reworked](https://www.curseforge.com/minecraft/mc-mods/better-forge-chat-reforged-reworked) | Hex & gradient color rendering in chat |
| ✅ OpacFixes | Enhanced claim wipe support |

All integrations are **optional** — detected at runtime and silently disabled if not present.

---

## 📊 The Rank Progression

Here's the full 33-rank journey from first login to ultimate power:

**🌱 Phase 1 — The Grounded** *(1h → 16h)*
> Starter → Explorer → Gatherer → **Settler** *(gradient underline)*

**🔮 Phase 2 — The Arcane** *(24h → 65h)*
> Apprentice → Alchemist → Sage → **Wizard** *(gradient underline)*

**⚙️ Phase 3 — The Industrial** *(80h → 140h)*
> Tinker → Machinist → Cogwright → **Steamlord** *(gradient bold)*

**🔧 Phase 4 — The Technological** *(170h → 275h)*
> Technician → Engineer → Architect → **Commander** *(gradient bold)*

**🚀 Phase 5 — The Ascent** *(320h → 485h)*
> Aviator → Astronaut → Cosmonaut → **Orbiteer** *(gradient bold)*

**🪐 Phase 6 — The Interplanetary** *(550h → 775h)*
> Spacefarer → Planetwalker → Galaxytamer → **Starseeker** *(gradient bold)*

**🌌 Phase 7 — The Cosmic Manipulators** *(860h → 1145h)*
> Riftshaper → Chronoshifter → Voidweaver → **Eclipsebringer** *(gradient bold)*

**⭐ Phase 8 — The Absolute** *(1180h → 1250h)*
> Ascendant → Celestial → Hypernova → **Singularity** *(all gradient bold)*

🏆 **Singularity** — 1,250 hours, 2,000 claims, 32 forceloads, **never** expires. The ultimate rank.

---

## ❓ FAQ

**Q: Do clients need to install this mod?**
> 🚫 No! It's entirely server-side. Clients connect normally.

**Q: Can I customize the ranks?**
> ✅ Yes! Edit `ranks.json` or use `/playtimeadmin rank` commands. Add as many ranks as you want — there's no limit.

**Q: Does AFK time count?**
> 🚫 No. The mod uses multi-signal detection. Players must show multiple types of activity (movement + rotation, interaction + hotbar change, etc.) to be tracked.

**Q: What happens if my data file gets corrupted?**
> 🛡️ The mod refuses to overwrite corrupted data and disables writes until you fix it. Automatic backups (hourly, daily, weekly) ensure you always have a recent snapshot to restore from.

**Q: Can I migrate from the old KubeJS playtime system?**
> ✅ Yes! Use `/playtimeadmin import <filepath>` to import your legacy data.

**Q: What if I don't use LuckPerms or OpenPAC?**
> 👍 The mod works perfectly standalone. Integrations are detected at runtime — if they're not installed, those features are silently skipped.

---

## 📝 License

All Rights Reserved.

---

*Made with ❤️ for the Minecraft community.*

