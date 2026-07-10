package com.amazon.ebook.booklet.reader.plugin.notesandmarks;

import com.amazon.ebook.booklet.reader.sdk.BookNavigator;
import com.amazon.ebook.booklet.reader.sdk.ReaderSDK;
import com.amazon.ebook.booklet.reader.sdk.content.Book;
import com.amazon.ebook.booklet.reader.sdk.content.BookMetadata;
import com.amazon.ebook.booklet.reader.sdk.content.Position;
import com.amazon.ebook.booklet.reader.sdk.content.TableOfContents;
import com.amazon.ebook.booklet.reader.sdk.content.TableOfContentsEntry;
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
        if ("Words".equals(promptName)) {
            return "Reader intention:\n"
                    + "The wording, idiom, grammar, metaphor, tone, or phrase structure confused me.\n\n"
                    + "Task:\n"
                    + "Explain the language of the selected fragment in Russian.\n\n"
                    + "If the text is in English:\n"
                    + "- Give a natural Russian paraphrase or translation.\n"
                    + "- Explain idioms, phrasal verbs, unusual syntax, pronouns, tone, register, irony, "
                    + "metaphor, or implied meaning when relevant.\n"
                    + "- Explain only the language features that matter for understanding this fragment.\n\n"
                    + "If the text is in Russian or another language:\n"
                    + "- Explain archaic, literary, formal, figurative, or culturally loaded wording.\n"
                    + "- Explain sentence structure if it blocks understanding.\n\n"
                    + "Do not summarize the plot or the whole argument.\n\n"
                    + "Output:\n"
                    + "Смысл: [natural Russian meaning]\n"
                    + "Язык: [why the wording means this / what nuance it carries]";
        }
        if ("Wiki".equals(promptName)) {
            return "Reader intention:\n"
                    + "I highlighted a name, term, place, event, work, or concept, often one I am "
                    + "meeting for the first time, and I want to know what or who it is, like a short "
                    + "encyclopedia entry.\n\n"
                    + "Task:\n"
                    + "Identify the highlighted item and explain it using real-world knowledge.\n\n"
                    + "- Lead with world knowledge: who the person is, what the event, place, work, or "
                    + "concept is. Add concrete anchors (dates, field, role, nationality) when relevant.\n"
                    + "- If the item is specific to this book (a term the author coined, a person or case "
                    + "introduced earlier), identify it from BEFORE_CONTEXT and say it comes from the book.\n"
                    + "- Stay factual and neutral. If the exact referent is unclear, name the most likely "
                    + "one and say so briefly.\n"
                    + "- Explain the item itself, not the meaning of the surrounding sentence.\n\n"
                    + "For fiction, memoir, mystery, classics, or other plot-driven works:\n"
                    + "- Explain only what is safe to know at this point in the book.\n"
                    + "- Do not reveal a real or fictional figure's later role, fate, or a later twist.\n\n"
                    + "Output:\n"
                    + "Что это: [one line: who or what, with a concrete anchor if relevant]\n"
                    + "Кратко: [2-4 sentence encyclopedic summary]\n"
                    + "Здесь: [one line: why it comes up in this passage]";
        }
        if ("Why".equals(promptName)) {
            return "Reader intention:\n"
                    + "I understand the words, but I do not understand why this fragment matters here.\n\n"
                    + "Task:\n"
                    + "Explain the function of the selected fragment in the surrounding scene, argument, or idea.\n\n"
                    + "For fiction, drama, memoir, and classics:\n"
                    + "- Explain motive, subtext, conflict, emotional shift, relationship, mood, irony, tension, or setup.\n"
                    + "- If it creates suspense or raises a question, say that without revealing the answer.\n"
                    + "- Do not reveal future plot.\n\n"
                    + "For nonfiction, philosophy, history, biography, or essays:\n"
                    + "- Explain whether the fragment works as a premise, conclusion, objection, example, "
                    + "definition, contrast, transition, consequence, or key distinction.\n"
                    + "- Say why the author needs this point here.\n\n"
                    + "Keep literal summary minimal. Focus on significance.\n\n"
                    + "Output:\n"
                    + "Зачем это здесь: [main function]\n"
                    + "Как это связано с контекстом: [brief connection]";
        }
        if ("Deep".equals(promptName)) {
            return "Reader intention:\n"
                    + "This fragment feels important, dense, or difficult. I want a deeper explanation.\n\n"
                    + "Task:\n"
                    + "Give a deeper explanation of the selected fragment while staying focused on it.\n\n"
                    + "For philosophy or theory:\n"
                    + "- Reconstruct the idea step by step.\n"
                    + "- Define important terms.\n"
                    + "- Explain the hidden assumption or logical move.\n"
                    + "- Give a simple example or analogy if useful.\n\n"
                    + "For literary fiction, classics, or drama:\n"
                    + "- Explain theme, psychological tension, symbolism, moral conflict, style, or ambiguity.\n"
                    + "- Do not reveal future plot or later outcomes.\n"
                    + "- Do not turn the answer into a general essay about the whole book.\n\n"
                    + "For popular nonfiction, business, history, psychology, or biography:\n"
                    + "- Explain the concept, stakes, implication, and why it matters.\n"
                    + "- Use safe external background if helpful, but keep the selected fragment central.\n\n"
                    + "Output:\n"
                    + "Главная идея: [core meaning]\n"
                    + "Глубже: [deeper layer]\n"
                    + "Пример / зачем это важно: [simple example, implication, or importance]\n\n"
                    + "Limit: 120-250 Russian words.";
        }
        return "Reader intention:\n"
                + "I do not understand what the selected fragment means.\n\n"
                + "Task:\n"
                + "Explain the selected fragment in simple Russian.\n\n"
                + "Adapt to the text:\n"
                + "- If it is dialogue, explain what the speaker likely means, the tone, and what is implied.\n"
                + "- If it is narration, explain what is happening or being described.\n"
                + "- If it is an argument, restate the claim in simpler words.\n"
                + "- If it is a concept, reference, metaphor, or compressed idea, briefly define it.\n"
                + "- If the wording is difficult, briefly clarify it, but do not turn this into a full language lesson.\n\n"
                + "Output:\n"
                + "Start with the immediate meaning. Then add one short nuance only if useful. "
                + "Usually 3-7 short sentences.";
    }

    static String buildUserPrompt(String modeName, String instruction, SelectionData selection) {
        StringBuffer prompt = new StringBuffer();
        prompt.append("<reader_request>\n");
        prompt.append("Mode: ");
        prompt.append(modeName);
        prompt.append('\n');
        prompt.append("Reader intention: ");
        prompt.append(modeIntention(modeName));
        prompt.append("\n</reader_request>\n\n");

        prompt.append("<book_metadata>\n");
        appendLine(prompt, "Title", selection.title);
        appendLine(prompt, "Authors", selection.authors);
        appendLine(prompt, "Book language", selection.language);
        appendLine(prompt, "Start position", selection.startPosition);
        appendLine(prompt, "End position", selection.endPosition);
        if (selection.chapter != null && selection.chapter.length() > 0) {
            appendLine(prompt, "Current chapter or section", selection.chapter);
        } else {
            prompt.append("Current chapter or section: unknown\n");
        }
        prompt.append("</book_metadata>\n\n");

        prompt.append("<reader_progress_policy>\n");
        prompt.append("The user likely has read BEFORE_CONTEXT and SELECTED_FRAGMENT.\n");
        prompt.append("AFTER_CONTEXT is supplied only for silent disambiguation.\n");
        prompt.append("In fiction, memoir, biography, mystery, classics, and other chronological or plot-driven texts, ");
        prompt.append("do not reveal information from AFTER_CONTEXT.\n");
        prompt.append("</reader_progress_policy>\n\n");

        prompt.append("<before_context>\n");
        if (selection.beforeContext != null && selection.beforeContext.length() > 0) {
            prompt.append(selection.beforeContext);
        } else {
            prompt.append("BEFORE_CONTEXT could not be extracted from Kindle.");
        }
        prompt.append("\n</before_context>\n\n");

        prompt.append("<selected_fragment>\n");
        prompt.append(selection.selectedText);
        prompt.append("\n</selected_fragment>\n\n");

        prompt.append("<after_context silent=\"true\">\n");
        if (selection.afterContext != null && selection.afterContext.length() > 0) {
            prompt.append(selection.afterContext);
        } else {
            prompt.append("AFTER_CONTEXT could not be extracted from Kindle.");
        }
        prompt.append("\n</after_context>\n\n");

        prompt.append("<adaptation_rules>\n");
        prompt.append("Before answering, silently infer:\n");
        prompt.append("1. the type of text,\n");
        prompt.append("2. the likely reason the user highlighted this,\n");
        prompt.append("3. what background is safe and useful,\n");
        prompt.append("4. how the selected mode should be applied here.\n\n");
        prompt.append("Use the selected mode as the strongest signal, but adapt to the actual fragment. ");
        prompt.append("If the mode and fragment suggest different needs, satisfy the selected mode first, ");
        prompt.append("then briefly cover the other need only if useful.\n");
        prompt.append("Do not show this analysis.\n");
        prompt.append("</adaptation_rules>\n\n");

        prompt.append("<mode_instructions>\n");
        prompt.append(instruction);
        prompt.append("\n</mode_instructions>\n\n");

        prompt.append("<output_rules>\n");
        prompt.append("Answer in Russian.\n");
        prompt.append("Focus on the selected fragment.\n");
        prompt.append("Start with the direct answer.\n");
        prompt.append("Do not summarize the whole context unless the mode requires it.\n");
        prompt.append("For plot-driven or chronological books: no spoilers beyond BEFORE_CONTEXT and SELECTED_FRAGMENT.\n");
        prompt.append("Keep the answer concise unless Mode is Deep.\n");
        prompt.append("Use plain text only: no Markdown bold/italic markers, no asterisks, no underscores for formatting.\n");
        prompt.append("</output_rules>\n");
        return prompt.toString();
    }

    private static String modeIntention(String modeName) {
        if ("Words".equals(modeName)) {
            return "The wording, idiom, grammar, metaphor, tone, or phrase structure confused me.";
        }
        if ("Wiki".equals(modeName)) {
            return "I want to know who or what the highlighted item is, like a short encyclopedia entry.";
        }
        if ("Why".equals(modeName)) {
            return "I understand the words, but I do not understand why this fragment matters here.";
        }
        if ("Deep".equals(modeName)) {
            return "This fragment feels important, dense, or difficult. I want a deeper explanation.";
        }
        return "I do not understand what the selected fragment means.";
    }

    private static void appendLine(StringBuffer prompt, String key, String value) {
        if (value != null && value.length() > 0) {
            prompt.append(key).append(": ").append(value).append('\n');
        }
    }

    static final class SelectionData {
        String selectedText;
        String beforeContext;
        String afterContext;
        String title;
        String authors;
        String language;
        String startPosition;
        String endPosition;
        String chapter;

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
                data.beforeContext = contextText(book, start, CONTEXT_WORDS_BEFORE, 0, "before");
                data.afterContext = contextText(book, end, 0, CONTEXT_WORDS_AFTER, "after");

                BookMetadata metadata = book.mx();
                if (metadata != null) {
                    data.title = safe(metadata.getTitle());
                    data.authors = safe(metadata.getAuthors());
                    data.language = safe(metadata.eU(true));
                }

                data.chapter = currentChapter(book, start);
            } catch (Throwable t) {
                AiAssistantLog.write("selection capture failed: " + t.toString());
            }
            return data;
        }

        private static String currentChapter(Book book, Position anchor) {
            try {
                if (book == null || anchor == null) {
                    return "";
                }
                TableOfContents toc = book.mA();
                if (toc == null) {
                    return "";
                }
                TableOfContentsEntry entry = toc.f(anchor);
                if (entry == null) {
                    return "";
                }
                String label = entry.getLabel();
                AiAssistantLog.write("chapter resolved="
                        + (label == null ? "" : label.trim()));
                return label == null ? "" : label.trim();
            } catch (Throwable t) {
                AiAssistantLog.write("chapter lookup failed: " + t.toString());
                return "";
            }
        }

        private static String contextText(
                Book book,
                Position anchor,
                int wordsBefore,
                int wordsAfter,
                String label) {
            WordIterator iterator = null;
            try {
                AiAssistantLog.write("context extraction start label=" + label
                        + " before=" + wordsBefore + " after=" + wordsAfter);
                iterator = book.a(anchor, wordsBefore, wordsAfter, true);
                if (iterator == null) {
                    AiAssistantLog.write("context iterator null label=" + label);
                    return "";
                }
                try {
                    iterator.setTimeout(CONTEXT_ITERATOR_TIMEOUT_MS);
                } catch (Throwable ignored) {
                    // Older content backends may ignore iterator timeout.
                }

                StringBuffer out = new StringBuffer();
                int words = 0;
                int wordLimit = wordsBefore + wordsAfter + 500;
                while (words < wordLimit && out.length() < CONTEXT_MAX_CHARS && iterator.hasNext()) {
                    WordEntry entry = iterator.sz();
                    appendWord(out, entry);
                    words++;
                }

                String context = cleanContext(out.toString());
                AiAssistantLog.write("context extraction done label=" + label + " words=" + words
                        + " length=" + context.length());
                return context;
            } catch (Throwable t) {
                AiAssistantLog.write("context extraction failed label=" + label + ": " + t.toString());
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
