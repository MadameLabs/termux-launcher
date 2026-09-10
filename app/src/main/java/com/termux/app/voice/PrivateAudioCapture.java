package com.termux.app.voice;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.IOException;

/**
 * Records a short dictation into the app's private cache and nowhere else.
 *
 * <p>The file lives under {@code cacheDir/voice}, which no other app and no file manager can read,
 * and the orchestrator deletes it on every terminal path. AAC in an MP4 container is used because
 * it is what every supported Android release encodes natively and what the transcription endpoint
 * accepts without conversion.
 */
public final class PrivateAudioCapture
        implements VoiceOrchestrator.Recorder, VoiceOrchestrator.AudioFileFactory {

    private static final String DIRECTORY = "voice";
    private static final String PREFIX = "dictation-";
    private static final String SUFFIX = ".m4a";
    private static final int SAMPLE_RATE_HZ = 16_000;
    private static final int BIT_RATE = 64_000;

    @NonNull private final Context mContext;
    @Nullable private MediaRecorder mRecorder;

    public PrivateAudioCapture(@NonNull Context context) {
        mContext = context.getApplicationContext();
    }

    /** Whether the microphone may be used right now. The caller asks for it when this is false. */
    public boolean hasMicrophonePermission() {
        return ContextCompat.checkSelfPermission(mContext, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED;
    }

    @NonNull
    @Override
    public File create() throws VoiceException {
        File directory = new File(mContext.getCacheDir(), DIRECTORY);
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new VoiceException(VoiceFailure.NO_AUDIO);
        }
        // A leftover from a process killed mid-dictation must not survive into a later session.
        deleteStaleFiles(directory);
        return new File(directory, PREFIX + System.currentTimeMillis() + SUFFIX);
    }

    @Override
    public void start(@NonNull File output) throws VoiceException {
        if (!hasMicrophonePermission()) throw new VoiceException(VoiceFailure.MICROPHONE);
        releaseRecorder();

        MediaRecorder recorder = newRecorder();
        try {
            recorder.setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioChannels(1);
            recorder.setAudioSamplingRate(SAMPLE_RATE_HZ);
            recorder.setAudioEncodingBitRate(BIT_RATE);
            recorder.setOutputFile(output.getAbsolutePath());
            recorder.prepare();
            recorder.start();
        } catch (IOException | RuntimeException e) {
            recorder.release();
            throw new VoiceException(VoiceFailure.MICROPHONE);
        }
        mRecorder = recorder;
    }

    @Override
    public void stop() throws VoiceException {
        MediaRecorder recorder = mRecorder;
        mRecorder = null;
        if (recorder == null) throw new VoiceException(VoiceFailure.NO_AUDIO);
        try {
            recorder.stop();
        } catch (RuntimeException e) {
            // MediaRecorder throws when it is stopped before it captured anything usable.
            throw new VoiceException(VoiceFailure.NO_AUDIO);
        } finally {
            recorder.release();
        }
    }

    @Override
    public void cancel() {
        releaseRecorder();
    }

    private void releaseRecorder() {
        MediaRecorder recorder = mRecorder;
        mRecorder = null;
        if (recorder == null) return;
        try {
            recorder.stop();
        } catch (RuntimeException ignored) {
            // Cancelling before the encoder produced a frame is ordinary, not an error.
        }
        recorder.release();
    }

    @SuppressWarnings("deprecation")
    private MediaRecorder newRecorder() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            ? new MediaRecorder(mContext)
            : new MediaRecorder();
    }

    private static void deleteStaleFiles(@NonNull File directory) {
        File[] files = directory.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isFile() && !file.delete()) file.deleteOnExit();
        }
    }
}
