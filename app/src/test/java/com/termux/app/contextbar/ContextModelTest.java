package com.termux.app.contextbar;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ContextModelTest {

    private static final List<String> SHELL = Collections.singletonList("zsh");

    private static ContextInput input(String remoteUserHost, String remotePath, String localPath,
                                          List<String> command, String title, boolean agent,
                                          String fiveHour, String weekly) {
        return new ContextInput(remoteUserHost, remotePath, localPath, command, title, agent,
            fiveHour, weekly);
    }

    @Test
    public void localPane() {
        ContextLine line = ContextModel.resolve(
            input(null, null, "/data/data/com.termux/files/home/dev", SHELL, null, false, null, null));
        assertEquals("tlnix", line.machine);
        assertEquals("dev", line.path);
        assertNull(line.chat);
    }

    @Test
    public void remoteFromOsc7() {
        ContextLine line = ContextModel.resolve(
            input("Arthur@Arch-PC", "/home/arthur/dev", "/data/x", Arrays.asList("ssh", "arch"),
                null, false, null, null));
        assertEquals("arthur@arch-pc", line.machine);
        assertEquals("dev", line.path);
    }

    @Test
    public void sshFallbackWithoutOsc7() {
        ContextLine labs = ContextModel.resolve(input(null, null, "/data/x",
            Arrays.asList("ssh", "-p", "22", "mrlabs@labs"), null, false, null, null));
        assertEquals("mrlabs@labs", labs.machine);
        assertNull(labs.path);

        ContextLine arch = ContextModel.resolve(input(null, null, "/data/x",
            Arrays.asList("/usr/bin/ssh", "-t", "arch", "tmux"), null, false, null, null));
        assertEquals("arch", arch.machine);
    }

    @Test
    public void leftSshReturnsToLocal() {
        ContextLine line = ContextModel.resolve(input(null, null, "/data/x", SHELL, null, false, null, null));
        assertEquals("tlnix", line.machine);
        assertEquals("x", line.path);
    }

    @Test
    public void limitsOnlyWithAgent() {
        ContextLine none = ContextModel.resolve(input(null, null, "/x", SHELL, "zsh", false, "42", "10"));
        assertNull(none.fiveHour);
        assertNull(none.weekly);
        assertNull(none.chat);

        ContextLine agent = ContextModel.resolve(input(null, null, "/x", SHELL, "barra", true, "42", "10"));
        assertEquals(Integer.valueOf(42), agent.fiveHour);
        assertEquals(Integer.valueOf(10), agent.weekly);
        assertEquals("barra", agent.chat);
    }

    @Test
    public void badLimitValuesAreDropped() {
        ContextLine line = ContextModel.resolve(input(null, null, "/x", SHELL, null, true, "abc", "101"));
        assertNull(line.fiveHour);
        assertNull(line.weekly);
    }

    @Test
    public void hotAboveEighty() {
        assertTrue(ContextModel.resolve(input(null, null, "/x", SHELL, null, true, "81", "80")).fiveHourHot);
        assertFalse(ContextModel.resolve(input(null, null, "/x", SHELL, null, true, "81", "80")).weeklyHot);
    }

    @Test
    public void chatCutAtChatMax() {
        String title = "uma conversa com nome bem comprido demais, comprido mesmo";
        ContextLine line = ContextModel.resolve(input(null, null, "/x", SHELL, title, true, null, null));
        assertEquals(ContextModel.CHAT_MAX, line.chat.length());
        assertTrue(line.chat.endsWith("…"));
        assertEquals(title.substring(0, ContextModel.CHAT_MAX - 1),
            line.chat.substring(0, ContextModel.CHAT_MAX - 1));
    }

    /** The weekly chip carries the day its limit renews, as the statusline sends it. */
    @Test
    public void weeklyResetIsKeptOnlyAsADay() {
        assertEquals("20", ContextModel.resetDay(" 20 "));
        assertEquals("20/11", ContextModel.resetDay("20/11"));
        assertEquals(null, ContextModel.resetDay("1731690000"));
        assertEquals(null, ContextModel.resetDay(null));
        ContextLine line = ContextModel.resolve(new ContextInput(null, null, "/x",
            SHELL, "t", true, "42", "10", "14:30", "20"));
        assertEquals("20", line.weeklyReset);
        ContextLine noWeekly = ContextModel.resolve(new ContextInput(null, null, "/x",
            SHELL, "t", true, "42", null, "14:30", "20"));
        assertEquals(null, noWeekly.weeklyReset);
    }

    /** The 5 h chip carries when the limit renews, HH:MM as the statusline sends it. */
    @Test
    public void fiveHourResetIsKeptOnlyAsATime() {
        assertEquals("14:30", ContextModel.resetTime(" 14:30 "));
        assertEquals("9:05", ContextModel.resetTime("9:05"));
        assertEquals("01h20", ContextModel.resetTime("01h20"));
        assertEquals(null, ContextModel.resetTime("1731690000"));
        assertEquals(null, ContextModel.resetTime(null));
        ContextLine line = ContextModel.resolve(new ContextInput(null, null, "/x",
            SHELL, "t", true, "42", "10", "14:30"));
        assertEquals("14:30", line.fiveHourReset);
        assertEquals("14:30", line.with("/y", "c").fiveHourReset);
    }

    /** The keys panel's chat row passes no cap: the view fits the name to its width. */
    @Test
    public void chatKeptWholeWithoutACap() {
        String title = "uma conversa com nome bem comprido demais, comprido mesmo";
        ContextLine line = ContextModel.resolve(
            input(null, null, "/x", SHELL, title, true, null, null), Integer.MAX_VALUE);
        assertEquals(title, line.chat);
    }

    @Test
    public void onlyTheLastFolder() {
        assertEquals("home", ContextModel.lastFolder("/data/data/com.termux/files/home/"));
        assertEquals("/", ContextModel.lastFolder("/"));
        assertNull(ContextModel.lastFolder(null));
        ContextLine line = ContextModel.resolve(input(null, null,
            "/data/data/com.termux/files/home/repos/termux-launcher", SHELL, null, false, null, null));
        assertEquals("termux-launcher", line.path);
    }

    @Test
    public void fitNeverCutsMachine() {
        ContextLine full = ContextModel.resolve(input("arthur@arch-pc", "/home/arthur/repos/termux-launcher",
            "/x", SHELL, "barra de orientacao", true, "42", "10"));

        ContextLine roomy = ContextModel.fit(full, 200);
        assertEquals(full.chat, roomy.chat);
        assertEquals(full.path, roomy.path);

        ContextLine tight = ContextModel.fit(full, 50);
        assertEquals("arthur@arch-pc", tight.machine);
        assertTrue("the chat shrinks first", tight.chat != null && tight.chat.length() < full.chat.length());
        assertEquals(full.path, tight.path);

        ContextLine tiny = ContextModel.fit(full, 10);
        assertEquals("arthur@arch-pc", tiny.machine);
        assertNull(tiny.chat);
        assertTrue(tiny.path == null || tiny.path.startsWith("…/"));
        assertEquals(Integer.valueOf(42), tiny.fiveHour);
    }

    @Test
    public void chatPrefersTheStatuslineNameOverTheTitle() {
        assertEquals("barra", ContextBarController.chatName("barra", "claude"));
        assertEquals("claude", ContextBarController.chatName(null, "claude"));
        assertEquals("claude", ContextBarController.chatName("  ", "claude"));
    }
}
