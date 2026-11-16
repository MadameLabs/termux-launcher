package com.termux.shared.termux.settings.preferences;

import androidx.annotation.NonNull;

/**
 * MadameLabs fork: the command box popup's element starts dressed as the terminal, once, behind
 * {@link #KEY_SEEDED}. The popup wore the Terminal element's glass before it had an element of its
 * own, so every cell the terminal holds (everywhere, per kind, per page) is copied to the command
 * box where the box holds none. From then on the two are tuned apart, each on its own card. Cells
 * the terminal follows Base for are not copied: the box follows Base for them too.
 */
public final class LookGridCommandBoxSeed {

    private LookGridCommandBoxSeed() {}

    public static final String KEY_SEEDED = "look_grid_command_box_seeded";

    /** Seeds the command box's cells once; returns how many it wrote. */
    public static int run(@NonNull LookGrid.Store store) {
        if (store.getBoolean(KEY_SEEDED, false)) return 0;
        int written = new LookGrid(store, null, null)
            .seedElement(LookGrid.Element.TERMINAL, LookGrid.Element.COMMAND_BOX);
        store.putBoolean(KEY_SEEDED, true);
        return written;
    }
}
