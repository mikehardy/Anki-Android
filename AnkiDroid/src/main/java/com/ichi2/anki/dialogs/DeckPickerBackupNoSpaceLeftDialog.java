package com.ichi2.anki.dialogs;

import android.content.res.Resources;
import android.os.Bundle;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.BackupManager;
import com.ichi2.anki.CollectionHelper;
import com.ichi2.anki.DeckPicker;
import com.ichi2.anki.R;
import com.ichi2.anki.analytics.AnalyticsDialogFragment;

import kotlin.Unit;

public class DeckPickerBackupNoSpaceLeftDialog extends AnalyticsDialogFragment {
    public static DeckPickerBackupNoSpaceLeftDialog newInstance() {
        DeckPickerBackupNoSpaceLeftDialog f = new DeckPickerBackupNoSpaceLeftDialog();
        return f;        
    }
    
    @Override
    public MaterialDialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Resources res = getResources();
        long space = BackupManager.getFreeDiscSpace(CollectionHelper.getCollectionPath(getActivity()));
        MaterialDialog dialog = new MaterialDialog(AnkiDroidApp.getInstance().getApplicationContext(), MaterialDialog.getDEFAULT_BEHAVIOR())
                .title(R.string.sd_card_almost_full_title, null)
                .message(null, res.getString(R.string.sd_space_warning, space/1024/1024), null)
                .positiveButton(R.string.dialog_ok, null, (innerDialog) -> {
                    ((DeckPicker) getActivity()).finishWithoutAnimation();
                    return Unit.INSTANCE;
                })
                .cancelable(true);
        dialog.setOnCancelListener(innerDialog -> ((DeckPicker) getActivity()).finishWithoutAnimation());
        dialog.show();
        return dialog;
    }
}