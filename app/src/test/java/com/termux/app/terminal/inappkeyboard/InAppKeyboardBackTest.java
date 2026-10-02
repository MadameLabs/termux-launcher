package com.termux.app.terminal.inappkeyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;
import android.view.KeyEvent;

import com.termux.app.chrome.OverlayRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Back puts the embedded keyboard down the way every app's Back puts its keyboard down, but only
 * once every surface drawn over the terminal has had its turn, and never by handing the press to
 * the shell.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class InAppKeyboardBackTest {

    private static final class FakeKeyboard implements InAppKeyboardBack.Keyboard {
        boolean visible;
        int hides;

        @Override public boolean isVisible() { return visible; }

        @Override public void hideForBack() {
            visible = false;
            hides++;
        }
    }

    /** A Back-only overlay that is open until Back closes it. */
    private static final class Pane implements OverlayRegistry.Overlay {
        boolean open;

        Pane(boolean open) { this.open = open; }

        @Override public boolean onBack() {
            if (!open) return false;
            open = false;
            return true;
        }
    }

    private static final KeyEvent BACK_DOWN = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK);

    @Test
    public void backWithTheKeyboardUpPutsItDownAndIsSpent() {
        FakeKeyboard keyboard = new FakeKeyboard();
        keyboard.visible = true;

        assertTrue(new InAppKeyboardBack(() -> keyboard).onBack());
        assertFalse(keyboard.visible);
    }

    @Test
    public void backWithTheKeyboardDownIsLeftForWhateverComesNext() {
        FakeKeyboard keyboard = new FakeKeyboard();

        assertFalse(new InAppKeyboardBack(() -> keyboard).onBack());
        assertEquals(0, keyboard.hides);
    }

    @Test
    public void backBeforeTheKeyboardExistsIsNotSpent() {
        assertFalse(new InAppKeyboardBack(() -> null).onBack());
    }

    @Test
    public void anOverlayOverTheTerminalClosesFirstAndTheKeyboardStaysUp() {
        FakeKeyboard keyboard = new FakeKeyboard();
        keyboard.visible = true;
        Pane pane = new Pane(true);
        OverlayRegistry registry = new OverlayRegistry();
        registry.register(pane);
        registry.register(new InAppKeyboardBack(() -> keyboard));

        assertTrue(registry.onBackPressed());
        assertFalse(pane.open);
        assertTrue("one press closes one layer", keyboard.visible);

        assertTrue(registry.onBackPressed());
        assertFalse(keyboard.visible);
    }

    @Test
    public void theKeyChannelClaimsTheStrokeSoTheReleaseNeverReachesTheShell() {
        FakeKeyboard keyboard = new FakeKeyboard();
        keyboard.visible = true;
        OverlayRegistry registry = new OverlayRegistry();
        registry.register(new InAppKeyboardBack(() -> keyboard));

        assertTrue(registry.consumeKeyDown(KeyEvent.KEYCODE_BACK, BACK_DOWN));
        assertFalse(keyboard.visible);
        assertTrue(registry.consumeKeyUp(KeyEvent.KEYCODE_BACK));
    }

    @Test
    public void lifecycleDropsLeaveTheKeyboardAlone() {
        FakeKeyboard keyboard = new FakeKeyboard();
        keyboard.visible = true;

        new InAppKeyboardBack(() -> keyboard).closeImmediately(OverlayRegistry.CloseReason.HOME);

        assertTrue(keyboard.visible);
    }
}
