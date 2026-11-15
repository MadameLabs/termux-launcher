package com.termux.app.terminal.inappkeyboard;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.chrome.OverlayRegistry;

/**
 * Back puts the embedded keyboard down, the way Back puts any app's keyboard down.
 *
 * <p>Registered as the outermost overlay: every surface drawn over the terminal gets the press
 * first, and only a press nothing else wanted lowers the keyboard. Through the registry it covers
 * both routes Back travels — {@code onBackPressed()} and the key channel, which also swallows the
 * release so it never reaches the shell. A lifecycle drop leaves the keyboard alone: it is not a
 * transient surface, and the place decides whether it is up.
 */
public final class InAppKeyboardBack implements OverlayRegistry.Overlay {

    /** The part of the keyboard Back needs. */
    public interface Keyboard {
        boolean isVisible();

        /** Lowers it exactly as the KEYBOARD extra key does. */
        void hideForBack();
    }

    /** The keyboard is created after the registry, so it is looked up at press time. */
    public interface KeyboardSource {
        @Nullable Keyboard keyboard();
    }

    @NonNull private final KeyboardSource mSource;

    public InAppKeyboardBack(@NonNull KeyboardSource source) {
        mSource = source;
    }

    @Override
    public boolean onBack() {
        Keyboard keyboard = mSource.keyboard();
        if (keyboard == null || !keyboard.isVisible()) return false;
        keyboard.hideForBack();
        return true;
    }
}
