package com.termux.app.terminal.inappkeyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.app.Application;
import android.content.res.Resources;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import juloo.keyboard2.KeyValue;
import juloo.keyboard2.KeyboardData;
import juloo.keyboard2.LayoutModifier;

/**
 * The layouts as the app shows them, after {@code LayoutModifier.modify} with the default extra
 * keys: nothing lands on the layouts key's free corners, the #+= pad's symbol corners stay as
 * drawn, and the Greek/math pad keeps Esc and Tab even with those extra keys turned off.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class ComposedLayoutsKeyTest {

    private static final int[] LAYOUTS = {
        juloo.keyboard2.R.xml.termux_launcher_minimal, juloo.keyboard2.R.xml.termux_launcher_qwerty,
        juloo.keyboard2.R.xml.full_qwerty, juloo.keyboard2.R.xml.symbols,
        juloo.keyboard2.R.xml.numeric, juloo.keyboard2.R.xml.greekmath,
    };

    private final Resources resources = RuntimeEnvironment.getApplication().getResources();

    @Test
    public void defaultExtraKeysLeaveTheLayoutsKeyAlone() {
        for (int id : LAYOUTS) {
            KeyboardData raw = KeyboardData.load(resources, id);
            KeyboardData shown = compose(raw, InAppKeyboardExtraKeys.defaultStoredValue());
            KeyboardData.Key before = layoutsKey(raw);
            KeyboardData.Key after = layoutsKey(shown);
            for (int i = 0; i < 9; i++)
                assertEquals(resources.getResourceEntryName(id) + " slot " + i,
                    before.getKeyValue(i), after.getKeyValue(i));
        }
    }

    @Test
    public void symbolsPadTakesNoExtraKeysOnItsSymbols() {
        KeyboardData raw = KeyboardData.load(resources, juloo.keyboard2.R.xml.symbols);
        KeyboardData shown = compose(raw, InAppKeyboardExtraKeys.defaultStoredValue());
        for (int r = 0; r < 3; r++)
            for (int k = 0; k < raw.rows.get(r).keys.size(); k++)
                for (int i = 0; i < 9; i++)
                    assertEquals("row " + r + " key " + k + " slot " + i,
                        raw.rows.get(r).keys.get(k).getKeyValue(i),
                        shown.rows.get(r).keys.get(k).getKeyValue(i));
        // Esc and Tab, the terminal's own, are still on the pad: on Backspace's left corners.
        assertNotNull(keyWith(shown, "esc"));
        assertNotNull(keyWith(shown, "tab"));
    }

    @Test
    public void greekPadKeepsEscAndTabWithThoseExtraKeysOff() {
        KeyboardData shown = compose(KeyboardData.load(resources, juloo.keyboard2.R.xml.greekmath), "");
        assertNotNull("Esc", keyWith(shown, "esc"));
        assertNotNull("Tab", keyWith(shown, "tab"));
    }

    private KeyboardData compose(KeyboardData raw, String extraKeys) {
        return LayoutModifier.modify(raw, new LayoutModifier.LayoutOptions(true, false, true,
            InAppKeyboardExtraKeys.resolve(extraKeys)), resources);
    }

    /** The key whose centre is switch_full or switch_text and which has a switch corner. */
    private static KeyboardData.Key layoutsKey(KeyboardData kb) {
        KeyValue greek = KeyValue.getKeyByName("switch_greekmath");
        KeyValue full = KeyValue.getKeyByName("switch_full");
        for (KeyboardData.Row row : kb.rows)
            for (KeyboardData.Key key : row.keys)
                for (int i = 1; i < 5; i++)
                    if (greek.equals(key.getKeyValue(i))
                        || (full.equals(key.getKeyValue(i)) && i == 1))
                        return key;
        throw new AssertionError("no layouts key");
    }

    private static KeyboardData.Key keyWith(KeyboardData kb, String name) {
        KeyValue wanted = KeyValue.getKeyByName(name);
        for (KeyboardData.Row row : kb.rows)
            for (KeyboardData.Key key : row.keys)
                for (int i = 0; i < 9; i++)
                    if (wanted.equals(key.getKeyValue(i)))
                        return key;
        return null;
    }
}
