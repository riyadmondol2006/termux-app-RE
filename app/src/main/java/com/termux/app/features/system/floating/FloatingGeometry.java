package com.termux.app.features.system.floating;

/** Size and position rules of the floating terminal window, in pixels, free of Android classes so they can be tested. */
final class FloatingGeometry {

    private FloatingGeometry() {}

    /** Clamp {@code value} to [min, max], where max wins if max < min. */
    static int clamp(int value, int min, int max) {
        if (max < min) return max;
        return Math.max(min, Math.min(max, value));
    }

    /** The default size of the window: most of the width of the screen and a bit over a third of its height. */
    static int[] getDefaultSize(int screenWidth, int screenHeight) {
        return new int[]{(int) (screenWidth * 0.9f), (int) (screenHeight * 0.4f)};
    }

    /**
     * Clamp a window size to the screen, not smaller than {@code minWidth} x {@code minHeight}
     * unless the screen is smaller.
     */
    static int[] clampSize(int width, int height, int screenWidth, int screenHeight, int minWidth, int minHeight) {
        return new int[]{clamp(width, Math.min(minWidth, screenWidth), screenWidth),
            clamp(height, Math.min(minHeight, screenHeight), screenHeight)};
    }

    /**
     * Clamp the top left position of a window so that its title bar ({@code titleHeight}) is always
     * on the screen and at least {@code grab} pixels of it horizontally, so it can be moved back.
     */
    static int[] clampPosition(int x, int y, int width, int screenWidth, int screenHeight, int titleHeight, int grab) {
        int minX = -(width - grab);
        int maxX = screenWidth - grab;
        return new int[]{clamp(x, minX, maxX), clamp(y, 0, screenHeight - titleHeight)};
    }

    /**
     * Clamp a bubble of {@code size} pixels fully on the screen. Returns {@code {x, y}}.
     */
    static int[] clampBubble(int x, int y, int size, int screenWidth, int screenHeight) {
        return new int[]{clamp(x, 0, screenWidth - size), clamp(y, 0, screenHeight - size)};
    }

    /** Move a bubble to the nearest left or right edge of the screen, keeping its y. */
    static int snapBubbleToEdge(int x, int size, int screenWidth) {
        return x + size / 2 < screenWidth / 2 ? 0 : screenWidth - size;
    }

}
