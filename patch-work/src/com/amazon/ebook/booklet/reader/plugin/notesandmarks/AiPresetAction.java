package com.amazon.ebook.booklet.reader.plugin.notesandmarks;

import com.amazon.ebook.booklet.reader.sdk.ui.ReaderAction;
import com.amazon.ebook.booklet.reader.sdk.ui.uitest.AccessibilityData;
import com.amazon.kindle.reader.commons.ui.AbstractReaderAction;
import java.awt.event.ActionEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

final class AiPresetAction extends ReaderAction {
    private final AnnotationsSelectionDialogProvider provider;
    private final String modeName;
    private final int priority;

    AiPresetAction(AnnotationsSelectionDialogProvider provider, String modeName, int priority) {
        super(modeName);
        this.provider = provider;
        this.modeName = modeName;
        this.priority = priority;
        putValue("lab126TestID", "AI_" + modeName + "_Action");
        putValue("accessibility_property", new AccessibilityData("button", modeName));
    }

    public int getPriority() {
        return priority;
    }

    public int getType() {
        return 2;
    }

    public int compareTo(AbstractReaderAction other) {
        if (other == null) {
            return 1;
        }
        return getPriority() - other.getPriority();
    }

    public void actionPerformed(ActionEvent event) {
        AiAssistantController.open(provider, modeName);
    }

    static void syncActions(AnnotationsSelectionDialogProvider provider) {
        try {
            List actions = (List) getField(provider, "cUL");
            Object noteAction = getField(provider, "cUM");
            if (noteAction == null) {
                provider.update();
                actions = (List) getField(provider, "cUL");
                noteAction = getField(provider, "cUM");
            }
            if (actions == null) {
                return;
            }

            for (int i = actions.size() - 1; i >= 0; i--) {
                if (actions.get(i) instanceof AiPresetAction) {
                    actions.remove(i);
                }
            }

            if (noteAction != null && !actions.contains(noteAction)) {
                setPriority(noteAction, 25);
                actions.add(noteAction);
            }

            if (noteAction != null) {
                actions.add(new AiPresetAction(provider, "Words", 26));
                actions.add(new AiPresetAction(provider, "Recall", 27));
                actions.add(new AiPresetAction(provider, "Why", 28));
                actions.add(new AiPresetAction(provider, "Deep", 29));
            }
        } catch (Throwable t) {
            AiAssistantLog.write("AI preset action sync failed: " + t.toString());
        }
    }

    private static Object getField(Object target, String name) throws Exception {
        Class klass = target.getClass();
        while (klass != null) {
            try {
                Field field = klass.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException e) {
                klass = klass.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static void setPriority(Object action, int priority) {
        try {
            Method method = action.getClass().getMethod("setPriority", new Class[] { Integer.TYPE });
            method.invoke(action, new Object[] { new Integer(priority) });
        } catch (Throwable ignored) {
            // The action still works if the original class refuses priority changes.
        }
    }
}
