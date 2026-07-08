package com.amazon.ebook.booklet.reader.plugin.notesandmarks;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

final class OpenRouterConfig {
    static final String CONFIG_PATH = "/mnt/us/native-reader-research/ai-assistant.properties";

    String apiKey = "";
    String model = "deepseek/deepseek-v4-flash";
    String proModel = "deepseek/deepseek-v4-pro";
    String proFallbackModel = "xiaomi/mimo-v2.5-pro";
    String fallbackModel = "";
    boolean proRequest = false;
    boolean fallbackRequest = false;
    int retryPhase = 0;
    String baseUrl = "https://openrouter.ai/api/v1/chat/completions";
    String referer = "https://kindle.local/native-reader-ai";
    String title = "Kindle Native Reader AI";
    int timeoutMs = 45000;
    int maxTokens = 3000;
    double temperature = 0.2d;
    String reasoningEffort = "high";
    boolean reasoningExclude = false;
    boolean vpnEnabled = false;
    String vpnProxyHost = "127.0.0.1";
    int vpnProxyPort = 18080;
    String vpnStartScript = "/mnt/us/native-reader-research/vpn/start-ai-vpn.sh";
    String vpnStopScript = "/mnt/us/native-reader-research/vpn/stop-ai-vpn.sh";
    boolean diagnosticsEnabled = false;
    String geminiApiKey = "";
    String geminiApiKeys = "";
    String geminiBaseUrl = "https://generativelanguage.googleapis.com/v1beta";
    String geminiModels = "gemini-3.1-pro-preview,gemini-3.0-flash,gemini-3.5-flash,gemini-3-flash-preview";
    int diagnosticAttempts = 2;

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
            config.proModel = get(properties, "pro_model", config.proModel);
            config.proFallbackModel = get(properties, "pro_fallback_model", config.proFallbackModel);
            config.baseUrl = get(properties, "base_url", config.baseUrl);
            config.referer = get(properties, "referer", config.referer);
            config.title = get(properties, "title", config.title);
            config.timeoutMs = getInt(properties, "timeout_ms", config.timeoutMs);
            config.maxTokens = getInt(properties, "max_tokens", config.maxTokens);
            config.temperature = getDouble(properties, "temperature", config.temperature);
            config.reasoningEffort = get(properties, "reasoning_effort", config.reasoningEffort);
            config.reasoningExclude = getBoolean(properties, "reasoning_exclude", config.reasoningExclude);
            config.vpnEnabled = getBoolean(properties, "vpn_enabled", config.vpnEnabled);
            config.vpnProxyHost = get(properties, "vpn_proxy_host", config.vpnProxyHost);
            config.vpnProxyPort = getInt(properties, "vpn_proxy_port", config.vpnProxyPort);
            config.vpnStartScript = get(properties, "vpn_start_script", config.vpnStartScript);
            config.vpnStopScript = get(properties, "vpn_stop_script", config.vpnStopScript);
            config.diagnosticsEnabled = getBoolean(properties, "diagnostics_enabled", config.diagnosticsEnabled);
            config.geminiApiKey = get(properties, "gemini_api_key", config.geminiApiKey);
            config.geminiApiKeys = get(properties, "gemini_api_keys", config.geminiApiKeys);
            config.geminiBaseUrl = get(properties, "gemini_base_url", config.geminiBaseUrl);
            config.geminiModels = get(properties, "gemini_models", config.geminiModels);
            config.diagnosticAttempts = getInt(properties, "diagnostic_attempts", config.diagnosticAttempts);
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

    private static boolean getBoolean(Properties properties, String key, boolean fallback) {
        String value = get(properties, key, String.valueOf(fallback));
        return "true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value);
    }
}
