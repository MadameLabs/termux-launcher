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
        /**
         * The panes' surface style, the Terminal element's glass the terminal panes wear (and the
         * assistant page and the floats' titlebars beside them); null before there is one.
         */
        @Nullable com.termux.app.terminal.PaneSurfaceStyle paneSurfaceStyle();
        /** The focused terminal's text colour, or 0 without one. */
        int terminalForegroundColor();
        /** Opens Settings › Terminal › Command box. */
        void openSettings();
        /** Starts or stops the launcher's voice dictation, as the Dictate key does. */
        void toggleDictation();
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
    private static final int MIN_HEIGHT_DP = 128;
    private static final int HEADER_DP = 28;
    private static final int CORNER_DP = 28;
    /** The one row of keys, at about the height of a key of the in-app keyboard. */
    private static final int KEY_ROW_DP = 48;
    private static final int KEY_GAP_DP = 4;
    /** Each key's share of the row, as a keyboard row shares its width. */
    private static final float W_KEY = 1f, W_SPACE = 2.8f, W_DPAD = 1.35f, W_ENTER = 1.6f;
    private static final String G_NEW_LINE = "¶";
    private static final String G_SETTINGS = "";

    // Glyphs of the in-app keyboard's font (juloo.keyboard2.KeyValue), so the keys match it.
    private static final String G_PAGE_UP = "", G_PAGE_DOWN = "";
    private static final String G_UP = "", G_RIGHT = "", G_DOWN = "",
        G_LEFT = "";
    private static final String G_HOME = "", G_END = "", G_ENTER = "";
    private static final String G_CLIPBOARD = "", G_DICTATE = "";
    private static final String G_COPY = "", G_CUT = "", G_PASTE = "";

    private final Context mContext;
    private final Host mHost;
    private final SharedPreferences mPrefs;
    private final float mDensity;
    @NonNull private final RectF mFrac;

    @Nullable private FrameLayout mParent;
    @Nullable private FrameLayout mRoot;
    /** The Terminal element's glass slab, as a pane wears it (PaneGlass). */
    @Nullable private com.termux.app.terminal.PaneGlassBackdropView mGlass;
    private final com.termux.app.terminal.PaneRim mRim = new com.termux.app.terminal.PaneRim();
    private float mRadiusPx;
    @Nullable private EditText mField;
    @NonNull private CommandBoxLook mLook = CommandBoxLook.defaults();
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
        // Built again on each open, so what was changed in Settings › Command box shows at once.
        mLook = CommandBoxLook.read(mContext);
        ensureViews(parent);
        FrameLayout root = mRoot;
        EditText field = mField;
        if (root == null || field == null) return;
        if (mLook.textSizeFollowsTerminal) {
            field.setTextSize(TypedValue.COMPLEX_UNIT_PX, mHost.terminalTextSizePx());
        } else {
            field.setTextSize(TypedValue.COMPLEX_UNIT_SP, mLook.textSizeSp);
        }
        Typeface typeface = mHost.terminalTypeface();
        if (typeface != null) field.setTypeface(typeface);
        root.setVisibility(View.VISIBLE);
        root.bringToFront();
        place();
        applyStyle(mHost.paneSurfaceStyle());
        // Once laid out, so the slab is cut at the popup's own size, as a pane's is.
        root.post(() -> applyStyle(mHost.paneSurfaceStyle()));
        field.requestFocus();
        field.setSelection(field.length());
        mHost.beginInput(field);
    }

    /** Hides the popup; the draft stays in the field for the next time. */
    public void hide() {
        FrameLayout root = mRoot;
        if (root == null || root.getVisibility() != View.VISIBLE) return;
        root.setVisibility(View.GONE);
        if (mField != null) mField.clearFocus();
        mHost.endInput();
    }

    // ------------------------------------------------------------------------------- views

    private void ensureViews(@NonNull FrameLayout parent) {
        CharSequence draft = mField != null ? mField.getText() : null;
        if (mRoot != null && mParent != null) {
            mParent.removeView(mRoot);
            mParent.removeOnLayoutChangeListener(mParentLayout);
        }
        mParent = parent;
        CommandBoxLook look = mLook;
        int outline = MaterialColors.getColor(mContext,
            com.termux.shared.R.attr.termuxColorOutlineVariant, Color.GRAY);
        int launcherInk = MaterialColors.getColor(mContext,
            com.termux.shared.R.attr.termuxColorOnSurface,
            ContextCompat.getColor(mContext, R.color.termux_on_surface));
        int terminalInk = look.terminalColors ? mHost.terminalForegroundColor() : 0;
        // The terminal's colours when asked for, so the text reads as it does in the terminal.
        int onSurface = terminalInk != 0 ? (terminalInk | 0xFF000000) : launcherInk;
        int hint = terminalInk != 0
            ? Color.argb(140, Color.red(onSurface), Color.green(onSurface), Color.blue(onSurface))
            : MaterialColors.getColor(mContext,
                com.termux.shared.R.attr.termuxColorOnSurfaceVariant, outline);

        // The Terminal element's glass, worn as a pane wears it (PaneGlass, as the assistant page
        // and the floats' titlebars do): one slab under the content, its rim on the frame, all of
        // it from the panes' surface style. Nothing here reads a look value of its own.
        FrameLayout frame = new FrameLayout(mContext);
        frame.setClickable(true);
        frame.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outlineOut) {
                outlineOut.setRoundRect(0, 0, view.getWidth(), view.getHeight(), mRadiusPx);
            }
        });
        frame.setClipToOutline(true);
        com.termux.app.terminal.PaneGlassBackdropView glass =
            new com.termux.app.terminal.PaneGlassBackdropView(mContext);
        glass.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        glass.setVisibility(View.GONE);
        frame.addView(glass, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        com.termux.app.terminal.PaneGlass.followLayout(glass);

        LinearLayout root = new LinearLayout(mContext);
        root.setOrientation(LinearLayout.VERTICAL);
        frame.addView(root, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

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
        field.setHintTextColor(hint);
        if (draft != null) field.setText(draft);
        field.setPadding(dp(12), dp(4), dp(12), dp(12));
        field.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        field.setVerticalScrollBarEnabled(true);
        field.setOnEditorActionListener((v, actionId, event) -> {
            send(field);
            return true;
        });
        field.setOnFocusChangeListener((v, hasFocus) -> mHost.lendTerminalFocus(hasFocus));
        body.addView(field, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        body.addView(resizeCorner(outline), new FrameLayout.LayoutParams(
            dp(CORNER_DP), dp(CORNER_DP), Gravity.BOTTOM | Gravity.END));
        root.addView(body, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(keyRow(field, onSurface), new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(KEY_ROW_DP)));

        frame.setVisibility(View.GONE);
        parent.addView(frame, new FrameLayout.LayoutParams(dp(MIN_WIDTH_DP), dp(MIN_HEIGHT_DP)));
        parent.addOnLayoutChangeListener(mParentLayout);
        mRoot = frame;
        mGlass = glass;
        mField = field;
    }

    /**
     * Dresses the popup from the panes' surface style, as a pane is dressed: the slab, the radius
     * and the rim, or a solid card while the Look wears no glass. Run on open and on every style
     * change, the same pass that re-dresses the panes and the wall's pages.
     */
    public void applyStyle(@Nullable com.termux.app.terminal.PaneSurfaceStyle style) {
        FrameLayout frame = mRoot;
        com.termux.app.terminal.PaneGlassBackdropView glass = mGlass;
        if (frame == null || glass == null || frame.getVisibility() != View.VISIBLE) return;
        mRadiusPx = style == null ? dp(14)
            : com.termux.app.terminal.PaneGlass.radiusPx(style, mDensity);
        boolean slab = com.termux.app.terminal.PaneGlass.apply(style, frame, glass, mRadiusPx);
        // CBX-DBG temporario (nao vai para a main).
        android.util.Log.i("CBX-DBG", "applyStyle style=" + (style == null ? "null" : style.getClass().getSimpleName())
            + " active=" + (style != null && style.isPaneGlassActive())
            + " frame=" + (style == null ? null : style.paneGlassBlurFrame())
            + " slab=" + slab + " glassVis=" + glass.getVisibility()
            + " size=" + frame.getWidth() + "x" + frame.getHeight()
            + " glassSize=" + glass.getWidth() + "x" + glass.getHeight());
        // Glass off: a solid card, as the assistant page falls back to.
        if (slab) {
            frame.setBackground(null);
        } else {
            GradientDrawable card = new GradientDrawable();
            card.setColor(MaterialColors.getColor(mContext,
                com.termux.shared.R.attr.termuxColorSurfacePanelHigh,
                ContextCompat.getColor(mContext, R.color.termux_surface_panel_high)));
            card.setCornerRadius(mRadiusPx);
            frame.setBackground(card);
        }
        if (slab || com.termux.app.terminal.PaneRim.plainBorderWanted(style)) {
            mRim.apply(frame, slab, mRadiusPx, com.termux.app.terminal.PaneBorderStyle.lone(), style);
        } else {
            mRim.clear(frame);
        }
        frame.invalidateOutline();
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

    // ------------------------------------------------------------------------------- keys

    /**
     * The one row of keys under the field, drawn and shared out as a row of the in-app keyboard
     * with its icons: cut, copy, paste and the clipboard's history; the space bar, which slides
     * the cursor; the arrow key; and Enter. Cut and copy take the selection, or the whole text
     * when nothing is selected. The arrow key is swiped like the keyboard's: the four sides move
     * the cursor and repeat while held; the corners are home, page up, page down and end.
     */
    @NonNull
    private View keyRow(@NonNull EditText field, int ink) {
        LinearLayout row = new LinearLayout(mContext);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(6), dp(2), dp(6), dp(6));

        Typeface font = keyFont();
        int key = Color.argb(34, Color.red(ink), Color.green(ink), Color.blue(ink));
        int pressed = Color.argb(80, Color.red(ink), Color.green(ink), Color.blue(ink));

        addTapKey(row, font, ink, key, pressed, G_CUT, R.string.command_box_cut, v -> {
            CharSequence taken = taken(field);
            com.termux.app.terminal.ClipboardHistory.copy(mContext, taken.toString());
            replaceTaken(field, "");
        });
        addTapKey(row, font, ink, key, pressed, G_COPY, R.string.command_box_copy, v ->
            com.termux.app.terminal.ClipboardHistory.copy(mContext, taken(field).toString()));
        addTapKey(row, font, ink, key, pressed, G_PASTE, R.string.command_box_paste, v -> {
            String clip = clipboardText();
            if (clip != null) insert(field, clip);
        });
        addTapKey(row, font, ink, key, pressed, G_CLIPBOARD,
            R.string.command_box_clipboard_history, v -> showHistory(v, field));

        SpaceSliderView space = new SpaceSliderView(mContext,
            () -> insert(field, " "), steps -> moveBy(field, steps));
        space.setColors(ink, key, pressed);
        space.setContentDescription(mContext.getString(R.string.command_box_space));
        row.addView(space, keyParams(W_SPACE));

        SwipeKeyView dpad = new SwipeKeyView(mContext, font)
            .set(SwipeKeyView.N, G_UP, () -> moveLine(field, -1), true)
            .set(SwipeKeyView.S, G_DOWN, () -> moveLine(field, 1), true)
            .set(SwipeKeyView.W, G_LEFT, () -> moveBy(field, -1), true)
            .set(SwipeKeyView.E, G_RIGHT, () -> moveBy(field, 1), true)
            .set(SwipeKeyView.NW, G_HOME, () -> lineEdge(field, false), false)
            .set(SwipeKeyView.NE, G_PAGE_UP, () -> movePage(field, -1), false)
            .set(SwipeKeyView.SW, G_PAGE_DOWN, () -> movePage(field, 1), false)
            .set(SwipeKeyView.SE, G_END, () -> lineEdge(field, true), false);
        dpad.setColors(ink, key, pressed);
        dpad.setContentDescription(mContext.getString(R.string.command_box_arrows));
        row.addView(dpad, keyParams(W_DPAD));

        addTapKey(row, font, ink, key, pressed, G_DICTATE, R.string.command_box_dictate,
            v -> mHost.toggleDictation());

        // Enter sends; swiped up it breaks the line instead (a bare LF, which Claude Code and
        // most line editors take as a new line rather than as Enter).
        SwipeKeyView enter = new SwipeKeyView(mContext, font)
            .set(SwipeKeyView.C, G_ENTER, () -> send(field), false)
            .set(SwipeKeyView.N, G_NEW_LINE, () -> insert(field, "\n"), false)
            // Down and left: this box's own page in Settings.
            .set(SwipeKeyView.SW, G_SETTINGS, () -> {
                hide();
                mHost.openSettings();
            }, false);
        enter.setColors(ink, key, pressed);
        enter.setContentDescription(mContext.getString(R.string.command_box_send));
        LinearLayout.LayoutParams enterParams = keyParams(W_ENTER);
        enterParams.setMarginEnd(0);
        row.addView(enter, enterParams);
        return row;
    }

    @NonNull
    private LinearLayout.LayoutParams keyParams(float weight) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.MATCH_PARENT, weight);
        params.setMarginEnd(dp(KEY_GAP_DP));
        return params;
    }

    /** A key that only answers a tap, drawn as the others are. */
    private void addTapKey(@NonNull LinearLayout row, @Nullable Typeface font, int ink, int key,
                           int pressed, @NonNull String glyph, int labelRes,
                           @NonNull View.OnClickListener action) {
        SwipeKeyView view = new SwipeKeyView(mContext, font);
        view.set(SwipeKeyView.C, glyph, () -> action.onClick(view), false);
        view.setColors(ink, key, pressed);
        view.setContentDescription(mContext.getString(labelRes));
        row.addView(view, keyParams(W_KEY));
    }

    /** Sends the field to the focused session and closes, as the keyboard's Enter does. */
    private void send(@NonNull EditText field) {
        if (!mHost.send(field.getText().toString())) return;
        field.setText("");
        hide();
    }

    @Nullable
    private Typeface keyFont() {
        try {
            return juloo.keyboard2.Theme.getKeyFont(mContext);
        } catch (RuntimeException e) {
            return null;
        }
    }


    /** The selection, or the whole text when nothing is selected. */
    @NonNull
    private static CharSequence taken(@NonNull EditText field) {
        int start = Math.min(field.getSelectionStart(), field.getSelectionEnd());
        int end = Math.max(field.getSelectionStart(), field.getSelectionEnd());
        if (start < 0 || start == end) return field.getText();
        return field.getText().subSequence(start, end);
    }

    private static void replaceTaken(@NonNull EditText field, @NonNull String with) {
        int start = Math.min(field.getSelectionStart(), field.getSelectionEnd());
        int end = Math.max(field.getSelectionStart(), field.getSelectionEnd());
        if (start < 0 || start == end) {
            field.setText(with);
            field.setSelection(field.length());
        } else {
            field.getText().replace(start, end, with);
        }
    }

    /** {@code text} at the cursor, over the selection if there is one. */
    @VisibleForTesting
    static void insert(@NonNull EditText field, @NonNull String text) {
        int start = Math.max(0, Math.min(field.getSelectionStart(), field.getSelectionEnd()));
        int end = Math.max(0, Math.max(field.getSelectionStart(), field.getSelectionEnd()));
        field.getText().replace(start, end, text);
        field.setSelection(Math.min(field.length(), start + text.length()));
    }

    @VisibleForTesting
    static void moveBy(@NonNull EditText field, int delta) {
        int at = Math.max(0, Math.min(field.length(), field.getSelectionEnd() + delta));
        field.setSelection(at);
    }

    /** The cursor a line up or down, at about the same column, as the layout wraps the text. */
    private static void moveLine(@NonNull EditText field, int delta) {
        android.text.Layout layout = field.getLayout();
        if (layout == null) return;
        int at = field.getSelectionEnd();
        int line = layout.getLineForOffset(at) + delta;
        if (line < 0) {
            field.setSelection(0);
            return;
        }
        if (line >= layout.getLineCount()) {
            field.setSelection(field.length());
            return;
        }
        field.setSelection(layout.getOffsetForHorizontal(line, layout.getPrimaryHorizontal(at)));
    }

    /** The cursor to the start or the end of the line it is on, as the layout wraps the text. */
    private static void lineEdge(@NonNull EditText field, boolean end) {
        android.text.Layout layout = field.getLayout();
        if (layout == null) return;
        int line = layout.getLineForOffset(field.getSelectionEnd());
        int at = end ? layout.getLineVisibleEnd(line) : layout.getLineStart(line);
        field.setSelection(Math.max(0, Math.min(field.length(), at)));
    }

    /** The cursor a field's height of lines up or down. */
    private static void movePage(@NonNull EditText field, int direction) {
        int lineHeight = Math.max(1, field.getLineHeight());
        int lines = Math.max(1, (field.getHeight() - field.getTotalPaddingTop()
            - field.getTotalPaddingBottom()) / lineHeight - 1);
        moveLine(field, direction * lines);
    }

    @Nullable
    private String clipboardText() {
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager)
            mContext.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null || !clipboard.hasPrimaryClip()) return null;
        android.content.ClipData clip = clipboard.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) return null;
        CharSequence text = clip.getItemAt(0).coerceToText(mContext);
        return text == null ? null : text.toString();
    }

    /** The launcher's clipboard history as a menu; a tap puts that text at the cursor. */
    private void showHistory(@NonNull View anchor, @NonNull EditText field) {
        java.util.List<com.termux.app.terminal.ClipboardHistory.Item> items =
            com.termux.app.terminal.ClipboardHistory.get(mContext).items();
        android.widget.PopupMenu menu = new android.widget.PopupMenu(mContext, anchor);
        if (items.isEmpty()) {
            menu.getMenu().add(R.string.command_box_clipboard_empty).setEnabled(false);
        }
        for (int i = 0; i < items.size() && i < 20; i++) {
            String text = items.get(i).text;
            String label = text.replace('\n', ' ').trim();
            if (label.length() > 48) label = label.substring(0, 47) + "…";
            menu.getMenu().add(0, i, i, (items.get(i).pinned ? "📌 " : "") + label);
        }
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() >= 0 && item.getItemId() < items.size())
                insert(field, items.get(item.getItemId()).text);
            return true;
        });
        menu.show();
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
                // The grip sits in the bottom-right corner of the text, above the keys.
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
                    // Resized from the bottom-right corner: the left edge stays put.
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
                // A resize changes the slab's size and so its corner; a pane re-dresses the same way.
                if (resize) applyStyle(mHost.paneSurfaceStyle());
                return true;
            default:
                return false;
        }
    }

    /** Lays the popup at its fraction of the parent as the parent is now. */
    /** Where the status bar ends in {@code parent}, in px: 0 where it does not cover it. */
    private static int statusBarBottomPx(@NonNull View parent) {
        android.view.WindowInsets insets = parent.getRootWindowInsets();
        if (insets == null) return 0;
        int inset;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            inset = insets.getInsets(android.view.WindowInsets.Type.statusBars()).top;
        } else {
            inset = insets.getSystemWindowInsetTop();
        }
        // The inset is measured from the window's top; the parent may start below it.
        int[] at = new int[2];
        parent.getLocationInWindow(at);
        return Math.max(0, inset - at[1]);
    }

    private void place() {
        FrameLayout parent = mParent;
        FrameLayout root = mRoot;
        if (parent == null || root == null || root.getVisibility() != View.VISIBLE) return;
        int w = parent.getWidth(), h = parent.getHeight();
        if (w <= 0 || h <= 0) {
            parent.post(this::place);
            return;
        }
        int[] box = boundsPx(mFrac, w, h, dp(MIN_WIDTH_DP), dp(MIN_HEIGHT_DP));
        // Never under the status bar: there its header, the only place it is dragged from, would
        // be out of reach. A place kept from before this rule is brought down here too.
        int minTop = statusBarBottomPx(parent);
        if (box[1] < minTop) {
            box[1] = Math.max(0, Math.min(minTop, h - box[3]));
            float height = mFrac.height();
            mFrac.top = box[1] / (float) h;
            mFrac.bottom = Math.min(1f, mFrac.top + height);
        }
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
            // Resized from the bottom-right corner: the right and bottom edges move.
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
