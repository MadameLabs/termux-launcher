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
    public void resizeStopsAtTheMinimumAndTheEdge() {
        RectF small = CommandBoxPopup.clamp(new RectF(.2f, .2f, .21f, .22f), .1f, .1f, true);
        assertEquals(.3f, small.right, 1e-4f);
        assertEquals(.3f, small.bottom, 1e-4f);
        RectF big = CommandBoxPopup.clamp(new RectF(.2f, .2f, 1.5f, 1.5f), .1f, .1f, true);
        assertEquals(1f, big.right, 1e-4f);
        assertEquals(1f, big.bottom, 1e-4f);
    }
}
