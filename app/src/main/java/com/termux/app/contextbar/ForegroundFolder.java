package com.termux.app.contextbar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * The folder of the program in front of a terminal, read from procfs (MadameLabs fork). Blocking:
 * {@link ContextBarController} calls it on its reader thread, never on the main thread.
 *
 * <p>The terminal's foreground group (tpgid) is the program in front. Under TLNix's proot every
 * process's kernel cwd stays where proot started (home), since proot keeps the real one virtually,
 * so the folder comes from the program's {@code PWD}, which it inherited from the shell that ran
 * it. A shell in front, at its prompt, answers with OSC 7 instead, so it reads as null here.
 */
final class ForegroundFolder {

    /** Programs whose folder is the shell's own OSC 7, not their environment. */
    static final Set<String> SHELLS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "zsh", "bash", "sh", "fish", "dash", "proot-static", "proot")));

    @NonNull private final File mProc;

    ForegroundFolder() {
        this(new File("/proc"));
    }

    ForegroundFolder(@NonNull File proc) {
        mProc = proc;
    }

    /** The folder of the program in front of the terminal whose shell is {@code shellPid}, or null. */
    @Nullable
    String read(int shellPid) {
        if (shellPid <= 0) return null;
        int pid = foregroundPid(shellPid);
        if (isShell(pid)) return null;
        return environPwd(pid);
    }

    /**
     * The leader of the terminal's foreground process group, from the tpgid field of
     * {@code /proc/<pid>/stat}, or {@code pid} itself when it cannot be read.
     */
    int foregroundPid(int pid) {
        String stat = readText(pid, "stat");
        if (stat == null) return pid;
        try {
            // comm may hold spaces and parentheses: the fields start after the last ')'.
            String[] fields = stat.substring(stat.lastIndexOf(')') + 2).split(" ");
            int tpgid = Integer.parseInt(fields[5]); // state ppid pgrp session tty_nr tpgid
            return tpgid > 0 ? tpgid : pid;
        } catch (RuntimeException e) {
            return pid;
        }
    }

    /** Whether {@code pid} is a shell, by its {@code comm}; unknown counts as one. */
    boolean isShell(int pid) {
        String comm = readText(pid, "comm");
        return comm == null || SHELLS.contains(comm.trim());
    }

    /** The {@code PWD} in {@code pid}'s environment, or null. */
    @Nullable
    String environPwd(int pid) {
        String env = readText(pid, "environ");
        if (env == null) return null;
        for (String entry : env.split("\0")) {
            if (entry.startsWith("PWD=") && entry.length() > 4) return entry.substring(4);
        }
        return null;
    }

    @Nullable
    private String readText(int pid, @NonNull String name) {
        File file = new File(new File(mProc, Integer.toString(pid)), name);
        try (InputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            for (int n; (n = in.read(buffer)) > 0; ) out.write(buffer, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException | SecurityException e) {
            return null;
        }
    }
}
