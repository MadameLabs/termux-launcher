package com.termux.app.contextbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** The place strip moves into the keys panel and back to the same place in the status row. */
@RunWith(RobolectricTestRunner.class)
public class PlaceStripDockTest {

    @Test
    public void movesIntoTheDockAndBackToWhereItStood() {
        Context context = ApplicationProvider.getApplicationContext();
        LinearLayout row = new LinearLayout(context);
        View before = new View(context);
        View strip = new View(context);
        View after = new View(context);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(10, 20);
        row.addView(before);
        row.addView(strip, params);
        row.addView(after);
        FrameLayout dock = new FrameLayout(context);
        PlaceStripDock placeStrip = new PlaceStripDock();

        assertTrue(placeStrip.apply(true, strip, dock));
        assertSame(dock, strip.getParent());
        assertEquals(2, row.getChildCount());
        assertFalse("idempotent", placeStrip.apply(true, strip, dock));

        assertTrue(placeStrip.apply(false, strip, dock));
        assertSame(row, strip.getParent());
        assertEquals(1, row.indexOfChild(strip));
        assertSame(params, strip.getLayoutParams());
        assertFalse("idempotent", placeStrip.apply(false, strip, dock));
    }
}
