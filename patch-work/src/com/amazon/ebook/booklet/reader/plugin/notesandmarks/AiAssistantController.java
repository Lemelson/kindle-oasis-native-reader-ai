package com.amazon.ebook.booklet.reader.plugin.notesandmarks;

import com.amazon.ebook.booklet.reader.sdk.BookNavigator;
import com.amazon.ebook.booklet.reader.sdk.ReaderSDK;
import com.amazon.ebook.booklet.reader.sdk.content.Book;
import com.amazon.ebook.booklet.reader.sdk.content.BookMetadata;
import com.amazon.ebook.booklet.reader.sdk.content.Position;
import com.amazon.ebook.booklet.reader.sdk.content.WordEntry;
import com.amazon.ebook.booklet.reader.sdk.content.WordIterator;
import com.amazon.ebook.booklet.reader.sdk.ui.SelectionDialog;
import com.amazon.ebook.booklet.reader.sdk.ui.SelectionDialogCard;
import com.amazon.ebook.booklet.reader.sdk.ui.element.Element;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

final class AiAssistantController {
    private static final int CONTEXT_WORDS_BEFORE = 5000;
    private static final int CONTEXT_WORDS_AFTER = 3000;
    private static final int CONTEXT_MAX_CHARS = 60000;
    private static final int CONTEXT_ITERATOR_TIMEOUT_MS = 2500;

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
        enlargeSmartCard(smartCardDialog);
    }

    private static void enlargeSmartCard(Object smartCardDialog) {
        try {
            Rectangle bounds = getDialogBounds(smartCardDialog);
            if (bounds == null) {
                Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
                bounds = new Rectangle(
                        screen.width / 24,
                        screen.height / 2,
                        screen.width - (screen.width / 12),
                        screen.height / 3);
            }

            int extraHeight = Math.max(40, bounds.height / 2);
            Rectangle larger = new Rectangle(
                    bounds.x,
                    Math.max(0, bounds.y - extraHeight),
                    bounds.width,
                    bounds.height + extraHeight);

            Method setBounds = getMethod(
                    smartCardDialog.getClass(), "setDialogBounds", new Class[] { Rectangle.class });
            setBounds.invoke(smartCardDialog, new Object[] { larger });
            try {
                smartCardDialog.getClass().getMethod("validate", new Class[0])
                        .invoke(smartCardDialog, new Object[0]);
            } catch (Throwable ignored) {
                // Dialog resizing should not be able to break the AI card.
            }
        } catch (Throwable t) {
            AiAssistantLog.write("smart card resize skipped: " + t.toString());
        }
    }

    private static Rectangle getDialogBounds(Object smartCardDialog) {
        try {
            Method method = getMethod(smartCardDialog.getClass(), "getDialogBounds", new Class[0]);
            Object value = method.invoke(smartCardDialog, new Object[0]);
            if (value instanceof Rectangle) {
                return new Rectangle((Rectangle) value);
            }
        } catch (Throwable ignored) {
            // Some KDialog builds do not expose getDialogBounds.
        }
        return null;
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
        if ("Short".equals(promptName)) {
            return "Explain the selected fragment in Russian in 3-10 short sentences. "
                    + "Give the immediate meaning first. If it is a term, name, reference, or idea, "
                    + "define it and say why it matters here. If it is a sentence or argument, "
                    + "restate the point in simpler words.";
        }
        if ("Point".equals(promptName)) {
            return "Explain the point of the selected fragment in Russian. "
                    + "Say what role this fragment plays in the surrounding argument, scene, or idea. "
                    + "Focus on why it matters, not just what it literally says. If it connects to a "
                    + "previous idea in the context, mention that connection briefly.";
        }
        if ("Recall".equals(promptName)) {
            return "Explain what I should remember to understand the selected fragment. "
                    + "If this is a person, concept, event, place, or reference, remind me what it is "
                    + "and why it matters here. If there is no special background needed, explain the "
                    + "immediate meaning instead.";
        }
        return "Explain the selected fragment in simple Russian. "
                + "Use the context only to understand the selected fragment, not to summarize the whole context. "
                + "Restate the meaning in simpler words, explain any important term, reference, or compact idea, "
                + "and mention ambiguity only when it changes the meaning. Keep the answer concise but sufficient.";
    }

    static String buildUserPrompt(String modeName, String instruction, SelectionData selection) {
        StringBuffer prompt = new StringBuffer();
        prompt.append("Context window around the selected fragment:\n");
        prompt.append("The Kindle patch tries to include about ");
        prompt.append(CONTEXT_WORDS_BEFORE);
        prompt.append(" words before the selected fragment and about ");
        prompt.append(CONTEXT_WORDS_AFTER);
        prompt.append(" words after it. The selected fragment is also repeated separately below.\n\n");
        if (selection.contextText != null && selection.contextText.length() > 0) {
            prompt.append("<CONTEXT>\n");
            prompt.append(selection.contextText);
            prompt.append("\n</CONTEXT>\n\n");
        } else {
            prompt.append("<CONTEXT>\n");
            prompt.append("Context could not be extracted from Kindle; use the selected fragment only.\n");
            prompt.append("</CONTEXT>\n\n");
        }

        prompt.append("<SELECTED_FRAGMENT>\n");
        prompt.append(selection.selectedText);
        prompt.append("\n</SELECTED_FRAGMENT>\n\n");

        prompt.append("Selected mode: ");
        prompt.append(modeName);
        prompt.append("\n\n");
        prompt.append("Task:\n");
        prompt.append(instruction);
        prompt.append("\n\nAnswer in Russian. Be concise but useful. Adapt the answer to the attached context, ");
        prompt.append("the book metadata, the likely genre and situation, the selected fragment, and the selected mode. ");
        prompt.append("If the genre or situation is not obvious, use the most likely reading. ");
        prompt.append("Do not summarize the whole context unless the selected mode requires it; focus on the selected fragment.\n\n");
        prompt.append("Book metadata:\n");
        appendLine(prompt, "Title", selection.title);
        appendLine(prompt, "Authors", selection.authors);
        appendLine(prompt, "Book language", selection.language);
        appendLine(prompt, "Start position", selection.startPosition);
        appendLine(prompt, "End position", selection.endPosition);
        return prompt.toString();
    }

    private static void appendLine(StringBuffer prompt, String key, String value) {
        if (value != null && value.length() > 0) {
            prompt.append(key).append(": ").append(value).append('\n');
        }
    }

    static final class SelectionData {
        String selectedText;
        String contextText;
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
                data.contextText = contextText(book, start, data.selectedText);

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

        private static String contextText(Book book, Position start, String selectedText) {
            WordIterator iterator = null;
            try {
                AiAssistantLog.write("context extraction start");
                iterator = book.a(start, CONTEXT_WORDS_BEFORE, CONTEXT_WORDS_AFTER, true);
                if (iterator == null) {
                    AiAssistantLog.write("context iterator null");
                    return "";
                }
                try {
                    iterator.setTimeout(CONTEXT_ITERATOR_TIMEOUT_MS);
                } catch (Throwable ignored) {
                    // Older content backends may ignore iterator timeout.
                }

                StringBuffer out = new StringBuffer();
                int words = 0;
                int wordLimit = CONTEXT_WORDS_BEFORE + CONTEXT_WORDS_AFTER + 500;
                while (words < wordLimit && out.length() < CONTEXT_MAX_CHARS && iterator.hasNext()) {
                    WordEntry entry = iterator.sz();
                    appendWord(out, entry);
                    words++;
                }

                String context = cleanContext(out.toString());
                if (context.length() == 0) {
                    context = selectedText == null ? "" : selectedText;
                }
                AiAssistantLog.write("context extraction done words=" + words
                        + " length=" + context.length());
                return context;
            } catch (Throwable t) {
                AiAssistantLog.write("context extraction failed: " + t.toString());
                return "";
            } finally {
                if (iterator != null) {
                    try {
                        iterator.close();
                    } catch (Throwable ignored) {
                        // Ignore close errors.
                    }
                }
            }
        }

        private static void appendWord(StringBuffer out, WordEntry entry) {
            if (entry == null) {
                return;
            }
            String text = entry.getText();
            if (text == null || text.length() == 0) {
                return;
            }
            if (out.length() > 0 && shouldAddSpace(entry, text)) {
                out.append(' ');
            }
            out.append(text);
        }

        private static boolean shouldAddSpace(WordEntry entry, String text) {
            if (entry.hasLeadingSpace()) {
                return true;
            }
            char first = text.charAt(0);
            return ",.;:!?)]}\"'".indexOf(first) < 0;
        }

        private static String cleanContext(String value) {
            if (value == null) {
                return "";
            }
            return truncate(value.replace('\r', '\n').trim(), CONTEXT_MAX_CHARS);
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
