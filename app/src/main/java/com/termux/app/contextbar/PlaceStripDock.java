package com.termux.app.contextbar;

import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Moves the place strip (the sessions indicator and the window chips) between the status row and
 * the keys panel, for "Context chips above the keys" (MadameLabs fork). One strip, moved rather
 * than rebuilt, so everything bound to it by id (the chips, their reveal, the sessions indicator)
 * follows. It remembers where the strip stood so it goes back to the same place.
 */
public final class PlaceStripDock {

    @Nullable private ViewGroup mHomeParent;
    private int mHomeIndex = -1;
    @Nullable private ViewGroup.LayoutParams mHomeParams;
    private boolean mDocked;

    /**
     * Puts {@code strip} in {@code dockSlot} when {@code docked}, back where it stood otherwise.
     * Cheap and idempotent, since the context bar's tick calls it.
     *
     * @return whether anything moved, so the caller re-runs the bar's edge and geometry
     */
    public boolean apply(boolean docked, @NonNull View strip, @NonNull ViewGroup dockSlot) {
        if (docked == mDocked && (strip.getParent() == dockSlot) == docked) return false;
        mDocked = docked;
        ViewGroup current = (ViewGroup) strip.getParent();
        if (docked) {
            if (current != dockSlot) {
                if (current != null) {
                    mHomeParent = current;
                    mHomeIndex = current.indexOfChild(strip);
                    mHomeParams = strip.getLayoutParams();
                    current.removeView(strip);
                }
                strip.setTranslationX(0f);
                strip.setTranslationY(0f);
                dockSlot.addView(strip, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            }
        } else if (current == dockSlot && mHomeParent != null) {
            dockSlot.removeView(strip);
            int index = Math.min(Math.max(0, mHomeIndex), mHomeParent.getChildCount());
            if (mHomeParams != null) mHomeParent.addView(strip, index, mHomeParams);
            else mHomeParent.addView(strip, index);
        }
        return true;
    }
}
