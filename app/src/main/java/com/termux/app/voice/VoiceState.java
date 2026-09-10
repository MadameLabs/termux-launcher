package com.termux.app.voice;

/** Where a dictation is, for the indicator the person watches. */
public enum VoiceState {
    IDLE,
    RECORDING,
    TRANSCRIBING,
    POST_PROCESSING,
    DELIVERING
}
