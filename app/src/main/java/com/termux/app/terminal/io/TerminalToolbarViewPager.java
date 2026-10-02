package com.termux.app.terminal.io;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import androidx.annotation.NonNull;
import androidx.viewpager.widget.PagerAdapter;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.shared.termux.extrakeys.ExtraKeysView;
import com.termux.terminal.TerminalSession;

public class TerminalToolbarViewPager {

    public static class PageAdapter extends PagerAdapter {

        final TermuxActivity mActivity;

        public PageAdapter(TermuxActivity activity) {
            this.mActivity = activity;
        }

        @Override
        public int getCount() {
            return mActivity.getExtraKeysPageCount();
        }

        @Override
        public int getItemPosition(@NonNull Object object) {
            // Pages are rebuilt wholesale after an edit; nothing survives a notifyDataSetChanged.
            return POSITION_NONE;
        }

        @Override
        public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
            return view == object;
        }

        @NonNull
        @Override
        public Object instantiateItem(@NonNull ViewGroup collection, int position) {
            int keyPages = mActivity.getExtraKeysPageCount();
            // The page is the activity's own key view, lent here: the same instance the
            // portable host takes when the place stands the keys on another edge, so a latched
            // modifier and the picked colours survive the move either way.
            ExtraKeysView extraKeysView = mActivity.lendExtraKeysPage(position);
            extraKeysView.setVertical(false);
            extraKeysView.setExtraKeysViewClient(mActivity.getTermuxTerminalExtraKeys(position));
            extraKeysView.setButtonTextAllCaps(mActivity.getProperties().shouldExtraKeysTextBeAllCaps());
            extraKeysView.setToolbarTextInputSwipeListener(() -> focusCommandRow(mActivity));
            extraKeysView.setPageIndicator(position, keyPages);
            mActivity.setExtraKeysView(extraKeysView, position);
            extraKeysView.reload(
                mActivity.getTermuxTerminalExtraKeys(position).getExtraKeysInfo(),
                mActivity.getTerminalToolbarDefaultHeight());
            collection.addView(extraKeysView);
            return extraKeysView;
        }

        @Override
        public void destroyItem(@NonNull ViewGroup collection, int position, @NonNull Object view) {
            // A key page may already have been lent to the portable host, in which case it is no
            // longer a child here and this is a no-op — which is exactly what is wanted: the view
            // belongs to the activity, not to the pager.
            collection.removeView((View) view);
        }
    }

    /** Binds the persistent second row without taking focus away from the terminal on startup. */
    public static void bindCommandRow(TermuxActivity activity, String savedTextInput) {
        final Button button = activity.findViewById(R.id.terminal_toolbar_text_input_button);
        button.setText("\u2398");
        button.setOnClickListener(v ->
            TermuxTerminalExtraKeys.pasteWhereTheKeyboardPastes(activity));
        button.setOnLongClickListener(v -> {
            activity.endTerminalToolbarExternalTextInput();
            activity.getTerminalView().requestFocus();
            return true;
        });

        final EditText editText = activity.findViewById(R.id.terminal_toolbar_text_input);
        editText.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                activity.beginTerminalToolbarExternalTextInput(editText);
            }
            return false;
        });
        editText.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus) {
                activity.beginTerminalToolbarExternalTextInput(editText);
            } else if (activity.hasWindowFocus()) {
                activity.endTerminalToolbarExternalTextInput();
            }
            // Focus lost while the window itself is unfocused is lifecycle churn (screen
            // off, keyguard) — ending external input there suppresses an IME that cannot
            // be hidden yet, stranding it on screen. onResume restores the state instead.
        });
        if (savedTextInput != null) {
            editText.setText(savedTextInput);
        }
        editText.setOnEditorActionListener((v, actionId, event) -> {
            TerminalSession session = activity.getCurrentSession();
            if (session != null) {
                if (session.isRunning()) {
                    String textToSend = editText.getText().toString();
                    if (textToSend.length() == 0)
                        textToSend = "\r";
                    session.write(textToSend);
                } else {
                    activity.getTermuxTerminalSessionClient().removeFinishedSession(session);
                }
                editText.setText("");
            }
            return true;
        });
    }

    private static void focusCommandRow(TermuxActivity activity) {
        EditText input = activity.findViewById(R.id.terminal_toolbar_text_input);
        if (input == null) return;
        input.requestFocus();
        activity.beginTerminalToolbarExternalTextInput(input);
    }
}
