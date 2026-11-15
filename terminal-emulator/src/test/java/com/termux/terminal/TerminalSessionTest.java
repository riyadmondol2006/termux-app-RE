package com.termux.terminal;

import junit.framework.TestCase;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public class TerminalSessionTest extends TestCase {

	private static TerminalSession createRunningSession() {
		TerminalSession session = new TerminalSession("/bin/sh", "/", new String[0], new String[0], null, null);
		// Pretend that the shell process has been started without actually starting it.
		session.mShellPid = Integer.MAX_VALUE;
		return session;
	}

	/** Read all the bytes currently queued to be written to the shell process. */
	private static byte[] readQueuedInput(TerminalSession session) {
		ByteArrayOutputStream result = new ByteArrayOutputStream();
		byte[] buffer = new byte[4096];
		int read;
		while ((read = session.mTerminalToProcessIOQueue.read(buffer, false)) > 0)
			result.write(buffer, 0, read);
		return result.toByteArray();
	}

	private static void assertQueuedInput(TerminalSession session, String expected) {
		assertEquals(expected, new String(readQueuedInput(session), StandardCharsets.UTF_8));
	}

	/** Empty writes, like from pasting only escape characters, should be ignored (termux/termux-app#5119). */
	public void testEmptyWriteIsIgnored() {
		TerminalSession session = createRunningSession();
		session.write(new byte[0], 0, 0);
		session.write(new byte[]{'a'}, 1, 0);
		session.write("");
		assertQueuedInput(session, "");
	}

	public void testWriteCodePoint() {
		TerminalSession session = createRunningSession();
		session.writeCodePoint(false, 'a');
		session.writeCodePoint(true, 'b');
		session.writeCodePoint(false, 0xE9);
		session.writeCodePoint(false, 0x20AC);
		session.writeCodePoint(false, 0x1F600);
		assertQueuedInput(session, "a\033bé€😀");
	}

	/** Invalid code points, like lone surrogates from an IME, should not throw (termux/termux-app#3242). */
	public void testWriteInvalidCodePointWritesReplacementChar() {
		TerminalSession session = createRunningSession();
		session.writeCodePoint(false, 0xD800);
		session.writeCodePoint(false, 0xDBFF);
		session.writeCodePoint(false, 0xDC00);
		session.writeCodePoint(true, 0xDFFF);
		session.writeCodePoint(false, 0x110000);
		session.writeCodePoint(false, -2);
		assertQueuedInput(session, "���\033���");
	}

}
