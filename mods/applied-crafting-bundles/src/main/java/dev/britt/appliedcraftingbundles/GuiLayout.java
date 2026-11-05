package dev.britt.appliedcraftingbundles;

/**
 * Shared screen geometry so menus and screens agree on slot hitboxes.
 * <p>
 * The wireless terminal's Provider and Encoder tabs use the same window ({@code TERM_*}). The block
 * provider is wider so its 27-slot output buffer can sit on the main view beside the patterns.
 */
public final class GuiLayout {
    /** Wireless terminal and the Bundle Encoder part. */
    public static final int TERM_W = 176, TERM_H = 240;
    public static final int TERM_PATTERN_X = 8, TERM_PATTERN_Y = 44;
    public static final int TERM_INV_X = 8, TERM_INV_Y = 158;
    public static final int TERM_IN_X = 116, TERM_OUT_X = 152, TERM_IO_Y = 116;

    public static final int TAB_X = 8, TAB_Y = 16, TAB = 22;

    public static final int TERM_LIST_X = 100, TERM_LIST_Y = 46, TERM_ROW = 11, TERM_REQ_X = 152;
    public static final int TERM_SETS_Y = 100;
    public static final int TERM_NAME_X = 8, TERM_NAME_Y = 44, TERM_NAME_W = 160;
    public static final int TERM_CELL_X = 8, TERM_CELL_Y = 70;
    public static final int TERM_HELP_Y = 90, TERM_ENCODE_X = 8, TERM_ENCODE_Y = 117;

    /** Bundle Provider block. Output slots are on this same screen, not a separate tab. */
    public static final int PROV_W = 346, PROV_H = 254;
    public static final int PROV_PATTERN_X = 8, PROV_PATTERN_Y = 56;
    public static final int PROV_OUT_X = 176, PROV_OUT_Y = 56;
    public static final int PROV_INV_X = 92, PROV_INV_Y = 172;
    public static final int PROV_LIST_X = 100, PROV_LIST_Y = 58, PROV_ROW = 11, PROV_REQ_X = 152;
    public static final int PROV_SETS_Y = 114;
    public static final int PROV_LABEL_Y = 44;
    public static final int PROV_SEND_X = 176, PROV_SEND_Y = 116, PROV_SEND_W = 120, PROV_SEND_H = 16;
    public static final int PROV_QUEUE_Y = 58, PROV_QUEUE_ROW = 18, PROV_QUEUE_ROWS = 5, PROV_CANCEL_X = 128;

    private GuiLayout() {
    }
}
