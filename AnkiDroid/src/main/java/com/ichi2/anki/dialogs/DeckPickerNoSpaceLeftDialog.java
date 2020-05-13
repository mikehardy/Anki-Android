package com.ichi2.anki.dialogs;

import android.os.Bundle;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.DeckPicker;
import com.ichi2.anki.R;
import com.ichi2.anki.analytics.AnalyticsDialogFragment;

import androidx.annotation.NonNull;
import kotlin.Unit;

public class DeckPickerNoSpaceLeftDialog extends AnalyticsDialogFragment {
    public static DeckPickerNoSpaceLeftDialog newInstance() {
        return new DeckPickerNoSpaceLeftDialog();
    }
    
    @Override
    public @NonNull MaterialDialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MaterialDialog d = new MaterialDialog(AnkiDroidApp.getInstance().getApplicationContext(), MaterialDialog.getDEFAULT_BEHAVIOR())
                .title(R.string.sd_card_full_title, null)
                .message(R.string.backup_deck_no_space_left, null, null)
                .cancelable(true)
                .positiveButton(R.string.dialog_ok, null, (dialog) -> {
                    ((DeckPicker) getActivity()).startLoadingCollection();
                    return Unit.INSTANCE;
                });
        d.setOnCancelListener(dialog -> ((DeckPicker) getActivity()).startLoadingCollection());
        d.show();
        return d;
    }
}