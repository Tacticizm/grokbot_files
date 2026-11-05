package dev.britt.appliedcraftingbundles.client;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Wireless Provider and Encoder tabs are different menus, so Minecraft opens a new screen. Both windows
 * share a size; this also puts the new screen back on the same origin and undoes a cursor recenter.
 */
final class TabHandoff {
    private static boolean pending;
    private static boolean toEncoder;
    private static int originX, originY, guiW, guiH;
    private static long window;
    private static double cursorX, cursorY;
    private static int pinFrames;

    private TabHandoff() {
    }

    static void arm(boolean encoder, int left, int top, int width, int height) {
        var mc = Minecraft.getInstance();
        long handle = mc.getWindow().handle();
        double[] x = new double[1];
        double[] y = new double[1];
        GLFW.glfwGetCursorPos(handle, x, y);
        pending = true;
        toEncoder = encoder;
        originX = left;
        originY = top;
        guiW = width;
        guiH = height;
        window = handle;
        cursorX = x[0];
        cursorY = y[0];
        pinFrames = 0;
    }

    /** Call after {@code super.init()} and before widgets are placed. */
    static void applyOrigin(boolean encoder, ScreenOrigin screen) {
        if (!pending || toEncoder != encoder || screen.width() != guiW || screen.height() != guiH) {
            return;
        }
        screen.move(originX, originY);
        pending = false;
        pinFrames = 4;
        GLFW.glfwSetCursorPos(window, cursorX, cursorY);
    }

    /** If the new screen recentered the cursor, put it back. A real move away from center wins. */
    static void pinCursor() {
        if (pinFrames <= 0 || window == 0) {
            return;
        }
        var win = Minecraft.getInstance().getWindow();
        double[] x = new double[1];
        double[] y = new double[1];
        GLFW.glfwGetCursorPos(window, x, y);
        double cx = win.getScreenWidth() / 2.0;
        double cy = win.getScreenHeight() / 2.0;
        if (Math.abs(x[0] - cx) < 12 && Math.abs(y[0] - cy) < 12) {
            GLFW.glfwSetCursorPos(window, cursorX, cursorY);
            pinFrames--;
        } else if (Math.abs(x[0] - cursorX) < 2 && Math.abs(y[0] - cursorY) < 2) {
            pinFrames--;
        } else {
            pinFrames = 0;
        }
    }

    interface ScreenOrigin {
        int width();

        int height();

        void move(int left, int top);
    }
}
