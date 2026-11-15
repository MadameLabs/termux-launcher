package com.termux.app.orientacao;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalSession;

import java.util.List;

/**
 * Feeds the orientation bar from the focused pane (MadameLabs fork). Polls on a slow tick, as the
 * tmux bar it replaces does, and redraws only when what it would draw changed. Everything it reads
 * from the launcher comes through {@link Source}, so the activity is touched in one place.
 */
public final class OrientationBarController {

    /** What the controller reads from the launcher. */
    public interface Source {
        /** The session behind the focused pane, or null. */
        @Nullable TerminalSession focusedSession();
        /** The foreground argv of the pane whose shell is {@code shellPid}, or null if unknown. */
        @Nullable List<String> foregroundCommand(int shellPid);
        /** Whether a coding agent is recognised in that pane. */
        boolean agentPresent(int shellPid);
        /** Asks the foreground resolver for fresh answers; they arrive on a later tick. */
        void requestForegroundRefresh();
        /** The working directory of the pane's foreground process, or null if unknown. */
        @Nullable String foregroundCwd(int shellPid);
        /** The terminal's typeface, which carries the Nerd Font glyphs the chips draw. */
        @Nullable android.graphics.Typeface terminalTypeface();
    }

    /** How often the bar is refreshed, like tmux's status-interval. */
    static final long TICK_MS = 2000L;

    public static final String VAR_FIVE_HOUR = "ml_5h";
    public static final String VAR_WEEKLY = "ml_semana";

    @NonNull private final OrientationBarView mView;
    @NonNull private final Source mSource;
    @NonNull private final Handler mHandler = new Handler(Looper.getMainLooper());
    @Nullable private String mLastDrawn;
    private boolean mRunning;

    private final Runnable mTick = new Runnable() {
        @Override public void run() {
            if (!mRunning) return;
            mSource.requestForegroundRefresh();
            onRefresh();
            mHandler.postDelayed(this, TICK_MS);
        }
    };

    public OrientationBarController(@NonNull OrientationBarView view, @NonNull Source source) {
        mView = view;
        mSource = source;
    }

    public void start() {
        if (mRunning) return;
        mRunning = true;
        mHandler.post(mTick);
    }

    public void stop() {
        mRunning = false;
        mHandler.removeCallbacks(mTick);
    }

    /** Redraws now, for a focus change or a resolved foreground. */
    public void onRefresh() {
        mView.setChipTypeface(mSource.terminalTypeface());
        // The chips wrap their text, so they are fitted to the run the slot can give them (just
        // over half of it, as the clock gets), not to their own last width.
        android.view.ViewParent parent = mView.getParent();
        int room = parent instanceof android.view.View
            ? Math.round(((android.view.View) parent).getWidth() * .55f) : mView.getWidth();
        OrientationLine line = OrientationModel.fit(OrientationModel.resolve(read()),
            mView.maxChars(room));
        String key = room + "\u0000" + line.machine + '\u0000' + line.path + '\u0000' + line.chat + '\u0000'
            + line.fiveHour + '\u0000' + line.weekly;
        if (key.equals(mLastDrawn)) return;
        mLastDrawn = key;
        mView.render(line);
    }

    @NonNull
    private OrientationInput read() {
        TerminalSession session = mSource.focusedSession();
        TerminalEmulator emulator = session == null ? null : session.getEmulator();
        if (session == null || emulator == null) {
            return new OrientationInput(null, null, null, null, null, false, null, null);
        }
        int pid = session.getPid();
        // The foreground process's own folder first: a `cd` inside a subshell or an agent never
        // reaches the shell's prompt, so the shell's last OSC 7 would be stale.
        String cwd = mSource.foregroundCwd(pid);
        return new OrientationInput(emulator.getRemoteUserHost(), emulator.getRemotePath(),
            cwd != null ? cwd : emulator.getReportedWorkingDirectory(), mSource.foregroundCommand(pid),
            session.getTitle(),
            mSource.agentPresent(pid), emulator.getUserVar(VAR_FIVE_HOUR),
            emulator.getUserVar(VAR_WEEKLY));
    }
}
