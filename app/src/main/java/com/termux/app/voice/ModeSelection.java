package com.termux.app.voice;

import androidx.annotation.NonNull;

/**
 * What the next recording will do with its transcript.
 *
 * <p>The exclusivity rules come from the feature contract: correction and shortening are
 * alternatives to each other, emoji decorates any of them, and terminal owns the whole
 * transformation because its output must stay a bare command line.
 *
 * <p>Instances are immutable; every mutator returns a new selection.
 */
public final class ModeSelection {

    public static final ModeSelection RAW = new ModeSelection(VoiceMode.RAW, false);

    @NonNull private final VoiceMode mPrimary;
    private final boolean mEmojiEnabled;

    private ModeSelection(@NonNull VoiceMode primary, boolean emojiEnabled) {
        mPrimary = primary;
        // Terminal output is a command line: an emoji in it would be a syntax error waiting to run.
        mEmojiEnabled = emojiEnabled && primary != VoiceMode.TERMINAL;
    }

    public static ModeSelection of(@NonNull VoiceMode primary, boolean emojiEnabled) {
        return new ModeSelection(primary, emojiEnabled);
    }

    @NonNull public VoiceMode primary() { return mPrimary; }

    public boolean emojiEnabled() { return mEmojiEnabled; }

    /** Whether anything at all has to be sent to the post-processing endpoint. */
    public boolean requiresPostProcessing() {
        return mPrimary.requiresPostProcessing() || mEmojiEnabled;
    }

    /** Selecting a primary mode replaces the previous one; picking the active one clears it. */
    public ModeSelection withPrimary(@NonNull VoiceMode primary) {
        VoiceMode next = (primary == mPrimary) ? VoiceMode.RAW : primary;
        return new ModeSelection(next, mEmojiEnabled);
    }

    public ModeSelection withEmoji(boolean emojiEnabled) {
        return new ModeSelection(mPrimary, emojiEnabled);
    }

    public ModeSelection toggleEmoji() {
        return withEmoji(!mEmojiEnabled);
    }

    /**
     * The selection that survives into the next recording. Terminal is single use, so a successful
     * terminal transformation drops it; every other mode is sticky until the person changes it.
     */
    public ModeSelection afterSuccess(boolean transformed) {
        if (mPrimary == VoiceMode.TERMINAL && transformed) {
            return new ModeSelection(VoiceMode.RAW, mEmojiEnabled);
        }
        return this;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ModeSelection)) return false;
        ModeSelection that = (ModeSelection) other;
        return mPrimary == that.mPrimary && mEmojiEnabled == that.mEmojiEnabled;
    }

    @Override
    public int hashCode() {
        return mPrimary.hashCode() * 31 + (mEmojiEnabled ? 1 : 0);
    }

    @NonNull
    @Override
    public String toString() {
        return "ModeSelection{" + mPrimary + (mEmojiEnabled ? "+emoji" : "") + "}";
    }
}
