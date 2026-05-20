package com.enviouse.sef.chat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.enviouse.sef.ServerEssentialsForge;
import com.enviouse.sef.TextFormatter;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Op-bulletin system: stores messages that are shown to operators on login.
 */
public class OpBulletinHandler {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<String>>(){}.getType();
    private static final List<String> bulletins = new ArrayList<>();
    private static Path filePath;

    public static void init(MinecraftServer server) {
        Path dir = server.getServerDirectory().toPath().resolve("serverconfig").resolve("sef");
        filePath = dir.resolve("bulletin.json");
        load();
    }

    private static void load() {
        bulletins.clear();
        if (filePath == null || !Files.exists(filePath)) return;
        try (Reader reader = Files.newBufferedReader(filePath)) {
            List<String> loaded = GSON.fromJson(reader, LIST_TYPE);
            if (loaded != null) bulletins.addAll(loaded);
            ServerEssentialsForge.LOGGER.info("[SEF] Loaded {} bulletin(s)", bulletins.size());
        } catch (Exception e) {
            ServerEssentialsForge.LOGGER.error("[SEF] Failed to load bulletins", e);
        }
    }

    private static void save() {
        if (filePath == null) return;
        try {
            Files.createDirectories(filePath.getParent());
            try (Writer writer = Files.newBufferedWriter(filePath)) {
                GSON.toJson(bulletins, writer);
            }
        } catch (IOException e) {
            ServerEssentialsForge.LOGGER.error("[SEF] Failed to save bulletins", e);
        }
    }

    public static void showBulletins(ServerPlayer player) {
        if (bulletins.isEmpty()) return;
        if (!player.hasPermissions(2)) return; // op-level 2+
        player.sendSystemMessage(TextFormatter.stringToFormattedText("&6━━━━━━━━ Op Bulletin ━━━━━━━━"));
        for (String b : bulletins) {
            player.sendSystemMessage(TextFormatter.stringToFormattedText("&7• " + b));
        }
        player.sendSystemMessage(TextFormatter.stringToFormattedText("&6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("opbulletin")
            .requires(src -> src.hasPermission(2))
            .then(Commands.literal("add")
                .then(Commands.argument("message", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        String msg = StringArgumentType.getString(ctx, "message");
                        bulletins.add(msg);
                        save();
                        ctx.getSource().sendSuccess(() -> TextFormatter.stringToFormattedText(
                            "&aAdded bulletin: &7" + msg), false);
                        return 1;
                    })))
            .then(Commands.literal("remove")
                .then(Commands.argument("index", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                    .executes(ctx -> {
                        int index = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "index") - 1;
                        if (index < 0 || index >= bulletins.size()) {
                            ctx.getSource().sendFailure(TextFormatter.stringToFormattedText("&cInvalid index"));
                            return 0;
                        }
                        String removed = bulletins.remove(index);
                        save();
                        ctx.getSource().sendSuccess(() -> TextFormatter.stringToFormattedText(
                            "&aRemoved bulletin: &7" + removed), false);
                        return 1;
                    })))
            .then(Commands.literal("list")
                .executes(ctx -> {
                    if (bulletins.isEmpty()) {
                        ctx.getSource().sendSuccess(() -> TextFormatter.stringToFormattedText("&7No bulletins configured"), false);
                        return 1;
                    }
                    ctx.getSource().sendSuccess(() -> TextFormatter.stringToFormattedText("&6━━━━━━━━ Op Bulletins ━━━━━━━━"), false);
                    for (int i = 0; i < bulletins.size(); i++) {
                        final int idx = i + 1;
                        final String b = bulletins.get(i);
                        ctx.getSource().sendSuccess(() -> TextFormatter.stringToFormattedText("&e" + idx + ". &7" + b), false);
                    }
                    return 1;
                }))
            .then(Commands.literal("clear")
                .executes(ctx -> {
                    bulletins.clear();
                    save();
                    ctx.getSource().sendSuccess(() -> TextFormatter.stringToFormattedText("&aAll bulletins cleared"), false);
                    return 1;
                })));
    }
}

