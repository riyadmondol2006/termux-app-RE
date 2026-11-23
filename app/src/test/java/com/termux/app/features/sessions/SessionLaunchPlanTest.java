package com.termux.app.features.sessions;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SessionLaunchPlanTest {

    private static final SessionLaunchPlan.Paths PATHS = new SessionLaunchPlan.Paths("/p/usr/bin", "/p/home/.termux/re/shell");
    private static final List<String> NO_FILES = Collections.emptyList();
    private static final List<String> FILES = Arrays.asList("/p/home/.termux/re/startup.sh", "/p/home/.termux/re/profiles/x.sh");

    @Test
    public void testDefaultShellWithoutAutomationIsUnchanged() {
        SessionLaunchPlan plan = SessionLaunchPlan.create(PATHS, "/p/usr/bin/bash", true, NO_FILES, 0, null);
        Assert.assertNull(plan.executable);
        Assert.assertNull(plan.arguments);
        Assert.assertTrue(plan.environment.isEmpty());
    }

    @Test
    public void testBashStartupUsesPromptCommand() {
        SessionLaunchPlan plan = SessionLaunchPlan.create(PATHS, "/p/usr/bin/bash", true, FILES, 0, "Work");
        Assert.assertNull(plan.executable);
        Assert.assertNull(plan.arguments);
        Assert.assertEquals(". '/p/home/.termux/re/shell/init.bash'", plan.environment.get("PROMPT_COMMAND"));
        Assert.assertEquals("/p/home/.termux/re/startup.sh:/p/home/.termux/re/profiles/x.sh", plan.environment.get(SessionLaunchPlan.ENV_STARTUP));
        Assert.assertEquals("Work", plan.environment.get(SessionLaunchPlan.ENV_PROFILE));
        Assert.assertNull(plan.environment.get(SessionLaunchPlan.ENV_NOTIFY_AFTER));
    }

    @Test
    public void testBashNotificationsOnly() {
        SessionLaunchPlan plan = SessionLaunchPlan.create(PATHS, "/p/usr/bin/bash", true, NO_FILES, 15, null);
        Assert.assertEquals("15", plan.environment.get(SessionLaunchPlan.ENV_NOTIFY_AFTER));
        Assert.assertNull(plan.environment.get(SessionLaunchPlan.ENV_STARTUP));
        Assert.assertNotNull(plan.environment.get("PROMPT_COMMAND"));
    }

    @Test
    public void testZshUsesZdotdirAndLoginShellScript() {
        SessionLaunchPlan plan = SessionLaunchPlan.create(PATHS, "/p/usr/bin/zsh", false, FILES, 10, null);
        Assert.assertEquals("/p/usr/bin/sh", plan.executable);
        Assert.assertArrayEquals(new String[]{"/p/home/.termux/re/shell/login-shell", "/p/usr/bin/zsh"}, plan.arguments);
        Assert.assertEquals("/p/home/.termux/re/shell/zsh", plan.environment.get("ZDOTDIR"));
        Assert.assertEquals("10", plan.environment.get(SessionLaunchPlan.ENV_NOTIFY_AFTER));
        Assert.assertNull(plan.environment.get("PROMPT_COMMAND"));
    }

    @Test
    public void testFishUsesInitCommand() {
        SessionLaunchPlan plan = SessionLaunchPlan.create(PATHS, "/p/usr/bin/fish", false, FILES, 0, null);
        Assert.assertArrayEquals(new String[]{"/p/home/.termux/re/shell/login-shell", "/p/usr/bin/fish",
            "--init-command", "source '/p/home/.termux/re/shell/init.fish'"}, plan.arguments);

        // As the default shell, the arguments are passed to login, which passes them to the shell.
        plan = SessionLaunchPlan.create(PATHS, "/p/usr/bin/fish", true, FILES, 0, null);
        Assert.assertNull(plan.executable);
        Assert.assertArrayEquals(new String[]{"--init-command", "source '/p/home/.termux/re/shell/init.fish'"}, plan.arguments);
    }

    @Test
    public void testPosixShellUsesEnvAndHasNoNotifications() {
        SessionLaunchPlan plan = SessionLaunchPlan.create(PATHS, "/p/usr/bin/dash", false, FILES, 10, null);
        Assert.assertEquals(SessionLaunchPlan.ShellType.POSIX, plan.shellType);
        Assert.assertEquals("/p/home/.termux/re/shell/init.sh", plan.environment.get("ENV"));
        Assert.assertNull(plan.environment.get(SessionLaunchPlan.ENV_NOTIFY_AFTER));

        // Nothing to set up.
        plan = SessionLaunchPlan.create(PATHS, "/p/usr/bin/sh", false, NO_FILES, 10, null);
        Assert.assertNull(plan.environment.get("ENV"));
    }

    @Test
    public void testOtherShellsAreStartedWithoutAutomation() {
        SessionLaunchPlan plan = SessionLaunchPlan.create(PATHS, "/p/usr/bin/nu", false, FILES, 10, null);
        Assert.assertEquals(SessionLaunchPlan.ShellType.OTHER, plan.shellType);
        Assert.assertTrue(plan.environment.isEmpty());
        Assert.assertArrayEquals(new String[]{"/p/home/.termux/re/shell/login-shell", "/p/usr/bin/nu"}, plan.arguments);
    }

    @Test
    public void testShellQuote() {
        Assert.assertEquals("'a b'", SessionLaunchPlan.shellQuote("a b"));
        Assert.assertEquals("'it'\\''s'", SessionLaunchPlan.shellQuote("it's"));
    }

}
