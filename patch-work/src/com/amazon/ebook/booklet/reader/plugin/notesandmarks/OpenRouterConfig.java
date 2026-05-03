package com.amazon.ebook.booklet.reader.plugin.notesandmarks;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

final class OpenRouterConfig {
    static final String CONFIG_PATH = "/mnt/us/native-reader-research/ai-assistant.properties";

    String apiKey = "";
    String model = "deepseek/deepseek-v4-flash";
    String baseUrl = "https://openrouter.ai/api/v1/chat/completions";
    String referer = "https://kindle.local/native-reader-ai";
    String title = "Kindle Native Reader AI";
    int timeoutMs = 45000;
    int maxTokens = 900;
    double temperature = 0.2d;

    boolean isUsable() {
        return apiKey != null && apiKey.trim().length() > 0
                && model != null && model.trim().length() > 0
                && baseUrl != null && baseUrl.trim().length() > 0;
    }

    static OpenRouterConfig load() {
        OpenRouterConfig config = new OpenRouterConfig();
        File file = new File(CONFIG_PATH);
        if (!file.exists()) {
            AiAssistantLog.write("config not found: " + CONFIG_PATH);
            return config;
        }

        Properties properties = new Properties();
        FileInputStream input = null;
        try {
            input = new FileInputStream(file);
            properties.load(input);
            config.apiKey = get(properties, "api_key", config.apiKey);
            config.model = get(properties, "model", config.model);
            config.baseUrl = get(properties, "base_url", config.baseUrl);
            config.referer = get(properties, "referer", config.referer);
            config.title = get(properties, "title", config.title);
            config.timeoutMs = getInt(properties, "timeout_ms", config.timeoutMs);
            config.maxTokens = getInt(properties, "max_tokens", config.maxTokens);
            config.temperature = getDouble(properties, "temperature", config.temperature);
        } catch (Throwable t) {
            AiAssistantLog.write("config load failed: " + t.toString());
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (Throwable ignored) {
                    // Ignore close errors.
                }
            }
        }
        return config;
    }

    private static String get(Properties properties, String key, String fallback) {
        String value = properties.getProperty(key);
        return value == null ? fallback : value.trim();
    }

    private static int getInt(Properties properties, String key, int fallback) {
        try {
            return Integer.parseInt(get(properties, key, String.valueOf(fallback)));
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static double getDouble(Properties properties, String key, double fallback) {
        try {
            return Double.parseDouble(get(properties, key, String.valueOf(fallback)));
        } catch (Throwable ignored) {
            return fallback;
        }
    }
}
