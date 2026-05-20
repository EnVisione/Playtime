package com.enviouse.sef.banned;

import com.enviouse.sef.TextFormatter;
import com.enviouse.sef.config.ConfigHandler;
import com.enviouse.sef.events.CommandRegistrationHandler;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Event-driven enforcement for banned items/blocks.
 *
 * <p>Companion to {@link BannedItemsManager}'s periodic sweep — these handlers
 * provide instant feedback at the moment of interaction, so players don't get
 * to even briefly hold or place a banned item before the next sweep tick.
 */
@Mod.EventBusSubscriber
public class BannedItemsEventHandler {

    private static BannedItemsManager mgr() {
        return CommandRegistrationHandler.getBannedItemsManager();
    }

    private static boolean enabled() {
        return ConfigHandler.config.enableBannedItems.get();
    }

    /**
     * Block placement: cancel for non-bypassed players, auto-add as exception
     * when a creative/bypassed admin places a banned block. Runs at HIGHEST so
     * we beat any other mod that might consume the event.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent ev) {
        if (!enabled()) return;
        BannedItemsManager m = mgr();
        if (m == null || !m.isBlocksEnabled()) return;
        Entity entity = ev.getEntity();
        BlockState placed = ev.getPlacedBlock();
        BannedEntry hit = m.matchBlock(placed);
        if (hit == null) return;

        ResourceLocation blockRl = ForgeRegistries.BLOCKS.getKey(placed.getBlock());
        String blockId = blockRl != null ? blockRl.toString() : "unknown";

        if (entity instanceof ServerPlayer player) {
            if (m.isBypassed(player)) {
                // creative/bypass placement is permitted — record the exception
                if (ev.getLevel() instanceof ServerLevel sl) {
                    BlockPos pos = ev.getPos();
                    m.addException(sl, pos, blockId, player.getGameProfile().getName());
                }
                return;
            }
            // Confiscate any held copies of the offending item too
            ItemStack mainHand = player.getMainHandItem();
            if (m.matchItem(mainHand) != null) {
                player.getInventory().setItem(player.getInventory().selected, ItemStack.EMPTY);
            }
            ev.setCanceled(true);
            String fmt = ConfigHandler.config.bannedItemRemovedMsg.get();
            if (fmt == null || fmt.isEmpty()) {
                fmt = "&cYou cannot place that block here. &7(banned: &e$item&7)";
            }
            String text = fmt.replace("$item", blockId)
                    .replace("$reason", hit.reason == null ? "" : hit.reason)
                    .replace("$by", hit.bannedBy == null ? "" : hit.bannedBy)
                    .replace("$remaining", hit.getRemainingString());
            player.sendSystemMessage(TextFormatter.stringToFormattedText(text));
        } else {
            // non-player placer (dispenser, falling block, etc.) — just cancel
            ev.setCanceled(true);
        }
    }

    /** Block right-click with a banned item in hand: cancel before it acts. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock ev) {
        if (!enabled()) return;
        BannedItemsManager m = mgr();
        if (m == null || !m.isItemsEnabled()) return;
        if (!(ev.getEntity() instanceof ServerPlayer player)) return;
        if (m.isBypassed(player)) return;
        BannedEntry hit = m.matchItem(ev.getItemStack());
        if (hit != null) {
            ev.setCanceled(true);
            // a tick later the periodic sweep will scoop the stack — this just blocks the action
        }
    }

    /** Right-click in air with a banned item: cancel. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem ev) {
        if (!enabled()) return;
        BannedItemsManager m = mgr();
        if (m == null || !m.isItemsEnabled()) return;
        if (!(ev.getEntity() instanceof ServerPlayer player)) return;
        if (m.isBypassed(player)) return;
        if (m.matchItem(ev.getItemStack()) != null) ev.setCanceled(true);
    }

    /** Prevent eating / drawing bows / etc. with a banned item. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onUseItem(LivingEntityUseItemEvent.Start ev) {
        if (!enabled()) return;
        BannedItemsManager m = mgr();
        if (m == null || !m.isItemsEnabled()) return;
        if (!(ev.getEntity() instanceof ServerPlayer player)) return;
        if (m.isBypassed(player)) return;
        if (m.matchItem(ev.getItem()) != null) ev.setCanceled(true);
    }

    /** Prevent picking up banned items off the ground (they'd just be confiscated). */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPickup(EntityItemPickupEvent ev) {
        if (!enabled()) return;
        BannedItemsManager m = mgr();
        if (m == null || !m.isItemsEnabled()) return;
        ServerPlayer player = (ev.getEntity() instanceof ServerPlayer sp) ? sp : null;
        if (player == null || m.isBypassed(player)) return;
        ItemStack stack = ev.getItem().getItem();
        if (m.matchItem(stack) != null) {
            ev.setCanceled(true);
            // discard the dropped item so it doesn't sit forever
            ev.getItem().discard();
        }
    }

    /**
     * If a vanilla block break happens to land on an excepted block, drop the
     * exception entry so it doesn't accumulate forever. Runs after the break.
     */
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent ev) {
        if (!enabled()) return;
        BannedItemsManager m = mgr();
        if (m == null) return;
        if (!(ev.getLevel() instanceof ServerLevel sl)) return;
        BlockPos pos = ev.getPos();
        if (!m.isExcepted(sl, pos)) return;
        // remove by scanning — the list is small
        var list = new java.util.ArrayList<>(m.getExceptions());
        for (int i = 0; i < list.size(); i++) {
            BannedExceptedBlock b = list.get(i);
            if (b.matches(sl.dimension().location().toString(), pos.getX(), pos.getY(), pos.getZ())) {
                m.removeExceptionAt(i);
                break;
            }
        }
    }

    @SuppressWarnings("unused") // kept for future hover/tooltip interactions
    private static Component fmt(String s) {
        return TextFormatter.stringToFormattedText(s);
    }
}
