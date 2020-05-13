
package com.ichi2.anki.dialogs;

import android.app.Dialog;
import android.content.res.Resources;
import android.os.Bundle;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.DeckPicker;
import com.ichi2.anki.R;
import com.ichi2.anki.analytics.AnalyticsDialogFragment;

import kotlin.Unit;

public class DeckPickerConfirmDeleteDeckDialog extends AnalyticsDialogFragment {
    public static DeckPickerConfirmDeleteDeckDialog newInstance(String dialogMessage) {
        DeckPickerConfirmDeleteDeckDialog f = new DeckPickerConfirmDeleteDeckDialog();
        Bundle args = new Bundle();
        args.putString("dialogMessage", dialogMessage);
        f.setArguments(args);
        return f;
    }


    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Resources res = getResources();
        return new MaterialDialog(getActivity(), MaterialDialog.getDEFAULT_BEHAVIOR())
                .title(R.string.delete_deck_title, null)
                .message(null, getArguments().getString("dialogMessage"), null)
                .icon(R.attr.dialogErrorIcon, null)
                .cancelable(true)
                .positiveButton(R.string.dialog_positive_delete, null, (dialog) -> {
                    ((DeckPicker) getActivity()).deleteContextMenuDeck();
                    ((DeckPicker) getActivity()).dismissAllDialogFragments();
                    return Unit.INSTANCE;
                })
                .negativeButton(R.string.dialog_cancel, null, (dialog) -> {
                    ((DeckPicker) getActivity()).dismissAllDialogFragments();
                    return Unit.INSTANCE;
                });
    }
}
