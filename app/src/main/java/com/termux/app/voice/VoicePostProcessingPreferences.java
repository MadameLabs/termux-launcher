package com.termux.app.voice;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

/**
 * Loads and saves everything about voice post-processing except the key.
 *
 * <p>It shares the preference file with the settings screen, so the screen writes plain
 * {@code androidx.preference} values and this class reads them back into an immutable
 * {@link VoiceSettings}. The key is deliberately absent: it belongs to {@link GroqSecretStore}, and
 * keeping it out of this file is what lets the preferences be dumped for debugging.
 */
public final class VoicePostProcessingPreferences {

    public static final String PREFS_NAME = "termux_voice";

    public static final String KEY_ENABLED = "voice_post_processing_enabled";
    public static final String KEY_TEXT_MODEL = "voice_text_model";
    public static final String KEY_SPEECH_MODEL = "voice_speech_model";
    public static final String KEY_LANGUAGE = "voice_language";
    public static final String KEY_TEMPERATURE = "voice_temperature";
    public static final String KEY_CORRECTION_PROMPT = "voice_correction_prompt";
    public static final String KEY_SHORTEN_PROMPT = "voice_shorten_prompt";
    public static final String KEY_EMOJI_PROMPT = "voice_emoji_prompt";
    public static final String KEY_TERMINAL_PROMPT = "voice_terminal_prompt";
    public static final String KEY_OUTPUT_PROMPT = "voice_output_prompt";
    public static final String KEY_SHOW_TERMINAL_MODE = "voice_show_terminal_mode";
    public static final String KEY_VOCABULARY = "voice_vocabulary";

    /** Where the settings screen puts the key before it is moved into the Keystore. */
    public static final String KEY_API_KEY_INPUT = "voice_api_key_input";

    @NonNull private final SharedPreferences mPreferences;

    public VoicePostProcessingPreferences(@NonNull Context context) {
        mPreferences = context.getApplicationContext()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    @NonNull
    public SharedPreferences sharedPreferences() { return mPreferences; }

    @NonNull
    public VoiceSettings load() {
        return VoiceSettings.builder()
            .postProcessingEnabled(mPreferences.getBoolean(KEY_ENABLED, false))
            .textModel(mPreferences.getString(KEY_TEXT_MODEL, null))
            .speechModel(mPreferences.getString(KEY_SPEECH_MODEL, null))
            .language(mPreferences.getString(KEY_LANGUAGE, null))
            .temperature(readTemperature())
            .correctionPrompt(mPreferences.getString(KEY_CORRECTION_PROMPT, null))
            .shortenPrompt(mPreferences.getString(KEY_SHORTEN_PROMPT, null))
            .emojiPrompt(mPreferences.getString(KEY_EMOJI_PROMPT, null))
            .terminalPrompt(mPreferences.getString(KEY_TERMINAL_PROMPT, null))
            .outputPrompt(mPreferences.getString(KEY_OUTPUT_PROMPT, null))
            .showTerminalMode(mPreferences.getBoolean(KEY_SHOW_TERMINAL_MODE, true))
            .build();
    }

    @NonNull
    public Vocabulary loadVocabulary() {
        return Vocabulary.parse(mPreferences.getString(KEY_VOCABULARY, null));
    }

    public void saveVocabulary(@NonNull Vocabulary vocabulary) {
        mPreferences.edit().putString(KEY_VOCABULARY, vocabulary.asEditableText()).apply();
    }

    public void setPostProcessingEnabled(boolean enabled) {
        mPreferences.edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    /**
     * The temperature is edited as free text, so a half-typed value must not crash the flow — an
     * unparseable one falls back to the safe default.
     */
    private float readTemperature() {
        Object raw = mPreferences.getAll().get(KEY_TEMPERATURE);
        if (raw instanceof Float) return (Float) raw;
        if (raw instanceof String) {
            try {
                return Float.parseFloat(((String) raw).trim().replace(',', '.'));
            } catch (NumberFormatException ignored) {
                return VoiceSettings.DEFAULT_TEMPERATURE;
            }
        }
        return VoiceSettings.DEFAULT_TEMPERATURE;
    }
}
