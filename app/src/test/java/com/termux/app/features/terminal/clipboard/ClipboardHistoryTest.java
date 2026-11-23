package com.termux.app.features.terminal.clipboard;

import android.content.Context;

import com.termux.shared.interact.ShareUtils;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class ClipboardHistoryTest {

    private final Context mContext = RuntimeEnvironment.getApplication();

    @After
    public void tearDown() {
        ShareUtils.setClipboardTextListener(null);
        mContext.getSharedPreferences(ClipboardHistory.PREFERENCES_NAME, Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test
    public void testItemsAreSaved() {
        ClipboardHistory history = new ClipboardHistory(mContext);
        history.add("first");
        history.add("second");
        assertEquals(Arrays.asList("second", "first"), new ClipboardHistory(mContext).getItems());

        history.remove("first");
        assertEquals(Collections.singletonList("second"), new ClipboardHistory(mContext).getItems());

        history.clear();
        assertEquals(Collections.emptyList(), new ClipboardHistory(mContext).getItems());
    }

    @Test
    public void testTurningOffClearsAndStopsRecording() {
        ClipboardHistory history = new ClipboardHistory(mContext);
        history.add("secret");
        history.setEnabled(false);
        history.add("another");
        assertEquals(Collections.emptyList(), history.getItems());

        ClipboardHistory reloaded = new ClipboardHistory(mContext);
        assertFalse(reloaded.isEnabled());
        assertEquals(Collections.emptyList(), reloaded.getItems());

        reloaded.setEnabled(true);
        assertTrue(reloaded.isEnabled());
        reloaded.add("again");
        assertEquals(Collections.singletonList("again"), reloaded.getItems());
    }

    @Test
    public void testCopiedAndPastedTextIsAdded() {
        ClipboardHistory.install(mContext);
        ShareUtils.copyTextToClipboard(mContext, "copied text");
        assertEquals("copied text", ClipboardHistory.getInstance(mContext).getItems().get(0));

        assertEquals("copied text", ShareUtils.getTextStringFromClipboardIfSet(mContext, true));
        assertEquals(1, Collections.frequency(ClipboardHistory.getInstance(mContext).getItems(), "copied text"));
    }

    @Test
    public void testParseItems() {
        assertEquals(Arrays.asList("a", "b\nc"), ClipboardHistory.parseItems(ClipboardHistory.toJson(Arrays.asList("a", "b\nc"))));
        assertEquals(Collections.emptyList(), ClipboardHistory.parseItems("not json"));
        assertEquals(Collections.emptyList(), ClipboardHistory.parseItems(null));
    }

}
