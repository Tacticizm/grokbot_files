package dev.britt.appliedcraftingbundles;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Rules for the encoder's input and output pattern slots (shared by the part and the wireless terminal). */
public final class BundleEncoding {
    public static SimpleContainer newContainer(Runnable onChange) {
        return new SimpleContainer(2) {
            @Override
            public boolean canPlaceItem(int slot, ItemStack stack) {
                return stack.is(ModItems.BUNDLE_PATTERN.get());
            }

            @Override
            public void setChanged() {
                super.setChanged();
                onChange.run();
            }
        };
    }

    public static boolean isBlank(ItemStack s) {
        return s.is(ModItems.BUNDLE_PATTERN.get()) && !s.has(ModComponents.BUNDLE_CONTENTS.get());
    }

    public static boolean isEncoded(ItemStack s) {
        return s.is(ModItems.BUNDLE_PATTERN.get()) && s.has(ModComponents.BUNDLE_CONTENTS.get());
    }

    /**
     * Write {@code contents}:
     * a blank in the output slot gets a copy (from a stack of blanks, the copy goes to the player);
     * otherwise an encoded pattern in the input slot is re-encoded in place;
     * otherwise one blank from the input slot is encoded into the output slot (or the player's inventory).
     * Returns false if there was nothing to write to.
     */
    public static boolean encode(Container c, int in, int out, Player player, BundleContents contents) {
        if (contents.isEmpty()) {
            return false;
        }
        ItemStack input = c.getItem(in), output = c.getItem(out);
        ItemStack result = BundlePatternItem.encoded(contents);
        if (isBlank(output)) {
            if (output.getCount() == 1) {
                c.setItem(out, result);
            } else {
                output.shrink(1);
                give(player, result);
            }
        } else if (isEncoded(input)) {
            c.setItem(in, result);
        } else if (isBlank(input)) {
            input.shrink(1);
            if (output.isEmpty()) {
                c.setItem(out, result);
            } else {
                give(player, result);
            }
        } else {
            return false;
        }
        c.setChanged();
        return true;
    }

    private static void give(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private BundleEncoding() {
    }
}
