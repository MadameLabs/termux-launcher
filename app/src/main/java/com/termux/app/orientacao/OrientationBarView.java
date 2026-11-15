package com.termux.app.orientacao;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The orientation chips in the status bar's clock cell (MadameLabs fork): machine, folder and chat on the left,
 * the harness's 5 h and weekly spend on the right. Dressed like the window pills beside them: a
 * fully rounded body with a thin outline and a faint fill in the chip's Catppuccin Mocha colour,
 * the icon in that colour (drawn by the bundled Nerd Font symbols face) and the text in white.
 */
public class OrientationBarView extends LinearLayout {

    static final int TEXT = Color.WHITE;
    static final int RED = Color.parseColor("#f38ba8");
    static final int MACHINE = Color.parseColor("#a6e3a1");
    static final int FOLDER = Color.parseColor("#f5e0dc");
    static final int CHAT = Color.parseColor("#cba6f7");
    static final int FIVE_HOUR = Color.parseColor("#f9e2af");
    static final int WEEKLY = Color.parseColor("#89b4fa");

    /** Height of the bar, in dp: one line of chip text. */
    public static final float HEIGHT_DP = 20f;

    /** One chip: its root (cap + body) and the text in the body. */
    static final class Chip {
        final LinearLayout root;
        final TextView icon;
        final TextView text;

        Chip(LinearLayout root, TextView icon, TextView text) {
            this.root = root;
            this.icon = icon;
            this.text = text;
        }
    }

    private final Chip mMachine, mFolder, mChat, mFiveHour, mWeekly;
    private final float mDensity;

    public OrientationBarView(@NonNull Context context) {
        this(context, null);
    }

    public OrientationBarView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mDensity = context.getResources().getDisplayMetrics().density;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        int pad = dp(6);
        setPadding(pad, 0, pad, 0);
        mMachine = addChip(MACHINE, "\uDB80\uDF79"); // md-monitor, as tmux-ia.sh
        mFolder = addChip(FOLDER, "");           // fa-folder
        mChat = addChip(CHAT, "󰻞");         // md-chat
        View spacer = new View(context);
        addView(spacer, new LayoutParams(0, 1, 1f));
        mFiveHour = addChip(FIVE_HOUR, "󱑏"); // md-clock 5
        mWeekly = addChip(WEEKLY, "󰨳");     // md-calendar-week
    }

    /** Draws {@code line}; a null field hides its chip. */
    public void render(@NonNull OrientationLine line) {
        show(mMachine, line.machine, false);
        show(mFolder, line.path, false);
        show(mChat, line.chat, false);
        show(mFiveHour, line.fiveHour == null ? null : line.fiveHour + "%", line.fiveHourHot);
        show(mWeekly, line.weekly == null ? null : line.weekly + "%", line.weeklyHot); // the calendar icon names it
    }

    @Nullable private Typeface mTypeface;

    /** Draws every chip in {@code typeface}, the terminal's, so its Nerd Font glyphs show. */
    public void setChipTypeface(@Nullable Typeface typeface) {
        if (typeface == null || typeface == mTypeface) return;
        mTypeface = typeface;
        // The icons keep the symbols face: the terminal's own may carry no Nerd Font glyphs.
        for (Chip chip : new Chip[]{mMachine, mFolder, mChat, mFiveHour, mWeekly}) {
            chip.text.setTypeface(typeface);
        }
    }

    /** About how many characters fit across, for {@link OrientationModel#fit}. */
    public int maxChars(int widthPx) {
        float charPx = mMachine.text.getPaint().measureText("m");
        int perChip = 4; // icon, its gap and the rounded ends, in characters
        if (widthPx <= 0 || charPx <= 0) return 200; // not laid out yet: the next tick fits it
        return Math.max(10, (int) (widthPx / charPx) - perChip * 5);
    }

    @NonNull
    List<Chip> visibleChips() {
        List<Chip> out = new ArrayList<>();
        for (Chip chip : new Chip[]{mMachine, mFolder, mChat, mFiveHour, mWeekly}) {
            if (chip.root.getVisibility() == VISIBLE) out.add(chip);
        }
        return out;
    }

    private void show(@NonNull Chip chip, @Nullable String text, boolean hot) {
        if (text == null || text.isEmpty()) {
            chip.root.setVisibility(GONE);
            return;
        }
        chip.text.setText(text);
        chip.text.setTextColor(hot ? RED : TEXT);
        chip.root.setVisibility(VISIBLE);
    }

    @NonNull
    private Chip addChip(int color, @NonNull String glyph) {
        Context context = getContext();
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(HORIZONTAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        float radius = dp(HEIGHT_DP) / 2f;

        GradientDrawable pill = rounded(Color.argb(38, Color.red(color), Color.green(color),
            Color.blue(color)), new float[]{radius, radius, radius, radius, radius, radius, radius, radius});
        pill.setStroke(Math.max(1, dp(1)), Color.argb(150, Color.red(color), Color.green(color),
            Color.blue(color)));
        root.setBackground(pill);

        TextView icon = label(context);
        icon.setText(glyph);
        icon.setTextColor(color);
        Typeface symbols = com.termux.shared.termux.font.NerdFontSpans.typeface(context);
        if (symbols != null) icon.setTypeface(symbols);
        icon.setPadding(dp(7), 0, dp(4), 0);
        root.addView(icon, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));

        TextView text = label(context);
        text.setTextColor(TEXT);
        text.setPadding(0, 0, dp(8), 0);
        text.setEllipsize(TextUtils.TruncateAt.END);
        root.addView(text, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));

        LayoutParams params = new LayoutParams(LayoutParams.WRAP_CONTENT, dp(HEIGHT_DP));
        params.rightMargin = dp(4);
        root.setVisibility(GONE);
        addView(root, params);
        return new Chip(root, icon, text);
    }

    @NonNull
    private TextView label(@NonNull Context context) {
        TextView view = new TextView(context);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        view.setTypeface(Typeface.MONOSPACE);
        view.setSingleLine(true);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setIncludeFontPadding(false);
        return view;
    }

    @NonNull
    private static GradientDrawable rounded(int color, @NonNull float[] radii) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadii(radii);
        return drawable;
    }

    private int dp(float value) {
        return Math.round(value * mDensity);
    }
}
