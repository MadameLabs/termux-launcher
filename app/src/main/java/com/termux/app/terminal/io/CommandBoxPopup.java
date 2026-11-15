package com.termux.app.terminal.io;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.ContextCompat;

import com.google.android.material.color.MaterialColors;
import com.termux.R;

/**
 * MadameLabs fork: the command box as a floating popup, the way the scratchpad floats a terminal.
 * A multi-line field whose text wraps, so a message longer than a line can be read and revised
 * before it goes. The keyboard's Enter still sends it to the focused session, as the command row
 * does, and the popup closes; closing it without sending keeps the draft. It is dragged by its
 * header and resized from its corner, and where it stood is kept as a fraction of its parent, so
 * it comes back there and always fits above the keyboard the window has been resized for.
 */
public final class CommandBoxPopup {

    /** What the popup asks of the activity. */
    public interface Host {
        /** Sends {@code text} to the focused session ("\r" for an empty box); false without one. */
        boolean send(@NonNull String text);
        /** Gives the system keyboard to {@code field}, as the command row does. */
        void beginInput(@NonNull EditText field);
        /** Hands typing back to the terminal. */
        void endInput();
        /** The terminal's text size, in px, so the popup reads at the terminal's size. */
        float terminalTextSizePx();
        @Nullable Typeface terminalTypeface();
        /** Lends the terminal's focus while the field holds it (see TerminalView#setFocusLent). */
        void lendTerminalFocus(boolean lent);
    }

    private static final String PREFS = "command_box_popup";
    private static final String KEY_LEFT = "left";
    private static final String KEY_TOP = "top";
    private static final String KEY_RIGHT = "right";
    private static final String KEY_BOTTOM = "bottom";

    /** The first place it opens at: across most of the width, high in what is left. */
    @VisibleForTesting static final RectF DEFAULT_FRAC = new RectF(.04f, .08f, .96f, .48f);
    /** The smallest it can be resized to, in dp. */
    private static final int MIN_WIDTH_DP = 160;
    private static final int MIN_HEIGHT_DP = 96;
    private static final int HEADER_DP = 28;
    private static final int CORNER_DP = 28;

    private final Context mContext;
    private final Host mHost;
    private final SharedPreferences mPrefs;
    private final float mDensity;
    @NonNull private final RectF mFrac;

    @Nullable private FrameLayout mParent;
    @Nullable private LinearLayout mRoot;
    @Nullable private EditText mField;
    private final View.OnLayoutChangeListener mParentLayout =
        (v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol || b - t != ob - ot) place();
        };

    public CommandBoxPopup(@NonNull Context context, @NonNull Host host) {
        mContext = context;
        mHost = host;
        mPrefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        mDensity = context.getResources().getDisplayMetrics().density;
        mFrac = new RectF(
            mPrefs.getFloat(KEY_LEFT, DEFAULT_FRAC.left), mPrefs.getFloat(KEY_TOP, DEFAULT_FRAC.top),
            mPrefs.getFloat(KEY_RIGHT, DEFAULT_FRAC.right),
            mPrefs.getFloat(KEY_BOTTOM, DEFAULT_FRAC.bottom));
        if (!sane(mFrac)) mFrac.set(DEFAULT_FRAC);
    }

    public boolean isShown() {
        return mRoot != null && mRoot.getVisibility() == View.VISIBLE;
    }

    /** Shows the popup over {@code parent}, or hides it; answers whether it shows now. */
    public boolean toggle(@NonNull FrameLayout parent) {
        if (isShown()) {
            hide();
            return false;
        }
        show(parent);
        return true;
    }

    public void show(@NonNull FrameLayout parent) {
        ensureViews(parent);
        LinearLayout root = mRoot;
        EditText field = mField;
        if (root == null || field == null) return;
        field.setTextSize(TypedValue.COMPLEX_UNIT_PX, mHost.terminalTextSizePx());
        Typeface typeface = mHost.terminalTypeface();
        if (typeface != null) field.setTypeface(typeface);
        root.setVisibility(View.VISIBLE);
        root.bringToFront();
        place();
        field.requestFocus();
        field.setSelection(field.length());
        mHost.beginInput(field);
    }

    /** Hides the popup; the draft stays in the field for the next time. */
    public void hide() {
        LinearLayout root = mRoot;
        if (root == null || root.getVisibility() != View.VISIBLE) return;
        root.setVisibility(View.GONE);
        if (mField != null) mField.clearFocus();
        mHost.endInput();
    }

    // ------------------------------------------------------------------------------- views

    private void ensureViews(@NonNull FrameLayout parent) {
        if (mRoot != null && mParent == parent) return;
        if (mRoot != null && mParent != null) {
            mParent.removeView(mRoot);
            mParent.removeOnLayoutChangeListener(mParentLayout);
        }
        mParent = parent;
        LinearLayout root = new LinearLayout(mContext);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setClickable(true);
        int panel = MaterialColors.getColor(mContext,
            com.termux.shared.R.attr.termuxColorSurfacePanelHigh,
            ContextCompat.getColor(mContext, R.color.termux_surface_panel_high));
        int outline = MaterialColors.getColor(mContext,
            com.termux.shared.R.attr.termuxColorOutlineVariant, Color.GRAY);
        int onSurface = MaterialColors.getColor(mContext,
            com.termux.shared.R.attr.termuxColorOnSurface,
            ContextCompat.getColor(mContext, R.color.termux_on_surface));
        GradientDrawable card = new GradientDrawable();
        card.setColor(panel);
        card.setCornerRadius(dp(14));
        card.setStroke(Math.max(1, dp(1)), outline);
        root.setBackground(card);
        root.setElevation(dp(8));

        root.addView(header(onSurface), new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(HEADER_DP)));

        FrameLayout body = new FrameLayout(mContext);
        EditText field = new EditText(mContext);
        // Wraps instead of scrolling sideways, and the keyboard's Enter still says Send.
        field.setRawInputType(InputType.TYPE_CLASS_TEXT);
        field.setImeOptions(EditorInfo.IME_ACTION_SEND | EditorInfo.IME_FLAG_NO_FULLSCREEN);
        field.setHorizontallyScrolling(false);
        field.setMaxLines(Integer.MAX_VALUE);
        field.setGravity(Gravity.TOP | Gravity.START);
        field.setBackground(null);
        field.setTextColor(onSurface);
        field.setHint(R.string.extra_keys_command_hint);
        field.setHintTextColor(MaterialColors.getColor(mContext,
            com.termux.shared.R.attr.termuxColorOnSurfaceVariant, outline));
        field.setPadding(dp(12), dp(4), dp(12), dp(12));
        field.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        field.setVerticalScrollBarEnabled(true);
        field.setOnEditorActionListener((v, actionId, event) -> {
            if (!mHost.send(field.getText().toString())) return true;
            field.setText("");
            hide();
            return true;
        });
        field.setOnFocusChangeListener((v, hasFocus) -> mHost.lendTerminalFocus(hasFocus));
        body.addView(field, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        body.addView(resizeCorner(outline), new FrameLayout.LayoutParams(
            dp(CORNER_DP), dp(CORNER_DP), Gravity.BOTTOM | Gravity.END));
        root.addView(body, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        root.setVisibility(View.GONE);
        parent.addView(root, new FrameLayout.LayoutParams(dp(MIN_WIDTH_DP), dp(MIN_HEIGHT_DP)));
        parent.addOnLayoutChangeListener(mParentLayout);
        mRoot = root;
        mField = field;
    }

    @SuppressLint("ClickableViewAccessibility")
    @NonNull
    private View header(int ink) {
        LinearLayout header = new LinearLayout(mContext);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), 0, 0, 0);

        View grip = new View(mContext);
        GradientDrawable bar = new GradientDrawable();
        bar.setColor(Color.argb(110, Color.red(ink), Color.green(ink), Color.blue(ink)));
        bar.setCornerRadius(dp(2));
        grip.setBackground(bar);
        LinearLayout.LayoutParams gripParams = new LinearLayout.LayoutParams(dp(32), dp(4));
        header.addView(grip, gripParams);
        header.addView(new View(mContext), new LinearLayout.LayoutParams(0, 1, 1f));

        TextView close = new TextView(mContext);
        close.setText("✕");
        close.setTextColor(ink);
        close.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription(mContext.getString(android.R.string.cancel));
        close.setOnClickListener(v -> hide());
        header.addView(close, new LinearLayout.LayoutParams(dp(HEADER_DP + 12),
            ViewGroup.LayoutParams.MATCH_PARENT));

        float[] down = new float[2];
        RectF start = new RectF();
        header.setOnTouchListener((v, event) -> drag(event, down, start, false));
        return header;
    }

    @SuppressLint("ClickableViewAccessibility")
    @NonNull
    private View resizeCorner(int ink) {
        View corner = new View(mContext) {
            private final android.graphics.Paint mPaint = new android.graphics.Paint(
                android.graphics.Paint.ANTI_ALIAS_FLAG);

            @Override
            protected void onDraw(@NonNull android.graphics.Canvas canvas) {
                mPaint.setColor(ink);
                mPaint.setStrokeWidth(Math.max(1f, mDensity * 1.5f));
                float w = getWidth(), h = getHeight(), inset = mDensity * 7f;
                for (int i = 1; i <= 2; i++) {
                    float d = inset + i * mDensity * 5f;
                    canvas.drawLine(w - inset, h - d, w - d, h - inset, mPaint);
                }
            }
        };
        float[] down = new float[2];
        RectF start = new RectF();
        corner.setOnTouchListener((v, event) -> drag(event, down, start, true));
        return corner;
    }

    /** Moves the popup by the finger, or resizes it from its corner, within its parent. */
    private boolean drag(@NonNull MotionEvent event, @NonNull float[] down, @NonNull RectF start,
                         boolean resize) {
        FrameLayout parent = mParent;
        if (parent == null || parent.getWidth() <= 0 || parent.getHeight() <= 0) return false;
        float w = parent.getWidth(), h = parent.getHeight();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                down[0] = event.getRawX();
                down[1] = event.getRawY();
                start.set(mFrac);
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dx = (event.getRawX() - down[0]) / w;
                float dy = (event.getRawY() - down[1]) / h;
                RectF next = new RectF(start);
                if (resize) {
                    next.right = start.right + dx;
                    next.bottom = start.bottom + dy;
                } else {
                    next.offset(dx, dy);
                }
                mFrac.set(clamp(next, dp(MIN_WIDTH_DP) / w, dp(MIN_HEIGHT_DP) / h, resize));
                place();
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mPrefs.edit().putFloat(KEY_LEFT, mFrac.left).putFloat(KEY_TOP, mFrac.top)
                    .putFloat(KEY_RIGHT, mFrac.right).putFloat(KEY_BOTTOM, mFrac.bottom).apply();
                return true;
            default:
                return false;
        }
    }

    /** Lays the popup at its fraction of the parent as the parent is now. */
    private void place() {
        FrameLayout parent = mParent;
        LinearLayout root = mRoot;
        if (parent == null || root == null || root.getVisibility() != View.VISIBLE) return;
        int w = parent.getWidth(), h = parent.getHeight();
        if (w <= 0 || h <= 0) {
            parent.post(this::place);
            return;
        }
        int[] box = boundsPx(mFrac, w, h, dp(MIN_WIDTH_DP), dp(MIN_HEIGHT_DP));
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) root.getLayoutParams();
        if (params.leftMargin == box[0] && params.topMargin == box[1]
            && params.width == box[2] && params.height == box[3]) return;
        params.gravity = Gravity.TOP | Gravity.START;
        params.leftMargin = box[0];
        params.topMargin = box[1];
        params.width = box[2];
        params.height = box[3];
        root.setLayoutParams(params);
    }

    // ------------------------------------------------------------------------------- geometry

    /**
     * {@code frac} in pixels of a {@code w}×{@code h} parent as {left, top, width, height}: never
     * smaller than the minimum, never past the parent — so a keyboard that shortens the window
     * pushes the popup up and then shortens it, rather than covering it.
     */
    @VisibleForTesting
    static int[] boundsPx(@NonNull RectF frac, int w, int h, int minW, int minH) {
        int width = Math.min(w, Math.max(minW, Math.round(frac.width() * w)));
        int height = Math.min(h, Math.max(minH, Math.round(frac.height() * h)));
        int left = Math.max(0, Math.min(w - width, Math.round(frac.left * w)));
        int top = Math.max(0, Math.min(h - height, Math.round(frac.top * h)));
        return new int[]{left, top, width, height};
    }

    /** {@code next} kept inside the unit square, at least {@code minW}×{@code minH}. */
    @VisibleForTesting
    static RectF clamp(@NonNull RectF next, float minW, float minH, boolean resize) {
        RectF out = new RectF(next);
        minW = Math.min(1f, minW);
        minH = Math.min(1f, minH);
        if (resize) {
            out.right = Math.max(out.left + minW, Math.min(1f, out.right));
            out.bottom = Math.max(out.top + minH, Math.min(1f, out.bottom));
        } else {
            float width = out.width(), height = out.height();
            out.left = Math.max(0f, Math.min(1f - width, out.left));
            out.top = Math.max(0f, Math.min(1f - height, out.top));
            out.right = out.left + width;
            out.bottom = out.top + height;
        }
        return out;
    }

    private static boolean sane(@NonNull RectF frac) {
        return frac.left >= 0f && frac.top >= 0f && frac.right <= 1f && frac.bottom <= 1f
            && frac.width() > 0f && frac.height() > 0f;
    }

    private int dp(float value) {
        return Math.round(value * mDensity);
    }
}
