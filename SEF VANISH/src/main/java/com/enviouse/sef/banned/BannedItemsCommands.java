package com.enviouse.sef.banned;

import com.enviouse.sef.TextFormatter;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Registers the {@code /banned} command suite.
 *
 * <pre>
 * /banned                                 — list view (everyone, hover-tooltip)
 * /banned add &lt;item&gt; [duration] [announce] [reason...]
 * /banned remove &lt;item&gt;
 * /banned update &lt;item&gt; [duration] [announce] [reason...]
 * /banned list
 * /banned clear
 * /banned reload
 * /banned setradius &lt;n&gt;
 * /banned setinterval &lt;ticks&gt;
 * /banned toggle &lt;items|blocks|drops|all&gt;
 * /banned bypass &lt;player&gt; &lt;on|off&gt;
 * /banned scan &lt;player&gt;
 * /banned excepted
 * /banned excepted remove &lt;index&gt;
 * /banned excepted clear
 * </pre>
 *
 * <p>All mutating subcommands require op (level 2). The bare {@code /banned} and
 * {@code /banned list} are available to everyone for read-only inspection.
 */
public class BannedItemsCommands {
    private static BannedItemsManager manager;

    public static void setManager(BannedItemsManager mgr) { manager = mgr; }

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_ALL_ITEMS =
        (ctx, b) -> {
            String input = b.getRemainingLowerCase();
            for (String id : manager.suggestAllItems()) {
                if (id.toLowerCase().contains(input)) b.suggest(id);
            }
            return b.buildFuture();
        };

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_BANNED =
        (ctx, b) -> {
            for (BannedEntry e : manager.getEntries().values()) {
                if (e.pattern != null) b.suggest(e.pattern);
            }
            return b.buildFuture();
        };

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_DURATION =
        (ctx, b) -> {
            for (String s : new String[]{"infinite", "permanent", "30s", "5m", "1h", "1h30m", "1d"})
                b.suggest(s);
            return b.buildFuture();
        };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("banned")
            .executes(ctx -> doList(ctx.getSource())); // /banned → list view (everyone)

        // /banned list (alias of bare command)
        root.then(Commands.literal("list").executes(ctx -> doList(ctx.getSource())));

        // ── Mutations: op-only ──────────────────────────────────────────────
        // /banned add <item> [duration] [announce] [reason...]
        root.then(Commands.literal("add").requires(BannedItemsCommands::isOp)
            .then(Commands.argument("item", StringArgumentType.word())
                .suggests(SUGGEST_ALL_ITEMS)
                .executes(ctx -> doAdd(ctx, "infinite", false, ""))
                .then(Commands.argument("duration", StringArgumentType.word())
                    .suggests(SUGGEST_DURATION)
                    .executes(ctx -> doAdd(ctx, dur(ctx), false, ""))
                    .then(Commands.argument("announce", BoolArgumentType.bool())
                        .executes(ctx -> doAdd(ctx, dur(ctx), BoolArgumentType.getBool(ctx, "announce"), ""))
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                            .executes(ctx -> doAdd(ctx, dur(ctx),
                                BoolArgumentType.getBool(ctx, "announce"),
                                StringArgumentType.getString(ctx, "reason"))))))));

        // /banned addhand [duration] [announce] [reason...]
        // Bans whatever item the executing player holds in their main hand.
        root.then(Commands.literal("addhand").requires(BannedItemsCommands::isOp)
            .executes(ctx -> doAddHand(ctx, "infinite", false, ""))
            .then(Commands.argument("duration", StringArgumentType.word())
                .suggests(SUGGEST_DURATION)
                .executes(ctx -> doAddHand(ctx, dur(ctx), false, ""))
                .then(Commands.argument("announce", BoolArgumentType.bool())
                    .executes(ctx -> doAddHand(ctx, dur(ctx), BoolArgumentType.getBool(ctx, "announce"), ""))
                    .then(Commands.argument("reason", StringArgumentType.greedyString())
                        .executes(ctx -> doAddHand(ctx, dur(ctx),
                            BoolArgumentType.getBool(ctx, "announce"),
                            StringArgumentType.getString(ctx, "reason")))))));

        // /banned remove <item>
        root.then(Commands.literal("remove").requires(BannedItemsCommands::isOp)
            .then(Commands.argument("item", StringArgumentType.greedyString())
                .suggests(SUGGEST_BANNED)
                .executes(ctx -> doRemove(ctx.getSource(), StringArgumentType.getString(ctx, "item")))));

        // /banned update <item> [duration] [announce] [reason...]
        root.then(Commands.literal("update").requires(BannedItemsCommands::isOp)
            .then(Commands.argument("item", StringArgumentType.word())
                .suggests(SUGGEST_BANNED)
                .executes(ctx -> doUpdate(ctx, null, null, null))
                .then(Commands.argument("duration", StringArgumentType.word())
                    .suggests(SUGGEST_DURATION)
                    .executes(ctx -> doUpdate(ctx, dur(ctx), null, null))
                    .then(Commands.argument("announce", BoolArgumentType.bool())
                        .executes(ctx -> doUpdate(ctx, dur(ctx), BoolArgumentType.getBool(ctx, "announce"), null))
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                            .executes(ctx -> doUpdate(ctx, dur(ctx),
                                BoolArgumentType.getBool(ctx, "announce"),
                                StringArgumentType.getString(ctx, "reason"))))))));

        // /banned clear
        root.then(Commands.literal("clear").requires(BannedItemsCommands::isOp)
            .executes(ctx -> {
                int n = manager.clearAll();
                ctx.getSource().sendSuccess(() -> fmt("&aCleared &e" + n + "&a banned entries."), true);
                return n;
            }));

        // /banned reload
        root.then(Commands.literal("reload").requires(BannedItemsCommands::isOp)
            .executes(ctx -> {
                manager.reload(ctx.getSource().getServer());
                ctx.getSource().sendSuccess(() -> fmt("&aBanned items reloaded from disk."), true);
                return 1;
            }));

        // /banned setradius <n>
        root.then(Commands.literal("setradius").requires(BannedItemsCommands::isOp)
            .then(Commands.argument("n", IntegerArgumentType.integer(1, 64))
                .executes(ctx -> {
                    int n = IntegerArgumentType.getInteger(ctx, "n");
                    manager.setRadiusOverride(n);
                    ctx.getSource().sendSuccess(() -> fmt("&aBanned-block scan radius set to &e" + n + "&a."), true);
                    return 1;
                })));

        // /banned setinterval <ticks>
        root.then(Commands.literal("setinterval").requires(BannedItemsCommands::isOp)
            .then(Commands.argument("ticks", IntegerArgumentType.integer(1, 24000))
                .executes(ctx -> {
                    int n = IntegerArgumentType.getInteger(ctx, "ticks");
                    manager.setIntervalOverride(n);
                    ctx.getSource().sendSuccess(() -> fmt("&aBanned-block scan interval set to &e" + n + "&a ticks."), true);
                    return 1;
                })));

        // /banned toggle <items|blocks|drops|all>
        root.then(Commands.literal("toggle").requires(BannedItemsCommands::isOp)
            .then(Commands.literal("items").executes(ctx -> {
                manager.setItemsEnabled(!manager.isItemsEnabled());
                ctx.getSource().sendSuccess(() -> fmt("&aItem scanning: " + onOff(manager.isItemsEnabled())), true);
                return 1;
            }))
            .then(Commands.literal("blocks").executes(ctx -> {
                manager.setBlocksEnabled(!manager.isBlocksEnabled());
                ctx.getSource().sendSuccess(() -> fmt("&aBlock scanning: " + onOff(manager.isBlocksEnabled())), true);
                return 1;
            }))
            .then(Commands.literal("drops").executes(ctx -> {
                manager.setDropOnDestroy(!manager.isDropOnDestroy());
                ctx.getSource().sendSuccess(() -> fmt("&aDrop on destroy: " + onOff(manager.isDropOnDestroy())), true);
                return 1;
            }))
            .then(Commands.literal("all").executes(ctx -> {
                boolean both = !(manager.isItemsEnabled() && manager.isBlocksEnabled());
                manager.setItemsEnabled(both);
                manager.setBlocksEnabled(both);
                ctx.getSource().sendSuccess(() -> fmt("&aAll banned scans: " + onOff(both)), true);
                return 1;
            })));

        // /banned bypass <player> <on|off>
        root.then(Commands.literal("bypass").requires(BannedItemsCommands::isOp)
            .then(Commands.argument("player", EntityArgument.player())
                .then(Commands.literal("on").executes(ctx -> {
                    ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                    boolean changed = manager.setBypass(p.getUUID(), true);
                    ctx.getSource().sendSuccess(() -> fmt(changed
                        ? "&aBypass &2enabled &afor &e" + p.getGameProfile().getName()
                        : "&7" + p.getGameProfile().getName() + " already had bypass."), true);
                    return 1;
                }))
                .then(Commands.literal("off").executes(ctx -> {
                    ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                    boolean changed = manager.setBypass(p.getUUID(), false);
                    ctx.getSource().sendSuccess(() -> fmt(changed
                        ? "&aBypass &cdisabled &afor &e" + p.getGameProfile().getName()
                        : "&7" + p.getGameProfile().getName() + " did not have bypass."), true);
                    return 1;
                }))));

        // /banned scan <player>
        root.then(Commands.literal("scan").requires(BannedItemsCommands::isOp)
            .then(Commands.argument("player", EntityArgument.player())
                .executes(ctx -> {
                    ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                    int n = manager.forceScan(p);
                    ctx.getSource().sendSuccess(() -> fmt(
                        "&aForced scan on &e" + p.getGameProfile().getName() + "&a — removed &e" + n + "&a item(s)."), true);
                    return n;
                })));

        // /banned excepted [remove <index> | clear]
        LiteralArgumentBuilder<CommandSourceStack> excepted = Commands.literal("excepted")
            .requires(BannedItemsCommands::isOp)
            .executes(ctx -> doListExcepted(ctx.getSource()));
        excepted.then(Commands.literal("remove")
            .then(Commands.argument("index", IntegerArgumentType.integer(0))
                .executes(ctx -> {
                    int idx = IntegerArgumentType.getInteger(ctx, "index");
                    boolean ok = manager.removeExceptionAt(idx);
                    ctx.getSource().sendSuccess(() -> fmt(ok
                        ? "&aRemoved exception #" + idx
                        : "&cNo exception at index " + idx), true);
                    return ok ? 1 : 0;
                })));
        excepted.then(Commands.literal("clear")
            .executes(ctx -> {
                int n = manager.clearExceptions();
                ctx.getSource().sendSuccess(() -> fmt("&aCleared &e" + n + "&a exception(s)."), true);
                return n;
            }));
        root.then(excepted);

        dispatcher.register(root);
    }

    // ── Subcommand impls ────────────────────────────────────────────────────

    private static int doList(CommandSourceStack source) {
        var entries = manager.getEntries();
        if (entries.isEmpty()) {
            source.sendSuccess(() -> fmt("&7No banned items configured."), false);
            return 0;
        }
        source.sendSuccess(() -> fmt("&6━━━━━━━━ Banned Items &7(" + entries.size() + ") &6━━━━━━━━"), false);
        for (BannedEntry e : entries.values()) {
            source.sendSuccess(() -> formatEntryLine(e), false);
        }
        return entries.size();
    }

    private static int doAdd(CommandContext<CommandSourceStack> ctx, String durationStr,
                             boolean announce, String reason) {
        String item = StringArgumentType.getString(ctx, "item");
        long durMs = parseDurationMs(durationStr);
        String issuer = sourceName(ctx.getSource());
        boolean added = manager.addBan(item, reason == null ? "" : reason, durMs, issuer, announce);
        if (!added) {
            ctx.getSource().sendFailure(fmt("&cAlready banned: &e" + item));
            return 0;
        }
        BannedEntry e = manager.getEntry(item);
        ctx.getSource().sendSuccess(() -> fmt("&aBanned &e" + item
            + "&a — &7duration: &f" + (e != null ? e.getDurationString() : durationStr)
            + " &7| announce: &f" + announce
            + " &7| reason: &f" + (reason == null || reason.isEmpty() ? "—" : reason)), true);
        return 1;
    }

    private static int doAddHand(CommandContext<CommandSourceStack> ctx, String durationStr,
                                 boolean announce, String reason) {
        ServerPlayer player;
        try {
            player = ctx.getSource().getPlayerOrException();
        } catch (Exception e) {
            ctx.getSource().sendFailure(fmt("&c/banned addhand can only be used by a player."));
            return 0;
        }
        net.minecraft.world.item.ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            ctx.getSource().sendFailure(fmt("&cYour main hand is empty — nothing to ban."));
            return 0;
        }
        net.minecraft.resources.ResourceLocation rl =
            net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (rl == null) {
            ctx.getSource().sendFailure(fmt("&cCould not resolve registry id for the held item."));
            return 0;
        }
        String item = rl.toString();
        long durMs = parseDurationMs(durationStr);
        String issuer = sourceName(ctx.getSource());
        boolean added = manager.addBan(item, reason == null ? "" : reason, durMs, issuer, announce);
        if (!added) {
            ctx.getSource().sendFailure(fmt("&cAlready banned: &e" + item));
            return 0;
        }
        BannedEntry e = manager.getEntry(item);
        ctx.getSource().sendSuccess(() -> fmt("&aBanned (from hand) &e" + item
            + "&a — &7duration: &f" + (e != null ? e.getDurationString() : durationStr)
            + " &7| announce: &f" + announce
            + " &7| reason: &f" + (reason == null || reason.isEmpty() ? "—" : reason)), true);
        return 1;
    }

    private static int doRemove(CommandSourceStack source, String item) {
        if (manager.removeBan(item)) {
            source.sendSuccess(() -> fmt("&aRemoved banned entry: &e" + item), true);
            return 1;
        }
        source.sendFailure(fmt("&cItem not in banned list: " + item));
        return 0;
    }

    private static int doUpdate(CommandContext<CommandSourceStack> ctx,
                                String durationStr, Boolean announce, String reason) {
        String item = StringArgumentType.getString(ctx, "item");
        Long durMs = durationStr == null ? null : parseDurationMs(durationStr);
        boolean ok = manager.updateBan(item, reason, durMs, announce);
        if (!ok) {
            ctx.getSource().sendFailure(fmt("&cNot banned (cannot update): &e" + item));
            return 0;
        }
        BannedEntry e = manager.getEntry(item);
        ctx.getSource().sendSuccess(() -> fmt("&aUpdated &e" + item
            + "&a — &7duration: &f" + (e != null ? e.getDurationString() : "(unchanged)")
            + " &7| announce: &f" + (e != null ? e.announce : "(unchanged)")
            + " &7| reason: &f" + (e != null && !e.reason.isEmpty() ? e.reason : "—")), true);
        return 1;
    }

    private static int doListExcepted(CommandSourceStack source) {
        List<BannedExceptedBlock> list = manager.getExceptions();
        if (list.isEmpty()) {
            source.sendSuccess(() -> fmt("&7No excepted blocks recorded."), false);
            return 0;
        }
        source.sendSuccess(() -> fmt("&6━━━━ Banned-block Exceptions &7(" + list.size() + ") &6━━━━"), false);
        for (int i = 0; i < list.size(); i++) {
            BannedExceptedBlock b = list.get(i);
            int idx = i;
            source.sendSuccess(() -> formatExceptedLine(idx, b), false);
        }
        source.sendSuccess(() -> fmt("&7Use &f/banned excepted remove <index>&7 to drop one."), false);
        return list.size();
    }

    // ── Formatting ──────────────────────────────────────────────────────────

    /** A single line in /banned list with a hover tooltip. */
    private static MutableComponent formatEntryLine(BannedEntry e) {
        String label = e.pattern + (e.isPermanent() ? "" : " (" + e.getRemainingString() + ")");
        MutableComponent line = (MutableComponent) TextFormatter.stringToFormattedText("  &7• &f" + label);

        MutableComponent tooltip = Component.empty()
            .append(Component.literal("Banned item ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(e.pattern).withStyle(ChatFormatting.YELLOW))
            .append(Component.literal("\n"))
            .append(Component.literal("By: ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(e.bannedBy == null ? "?" : e.bannedBy).withStyle(ChatFormatting.WHITE))
            .append(Component.literal("\n"))
            .append(Component.literal("Reason: ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(e.reason == null || e.reason.isEmpty() ? "—" : e.reason).withStyle(ChatFormatting.WHITE))
            .append(Component.literal("\n"))
            .append(Component.literal("Duration: ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(e.getDurationString()).withStyle(ChatFormatting.WHITE))
            .append(Component.literal("\n"))
            .append(Component.literal(e.isPermanent() ? "Status: Permanent" : "Time left: " + e.getRemainingString())
                .withStyle(e.isPermanent() ? ChatFormatting.RED : ChatFormatting.AQUA))
            .append(Component.literal("\n"))
            .append(Component.literal("Announce on hit: ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(String.valueOf(e.announce)).withStyle(ChatFormatting.WHITE));

        line.setStyle(line.getStyle().withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, tooltip)));
        return line;
    }

    /** A single line in /banned excepted with a click-to-tp action. */
    private static MutableComponent formatExceptedLine(int index, BannedExceptedBlock b) {
        String posStr = b.x + " " + b.y + " " + b.z;
        String tpCmd = "/execute in " + b.dimension + " run tp @s " + posStr;
        MutableComponent line = (MutableComponent) TextFormatter.stringToFormattedText(
            "  &7#" + index + " &f" + b.itemId + " &7at &e" + posStr + " &7(" + b.dimension + ")");

        MutableComponent tooltip = Component.empty()
            .append(Component.literal("Click to teleport").withStyle(ChatFormatting.AQUA))
            .append(Component.literal("\n"))
            .append(Component.literal("Placed by: ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(b.addedBy == null ? "?" : b.addedBy).withStyle(ChatFormatting.WHITE));

        Style style = line.getStyle()
            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, tpCmd))
            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, tooltip));
        line.setStyle(style);
        return line;
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private static boolean isOp(CommandSourceStack src) {
        return src.hasPermission(2);
    }

    private static String sourceName(CommandSourceStack src) {
        try { return src.getPlayerOrException().getGameProfile().getName(); }
        catch (Exception e) { return "Console"; }
    }

    private static String dur(CommandContext<CommandSourceStack> ctx) {
        try { return StringArgumentType.getString(ctx, "duration"); }
        catch (IllegalArgumentException e) { return "infinite"; }
    }

    private static String onOff(boolean v) {
        return v ? "&2enabled" : "&cdisabled";
    }

    private static MutableComponent fmt(String s) {
        return (MutableComponent) TextFormatter.stringToFormattedText(s);
    }

    /**
     * Parses durations like {@code 30s}, {@code 5m}, {@code 1h30m}, {@code 2d12h},
     * {@code infinite}/{@code permanent}/{@code inf}/{@code forever}/{@code perm}.
     * Returns milliseconds, or {@code -1} for infinite. Plain numbers are seconds.
     */
    public static long parseDurationMs(String input) {
        if (input == null || input.isEmpty()) return -1L;
        String s = input.trim().toLowerCase();
        if (s.equals("infinite") || s.equals("inf") || s.equals("forever")
                || s.equals("perm") || s.equals("permanent")) return -1L;
        long total = 0L;
        long current = 0L;
        boolean sawAnyDigit = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') {
                current = current * 10 + (c - '0');
                sawAnyDigit = true;
            } else {
                long mult;
                switch (c) {
                    case 's': mult = 1000L; break;
                    case 'm': mult = 60L * 1000L; break;
                    case 'h': mult = 60L * 60L * 1000L; break;
                    case 'd': mult = 24L * 60L * 60L * 1000L; break;
                    default: return -1L; // unknown suffix → treat as bad input → infinite
                }
                total += current * mult;
                current = 0L;
            }
        }
        // bare number with no suffix — treat as seconds
        if (current > 0L && sawAnyDigit) total += current * 1000L;
        return total <= 0L ? -1L : total;
    }
}
