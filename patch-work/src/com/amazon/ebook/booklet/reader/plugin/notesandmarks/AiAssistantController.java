package com.amazon.ebook.booklet.reader.plugin.notesandmarks;

import com.amazon.ebook.booklet.reader.sdk.BookNavigator;
import com.amazon.ebook.booklet.reader.sdk.ReaderSDK;
import com.amazon.ebook.booklet.reader.sdk.content.Book;
import com.amazon.ebook.booklet.reader.sdk.content.BookMetadata;
import com.amazon.ebook.booklet.reader.sdk.content.Position;
import com.amazon.ebook.booklet.reader.sdk.ui.element.Element;
import java.awt.Component;
import javax.swing.JOptionPane;

final class AiAssistantController {
    private static final String[] ACTIONS = {
        "Explain",
        "Translate",
        "Words",
        "Grammar",
        "Custom",
        "Cancel"
    };

    private AiAssistantController() { }

    static void open(AnnotationsSelectionDialogProvider provider) {
        open(getReaderSDK(provider));
    }

    static void open(final ReaderSDK sdk) {
        if (sdk == null) {
            return;
        }

        final SelectionData selection = SelectionData.capture(sdk);
        if (!selection.hasText()) {
            showMessage(sdk, "No selected text found.");
            return;
        }

        try {
            sdk.Jl().HF().bQ(false);
        } catch (Throwable ignored) {
            // The AI dialog can still be shown if the selection menu close path changes.
        }

        final Component parent = getParentComponent(sdk);
        int choice = JOptionPane.showOptionDialog(
                parent,
                "Choose AI prompt for selected text.",
                "AI",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.PLAIN_MESSAGE,
                null,
                ACTIONS,
                ACTIONS[0]);

        if (choice < 0 || choice >= ACTIONS.length || "Cancel".equals(ACTIONS[choice])) {
            return;
        }

        String promptName = ACTIONS[choice];
        String promptInstruction = presetPrompt(promptName);
        if ("Custom".equals(promptName)) {
            Object input = JOptionPane.showInputDialog(
                    parent,
                    "Custom request for selected text:",
                    "AI Custom",
                    JOptionPane.PLAIN_MESSAGE);
            if (input == null) {
                return;
            }
            promptInstruction = String.valueOf(input).trim();
            if (promptInstruction.length() == 0) {
                return;
            }
        }

        OpenRouterConfig config = OpenRouterConfig.load();
        if (!config.isUsable()) {
            showMessage(sdk, "OpenRouter config missing. Check /mnt/us/native-reader-research/ai-assistant.properties");
            return;
        }

        final AiResponseWindow window = AiResponseWindow.show(
                sdk,
                "AI " + promptName,
                "Sending request to OpenRouter...");

        String userPrompt = buildUserPrompt(promptInstruction, selection);
        OpenRouterClient.send(sdk, config, userPrompt, new OpenRouterClient.Callback() {
            public void onSuccess(final String text) {
                sdk.invokeLater(new Runnable() {
                    public void run() {
                        window.setText(text);
                    }
                });
            }

            public void onError(final String message) {
                sdk.invokeLater(new Runnable() {
                    public void run() {
                        window.setText("OpenRouter error:\n" + message);
                    }
                });
            }
        });
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

    private static Component getParentComponent(ReaderSDK sdk) {
        try {
            return sdk.Jl().KM();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void showMessage(ReaderSDK sdk, String message) {
        try {
            sdk.Jl().showMessage(message);
        } catch (Throwable ignored) {
            JOptionPane.showMessageDialog(getParentComponent(sdk), message);
        }
    }

    private static String presetPrompt(String promptName) {
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
        return "Explain the selected text in simple Russian. Start with the core meaning, then mention "
                + "important nuance, references, difficult words, or ambiguity.";
    }

    private static String buildUserPrompt(String instruction, SelectionData selection) {
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
                data.nearbyText = nearbyText(book, start);

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

        private static String nearbyText(Book book, Position start) {
            try {
                String text = cleanText(book.a(start, 5000));
                if (text != null && text.length() > 0) {
                    return truncate(text, 5000);
                }
            } catch (Throwable t) {
                AiAssistantLog.write("nearby text unavailable: " + t.toString());
            }
            return "";
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
