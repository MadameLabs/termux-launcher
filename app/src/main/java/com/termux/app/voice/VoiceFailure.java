package com.termux.app.voice;

import androidx.annotation.NonNull;

/**
 * Why a dictation could not finish, in categories a person can act on.
 *
 * <p>Deliberately coarse: the categories are what reaches the screen and the log, so none of them
 * may carry a transcript, a request body, a header or a key.
 */
public enum VoiceFailure {
    /** The microphone permission was denied, or no microphone answered. */
    MICROPHONE,
    /** Nothing was recorded — the gesture ended before any audio existed. */
    NO_AUDIO,
    /** No Groq key is configured yet. */
    MISSING_KEY,
    /** Groq rejected the key. */
    CREDENTIAL,
    /** Groq answered with a rate limit or an exhausted quota. */
    RATE_LIMIT,
    /** The request never reached Groq, or the answer never came back. */
    NETWORK,
    /** Groq answered, but not with anything this flow can use. */
    SERVICE,
    /** The transcription came back empty: there is no text to insert. */
    EMPTY_TRANSCRIPT,
    /** The terminal session that started the dictation is gone. */
    SESSION_GONE;

    /** Maps an HTTP status to the category shown to the person. */
    @NonNull
    public static VoiceFailure fromHttpStatus(int status) {
        if (status == 401 || status == 403) return CREDENTIAL;
        if (status == 429) return RATE_LIMIT;
        return SERVICE;
    }
}
