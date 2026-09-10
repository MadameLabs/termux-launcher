package com.termux.app.voice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.concurrent.Executor;

/**
 * Runs one dictation from the gesture to the terminal write.
 *
 * <p>It owns the state machine described in the feature contract and nothing else: audio, network
 * and secrets arrive as collaborators, which is what makes the two rules that matter — the raw
 * transcript is never lost, and the audio file is always deleted — testable without a device.
 */
public final class VoiceOrchestrator {

    /** The terminal session captured when the gesture began. */
    public interface SessionTarget {
        /** Whether this session is still the live one it was at the start of the recording. */
        boolean isValid();

        /** Writes the finished text. Never sends Enter and never runs anything. */
        void write(@NonNull String text);
    }

    /** Records into a private file. Implementations own the microphone permission check. */
    public interface Recorder {
        void start(@NonNull File output) throws VoiceException;

        /** Closes the file so it can be uploaded. */
        void stop() throws VoiceException;

        /** Abandons the recording; the file is deleted by the orchestrator either way. */
        void cancel();
    }

    /** Supplies the Groq key at request time so it never enters a snapshot. */
    public interface KeyProvider {
        @Nullable String apiKey();
    }

    /** Creates the private, disposable file each recording writes into. */
    public interface AudioFileFactory {
        @NonNull File create() throws VoiceException;
    }

    public interface Listener {
        void onStateChanged(@NonNull VoiceState state);

        /**
         * @param result what was produced, including the warning when a transformation was lost
         * @param nextSelection the selection to carry into the next recording
         */
        void onDelivered(@NonNull VoiceResult result, @NonNull ModeSelection nextSelection);

        void onCancelled();

        void onFailed(@NonNull VoiceFailure failure);
    }

    @NonNull private final Recorder mRecorder;
    @NonNull private final AudioFileFactory mAudioFileFactory;
    @NonNull private final GroqSpeechClient mSpeechClient;
    @NonNull private final GroqPostProcessingClient mPostProcessingClient;
    @NonNull private final KeyProvider mKeyProvider;
    @NonNull private final Executor mBackgroundExecutor;
    @NonNull private final Executor mCallbackExecutor;
    @NonNull private final Listener mListener;

    // Written on the background thread during a run and read on the UI thread by the panel, so
    // both need the visibility guarantee rather than just the atomicity of a reference write.
    @NonNull private volatile VoiceState mState = VoiceState.IDLE;
    @Nullable private volatile VoiceRequest mRequest;

    public VoiceOrchestrator(@NonNull Recorder recorder,
                             @NonNull AudioFileFactory audioFileFactory,
                             @NonNull GroqSpeechClient speechClient,
                             @NonNull GroqPostProcessingClient postProcessingClient,
                             @NonNull KeyProvider keyProvider,
                             @NonNull Executor backgroundExecutor,
                             @NonNull Executor callbackExecutor,
                             @NonNull Listener listener) {
        mRecorder = recorder;
        mAudioFileFactory = audioFileFactory;
        mSpeechClient = speechClient;
        mPostProcessingClient = postProcessingClient;
        mKeyProvider = keyProvider;
        mBackgroundExecutor = backgroundExecutor;
        mCallbackExecutor = callbackExecutor;
        mListener = listener;
    }

    @NonNull public VoiceState state() { return mState; }

    public boolean isBusy() { return mState != VoiceState.IDLE; }

    /**
     * Captures the snapshot and starts recording.
     *
     * <p>The key is checked here rather than after the person has spoken: failing before the
     * recording is far kinder than failing after it.
     */
    public void start(@NonNull SessionTarget target,
                      @NonNull ModeSelection modeSelection,
                      @NonNull VoiceSettings settings,
                      @NonNull Vocabulary vocabulary,
                      long startedAtMillis) {
        if (isBusy()) return;

        ModeSelection effective = settings.postProcessingEnabled() ? modeSelection : ModeSelection.RAW;
        if (mKeyProvider.apiKey() == null) {
            fail(VoiceFailure.MISSING_KEY);
            return;
        }

        File audioFile;
        try {
            audioFile = mAudioFileFactory.create();
        } catch (VoiceException e) {
            fail(e.failure());
            return;
        }

        VoiceRequest request =
            new VoiceRequest(target, effective, settings, vocabulary, audioFile, startedAtMillis);
        try {
            mRecorder.start(audioFile);
        } catch (VoiceException e) {
            discard(request);
            fail(e.failure());
            return;
        }

        mRequest = request;
        setState(VoiceState.RECORDING);
    }

    /** Abandons the recording: nothing is transcribed, nothing is written, the audio is deleted. */
    public void cancel() {
        VoiceRequest request = mRequest;
        if (request == null) {
            setState(VoiceState.IDLE);
            return;
        }
        mRequest = null;
        mRecorder.cancel();
        discard(request);
        setState(VoiceState.IDLE);
        mCallbackExecutor.execute(mListener::onCancelled);
    }

    /** Ends the recording and starts the asynchronous half of the flow. */
    public void finish() {
        VoiceRequest request = mRequest;
        if (request == null || mState != VoiceState.RECORDING) return;

        try {
            mRecorder.stop();
        } catch (VoiceException e) {
            mRequest = null;
            discard(request);
            failFrom(e.failure());
            return;
        }

        setState(VoiceState.TRANSCRIBING);
        mBackgroundExecutor.execute(() -> process(request));
    }

    private void process(@NonNull VoiceRequest request) {
        try {
            String apiKey = mKeyProvider.apiKey();
            if (apiKey == null) {
                failFrom(VoiceFailure.MISSING_KEY);
                return;
            }

            VoiceSettings settings = request.settings();
            String transcript = mSpeechClient.transcribe(request.audioFile(), apiKey,
                settings.speechModel(), settings.language(), request.vocabulary().asSpellingHint());

            VoiceResult result = request.modeSelection().requiresPostProcessing()
                ? postProcess(request, transcript, apiKey)
                : VoiceResult.raw(transcript);

            deliver(request, result);
        } catch (VoiceException e) {
            failFrom(e.failure());
        } finally {
            // Success, cancellation or failure: the audio does not outlive the request.
            mRequest = null;
            discard(request);
        }
    }

    /** A failed transformation costs the transformation, never the dictation. */
    private VoiceResult postProcess(@NonNull VoiceRequest request, @NonNull String transcript,
                                    @NonNull String apiKey) {
        setState(VoiceState.POST_PROCESSING);
        VoiceSettings settings = request.settings();
        try {
            String processed = mPostProcessingClient.transform(
                GroqPromptComposer.systemMessage(request.modeSelection(), settings, request.vocabulary()),
                GroqPromptComposer.userMessage(transcript),
                apiKey, settings.textModel(), settings.temperature());
            if (processed == null) {
                return VoiceResult.fallback(transcript, VoiceResult.Warning.POST_PROCESSING_EMPTY);
            }
            return VoiceResult.processed(transcript, processed);
        } catch (VoiceException e) {
            return VoiceResult.fallback(transcript, VoiceResult.Warning.POST_PROCESSING_FAILED);
        }
    }

    private void deliver(@NonNull VoiceRequest request, @NonNull VoiceResult result) {
        setState(VoiceState.DELIVERING);
        mCallbackExecutor.execute(() -> {
            SessionTarget target = request.target();
            if (!target.isValid()) {
                // Never redirect to whatever panel is in front now: that is someone else's prompt.
                mState = VoiceState.IDLE;
                mListener.onStateChanged(VoiceState.IDLE);
                mListener.onFailed(VoiceFailure.SESSION_GONE);
                return;
            }
            target.write(result.deliveryText());
            mState = VoiceState.IDLE;
            mListener.onStateChanged(VoiceState.IDLE);
            mListener.onDelivered(result,
                request.modeSelection().afterSuccess(result.wasTransformed()));
        });
    }

    private void discard(@NonNull VoiceRequest request) {
        File audio = request.audioFile();
        if (audio.exists() && !audio.delete()) audio.deleteOnExit();
    }

    private void fail(@NonNull VoiceFailure failure) {
        mState = VoiceState.IDLE;
        mCallbackExecutor.execute(() -> {
            mListener.onStateChanged(VoiceState.IDLE);
            mListener.onFailed(failure);
        });
    }

    private void failFrom(@NonNull VoiceFailure failure) {
        mRequest = null;
        fail(failure);
    }

    private void setState(@NonNull VoiceState state) {
        mState = state;
        mCallbackExecutor.execute(() -> mListener.onStateChanged(state));
    }

}
