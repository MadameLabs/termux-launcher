package com.termux.app.terminal.io;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

/**
 * MadameLabs fork: the command box popup's space bar, clean: a key with one short bar on it. A
 * tap types a space; a finger slid along it moves the cursor a character per step, as the in-app
 * keyboard's space bar does, which is the easy way to put the cursor where it is wanted.
 */
public final class SpaceSliderView extends View {

    /** Moves the cursor by {@code steps} characters, negative to the left. */
    public interface Slide {
        void by(int steps);
    }

    /** How far the finger goes for the cursor to move one character, in dp. */
    @VisibleForTesting static final float STEP_DP = 8f;

    private final Runnable mTap;
    private final Slide mSlide;
    private final float mDensity;
    private final Paint mKeyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();

    private int mInk = Color.WHITE;
    private int mKeyColor = Color.argb(40, 255, 255, 255);
    private int mPressedColor = Color.argb(80, 255, 255, 255);
    private boolean mDown, mSliding;
    private float mDownX, mLastX;

    public SpaceSliderView(@NonNull Context context, @NonNull Runnable tap, @NonNull Slide slide) {
        super(context);
        mTap = tap;
        mSlide = slide;
        mDensity = context.getResources().getDisplayMetrics().density;
        mBarPaint.setStrokeCap(Paint.Cap.ROUND);
        setFocusable(false);
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
        mBarPaint.setColor(Color.argb(150, Color.red(mInk), Color.green(mInk), Color.blue(mInk)));
        mBarPaint.setStrokeWidth(2 * mDensity);
        float half = Math.min(w * 0.3f, 28 * mDensity);
        canvas.drawLine(w / 2f - half, h / 2f, w / 2f + half, h / 2f, mBarPaint);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        float step = STEP_DP * mDensity;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDown = true;
                mSliding = false;
                mDownX = mLastX = event.getX();
                getParent().requestDisallowInterceptTouchEvent(true);
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE: {
                float x = event.getX();
                if (!mSliding && Math.abs(x - mDownX) >= step) mSliding = true;
                if (!mSliding) return true;
                int steps = steps(x - mLastX, step);
                if (steps != 0) {
                    mLastX += steps * step;
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    mSlide.by(steps);
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
                if (!mSliding) {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    mTap.run();
                }
                // fall through
            case MotionEvent.ACTION_CANCEL:
                mDown = false;
                mSliding = false;
                invalidate();
                return true;
            default:
                return false;
        }
    }

    /** Whole steps of {@code step} in {@code dx}, toward zero. */
    @VisibleForTesting
    static int steps(float dx, float step) {
        return step <= 0 ? 0 : (int) (dx / step);
    }
}
