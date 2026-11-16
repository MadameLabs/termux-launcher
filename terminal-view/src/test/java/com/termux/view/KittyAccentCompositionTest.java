package com.termux.view;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.KeyCharacterMap;

import org.junit.Test;

/** Which keys the kitty encoder leaves to the legacy path's accent composition. */
public class KittyAccentCompositionTest {

    private static final int TILDE = '~' | KeyCharacterMap.COMBINING_ACCENT;

    @Test
    public void aDeadKeyBareOrWithItsModifierIsComposed() {
        assertTrue("bare dead key", TerminalView.composesAccent(TILDE, TILDE, 0));
        assertTrue("dead key behind AltGr (scrcpy)", TerminalView.composesAccent('n', TILDE, 0));
    }

    @Test
    public void theKeyAfterADeadKeyIsComposed() {
        assertTrue(TerminalView.composesAccent('a', 'a', '~'));
    }

    @Test
    public void anOrdinaryKeyStaysTheEncoders() {
        assertFalse(TerminalView.composesAccent('a', 'a', 0));
        assertFalse(TerminalView.composesAccent('a', 'A', 0));
    }
}
