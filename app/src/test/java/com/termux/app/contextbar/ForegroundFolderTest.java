package com.termux.app.contextbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** The foreground program's folder, read from a fake procfs. */
public class ForegroundFolderTest {

    @Rule public TemporaryFolder proc = new TemporaryFolder();

    private void process(int pid, String comm, int tpgid, String environ) throws IOException {
        File dir = new File(proc.getRoot(), Integer.toString(pid));
        assertTrue(dir.mkdirs() || dir.isDirectory());
        write(new File(dir, "comm"), comm + "\n");
        // comm in stat may hold spaces and parentheses; tpgid is the sixth field after it.
        write(new File(dir, "stat"), pid + " (" + comm + ") S 1 " + pid + " " + pid + " 34816 "
            + tpgid + " 4194560 0 0");
        write(new File(dir, "environ"), environ);
    }

    private static void write(File file, String text) throws IOException {
        Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void readsTheForegroundProgramsPwd() throws Exception {
        process(100, "proot", 200, "PWD=/home\0");
        process(200, "node (claude)", 200, "HOME=/home\0PWD=/home/repos/app\0TERM=xterm\0");
        assertEquals("/home/repos/app", new ForegroundFolder(proc.getRoot()).read(100));
    }

    @Test
    public void aShellInFrontLeavesItToOsc7() throws Exception {
        process(100, "zsh", 100, "PWD=/home/old\0");
        assertNull(new ForegroundFolder(proc.getRoot()).read(100));
    }

    @Test
    public void unreadableProcessesAnswerNull() {
        ForegroundFolder folder = new ForegroundFolder(proc.getRoot());
        assertNull(folder.read(4242));
        assertNull(folder.read(0));
        assertEquals(4242, folder.foregroundPid(4242));
        assertTrue("unknown counts as a shell", folder.isShell(4242));
    }

    @Test
    public void aProgramWithoutPwdAnswersNull() throws Exception {
        process(100, "bash", 300, "");
        process(300, "vim", 300, "HOME=/home\0");
        assertNull(new ForegroundFolder(proc.getRoot()).read(100));
    }
}
