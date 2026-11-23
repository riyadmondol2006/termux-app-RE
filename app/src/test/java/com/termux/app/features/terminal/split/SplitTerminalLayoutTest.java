package com.termux.app.features.terminal.split;

import android.content.Context;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class SplitTerminalLayoutTest {

    private final Context mContext = RuntimeEnvironment.getApplication();

    private static final int WIDTH = 1000;
    private static final int HEIGHT = 2000;

    private SplitTerminalLayout createLayout(View primary) {
        SplitTerminalLayout layout = new SplitTerminalLayout(mContext);
        layout.addView(primary);
        layout.onFinishInflate();
        layout.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        layout.layout(0, 0, WIDTH, HEIGHT);
        return layout;
    }

    private static View divider(Context context, int size) {
        View divider = new View(context);
        divider.setMinimumWidth(size);
        divider.setMinimumHeight(size);
        return divider;
    }

    @Test
    public void testWithoutSplitThePrimaryViewTakesTheWholeSize() {
        View primary = new View(mContext);
        SplitTerminalLayout layout = createLayout(primary);
        assertFalse(layout.isSplit());
        assertEquals(WIDTH, primary.getWidth());
        assertEquals(HEIGHT, primary.getHeight());
    }

    @Test
    @Config(qualifiers = "port")
    public void testStackedInPortrait() {
        View primary = new View(mContext);
        View secondary = new View(mContext);
        View divider = divider(mContext, 20);
        SplitTerminalLayout layout = createLayout(primary);

        layout.split(secondary, divider);
        assertTrue(layout.isSplit());
        assertFalse(layout.isSideBySide());
        // (2000 - 20) / 2 = 990 for each pane.
        assertEquals(0, primary.getTop());
        assertEquals(990, primary.getBottom());
        assertEquals(WIDTH, primary.getWidth());
        assertEquals(990, divider.getTop());
        assertEquals(1010, divider.getBottom());
        assertEquals(1010, secondary.getTop());
        assertEquals(HEIGHT, secondary.getBottom());

        // The primary view moves to the other pane.
        layout.setPrimaryPane(1);
        assertEquals(0, secondary.getTop());
        assertEquals(1010, primary.getTop());

        layout.setRatio(0.25f);
        assertEquals(495, secondary.getHeight());
        assertEquals(1980 - 495, primary.getHeight());

        // The ratio is limited, so a pane does not get too small.
        layout.setRatio(0f);
        assertEquals(Math.round(HEIGHT * SplitTerminalLayout.MIN_PANE_FRACTION), secondary.getHeight());

        layout.unsplit();
        assertFalse(layout.isSplit());
        assertNull(secondary.getParent());
        assertNull(divider.getParent());
        assertEquals(0, primary.getTop());
        assertEquals(HEIGHT, primary.getHeight());
    }

    @Test
    @Config(qualifiers = "land")
    public void testSideBySideInLandscape() {
        View primary = new View(mContext);
        View secondary = new View(mContext);
        View divider = divider(mContext, 20);
        SplitTerminalLayout layout = createLayout(primary);

        layout.split(secondary, divider);
        assertTrue(layout.isSideBySide());
        assertEquals(0, primary.getLeft());
        assertEquals(490, primary.getRight());
        assertEquals(HEIGHT, primary.getHeight());
        assertEquals(510, secondary.getLeft());
        assertEquals(WIDTH, secondary.getRight());
    }

}
