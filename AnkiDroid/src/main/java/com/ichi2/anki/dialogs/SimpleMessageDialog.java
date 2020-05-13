
package com.ichi2.anki.dialogs;

import android.os.Bundle;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.R;

import kotlin.Unit;

public class SimpleMessageDialog extends AsyncDialogFragment {

    public interface SimpleMessageDialogListener {
        void dismissSimpleMessageDialog(boolean reload);
    }


    public static SimpleMessageDialog newInstance(String message, boolean reload) {
        return newInstance("" , message, reload);
    }


    public static SimpleMessageDialog newInstance(String title, String message, boolean reload) {
        SimpleMessageDialog f = new SimpleMessageDialog();
        Bundle args = new Bundle();
        args.putString("title", title);
        args.putString("message", message);
        args.putBoolean("reload", reload);
        f.setArguments(args);
        return f;
    }


    @Override
    public MaterialDialog onCreateDialog(Bundle savedInstanceState) {
        // FIXME this should be super.onCreateDialog(Bundle), no?
        super.onCreate(savedInstanceState);
        MaterialDialog d = new MaterialDialog(getActivity(), MaterialDialog.getDEFAULT_BEHAVIOR())
                .title(null, getNotificationTitle())
                .message(null, getNotificationMessage(), null)
                .positiveButton(R.string.dialog_ok, null, (dialog) -> {
                    ((SimpleMessageDialogListener) getActivity())
                            .dismissSimpleMessageDialog(getArguments().getBoolean("reload"));
                    return Unit.INSTANCE;
                });
        d.show();
        return d;
    }


    public String getNotificationTitle() {
        String title = getArguments().getString("title");
        if (!"".equals(title)) {
            return title;
        } else {
            return AnkiDroidApp.getAppResources().getString(R.string.app_name);
        }
    }


    public String getNotificationMessage() {
        return getArguments().getString("message");
    }
}
