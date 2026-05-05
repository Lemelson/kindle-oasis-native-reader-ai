package com.amazon.ebook.booklet.reader.plugin.share;

import com.amazon.ebook.booklet.reader.sdk.ReaderSDK;
import com.amazon.ebook.booklet.reader.sdk.Registry;
import com.amazon.ebook.booklet.reader.sdk.ui.ReaderAction;
import com.amazon.ebook.booklet.reader.sdk.ui.SelectionDialog;
import com.amazon.ebook.booklet.reader.sdk.ui.SelectionDialogCard;
import com.amazon.ebook.booklet.reader.sdk.ui.SelectionDialogCardProvider;
import com.amazon.ebook.booklet.reader.sdk.ui.uitest.AccessibilityData;
import com.amazon.kindle.reader.commons.ui.AbstractReaderAction;
import java.awt.event.ActionEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.ResourceBundle;

public class ShareSelectedTextAction extends ReaderAction {
    protected final ReaderSDK aam;
    protected final int priority;

    public ShareSelectedTextAction(ReaderSDK sdk, String ignoredLabel, int priority, boolean showIcon) {
        super("Translate", null);
        this.aam = sdk;
        this.priority = priority;
        putValue("lab126TestID", "Translator_Action");
        putValue("accessibility_property", new AccessibilityData("button", "Translate"));
    }

    public boolean isEnabled() {
        try {
            return findTranslatorCard() != null;
        } catch (Throwable t) {
            return false;
        }
    }

    public void actionPerformed(ActionEvent event) {
        SelectionDialogCard card = findTranslatorCard();
        if (card == null) {
            return;
        }
        forceRussianTargetLanguage();
        SelectionDialog dialog = aam.Jl().HF();
        try {
            Object smartCardDialog = getField(dialog, "bBS");
            Method compactMode = getMethod(dialog.getClass(), "bN", new Class[] { Boolean.TYPE });
            Boolean compact = (Boolean) compactMode.invoke(dialog, new Object[] { Boolean.FALSE });
            Method openCard = smartCardDialog.getClass().getMethod(
                    "a", new Class[] { SelectionDialogCard.class, Boolean.TYPE });
            openCard.invoke(smartCardDialog, new Object[] { card, compact });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private SelectionDialogCard findTranslatorCard() {
        Registry registry = aam.Jk();
        List providers = registry.MM();
        for (int i = 0; i < providers.size(); i++) {
            Object provider = providers.get(i);
            if (provider instanceof SelectionDialogCardProvider) {
                SelectionDialogCard card = ((SelectionDialogCardProvider) provider).anG();
                if (card != null && "TRANSLATOR_CARD".equals(card.anN())) {
                    return card;
                }
            }
        }
        return null;
    }

    private void forceRussianTargetLanguage() {
        try {
            Class storageClass = Class.forName(
                    "com.amazon.ebook.booklet.reader.plugin.translator.ui.TranslatorStorage");
            Object storage = storageClass.getConstructor(new Class[] { ReaderSDK.class })
                    .newInstance(new Object[] { aam });
            Method saveTargetLanguage = storageClass.getMethod("mA", new Class[] { String.class });
            saveTargetLanguage.invoke(storage, new Object[] { "ru" });
        } catch (Throwable ignored) {
            // Keep the translate button usable even if the translator storage API changes.
        }
    }

    public int getType() { return 2; }
    public int getPriority() { return priority; }
    public int compareTo(AbstractReaderAction other) {
        if (other == null) {
            return 1;
        }
        return getPriority() - other.getPriority();
    }
    public boolean Xa() { return false; }
    protected void a(boolean showIcon, ResourceBundle bundle) { }

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

    private static Method getMethod(Class klass, String name, Class[] parameterTypes) throws Exception {
        Class current = klass;
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(name, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException e) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchMethodException(name);
    }
}
