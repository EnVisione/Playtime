package com.enviouse.playtime.command;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;

/**
 * Thin wrappers around Brigadier's own builder factories.
 * <p>
 * Minecraft's {@code Commands.literal()} and {@code Commands.argument()} are
 * convenience wrappers that go through Mojang obfuscation (official → SRG).
 * If the mod jar isn't perfectly reobfuscated the call sites break at runtime
 * with {@code NoSuchMethodError}.
 * <p>
 * Using Brigadier's API directly sidesteps the issue entirely because
 * Brigadier is a separate, never-obfuscated library.
 */
public final class Cmd {

    private Cmd() {}

    /** Drop-in replacement for {@code Commands.literal(name)}. */
    public static LiteralArgumentBuilder<CommandSourceStack> literal(String name) {
        return LiteralArgumentBuilder.literal(name);
    }

    /** Drop-in replacement for {@code Commands.argument(name, type)}. */
    public static <T> RequiredArgumentBuilder<CommandSourceStack, T> argument(String name, ArgumentType<T> type) {
        return RequiredArgumentBuilder.argument(name, type);
    }
}

