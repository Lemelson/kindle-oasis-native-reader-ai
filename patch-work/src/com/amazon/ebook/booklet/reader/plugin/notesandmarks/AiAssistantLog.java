package com.amazon.ebook.booklet.reader.plugin.notesandmarks;

import java.io.FileWriter;

final class AiAssistantLog {
    private static final String LOG_PATH = "/mnt/us/native-reader-research/ai-assistant.log";

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
}
