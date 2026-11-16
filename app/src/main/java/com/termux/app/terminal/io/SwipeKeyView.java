package com.termux.app.terminal.io;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

/**
 * MadameLabs fork: one key of the in-app keyboard, drawn and swiped the way that keyboard does
 * it, for the command box popup. A tap runs the centre action; a swipe toward a side or a corner
 * runs the action there, and an action marked to repeat keeps going while the finger stays, as
 * the keyboard's arrows do. Glyphs come from the keyboard's own font, so the popup's arrows,
 * Enter and clipboard look like the keys under it.
 */
public final class SwipeKeyView extends View {

    /** The nine places of a key, in the keyboard's order: centre, then nw, n, ne, w, e, sw, s, se. */
    public static final int C = 0, NW = 1, N = 2, NE = 3, W = 4, E = 5, SW = 6, S = 7, SE = 8;

    private static final long REPEAT_DELAY_MS = 400;
    private static final long REPEAT_INTERVAL_MS = 50;

    private final String[] mGlyphs = new String[9];
    private final Runnable[] mActions = new Runnable[9];
    private final boolean[] mRepeats = new boolean[9];
    private final Paint mKeyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mSubPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final float mDensity;

    private int mInk = Color.WHITE;
    private int mKeyColor = Color.argb(40, 255, 255, 255);
    private int mPressedColor = Color.argb(80, 255, 255, 255);
    private float mDownX, mDownY;
    private boolean mDown;
    private int mActive = -1;

    private final Runnable mRepeat = new Runnable() {
        @Override
        public void run() {
            if (!mDown || mActive < 0 || !mRepeats[mActive]) return;
            fire(mActive);
            mHandler.postDelayed(this, REPEAT_INTERVAL_MS);
        }
    };

    public SwipeKeyView(@NonNull Context context, @Nullable Typeface keyFont) {
        super(context);
        mDensity = context.getResources().getDisplayMetrics().density;
        if (keyFont != null) {
            mLabelPaint.setTypeface(keyFont);
            mSubPaint.setTypeface(keyFont);
        }
        mLabelPaint.setTextAlign(Paint.Align.CENTER);
        mSubPaint.setTextAlign(Paint.Align.CENTER);
        setFocusable(false);
    }

    /** The glyph and action at {@code place}; {@code repeat} keeps it going while held. */
    public SwipeKeyView set(int place, @Nullable String glyph, @Nullable Runnable action,
                            boolean repeat) {
        mGlyphs[place] = glyph;
        mActions[place] = action;
        mRepeats[place] = repeat;
        invalidate();
        return this;
    }

    public void setColors(int ink, int key, int pressed) {
        mInk = ink;
        mKeyColor = key;
        mPressedColor = pressed;
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        float w = getWidth(), h = getHeight();
        mRect.set(0, 0, w, h);
        mKeyPaint.setColor(mDown ? mPressedColor : mKeyColor);
        float radius = 6 * mDensity;
        canvas.drawRoundRect(mRect, radius, radius, mKeyPaint);

        mLabelPaint.setColor(mInk);
        mLabelPaint.setTextSize(Math.min(w, h) * 0.42f);
        mSubPaint.setTextSize(Math.min(w, h) * 0.24f);
        float pad = Math.min(w, h) * 0.2f;
        float[] xs = {w / 2f, pad, w / 2f, w - pad, pad, w - pad, pad, w / 2f, w - pad};
        float[] ys = {h / 2f, pad, pad, pad, h / 2f, h / 2f, h - pad, h - pad, h - pad};
        for (int i = 0; i < 9; i++) {
            String glyph = mGlyphs[i];
            if (glyph == null) continue;
            Paint paint = i == C ? mLabelPaint : mSubPaint;
            int alpha = (mDown && mActive == i) || i == C ? 255 : 190;
            paint.setColor(Color.argb(alpha, Color.red(mInk), Color.green(mInk), Color.blue(mInk)));
            float baseline = ys[i] - (paint.descent() + paint.ascent()) / 2f;
            canvas.drawText(glyph, xs[i], baseline, paint);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDown = true;
                mActive = -1;
                mDownX = event.getX();
                mDownY = event.getY();
                getParent().requestDisallowInterceptTouchEvent(true);
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE: {
                int place = placeFor(event.getX() - mDownX, event.getY() - mDownY,
                    10 * mDensity);
                if (place != C && place != mActive && mActions[place] != null) {
                    mActive = place;
                    mHandler.removeCallbacks(mRepeat);
                    fire(place);
                    if (mRepeats[place]) mHandler.postDelayed(mRepeat, REPEAT_DELAY_MS);
                    invalidate();
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
                if (mActive < 0 && mActions[C] != null) fire(C);
                // fall through
            case MotionEvent.ACTION_CANCEL:
                mDown = false;
                mActive = -1;
                mHandler.removeCallbacks(mRepeat);
                invalidate();
                return true;
            default:
                return false;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mHandler.removeCallbacks(mRepeat);
    }

    private void fire(int place) {
        Runnable action = mActions[place];
        if (action == null) return;
        performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
        action.run();
    }

    /**
     * The place a finger moved {@code dx},{@code dy} from where it went down points at: the
     * centre until it travels {@code threshold}, then one of eight 45° sectors.
     */
    @VisibleForTesting
    static int placeFor(float dx, float dy, float threshold) {
        if (dx * dx + dy * dy < threshold * threshold) return C;
        double angle = Math.toDegrees(Math.atan2(dy, dx)); // 0 = east, 90 = south
        int sector = (int) Math.floor(((angle + 360 + 22.5) % 360) / 45);
        switch (sector) {
            case 0: return E;
            case 1: return SE;
            case 2: return S;
            case 3: return SW;
            case 4: return W;
            case 5: return NW;
            case 6: return N;
            default: return NE;
        }
    }
}
