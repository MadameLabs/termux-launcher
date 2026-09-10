package com.termux.app.voice;

/**
 * The transformation applied to a dictation after transcription.
 *
 * <p>{@link #RAW} is not a transformation: it delivers the transcript untouched and never calls
 * the post-processing endpoint.
 */
public enum VoiceMode {
    RAW,
    CORRECTION,
    SHORTEN,
    TERMINAL;

    /** Whether this mode needs a chat completion round after transcription. */
    public boolean requiresPostProcessing() {
        return this != RAW;
    }
}
