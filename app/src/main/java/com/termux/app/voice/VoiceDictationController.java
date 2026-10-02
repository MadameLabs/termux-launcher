package com.termux.app.voice;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.app.ActivityCompat;

import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.R;
import com.termux.app.notice.AppNotice;
import com.termux.terminal.TerminalSession;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The activity-side half of voice dictation: the panel, the permission, and the wiring that turns
 * the keyboard gesture into a {@link VoiceOrchestrator} run.
 *
 * <p>The keyboard module never sees any of this. It emits its two existing voice events and this
 * class decides what they mean, which is what keeps HTTP, credentials and product rules out of a
 * component whose job is drawing keys.
 */
public final class VoiceDictationController implements VoiceOrchestrator.Listener {

    /** Supplies the terminal session that is live right now. */
    public interface SessionSource {
        @Nullable TerminalSession currentSession();

        void write(@NonNull TerminalSession session, @NonNull String text);
    }

    /** Record/Stop sits in the middle slot, Stop & send on the right. */
    private static final int PRIMARY_BUTTON = android.R.id.button2;
    private static final int SUBMIT_BUTTON = android.R.id.button1;

    @NonNull private final Activity mActivity;
    @NonNull private final SessionSource mSessionSource;
    private final int mPermissionRequestCode;

    @NonNull private final VoicePostProcessingPreferences mPreferences;
    @NonNull private final GroqSecretStore mSecretStore;
    @NonNull private final PrivateAudioCapture mCapture;
    @NonNull private final ExecutorService mBackgroundExecutor;
    @NonNull private final VoiceOrchestrator mOrchestrator;

    /** Sticky between recordings, except terminal mode, which the orchestrator clears on success. */
    @NonNull private ModeSelection mSelection = ModeSelection.RAW;

    @Nullable private Dialog mDialog;
    @Nullable private TextView mStateView;
    private boolean mAwaitingPermission;

    public VoiceDictationController(@NonNull Activity activity,
                                    @NonNull SessionSource sessionSource,
                                    int permissionRequestCode) {
        mActivity = activity;
        mSessionSource = sessionSource;
        mPermissionRequestCode = permissionRequestCode;
        mPreferences = new VoicePostProcessingPreferences(activity);
        mSecretStore = new GroqSecretStore(activity);
        mCapture = new PrivateAudioCapture(activity);
        mBackgroundExecutor = Executors.newSingleThreadExecutor();
        mOrchestrator = new VoiceOrchestrator(mCapture, mCapture, new GroqSpeechClient(),
            new GroqPostProcessingClient(), mSecretStore::getKey, mBackgroundExecutor,
            mActivity::runOnUiThread, this);
    }

    /**
     * Whether this controller, rather than the platform speech recogniser, should answer the
     * gesture. Without post-processing enabled and a key stored, the launcher keeps its original
     * behaviour instead of failing at a feature that was never configured.
     */
    public boolean isConfigured() {
        return mPreferences.load().postProcessingEnabled() && mSecretStore.hasKey();
    }

    /**
     * Answers the keyboard gesture. Both the swipe and the long press open the panel already
     * recording; only a missing microphone permission holds the recording back until it is granted.
     */
    public void onVoiceGesture(boolean showModePanel) {
        if (mOrchestrator.isBusy()) return;
        if (mSessionSource.currentSession() == null) return;

        if (!mCapture.hasMicrophonePermission()) {
            showPanel(true);
            return;
        }
        showPanel(false);
        beginRecording();
    }

    /** Called by the activity when the microphone permission dialog is answered. */
    public void onMicrophonePermissionResult(boolean granted) {
        if (!mAwaitingPermission) return;
        mAwaitingPermission = false;
        if (granted) {
            beginRecording();
        } else {
            notice(R.string.voice_error_microphone);
            dismissPanel();
        }
    }

    public void onActivityStopped() {
        if (mOrchestrator.state() == VoiceState.RECORDING) mOrchestrator.cancel();
        dismissPanel();
    }

    public void shutdown() {
        onActivityStopped();
        mBackgroundExecutor.shutdownNow();
    }

    private void showPanel(boolean requestPermissionFirst) {
        VoiceSettings settings = mPreferences.load();
        View content = LayoutInflater.from(mActivity)
            .inflate(R.layout.dialog_voice_dictation, null, false);
        mStateView = content.findViewById(R.id.voice_state);

        bindChip(content, R.id.voice_mode_correction, VoiceMode.CORRECTION, true);
        bindChip(content, R.id.voice_mode_shorten, VoiceMode.SHORTEN, true);
        bindChip(content, R.id.voice_mode_terminal, VoiceMode.TERMINAL, settings.showTerminalMode());
        Chip emoji = content.findViewById(R.id.voice_mode_emoji);
        emoji.setChecked(mSelection.emojiEnabled());
        emoji.setOnClickListener(view -> {
            mSelection = mSelection.withEmoji(emoji.isChecked());
            syncChips(content);
        });

        Dialog dialog = new MaterialAlertDialogBuilder(mActivity)
            .setTitle(R.string.voice_dictation_title)
            .setView(content)
            // The bar lays out neutral, negative, positive from left to right, so the roles follow
            // the order the person reads: Cancel, Record/Stop, Stop & send.
            .setNeutralButton(R.string.voice_action_cancel, (d, which) -> mOrchestrator.cancel())
            .setNegativeButton(R.string.voice_action_record, null)
            .setPositiveButton(R.string.voice_action_stop_and_send, null)
            .setOnDismissListener(d -> {
                mDialog = null;
                mStateView = null;
                if (mOrchestrator.state() == VoiceState.RECORDING) mOrchestrator.cancel();
            })
            .create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
        positionPanelAtBottom(dialog);
        mDialog = dialog;

        // Set after show() so the buttons can act without dismissing the panel mid-dictation.
        View primary = dialog.findViewById(PRIMARY_BUTTON);
        if (primary != null) primary.setOnClickListener(view -> onPrimaryAction());
        View submit = dialog.findViewById(SUBMIT_BUTTON);
        if (submit != null) submit.setOnClickListener(view -> onSubmitAction());
        onStateChanged(mOrchestrator.state());

        if (requestPermissionFirst) requestMicrophonePermission();
    }

    static void positionPanelAtBottom(@NonNull Dialog dialog) {
        Window window = dialog.getWindow();
        if (window == null) return;
        window.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
    }

    private void bindChip(@NonNull View content, int viewId, @NonNull VoiceMode mode, boolean visible) {
        Chip chip = content.findViewById(viewId);
        chip.setVisibility(visible ? View.VISIBLE : View.GONE);
        chip.setChecked(mSelection.primary() == mode);
        chip.setOnClickListener(view -> {
            mSelection = mSelection.withPrimary(mode);
            syncChips(content);
        });
    }

    /** Redraws every chip from the selection, so the exclusivity rules are what the person sees. */
    private void syncChips(@NonNull View content) {
        ((Chip) content.findViewById(R.id.voice_mode_correction))
            .setChecked(mSelection.primary() == VoiceMode.CORRECTION);
        ((Chip) content.findViewById(R.id.voice_mode_shorten))
            .setChecked(mSelection.primary() == VoiceMode.SHORTEN);
        ((Chip) content.findViewById(R.id.voice_mode_terminal))
            .setChecked(mSelection.primary() == VoiceMode.TERMINAL);
        Chip emoji = content.findViewById(R.id.voice_mode_emoji);
        emoji.setEnabled(mSelection.primary() != VoiceMode.TERMINAL);
        emoji.setChecked(mSelection.emojiEnabled());
    }

    private void onPrimaryAction() {
        if (mOrchestrator.state() == VoiceState.RECORDING) {
            mOrchestrator.finish();
        } else if (!mOrchestrator.isBusy()) {
            if (mCapture.hasMicrophonePermission()) {
                beginRecording();
            } else {
                requestMicrophonePermission();
            }
        }
    }

    /** Stop and send: the orchestrator decides again whether Enter is allowed for this recording. */
    private void onSubmitAction() {
        if (mOrchestrator.canSubmit()) mOrchestrator.finish(true);
    }

    private void requestMicrophonePermission() {
        mAwaitingPermission = true;
        ActivityCompat.requestPermissions(mActivity,
            new String[] {Manifest.permission.RECORD_AUDIO}, mPermissionRequestCode);
    }

    private void beginRecording() {
        TerminalSession session = mSessionSource.currentSession();
        if (session == null) {
            notice(R.string.voice_error_session_gone);
            dismissPanel();
            return;
        }
        mOrchestrator.start(new SessionHandle(session), mSelection, mPreferences.load(),
            mPreferences.loadVocabulary(), System.currentTimeMillis());
    }

    @Override
    public void onStateChanged(@NonNull VoiceState state) {
        if (mStateView != null) mStateView.setText(stateLabel(state));
        Dialog dialog = mDialog;
        if (dialog == null) return;
        View submit = dialog.findViewById(SUBMIT_BUTTON);
        if (submit != null) {
            // Only a recording can be stopped; a terminal mode command is offered but never sent.
            submit.setVisibility(state == VoiceState.RECORDING ? View.VISIBLE : View.GONE);
            submit.setEnabled(mOrchestrator.canSubmit());
        }
        View primary = dialog.findViewById(PRIMARY_BUTTON);
        if (!(primary instanceof TextView)) return;
        ((TextView) primary).setText(state == VoiceState.RECORDING
            ? R.string.voice_action_stop : R.string.voice_action_record);
        primary.setEnabled(state == VoiceState.IDLE || state == VoiceState.RECORDING);
    }

    @Override
    public void onDelivered(@NonNull VoiceResult result, @NonNull ModeSelection nextSelection) {
        mSelection = nextSelection;
        if (result.warning() != null) notice(R.string.voice_warning_post_processing);
        dismissPanel();
    }

    @Override
    public void onCancelled() {
        dismissPanel();
    }

    @Override
    public void onFailed(@NonNull VoiceFailure failure) {
        notice(errorLabel(failure));
        dismissPanel();
    }

    private void dismissPanel() {
        Dialog dialog = mDialog;
        mDialog = null;
        mStateView = null;
        if (dialog != null && dialog.isShowing()) {
            dialog.setOnDismissListener((DialogInterface.OnDismissListener) null);
            dialog.dismiss();
        }
    }

    private void notice(@StringRes int message) {
        AppNotice.show(mActivity, message, false);
    }

    @StringRes
    private static int stateLabel(@NonNull VoiceState state) {
        switch (state) {
            case RECORDING: return R.string.voice_state_recording;
            case TRANSCRIBING: return R.string.voice_state_transcribing;
            case POST_PROCESSING: return R.string.voice_state_post_processing;
            case DELIVERING: return R.string.voice_state_delivering;
            default: return R.string.voice_state_ready;
        }
    }

    @StringRes
    private static int errorLabel(@NonNull VoiceFailure failure) {
        switch (failure) {
            case MICROPHONE: return R.string.voice_error_microphone;
            case NO_AUDIO: return R.string.voice_error_no_audio;
            case MISSING_KEY: return R.string.voice_error_missing_key;
            case CREDENTIAL: return R.string.voice_error_credential;
            case RATE_LIMIT: return R.string.voice_error_rate_limit;
            case NETWORK: return R.string.voice_error_network;
            case EMPTY_TRANSCRIPT: return R.string.voice_error_empty_transcript;
            case SESSION_GONE: return R.string.voice_error_session_gone;
            default: return R.string.voice_error_service;
        }
    }

    /**
     * Holds the session captured at the start of the recording and refuses to write anywhere else.
     * Identity, not index: a panel swap must not make a later session look like the original.
     */
    private final class SessionHandle implements VoiceOrchestrator.SessionTarget {
        @NonNull private final TerminalSession mSession;

        SessionHandle(@NonNull TerminalSession session) { mSession = session; }

        @Override public boolean isValid() { return mSessionSource.currentSession() == mSession; }

        @Override public void write(@NonNull String text) { mSessionSource.write(mSession, text); }
    }
}
