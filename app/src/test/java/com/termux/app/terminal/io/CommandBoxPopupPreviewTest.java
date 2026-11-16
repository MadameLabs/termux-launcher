package com.termux.app.terminal.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Build;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** The command box as the Appearance editor's preview: above the sheet, never taking the input. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P)
public class CommandBoxPopupPreviewTest {

    private FrameLayout mParent;
    private View mSheet;
    private CommandBoxPopup mPopup;
    private int mBegins;
    private int mEnds;

    @Before
    public void setUp() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        mParent = new FrameLayout(activity);
        mSheet = new View(activity);
        mSheet.setElevation(30f);
        mParent.addView(mSheet);
        activity.setContentView(mParent);
        mPopup = new CommandBoxPopup(activity, new CommandBoxPopup.Host() {
            @Override public boolean send(@NonNull String text) { return true; }
            @Override public void beginInput(@NonNull EditText field) { mBegins++; }
            @Override public void endInput() { mEnds++; }
            @Override public float terminalTextSizePx() { return 28f; }
            @Nullable @Override public Typeface terminalTypeface() { return null; }
            @Nullable @Override public com.termux.app.terminal.PaneSurfaceStyle paneSurfaceStyle() { return null; }
            @Override public int terminalForegroundColor() { return 0; }
            @Override public void openSettings() {}
            @Override public void toggleDictation() {}
            @Override public void lendTerminalFocus(boolean lent) {}
        });
    }

    @Test
    public void thePreviewStandsAboveTheSheetWithoutTakingTheInput() {
        mPopup.showPreview(mParent);
        assertTrue(mPopup.isShown());
        assertEquals(0, mBegins);
        View root = mParent.getChildAt(mParent.getChildCount() - 1);
        assertTrue("above the editor's sheet", root.getZ() > mSheet.getZ());

        mPopup.hidePreview();
        assertFalse(mPopup.isShown());
        assertEquals("the preview never took the input", 0, mEnds);
        assertEquals(0f, root.getTranslationZ(), 0f);
    }

    @Test
    public void aBoxOpenForTypingIsNotTakenDownByTheEditor() {
        mPopup.show(mParent);
        assertEquals(1, mBegins);
        mPopup.showPreview(mParent);
        mPopup.hidePreview();
        assertTrue(mPopup.isShown());
    }

    @Test
    public void theToggleKeyOverThePreviewOpensTheBoxForTyping() {
        mPopup.showPreview(mParent);
        assertTrue(mPopup.toggle(mParent));
        assertEquals(1, mBegins);
        mPopup.hidePreview();
        assertTrue("typing now, so it stays", mPopup.isShown());
    }
}
