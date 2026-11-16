package com.termux.app.terminal.io;

import android.graphics.RectF;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P)
public class CommandBoxPopupTest {

    @Test
    public void boundsFollowTheFractionOfTheParent() {
        int[] box = CommandBoxPopup.boundsPx(new RectF(.1f, .2f, .9f, .6f), 1000, 2000, 100, 100);
        assertArrayEquals(new int[]{100, 400, 800, 800}, box);
    }

    /** A keyboard that shortens the window shrinks the popup with it, and never lets it out. */
    @Test
    public void aShorterParentKeepsThePopupInside() {
        RectF frac = new RectF(.1f, .5f, .9f, .9f);
        assertArrayEquals(new int[]{100, 500, 800, 400},
            CommandBoxPopup.boundsPx(frac, 1000, 1000, 100, 100));
        // Half the height: the popup keeps its fraction's height and stays inside.
        assertArrayEquals(new int[]{100, 250, 800, 200},
            CommandBoxPopup.boundsPx(frac, 1000, 500, 100, 100));
        // Shorter than the minimum: it fills what there is from the top.
        assertArrayEquals(new int[]{100, 0, 800, 80},
            CommandBoxPopup.boundsPx(frac, 1000, 80, 100, 100));
    }

    @Test
    public void dragKeepsTheSizeAndStaysInside() {
        RectF moved = CommandBoxPopup.clamp(new RectF(.8f, -.1f, 1.2f, .3f), .1f, .1f, false);
        assertEquals(.6f, moved.left, 1e-4f);
        assertEquals(0f, moved.top, 1e-4f);
        assertEquals(.4f, moved.width(), 1e-4f);
        assertEquals(.4f, moved.height(), 1e-4f);
    }

    @Test
    public void resizeFromTheBottomRightStopsAtTheMinimumAndTheEdge() {
        RectF small = CommandBoxPopup.clamp(new RectF(.2f, .2f, .21f, .22f), .1f, .1f, true);
        assertEquals(.2f, small.left, 1e-4f);
        assertEquals(.3f, small.right, 1e-4f);
        assertEquals(.3f, small.bottom, 1e-4f);
        RectF big = CommandBoxPopup.clamp(new RectF(.2f, .2f, 1.5f, 1.5f), .1f, .1f, true);
        assertEquals(.2f, big.left, 1e-4f);
        assertEquals(1f, big.right, 1e-4f);
        assertEquals(1f, big.bottom, 1e-4f);
    }

    @Test
    public void theBoxReadsNoGlassOfItsOwn() {
        // ADR 0018: its glass is the Terminal element's; the keys it once held are cleared.
        android.content.SharedPreferences p = androidx.test.core.app.ApplicationProvider
            .getApplicationContext().getSharedPreferences("command_box_test", 0);
        p.edit().putBoolean("command_box_own_glass", true).putInt("command_box_frost", 40)
            .putString(CommandBoxLook.KEY_TEXT_COLORS, CommandBoxLook.TEXT_COLORS_LAUNCHER).commit();
        CommandBoxLook look = CommandBoxLook.of(p);
        assertEquals(false, look.terminalColors);
        assertEquals(false, p.contains("command_box_own_glass"));
        assertEquals(false, p.contains("command_box_frost"));
    }

    @Test
    public void theBoxStartsAsTheTerminalOnceAndThenStandsApart() {
        com.termux.shared.termux.settings.preferences.LookGrid.MemoryStore store =
            new com.termux.shared.termux.settings.preferences.LookGrid.MemoryStore();
        com.termux.shared.termux.settings.preferences.LookGrid grid =
            new com.termux.shared.termux.settings.preferences.LookGrid(store, null, null);
        com.termux.shared.termux.settings.preferences.LookGrid.Property opacity =
            com.termux.shared.termux.settings.preferences.LookGrid.Property.OPACITY;
        com.termux.shared.termux.settings.preferences.LookGrid.Element terminal =
            com.termux.shared.termux.settings.preferences.LookGrid.Element.TERMINAL;
        com.termux.shared.termux.settings.preferences.LookGrid.Element box =
            com.termux.shared.termux.settings.preferences.LookGrid.Element.COMMAND_BOX;
        com.termux.shared.termux.settings.preferences.LookGrid.Kind kind =
            com.termux.shared.termux.settings.preferences.LookGrid.Kind.TERMINAL;
        grid.write(opacity, terminal, kind, 15);
        grid.write(opacity, terminal, 7, 30);
        assertEquals(2, com.termux.shared.termux.settings.preferences.LookGridCommandBoxSeed.run(store));
        assertEquals(15, grid.value(opacity, box, kind));
        assertEquals(30, grid.value(opacity, box, 7));
        // Once: a later terminal change no longer reaches the box.
        grid.write(opacity, terminal, kind, 60);
        assertEquals(0, com.termux.shared.termux.settings.preferences.LookGridCommandBoxSeed.run(store));
        assertEquals(15, grid.value(opacity, box, kind));
    }

    @Test
    public void spaceBarSlidesWholeStepsOnly() {
        assertEquals(0, SpaceSliderView.steps(7f, 8f));
        assertEquals(2, SpaceSliderView.steps(17f, 8f));
        assertEquals(-1, SpaceSliderView.steps(-9f, 8f));
        assertEquals(0, SpaceSliderView.steps(40f, 0f));
    }

    @Test
    public void swipeSectorsFollowTheKeyboard() {
        assertEquals(SwipeKeyView.C, SwipeKeyView.placeFor(3, 3, 10));
        assertEquals(SwipeKeyView.E, SwipeKeyView.placeFor(20, 2, 10));
        assertEquals(SwipeKeyView.W, SwipeKeyView.placeFor(-20, -2, 10));
        assertEquals(SwipeKeyView.N, SwipeKeyView.placeFor(1, -20, 10));
        assertEquals(SwipeKeyView.S, SwipeKeyView.placeFor(-1, 20, 10));
        assertEquals(SwipeKeyView.NW, SwipeKeyView.placeFor(-15, -15, 10));
        assertEquals(SwipeKeyView.NE, SwipeKeyView.placeFor(15, -15, 10));
        assertEquals(SwipeKeyView.SW, SwipeKeyView.placeFor(-15, 15, 10));
        assertEquals(SwipeKeyView.SE, SwipeKeyView.placeFor(15, 15, 10));
    }
}
