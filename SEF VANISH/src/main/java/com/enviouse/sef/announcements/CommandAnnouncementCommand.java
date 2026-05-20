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
 * /commandannouncement add|remove|list — schedules server commands to run on an interval.
 */
public class CommandAnnouncementCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, AnnouncementManager manager) {
        dispatcher.register(Commands.literal("commandannouncement")
            // add <id> <interval> <command...>
            .then(Commands.literal("add")
                .requires(CommandAnnouncementCommand::hasManage)
                .then(Commands.argument("id", StringArgumentType.word())
                    .then(Commands.argument("interval", StringArgumentType.word())
                        .then(Commands.argument("command", StringArgumentType.greedyString())
                            .executes(ctx -> doAdd(ctx, manager))))))
            // remove <id>
            .then(Commands.literal("remove")
                .requires(CommandAnnouncementCommand::hasManage)
                .then(Commands.argument("id", StringArgumentType.word())
                    .suggests((ctx, b) -> { manager.getAnnouncements().stream().filter(a -> "command".equalsIgnoreCase(a.type())).forEach(a -> b.suggest(a.id())); return b.buildFuture(); })
                    .executes(ctx -> doRemove(ctx, manager))))
            // list [page]
            .then(Commands.literal("list")
                .requires(CommandAnnouncementCommand::hasManage)
                .executes(ctx -> doList(ctx, manager, 1))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                    .executes(ctx -> doList(ctx, manager, IntegerArgumentType.getInteger(ctx, "page"))))));
    }

    private static boolean hasManage(CommandSourceStack src) {
        try {
            return PermissionsHandler.playerHasPermission(
                src.getPlayerOrException().getUUID(), PermissionsHandler.announcementManage);
        } catch (Exception e) {
            return src.hasPermission(2);
        }
    }

    private static int doAdd(CommandContext<CommandSourceStack> ctx, AnnouncementManager manager) {
        String id = StringArgumentType.getString(ctx, "id");
        String intervalStr = StringArgumentType.getString(ctx, "interval");
        String command = StringArgumentType.getString(ctx, "command");

        long seconds = DurationParser.parseSeconds(intervalStr);
        if (seconds < 1) {
            ctx.getSource().sendFailure(TextFormatter.stringToFormattedText(
                "&cInvalid interval: &e" + intervalStr + "&c. Use formats like 30S, 5M, 1H, 1H30M15S."));
            return 0;
        }

        AnnouncementManager.Announcement a = new AnnouncementManager.Announcement(
            id, "command", command, seconds, false, "@server", true, 0);
        if (!manager.add(a)) {
            ctx.getSource().sendFailure(TextFormatter.stringToFormattedText(
                "&cAn announcement with id &e" + id + "&c already exists."));
            return 0;
        }
        String confirm = ConfigHandler.config.announcementConfirmFormat.get()
            .replace("$id", id)
            .replace("$interval", DurationParser.humanReadable(seconds))
            .replace("$message", "/" + command);
        ctx.getSource().sendSuccess(() -> TextFormatter.stringToFormattedText(confirm), false);
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
            .filter(a -> "command".equalsIgnoreCase(a.type())).toList();
        int perPage = 8;
        int totalPages = Math.max(1, (int) Math.ceil(all.size() / (double) perPage));
        final int shownPage = Math.min(page, totalPages);
        int from = (shownPage - 1) * perPage;
        int to = Math.min(all.size(), from + perPage);

        ctx.getSource().sendSuccess(() -> TextFormatter.stringToFormattedText(
            ConfigHandler.config.announcementListHeaderCmd.get() + " &7(page " + shownPage + "/" + totalPages + ")"), false);
        if (all.isEmpty()) {
            ctx.getSource().sendSuccess(() -> TextFormatter.stringToFormattedText("&7  (none)"), false);
            return 1;
        }
        for (int i = from; i < to; i++) {
            AnnouncementManager.Announcement a = all.get(i);
            String status = a.enabled() ? "&a[ON]" : "&c[OFF]";
            String line = "  &e" + a.id() + " " + status + " &7(" + DurationParser.humanReadable(a.intervalSeconds()) + ") &r/" + a.message();
            ctx.getSource().sendSuccess(() -> TextFormatter.stringToFormattedText(line), false);
        }
        return 1;
    }
}
