package com.amazon.ebook.booklet.reader.plugin.notesandmarks;

import com.amazon.ebook.booklet.reader.sdk.ReaderSDK;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Properties;
import java.util.List;
import java.util.Map;

final class AiProviderChainClient {
    private static final String QUOTA_STATE_PATH = "/mnt/us/native-reader-research/gemini-quota.properties";
    private static final long[] GEMINI_RETRY_DELAYS_MS = new long[] { 500L, 1000L, 2000L };
    private static final long[] OPENROUTER_RETRY_DELAYS_MS = new long[] { 500L, 1000L, 2000L, 2000L };
    private static final int GEMINI_ATTEMPTS = 4;
    private static final int OPENROUTER_PRO_ATTEMPTS = 5;
    private static final int OPENROUTER_FLASH_ATTEMPTS = 4;
    private static final Object REQUEST_LOCK = new Object();
    private static Request activeRequest;

    private AiProviderChainClient() { }

    static final class Request {
        private volatile boolean cancelled;
        private volatile Thread thread;
        private volatile HttpURLConnection connection;

        boolean isCancelled() {
            return cancelled;
        }

        void cancel() {
            cancelled = true;
            HttpURLConnection currentConnection = connection;
            if (currentConnection != null) {
                try {
                    currentConnection.disconnect();
                } catch (Throwable ignored) {
                    // Best-effort cancellation of an active network read.
                }
            }
            Thread currentThread = thread;
            if (currentThread != null) {
                currentThread.interrupt();
            }
        }

        private void setThread(Thread value) {
            thread = value;
        }

        private void setConnection(HttpURLConnection value) {
            connection = value;
            if (cancelled && value != null) {
                value.disconnect();
            }
        }

        private void clearConnection(HttpURLConnection value) {
            if (connection == value) {
                connection = null;
            }
        }
    }

    static Request send(
            ReaderSDK sdk,
            final OpenRouterConfig config,
            final String userPrompt,
            final boolean proFirst,
            final OpenRouterClient.Callback callback) {
        final Request request = new Request();
        Thread thread = new Thread(new Runnable() {
            public void run() {
                try {
                    runChain(config, userPrompt, proFirst, callback, request);
                } finally {
                    synchronized (REQUEST_LOCK) {
                        if (activeRequest == request) {
                            activeRequest = null;
                        }
                    }
                }
            }
        }, "AIAssistantProviderChain");
        request.setThread(thread);
        synchronized (REQUEST_LOCK) {
            if (activeRequest != null) {
                activeRequest.cancel();
            }
            activeRequest = request;
        }
        try {
            thread.setDaemon(true);
        } catch (Throwable ignored) {
            // Some Kindle runtimes may not care about daemon status.
        }
        thread.start();
        return request;
    }

    private static void runChain(
            OpenRouterConfig config,
            String userPrompt,
            boolean proFirst,
            OpenRouterClient.Callback callback,
            Request request) {
        if (request.isCancelled()) {
            return;
        }
        AiAssistantLog.writeLastPrompt(proFirst ? config.proModel : config.geminiModels, userPrompt);
        try {
            if (proFirst) {
                if (tryOpenRouter(config, userPrompt, callback, request,
                        config.proModel,
                        OPENROUTER_PRO_ATTEMPTS,
                        "DeepSeek Pro")) {
                    return;
                }
                if (request.isCancelled()) {
                    return;
                }
                callback.onStatus("Переключаюсь на Gemini…");
                if (tryGemini(config, userPrompt, callback, request,
                        GEMINI_ATTEMPTS,
                        "Gemini")) {
                    return;
                }
                if (request.isCancelled()) {
                    return;
                }
                callback.onStatus("Переключаюсь на DeepSeek Flash…");
                if (tryOpenRouter(config, userPrompt, callback, request,
                        config.model,
                        OPENROUTER_FLASH_ATTEMPTS,
                        "DeepSeek Flash")) {
                    return;
                }
                throw new RuntimeException("All providers failed.");
            }

            if (tryGemini(config, userPrompt, callback, request,
                    GEMINI_ATTEMPTS,
                    "Gemini")) {
                return;
            }
            if (request.isCancelled()) {
                return;
            }
            callback.onStatus("Переключаюсь на DeepSeek Pro…");
            if (tryOpenRouter(config, userPrompt, callback, request,
                    config.proModel,
                    OPENROUTER_PRO_ATTEMPTS,
                    "DeepSeek Pro")) {
                return;
            }
            throw new RuntimeException("All providers failed.");
        } catch (Throwable t) {
            if (request.isCancelled()) {
                AiAssistantLog.write("provider chain cancelled");
                return;
            }
            String message = t.toString();
            AiAssistantLog.writeLastError(message);
            callback.onError("Не удалось получить ответ. Нажмите ещё раз.");
        }
    }

    private static boolean tryGemini(
            OpenRouterConfig config,
            String userPrompt,
            OpenRouterClient.Callback callback,
            Request request,
            int attempts,
            String label) throws Exception {
        String lastError = "";
        GeminiQuotaState quotaState = GeminiQuotaState.load(config);
        for (int attempt = 1; attempt <= attempts; attempt++) {
            if (request.isCancelled()) {
                return false;
            }
            if (attempt > 1) {
                callback.onStatus(label + ": повтор " + attempt + "/" + attempts + "…");
                if (!sleep(retryDelay(GEMINI_RETRY_DELAYS_MS, attempt - 2), request)) {
                    return false;
                }
            }
            String[] keys = geminiKeys(config);
            int keyIndex = quotaState.selectAvailableKey(keys);
            if (keyIndex < 0) {
                lastError = "Gemini quota exhausted for all configured keys.";
                callback.onStatus("Gemini недоступен.");
                break;
            }
            if (attempt == 1) {
                callback.onStatus(label + " думает…");
            }
            try {
                String text = sendGemini(config, userPrompt, keys[keyIndex], callback, request);
                if (request.isCancelled()) {
                    return false;
                }
                if (text != null && text.trim().length() > 0) {
                    quotaState.recordSuccess(keyIndex);
                    AiAssistantLog.writeLastResponse("Gemini 3.6 Flash\n\n" + text);
                    callback.onSuccess(text);
                    return true;
                }
                lastError = "Empty Gemini response.";
            } catch (GeminiProviderException t) {
                lastError = t.toString();
                if (t.quotaLimited) {
                    quotaState.recordQuotaLimited(keyIndex);
                    callback.onStatus("Gemini: пробую другой ключ…");
                }
            } catch (Throwable t) {
                lastError = t.toString();
            }
            AiAssistantLog.write(label + " attempt " + attempt + "/" + attempts
                    + " failed: " + shortReason(lastError));
        }
        return false;
    }

    private static boolean tryOpenRouter(
            OpenRouterConfig config,
            String userPrompt,
            OpenRouterClient.Callback callback,
            Request request,
            String model,
            int attempts,
            String label) throws Exception {
        String lastError = "";
        for (int attempt = 1; attempt <= attempts; attempt++) {
            if (request.isCancelled()) {
                return false;
            }
            if (attempt > 1) {
                callback.onStatus(label + ": повтор " + attempt + "/" + attempts + "…");
                if (!sleep(retryDelay(OPENROUTER_RETRY_DELAYS_MS, attempt - 2), request)) {
                    return false;
                }
            } else {
                callback.onStatus(label + " думает…");
            }
            try {
                String text = sendOpenRouter(config, userPrompt, model, callback, request);
                if (request.isCancelled()) {
                    return false;
                }
                if (text != null && text.trim().length() > 0) {
                    AiAssistantLog.writeLastResponse(label + "\n\n" + text);
                    callback.onSuccess(text);
                    return true;
                }
                lastError = "Empty OpenRouter response.";
            } catch (Throwable t) {
                lastError = t.toString();
            }
            AiAssistantLog.write(label + " attempt " + attempt + "/" + attempts
                    + " failed: " + shortReason(lastError));
        }
        return false;
    }

    private static String sendGemini(
            OpenRouterConfig config,
            String userPrompt,
            String key,
            OpenRouterClient.Callback callback,
            Request request) throws Exception {
        if (key.length() == 0) {
            throw new RuntimeException("Gemini API key missing.");
        }
        String model = firstGeminiModel(config);
        String url = trimSlash(config.geminiBaseUrl) + "/models/" + model
                + ":streamGenerateContent?alt=sse&key=" + java.net.URLEncoder.encode(key, "UTF-8");
        HttpURLConnection connection = OpenRouterClient.openConnection(new URL(url), config);
        request.setConnection(connection);
        try {
            connection.setConnectTimeout(config.timeoutMs);
            connection.setReadTimeout(config.timeoutMs);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "text/event-stream");
            byte[] body = geminiPayload(config, userPrompt).getBytes("UTF-8");
            connection.setRequestProperty("Content-Length", String.valueOf(body.length));
            OutputStream output = connection.getOutputStream();
            try {
                output.write(body);
                output.flush();
            } finally {
                output.close();
            }
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                String response = OpenRouterClient.readBody(connection.getErrorStream());
                throw new GeminiProviderException(
                        "Gemini HTTP " + status + ": " + shortReason(response),
                        isQuotaLimited(status, response));
            }
            return readGeminiEventStream(connection.getInputStream(), callback, request);
        } finally {
            request.clearConnection(connection);
            try {
                connection.disconnect();
            } catch (Throwable ignored) {
                // Ignore disconnect errors.
            }
        }
    }

    static String readGeminiEventStream(
            InputStream input,
            OpenRouterClient.Callback callback) throws Exception {
        return readGeminiEventStream(input, callback, null);
    }

    static String readGeminiEventStream(
            InputStream input,
            OpenRouterClient.Callback callback,
            Request request) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"));
        StringBuffer answer = new StringBuffer();
        StringBuffer eventData = new StringBuffer();
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                if (request != null && request.isCancelled()) {
                    break;
                }
                if (line.length() == 0) {
                    appendGeminiEvent(eventData, answer, callback, request);
                    eventData.setLength(0);
                    continue;
                }
                if (line.startsWith("data:")) {
                    String data = line.substring(5);
                    if (data.startsWith(" ")) {
                        data = data.substring(1);
                    }
                    if (eventData.length() > 0) {
                        eventData.append('\n');
                    }
                    eventData.append(data);
                }
            }
            appendGeminiEvent(eventData, answer, callback, request);
        } finally {
            try {
                reader.close();
            } catch (Throwable ignored) {
                // Ignore close errors.
            }
        }
        AiAssistantLog.write("Gemini stream complete content=" + answer.length());
        return answer.toString();
    }

    private static void appendGeminiEvent(
            StringBuffer eventData,
            StringBuffer answer,
            OpenRouterClient.Callback callback,
            Request request) throws Exception {
        if (request != null && request.isCancelled()) {
            return;
        }
        if (eventData.length() == 0 || "[DONE]".equals(eventData.toString())) {
            return;
        }
        String text = geminiText(eventData.toString());
        if (text.length() > 0) {
            answer.append(text);
            callback.onContent(text);
        }
    }

    private static String sendOpenRouter(
            OpenRouterConfig config,
            String userPrompt,
            String model,
            OpenRouterClient.Callback callback,
            Request request) throws Exception {
        HttpURLConnection connection = OpenRouterClient.openConnection(new URL(config.baseUrl), config);
        request.setConnection(connection);
        try {
            connection.setConnectTimeout(config.timeoutMs);
            connection.setReadTimeout(config.timeoutMs);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Authorization", "Bearer " + config.apiKey);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "text/event-stream");
            connection.setRequestProperty("HTTP-Referer", config.referer);
            connection.setRequestProperty("X-Title", config.title);
            byte[] body = openRouterPayload(config, userPrompt, model).getBytes("UTF-8");
            connection.setRequestProperty("Content-Length", String.valueOf(body.length));
            OutputStream output = connection.getOutputStream();
            try {
                output.write(body);
                output.flush();
            } finally {
                output.close();
            }
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                String response = OpenRouterClient.readBody(connection.getErrorStream());
                throw new RuntimeException("OpenRouter HTTP " + status + ": " + shortReason(response));
            }
            return OpenRouterClient.readContentEventStream(connection.getInputStream(), callback);
        } finally {
            request.clearConnection(connection);
            try {
                connection.disconnect();
            } catch (Throwable ignored) {
                // Ignore disconnect errors.
            }
        }
    }

    private static String geminiPayload(OpenRouterConfig config, String userPrompt) {
        StringBuffer json = new StringBuffer();
        json.append('{');
        json.append("\"systemInstruction\":{\"parts\":[{\"text\":");
        json.append(quote(OpenRouterClient.systemPrompt()));
        json.append("}]},");
        json.append("\"contents\":[{\"parts\":[{\"text\":");
        json.append(quote(userPrompt));
        json.append("}]}],");
        json.append("\"generationConfig\":{\"maxOutputTokens\":").append(config.maxTokens).append(',');
        json.append("\"thinkingConfig\":{\"thinkingLevel\":").append(quote(config.geminiThinkingLevel)).append("}}");
        json.append('}');
        return json.toString();
    }

    private static String openRouterPayload(OpenRouterConfig config, String userPrompt, String model) {
        StringBuffer json = new StringBuffer();
        json.append('{');
        appendField(json, "model", model);
        json.append(',');
        json.append("\"provider\":{\"allow_fallbacks\":false}");
        json.append(',');
        json.append("\"reasoning\":{");
        json.append("\"effort\":").append(quote(config.reasoningEffort));
        json.append(',');
        json.append("\"exclude\":").append(config.reasoningExclude ? "true" : "false");
        json.append('}');
        json.append(',');
        json.append("\"messages\":[");
        json.append("{\"role\":\"system\",\"content\":");
        json.append(quote(OpenRouterClient.systemPrompt()));
        json.append("},");
        json.append("{\"role\":\"user\",\"content\":");
        json.append(quote(userPrompt));
        json.append("}");
        json.append(']');
        json.append(',');
        json.append("\"temperature\":").append(config.temperature);
        json.append(',');
        json.append("\"max_tokens\":").append(config.maxTokens);
        json.append(',');
        json.append("\"stream\":true");
        json.append('}');
        return json.toString();
    }

    private static String geminiText(String response) throws Exception {
        Object parsed = parseJson(response);
        Map root = mapValue(parsed);
        if (root == null) {
            return "";
        }
        Object error = root.get("error");
        if (error instanceof Map) {
            throw new RuntimeException("Gemini error: "
                    + stringValue(((Map) error).get("message")));
        }
        Object candidatesObject = root.get("candidates");
        if (!(candidatesObject instanceof List) || ((List) candidatesObject).isEmpty()) {
            return "";
        }
        Map candidate = mapValue(((List) candidatesObject).get(0));
        if (candidate == null) {
            return "";
        }
        Map content = mapValue(candidate.get("content"));
        if (content == null) {
            return "";
        }
        Object partsObject = content.get("parts");
        if (!(partsObject instanceof List)) {
            return "";
        }
        StringBuffer out = new StringBuffer();
        List parts = (List) partsObject;
        for (int i = 0; i < parts.size(); i++) {
            Map part = mapValue(parts.get(i));
            if (part == null) {
                continue;
            }
            if (Boolean.TRUE.equals(part.get("thought"))) {
                continue;
            }
            String text = stringValue(part.get("text"));
            if (text.length() > 0) {
                out.append(text);
            }
        }
        return out.toString();
    }

    private static String openRouterText(String response) throws Exception {
        Object parsed = parseJson(response);
        Map root = mapValue(parsed);
        if (root == null) {
            return "";
        }
        Object error = root.get("error");
        if (error instanceof Map) {
            throw new RuntimeException("OpenRouter error: " + stringValue(((Map) error).get("message")));
        }
        Object choicesObject = root.get("choices");
        if (!(choicesObject instanceof List) || ((List) choicesObject).isEmpty()) {
            return "";
        }
        Map choice = mapValue(((List) choicesObject).get(0));
        if (choice == null) {
            return "";
        }
        Map message = mapValue(choice.get("message"));
        return message == null ? "" : stringValue(message.get("content"));
    }

    private static String[] geminiKeys(OpenRouterConfig config) {
        String source = config.geminiApiKeys;
        if (source == null || source.trim().length() == 0) {
            source = config.geminiApiKey;
        }
        if (source == null) {
            return new String[0];
        }
        String[] parts = source.split(",");
        int count = 0;
        for (int i = 0; i < parts.length; i++) {
            if (parts[i] != null && parts[i].trim().length() > 0) {
                count++;
            }
        }
        String[] keys = new String[count];
        int out = 0;
        for (int i = 0; i < parts.length; i++) {
            String key = parts[i] == null ? "" : parts[i].trim();
            if (key.length() > 0) {
                keys[out++] = key;
            }
        }
        return keys;
    }

    private static String firstGeminiModel(OpenRouterConfig config) {
        String source = config.geminiModels;
        if (source == null || source.trim().length() == 0) {
            return "gemini-3.6-flash";
        }
        String[] parts = source.split(",");
        return parts.length == 0 ? "gemini-3.6-flash" : parts[0].trim();
    }

    private static long retryDelay(long[] delays, int index) {
        return delays[Math.min(Math.max(index, 0), delays.length - 1)];
    }

    private static boolean sleep(long delayMs, Request request) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ignored) {
            // Cancellation interrupts retry waits.
        }
        return !request.isCancelled();
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

    private static String shortReason(String reason) {
        if (reason == null || reason.length() == 0) {
            return "unknown error";
        }
        String clean = reason.replace('\n', ' ').replace('\r', ' ').trim();
        if (clean.length() > 140) {
            return clean.substring(0, 140) + "...";
        }
        return clean;
    }

    private static boolean isQuotaLimited(int status, String body) {
        if (status == 429) {
            return true;
        }
        if (body == null) {
            return false;
        }
        String lower = body.toLowerCase();
        return lower.indexOf("resource_exhausted") >= 0
                || lower.indexOf("quota") >= 0
                || lower.indexOf("exceeded your current quota") >= 0
                || lower.indexOf("generate_content_free_tier_requests") >= 0
                || lower.indexOf("toomanyrequests") >= 0
                || lower.indexOf("rate limit") >= 0;
    }

    private static void appendField(StringBuffer json, String key, String value) {
        json.append('"').append(key).append('"').append(':').append(quote(value));
    }

    private static String quote(String value) {
        if (value == null) {
            return "\"\"";
        }
        StringBuffer out = new StringBuffer(value.length() + 16);
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\b':
                    out.append("\\b");
                    break;
                case '\f':
                    out.append("\\f");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                default:
                    if (c < 32) {
                        String hex = Integer.toHexString(c);
                        out.append("\\u");
                        for (int j = hex.length(); j < 4; j++) {
                            out.append('0');
                        }
                        out.append(hex);
                    } else {
                        out.append(c);
                    }
                    break;
            }
        }
        out.append('"');
        return out.toString();
    }

    private static Object parseJson(String value) throws Exception {
        Class jsonValue = Class.forName("org.json.simple.JSONValue");
        Method parse = jsonValue.getMethod("parse", new Class[] { String.class });
        return parse.invoke(null, new Object[] { value });
    }

    private static Map mapValue(Object value) {
        return value instanceof Map ? (Map) value : null;
    }

    private static String stringValue(Object value) {
        return value instanceof String ? (String) value : "";
    }

    private static final class GeminiProviderException extends Exception {
        final boolean quotaLimited;

        GeminiProviderException(String message, boolean quotaLimited) {
            super(message);
            this.quotaLimited = quotaLimited;
        }
    }

    private static final class GeminiQuotaState {
        private final OpenRouterConfig config;
        private final Properties properties;

        private GeminiQuotaState(OpenRouterConfig config, Properties properties) {
            this.config = config;
            this.properties = properties;
        }

        static GeminiQuotaState load(OpenRouterConfig config) {
            Properties properties = new Properties();
            FileInputStream input = null;
            try {
                File file = new File(QUOTA_STATE_PATH);
                if (file.exists()) {
                    input = new FileInputStream(file);
                    properties.load(input);
                }
            } catch (Throwable t) {
                AiAssistantLog.write("Gemini quota state load failed: " + t.toString());
            } finally {
                if (input != null) {
                    try {
                        input.close();
                    } catch (Throwable ignored) {
                        // Ignore close errors.
                    }
                }
            }
            return new GeminiQuotaState(config, properties);
        }

        int selectAvailableKey(String[] keys) {
            long now = System.currentTimeMillis();
            for (int i = 0; i < keys.length; i++) {
                normalizeKey(i, keys[i], now);
                if (now < cooldownUntil(i)) {
                    continue;
                }
                if (successCount(i, now) >= config.geminiDailyRequestLimit) {
                    continue;
                }
                return i;
            }
            save();
            return -1;
        }

        void recordSuccess(int index) {
            long now = System.currentTimeMillis();
            String key = successesKey(index);
            String current = properties.getProperty(key, "");
            String updated = pruneSuccesses(current, now);
            if (updated.length() > 0) {
                updated = updated + "," + now;
            } else {
                updated = String.valueOf(now);
            }
            properties.setProperty(key, updated);
            save();
        }

        void recordQuotaLimited(int index) {
            long until = System.currentTimeMillis() + config.geminiQuotaCooldownMs;
            properties.setProperty(cooldownKey(index), String.valueOf(until));
            save();
        }

        private void normalizeKey(int index, String key, long now) {
            String hash = hash(key);
            String hashKey = hashKey(index);
            String previous = properties.getProperty(hashKey, "");
            if (!hash.equals(previous)) {
                properties.setProperty(hashKey, hash);
                properties.remove(successesKey(index));
                properties.remove(cooldownKey(index));
            } else {
                properties.setProperty(successesKey(index),
                        pruneSuccesses(properties.getProperty(successesKey(index), ""), now));
            }
        }

        private int successCount(int index, long now) {
            String pruned = pruneSuccesses(properties.getProperty(successesKey(index), ""), now);
            if (pruned.length() == 0) {
                return 0;
            }
            return pruned.split(",").length;
        }

        private long cooldownUntil(int index) {
            try {
                return Long.parseLong(properties.getProperty(cooldownKey(index), "0"));
            } catch (Throwable ignored) {
                return 0L;
            }
        }

        private String pruneSuccesses(String value, long now) {
            if (value == null || value.trim().length() == 0) {
                return "";
            }
            long cutoff = now - config.geminiQuotaWindowMs;
            String[] parts = value.split(",");
            StringBuffer out = new StringBuffer();
            for (int i = 0; i < parts.length; i++) {
                try {
                    long stamp = Long.parseLong(parts[i].trim());
                    if (stamp >= cutoff) {
                        if (out.length() > 0) {
                            out.append(',');
                        }
                        out.append(stamp);
                    }
                } catch (Throwable ignored) {
                    // Drop malformed entries.
                }
            }
            return out.toString();
        }

        private void save() {
            FileOutputStream output = null;
            try {
                output = new FileOutputStream(QUOTA_STATE_PATH);
                properties.store(output, "Gemini quota state. Stores key hashes and successful request timestamps only.");
                output.flush();
            } catch (Throwable t) {
                AiAssistantLog.write("Gemini quota state save failed: " + t.toString());
            } finally {
                if (output != null) {
                    try {
                        output.close();
                    } catch (Throwable ignored) {
                        // Ignore close errors.
                    }
                }
            }
        }

        private String hashKey(int index) {
            return "key." + index + ".hash";
        }

        private String successesKey(int index) {
            return "key." + index + ".successes";
        }

        private String cooldownKey(int index) {
            return "key." + index + ".cooldown_until";
        }

        private String hash(String key) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] bytes = digest.digest(String.valueOf(key).getBytes("UTF-8"));
                StringBuffer out = new StringBuffer();
                for (int i = 0; i < 8 && i < bytes.length; i++) {
                    String hex = Integer.toHexString(bytes[i] & 0xff);
                    if (hex.length() == 1) {
                        out.append('0');
                    }
                    out.append(hex);
                }
                return out.toString();
            } catch (Throwable ignored) {
                return String.valueOf(key == null ? 0 : key.hashCode());
            }
        }
    }
}
