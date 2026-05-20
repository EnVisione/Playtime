package com.enviouse.sef.commands;

import java.util.Arrays;

import com.enviouse.sef.ServerEssentialsForge;
import com.enviouse.sef.TextFormatter;
import com.enviouse.sef.config.ConfigHandler;
import com.enviouse.sef.config.ConfigurationEventHandler;
import com.enviouse.sef.config.PermissionsHandler;
import com.enviouse.sef.filter.FilterManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraftforge.server.permission.nodes.PermissionNode;

public class BfcCommands {
	private static final Iterable<String> sefSubCommands = Arrays.asList(
			"info", "colors", "test", "reload"
	);

	private static FilterManager filterManager;

	protected static boolean checkPermission(CommandSourceStack c, PermissionNode<Boolean> node) {
		try {
			return PermissionsHandler.playerHasPermission(c.getPlayerOrException().getUUID(), node);
		} catch(CommandSyntaxException e) {
			// Not a player (console or rcon)
			return true;
		}
	}
	protected static boolean checkContextPermission(CommandContext<CommandSourceStack> c, PermissionNode<Boolean> node) {
		return checkPermission(c.getSource(), node);
	}
	protected static int failNoPermission(CommandContext<CommandSourceStack> ctx) {
		ctx.getSource().sendFailure(TextFormatter.stringToFormattedText(TextFormatter.COLOR_RED + "You don't have permission to run this command" + TextFormatter.RESET_ALL_FORMAT));
		return 0;
	}
	
	public static void register(CommandDispatcher<CommandSourceStack> disp) {
		disp.register(Commands.literal("sef")
			.requires(c -> checkPermission(c, PermissionsHandler.sefCommand))
			.then(Commands.argument("mode", StringArgumentType.greedyString())
					.suggests((context, builder) -> SharedSuggestionProvider.suggest(sefSubCommands, builder))
					.executes(BfcCommands::modCommand)));
		if(ConfigHandler.config.enableColorsCommand.get()) {
			disp.register(Commands.literal("colors")
				.requires(c -> checkPermission(c, PermissionsHandler.coloredChatNode))
				.executes(BfcCommands::colorCommand));
		}

		// Register filter commands if enabled
		if(ConfigHandler.config.enableFilterSystem.get()) {
			registerFilterCommands(disp);
		}

		NickCommands.register(disp);
	}
	
	public static void initFilterManager(FilterManager manager) {
		filterManager = manager;
	}


	private static void registerFilterCommands(CommandDispatcher<CommandSourceStack> disp) {
		SuggestionProvider<CommandSourceStack> caseSensitiveSuggest = (c, b) -> {
			b.suggest("yes");
			b.suggest("no");
			return b.buildFuture();
		};

		SuggestionProvider<CommandSourceStack> filterIdSuggest = (c, b) -> {
			if(filterManager != null) {
				filterManager.list().keySet().forEach(b::suggest);
			}
			return b.buildFuture();
		};

		disp.register(Commands.literal("sef")
			.requires(src -> src.hasPermission(2))
			.then(Commands.literal("filter")
				// /sef filter add <id> <caseSensitive yes/no> <wordToFilter> [replacement]
				.then(Commands.literal("add")
					.then(Commands.argument("id", StringArgumentType.word())
						.then(Commands.argument("caseSensitive", StringArgumentType.word()).suggests(caseSensitiveSuggest)
							.then(Commands.argument("wordToFilter", StringArgumentType.string())
								// With replacement
								.then(Commands.argument("replacement", StringArgumentType.greedyString())
									.executes(ctx -> filterAdd(ctx, false)))
								// Without replacement (just remove the word)
								.executes(ctx -> filterAdd(ctx, true))))))
				// /sef filter remove <id>
				.then(Commands.literal("remove")
					.then(Commands.argument("id", StringArgumentType.word())
						.suggests(filterIdSuggest)
						.executes(BfcCommands::filterRemove)))
				// /sef filter list
				.then(Commands.literal("list")
					.executes(ctx -> filterList(ctx, 1))
					.then(Commands.argument("page", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
						.executes(ctx -> filterList(ctx, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "page")))))));
	}

	private static int filterAdd(CommandContext<CommandSourceStack> ctx, boolean noReplacement) {
		if(filterManager == null) {
			ctx.getSource().sendFailure(TextFormatter.stringToFormattedText("&cFilter system not initialized"));
			return 0;
		}

		String id = StringArgumentType.getString(ctx, "id");
		String caseSensitiveStr = StringArgumentType.getString(ctx, "caseSensitive").toLowerCase();
		String wordToFilter = StringArgumentType.getString(ctx, "wordToFilter");
		String replacement = noReplacement ? "" : StringArgumentType.getString(ctx, "replacement");

		boolean caseSensitive = caseSensitiveStr.equals("yes") || caseSensitiveStr.equals("true");

		filterManager.addFilter(id, wordToFilter, replacement, caseSensitive);

		ctx.getSource().sendSuccess(() -> TextFormatter.stringToFormattedText("&aAdded filter: &e" + id), false);
		ctx.getSource().sendSuccess(() -> TextFormatter.stringToFormattedText("&7  Word: &f" + wordToFilter), false);
		ctx.getSource().sendSuccess(() -> TextFormatter.stringToFormattedText("&7  Replacement: &f" + (replacement.isEmpty() ? "(removed)" : replacement)), false);
		ctx.getSource().sendSuccess(() -> TextFormatter.stringToFormattedText("&7  Case sensitive: &f" + caseSensitive), false);
		return 1;
	}

	private static int filterRemove(CommandContext<CommandSourceStack> ctx) {
		if(filterManager == null) {
			ctx.getSource().sendFailure(TextFormatter.stringToFormattedText("&cFilter system not initialized"));
			return 0;
		}

		String id = StringArgumentType.getString(ctx, "id");
		boolean removed = filterManager.removeFilter(id);

		if(removed) {
			ctx.getSource().sendSuccess(() -> TextFormatter.stringToFormattedText("&aRemoved filter: &e" + id), false);
		} else {
			ctx.getSource().sendFailure(TextFormatter.stringToFormattedText("&cFilter not found: " + id));
		}
		return removed ? 1 : 0;
	}

	private static final int FILTER_ITEMS_PER_PAGE = 5; // 5 items * 3 lines = 15 + header/footer = ~18 lines

	private static int filterList(CommandContext<CommandSourceStack> ctx, int page) {
		if(filterManager == null) {
			ctx.getSource().sendFailure(TextFormatter.stringToFormattedText("&cFilter system not initialized"));
			return 0;
		}

		var filters = filterManager.list();
		if(filters.isEmpty()) {
			ctx.getSource().sendSuccess(() -> TextFormatter.stringToFormattedText("&6━━━━━━━━ Word Filters ━━━━━━━━"), false);
			ctx.getSource().sendSuccess(() -> TextFormatter.stringToFormattedText("&7No filters configured"), false);
			ctx.getSource().sendSuccess(() -> TextFormatter.stringToFormattedText("&6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"), false);
			return 1;
		}

		java.util.List<java.util.Map.Entry<String, com.enviouse.sef.filter.FilterDataStore.FilterRecord>> filterList = new java.util.ArrayList<>(filters.entrySet());
		int totalPages = (int) Math.ceil((double) filterList.size() / FILTER_ITEMS_PER_PAGE);
		if(page > totalPages) page = totalPages;
		if(page < 1) page = 1;

		int startIdx = (page - 1) * FILTER_ITEMS_PER_PAGE;
		int endIdx = Math.min(startIdx + FILTER_ITEMS_PER_PAGE, filterList.size());

		ctx.getSource().sendSuccess(() -> TextFormatter.stringToFormattedText("&6━━━━━━━━ Word Filters ━━━━━━━━"), false);

		for(int i = startIdx; i < endIdx; i++) {
			var entry = filterList.get(i);
			String id = entry.getKey();
			var rec = entry.getValue();
			String caseSensitive = rec.caseSensitive() ? "&a[case-sensitive]" : "&e[case-insensitive]";
			String replacement = rec.replacement().isEmpty() ? "&c(removed)" : "&f" + rec.replacement();
			ctx.getSource().sendSuccess(() -> TextFormatter.stringToFormattedText("&e" + id + " " + caseSensitive), false);
			ctx.getSource().sendSuccess(() -> TextFormatter.stringToFormattedText("  &7Word: &f" + rec.wordToFilter()), false);
			ctx.getSource().sendSuccess(() -> TextFormatter.stringToFormattedText("  &7Replacement: " + replacement), false);
		}

		// Footer with navigation arrows
		final int currentPage = page;
		final int finalTotalPages = totalPages;

		net.minecraft.network.chat.MutableComponent footer = TextFormatter.stringToFormattedText("&6━━━━");

		// Left arrow (previous page)
		if(currentPage > 1) {
			net.minecraft.network.chat.MutableComponent leftArrow = TextFormatter.stringToFormattedText("&e&l[◄]");
			leftArrow = leftArrow.withStyle(style -> style
				.withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/sef filter list " + (currentPage - 1)))
				.withHoverEvent(new net.minecraft.network.chat.HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
					TextFormatter.stringToFormattedText("&7Previous page"))));
			footer = footer.append(leftArrow);
		} else {
			footer = footer.append(TextFormatter.stringToFormattedText("&8[◄]"));
		}

		footer = footer.append(TextFormatter.stringToFormattedText("&6━━&f" + currentPage + "/" + finalTotalPages + "&6━━"));

		// Right arrow (next page)
		if(currentPage < finalTotalPages) {
			net.minecraft.network.chat.MutableComponent rightArrow = TextFormatter.stringToFormattedText("&e&l[►]");
			rightArrow = rightArrow.withStyle(style -> style
				.withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/sef filter list " + (currentPage + 1)))
				.withHoverEvent(new net.minecraft.network.chat.HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
					TextFormatter.stringToFormattedText("&7Next page"))));
			footer = footer.append(rightArrow);
		} else {
			footer = footer.append(TextFormatter.stringToFormattedText("&8[►]"));
		}

		footer = footer.append(TextFormatter.stringToFormattedText("&6━━━━"));

		final net.minecraft.network.chat.MutableComponent finalFooter = footer;
		ctx.getSource().sendSuccess(() -> finalFooter, false);

		return 1;
	}

	public static int modCommand(CommandContext<CommandSourceStack> ctx) {
		String arg = StringArgumentType.getString(ctx, "mode");
            switch (arg) {
                case "colors" -> {
                    if(checkContextPermission(ctx, PermissionsHandler.sefCommandColorsSubCommand))
                        return colorCommand(ctx);
                    else return failNoPermission(ctx);
                }
                case "info" -> {
                    if(checkContextPermission(ctx, PermissionsHandler.sefCommandInfoSubCommand)) {
                        boolean hasMetaProv = ServerEssentialsForge.instance.metadataProvider != null;
                        boolean hasNickProv = ServerEssentialsForge.instance.nicknameProvider != null;
                        String metaProvName = hasMetaProv ? ServerEssentialsForge.instance.metadataProvider.getProviderName() : "";
                        String nickProvName = hasNickProv ? ServerEssentialsForge.instance.nicknameProvider.getProviderName() : "";
                        if(hasMetaProv) metaProvName = " (via " + metaProvName + ")";
                        if(hasNickProv) nickProvName = " (via " + nickProvName + ")";
						String finalMetaProvName = metaProvName;
						String finalNickProvName = nickProvName;
						ctx.getSource().sendSuccess(() ->TextFormatter.stringToFormattedText(
                                ServerEssentialsForge.CHAT_ID_STR + "\n&eMod ID: &d" + ServerEssentialsForge.MODID + "    &r&eMod version: &d" + ServerEssentialsForge.VERSION + " (forge)&r\n\n"
                                        + (hasMetaProv ? "&a&lWITH" : "&c&lWITHOUT") + "&r&e metadata integration" + finalMetaProvName + "&r\n"
                                        + (hasNickProv ? "&a&lWITH" : "&c&lWITHOUT") + "&r&e nickname integration" + finalNickProvName + "&r\n"), false);
                        return 1;
                    } else return failNoPermission(ctx);
                }
                case "test" -> {
                    ctx.getSource().sendSuccess(() ->TextFormatter.stringToFormattedText(
                            ServerEssentialsForge.CHAT_ID_STR
                                    + "&eColors & Styling internal debug test&r\n"
                                            + "Normal &lBold&r &nUnderline&r &oItalic&r &mStrikthrough&r &kObfuscated&r &rReset\n"
                                            + "Normal &lBold &nUnderline &oItalic &mStrikthrough &kObfuscated &rReset"), false);
                    return 1;
                }
                case "reload" -> {
                    if(checkContextPermission(ctx, PermissionsHandler.sefCommandReloadSubCommand)) {
                        ConfigHandler.reloadFromDisk();
                        ConfigurationEventHandler.reloadConfigOptions();
                        ctx.getSource().sendSuccess(() -> TextFormatter.stringToFormattedText("&aServerEssentialsForge config reloaded."), false);
                        return 1;
                    } else return failNoPermission(ctx);
                }
                default -> {
                    return 0;
                }
            }
	}
	public static int colorCommand(CommandContext<CommandSourceStack> ctx) {
		ctx.getSource().sendSuccess(() ->
				TextFormatter.stringToFormattedText(
				ServerEssentialsForge.CHAT_ID_STR + TextFormatter.colorString()), false);
		return 1;
	}
}
