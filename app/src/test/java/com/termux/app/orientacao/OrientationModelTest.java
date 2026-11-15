package com.termux.app.orientacao;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class OrientationModelTest {

    private static final List<String> SHELL = Collections.singletonList("zsh");

    private static OrientationInput input(String remoteUserHost, String remotePath, String localPath,
                                          List<String> command, String title, boolean agent,
                                          String fiveHour, String weekly) {
        return new OrientationInput(remoteUserHost, remotePath, localPath, command, title, agent,
            fiveHour, weekly);
    }

    @Test
    public void localPane() {
        OrientationLine line = OrientationModel.resolve(
            input(null, null, "/data/data/com.termux/files/home/dev", SHELL, null, false, null, null));
        assertEquals("tlnix", line.machine);
        assertEquals("dev", line.path);
        assertNull(line.chat);
    }

    @Test
    public void remoteFromOsc7() {
        OrientationLine line = OrientationModel.resolve(
            input("Arthur@Arch-PC", "/home/arthur/dev", "/data/x", Arrays.asList("ssh", "arch"),
                null, false, null, null));
        assertEquals("arthur@arch-pc", line.machine);
        assertEquals("dev", line.path);
    }

    @Test
    public void sshFallbackWithoutOsc7() {
        OrientationLine labs = OrientationModel.resolve(input(null, null, "/data/x",
            Arrays.asList("ssh", "-p", "22", "mrlabs@labs"), null, false, null, null));
        assertEquals("mrlabs@labs", labs.machine);
        assertNull(labs.path);

        OrientationLine arch = OrientationModel.resolve(input(null, null, "/data/x",
            Arrays.asList("/usr/bin/ssh", "-t", "arch", "tmux"), null, false, null, null));
        assertEquals("arch", arch.machine);
    }

    @Test
    public void leftSshReturnsToLocal() {
        OrientationLine line = OrientationModel.resolve(input(null, null, "/data/x", SHELL, null, false, null, null));
        assertEquals("tlnix", line.machine);
        assertEquals("x", line.path);
    }

    @Test
    public void limitsOnlyWithAgent() {
        OrientationLine none = OrientationModel.resolve(input(null, null, "/x", SHELL, "zsh", false, "42", "10"));
        assertNull(none.fiveHour);
        assertNull(none.weekly);
        assertNull(none.chat);

        OrientationLine agent = OrientationModel.resolve(input(null, null, "/x", SHELL, "barra", true, "42", "10"));
        assertEquals(Integer.valueOf(42), agent.fiveHour);
        assertEquals(Integer.valueOf(10), agent.weekly);
        assertEquals("barra", agent.chat);
    }

    @Test
    public void badLimitValuesAreDropped() {
        OrientationLine line = OrientationModel.resolve(input(null, null, "/x", SHELL, null, true, "abc", "101"));
        assertNull(line.fiveHour);
        assertNull(line.weekly);
    }

    @Test
    public void hotAboveEighty() {
        assertTrue(OrientationModel.resolve(input(null, null, "/x", SHELL, null, true, "81", "80")).fiveHourHot);
        assertFalse(OrientationModel.resolve(input(null, null, "/x", SHELL, null, true, "81", "80")).weeklyHot);
    }

    @Test
    public void chatCutAtChatMax() {
        String title = "uma conversa com nome bem comprido demais, comprido mesmo";
        OrientationLine line = OrientationModel.resolve(input(null, null, "/x", SHELL, title, true, null, null));
        assertEquals(OrientationModel.CHAT_MAX, line.chat.length());
        assertTrue(line.chat.endsWith("…"));
        assertEquals(title.substring(0, OrientationModel.CHAT_MAX - 1),
            line.chat.substring(0, OrientationModel.CHAT_MAX - 1));
    }

    /** The keys panel's chat row passes no cap: the view fits the name to its width. */
    @Test
    public void chatKeptWholeWithoutACap() {
        String title = "uma conversa com nome bem comprido demais, comprido mesmo";
        OrientationLine line = OrientationModel.resolve(
            input(null, null, "/x", SHELL, title, true, null, null), Integer.MAX_VALUE);
        assertEquals(title, line.chat);
    }

    @Test
    public void onlyTheLastFolder() {
        assertEquals("home", OrientationModel.lastFolder("/data/data/com.termux/files/home/"));
        assertEquals("/", OrientationModel.lastFolder("/"));
        assertNull(OrientationModel.lastFolder(null));
        OrientationLine line = OrientationModel.resolve(input(null, null,
            "/data/data/com.termux/files/home/repos/termux-launcher", SHELL, null, false, null, null));
        assertEquals("termux-launcher", line.path);
    }

    @Test
    public void fitNeverCutsMachine() {
        OrientationLine full = OrientationModel.resolve(input("arthur@arch-pc", "/home/arthur/repos/termux-launcher",
            "/x", SHELL, "barra de orientacao", true, "42", "10"));

        OrientationLine roomy = OrientationModel.fit(full, 200);
        assertEquals(full.chat, roomy.chat);
        assertEquals(full.path, roomy.path);

        OrientationLine tight = OrientationModel.fit(full, 50);
        assertEquals("arthur@arch-pc", tight.machine);
        assertTrue("the chat shrinks first", tight.chat != null && tight.chat.length() < full.chat.length());
        assertEquals(full.path, tight.path);

        OrientationLine tiny = OrientationModel.fit(full, 10);
        assertEquals("arthur@arch-pc", tiny.machine);
        assertNull(tiny.chat);
        assertTrue(tiny.path == null || tiny.path.startsWith("…/"));
        assertEquals(Integer.valueOf(42), tiny.fiveHour);
    }

    @Test
    public void chatPrefersTheStatuslineNameOverTheTitle() {
        assertEquals("barra", OrientationBarController.chatName("barra", "claude"));
        assertEquals("claude", OrientationBarController.chatName(null, "claude"));
        assertEquals("claude", OrientationBarController.chatName("  ", "claude"));
    }
}
