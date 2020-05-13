
package com.ichi2.anki.dialogs;

import android.content.res.Resources;
import android.os.Bundle;
import androidx.fragment.app.DialogFragment;
import kotlin.Unit;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.R;

    /**
     * This is a reusable convenience class which makes it easy to show a confirmation dialog as a DialogFragment.
     * Create a new instance, call setArgs(...), setConfirm(), and setCancel() then show it via the fragment manager as usual.
     */
    public class ConfirmationDialog extends DialogFragment {
        // TODO: Replace these with lambdas
        private Runnable mConfirm = new Runnable() {
            @Override
            public void run() { // Do nothing by default
            }
        };
        private Runnable mCancel = new Runnable() {
            @Override
            public void run() { // Do nothing by default
            }
        };

        public void setArgs(String message) {
            setArgs("" , message);
        }

        public void setArgs(String title, String message) {
            Bundle args = new Bundle();
            args.putString("message", message);
            args.putString("title", title);
            setArguments(args);
        }

        public void setConfirm(Runnable confirm) {
            mConfirm = confirm;
        }

        public void setCancel(Runnable cancel) {
            mCancel = cancel;
        }

        @Override
        public MaterialDialog onCreateDialog(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            Resources res = getActivity().getResources();
            String title = getArguments().getString("title");
            MaterialDialog d = new MaterialDialog(AnkiDroidApp.getInstance().getApplicationContext(), MaterialDialog.getDEFAULT_BEHAVIOR())
                    .title(null, "".equals(title) ? res.getString(R.string.app_name) : title)
                    .message(null, getArguments().getString("message"), null)
                    .positiveButton(R.string.dialog_ok, null, (dialog) -> {
                        mConfirm.run();
                        return Unit.INSTANCE;
                    })
                    .negativeButton(R.string.dialog_cancel, null, (dialog) -> {
                mCancel.run();
                return Unit.INSTANCE;
            });
            d.show();
            return d;
        }
    }
