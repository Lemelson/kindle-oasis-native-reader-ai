package com.amazon.ebook.booklet.reader.plugin.notesandmarks;

import java.io.FileWriter;

final class AiAssistantLog {
    private static final String LOG_PATH = "/mnt/us/native-reader-research/ai-assistant.log";
    private static final String LAST_PROMPT_PATH = "/mnt/us/native-reader-research/ai-last-prompt.txt";
    private static final String LAST_RESPONSE_PATH = "/mnt/us/native-reader-research/ai-last-response.txt";
    private static final String LAST_ERROR_PATH = "/mnt/us/native-reader-research/ai-last-error.txt";

    private AiAssistantLog() { }

    static synchronized void write(String message) {
        FileWriter writer = null;
        try {
            writer = new FileWriter(LOG_PATH, true);
            writer.write(String.valueOf(System.currentTimeMillis()));
            writer.write(" ");
            writer.write(message == null ? "" : message);
            writer.write("\n");
        } catch (Throwable ignored) {
            // Logging must never break reader UI.
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (Throwable ignored) {
                    // Ignore close errors.
                }
            }
        }
    }

    static void writeLastPrompt(String model, String prompt) {
        writeSnapshot(LAST_PROMPT_PATH, "model=" + String.valueOf(model) + "\n\n" + String.valueOf(prompt));
    }

    static void writeLastResponse(String response) {
        writeSnapshot(LAST_RESPONSE_PATH, String.valueOf(response));
    }

    static void writeLastError(String error) {
        writeSnapshot(LAST_ERROR_PATH, String.valueOf(error));
    }

    private static synchronized void writeSnapshot(String path, String content) {
        FileWriter writer = null;
        try {
            writer = new FileWriter(path, false);
            writer.write(String.valueOf(System.currentTimeMillis()));
            writer.write("\n");
            writer.write(content == null ? "" : content);
            writer.write("\n");
        } catch (Throwable ignored) {
            // Snapshot logging must never break reader UI.
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (Throwable ignored) {
                    // Ignore close errors.
                }
            }
        }
    }
}
