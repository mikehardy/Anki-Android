/****************************************************************************************
 * Copyright (c) 2018 Mike Hardy <mike@mikehardy.net>                                   *
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

package com.ichi2.anki;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;

import com.afollestad.materialdialogs.DialogAction;

import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowIntent;
import org.robolectric.shadows.ShadowToast;

import java.io.Serializable;
import java.util.ArrayList;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import timber.log.Timber;

import static com.ichi2.anki.CardTemplateEditor.ChangeType.ADD;
import static com.ichi2.anki.CardTemplateEditor.ChangeType.DELETE;


@RunWith(AndroidJUnit4.class)
@Config(shadows = { ShadowViewPager.class })
public class CardTemplateEditorTest extends RobolectricTest {

    @Test
    public void testTempModelStorage() throws Exception {

        // Start off with clean state in the cache dir
        CardTemplateEditor.clearTempModelFiles(getTargetContext());

        // Make sure save / retrieve works
        String tempModelPath = CardTemplateEditor.saveTempModel(getTargetContext(), new JSONObject("{foo: bar}"));
        Assert.assertNotNull("Saving temp model unsuccessful", tempModelPath);
        JSONObject tempModel = CardTemplateEditor.getTempModel(tempModelPath);
        Assert.assertNotNull("Temp model not read successfully", tempModel);
        Assert.assertEquals(new JSONObject("{foo: bar}").toString(), tempModel.toString());

        // Make sure clearing works
        Assert.assertEquals(1, CardTemplateEditor.clearTempModelFiles(getTargetContext()));
        Timber.i("The following logged NoSuchFileException is an expected part of verifying a file delete.");
        Assert.assertNull("tempModel not correctly deleted", CardTemplateEditor.getTempModel(tempModelPath));
    }

    @Test
    public void testEditTemplateContents() throws Exception {

        String modelName = "Basic";

        // Start the CardTemplateEditor with a specific model, and make sure the model starts unchanged
        JSONObject collectionBasicModelOriginal = getCurrentDatabaseModelCopy(modelName);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.putExtra("modelId", collectionBasicModelOriginal.getLong("id"));
        ActivityController templateEditorController = Robolectric.buildActivity(CardTemplateEditor.class, intent).create().start().resume().visible();
        CardTemplateEditor testEditor = (CardTemplateEditor)templateEditorController.get();
        ShadowActivity shadowTestEditor = Shadows.shadowOf(testEditor);
        Assert.assertFalse("Model should not have changed yet", testEditor.modelHasChanged());

        // Change the model and make sure it registers as changed, but the database is unchanged
        EditText templateFront = testEditor.findViewById(R.id.front_edit);
        String TEST_MODEL_QFMT_EDIT = "!@#$%^&*TEST*&^%$#@!";
        templateFront.getText().append(TEST_MODEL_QFMT_EDIT);
        Assert.assertTrue("Model did not change after edit?", testEditor.modelHasChanged());
        Assert.assertEquals("Change already in database?", collectionBasicModelOriginal.toString().trim(), getCurrentDatabaseModelCopy(modelName).toString().trim());

        // Kill and restart the Activity, make sure model edit is preserved
        Bundle outBundle = new Bundle();
        templateEditorController.saveInstanceState(outBundle);
        templateEditorController.pause().stop().destroy();
        templateEditorController = Robolectric.buildActivity(CardTemplateEditor.class).create(outBundle).start().resume().visible();
        testEditor = (CardTemplateEditor)templateEditorController.get();
        shadowTestEditor = Shadows.shadowOf(testEditor);
        Assert.assertTrue("model change not preserved across activity lifecycle?", testEditor.modelHasChanged());
        Assert.assertEquals("Change already in database?", collectionBasicModelOriginal.toString().trim(), getCurrentDatabaseModelCopy(modelName).toString().trim());

        // Make sure we get a confirmation dialog if we hit the back button
        shadowTestEditor.clickMenuItem(android.R.id.home);
        Assert.assertEquals("Wrong dialog shown?", getDialogText(), getResourceString(R.string.discard_unsaved_changes));
        clickDialogButton(DialogAction.NEGATIVE);
        Assert.assertTrue("model change not preserved despite canceling back button?", testEditor.modelHasChanged());

        // Make sure we things are cleared out after a cancel
        shadowTestEditor.clickMenuItem(android.R.id.home);
        Assert.assertEquals("Wrong dialog shown?", getDialogText(), getResourceString(R.string.discard_unsaved_changes));
        clickDialogButton(DialogAction.POSITIVE);
        Assert.assertFalse("model change not cleared despite discarding changes?", testEditor.modelHasChanged());

        // Get going for content edit assertions again...
        templateEditorController = Robolectric.buildActivity(CardTemplateEditor.class, intent).create().start().resume().visible();
        testEditor = (CardTemplateEditor)templateEditorController.get();
        shadowTestEditor = Shadows.shadowOf(testEditor);
        templateFront = testEditor.findViewById(R.id.front_edit);
        templateFront.getText().append(TEST_MODEL_QFMT_EDIT);
        Assert.assertTrue("Model did not change after edit?", testEditor.modelHasChanged());

        // Make sure we pass the edit to the Previewer
        shadowTestEditor.clickMenuItem(R.id.action_preview);
        Intent startedIntent = shadowTestEditor.getNextStartedActivity();
        ShadowIntent shadowIntent = Shadows.shadowOf(startedIntent);
        Assert.assertEquals("Previewer not started?", CardTemplatePreviewer.class.getName(), shadowIntent.getIntentClass().getName());
        Assert.assertNotNull("intent did not have model JSON filename?", startedIntent.getStringExtra(CardTemplateEditor.INTENT_MODEL_FILENAME));
        Assert.assertNotEquals("Model sent to Previewer is unchanged?", testEditor.getEditedModel(), CardTemplateEditor.getTempModel(startedIntent.getStringExtra(CardTemplateEditor.INTENT_MODEL_FILENAME)));
        Assert.assertEquals("Change already in database?", collectionBasicModelOriginal.toString().trim(), getCurrentDatabaseModelCopy(modelName).toString().trim());
        shadowTestEditor.receiveResult(startedIntent, Activity.RESULT_OK, new Intent());

        // Save the template then fetch it from the collection to see if it was saved correctly
        JSONObject testEditorModelEdited = testEditor.getEditedModel();
        shadowTestEditor.clickMenuItem(R.id.action_confirm);
        JSONObject collectionBasicModelCopyEdited = getCurrentDatabaseModelCopy(modelName);
        Assert.assertNotEquals("model is unchanged?", collectionBasicModelOriginal, collectionBasicModelCopyEdited);
        Assert.assertEquals("model did not save?", testEditorModelEdited.toString().trim(), collectionBasicModelCopyEdited.toString().trim());
        Assert.assertTrue("model does not have our change?", collectionBasicModelCopyEdited.toString().contains(TEST_MODEL_QFMT_EDIT));
    }

    @Test
    public void testAddDeleteTracking() throws Exception {

        // Assume you start with a 2 template model (like "Basic (and reversed)")
        // Add a 3rd new template, remove the 2nd, remove the 1st, add a new now-2nd, remove 1st again
        // ...and it should reduce to just removing the original 1st/2nd and adding the final as first

        // We'll create an actual Activity here to use later for lifecycle persistence checks. The model we use is unimportant for the test.
        String modelName = "Basic (and reversed card)";
        JSONObject collectionBasicModelOriginal = getCurrentDatabaseModelCopy(modelName);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.putExtra("modelId", collectionBasicModelOriginal.getLong("id"));
        ActivityController templateEditorController = Robolectric.buildActivity(CardTemplateEditor.class, intent).create().start().resume().visible();
        CardTemplateEditor testEditor = (CardTemplateEditor) templateEditorController.get();


        testEditor.addTemplateChange(ADD, 3);
        Object[][] expected1 = {{3, ADD}};
        assertTemplateChangesEqual(expected1, testEditor.getTemplateChanges());
        testEditor.addTemplateChange(DELETE, 2);
        testEditor.addTemplateChange(DELETE, 1);
        Object[][] expected2 = {{3, ADD}, {2, DELETE}, {1, DELETE}};
        assertTemplateChangesEqual(expected2, testEditor.getTemplateChanges());
        testEditor.addTemplateChange(ADD, 2);
        Object[][] expected3 = {{3, ADD}, {2, DELETE}, {1, DELETE}, {2, ADD}};
        assertTemplateChangesEqual(expected3, testEditor.getTemplateChanges());

        // Make sure we can resurrect these changes across lifecycle
        Bundle outBundle = new Bundle();
        testEditor.onSaveInstanceState(outBundle);
        assertTemplateChangesEqual(expected3, outBundle.getSerializable("mTemplateChanges"));

        // This is the hard part. We will delete a template we added so everything shifts.
        // The template currently at ordinal 1 was added as template 3 at the start before it slid down on the deletes
        // So the first template add should be negated by this delete, and the second template add should slide down to 1
        testEditor.addTemplateChange(DELETE, 1);
        Object[][] expected4 = {{2, DELETE}, {1, DELETE}, {1, ADD}};
        assertTemplateChangesEqual(expected4, testEditor.getTemplateChanges());
        testEditor.addTemplateChange(ADD, 2);
        Object[][] expected5 = {{2, DELETE}, {1, DELETE}, {1, ADD}, {2, ADD}};
        assertTemplateChangesEqual(expected5, testEditor.getTemplateChanges());
        testEditor.addTemplateChange(DELETE, 2);
        Object[][] expected6 = {{2, DELETE}, {1, DELETE}, {1, ADD}};
        assertTemplateChangesEqual(expected6, testEditor.getTemplateChanges());
    }


    private void assertTemplateChangesEqual(Object[][] expected, Serializable actual) {
        if (!(actual instanceof ArrayList)) {
            Assert.fail("actual array null or not the correct type");
        }
        Assert.assertEquals("arrays didn't have the same length?", expected.length, ((ArrayList) actual).size());
        for (int i = 0; i < expected.length; i++) {
            if (!(((ArrayList) actual).get(i) instanceof Object[])) {
                Assert.fail("actual array does not contain Object[] entries");
            }
            Object[] actualChange = (Object[]) ((ArrayList) actual).get(i);
            Assert.assertEquals("ordinal at " + i + " not correct?", expected[i][0], actualChange[0]);
            Assert.assertEquals("changeType at " + i + " not correct?", expected[i][1], actualChange[1]);
        }
    }


    @Test
    public void testDeleteTemplate() throws Exception {

        String modelName = "Basic (and reversed card)";

        // Start the CardTemplateEditor with a specific model, and make sure the model starts unchanged
        JSONObject collectionBasicModelOriginal = getCurrentDatabaseModelCopy(modelName);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.putExtra("modelId", collectionBasicModelOriginal.getLong("id"));
        ActivityController templateEditorController = Robolectric.buildActivity(NonPagingCardTemplateEditor.class, intent).create().start().resume().visible();
        CardTemplateEditor testEditor = (CardTemplateEditor)templateEditorController.get();
        Assert.assertFalse("Model should not have changed yet", testEditor.modelHasChanged());

        // Try to delete the template - click delete, click confirm for card delete, click confirm again for full sync
        ShadowActivity shadowTestEditor = Shadows.shadowOf(testEditor);
        shadowTestEditor.clickMenuItem(R.id.action_delete);
        Assert.assertEquals("Wrong dialog shown?", "Delete the “Card 1” card type, and its 0 cards?", getDialogText());
        clickDialogButton(DialogAction.POSITIVE);
        Assert.assertTrue("Model should have changed", testEditor.modelHasChanged());

        // Try to delete the template again, but there's only one so we should toast
        shadowTestEditor.clickMenuItem(R.id.action_delete);
        Assert.assertEquals("Did not show toast about deleting only card?",
                getResourceString(R.string.card_template_editor_cant_delete),
                ShadowToast.getTextOfLatestToast());
        Assert.assertEquals("Change already in database?", collectionBasicModelOriginal.toString().trim(), getCurrentDatabaseModelCopy(modelName).toString().trim());

        // Related to Robolectric ViewPager support, see below
        NonPagingCardTemplateEditor.pagerCount = 1;

        // Kill and restart the Activity, make sure model edit is preserved
        // The saveInstanceState test would be useful but we can't run it without Robolectric ViewPager support
//        Bundle outBundle = new Bundle();
//        templateEditorController.saveInstanceState(outBundle);
//        templateEditorController.pause().stop().destroy();
//        templateEditorController = Robolectric.buildActivity(CardTemplateEditor.class).create(outBundle).start().resume().visible();
//        testEditor = (CardTemplateEditor)templateEditorController.get();
//        Assert.assertTrue("model change not preserved across activity lifecycle?", testEditor.modelHasChanged());
//        Assert.assertEquals("Change already in database?", collectionBasicModelOriginal.toString().trim(), getCurrentDatabaseModelCopy(modelName).toString().trim());

        // Save the change to the database and make sure there's only one template after
        JSONObject testEditorModelEdited = testEditor.getEditedModel();
        shadowTestEditor.clickMenuItem(R.id.action_confirm);
        JSONObject collectionBasicModelCopyEdited = getCurrentDatabaseModelCopy(modelName);
        Assert.assertNotEquals("model is unchanged?", collectionBasicModelOriginal, collectionBasicModelCopyEdited);
        Assert.assertEquals("model did not save?", testEditorModelEdited.toString().trim(), collectionBasicModelCopyEdited.toString().trim());
    }

    @Test
    public void testTemplateAdd() throws Exception {

        // Make sure we test previewing a new card template - not working for real yet
        String modelName = "Basic";
        JSONObject collectionBasicModelOriginal = getCurrentDatabaseModelCopy(modelName);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.putExtra("modelId", collectionBasicModelOriginal.getLong("id"));
        NonPagingCardTemplateEditor.pagerCount = 1;
        ActivityController templateEditorController = Robolectric.buildActivity(NonPagingCardTemplateEditor.class, intent).create().start().resume().visible();
        CardTemplateEditor testEditor = (CardTemplateEditor)templateEditorController.get();

        // Try to add a template - click add, click confirm for card add, click confirm again for full sync
        ShadowActivity shadowTestEditor = Shadows.shadowOf(testEditor);
        shadowTestEditor.clickMenuItem(R.id.action_add);
        // TODO never existed in AnkiDroid but to match AnkiDesktop we should pop a dialog to confirm card create
        //Assert.assertEquals("Wrong dialog shown?", "This will create NN cards. Proceed?", getDialogText());
        //clickDialogButton(DialogAction.POSITIVE);
        Assert.assertTrue("Model should have changed", testEditor.modelHasChanged());

        // Make sure we pass the new template to the Previewer
        shadowTestEditor.clickMenuItem(R.id.action_preview);
        Intent startedIntent = shadowTestEditor.getNextStartedActivity();
        ShadowIntent shadowIntent = Shadows.shadowOf(startedIntent);
        Assert.assertEquals("Previewer not started?", CardTemplatePreviewer.class.getName(), shadowIntent.getIntentClass().getName());
        Assert.assertNotNull("intent did not have model JSON filename?", startedIntent.getStringExtra(CardTemplateEditor.INTENT_MODEL_FILENAME));
        Assert.assertEquals("intent did not have ordinal?", startedIntent.getIntExtra("index", -1), 0);
        Assert.assertNotEquals("Model sent to Previewer is unchanged?", testEditor.getEditedModel(), CardTemplateEditor.getTempModel(startedIntent.getStringExtra(CardTemplateEditor.INTENT_MODEL_FILENAME)));
        Assert.assertEquals("Change already in database?", collectionBasicModelOriginal.toString().trim(), getCurrentDatabaseModelCopy(modelName).toString().trim());

        // Save the change to the database and make sure there are two templates after
        JSONObject testEditorModelEdited = testEditor.getEditedModel();
        shadowTestEditor.clickMenuItem(R.id.action_confirm);
        JSONObject collectionBasicModelCopyEdited = getCurrentDatabaseModelCopy(modelName);
        Assert.assertNotEquals("model is unchanged?", collectionBasicModelOriginal, collectionBasicModelCopyEdited);
        Assert.assertEquals("model did not save?", testEditorModelEdited.toString().trim(), collectionBasicModelCopyEdited.toString().trim());

    }
}
