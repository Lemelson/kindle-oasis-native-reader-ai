package com.amazon.ebook.booklet.reader.plugin.notesandmarks;

import java.io.FileInputStream;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class DirectProviderChainSmoke {
    private DirectProviderChainSmoke() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 2
                || (!"normal".equals(args[1]) && !"pro".equals(args[1]))) {
            throw new IllegalArgumentException(
                    "usage: DirectProviderChainSmoke <properties> <normal|pro>");
        }

        OpenRouterConfig config = loadConfig(args[0]);
        final boolean proFirst = "pro".equals(args[1]);
        final CountDownLatch done = new CountDownLatch(1);
        final boolean[] success = new boolean[] { false };
        final String[] provider = new String[] { proFirst ? "openrouter" : "gemini" };
        final int[] contentChars = new int[] { 0 };

        AiProviderChainClient.send(
                null,
                config,
                "Connectivity smoke test. Reply exactly OK.",
                proFirst,
                new OpenRouterClient.Callback() {
                    public void onStatus(String message) {
                        if (message != null && message.indexOf("Переключаюсь на Gemini") >= 0) {
                            provider[0] = "gemini";
                        } else if (message != null
                                && message.indexOf("Переключаюсь на DeepSeek") >= 0) {
                            provider[0] = "openrouter";
                        }
                    }

                    public void onThinking(String text) { }

                    public void onContent(String text) {
                        if (text != null) {
                            contentChars[0] += text.length();
                        }
                    }

                    public void onSuccess(String text) {
                        success[0] = text != null && text.trim().length() > 0;
                        done.countDown();
                    }

                    public void onError(String message) {
                        done.countDown();
                    }
                });

        if (!done.await(150L, TimeUnit.SECONDS)) {
            throw new RuntimeException("provider chain timed out");
        }
        System.out.println(
                "mode=" + args[1]
                + " provider=" + provider[0]
                + " success=" + success[0]
                + " content_chars=" + contentChars[0]);
        if (!success[0]) {
            throw new RuntimeException("provider chain failed");
        }
    }

    private static OpenRouterConfig loadConfig(String path) throws Exception {
        Properties properties = new Properties();
        FileInputStream input = new FileInputStream(path);
        try {
            properties.load(input);
        } finally {
            input.close();
        }

        OpenRouterConfig config = new OpenRouterConfig();
        config.apiKey = get(properties, "api_key", config.apiKey);
        config.model = get(properties, "model", config.model);
        config.proModel = get(properties, "pro_model", config.proModel);
        config.proFallbackModel = get(
                properties, "pro_fallback_model", config.proFallbackModel);
        config.baseUrl = get(properties, "base_url", config.baseUrl);
        config.referer = get(properties, "referer", config.referer);
        config.title = get(properties, "title", config.title);
        config.timeoutMs = getInt(properties, "timeout_ms", config.timeoutMs);
        config.maxTokens = getInt(properties, "max_tokens", config.maxTokens);
        config.temperature = getDouble(
                properties, "temperature", config.temperature);
        config.reasoningEffort = get(
                properties, "reasoning_effort", config.reasoningEffort);
        config.reasoningExclude = getBoolean(
                properties, "reasoning_exclude", config.reasoningExclude);
        config.geminiApiKey = get(
                properties, "gemini_api_key", config.geminiApiKey);
        config.geminiApiKeys = get(
                properties, "gemini_api_keys", config.geminiApiKeys);
        config.geminiBaseUrl = get(
                properties, "gemini_base_url", config.geminiBaseUrl);
        config.geminiModels = get(
                properties, "gemini_models", config.geminiModels);
        config.geminiThinkingLevel = get(
                properties, "gemini_thinking_level", config.geminiThinkingLevel);
        config.geminiDailyRequestLimit = getInt(
                properties,
                "gemini_daily_request_limit",
                config.geminiDailyRequestLimit);
        config.geminiQuotaWindowMs = getLong(
                properties, "gemini_quota_window_ms", config.geminiQuotaWindowMs);
        config.geminiQuotaCooldownMs = getLong(
                properties,
                "gemini_quota_cooldown_ms",
                config.geminiQuotaCooldownMs);
        return config;
    }

    private static String get(
            Properties properties, String key, String fallback) {
        String value = properties.getProperty(key);
        return value == null ? fallback : value.trim();
    }

    private static int getInt(
            Properties properties, String key, int fallback) {
        try {
            return Integer.parseInt(get(properties, key, String.valueOf(fallback)));
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static long getLong(
            Properties properties, String key, long fallback) {
        try {
            return Long.parseLong(get(properties, key, String.valueOf(fallback)));
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static double getDouble(
            Properties properties, String key, double fallback) {
        try {
            return Double.parseDouble(get(properties, key, String.valueOf(fallback)));
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static boolean getBoolean(
            Properties properties, String key, boolean fallback) {
        String value = get(properties, key, String.valueOf(fallback));
        return "true".equalsIgnoreCase(value)
                || "1".equals(value)
                || "yes".equalsIgnoreCase(value);
    }
}
