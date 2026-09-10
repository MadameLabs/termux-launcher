package com.termux.app.voice;

import androidx.annotation.NonNull;

import java.io.File;

/**
 * Everything a single recording is allowed to depend on, frozen the moment it starts.
 *
 * <p>The snapshot is what keeps a dictation honest: changing the mode, editing a prompt or
 * switching terminal panel while the person is still speaking cannot retarget or reshape the
 * dictation already in flight.
 */
public final class VoiceRequest {

    @NonNull private final VoiceOrchestrator.SessionTarget mTarget;
    @NonNull private final ModeSelection mModeSelection;
    @NonNull private final VoiceSettings mSettings;
    @NonNull private final Vocabulary mVocabulary;
    @NonNull private final File mAudioFile;
    private final long mStartedAtMillis;

    public VoiceRequest(@NonNull VoiceOrchestrator.SessionTarget target,
                        @NonNull ModeSelection modeSelection,
                        @NonNull VoiceSettings settings,
                        @NonNull Vocabulary vocabulary,
                        @NonNull File audioFile,
                        long startedAtMillis) {
        mTarget = target;
        mModeSelection = modeSelection;
        mSettings = settings;
        mVocabulary = vocabulary;
        mAudioFile = audioFile;
        mStartedAtMillis = startedAtMillis;
    }

    @NonNull public VoiceOrchestrator.SessionTarget target() { return mTarget; }
    @NonNull public ModeSelection modeSelection() { return mModeSelection; }
    @NonNull public VoiceSettings settings() { return mSettings; }
    @NonNull public Vocabulary vocabulary() { return mVocabulary; }
    @NonNull public File audioFile() { return mAudioFile; }
    public long startedAtMillis() { return mStartedAtMillis; }
}
