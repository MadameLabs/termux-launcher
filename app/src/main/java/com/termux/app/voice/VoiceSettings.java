package com.termux.app.voice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The non-secret half of the voice configuration, frozen at the moment a recording starts.
 *
 * <p>The Groq key never lives here: it is fetched from {@link GroqSecretStore} at request time so
 * it cannot reach a snapshot, a log line or a crash report.
 *
 * <p>Prompt text is not defaulted here either. The defaults are localized string resources, so
 * they arrive already resolved from {@link VoicePostProcessingPreferences} — an instruction and
 * the dictation it transforms should speak the same language. A prompt that still arrives blank
 * is simply omitted from the request rather than silently replaced.
 */
public final class VoiceSettings {

    public static final String DEFAULT_TEXT_MODEL = "llama-3.3-70b-versatile";
    public static final String DEFAULT_SPEECH_MODEL = "whisper-large-v3-turbo";
    public static final String DEFAULT_LANGUAGE = "pt";
    public static final float DEFAULT_TEMPERATURE = 0.2f;
    public static final float MIN_TEMPERATURE = 0f;
    public static final float MAX_TEMPERATURE = 1f;


    private final boolean mPostProcessingEnabled;
    @NonNull private final String mTextModel;
    @NonNull private final String mSpeechModel;
    @NonNull private final String mLanguage;
    private final float mTemperature;
    @NonNull private final String mCorrectionPrompt;
    @NonNull private final String mShortenPrompt;
    @NonNull private final String mEmojiPrompt;
    @NonNull private final String mTerminalPrompt;
    @NonNull private final String mOutputPrompt;
    private final boolean mShowTerminalMode;

    private VoiceSettings(Builder builder) {
        mPostProcessingEnabled = builder.mPostProcessingEnabled;
        mTextModel = orDefault(builder.mTextModel, DEFAULT_TEXT_MODEL);
        mSpeechModel = orDefault(builder.mSpeechModel, DEFAULT_SPEECH_MODEL);
        mLanguage = orDefault(builder.mLanguage, DEFAULT_LANGUAGE);
        mTemperature = clampTemperature(builder.mTemperature);
        mCorrectionPrompt = trimmed(builder.mCorrectionPrompt);
        mShortenPrompt = trimmed(builder.mShortenPrompt);
        mEmojiPrompt = trimmed(builder.mEmojiPrompt);
        mTerminalPrompt = trimmed(builder.mTerminalPrompt);
        mOutputPrompt = trimmed(builder.mOutputPrompt);
        mShowTerminalMode = builder.mShowTerminalMode;
    }

    public static Builder builder() { return new Builder(); }

    public static VoiceSettings defaults() { return builder().build(); }

    public static float clampTemperature(float temperature) {
        if (Float.isNaN(temperature)) return DEFAULT_TEMPERATURE;
        if (temperature < MIN_TEMPERATURE) return MIN_TEMPERATURE;
        if (temperature > MAX_TEMPERATURE) return MAX_TEMPERATURE;
        return temperature;
    }

    private static String orDefault(@Nullable String value, @NonNull String fallback) {
        return (value == null || value.trim().isEmpty()) ? fallback : value.trim();
    }

    private static String trimmed(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    public boolean postProcessingEnabled() { return mPostProcessingEnabled; }
    @NonNull public String textModel() { return mTextModel; }
    @NonNull public String speechModel() { return mSpeechModel; }
    @NonNull public String language() { return mLanguage; }
    public float temperature() { return mTemperature; }
    @NonNull public String correctionPrompt() { return mCorrectionPrompt; }
    @NonNull public String shortenPrompt() { return mShortenPrompt; }
    @NonNull public String emojiPrompt() { return mEmojiPrompt; }
    @NonNull public String terminalPrompt() { return mTerminalPrompt; }
    @NonNull public String outputPrompt() { return mOutputPrompt; }
    public boolean showTerminalMode() { return mShowTerminalMode; }

    /** The prompt that describes {@code mode}, already defaulted when the person left it blank. */
    @NonNull
    public String promptFor(@NonNull VoiceMode mode) {
        switch (mode) {
            case CORRECTION: return mCorrectionPrompt;
            case SHORTEN: return mShortenPrompt;
            case TERMINAL: return mTerminalPrompt;
            default: return "";
        }
    }

    public static final class Builder {
        private boolean mPostProcessingEnabled;
        @Nullable private String mTextModel;
        @Nullable private String mSpeechModel;
        @Nullable private String mLanguage;
        private float mTemperature = DEFAULT_TEMPERATURE;
        @Nullable private String mCorrectionPrompt;
        @Nullable private String mShortenPrompt;
        @Nullable private String mEmojiPrompt;
        @Nullable private String mTerminalPrompt;
        @Nullable private String mOutputPrompt;
        private boolean mShowTerminalMode = true;

        public Builder postProcessingEnabled(boolean value) { mPostProcessingEnabled = value; return this; }
        public Builder textModel(@Nullable String value) { mTextModel = value; return this; }
        public Builder speechModel(@Nullable String value) { mSpeechModel = value; return this; }
        public Builder language(@Nullable String value) { mLanguage = value; return this; }
        public Builder temperature(float value) { mTemperature = value; return this; }
        public Builder correctionPrompt(@Nullable String value) { mCorrectionPrompt = value; return this; }
        public Builder shortenPrompt(@Nullable String value) { mShortenPrompt = value; return this; }
        public Builder emojiPrompt(@Nullable String value) { mEmojiPrompt = value; return this; }
        public Builder terminalPrompt(@Nullable String value) { mTerminalPrompt = value; return this; }
        public Builder outputPrompt(@Nullable String value) { mOutputPrompt = value; return this; }
        public Builder showTerminalMode(boolean value) { mShowTerminalMode = value; return this; }

        public VoiceSettings build() { return new VoiceSettings(this); }
    }
}
