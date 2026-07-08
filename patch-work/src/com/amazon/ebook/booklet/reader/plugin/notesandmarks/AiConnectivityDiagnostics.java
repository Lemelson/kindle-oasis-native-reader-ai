package com.amazon.ebook.booklet.reader.plugin.notesandmarks;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

final class AiConnectivityDiagnostics {
    private static final int BODY_LIMIT = 240;
    private static final long RETRY_DELAY_MS = 1500L;

    interface Callback {
        void onUpdate(String text);
    }

    private AiConnectivityDiagnostics() { }

    static void run(final OpenRouterConfig config, final Callback callback) {
        Thread thread = new Thread(new Runnable() {
            public void run() {
                StringBuffer report = new StringBuffer();
                append(report, "AI connectivity diagnostics");
                append(report, "VPN: " + (config.vpnEnabled ? "enabled" : "disabled"));
                callback.onUpdate(report.toString());

                try {
                    if (config.vpnEnabled) {
                        append(report, "Starting VPN proxy...");
                        callback.onUpdate(report.toString());
                        OpenRouterClient.ensureVpnProxy(config);
                        append(report, "VPN proxy: OK " + config.vpnProxyHost + ":" + config.vpnProxyPort);
                    }

                    testOpenRouter(config, report, callback);
                    testGeminiModelsList(config, report, callback);
                    testGeminiGenerate(config, report, callback);
                } catch (Throwable t) {
                    append(report, "Diagnostics fatal error: " + shortText(t.toString()));
                } finally {
                    OpenRouterClient.stopVpnProxy(config);
                    append(report, "Done.");
                    callback.onUpdate(report.toString());
                    AiAssistantLog.write("diagnostics complete");
                }
            }
        }, "AIAssistantConnectivityDiagnostics");
        try {
            thread.setDaemon(true);
        } catch (Throwable ignored) {
            // Some Kindle runtimes may not care about daemon status.
        }
        thread.start();
    }

    private static void testOpenRouter(OpenRouterConfig config, StringBuffer report, Callback callback) {
        append(report, "");
        append(report, "OpenRouter models:");
        callback.onUpdate(report.toString());
        try {
            HttpURLConnection connection = OpenRouterClient.openConnection(
                    new URL("https://openrouter.ai/api/v1/models"), config);
            connection.setConnectTimeout(config.timeoutMs);
            connection.setReadTimeout(config.timeoutMs);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
            if (config.apiKey != null && config.apiKey.length() > 0) {
                connection.setRequestProperty("Authorization", "Bearer " + config.apiKey);
            }
            int status = connection.getResponseCode();
            String body = OpenRouterClient.readBody(status >= 200 && status < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream());
            append(report, line("OpenRouter", status, body));
        } catch (Throwable t) {
            append(report, "OpenRouter: FAIL " + shortText(t.toString()));
        }
        callback.onUpdate(report.toString());
    }

    private static void testGeminiModelsList(OpenRouterConfig config, StringBuffer report, Callback callback) {
        append(report, "");
        append(report, "Gemini models.list:");
        String[] keys = geminiKeys(config);
        if (keys.length == 0) {
            append(report, "Gemini models.list: SKIP missing gemini_api_key/gemini_api_keys");
            callback.onUpdate(report.toString());
            return;
        }
        callback.onUpdate(report.toString());
        try {
            String url = trimSlash(config.geminiBaseUrl) + "/models?key=" + urlEncode(keys[0]);
            HttpURLConnection connection = OpenRouterClient.openConnection(new URL(url), config);
            connection.setConnectTimeout(config.timeoutMs);
            connection.setReadTimeout(config.timeoutMs);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
            int status = connection.getResponseCode();
            String body = OpenRouterClient.readBody(status >= 200 && status < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream());
            append(report, line("Gemini models.list", status, body));
        } catch (Throwable t) {
            append(report, "Gemini models.list: FAIL " + shortText(t.toString()));
        }
        callback.onUpdate(report.toString());
    }

    private static void testGeminiGenerate(OpenRouterConfig config, StringBuffer report, Callback callback) {
        append(report, "");
        append(report, "Gemini generateContent:");
        String[] keys = geminiKeys(config);
        if (keys.length == 0) {
            append(report, "Gemini generateContent: SKIP missing gemini_api_key/gemini_api_keys");
            callback.onUpdate(report.toString());
            return;
        }
        String[] models = splitModels(config.geminiModels);
        for (int i = 0; i < models.length; i++) {
            String model = models[i];
            if (model.length() == 0) {
                continue;
            }
            callback.onUpdate(report.toString());
            testGeminiModel(config, model, keys, report, callback);
        }
    }

    private static void testGeminiModel(
            OpenRouterConfig config,
            String model,
            String[] keys,
            StringBuffer report,
            Callback callback) {
        int attempts = Math.max(1, Math.min(Math.max(config.diagnosticAttempts, keys.length), keys.length));
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                String apiKey = keys[(attempt - 1) % keys.length];
                String url = trimSlash(config.geminiBaseUrl) + "/models/" + model
                        + ":generateContent?key=" + urlEncode(apiKey);
                HttpURLConnection connection = OpenRouterClient.openConnection(new URL(url), config);
                connection.setConnectTimeout(config.timeoutMs);
                connection.setReadTimeout(config.timeoutMs);
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Accept", "application/json");

                byte[] body = geminiPayload().getBytes("UTF-8");
                connection.setRequestProperty("Content-Length", String.valueOf(body.length));
                OutputStream output = connection.getOutputStream();
                try {
                    output.write(body);
                    output.flush();
                } finally {
                    output.close();
                }

                int status = connection.getResponseCode();
                String response = OpenRouterClient.readBody(status >= 200 && status < 300
                        ? connection.getInputStream()
                        : connection.getErrorStream());
                append(report, line(modelLabel(model) + " key " + attempt + "/" + attempts, status, response));
                callback.onUpdate(report.toString());
                if (status >= 200 && status < 300) {
                    return;
                }
            } catch (Throwable t) {
                append(report, modelLabel(model) + " key " + attempt + "/" + attempts
                        + ": FAIL " + shortText(t.toString()));
                callback.onUpdate(report.toString());
            }
            if (attempt < attempts) {
                sleep(RETRY_DELAY_MS);
            }
        }
    }

    private static String geminiPayload() {
        return "{\"contents\":[{\"parts\":[{\"text\":\"Reply with exactly: OK\"}]}],"
                + "\"generationConfig\":{\"temperature\":0,\"maxOutputTokens\":32}}";
    }

    private static String[] geminiKeys(OpenRouterConfig config) {
        String source = config.geminiApiKeys;
        if (source == null || source.trim().length() == 0) {
            source = config.geminiApiKey;
        }
        if (source == null || source.trim().length() == 0) {
            return new String[0];
        }
        String[] raw = source.split(",");
        int count = 0;
        for (int i = 0; i < raw.length; i++) {
            if (raw[i] != null && raw[i].trim().length() > 0) {
                count++;
            }
        }
        String[] keys = new String[count];
        int out = 0;
        for (int i = 0; i < raw.length; i++) {
            String key = raw[i] == null ? "" : raw[i].trim();
            if (key.length() > 0) {
                keys[out++] = key;
            }
        }
        return keys;
    }

    private static String[] splitModels(String value) {
        if (value == null || value.trim().length() == 0) {
            return new String[0];
        }
        return value.split(",");
    }

    private static String line(String label, int status, String body) {
        String prefix = (status >= 200 && status < 300) ? "OK" : "FAIL";
        return label + ": " + prefix + " HTTP " + status + " " + shortText(body);
    }

    private static String modelLabel(String model) {
        if ("gemini-3.1-pro-preview".equals(model)) {
            return "Gemini 3.1 Pro (" + model + ")";
        }
        if ("gemini-3.0-flash".equals(model)) {
            return "Gemini 3.0 Flash (" + model + ")";
        }
        if ("gemini-3.5-flash".equals(model)) {
            return "Gemini 3.5 Flash (" + model + ")";
        }
        if ("gemini-3-flash-preview".equals(model)) {
            return "Gemini 3 Flash Preview (" + model + ")";
        }
        return model;
    }

    private static String shortText(String text) {
        if (text == null) {
            return "";
        }
        String clean = text.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').trim();
        if (clean.length() > BODY_LIMIT) {
            return clean.substring(0, BODY_LIMIT) + "...";
        }
        return clean;
    }

    private static String trimSlash(String value) {
        if (value == null || value.length() == 0) {
            return "https://generativelanguage.googleapis.com/v1beta";
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static String urlEncode(String value) throws Exception {
        return java.net.URLEncoder.encode(value == null ? "" : value, "UTF-8");
    }

    private static void append(StringBuffer out, String line) {
        out.append(line).append('\n');
    }

    private static void sleep(long delayMs) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ignored) {
            // Keep diagnostics simple.
        }
    }
}
