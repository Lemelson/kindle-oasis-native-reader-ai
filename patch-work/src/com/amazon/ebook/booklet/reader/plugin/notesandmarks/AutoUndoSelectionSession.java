package com.amazon.ebook.booklet.reader.plugin.notesandmarks;

import com.amazon.ebook.booklet.reader.sdk.ui.ReaderAction;
import com.amazon.ebook.booklet.reader.sdk.ui.SelectionDialog;
import com.amazon.ebook.booklet.reader.sdk.ui.SelectionDialogCard;
import java.awt.event.ActionEvent;
import java.lang.reflect.Method;
import java.util.List;

public final class AutoUndoSelectionSession {
    private static SelectionDialogCard activeCard;

    private AutoUndoSelectionSession() { }

    public static synchronized void arm(SelectionDialogCard card) {
        activeCard = card;
        if (card != null) {
            AiAssistantLog.write("auto undo armed card=" + card.anN());
        }
    }

    public static void handleBookTap(SelectionDialog dialog, boolean clearSelection) {
        if (dialog == null) {
            return;
        }

        SelectionDialogCard card = takeVisibleCard();
        if (card == null) {
            dialog.bQ(clearSelection);
            return;
        }

        AiAssistantLog.write("auto undo matched outside book tap card=" + card.anN());
        ReaderAction undo = findUndoAction(dialog);
        if (undo != null) {
            try {
                AiAssistantLog.write("auto undo invoking native Undo");
                undo.actionPerformed(new ActionEvent(
                        dialog, ActionEvent.ACTION_PERFORMED, "Undo"));
                return;
            } catch (Throwable t) {
                AiAssistantLog.write("auto undo native Undo failed: " + t.toString());
            }
        }

        AiAssistantLog.write("auto undo fallback bQ(true)");
        dialog.bQ(true);
    }

    private static synchronized SelectionDialogCard takeVisibleCard() {
        SelectionDialogCard card = activeCard;
        activeCard = null;
        if (card != null && card.isShowing()) {
            return card;
        }
        return null;
    }

    private static ReaderAction findUndoAction(SelectionDialog dialog) {
        try {
            Method actionsMethod = dialog.getClass().getMethod("VC", new Class[0]);
            actionsMethod.setAccessible(true);
            Object value = actionsMethod.invoke(dialog, new Object[0]);
            if (!(value instanceof List)) {
                return null;
            }

            List actions = (List) value;
            for (int i = 0; i < actions.size(); i++) {
                Object action = actions.get(i);
                if (action instanceof ReaderAction
                        && action.getClass().getName().endsWith("$DeleteHighlightAction")
                        && ((ReaderAction) action).isEnabled()) {
                    return (ReaderAction) action;
                }
            }
        } catch (Throwable t) {
            AiAssistantLog.write("auto undo action lookup failed: " + t.toString());
        }
        return null;
    }
}
