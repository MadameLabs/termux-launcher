package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceDataStore;

import com.termux.R;
import com.termux.app.fragments.settings.MaterialPreferenceFragment;
import com.termux.app.fragments.settings.SettingsLayoutUtils;
import com.termux.app.terminal.io.CommandBoxLook;

/**
 * MadameLabs fork: Settings › Terminal › Command box. Whether the box opens as a popup (the
 * switch that sat on the Status bar page), a door to its glass's own card in Appearance (the box
 * is an element of the look grid, ADR 0017, tuned there like every other glass), and its text's
 * colours and size. The popup reads these each time it opens ({@link CommandBoxLook}).
 */
@Keep
public final class CommandBoxPreferencesFragment extends MaterialPreferenceFragment {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null) return;
        getPreferenceManager().setPreferenceDataStore(new Store(context));
        setPreferencesFromResource(R.xml.command_box_preferences, rootKey);
        SettingsLayoutUtils.applyScreenLayout(this);
        androidx.preference.Preference glass = findPreference("command_box_glass");
        if (glass != null) glass.setOnPreferenceClickListener(row -> {
            // The box's own card in Appearance; closing the editor comes back here.
            SettingsEditorDoors.open(this, row, com.termux.app.surfaces.AppearanceEditorEntry
                .intent(context, com.termux.app.surfaces.EditorCard.COMMAND_BOX, true));
            return true;
        });
    }

    /** The popup switch goes to the launcher's preferences, as before; the text rows to the box's. */
    static final class Store extends PreferenceDataStore {
        @NonNull private final SharedPreferences mBox;
        @NonNull private final PreferenceDataStore mLauncher;

        Store(@NonNull Context context) {
            mBox = CommandBoxLook.prefs(context.getApplicationContext());
            mLauncher = TerminalIOPreferencesDataStore.getInstance(context);
        }

        @Override
        public void putBoolean(String key, boolean value) {
            if ("command_box_popup".equals(key)) mLauncher.putBoolean(key, value);
            else mBox.edit().putBoolean(key, value).apply();
        }

        @Override
        public boolean getBoolean(String key, boolean defValue) {
            if ("command_box_popup".equals(key)) return mLauncher.getBoolean(key, defValue);
            return mBox.getBoolean(key, defValue);
        }

        @Override
        public void putInt(String key, int value) {
            mBox.edit().putInt(key, value).apply();
        }

        @Override
        public int getInt(String key, int defValue) {
            return mBox.getInt(key, defValue);
        }

        @Override
        public void putString(String key, @Nullable String value) {
            mBox.edit().putString(key, value).apply();
        }

        @Nullable
        @Override
        public String getString(String key, @Nullable String defValue) {
            return mBox.getString(key, defValue);
        }
    }
}
