package com.amazon.ebook.booklet.reader.plugin.notesandmarks;

import com.amazon.agui.swing.KDialog;
import com.amazon.ebook.booklet.reader.sdk.content.Book;
import com.amazon.ebook.booklet.reader.sdk.ui.ReaderAction;
import com.amazon.ebook.booklet.reader.sdk.ui.SelectionDialog;
import com.amazon.ebook.booklet.reader.sdk.ui.SelectionDialogCard;
import com.amazon.ebook.booklet.reader.sdk.ui.SmartLookupMetricsCollector;
import com.amazon.ebook.booklet.reader.sdk.ui.element.Element;
import com.amazon.ebook.booklet.reader.sdk.ui.selection.SelectionMetricsPublisher;
import com.amazon.kindle.reader.commons.ui.AbstractReaderAction;
import java.awt.event.ActionEvent;
import java.util.Collections;
import java.util.List;

public final class AutoUndoSelectionSessionTest {
    public static void main(String[] args) {
        testUnarmedBookTapKeepsNativeCloseBehavior();
        testArmedVisibleCardPerformsNativeUndo();
        testHiddenCardDoesNotAffectLaterSelection();
        testMissingUndoStillClearsSelection();
        testArmedSessionIsConsumedBeforeUndoReentry();
        System.out.println("AutoUndoSelectionSessionTest OK");
    }

    private static void testUnarmedBookTapKeepsNativeCloseBehavior() {
        FakeSelectionDialog dialog = new FakeSelectionDialog();

        AutoUndoSelectionSession.handleBookTap(dialog, false);

        assertEquals(1, dialog.closeCount, "native close count");
        assertFalse(dialog.lastClearSelection, "native close flag");
    }

    private static void testArmedVisibleCardPerformsNativeUndo() {
        FakeSelectionDialog dialog = new FakeSelectionDialog();
        DeleteHighlightAction undo = new DeleteHighlightAction(dialog);
        dialog.actions = Collections.singletonList(undo);
        FakeCard card = new FakeCard(true);
        assertTrue(card.isShowing(), "test card visibility");
        assertTrue(undo.isEnabled(), "test Undo enabled");
        assertTrue(undo.getClass().getName().endsWith("$DeleteHighlightAction"),
                "test Undo class name");
        AutoUndoSelectionSession.arm(card);

        AutoUndoSelectionSession.handleBookTap(dialog, false);

        assertEquals(1, undo.performCount, "Undo action count");
        assertEquals(1, dialog.closeCount, "Undo close count");
        assertTrue(dialog.lastClearSelection, "Undo clear flag");
    }

    private static void testHiddenCardDoesNotAffectLaterSelection() {
        FakeSelectionDialog dialog = new FakeSelectionDialog();
        DeleteHighlightAction undo = new DeleteHighlightAction(dialog);
        dialog.actions = Collections.singletonList(undo);
        AutoUndoSelectionSession.arm(new FakeCard(false));

        AutoUndoSelectionSession.handleBookTap(dialog, false);

        assertEquals(0, undo.performCount, "hidden-card Undo count");
        assertEquals(1, dialog.closeCount, "hidden-card native close count");
        assertFalse(dialog.lastClearSelection, "hidden-card native close flag");
    }

    private static void testMissingUndoStillClearsSelection() {
        FakeSelectionDialog dialog = new FakeSelectionDialog();
        AutoUndoSelectionSession.arm(new FakeCard(true));

        AutoUndoSelectionSession.handleBookTap(dialog, false);

        assertEquals(1, dialog.closeCount, "missing-Undo close count");
        assertTrue(dialog.lastClearSelection, "missing-Undo clear flag");
    }

    private static void testArmedSessionIsConsumedBeforeUndoReentry() {
        FakeSelectionDialog dialog = new FakeSelectionDialog();
        DeleteHighlightAction undo = new DeleteHighlightAction(dialog);
        dialog.actions = Collections.singletonList(undo);
        AutoUndoSelectionSession.arm(new FakeCard(true));

        AutoUndoSelectionSession.handleBookTap(dialog, false);
        AutoUndoSelectionSession.handleBookTap(dialog, false);

        assertEquals(1, undo.performCount, "one-shot Undo count");
        assertEquals(2, dialog.closeCount, "one-shot total close count");
        assertFalse(dialog.lastClearSelection, "second tap native close flag");
    }

    public static final class FakeSelectionDialog implements SelectionDialog {
        int closeCount;
        boolean lastClearSelection;
        List actions = Collections.EMPTY_LIST;

        public void bQ(boolean clearSelection) {
            closeCount++;
            lastClearSelection = clearSelection;
        }

        public List VC() {
            return actions;
        }

        public void VD() { }
        public void bO(boolean visible) { }
        public boolean VN() { return false; }
        public boolean VE() { return false; }
        public SmartLookupMetricsCollector VO() { return null; }
        public SelectionMetricsPublisher VQ() { return null; }
        public KDialog VP() { return null; }
        public boolean a(Element element, boolean value) { return false; }
        public boolean a(Element element, boolean first, boolean second) { return false; }
        public boolean isShowing() { return true; }
        public void a(Book book, boolean value) { }
    }

    private static final class FakeCard extends SelectionDialogCard {
        private final boolean showing;

        FakeCard(boolean showing) {
            this.showing = showing;
        }

        public boolean isShowing() { return showing; }
        public String anL() { return "Fake"; }
        public boolean anM() { return true; }
        public int getPriority() { return 1; }
    }

    private static final class DeleteHighlightAction extends ReaderAction {
        private final FakeSelectionDialog dialog;
        int performCount;

        DeleteHighlightAction(FakeSelectionDialog dialog) {
            super("Undo");
            this.dialog = dialog;
            setEnabled(true);
        }

        public void actionPerformed(ActionEvent event) {
            performCount++;
            dialog.bQ(true);
        }

        public int getType() { return 2; }
        public int getPriority() { return 1; }
        public int compareTo(AbstractReaderAction other) { return 0; }
    }

    private static void assertEquals(int expected, int actual, String label) {
        if (expected != actual) {
            throw new AssertionError(label + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertFalse(boolean value, String label) {
        if (value) {
            throw new AssertionError(label + ": expected false");
        }
    }

    private static void assertTrue(boolean value, String label) {
        if (!value) {
            throw new AssertionError(label + ": expected true");
        }
    }
}
