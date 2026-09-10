package com.termux.app.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * The orchestrator is exercised with fakes for audio, network and terminal, so every rule that the
 * feature contract calls non-negotiable is checked without a device or a key.
 */
public class VoiceOrchestratorTest {

    @Rule public TemporaryFolder mFolder = new TemporaryFolder();

    /** Runs work inline: the test sees a finished flow when the call returns. */
    private static final Executor DIRECT = Runnable::run;

    private FakeRecorder mRecorder;
    private FakeSpeechClient mSpeechClient;
    private FakePostProcessingClient mPostProcessingClient;
    private FakeTarget mTarget;
    private RecordingListener mListener;
    private File mAudioFile;
    private String mApiKey;

    @Before
    public void setUp() throws IOException {
        mRecorder = new FakeRecorder();
        mSpeechClient = new FakeSpeechClient();
        mPostProcessingClient = new FakePostProcessingClient();
        mTarget = new FakeTarget();
        mListener = new RecordingListener();
        mAudioFile = mFolder.newFile("dictation.m4a");
        writeSomeAudio();
        mApiKey = "gsk_fake";
    }

    private void writeSomeAudio() throws IOException {
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(mAudioFile)) {
            out.write(new byte[] {1, 2, 3, 4});
        }
    }

    private VoiceOrchestrator orchestrator() {
        return new VoiceOrchestrator(mRecorder, () -> mAudioFile, mSpeechClient,
            mPostProcessingClient, () -> mApiKey, DIRECT, DIRECT, mListener);
    }

    private VoiceSettings settings(boolean postProcessingEnabled) {
        return VoiceSettings.builder().postProcessingEnabled(postProcessingEnabled).build();
    }

    private VoiceOrchestrator startedWith(ModeSelection selection, boolean postProcessingEnabled) {
        VoiceOrchestrator orchestrator = orchestrator();
        orchestrator.start(mTarget, selection, settings(postProcessingEnabled), Vocabulary.empty(), 0L);
        return orchestrator;
    }

    @Test
    public void rawDictationReachesTheSessionThatStartedIt() {
        mSpeechClient.transcript = "ola mundo";
        VoiceOrchestrator orchestrator = startedWith(ModeSelection.RAW, false);
        assertEquals(VoiceState.RECORDING, orchestrator.state());

        orchestrator.finish();

        assertEquals("ola mundo", mTarget.written);
        assertEquals(VoiceState.IDLE, orchestrator.state());
        assertNull(mListener.failure);
        assertFalse(mPostProcessingClient.called);
    }

    @Test
    public void aRawDictationNeverCallsTheTransformationEndpoint() {
        mSpeechClient.transcript = "texto";
        startedWith(ModeSelection.RAW, true).finish();
        assertFalse(mPostProcessingClient.called);
    }

    @Test
    public void transformedDictationDeliversTheProcessedText() {
        mSpeechClient.transcript = "eh eh bom dia";
        mPostProcessingClient.answer = "Bom dia.";

        startedWith(ModeSelection.of(VoiceMode.CORRECTION, false), true).finish();

        assertEquals("Bom dia.", mTarget.written);
        assertEquals("Bom dia.", mListener.result.processedText());
        assertEquals("eh eh bom dia", mListener.result.rawTranscript());
        assertNull(mListener.result.warning());
    }

    @Test
    public void postProcessingIsSkippedWhenTheFeatureIsDisabledInSettings() {
        mSpeechClient.transcript = "texto cru";
        startedWith(ModeSelection.of(VoiceMode.CORRECTION, false), false).finish();

        assertFalse(mPostProcessingClient.called);
        assertEquals("texto cru", mTarget.written);
    }

    @Test
    public void aFailedTransformationStillDeliversTheRawTranscript() {
        mSpeechClient.transcript = "bom dia";
        mPostProcessingClient.failure = VoiceFailure.NETWORK;

        startedWith(ModeSelection.of(VoiceMode.CORRECTION, false), true).finish();

        assertEquals("bom dia", mTarget.written);
        assertEquals(VoiceResult.Warning.POST_PROCESSING_FAILED, mListener.result.warning());
        assertNull(mListener.failure);
    }

    @Test
    public void anEmptyTransformationStillDeliversTheRawTranscript() {
        mSpeechClient.transcript = "bom dia";
        mPostProcessingClient.answer = null;

        startedWith(ModeSelection.of(VoiceMode.CORRECTION, false), true).finish();

        assertEquals("bom dia", mTarget.written);
        assertEquals(VoiceResult.Warning.POST_PROCESSING_EMPTY, mListener.result.warning());
    }

    @Test
    public void aFailedTranscriptionWritesNothingAtAll() {
        mSpeechClient.failure = VoiceFailure.CREDENTIAL;

        startedWith(ModeSelection.RAW, false).finish();

        assertNull(mTarget.written);
        assertEquals(VoiceFailure.CREDENTIAL, mListener.failure);
    }

    @Test
    public void cancellingWritesNothingAndReportsCancellation() {
        VoiceOrchestrator orchestrator = startedWith(ModeSelection.RAW, false);
        orchestrator.cancel();

        assertNull(mTarget.written);
        assertTrue(mRecorder.cancelled);
        assertTrue(mListener.cancelled);
        assertEquals(VoiceState.IDLE, orchestrator.state());
    }

    @Test
    public void theAudioFileIsDeletedOnEveryTerminalPath() {
        mSpeechClient.transcript = "ok";
        startedWith(ModeSelection.RAW, false).finish();
        assertFalse(mAudioFile.exists());
    }

    @Test
    public void theAudioFileIsDeletedWhenTheDictationIsCancelled() {
        startedWith(ModeSelection.RAW, false).cancel();
        assertFalse(mAudioFile.exists());
    }

    @Test
    public void theAudioFileIsDeletedWhenTranscriptionFails() {
        mSpeechClient.failure = VoiceFailure.NETWORK;
        startedWith(ModeSelection.RAW, false).finish();
        assertFalse(mAudioFile.exists());
    }

    @Test
    public void textIsDiscardedRatherThanWrittenToWhicheverPanelIsNowInFront() {
        mSpeechClient.transcript = "nao escreva isso";
        VoiceOrchestrator orchestrator = startedWith(ModeSelection.RAW, false);
        mTarget.valid = false;

        orchestrator.finish();

        assertNull(mTarget.written);
        assertEquals(VoiceFailure.SESSION_GONE, mListener.failure);
    }

    @Test
    public void terminalModeDeliversTextAndTurnsItselfOffForTheNextRecording() {
        mSpeechClient.transcript = "listar arquivos ocultos";
        mPostProcessingClient.answer = "ls -la";

        startedWith(ModeSelection.of(VoiceMode.TERMINAL, false), true).finish();

        assertEquals("ls -la", mTarget.written);
        assertEquals(VoiceMode.RAW, mListener.nextSelection.primary());
        // Delivery is a single write: nothing appends a newline or runs the command.
        assertEquals(1, mTarget.writes);
        assertFalse(mTarget.written.endsWith("\n"));
    }

    @Test
    public void terminalModeSurvivesWhenTheTransformationFellBackToRawText() {
        mSpeechClient.transcript = "listar arquivos";
        mPostProcessingClient.failure = VoiceFailure.SERVICE;

        startedWith(ModeSelection.of(VoiceMode.TERMINAL, false), true).finish();

        assertEquals(VoiceMode.TERMINAL, mListener.nextSelection.primary());
    }

    @Test
    public void withoutAKeyNothingIsRecordedInTheFirstPlace() {
        mApiKey = null;
        VoiceOrchestrator orchestrator = startedWith(ModeSelection.RAW, false);

        assertFalse(mRecorder.started);
        assertEquals(VoiceFailure.MISSING_KEY, mListener.failure);
        assertEquals(VoiceState.IDLE, orchestrator.state());
    }

    @Test
    public void aDeniedMicrophoneFailsBeforeAnythingIsSpoken() {
        mRecorder.startFailure = VoiceFailure.MICROPHONE;
        VoiceOrchestrator orchestrator = startedWith(ModeSelection.RAW, false);

        assertEquals(VoiceFailure.MICROPHONE, mListener.failure);
        assertEquals(VoiceState.IDLE, orchestrator.state());
        assertFalse(mAudioFile.exists());
    }

    @Test
    public void aSecondGestureDuringAnActiveDictationIsIgnored() {
        VoiceOrchestrator orchestrator = startedWith(ModeSelection.RAW, false);
        mRecorder.started = false;

        orchestrator.start(mTarget, ModeSelection.RAW, settings(false), Vocabulary.empty(), 0L);

        assertFalse(mRecorder.started);
    }

    @Test
    public void theSnapshotIgnoresSettingsChangedAfterTheGesture() {
        mSpeechClient.transcript = "bom dia";
        mPostProcessingClient.answer = "Bom dia!";
        VoiceOrchestrator orchestrator = orchestrator();
        VoiceSettings snapshot = settings(true);

        orchestrator.start(mTarget, ModeSelection.of(VoiceMode.CORRECTION, false), snapshot,
            Vocabulary.empty(), 0L);
        // Whatever the settings screen does now, this dictation keeps the prompts it started with.
        orchestrator.finish();

        assertTrue(mPostProcessingClient.systemMessage.contains(
            VoiceSettings.DEFAULT_CORRECTION_PROMPT));
    }

    @Test
    public void statesFollowTheContractOrder() {
        mSpeechClient.transcript = "bom dia";
        mPostProcessingClient.answer = "Bom dia.";

        startedWith(ModeSelection.of(VoiceMode.CORRECTION, false), true).finish();

        assertEquals(List.of(VoiceState.RECORDING, VoiceState.TRANSCRIBING,
            VoiceState.POST_PROCESSING, VoiceState.DELIVERING, VoiceState.IDLE), mListener.states);
    }

    private static final class FakeRecorder implements VoiceOrchestrator.Recorder {
        boolean started;
        boolean cancelled;
        @Nullable VoiceFailure startFailure;
        @Nullable VoiceFailure stopFailure;

        @Override public void start(@NonNull File output) throws VoiceException {
            if (startFailure != null) throw new VoiceException(startFailure);
            started = true;
        }

        @Override public void stop() throws VoiceException {
            if (stopFailure != null) throw new VoiceException(stopFailure);
        }

        @Override public void cancel() { cancelled = true; }
    }

    private static final class FakeSpeechClient extends GroqSpeechClient {
        @Nullable String transcript;
        @Nullable VoiceFailure failure;

        @NonNull
        @Override
        public String transcribe(@NonNull File audio, @NonNull String apiKey, @NonNull String model,
                                 @NonNull String language, @NonNull String spellingHint)
                throws VoiceException {
            if (failure != null) throw new VoiceException(failure);
            return transcript == null ? "" : transcript;
        }
    }

    private static final class FakePostProcessingClient extends GroqPostProcessingClient {
        boolean called;
        @Nullable String answer;
        @Nullable VoiceFailure failure;
        String systemMessage = "";

        @Nullable
        @Override
        public String transform(@NonNull String systemMessage, @NonNull String userMessage,
                                @NonNull String apiKey, @NonNull String model, float temperature)
                throws VoiceException {
            called = true;
            this.systemMessage = systemMessage;
            if (failure != null) throw new VoiceException(failure);
            return answer;
        }
    }

    private static final class FakeTarget implements VoiceOrchestrator.SessionTarget {
        boolean valid = true;
        @Nullable String written;
        int writes;

        @Override public boolean isValid() { return valid; }

        @Override public void write(@NonNull String text) {
            written = text;
            writes++;
        }
    }

    private static final class RecordingListener implements VoiceOrchestrator.Listener {
        final List<VoiceState> states = new ArrayList<>();
        @Nullable VoiceResult result;
        @Nullable ModeSelection nextSelection;
        @Nullable VoiceFailure failure;
        boolean cancelled;

        @Override public void onStateChanged(@NonNull VoiceState state) { states.add(state); }

        @Override public void onDelivered(@NonNull VoiceResult result,
                                          @NonNull ModeSelection nextSelection) {
            this.result = result;
            this.nextSelection = nextSelection;
        }

        @Override public void onCancelled() { cancelled = true; }

        @Override public void onFailed(@NonNull VoiceFailure failure) { this.failure = failure; }
    }
}
