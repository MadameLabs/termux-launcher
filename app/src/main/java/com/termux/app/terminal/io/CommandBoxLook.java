package com.termux.app.terminal.io;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

/**
 * MadameLabs fork: the command box popup's own settings (Settings › Terminal › Command box), read
 * each time the popup opens. They are only about its text: the terminal's colours or the
 * launcher's, at the terminal's size or a size of its own. Its glass is not here: the popup wears
 * the Terminal element's glass through the panes' surface style (ADR 0017, 0018), tuned on the
 * Terminal card in Appearance, so no look value is read from these preferences.
 */
public final class CommandBoxLook {

    /** The file these settings live in; the popup's place and size are kept there too. */
    public static final String PREFS = "command_box_popup";

    public static final String KEY_TEXT_COLORS = "command_box_text_colors";
    public static final String KEY_TEXT_SIZE_FOLLOWS = "command_box_text_size_follows_terminal";
    public static final String KEY_TEXT_SIZE = "command_box_text_size";

    public static final String TEXT_COLORS_TERMINAL = "terminal";
    public static final String TEXT_COLORS_LAUNCHER = "launcher";

    public static final int DEFAULT_TEXT_SIZE_SP = 14;

    /** The keys of the glass this box once had of its own, cleared on first read. */
    private static final String[] RETIRED_KEYS = {"command_box_own_glass", "command_box_opacity",
        "command_box_blur", "command_box_frost", "command_box_grain", "command_box_tint",
        "command_box_corners", "command_box_rim"};

    /** True for the terminal's colours, false for the launcher's. */
    public final boolean terminalColors;
    public final boolean textSizeFollowsTerminal;
    public final int textSizeSp;

    @VisibleForTesting
    CommandBoxLook(boolean terminalColors, boolean textSizeFollowsTerminal, int textSizeSp) {
        this.terminalColors = terminalColors;
        this.textSizeFollowsTerminal = textSizeFollowsTerminal;
        this.textSizeSp = Math.max(8, Math.min(40, textSizeSp));
    }

    /** The defaults: the terminal's colours at the terminal's size. */
    @NonNull
    public static CommandBoxLook defaults() {
        return new CommandBoxLook(true, true, DEFAULT_TEXT_SIZE_SP);
    }

    @NonNull
    public static SharedPreferences prefs(@NonNull Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    @NonNull
    public static CommandBoxLook read(@NonNull Context context) {
        return of(prefs(context));
    }

    @NonNull
    @VisibleForTesting
    static CommandBoxLook of(@NonNull SharedPreferences p) {
        SharedPreferences.Editor retired = null;
        for (String key : RETIRED_KEYS) {
            if (!p.contains(key)) continue;
            if (retired == null) retired = p.edit();
            retired.remove(key);
        }
        if (retired != null) retired.apply();
        return new CommandBoxLook(
            !TEXT_COLORS_LAUNCHER.equals(p.getString(KEY_TEXT_COLORS, TEXT_COLORS_TERMINAL)),
            p.getBoolean(KEY_TEXT_SIZE_FOLLOWS, true),
            p.getInt(KEY_TEXT_SIZE, DEFAULT_TEXT_SIZE_SP));
    }
}
