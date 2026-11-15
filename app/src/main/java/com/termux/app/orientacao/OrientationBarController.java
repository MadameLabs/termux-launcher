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
        /** Whether the chips stand in the status row (on) or in the clock's cell (off). */
        boolean chipsInRow();
        /** Lets the clock's cell hold the chips, or not while they stand in the row. */
        void allowChipsInSlot(boolean allowed);
    }

    /** How often the bar is refreshed, like tmux's status-interval. */
    static final long TICK_MS = 2000L;

    public static final String VAR_FIVE_HOUR = "ml_5h";
    public static final String VAR_WEEKLY = "ml_semana";
    /** The conversation's name, from the statusline script; the terminal title is the fallback. */
    public static final String VAR_CHAT = "ml_chat";

    /** The chips in the clock's cell of the widget slot. */
    @NonNull private final OrientationBarView mView;
    /** The chips in the status row, beside the window pills; null without that row. */
    @Nullable private final OrientationBarView mRowView;
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

    public OrientationBarController(@NonNull OrientationBarView view,
                                    @Nullable OrientationBarView rowView, @NonNull Source source) {
        mView = view;
        mRowView = rowView;
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
        boolean inRow = mRowView != null && mSource.chipsInRow();
        mSource.allowChipsInSlot(!inRow);
        if (mRowView != null) mRowView.setVisibility(inRow ? android.view.View.VISIBLE : android.view.View.GONE);
        OrientationBarView view = inRow ? mRowView : mView;
        view.setChipTypeface(mSource.terminalTypeface());
        // The chips wrap their text, so they are fitted to the run their place can give them,
        // not to their own last width: just over half of the slot (as the clock gets), or of the
        // status row, whose window pills keep the rest.
        android.view.ViewParent parent = view.getParent();
        // In the keys panel the row scrolls sideways, so nothing is cut to fit it.
        int room = inRow ? Integer.MAX_VALUE / 4
            : parent instanceof android.view.View
            ? Math.round(((android.view.View) parent).getWidth() * .55f) : view.getWidth();
        // In the clock's cell the slot says what the chips really get: the whole row when nothing
        // stands beside them, which the 55% left mostly empty while the chat was cut.
        if (!inRow && parent instanceof com.termux.app.statusbar.TopPaneWidgetSlot) {
            int slotRoom = ((com.termux.app.statusbar.TopPaneWidgetSlot) parent).orientationRoomPx();
            if (slotRoom > 0) room = slotRoom;
        }
        OrientationLine line = OrientationModel.fit(OrientationModel.resolve(read(),
                inRow ? Integer.MAX_VALUE : OrientationModel.CHAT_MAX),
            view.maxChars(room));
        String key = inRow + "\u0000" + room + "\u0000" + line.machine + '\u0000' + line.path + '\u0000' + line.chat + '\u0000'
            + line.fiveHour + '\u0000' + line.weekly;
        if (key.equals(mLastDrawn)) return;
        mLastDrawn = key;
        view.render(line);
    }

    @Nullable
    static String chatName(@Nullable String var, @Nullable String title) {
        return var != null && !var.trim().isEmpty() ? var : title;
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
            chatName(emulator.getUserVar(VAR_CHAT), session.getTitle()),
            mSource.agentPresent(pid), emulator.getUserVar(VAR_FIVE_HOUR),
            emulator.getUserVar(VAR_WEEKLY));
    }
}
