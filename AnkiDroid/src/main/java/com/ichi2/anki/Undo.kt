/***************************************************************************************
 * Copyright (c) 2022 Ankitects Pty Ltd <https://apps.ankiweb.net>                       *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.anki

import androidx.fragment.app.FragmentActivity
import anki.collection.OpChangesAfterUndo
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.libanki.redo
import com.ichi2.anki.libanki.undo
import com.ichi2.anki.observability.undoableOp
import com.ichi2.anki.snackbar.showSnackbar

/** If there's an action pending in the review queue, undo it and show a snackbar */
suspend fun FragmentActivity.undoAndShowSnackbar(duration: Int = Snackbar.LENGTH_SHORT) {
    withProgress {
        val changes =
            undoableOp {
                if (!undoAvailable()) {
                    OpChangesAfterUndo.getDefaultInstance()
                } else {
                    undo()
                }
            }
        val message =
            if (changes.operation.isEmpty()) {
                TR.actionsNothingToUndo()
            } else {
                TR.undoActionUndone(changes.operation)
            }
        showSnackbar(message, duration)
    }
}

suspend fun FragmentActivity.redoAndShowSnackbar(duration: Int = Snackbar.LENGTH_SHORT) {
    withProgress {
        val changes =
            undoableOp {
                if (redoAvailable()) {
                    redo()
                } else {
                    OpChangesAfterUndo.getDefaultInstance()
                }
            }
        val message =
            if (changes.operation.isEmpty()) {
                TR.actionsNothingToRedo()
            } else {
                TR.undoActionRedone(changes.operation)
            }
        showSnackbar(message, duration)
    }
}
