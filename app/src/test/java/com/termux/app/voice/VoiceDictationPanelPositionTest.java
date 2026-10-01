package com.termux.app.voice;

import static org.junit.Assert.assertEquals;

import android.app.Activity;
import android.app.Dialog;
import android.view.Gravity;
import android.view.WindowManager;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class VoiceDictationPanelPositionTest {

    @Test
    public void panelOpensAtBottomWithoutDimmingTheTerminal() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        Dialog dialog = new Dialog(activity);
        dialog.show();

        VoiceDictationController.positionPanelAtBottom(dialog);

        WindowManager.LayoutParams attributes = dialog.getWindow().getAttributes();
        assertEquals(Gravity.BOTTOM, attributes.gravity & Gravity.VERTICAL_GRAVITY_MASK);
        assertEquals(0, attributes.flags & WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        dialog.dismiss();
    }
}
