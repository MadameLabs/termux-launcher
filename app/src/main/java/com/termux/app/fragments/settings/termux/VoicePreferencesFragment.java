package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;

import com.termux.R;
import com.termux.app.fragments.settings.MaterialPreferenceFragment;
import com.termux.app.fragments.settings.SettingsLayoutUtils;
import com.termux.app.notice.AppNotice;
import com.termux.app.voice.GroqSecretStore;
import com.termux.app.voice.VoicePostProcessingPreferences;
import com.termux.app.voice.VoiceSettings;
import com.termux.app.voice.Vocabulary;

/** Settings page for voice dictation and its post-processing modes. */
@Keep
public class VoicePreferencesFragment extends MaterialPreferenceFragment {

    @Nullable private GroqSecretStore mSecretStore;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null) return;

        mSecretStore = new GroqSecretStore(context);
        // Everything on this screen lives in the voice preference file, which is also the file
        // VoicePostProcessingPreferences reads: one place, no mirroring.
        getPreferenceManager().setSharedPreferencesName(VoicePostProcessingPreferences.PREFS_NAME);
        setPreferencesFromResource(R.xml.termux_voice_preferences, rootKey);
        SettingsLayoutUtils.applyScreenLayout(this);

        bindApiKey();
        applyDefaultHint(VoicePostProcessingPreferences.KEY_SPEECH_MODEL,
            VoiceSettings.DEFAULT_SPEECH_MODEL);
        applyDefaultHint(VoicePostProcessingPreferences.KEY_TEXT_MODEL,
            VoiceSettings.DEFAULT_TEXT_MODEL);
        applyDefaultHint(VoicePostProcessingPreferences.KEY_LANGUAGE,
            VoiceSettings.DEFAULT_LANGUAGE);
        applyDefaultHint(VoicePostProcessingPreferences.KEY_TEMPERATURE,
            String.valueOf(VoiceSettings.DEFAULT_TEMPERATURE));
        bindVocabulary();
    }

    /**
     * The key field is write-only on purpose: it never shows what is stored, and what it accepts
     * goes straight into the Keystore instead of into the preference file.
     */
    private void bindApiKey() {
        EditTextPreference preference = findPreference(VoicePostProcessingPreferences.KEY_API_KEY_INPUT);
        if (preference == null || mSecretStore == null) return;
        GroqSecretStore store = mSecretStore;

        preference.setText("");
        updateApiKeySummary(preference);
        preference.setOnBindEditTextListener(editText -> editText.setText(""));
        preference.setOnPreferenceChangeListener((changed, newValue) -> {
            String value = newValue == null ? "" : newValue.toString().trim();
            store.setKey(value);
            AppNotice.show(requireContext(), value.isEmpty()
                ? R.string.voice_settings_api_key_cleared : R.string.voice_settings_api_key_saved, false);
            updateApiKeySummary((EditTextPreference) changed);
            // Returning false keeps the framework from persisting the key alongside the settings.
            return false;
        });
    }

    private void updateApiKeySummary(@NonNull EditTextPreference preference) {
        boolean hasKey = mSecretStore != null && mSecretStore.hasKey();
        preference.setSummary(hasKey
            ? R.string.voice_settings_api_key_summary_set
            : R.string.voice_settings_api_key_summary_unset);
    }

    /** Shows the value that will actually be used while the field is still empty. */
    private void applyDefaultHint(@NonNull String key, @NonNull String defaultValue) {
        EditTextPreference preference = findPreference(key);
        if (preference == null) return;
        preference.setSummaryProvider(p -> {
            CharSequence text = ((EditTextPreference) p).getText();
            return (text == null || text.toString().trim().isEmpty()) ? defaultValue : text;
        });
    }

    /** Normalises on save so the stored list is the one the dictation will really use. */
    private void bindVocabulary() {
        Preference preference = findPreference(VoicePostProcessingPreferences.KEY_VOCABULARY);
        if (preference == null) return;
        preference.setOnPreferenceChangeListener((changed, newValue) -> {
            String normalised = Vocabulary.parse(newValue == null ? "" : newValue.toString())
                .asEditableText();
            ((EditTextPreference) changed).setText(normalised);
            return false;
        });
    }
}
