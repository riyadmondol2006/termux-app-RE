package com.termux.app.features.sessions;

import com.termux.shared.termux.TermuxConstants;

import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class TerminalProfileTest {

    @Test
    public void testJsonRoundTrip() throws Exception {
        TerminalProfile profile = new TerminalProfile("id-1");
        profile.name = "Work";
        profile.shell = "zsh";
        profile.workingDirectory = "~/work";
        profile.startupCommands = "cd src\nexport A=1";
        profile.colorSchemeId = "dracula";
        profile.fontSize = 14;

        TerminalProfile copy = TerminalProfile.fromJson(new JSONObject(profile.toJson().toString()));
        Assert.assertNotNull(copy);
        Assert.assertEquals("id-1", copy.id);
        Assert.assertEquals("Work", copy.name);
        Assert.assertEquals("zsh", copy.shell);
        Assert.assertEquals("~/work", copy.workingDirectory);
        Assert.assertEquals("cd src\nexport A=1", copy.startupCommands);
        Assert.assertEquals("dracula", copy.colorSchemeId);
        Assert.assertEquals(14, copy.fontSize);
    }

    @Test
    public void testInvalidJson() throws Exception {
        Assert.assertNull(TerminalProfile.fromJson(new JSONObject("{\"name\": \"no id\"}")));
        TerminalProfile profile = TerminalProfile.fromJson(new JSONObject("{\"id\": \"x\", \"fontSize\": 1000}"));
        Assert.assertNotNull(profile);
        Assert.assertEquals(TerminalProfile.SHELL_DEFAULT, profile.shell);
        Assert.assertEquals(TerminalProfile.MAX_FONT_SIZE, profile.fontSize);
    }

    @Test
    public void testClampFontSize() {
        Assert.assertEquals(0, TerminalProfile.clampFontSize(0));
        Assert.assertEquals(0, TerminalProfile.clampFontSize(-3));
        Assert.assertEquals(TerminalProfile.MIN_FONT_SIZE, TerminalProfile.clampFontSize(1));
        Assert.assertEquals(12, TerminalProfile.clampFontSize(12));
    }

    @Test
    public void testExpandHome() {
        String home = TermuxConstants.TERMUX_HOME_DIR_PATH;
        Assert.assertNull(SessionStartup.expandHome(null));
        Assert.assertNull(SessionStartup.expandHome("  "));
        Assert.assertEquals(home, SessionStartup.expandHome("~"));
        Assert.assertEquals(home + "/src", SessionStartup.expandHome("~/src"));
        Assert.assertEquals(home + "/src", SessionStartup.expandHome("$HOME/src"));
        Assert.assertEquals("/sdcard", SessionStartup.expandHome(" /sdcard "));
    }

    @Test
    public void testProfileStartupFilePath() {
        Assert.assertEquals(SessionStartup.PROFILES_DIR_PATH + "/abc-1.sh", SessionStartup.getProfileStartupFilePath("abc-1"));
        // Ids can't be used to write files outside of the profiles directory.
        Assert.assertEquals(SessionStartup.PROFILES_DIR_PATH + "/___x.sh", SessionStartup.getProfileStartupFilePath("../x"));
    }

}
