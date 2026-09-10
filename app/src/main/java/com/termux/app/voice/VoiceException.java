package com.termux.app.voice;

import androidx.annotation.NonNull;

/**
 * A dictation failure carrying only its {@link VoiceFailure} category.
 *
 * <p>The message is the category name and nothing else. That is the point: a stack trace from this
 * flow must never be able to leak a key, a header or a piece of what was said.
 */
public final class VoiceException extends Exception {

    @NonNull private final VoiceFailure mFailure;

    public VoiceException(@NonNull VoiceFailure failure) {
        super(failure.name());
        mFailure = failure;
    }

    @NonNull public VoiceFailure failure() { return mFailure; }
}
