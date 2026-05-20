package com.enviouse.sef.invsee;

import com.enviouse.sef.TextFormatter;
import com.enviouse.sef.config.ConfigHandler;
import com.enviouse.sef.utils.moddeps.CuriosInventoryHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.SlotItemHandler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Editable InvSee container that directly references the target player's inventory.
 * Changes made in the GUI are immediately reflected in the target's real inventory.
 *
 * Page 0 Layout (Main Inventory):
 *   Row 0: [ArmorLabel] [Helmet] [Chest] [Legs] [Boots] [OHLabel] [Offhand] [Fill] [Fill]
 *   Row 1: [InvLabel] [Fill] [Fill] [Fill] [Fill] [Fill] [Fill] [Fill] [NextPage/Fill]
 *   Row 2: [Inv 0-8]
 *   Row 3: [Inv 9-17]
 *   Row 4: [Inv 18-26]
 *   Row 5: [Inv 27-35]
 *
 * Page 1+ Layout (Curios):
 *   Row 0: [PrevPage] [CuriosLabel] [Fill...] [NextPage/Fill]
 *   Rows 1-5: Curios slots with per-type glass pane labels, rest filled with glass
 */
public class InvSeeContainer extends AbstractContainerMenu {
    private final ServerPlayer target;
    private final ServerPlayer viewer;
    private final int page;
    private final int totalPages;
    private final boolean readOnly;
    private final SimpleContainer decorContainer;
    private final boolean[] lockedSlots = new boolean[54];
    private final int[] navTargetPage = new int[54];
    private int pendingPageChange = -1;

    /** Curios display entry: either a label (String) or a slot reference. */
    private record CuriosSlotRef(IItemHandlerModifiable handler, int handlerSlot) {}

    public InvSeeContainer(int containerId, Inventory viewerInventory, ServerPlayer target, int page) {
        super(MenuType.GENERIC_9x6, containerId);
        this.target = target;
        this.viewer = (ServerPlayer) viewerInventory.player;
        this.page = page;
        this.readOnly = ConfigHandler.config.invSeeReadOnly.get();
        this.decorContainer = new SimpleContainer(54);

        Arrays.fill(navTargetPage, -1);

        // Gather curios data
        List<CuriosInventoryHelper.CuriosSlotGroup> curiosGroups =
                CuriosInventoryHelper.getCuriosSlotGroups(target);

        // Build flat curios display list: labels (String) interleaved with slot refs
        List<Object> curiosEntries = new ArrayList<>();
        for (CuriosInventoryHelper.CuriosSlotGroup group : curiosGroups) {
            String typeLabel = "&d" + capitalize(group.slotType());
            curiosEntries.add(typeLabel);
            for (int i = 0; i < group.slotCount(); i++) {
                curiosEntries.add(new CuriosSlotRef(group.handler(), i));
            }
        }

        // Calculate total pages: page 0 = main inv, page 1+ = curios (45 per page)
        int curiosSlotsPerPage = 45; // rows 1-5 on curios pages
        int curiosPages = curiosEntries.isEmpty() ? 0
                : (int) Math.ceil((double) curiosEntries.size() / curiosSlotsPerPage);
        this.totalPages = 1 + curiosPages;

        // Build the display slots for the current page
        if (page == 0) {
            buildMainPage(target.getInventory(), !curiosEntries.isEmpty());
        } else {
            buildCuriosPage(curiosEntries, page);
        }

        // Add the viewer's own inventory (bottom 3 rows + hotbar)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(viewerInventory, col + row * 9 + 9, 8 + col * 18, 140 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(viewerInventory, col, 8 + col * 18, 198));
        }
    }

    // ==================== Page Builders ====================

    private void buildMainPage(Inventory targetInv, boolean hasCurios) {
        for (int i = 0; i < 54; i++) {
            int col = i % 9;
            int row = i / 9;
            int x = 8 + col * 18;
            int y = 18 + row * 18;

            switch (i) {
                // --- Row 0: Armor + Offhand ---
                case 0 -> addDecoSlot(i, x, y, ConfigHandler.config.invSeeArmorLabel.get());
                case 1 -> addTargetInvSlot(targetInv, 39, x, y); // Helmet
                case 2 -> addTargetInvSlot(targetInv, 38, x, y); // Chestplate
                case 3 -> addTargetInvSlot(targetInv, 37, x, y); // Leggings
                case 4 -> addTargetInvSlot(targetInv, 36, x, y); // Boots
                case 5 -> addDecoSlot(i, x, y, ConfigHandler.config.invSeeOffhandLabel.get());
                case 6 -> addTargetInvSlot(targetInv, 40, x, y); // Offhand
                case 7, 8 -> addFillerSlot(i, x, y);

                // --- Row 1: Separator row ---
                case 9 -> addDecoSlot(i, x, y, ConfigHandler.config.invSeeMainInvLabel.get());
                case 17 -> {
                    if (hasCurios) {
                        addNavSlot(i, x, y, ConfigHandler.config.invSeeNextPageLabel.get(), 1);
                    } else {
                        addFillerSlot(i, x, y);
                    }
                }

                default -> {
                    if (row == 1) {
                        // Separator filler (slots 10-16)
                        addFillerSlot(i, x, y);
                    } else if (row >= 2) {
                        // Rows 2-5: Main inventory slots 0-35
                        int invSlot = i - 18;
                        if (invSlot >= 0 && invSlot < 36) {
                            addTargetInvSlot(targetInv, invSlot, x, y);
                        } else {
                            addFillerSlot(i, x, y);
                        }
                    }
                }
            }
        }
    }

    private void buildCuriosPage(List<Object> curiosEntries, int pageNum) {
        int slotsPerPage = 45;
        int startIdx = (pageNum - 1) * slotsPerPage;

        for (int i = 0; i < 54; i++) {
            int col = i % 9;
            int row = i / 9;
            int x = 8 + col * 18;
            int y = 18 + row * 18;

            if (row == 0) {
                // Header row: [←Back] [CuriosLabel] [Fill...] [→Next/Fill]
                if (i == 0) {
                    addNavSlot(i, x, y, ConfigHandler.config.invSeePrevPageLabel.get(), pageNum - 1);
                } else if (i == 1) {
                    addDecoSlot(i, x, y, ConfigHandler.config.invSeeCuriosLabel.get());
                } else if (i == 8) {
                    if (pageNum < totalPages - 1) {
                        addNavSlot(i, x, y, ConfigHandler.config.invSeeNextPageLabel.get(), pageNum + 1);
                    } else {
                        addFillerSlot(i, x, y);
                    }
                } else {
                    addFillerSlot(i, x, y);
                }
            } else {
                // Content rows 1-5: curios slots or filler
                int entryIdx = startIdx + (i - 9);
                if (entryIdx >= 0 && entryIdx < curiosEntries.size()) {
                    Object entry = curiosEntries.get(entryIdx);
                    if (entry instanceof String label) {
                        addDecoSlot(i, x, y, label);
                    } else if (entry instanceof CuriosSlotRef ref) {
                        addCuriosSlot(ref.handler, ref.handlerSlot, x, y);
                    } else {
                        addFillerSlot(i, x, y);
                    }
                } else {
                    addFillerSlot(i, x, y);
                }
            }
        }
    }

    // ==================== Slot Creation Helpers ====================

    /** Adds a slot that directly references the target player's inventory. Editable unless readOnly. */
    private void addTargetInvSlot(Inventory targetInv, int invIndex, int x, int y) {
        if (readOnly) {
            addSlot(new LockedSlot(targetInv, invIndex, x, y));
        } else {
            addSlot(new UnrestrictedSlot(targetInv, invIndex, x, y));
        }
    }

    /** Adds a SlotItemHandler for a curios slot. Editable unless readOnly. */
    private void addCuriosSlot(IItemHandlerModifiable handler, int handlerSlot, int x, int y) {
        if (readOnly) {
            addSlot(new LockedCuriosSlot(handler, handlerSlot, x, y));
        } else {
            addSlot(new SlotItemHandler(handler, handlerSlot, x, y));
        }
    }

    /** Adds a labeled glass pane decoration slot (always locked). */
    private void addDecoSlot(int displayIdx, int x, int y, String label) {
        ItemStack pane = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        pane.setHoverName(TextFormatter.stringToFormattedText(label));
        decorContainer.setItem(displayIdx, pane);
        addSlot(new LockedSlot(decorContainer, displayIdx, x, y));
        lockedSlots[displayIdx] = true;
    }

    /** Adds an empty filler glass pane slot (always locked). */
    private void addFillerSlot(int displayIdx, int x, int y) {
        ItemStack pane = new ItemStack(Items.BLACK_STAINED_GLASS_PANE);
        pane.setHoverName(Component.literal(" "));
        decorContainer.setItem(displayIdx, pane);
        addSlot(new LockedSlot(decorContainer, displayIdx, x, y));
        lockedSlots[displayIdx] = true;
    }

    /** Adds a navigation arrow slot that triggers a page change on click. */
    private void addNavSlot(int displayIdx, int x, int y, String label, int targetPage) {
        ItemStack arrow = new ItemStack(Items.ARROW);
        arrow.setHoverName(TextFormatter.stringToFormattedText(label));
        decorContainer.setItem(displayIdx, arrow);
        addSlot(new LockedSlot(decorContainer, displayIdx, x, y));
        lockedSlots[displayIdx] = true;
        navTargetPage[displayIdx] = targetPage;
    }

    // ==================== Interaction Handling ====================

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < 54) {
            // Handle navigation arrow clicks
            if (navTargetPage[slotId] >= 0) {
                pendingPageChange = navTargetPage[slotId];
                return;
            }
            // Block all interaction on locked slots (decorations, fillers)
            if (lockedSlots[slotId]) {
                return;
            }
            // Block all target slots if readOnly
            if (readOnly) {
                return;
            }
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        // Handle deferred page navigation (runs after click processing is complete)
        if (pendingPageChange >= 0) {
            int newPage = pendingPageChange;
            pendingPageChange = -1;
            InvSeeCommand.openInvSee(viewer, target, newPage);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // Disable shift-click to prevent items going to wrong slots
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return target != null && target.isAlive() && !target.hasDisconnected();
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    // ==================== Custom Slot Types ====================

    /** Slot that blocks all pickup and placement (for decorations, nav, and readOnly mode). */
    private static class LockedSlot extends Slot {
        public LockedSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) { return false; }

        @Override
        public boolean mayPickup(Player player) { return false; }
    }

    /** Slot that allows any item in any slot (overrides armor slot restrictions for admin editing). */
    private static class UnrestrictedSlot extends Slot {
        public UnrestrictedSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) { return true; }
    }

    /** Curios SlotItemHandler that blocks interaction in readOnly mode. */
    private static class LockedCuriosSlot extends SlotItemHandler {
        public LockedCuriosSlot(IItemHandlerModifiable handler, int index, int x, int y) {
            super(handler, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) { return false; }

        @Override
        public boolean mayPickup(Player player) { return false; }
    }
}

