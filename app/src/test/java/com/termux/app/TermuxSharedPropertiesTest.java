package com.termux.app;

import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;
import com.termux.shared.termux.settings.properties.TermuxSharedProperties;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class TermuxSharedPropertiesTest {

    private static Object getValue(String key, String value) {
        return TermuxSharedProperties.getInternalTermuxPropertyValueFromValue(null, key, value);
    }

    @Test
    public void testDisableTerminalSessionTitleChangeToast() {
        final String key = TermuxPropertyConstants.KEY_DISABLE_TERMINAL_SESSION_TITLE_CHANGE_TOAST;
        Assert.assertEquals("disable-terminal-session-title-change-toast", key);
        Assert.assertTrue(TermuxPropertyConstants.TERMUX_APP_PROPERTIES_LIST.contains(key));
        Assert.assertTrue(TermuxPropertyConstants.TERMUX_DEFAULT_FALSE_BOOLEAN_BEHAVIOUR_PROPERTIES_LIST.contains(key));

        Assert.assertEquals(true, getValue(key, "true"));
        Assert.assertEquals(false, getValue(key, "false"));
        // Default is false, so title change toasts are shown.
        Assert.assertEquals(false, getValue(key, null));
        Assert.assertEquals(false, getValue(key, "invalid"));
    }

}
