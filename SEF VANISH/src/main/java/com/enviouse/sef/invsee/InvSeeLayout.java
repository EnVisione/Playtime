package com.enviouse.sef.invsee;

import com.enviouse.sef.TextFormatter;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Utility class for InvSee GUI display items.
 * Layout logic has been moved into {@link InvSeeContainer}.
 */
public class InvSeeLayout {

    /**
     * Creates a labeled gray glass pane separator.
     */
    public static ItemStack makeSeparator(String name) {
        ItemStack pane = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        pane.setHoverName(TextFormatter.stringToFormattedText(name));
        return pane;
    }

    /**
     * Creates a navigation arrow item.
     */
    public static ItemStack makeNavArrow(String name) {
        ItemStack arrow = new ItemStack(Items.ARROW);
        arrow.setHoverName(TextFormatter.stringToFormattedText(name));
        return arrow;
    }

    /**
     * Creates a filler black glass pane for empty slots.
     */
    public static ItemStack makeFiller() {
        ItemStack pane = new ItemStack(Items.BLACK_STAINED_GLASS_PANE);
        pane.setHoverName(Component.literal(" "));
        return pane;
    }
}
