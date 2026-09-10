package com.termux.app.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
public class VoicePostProcessingPreferencesTest {

    private Context mContext;
    private VoicePostProcessingPreferences mPreferences;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        mPreferences = new VoicePostProcessingPreferences(mContext);
        mPreferences.sharedPreferences().edit().clear().commit();
    }

    private SharedPreferences prefs() {
        return mContext.getSharedPreferences(VoicePostProcessingPreferences.PREFS_NAME,
            Context.MODE_PRIVATE);
    }

    @Test
    public void anUnconfiguredInstallLoadsSafeDefaultsAndStaysDisabled() {
        VoiceSettings settings = mPreferences.load();

        assertFalse(settings.postProcessingEnabled());
        assertEquals(VoiceSettings.DEFAULT_TEXT_MODEL, settings.textModel());
        assertEquals(VoiceSettings.DEFAULT_SPEECH_MODEL, settings.speechModel());
        assertEquals(VoiceSettings.DEFAULT_LANGUAGE, settings.language());
        assertEquals(VoiceSettings.DEFAULT_CORRECTION_PROMPT, settings.correctionPrompt());
        assertEquals(VoiceSettings.DEFAULT_TERMINAL_PROMPT, settings.terminalPrompt());
        assertEquals(VoiceSettings.DEFAULT_OUTPUT_PROMPT, settings.outputPrompt());
        assertTrue(settings.showTerminalMode());
    }

    @Test
    public void anEmptyPromptFallsBackToItsDefaultInsteadOfSendingNothing() {
        prefs().edit().putString(VoicePostProcessingPreferences.KEY_CORRECTION_PROMPT, "   ").commit();
        assertEquals(VoiceSettings.DEFAULT_CORRECTION_PROMPT,
            mPreferences.load().correctionPrompt());
    }

    @Test
    public void editedPromptsAndModelsSurviveAReload() {
        prefs().edit()
            .putBoolean(VoicePostProcessingPreferences.KEY_ENABLED, true)
            .putString(VoicePostProcessingPreferences.KEY_TEXT_MODEL, "llama-fake")
            .putString(VoicePostProcessingPreferences.KEY_CORRECTION_PROMPT, "so corrija")
            .putBoolean(VoicePostProcessingPreferences.KEY_SHOW_TERMINAL_MODE, false)
            .commit();

        VoiceSettings settings = new VoicePostProcessingPreferences(mContext).load();

        assertTrue(settings.postProcessingEnabled());
        assertEquals("llama-fake", settings.textModel());
        assertEquals("so corrija", settings.correctionPrompt());
        assertFalse(settings.showTerminalMode());
    }

    @Test
    public void theTemperatureIsClampedAndSurvivesAHalfTypedValue() {
        prefs().edit().putString(VoicePostProcessingPreferences.KEY_TEMPERATURE, "0,7").commit();
        assertEquals(0.7f, mPreferences.load().temperature(), 0.0001f);

        prefs().edit().putString(VoicePostProcessingPreferences.KEY_TEMPERATURE, "9").commit();
        assertEquals(VoiceSettings.MAX_TEMPERATURE, mPreferences.load().temperature(), 0.0001f);

        prefs().edit().putString(VoicePostProcessingPreferences.KEY_TEMPERATURE, "abc").commit();
        assertEquals(VoiceSettings.DEFAULT_TEMPERATURE, mPreferences.load().temperature(), 0.0001f);
    }

    @Test
    public void vocabularyRoundTripsNormalised() {
        mPreferences.saveVocabulary(Vocabulary.of(Arrays.asList("TLNix", "tlnix", " Groq ")));
        assertEquals(Arrays.asList("TLNix", "Groq"),
            new VoicePostProcessingPreferences(mContext).loadVocabulary().terms());
    }

    @Test
    public void theKeyNeverEntersThePreferenceFile() {
        mPreferences.setPostProcessingEnabled(true);
        try {
            new GroqSecretStore(mContext).setKey("gsk_super_secret");
        } catch (IllegalStateException expectedOffDevice) {
            // Robolectric has no AndroidKeyStore provider. Storing may fail here; what this test
            // is about is that neither outcome puts anything of the key in the settings file.
        }

        for (Map.Entry<String, ?> entry : prefs().getAll().entrySet()) {
            Object value = entry.getValue();
            assertFalse("preference " + entry.getKey() + " holds the key",
                value instanceof String && ((String) value).contains("gsk_super_secret"));
        }
    }

    @Test
    public void theSettingsSnapshotCarriesNoKeyAtAll() {
        // The absence is structural: VoiceSettings has no field for it, so nothing that logs or
        // serialises a snapshot can leak one.
        for (java.lang.reflect.Field field : VoiceSettings.class.getDeclaredFields()) {
            assertFalse(field.getName().toLowerCase(java.util.Locale.ROOT).contains("key"));
        }
    }
}
