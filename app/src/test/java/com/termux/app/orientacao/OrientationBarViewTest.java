package com.termux.app.orientacao;

import android.app.Application;
import android.os.Build;
import android.view.View;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class OrientationBarViewTest {

    private static OrientationBarView view() {
        return new OrientationBarView(ApplicationProvider.getApplicationContext());
    }

    /** The texts of the visible chips, left to right. */
    private static List<String> texts(OrientationBarView bar) {
        List<String> out = new ArrayList<>();
        for (OrientationBarView.Chip chip : bar.visibleChips()) out.add(chip.text.getText().toString());
        return out;
    }

    @Test
    public void rendersMachinePathChat() {
        OrientationBarView bar = view();
        bar.render(new OrientationLine("arthur@arch-pc", "/home/arthur/dev", "barra", null, null));
        List<String> texts = texts(bar);
        assertEquals(3, texts.size());
        assertEquals("arthur@arch-pc", texts.get(0));
        assertEquals("/home/arthur/dev", texts.get(1));
        assertEquals("barra", texts.get(2));
    }

    @Test
    public void hidesLimitsWhenNull() {
        OrientationBarView bar = view();
        bar.render(new OrientationLine("tlnix", "/data/x", null, null, null));
        assertEquals(2, bar.visibleChips().size());
        bar.render(new OrientationLine("tlnix", "/data/x", null, 42, 10));
        List<String> texts = texts(bar);
        assertEquals(4, texts.size());
        assertTrue(texts.contains("42%"));
        assertTrue(texts.contains("sem 10%"));
    }

    @Test
    public void hotChipUsesRed() {
        OrientationBarView bar = view();
        bar.render(new OrientationLine("tlnix", null, null, 81, 10));
        OrientationBarView.Chip fiveHour = null, weekly = null;
        for (OrientationBarView.Chip chip : bar.visibleChips()) {
            if ("81%".contentEquals(chip.text.getText())) fiveHour = chip;
            if ("sem 10%".contentEquals(chip.text.getText())) weekly = chip;
        }
        assertEquals(OrientationBarView.RED, fiveHour.text.getCurrentTextColor());
        assertNotEquals(OrientationBarView.RED, weekly.text.getCurrentTextColor());
        assertEquals(View.VISIBLE, fiveHour.root.getVisibility());
    }
}
