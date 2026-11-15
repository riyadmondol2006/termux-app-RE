package com.termux.terminal;

import junit.framework.TestCase;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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
		byte[] data;
		while ((data = session.mTerminalToProcessIOQueue.poll()) != null) {
			assertTrue("Empty writes should not be queued", data.length > 0);
			result.write(data, 0, data.length);
		}
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

	/**
	 * Writing a large amount of data, like for a large paste, should not block the writing thread, which is normally
	 * the main thread, even if the process is not reading its input (termux/termux-app#5379).
	 */
	public void testLargeWriteDoesNotBlock() throws InterruptedException {
		TerminalSession session = createRunningSession();
		final byte[] data = new byte[4 * 1024 * 1024];
		for (int i = 0; i < data.length; i++) data[i] = (byte) ('a' + (i % 26));

		Thread writer = new Thread(() -> {
			session.write(data, 0, data.length);
			session.write("end");
		});
		writer.start();
		writer.join(TimeUnit.SECONDS.toMillis(10));
		assertFalse("Writing to the session blocked", writer.isAlive());

		byte[] expected = Arrays.copyOf(data, data.length + 3);
		expected[data.length] = 'e'; expected[data.length + 1] = 'n'; expected[data.length + 2] = 'd';
		assertTrue(Arrays.equals(expected, readQueuedInput(session)));
	}

	/** The data written should not be affected by changes to the passed array after the write returns. */
	public void testWriteCopiesData() {
		TerminalSession session = createRunningSession();
		byte[] data = "abcdef".getBytes(StandardCharsets.UTF_8);
		session.write(data, 1, 3);
		Arrays.fill(data, (byte) 'x');
		assertQueuedInput(session, "bcd");
	}

	/** The writer thread should write all data in order and stop when the queue is closed. */
	public void testWriterThreadWritesInOrderAndStopsWhenClosed() throws Exception {
		TerminalSession session = createRunningSession();
		final ByteArrayOutputStream written = new ByteArrayOutputStream();
		final CountDownLatch firstWriteDone = new CountDownLatch(1);
		final AtomicReference<Throwable> writerError = new AtomicReference<>();
		OutputStream out = new OutputStream() {
			@Override
			public void write(int b) {
				written.write(b);
			}

			@Override
			public void write(byte[] b, int off, int len) {
				written.write(b, off, len);
				firstWriteDone.countDown();
			}
		};

		Thread writerThread = new Thread(() -> {
			try {
				session.writeTerminalToProcessIOQueue(out);
			} catch (IOException | InterruptedException | RuntimeException e) {
				writerError.set(e);
			}
		});
		writerThread.start();

		session.write("first");
		assertTrue(firstWriteDone.await(10, TimeUnit.SECONDS));

		// Writes from multiple threads, like the main thread and an auto repeat thread, should not be interleaved.
		Thread[] threads = new Thread[4];
		for (int t = 0; t < threads.length; t++) {
			final char c = (char) ('A' + t);
			threads[t] = new Thread(() -> {
				char[] chunk = new char[1000];
				Arrays.fill(chunk, c);
				String s = new String(chunk);
				for (int i = 0; i < 100; i++) session.write(s);
			});
		}
		for (Thread thread : threads) thread.start();
		for (Thread thread : threads) thread.join();
		session.write("last");

		// Wait for all queued data to be written before closing, since closing discards unwritten data.
		long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(10);
		while (!session.mTerminalToProcessIOQueue.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(10);
		session.closeTerminalToProcessIOQueue();
		writerThread.join(TimeUnit.SECONDS.toMillis(10));
		assertFalse("Writer thread did not stop after closing the queue", writerThread.isAlive());
		assertNull(writerError.get());

		String result;
		synchronized (written) {
			result = new String(written.toByteArray(), StandardCharsets.UTF_8);
		}
		assertTrue(result.startsWith("first"));
		assertTrue(result.endsWith("last"));
		String middle = result.substring("first".length(), result.length() - "last".length());
		assertEquals(4 * 100 * 1000, middle.length());
		for (int i = 0; i < middle.length(); i += 1000) {
			char c = middle.charAt(i);
			for (int j = i; j < i + 1000; j++)
				assertEquals("Writes were interleaved at index " + j, c, middle.charAt(j));
		}
	}

}
