package com.termux.terminal;

/**
 * What the orientation bar reads from a pane (MadameLabs fork): the user and host of the last OSC 7
 * that named another machine, its path, and the values a harness sets with iTerm's
 * {@code OSC 1337 ; SetUserVar=name=base64}. None of it may change the local working directory a
 * new pane opens in.
 */
public class OrientationOscTest extends TerminalTestCase {

    public void testRemoteOsc7KeepsUserHostAndPath() {
        withTerminalSized(5, 3).enterString("\033]7;file:///tmp\007");
        enterString("\033]7;file://arthur@Arch-PC/home/arthur/dev\007");
        assertEquals("arthur@Arch-PC", mTerminal.getRemoteUserHost());
        assertEquals("/home/arthur/dev", mTerminal.getRemotePath());
        assertEquals("/tmp", mTerminal.getReportedWorkingDirectory());
    }

    public void testARemoteHostWithoutAUserIsKeptToo() {
        withTerminalSized(5, 3).enterString("\033]7;file://labs/home/mrlabs\007");
        assertEquals("labs", mTerminal.getRemoteUserHost());
        assertEquals("/home/mrlabs", mTerminal.getRemotePath());
    }

    public void testALocalOsc7ClearsTheRemote() {
        withTerminalSized(5, 3).enterString("\033]7;file://arthur@Arch-PC/home/arthur\007");
        enterString("\033]7;file://localhost/data/x\007");
        assertNull(mTerminal.getRemoteUserHost());
        assertNull(mTerminal.getRemotePath());
        assertEquals("/data/x", mTerminal.getReportedWorkingDirectory());
    }

    /** A user in front of localhost is still this device, and still opens new panes there. */
    public void testAUserAtLocalhostIsLocal() {
        withTerminalSized(5, 3).enterString("\033]7;file://nix-on-droid@localhost/data/x\007");
        assertNull(mTerminal.getRemoteUserHost());
        assertEquals("/data/x", mTerminal.getReportedWorkingDirectory());
    }

    public void testAnEmptyReportClearsTheRemote() {
        withTerminalSized(5, 3).enterString("\033]7;file://labs/home/mrlabs\007");
        enterString("\033]7;\007");
        assertNull(mTerminal.getRemoteUserHost());
        assertNull(mTerminal.getRemotePath());
    }

    public void testSetUserVarDecodesBase64() {
        withTerminalSized(5, 3).enterString("\033]1337;SetUserVar=ml_5h=NDI=\007");
        assertEquals("42", mTerminal.getUserVar("ml_5h"));
        assertNull(mTerminal.getUserVar("ml_semana"));
    }

    public void testABadUserVarIsIgnored() {
        withTerminalSized(5, 3).enterString("\033]1337;SetUserVar=ml_5h=NDI=\007");
        enterString("\033]1337;SetUserVar=ml_5h=%%%\007");
        assertEquals("42", mTerminal.getUserVar("ml_5h"));
        enterString("\033]1337;SetUserVar=semnome\007");
        assertEquals("42", mTerminal.getUserVar("ml_5h"));
    }

    public void testTheStampMovesOnEveryChange() {
        withTerminalSized(5, 3);
        long start = mTerminal.getOrientationStamp();
        enterString("\033]7;file://labs/home/mrlabs\007");
        long afterHost = mTerminal.getOrientationStamp();
        assertTrue(afterHost > start);
        enterString("\033]1337;SetUserVar=ml_5h=NDI=\007");
        long afterVar = mTerminal.getOrientationStamp();
        assertTrue(afterVar > afterHost);
        enterString("\033]1337;SetUserVar=ml_5h=NDI=\007");
        assertEquals("the same value again is not a change", afterVar, mTerminal.getOrientationStamp());
        enterString("\033]7;file://localhost/tmp\007");
        assertTrue(mTerminal.getOrientationStamp() > afterVar);
    }
}
