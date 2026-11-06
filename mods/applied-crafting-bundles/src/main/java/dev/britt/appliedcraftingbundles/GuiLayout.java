package dev.britt.appliedcraftingbundles;

/**
 * Shared screen geometry so menus and screens agree on slot hitboxes.
 * Encoded patterns are one row across the top. The selected pattern's name and its one Craft button
 * sit in the space left of the provider output. Encoder ghost cells are unchanged.
 */
public final class GuiLayout {
    public static final int TAB_X = 8, TAB_Y = 16, TAB = 22;

    /** Wireless terminal and the Bundle Encoder part share this window. */
    public static final int TERM_W = 324, TERM_H = 308;
    /** Nine slots are 162px. Center them the way a chest centers its rows in the window. */
    public static final int INV_W = 9 * 18;
    public static final int TERM_PATTERN_X = 8, TERM_PATTERN_Y = 42;
    public static final int TERM_INV_X = (TERM_W - INV_W) / 2, TERM_INV_Y = 226;
    public static final int TERM_IN_X = 116, TERM_OUT_X = 152, TERM_IO_Y = 116;
    public static final int TERM_NAME_X = 22, TERM_NAME_Y = 44, TERM_NAME_W = 160;
    public static final int TERM_BUNDLE_X = 8, TERM_BUNDLE_Y = 74;
    public static final int TERM_CRAFT_X = 248, TERM_CRAFT_Y = 70, TERM_CRAFT_W = 68, TERM_CRAFT_H = 16;
    public static final int TERM_SETS_Y = 96;
    public static final int TERM_CELL_X = 8, TERM_CELL_Y = 70;
    public static final int TERM_HELP_Y = 90, TERM_ENCODE_X = 8, TERM_ENCODE_Y = 117;

    /**
     * Block provider. Nine pattern slots in one row. One selected name and one Craft button
     * use the column left of the 27-slot output.
     */
    public static final int PROV_W = 448, PROV_H = 252;
    public static final int PROV_PATTERN_X = 8, PROV_PATTERN_Y = 42;
    public static final int PROV_OUT_X = 278, PROV_OUT_Y = 78;
    public static final int PROV_INV_X = (PROV_W - INV_W) / 2, PROV_INV_Y = 168;
    public static final int PROV_NAME_X = 8, PROV_NAME_Y = 82;
    public static final int PROV_CRAFT_X = 206, PROV_CRAFT_Y = 78, PROV_CRAFT_W = 64, PROV_CRAFT_H = 16;
    public static final int PROV_SETS_Y = 108;
    public static final int PROV_LABEL_Y = 64;
    public static final int PROV_SEND_X = 278, PROV_SEND_Y = 136, PROV_SEND_W = 162, PROV_SEND_H = 16;
    public static final int PROV_QUEUE_Y = 78, PROV_QUEUE_ROW = 18, PROV_QUEUE_ROWS = 4, PROV_CANCEL_X = 206;

    private GuiLayout() {
    }
}
