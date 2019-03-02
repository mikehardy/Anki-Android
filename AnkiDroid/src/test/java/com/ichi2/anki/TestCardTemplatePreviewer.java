package com.ichi2.anki;

public class TestCardTemplatePreviewer extends CardTemplatePreviewer {
    public boolean getShowingAnswer() { return mShowingAnswer; }
    public void disableDoubleClickPrevention() { mLastClickTime = (AbstractFlashcardViewer.DOUBLE_TAP_IGNORE_THRESHOLD * -2); }
}
