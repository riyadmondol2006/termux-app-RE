package com.termux.terminal;

import junit.framework.TestCase;

public class TerminalSessionTest extends TestCase {

	private static TerminalSession createRunningSession() {
		TerminalSession session = new TerminalSession("/bin/sh", "/", new String[0], new String[0], null, null);
		// Pretend that the shell process has been started without actually starting it.
		session.mShellPid = Integer.MAX_VALUE;
		return session;
	}

	/** Empty writes, like from pasting only escape characters, should be ignored (termux/termux-app#5119). */
	public void testEmptyWriteIsIgnored() {
		TerminalSession session = createRunningSession();
		session.write(new byte[0], 0, 0);
		session.write(new byte[]{'a'}, 1, 0);
		session.write("");
	}

}
