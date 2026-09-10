package com.termux.app.voice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** What a finished dictation produced, and what of it reaches the terminal. */
public final class VoiceResult {

    /** Why a dictation delivered less than it promised. Never carries content or credentials. */
    public enum Warning {
        /** A transcript exists but the transformation could not run; raw text was delivered. */
        POST_PROCESSING_FAILED,
        /** The transformation answered with nothing usable; raw text was delivered. */
        POST_PROCESSING_EMPTY
    }

    @NonNull private final String mRawTranscript;
    @Nullable private final String mProcessedText;
    @Nullable private final Warning mWarning;

    private VoiceResult(@NonNull String rawTranscript, @Nullable String processedText,
                        @Nullable Warning warning) {
        mRawTranscript = rawTranscript;
        mProcessedText = processedText;
        mWarning = warning;
    }

    public static VoiceResult raw(@NonNull String rawTranscript) {
        return new VoiceResult(rawTranscript, null, null);
    }

    public static VoiceResult processed(@NonNull String rawTranscript, @NonNull String processedText) {
        return new VoiceResult(rawTranscript, processedText, null);
    }

    /** A transformation was asked for and did not deliver; the raw transcript is not lost. */
    public static VoiceResult fallback(@NonNull String rawTranscript, @NonNull Warning warning) {
        return new VoiceResult(rawTranscript, null, warning);
    }

    @NonNull public String rawTranscript() { return mRawTranscript; }
    @Nullable public String processedText() { return mProcessedText; }
    @Nullable public Warning warning() { return mWarning; }

    /** Whether a transformation actually replaced the transcript — what makes terminal mode reset. */
    public boolean wasTransformed() { return mProcessedText != null; }

    /** The single string written to the terminal session. */
    @NonNull
    public String deliveryText() {
        return mProcessedText != null ? mProcessedText : mRawTranscript;
    }
}
