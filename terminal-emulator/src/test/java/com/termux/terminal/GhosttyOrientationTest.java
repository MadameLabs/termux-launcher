package com.termux.terminal;

import java.nio.charset.StandardCharsets;

/**
 * The orientation bar's reads (MadameLabs fork) are the same on the Fast (libghostty-vt) core as on
 * the Java one: the user and host of a remote OSC 7, and iTerm's SetUserVar, which libghostty-vt
 * drops and the JNI sniffer hands to Java.
 */
public class GhosttyOrientationTest extends TerminalTestCase {

    private static boolean libraryBuilt() {
        return System.getProperty(GhosttyNative.TEST_LIBRARY_DIR_PROPERTY) != null;
    }

    private static TerminalEmulator pane(boolean ghostty) {
        TerminalEmulator emulator = new TerminalEmulator(new MockTerminalOutput(), false, 20, 4,
            INITIAL_CELL_WIDTH_PIXELS, INITIAL_CELL_HEIGHT_PIXELS, 50, null);
        if (ghostty) assertTrue("library failed to load: " + GhosttyNative.loadError(), emulator.useGhosttyCore());
        return emulator;
    }

    private static void feed(TerminalEmulator emulator, String text, boolean byteByByte) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (!byteByByte) {
            emulator.append(bytes, bytes.length);
            return;
        }
        for (byte b : bytes) emulator.append(new byte[]{b}, 1);
    }

    public void testGhosttyOsc7KeepsUserInfo() {
        if (!libraryBuilt()) return;
        for (boolean ghostty : new boolean[]{false, true}) {
            TerminalEmulator emulator = pane(ghostty);
            feed(emulator, "\033]7;file://arthur@arch-pc/home/arthur\007", false);
            assertEquals("ghostty=" + ghostty, "arthur@arch-pc", emulator.getRemoteUserHost());
            assertEquals("ghostty=" + ghostty, "/home/arthur", emulator.getRemotePath());
            feed(emulator, "\033]7;file://localhost/data/x\007", false);
            assertNull("ghostty=" + ghostty, emulator.getRemoteUserHost());
            assertEquals("ghostty=" + ghostty, "/data/x", emulator.getReportedWorkingDirectory());
        }
    }

    public void testGhosttySetUserVar() {
        if (!libraryBuilt()) return;
        for (boolean split : new boolean[]{false, true}) {
            TerminalEmulator emulator = pane(true);
            feed(emulator, "\033]1337;SetUserVar=ml_5h=NDI=\007", split);
            assertEquals("split=" + split, "42", emulator.getUserVar("ml_5h"));
            // OSC 133 shares the sniffer's first three bytes and must still reach the library.
            feed(emulator, "\033]133;A\007$ ", split);
            assertEquals("split=" + split, "42", emulator.getUserVar("ml_5h"));
        }
    }
}
