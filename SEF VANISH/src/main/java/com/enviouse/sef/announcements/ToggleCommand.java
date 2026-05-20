package com.enviouse.sef.announcements;

import com.enviouse.sef.TextFormatter;
import com.enviouse.sef.config.ConfigHandler;
import com.enviouse.sef.config.PermissionsHandler;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * /toggle           — list toggleable announcements with ON/OFF state
 * /toggle &lt;id&gt;  — toggle one announcement for the invoking player
 */
public class ToggleCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, AnnouncementManager manager) {
        dispatcher.register(Commands.literal("toggle")
            .executes(ctx -> doList(ctx, manager))
            .then(Commands.argument("id", StringArgumentType.word())
                .suggests((ctx, b) -> { manager.getToggleable().forEach(a -> b.suggest(a.id())); return b.buildFuture(); })
                .executes(ctx -> doToggle(ctx, manager))));
    }

    private static int doList(CommandContext<CommandSourceStack> ctx, AnnouncementManager manager) {
        ServerPlayer player;
        try {
            player = ctx.getSource().getPlayerOrException();
        } catch (Exception e) {
            ctx.getSource().sendFailure(TextFormatter.stringToFormattedText(
                "&c/toggle can only be used by players."));
            return 0;
        }
        if (!PermissionsHandler.playerHasPermission(player.getUUID(), PermissionsHandler.announcementToggle)) {
            ctx.getSource().sendFailure(TextFormatter.stringToFormattedText(ConfigHandler.config.noPermissionMsg.get()));
            return 0;
        }
        List<AnnouncementManager.Announcement> toggleable = manager.getToggleable();
        ctx.getSource().sendSuccess(() -> TextFormatter.stringToFormattedText(
            ConfigHandler.config.toggleListHeader.get()), false);
        if (toggleable.isEmpty()) {
            ctx.getSource().sendSuccess(() -> TextFormatter.stringToFormattedText("&7  (no toggleable announcements)"), false);
            return 1;
        }
        for (AnnouncementManager.Announcement a : toggleable) {
            boolean off = manager.isToggledOff(player.getUUID(), a.id());
            String state = off ? ConfigHandler.config.toggleOffText.get() : ConfigHandler.config.toggleOnText.get();
            ctx.getSource().sendSuccess(() -> TextFormatter.stringToFormattedText(
                "  &e" + a.id() + " " + state + " &7- use &f/toggle " + a.id()), false);
        }
        return 1;
    }

    private static int doToggle(CommandContext<CommandSourceStack> ctx, AnnouncementManager manager) {
        ServerPlayer player;
        try {
            player = ctx.getSource().getPlayerOrException();
        } catch (Exception e) {
            ctx.getSource().sendFailure(TextFormatter.stringToFormattedText(
                "&c/toggle can only be used by players."));
            return 0;
        }
        if (!PermissionsHandler.playerHasPermission(player.getUUID(), PermissionsHandler.announcementToggle)) {
            ctx.getSource().sendFailure(TextFormatter.stringToFormattedText(ConfigHandler.config.noPermissionMsg.get()));
            return 0;
        }
        String id = StringArgumentType.getString(ctx, "id");
        AnnouncementManager.Announcement a = manager.getById(id);
        if (a == null || !"text".equalsIgnoreCase(a.type()) || !a.toggleable()) {
            ctx.getSource().sendFailure(TextFormatter.stringToFormattedText(
                "&cNo toggleable announcement with id &e" + id));
            return 0;
        }
        boolean nowOn = manager.togglePlayer(player.getUUID(), id);
        String text = nowOn
            ? ConfigHandler.config.toggleOnText.get() + " &7Enabled announcement: &e" + id
            : ConfigHandler.config.toggleOffText.get() + " &7Disabled announcement: &e" + id;
        ctx.getSource().sendSuccess(() -> TextFormatter.stringToFormattedText(text), false);
        return 1;
    }
}
