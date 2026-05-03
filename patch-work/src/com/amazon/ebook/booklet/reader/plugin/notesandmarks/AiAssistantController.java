package com.amazon.ebook.booklet.reader.plugin.notesandmarks;

import com.amazon.ebook.booklet.reader.sdk.BookNavigator;
import com.amazon.ebook.booklet.reader.sdk.ReaderSDK;
import com.amazon.ebook.booklet.reader.sdk.content.Book;
import com.amazon.ebook.booklet.reader.sdk.content.BookMetadata;
import com.amazon.ebook.booklet.reader.sdk.content.Position;
import com.amazon.ebook.booklet.reader.sdk.ui.SelectionDialog;
import com.amazon.ebook.booklet.reader.sdk.ui.SelectionDialogCard;
import com.amazon.ebook.booklet.reader.sdk.ui.element.Element;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

final class AiAssistantController {
    private AiAssistantController() { }

    static void open(AnnotationsSelectionDialogProvider provider) {
        open(getReaderSDK(provider));
    }

    static void open(final ReaderSDK sdk) {
        if (sdk == null) {
            return;
        }

        try {
            AiAssistantLog.write("ai action start");
            final SelectionData selection = SelectionData.capture(sdk);
            AiAssistantLog.write("selection captured length=" + selection.textLength());
            if (!selection.hasText()) {
                showMessage(sdk, "No selected text found.");
                return;
            }

            AiAssistantCard card = new AiAssistantCard(sdk, selection);
            openSmartCard(sdk, card);
            AiAssistantLog.write("ai card opened");
        } catch (Throwable t) {
            AiAssistantLog.write("ai action failed: " + t.toString());
            showMessage(sdk, "AI failed. Use KUAL rollback if reader acts oddly.");
        }
    }

    private static ReaderSDK getReaderSDK(AnnotationsSelectionDialogProvider provider) {
        try {
            java.lang.reflect.Field field =
                    AnnotationsSelectionDialogProvider.class.getDeclaredField("alz");
            field.setAccessible(true);
            return (ReaderSDK) field.get(provider);
        } catch (Throwable t) {
            AiAssistantLog.write("ReaderSDK lookup failed: " + t.toString());
            return null;
        }
    }

    private static void openSmartCard(ReaderSDK sdk, SelectionDialogCard card) throws Exception {
        SelectionDialog dialog = sdk.Jl().HF();
        Object smartCardDialog = getField(dialog, "bBS");
        Method compactMode = getMethod(dialog.getClass(), "bN", new Class[] { Boolean.TYPE });
        Boolean compact = (Boolean) compactMode.invoke(dialog, new Object[] { Boolean.FALSE });
        Method openCard = smartCardDialog.getClass().getMethod(
                "a", new Class[] { SelectionDialogCard.class, Boolean.TYPE });
        openCard.invoke(smartCardDialog, new Object[] { card, compact });
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

    private static void showMessage(ReaderSDK sdk, String message) {
        try {
            sdk.Jl().showMessage(message);
        } catch (Throwable t) {
            AiAssistantLog.write("showMessage failed: " + t.toString());
        }
    }

    static String presetPrompt(String promptName) {
        if ("Translate".equals(promptName)) {
            return "Translate the selected text into natural Russian. Preserve meaning, tone, and nuance. "
                    + "If there is an idiom, translate it naturally and add one short note.";
        }
        if ("Words".equals(promptName)) {
            return "For a Russian-speaking English learner, explain the key vocabulary, idioms, "
                    + "phrasal verbs, and difficult references in the selected text.";
        }
        if ("Grammar".equals(promptName)) {
            return "Explain why this English sentence or passage is written this way. Focus on clause "
                    + "structure, tense/aspect, grammar, idioms, and a simpler English paraphrase.";
        }
        if ("Short".equals(promptName)) {
            return "Give a very short Russian explanation of the selected text in 2-4 bullet points.";
        }
        return "Explain the selected text in simple Russian. Start with the core meaning, then mention "
                + "important nuance, references, difficult words, or ambiguity.";
    }

    static String buildUserPrompt(String instruction, SelectionData selection) {
        StringBuffer prompt = new StringBuffer();
        prompt.append("Task:\n");
        prompt.append(instruction);
        prompt.append("\n\nAnswer in Russian. Be concise but useful.\n\n");
        prompt.append("Book metadata:\n");
        appendLine(prompt, "Title", selection.title);
        appendLine(prompt, "Authors", selection.authors);
        appendLine(prompt, "Book language", selection.language);
        appendLine(prompt, "Start position", selection.startPosition);
        appendLine(prompt, "End position", selection.endPosition);
        prompt.append("\nSelected text:\n");
        prompt.append(selection.selectedText);
        if (selection.nearbyText != null && selection.nearbyText.length() > 0) {
            prompt.append("\n\nNearby text, if useful:\n");
            prompt.append(selection.nearbyText);
        }
        return prompt.toString();
    }

    private static void appendLine(StringBuffer prompt, String key, String value) {
        if (value != null && value.length() > 0) {
            prompt.append(key).append(": ").append(value).append('\n');
        }
    }

    static final class SelectionData {
        String selectedText;
        String nearbyText;
        String title;
        String authors;
        String language;
        String startPosition;
        String endPosition;

        boolean hasText() {
            return selectedText != null && selectedText.trim().length() > 0;
        }

        int textLength() {
            return selectedText == null ? 0 : selectedText.length();
        }

        static SelectionData capture(ReaderSDK sdk) {
            SelectionData data = new SelectionData();
            try {
                Book book = sdk.mO();
                BookNavigator nav = sdk.mM();
                Element startElement = nav.aAK();
                Element endElement = nav.aAL();
                if (book == null || startElement == null || endElement == null) {
                    return data;
                }

                Position start = startElement.my();
                Position end = endElement.mv();
                data.startPosition = safePosition(start);
                data.endPosition = safePosition(end);
                data.selectedText = cleanText(book.r(start, end));
                if (data.selectedText == null || data.selectedText.length() == 0) {
                    data.selectedText = cleanText(startElement.oT());
                }
                data.nearbyText = "";

                BookMetadata metadata = book.mx();
                if (metadata != null) {
                    data.title = safe(metadata.getTitle());
                    data.authors = safe(metadata.getAuthors());
                    data.language = safe(metadata.eU(true));
                }
            } catch (Throwable t) {
                AiAssistantLog.write("selection capture failed: " + t.toString());
            }
            return data;
        }

        private static String safePosition(Position position) {
            try {
                return position == null ? "" : position.rf();
            } catch (Throwable ignored) {
                return "";
            }
        }

        private static String safe(String value) {
            return value == null ? "" : value;
        }

        private static String cleanText(String value) {
            if (value == null) {
                return "";
            }
            return truncate(value.replace('\r', '\n').trim(), 7000);
        }

        private static String truncate(String value, int max) {
            if (value == null || value.length() <= max) {
                return value;
            }
            return value.substring(0, max) + "\n[...]";
        }
    }
}
