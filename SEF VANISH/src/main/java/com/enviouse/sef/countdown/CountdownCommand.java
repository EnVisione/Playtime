package com.enviouse.sef.countdown;

import com.enviouse.sef.TextFormatter;
import com.enviouse.sef.config.PermissionsHandler;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/**
 * <pre>
 * /countdown &lt;time&gt;
 * /countdown &lt;time&gt; &lt;message&gt;
 * /countdown &lt;time&gt; &lt;message&gt; &lt;color&gt;
 * /countdown &lt;time&gt; &lt;message&gt; &lt;color&gt; &lt;chat_too&gt;
 * </pre>
 *
 * <p>Time accepts {@code 90}, {@code 1m30s}, {@code 1h}, {@code 1d2h30m}.
 * Color accepts vanilla legacy codes ({@code &c}) and hex ({@code &#FF5500} or
 * the bare {@code #FF5500} — {@link TextFormatter} resolves both). Message
 * supports either an unquoted single word or a quoted multi-word phrase via
 * Brigadier's {@code StringArgumentType.string()}.
 */
public final class CountdownCommand {

    private CountdownCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("countdown")
            .requires(CountdownCommand::hasPerm)
            .then(Commands.argument("time", StringArgumentType.word())
                .executes(ctx -> exec(ctx, "Countdown", "&c", false))
                .then(Commands.argument("message", StringArgumentType.string())
                    .executes(ctx -> exec(ctx, msg(ctx), "&c", false))
                    .then(Commands.argument("color", StringArgumentType.string())
                        .suggests((c, b) -> {
                            for (String s : new String[]{
                                "&c", "&a", "&b", "&e", "&6", "&d", "&5", "&f", "&7",
                                "&#FF5500", "&#00AAFF", "#FFAA00"
                            }) b.suggest(s);
                            return b.buildFuture();
                        })
                        .executes(ctx -> exec(ctx, msg(ctx), color(ctx), false))
                        .then(Commands.argument("chat_too", BoolArgumentType.bool())
                            .executes(ctx -> exec(ctx, msg(ctx), color(ctx),
                                BoolArgumentType.getBool(ctx, "chat_too")))))));
        dispatcher.register(root);
    }

    private static boolean hasPerm(CommandSourceStack src) {
        try {
            return PermissionsHandler.playerHasPermission(
                src.getPlayerOrException().getUUID(), PermissionsHandler.countdownCommand);
        } catch (Exception e) {
            return src.hasPermission(2);
        }
    }

    private static String msg(CommandContext<CommandSourceStack> ctx) {
        try { return StringArgumentType.getString(ctx, "message"); }
        catch (IllegalArgumentException e) { return "Countdown"; }
    }

    private static String color(CommandContext<CommandSourceStack> ctx) {
        try { return StringArgumentType.getString(ctx, "color"); }
        catch (IllegalArgumentException e) { return "&c"; }
    }

    private static int exec(CommandContext<CommandSourceStack> ctx, String message,
                            String colorCode, boolean alsoChat) {
        String timeStr = StringArgumentType.getString(ctx, "time");
        long seconds = CountdownManager.parseDurationSeconds(timeStr);
        if (seconds < 1L) {
            ctx.getSource().sendFailure(TextFormatter.stringToFormattedText(
                "&cInvalid duration: &e" + timeStr + " &7(use 90, 1m30s, 1h, 1d2h30m, etc.)"));
            return 0;
        }
        if (ctx.getSource().getServer() == null) {
            ctx.getSource().sendFailure(TextFormatter.stringToFormattedText("&cNo server context."));
            return 0;
        }
        CountdownManager.start(ctx.getSource().getServer(), seconds, message, colorCode, alsoChat);
        ctx.getSource().sendSuccess(() -> TextFormatter.stringToFormattedText(
            "&aCountdown started: &e" + CountdownManager.humanRemaining(seconds)
                + " &7| message: &f" + message
                + " &7| color: &f" + colorCode
                + " &7| chat: &f" + alsoChat), true);
        return 1;
    }
}
