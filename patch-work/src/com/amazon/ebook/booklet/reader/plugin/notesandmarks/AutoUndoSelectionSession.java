package com.amazon.ebook.booklet.reader.plugin.notesandmarks;

import com.amazon.ebook.booklet.reader.sdk.ui.ReaderAction;
import com.amazon.ebook.booklet.reader.sdk.ui.SelectionDialog;
import com.amazon.ebook.booklet.reader.sdk.ui.SelectionDialogCard;
import java.awt.event.ActionEvent;
import java.lang.reflect.Method;
import java.util.List;

public final class AutoUndoSelectionSession {
    private static Session activeSession;

    private AutoUndoSelectionSession() { }

    public static synchronized void arm(
            SelectionDialog dialog, SelectionDialogCard card) {
        activeSession = dialog == null || card == null
                ? null : new Session(dialog, card);
        if (activeSession != null) {
            AiAssistantLog.write("auto undo armed card=" + card.anN());
        }
    }

    public static void handleBookTap(SelectionDialog dialog, boolean clearSelection) {
        if (dialog == null) {
            return;
        }

        Session session = takeSession();
        if (session == null
                || session.dialog != dialog
                || !isCurrentSmartCard(dialog, session.card)) {
            dialog.bQ(clearSelection);
            return;
        }

        AiAssistantLog.write(
                "auto undo matched outside book tap card=" + session.card.anN());
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

    private static synchronized Session takeSession() {
        Session session = activeSession;
        activeSession = null;
        return session;
    }

    private static boolean isCurrentSmartCard(
            SelectionDialog dialog, SelectionDialogCard expectedCard) {
        try {
            if (!dialog.VN()) {
                return false;
            }
            Object smartCardDialog = invokeNoArgs(dialog, "VP");
            Object currentCard = invokeNoArgs(smartCardDialog, "YO");
            return currentCard == expectedCard;
        } catch (Throwable t) {
            AiAssistantLog.write(
                    "auto undo current card lookup failed: " + t.toString());
            return false;
        }
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

    private static Object invokeNoArgs(Object target, String name) throws Exception {
        if (target == null) {
            throw new NullPointerException(name + " target");
        }
        Class current = target.getClass();
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(name, new Class[0]);
                method.setAccessible(true);
                return method.invoke(target, new Object[0]);
            } catch (NoSuchMethodException e) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchMethodException(name);
    }

    private static final class Session {
        final SelectionDialog dialog;
        final SelectionDialogCard card;

        Session(SelectionDialog dialog, SelectionDialogCard card) {
            this.dialog = dialog;
            this.card = card;
        }
    }
}
