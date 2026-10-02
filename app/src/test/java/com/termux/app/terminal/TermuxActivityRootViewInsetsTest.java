package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;

import android.os.Build;
import android.graphics.Rect;
import android.view.WindowInsets;

import androidx.core.graphics.Insets;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsCompat.Type;
import androidx.core.view.DisplayCutoutCompat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.RuntimeEnvironment;

import java.util.Collections;

/**
 * The root's fitsSystemWindows padding must not cover the cutout's horizontal column: the content
 * root and the landscape dock rail account for it themselves, so a root that padded for it too
 * pushed everything a second cutout width inward.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.R)
public class TermuxActivityRootViewInsetsTest {

    @Test public void fullscreenDoesNotKeepTheCameraHeightAsTopPadding() {
        // HyperOS's legacy fitsSystemWindows path retains the camera height even with bars hidden.
        // Model that framework boundary while exercising our actual insets listener.
        TermuxActivityRootView root = new TermuxActivityRootView(RuntimeEnvironment.getApplication()) {
            @Override public WindowInsets onApplyWindowInsets(WindowInsets insets) {
                setPadding(0, 152, 0, 0);
                return insets;
            }
        };
        root.setFitsSystemWindows(true);
        WindowInsetsCompat insets = new WindowInsetsCompat.Builder()
            .setInsets(Type.statusBars(), Insets.NONE)
            .setVisible(Type.statusBars(), false)
            .setDisplayCutout(new DisplayCutoutCompat(new Rect(0, 152, 0, 0),
                Collections.singletonList(new Rect(600, 0, 680, 152))))
            .build();
        new TermuxActivityRootView.WindowInsetsListener().onApplyWindowInsets(root, insets.toWindowInsets());
        assertEquals("hidden status bar must free the top band even with a camera cutout", 0,
            root.getPaddingTop());
    }

    @Test public void visibleStatusBarStillKeepsContentBelowItsIcons() {
        TermuxActivityRootView root = new TermuxActivityRootView(RuntimeEnvironment.getApplication());
        root.setFitsSystemWindows(true);
        WindowInsetsCompat insets = new WindowInsetsCompat.Builder()
            .setInsets(Type.statusBars(), Insets.of(0, 152, 0, 0))
            .setVisible(Type.statusBars(), true)
            .build();
        new TermuxActivityRootView.WindowInsetsListener().onApplyWindowInsets(root, insets.toWindowInsets());
        assertEquals(152, root.getPaddingTop());
    }

    @Test public void aCutoutColumnIsNotRootPadding() {
        WindowInsetsCompat insets = new WindowInsetsCompat.Builder()
            .setInsets(Type.statusBars(), Insets.of(0, 63, 0, 0))
            .setInsets(Type.navigationBars(), Insets.of(0, 0, 0, 40))
            .setInsets(Type.displayCutout(), Insets.of(128, 0, 0, 0))
            .build();

        assertEquals(Insets.of(0, 0, 0, 0), TermuxActivityRootView.horizontalRootInsets(insets));
    }

    @Test public void aSideNavigationBarStaysRootPadding() {
        WindowInsetsCompat insets = new WindowInsetsCompat.Builder()
            .setInsets(Type.navigationBars(), Insets.of(0, 0, 126, 0))
            .setInsets(Type.displayCutout(), Insets.of(0, 0, 126, 0))
            .build();

        assertEquals(Insets.of(0, 0, 126, 0), TermuxActivityRootView.horizontalRootInsets(insets));
    }
}
