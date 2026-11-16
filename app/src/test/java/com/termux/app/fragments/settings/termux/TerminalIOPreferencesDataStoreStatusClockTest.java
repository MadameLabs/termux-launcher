package com.termux.app.fragments.settings.termux;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.os.Build;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class TerminalIOPreferencesDataStoreStatusClockTest {

    /**
     * MadameLabs fork: Settings › Status bar › Show clock writes the same key as the Status bar
     * card's Clock toggle in the appearance editor, so the two never disagree.
     */
    @Test
    public void showClockRoundTripsThroughTheDataStore() {
        Context context = RuntimeEnvironment.getApplication();
        TerminalIOPreferencesDataStore store = TerminalIOPreferencesDataStore.getInstance(context);

        assertTrue("shown by default, as upstream", store.getBoolean("status_clock_shown", false));

        store.putBoolean("status_clock_shown", false);
        assertFalse(store.getBoolean("status_clock_shown", true));
        assertFalse(TermuxAppSharedPreferences.build(context).isStatusClockShown());

        store.putBoolean("status_clock_shown", true);
        assertTrue(TermuxAppSharedPreferences.build(context).isStatusClockShown());
    }

    /** MadameLabs fork: the context chips' place toggles through the same store. */
    @Test
    public void chipsInRowRoundTripsThroughTheDataStore() {
        Context context = RuntimeEnvironment.getApplication();
        TerminalIOPreferencesDataStore store = TerminalIOPreferencesDataStore.getInstance(context);

        assertFalse("the clock's cell by default", store.getBoolean("orientation_chips_in_row", true));
        store.putBoolean("orientation_chips_in_row", true);
        assertTrue(TermuxAppSharedPreferences.build(context).isContextChipsInRow());
        store.putBoolean("orientation_chips_in_row", false);
        assertFalse(TermuxAppSharedPreferences.build(context).isContextChipsInRow());
    }
}
