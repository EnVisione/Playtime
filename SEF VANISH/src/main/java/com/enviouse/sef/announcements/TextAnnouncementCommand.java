package com.enviouse.sef.announcements;

import com.enviouse.sef.TextFormatter;
import com.enviouse.sef.config.ConfigHandler;
import com.enviouse.sef.config.PermissionsHandler;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

import java.util.List;

/**
 * /textannouncement add|ontime|modify|remove|list with per-announcement
 * interval, toggleable flag, target, and message (supports <br> for multi-line).
 */
public class TextAnnouncementCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, AnnouncementManager manager) {
        dispatcher.register(Commands.literal("textannouncement")
            // add <id> <interval> <toggle|notoggle> <target> <message...>
            .then(Commands.literal("add")
                .requires(src -> hasManage(src))
                .then(Commands.argument("id", StringArgumentType.word())
                    .then(Commands.argument("interval", StringArgumentType.word())
                        .then(Commands.argument("toggleable", StringArgumentType.word())
                            .suggests((ctx, b) -> { b.suggest("toggle"); b.suggest("notoggle"); return b.buildFuture(); })
                            .then(Commands.argument("target", StringArgumentType.word())
                                .suggests((ctx, b) -> { b.suggest("@a"); b.suggest("@server"); return b.buildFuture(); })
                                .then(Commands.argument("message", StringArgumentType.greedyString())
                                    .executes(TextAnnouncementCommand::doAdd)))))))
            // ontime <message...>  — fires once immediately to all
            .then(Commands.literal("ontime")
                .requires(src -> hasManage(src))
                .then(Commands.argument("message", StringArgumentType.greedyString())
                    .executes(ctx -> doOntime(ctx, manager))))
            // modify <id> <interval> <toggle|notoggle> <target> <message...>
            .then(Commands.literal("modify")
                .requires(src -> hasManage(src))
                .then(Commands.argument("id", StringArgumentType.word())
                    .suggests((ctx, b) -> { manager.getAnnouncements().stream().filter(a -> "text".equalsIgnoreCase(a.type())).forEach(a -> b.suggest(a.id())); return b.buildFuture(); })
                    .then(Commands.argument("interval", StringArgumentType.word())
                        .then(Commands.argument("toggleable", StringArgumentType.word())
                            .suggests((ctx, b) -> { b.suggest("toggle"); b.suggest("notoggle"); return b.buildFuture(); })
                            .then(Commands.argument("target", StringArgumentType.word())
                                .suggests((ctx, b) -> { b.suggest("@a"); b.suggest("@server"); return b.buildFuture(); })
                                .then(Commands.argument("message", StringArgumentType.greedyString())
                                    .executes(ctx -> doModify(ctx, manager))))))))
            // remove <id>
            .then(Commands.literal("remove")
                .requires(src -> hasManage(src))
                .then(Commands.argument("id", StringArgumentType.word())
                    .suggests((ctx, b) -> { manager.getAnnouncements().stream().filter(a -> "text".equalsIgnoreCase(a.type())).forEach(a -> b.suggest(a.id())); return b.buildFuture(); })
                    .executes(ctx -> doRemove(ctx, manager))))
            // list [page]
            .then(Commands.literal("list")
                .requires(src -> hasManage(src))
                .executes(ctx -> doList(ctx, manager, 1))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                    .executes(ctx -> doList(ctx, manager, IntegerArgumentType.getInteger(ctx, "page"))))));
        // re-capture manager reference for add executor (since .executes captured outer manager)
        // (we register add twice to avoid passing the manager via a lambda field — kept single-register above
        //  by closing over manager via inner class — but the static doAdd above needs manager via context)
    }

    private static boolean hasManage(CommandSourceStack src) {
        try {
            return PermissionsHandler.playerHasPermission(
                src.getPlayerOrException().getUUID(), PermissionsHandler.announcementManage);
        } catch (Exception e) {
            return src.hasPermission(2); // console / OP fallback
        }
    }

    // --- Executors ---

    private static AnnouncementManager capturedManager;
    public static void setManager(AnnouncementManager m) { capturedManager = m; }

    private static int doAdd(CommandContext<CommandSourceStack> ctx) {
        AnnouncementManager manager = capturedManager;
        if (manager == null) {
            ctx.getSource().sendFailure(TextFormatter.stringToFormattedText("&cAnnouncement manager not initialised"));
            return 0;
        }
        String id = StringArgumentType.getString(ctx, "id");
        String intervalStr = StringArgumentType.getString(ctx, "interval");
        String toggleStr = StringArgumentType.getString(ctx, "toggleable").toLowerCase();
        String target = StringArgumentType.getString(ctx, "target");
        String message = StringArgumentType.getString(ctx, "message");

        long seconds = DurationParser.parseSeconds(intervalStr);
        if (seconds < 1) {
            ctx.getSource().sendFailure(TextFormatter.stringToFormattedText(
                "&cInvalid interval: &e" + intervalStr + "&c. Use formats like 30S, 5M, 1H, 1H30M15S."));
            return 0;
        }
        if (!"toggle".equals(toggleStr) && !"notoggle".equals(toggleStr)) {
            ctx.getSource().sendFailure(TextFormatter.stringToFormattedText(
                "&cToggleable must be &etoggle&c or &enotoggle&c. Got: &e" + toggleStr));
            return 0;
        }
        boolean toggleable = "toggle".equals(toggleStr);

        AnnouncementManager.Announcement a = new AnnouncementManager.Announcement(
            id, "text", message, seconds, toggleable, target, true, 0);
        if (!manager.add(a)) {
            ctx.getSource().sendFailure(TextFormatter.stringToFormattedText(
                "&cAn announcement with id &e" + id + "&c already exists."));
            return 0;
        }

        String confirm = ConfigHandler.config.announcementConfirmFormat.get()
            .replace("$id", id)
            .replace("$interval", DurationParser.humanReadable(seconds))
            .replace("$message", message);
        ctx.getSource().sendSuccess(() -> TextFormatter.stringToFormattedText(confirm), false);
        return 1;
    }

    private static int doOntime(CommandContext<CommandSourceStack> ctx, AnnouncementManager manager) {
        String message = StringArgumentType.getString(ctx, "message");
        var server = ctx.getSource().getServer();
        manager.broadcastText(server, message, "@a", null, false);
        ctx.getSource().sendSuccess(() -> TextFormatter.stringToFormattedText("&aOne-time announcement sent."), false);
        return 1;
    }

    private static int doModify(CommandContext<CommandSourceStack> ctx, AnnouncementManager manager) {
        String id = StringArgumentType.getString(ctx, "id");
        String intervalStr = StringArgumentType.getString(ctx, "interval");
        String toggleStr = StringArgumentType.getString(ctx, "toggleable").toLowerCase();
        String target = StringArgumentType.getString(ctx, "target");
        String message = StringArgumentType.getString(ctx, "message");

        long seconds = DurationParser.parseSeconds(intervalStr);
        if (seconds < 1) {
            ctx.getSource().sendFailure(TextFormatter.stringToFormattedText(
                "&cInvalid interval: &e" + intervalStr));
            return 0;
        }
        if (!"toggle".equals(toggleStr) && !"notoggle".equals(toggleStr)) {
            ctx.getSource().sendFailure(TextFormatter.stringToFormattedText(
                "&cToggleable must be &etoggle&c or &enotoggle&c."));
            return 0;
        }
        boolean toggleable = "toggle".equals(toggleStr);

        if (!manager.modify(id, seconds, toggleable, target, message)) {
            ctx.getSource().sendFailure(TextFormatter.stringToFormattedText(
                "&cNo text announcement with id &e" + id));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> TextFormatter.stringToFormattedText(
            "&aModified announcement: &e" + id + " &7(every " + DurationParser.humanReadable(seconds) + ")"), false);
        return 1;
    }

    private static int doRemove(CommandContext<CommandSourceStack> ctx, AnnouncementManager manager) {
        String id = StringArgumentType.getString(ctx, "id");
        if (!manager.remove(id)) {
            ctx.getSource().sendFailure(TextFormatter.stringToFormattedText(
                "&cNo announcement with id &e" + id));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> TextFormatter.stringToFormattedText("&aRemoved announcement: &e" + id), false);
        return 1;
    }

    private static int doList(CommandContext<CommandSourceStack> ctx, AnnouncementManager manager, int page) {
        List<AnnouncementManager.Announcement> all = manager.getAnnouncements().stream()
            .filter(a -> "text".equalsIgnoreCase(a.type())).toList();
        int perPage = 8;
        int totalPages = Math.max(1, (int) Math.ceil(all.size() / (double) perPage));
        final int shownPage = Math.min(page, totalPages);
        int from = (shownPage - 1) * perPage;
        int to = Math.min(all.size(), from + perPage);

        ctx.getSource().sendSuccess(() -> TextFormatter.stringToFormattedText(
            ConfigHandler.config.announcementListHeaderText.get() + " &7(page " + shownPage + "/" + totalPages + ")"), false);
        if (all.isEmpty()) {
            ctx.getSource().sendSuccess(() -> TextFormatter.stringToFormattedText("&7  (none)"), false);
            return 1;
        }
        for (int i = from; i < to; i++) {
            AnnouncementManager.Announcement a = all.get(i);
            String status = a.enabled() ? "&a[ON]" : "&c[OFF]";
            String tog = a.toggleable() ? "&b[toggle]" : "&8[notoggle]";
            String line = "  &e" + a.id() + " " + status + " " + tog + " &7(" + DurationParser.humanReadable(a.intervalSeconds()) + " → " + a.target() + ") &r" + a.message();
            ctx.getSource().sendSuccess(() -> TextFormatter.stringToFormattedText(line), false);
        }
        return 1;
    }
}
