package com.enviouse.sef.invsee;

import com.enviouse.sef.ServerEssentialsForge;
import com.enviouse.sef.TextFormatter;
import com.enviouse.sef.config.ConfigHandler;
import com.enviouse.sef.config.PermissionsHandler;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.RootCommandNode;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * Custom /invsee command that shows a player's inventory, armor, offhand,
 * and Curios slots in a double-chest GUI with glass pane separators.
 * All slots are editable - changes sync directly to the target player's inventory.
 *
 * If FTB Essentials is detected and invSeeDisableFtbInvsee is true,
 * this command overrides FTB's /invsee by removing their command node first.
 */
public class InvSeeCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        if (!ConfigHandler.config.enableInvSee.get()) return;

        // If FTB Essentials is loaded and override is enabled, remove FTB's /invsee node
        if (ConfigHandler.config.invSeeDisableFtbInvsee.get() && ModList.get().isLoaded("ftbessentials")) {
            removeCommandNode(dispatcher, "invsee");
            ServerEssentialsForge.LOGGER.info("[SEF] Overriding FTB Essentials /invsee with sef version (Curios support)");
        }

        dispatcher.register(Commands.literal("invsee")
            .requires(src -> {
                try {
                    return PermissionsHandler.playerHasPermission(
                        src.getPlayerOrException().getUUID(), PermissionsHandler.invSeeCommand);
                } catch (Exception e) { return src.hasPermission(2); }
            })
            .then(Commands.argument("player", EntityArgument.player())
                .executes(ctx -> {
                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                    ServerPlayer viewer;
                    try {
                        viewer = ctx.getSource().getPlayerOrException();
                    } catch (Exception e) {
                        ctx.getSource().sendFailure(TextFormatter.stringToFormattedText(
                            "&c/invsee can only be used by players — it opens a GUI."));
                        return 0;
                    }
                    return openInvSee(viewer, target, 0);
                })));
    }

    /**
     * Removes a command node from the dispatcher's root using reflection.
     * This is needed to fully replace FTB's /invsee registration.
     */
    @SuppressWarnings("unchecked")
    private static void removeCommandNode(CommandDispatcher<CommandSourceStack> dispatcher, String name) {
        try {
            RootCommandNode<CommandSourceStack> root = dispatcher.getRoot();
            // Remove from 'children' map
            Field childrenField = CommandNode.class.getDeclaredField("children");
            childrenField.setAccessible(true);
            Map<String, CommandNode<CommandSourceStack>> children =
                (Map<String, CommandNode<CommandSourceStack>>) childrenField.get(root);
            children.remove(name);

            // Remove from 'literals' map
            Field literalsField = CommandNode.class.getDeclaredField("literals");
            literalsField.setAccessible(true);
            Map<String, ?> literals = (Map<String, ?>) literalsField.get(root);
            literals.remove(name);
        } catch (Exception e) {
            ServerEssentialsForge.LOGGER.warn("[SEF] Could not remove existing /invsee command node: {}", e.getMessage());
        }
    }

    /**
     * Opens the InvSee GUI for the viewer showing the target's inventory.
     * @param viewer The admin viewing the inventory
     * @param target The player whose inventory is being viewed/edited
     * @param page 0 = main inventory, 1+ = curios pages
     */
    public static int openInvSee(ServerPlayer viewer, ServerPlayer target, int page) {
        String titleStr = ConfigHandler.config.invSeeTitle.get()
            .replace("$player", target.getGameProfile().getName());
        if (page > 0) {
            titleStr += " &7(Curios)";
        }
        Component title = TextFormatter.stringToFormattedText(titleStr);

        viewer.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return title;
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player p) {
                return new InvSeeContainer(id, playerInventory, target, page);
            }
        });

        return 1;
    }
}
