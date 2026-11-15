package com.termux.app.orientacao;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * Decides the orientation bar's chips from what a pane reports (MadameLabs fork). Free of Android
 * so every rule is unit-tested: the machine is never a guess — a remote OSC 7 first, then the
 * destination of an {@code ssh} in the foreground, and only then this device.
 */
public final class OrientationModel {

    /** This device, whose hostname is always "localhost" under the TLNix. */
    public static final String LOCAL_MACHINE = "tlnix";
    /** A chat name longer than this is cut, as tmux-ia.sh does on the Arch-PC. */
    public static final int CHAT_MAX = 28;
    /** A limit above this percent draws red. */
    public static final int HOT_ABOVE = 80;

    /** ssh options that take a value as the next argument. */
    private static final String SSH_VALUE_OPTIONS = "BbcDEeFIiJLlmOoPpQRSWw";

    private OrientationModel() {}

    @NonNull
    public static OrientationLine resolve(@NonNull OrientationInput in) {
        String machine;
        String path;
        String sshTarget = sshDestination(in.foregroundCommand);
        if (in.remoteUserHost != null) {
            machine = in.remoteUserHost.toLowerCase(Locale.ROOT);
            path = in.remotePath;
        } else if (sshTarget != null) {
            machine = sshTarget.toLowerCase(Locale.ROOT);
            path = null;
        } else {
            machine = LOCAL_MACHINE;
            path = in.localPath;
        }
        path = lastFolder(path);
        String chat = in.agentPresent ? cut(in.title, CHAT_MAX) : null;
        Integer fiveHour = in.agentPresent ? percent(in.fiveHour) : null;
        Integer weekly = in.agentPresent ? percent(in.weekly) : null;
        return new OrientationLine(machine, path, chat, fiveHour, weekly);
    }

    /**
     * The line cut to about {@code maxChars} of text: the chat shrinks first, then the path keeps
     * its end behind "…/", then goes; the machine and the limits always stay.
     */
    @NonNull
    public static OrientationLine fit(@NonNull OrientationLine line, int maxChars) {
        int fixed = line.machine.length() + limitsLength(line);
        String path = line.path;
        String chat = line.chat;
        int room = maxChars - fixed - len(path);
        if (chat != null && chat.length() > room) chat = room >= 4 ? cut(chat, room) : null;
        if (path != null && fixed + path.length() + len(chat) > maxChars) {
            int pathRoom = maxChars - fixed - len(chat);
            path = pathRoom >= 6 ? tail(path, pathRoom) : null;
        }
        return line.with(path, chat);
    }

    /** The destination of an {@code ssh} argv, or null when the foreground is not ssh. */
    @Nullable
    static String sshDestination(@NonNull List<String> argv) {
        if (argv.isEmpty()) return null;
        String program = argv.get(0);
        if (!"ssh".equals(program.substring(program.lastIndexOf('/') + 1))) return null;
        for (int i = 1; i < argv.size(); i++) {
            String arg = argv.get(i);
            if (arg.startsWith("-") && arg.length() > 1) {
                if (arg.length() == 2 && SSH_VALUE_OPTIONS.indexOf(arg.charAt(1)) >= 0) i++;
                continue;
            }
            return arg.isEmpty() ? null : arg;
        }
        return null;
    }

    /** Only the last folder of a path, as the Arthur asked: "/home/arthur/dev" reads "dev". */
    @Nullable
    static String lastFolder(@Nullable String path) {
        if (path == null) return null;
        String trimmed = path;
        while (trimmed.length() > 1 && trimmed.endsWith("/")) trimmed = trimmed.substring(0, trimmed.length() - 1);
        if (trimmed.isEmpty()) return null;
        if (trimmed.equals("/")) return "/";
        return trimmed.substring(trimmed.lastIndexOf('/') + 1);
    }

    @Nullable
    private static Integer percent(@Nullable String raw) {
        if (raw == null) return null;
        try {
            int value = (int) Math.floor(Double.parseDouble(raw.trim()));
            return value >= 0 && value <= 100 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Nullable
    private static String cut(@Nullable String text, int max) {
        if (text == null) return null;
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return null;
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max - 1) + "…";
    }

    @NonNull
    private static String tail(@NonNull String path, int max) {
        if (path.length() <= max) return path;
        String end = path.substring(path.length() - (max - 2));
        int slash = end.indexOf('/');
        if (slash > 0 && slash < end.length() - 1) end = end.substring(slash + 1);
        return "…/" + end;
    }

    private static int limitsLength(@NonNull OrientationLine line) {
        int n = 0;
        if (line.fiveHour != null) n += String.valueOf(line.fiveHour).length() + 1;
        if (line.weekly != null) n += String.valueOf(line.weekly).length() + 5;
        return n;
    }

    private static int len(@Nullable String s) {
        return s == null ? 0 : s.length();
    }
}
