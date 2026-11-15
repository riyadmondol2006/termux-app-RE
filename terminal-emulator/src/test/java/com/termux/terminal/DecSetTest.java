package com.termux.terminal;

/**
 * <pre>
 * "CSI ? Pm h", DEC Private Mode Set (DECSET)
 * </pre>
 * <p/>
 * and
 * <p/>
 * <pre>
 * "CSI ? Pm l", DEC Private Mode Reset (DECRST)
 * </pre>
 * <p/>
 * controls various aspects of the terminal
 */
public class DecSetTest extends TerminalTestCase {

	/** DECSET 25, DECTCEM, controls visibility of the cursor. */
	public void testEnableDisableCursor() {
		withTerminalSized(3, 3);
		assertTrue("Initially the cursor should be enabled", mTerminal.isCursorEnabled());
		enterString("\033[?25l"); // Disable Cursor (DECTCEM).
		assertFalse(mTerminal.isCursorEnabled());
		enterString("\033[?25h"); // Enable Cursor (DECTCEM).
		assertTrue(mTerminal.isCursorEnabled());

		enterString("\033[?25l"); // Disable Cursor (DECTCEM), again.
		assertFalse(mTerminal.isCursorEnabled());
		mTerminal.reset();
		assertTrue("Resetting the terminal should enable the cursor", mTerminal.isCursorEnabled());

		enterString("\033[?25l");
		assertFalse(mTerminal.isCursorEnabled());
		enterString("\033c"); // RIS resetting should enabled cursor.
		assertTrue(mTerminal.isCursorEnabled());
	}

	/** DECSET 1004, controls reporting of focus in and out events (termux/termux-app#3650). */
	public void testFocusReporting() {
		withTerminalSized(3, 3);
		assertTrue("Initially the terminal should be considered focused", mTerminal.hasFocus());
		mTerminal.onFocusChanged(false);
		mTerminal.onFocusChanged(true);
		assertEquals("Focus changes should not be reported when DECSET 1004 is not set", "", mOutput.getOutputAndClear());

		assertEnteringStringGivesResponse("\033[?1004$p", "\033[?1004;2$y");
		enterString("\033[?1004h"); // Enable focus reporting.
		assertEnteringStringGivesResponse("\033[?1004$p", "\033[?1004;1$y");
		assertEquals("Enabling focus reporting should not report the focus", "", mOutput.getOutputAndClear());

		mTerminal.onFocusChanged(false);
		assertEquals("\033[O", mOutput.getOutputAndClear());
		assertFalse(mTerminal.hasFocus());
		mTerminal.onFocusChanged(false);
		assertEquals("Focus should only be reported when it changes", "", mOutput.getOutputAndClear());
		mTerminal.onFocusChanged(true);
		assertEquals("\033[I", mOutput.getOutputAndClear());
		mTerminal.onFocusChanged(true);
		assertEquals("", mOutput.getOutputAndClear());

		enterString("\033[?1004l"); // Disable focus reporting.
		mTerminal.onFocusChanged(false);
		assertEquals("", mOutput.getOutputAndClear());
		enterString("\033[?1004h");
		mTerminal.onFocusChanged(false);
		assertEquals("The focus should be tracked while focus reporting is disabled", "", mOutput.getOutputAndClear());
		mTerminal.onFocusChanged(true);
		assertEquals("\033[I", mOutput.getOutputAndClear());

		enterString("\033c"); // RIS resetting should disable focus reporting.
		mTerminal.onFocusChanged(false);
		assertEquals("", mOutput.getOutputAndClear());
	}

	/** DECSET 2004, controls bracketed paste mode. */
	public void testBracketedPasteMode() {
		withTerminalSized(3, 3);

		mTerminal.paste("a");
		assertEquals("Pasting 'a' should output 'a' when bracketed paste mode is disabled", "a", mOutput.getOutputAndClear());

		enterString("\033[?2004h"); // Enable bracketed paste mode.
		mTerminal.paste("a");
		assertEquals("Pasting when in bracketed paste mode should be bracketed", "\033[200~a\033[201~", mOutput.getOutputAndClear());

		enterString("\033[?2004l"); // Disable bracketed paste mode.
		mTerminal.paste("a");
		assertEquals("Pasting 'a' should output 'a' when bracketed paste mode is disabled", "a", mOutput.getOutputAndClear());

		enterString("\033[?2004h"); // Enable bracketed paste mode, again.
		mTerminal.paste("a");
		assertEquals("Pasting when in bracketed paste mode again should be bracketed", "\033[200~a\033[201~", mOutput.getOutputAndClear());

		mTerminal.paste("\033ab\033cd\033");
		assertEquals("Pasting an escape character should not input it", "\033[200~abcd\033[201~", mOutput.getOutputAndClear());
		mTerminal.paste("\u0081ab\u0081cd\u009F");
		assertEquals("Pasting C1 control codes should not input it", "\033[200~abcd\033[201~", mOutput.getOutputAndClear());

		mTerminal.reset();
		mTerminal.paste("a");
		assertEquals("Terminal reset() should disable bracketed paste mode", "a", mOutput.getOutputAndClear());
	}

	/** DECSET 7, DECAWM, controls wraparound mode. */
	public void testWrapAroundMode() {
		// Default with wraparound:
		withTerminalSized(3, 3).enterString("abcd").assertLinesAre("abc", "d  ", "   ");
		// With wraparound disabled:
		withTerminalSized(3, 3).enterString("\033[?7labcd").assertLinesAre("abd", "   ", "   ");
		enterString("efg").assertLinesAre("abg", "   ", "   ");
		// Re-enabling wraparound:
		enterString("\033[?7hhij").assertLinesAre("abh", "ij ", "   ");
	}

}
