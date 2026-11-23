package com.termux.app.features.input;

import static org.junit.Assert.*;

import com.termux.shared.termux.extrakeys.ExtraKeysConstants;
import com.termux.shared.termux.extrakeys.ExtraKeysInfo;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.StringReader;
import java.util.Properties;

@RunWith(RobolectricTestRunner.class)
public class KeyboardPropertiesTest {
    @Test
    public void savedKeysMacrosPopupsAndSnippetsLoadWithRuntimeParser() throws Exception {
        String json =
                "[[\"ESC\",{\"macro\":\"CTRL c\",\"display\":\"Cancel\",\"popup\":{\"key\":\"hello"
                    + " world\"}},{\"key\":\"echo \\\"hi\\\"\\n"
                    + "\"}]]";
        Properties properties = new Properties();
        properties.load(
                new StringReader(InputFiles.keyboardProperties("# My keyboard\n", json, "all")));
        ExtraKeysInfo info =
                new ExtraKeysInfo(
                        properties.getProperty("extra-keys"),
                        properties.getProperty("extra-keys-style"),
                        ExtraKeysConstants.CONTROL_CHARS_ALIASES);
        assertEquals(3, info.getMatrix()[0].length);
        assertEquals("Cancel", info.getMatrix()[0][1].getDisplay());
        assertEquals("echo \"hi\"\n", info.getMatrix()[0][2].getKey());
    }
}
