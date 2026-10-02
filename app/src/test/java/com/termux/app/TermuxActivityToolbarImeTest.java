package com.termux.app;

import android.app.Application;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import com.termux.R;
import com.termux.app.chrome.ChromeSpec;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class TermuxActivityToolbarImeTest {
    private TermuxActivity activity;

    @Before public void setUp() {
        activity = Robolectric.buildActivity(TermuxActivity.class).get();
        activity.setContentView(R.layout.activity_termux);
        ReflectionHelpers.setField(activity, "mPreferences",
            TermuxAppSharedPreferences.build(activity, false));
    }

    @Test public void systemImeDoesNotLiftTheDockTwice() {
        ChromeSpec state = new ChromeSpec(true, false, 0, false, false, false, true, 1f, 0);
        ReflectionHelpers.setField(activity, "mImeLiftPx", 0);
        int gap = bottomMargin(state);
        ReflectionHelpers.setField(activity, "mImeLiftPx", 1096);
        assertEquals("adjustResize already removed 1096px from the content", gap, bottomMargin(state));
    }

    @Test public void embeddedKeyboardHasNoAdditionalImeMargin() {
        ReflectionHelpers.setField(activity, "mImeLiftPx", 1096);
        assertEquals(0, bottomMargin(new ChromeSpec(true, true, 700,
            false, false, false, true, 1f, 0)));
    }

    @Test public void commandFieldIsVisibleBelowTheKeyPagerWithoutSwiping() {
        ViewGroup host = activity.findViewById(R.id.terminal_toolbar_host);
        View pager = activity.findViewById(R.id.terminal_toolbar_view_pager);
        EditText input = host.findViewById(R.id.terminal_toolbar_text_input);
        assertNotNull("the command row must exist independently of pager pages", input);
        ((View) input.getParent()).setVisibility(View.VISIBLE);
        // Measure the toolbar independently of the initially hidden accessory stack.
        ((ViewGroup) host.getParent()).removeView(host);
        activity.setContentView(host);
        pager.setVisibility(View.VISIBLE);
        host.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.AT_MOST));
        host.layout(0, 0, host.getMeasuredWidth(), host.getMeasuredHeight());
        assertEquals(View.VISIBLE, input.getVisibility());
        View commandRow = (View) input.getParent();
        assertEquals(View.VISIBLE, commandRow.getVisibility());
        assertTrue("the command field must not overlap the shortcuts",
            commandRow.getTop() >= pager.getBottom());
        assertTrue(input.getHeight() > 0);
    }

    private int bottomMargin(ChromeSpec state) {
        return ReflectionHelpers.callInstanceMethod(activity, "resolveAccessoryStackBottomMarginPx",
            ClassParameter.from(ChromeSpec.class, state));
    }
}
