package com.amazon.ebook.booklet.reader.plugin.notesandmarks;

import com.amazon.ebook.booklet.reader.sdk.ReaderSDK;
import com.amazon.ebook.booklet.reader.utils.http.HttpRequest;
import com.amazon.ebook.booklet.reader.utils.http.HttpRequestBuilder;
import com.amazon.ebook.booklet.reader.utils.http.HttpRequestCallback;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class OpenRouterClient {
    interface Callback {
        void onSuccess(String text);
        void onError(String message);
    }

    private OpenRouterClient() { }

    static void send(ReaderSDK sdk, OpenRouterConfig config, String userPrompt, Callback callback) {
        try {
            Map headers = new HashMap();
            headers.put("Authorization", "Bearer " + config.apiKey);
            headers.put("Content-Type", "application/json");
            headers.put("Accept", "application/json");
            headers.put("HTTP-Referer", config.referer);
            headers.put("X-Title", config.title);

            String payload = buildPayload(config, userPrompt);
            AiAssistantLog.write("sending OpenRouter request model=" + config.model);

            HttpRequest request = new HttpRequestBuilder()
                    .pA(config.baseUrl)
                    .pB(payload)
                    .al(headers)
                    .hr(config.timeoutMs)
                    .hs(0)
                    .fY(false)
                    .fZ(true)
                    .a(new AiHttpCallback(callback), "KINDLEAPP:com.lab126.reader.AIAssistant");
            request.aKa();
        } catch (Throwable t) {
            AiAssistantLog.write("request setup failed: " + t.toString());
            callback.onError(t.toString());
        }
    }

    private static String buildPayload(OpenRouterConfig config, String userPrompt) {
        StringBuffer json = new StringBuffer();
        json.append('{');
        appendField(json, "model", config.model);
        json.append(',');
        json.append("\"provider\":{\"allow_fallbacks\":false}");
        json.append(',');
        json.append("\"messages\":[");
        json.append("{\"role\":\"system\",\"content\":");
        json.append(quote("You are a reading assistant on a Kindle. Always answer in Russian. "
                + "Be concise, accurate, and useful for a Russian-speaking English learner."));
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
        json.append("\"stream\":false");
        json.append('}');
        return json.toString();
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

    private static final class AiHttpCallback implements HttpRequestCallback {
        private final Callback callback;

        AiHttpCallback(Callback callback) {
            this.callback = callback;
        }

        public void jg(String response) {
            String text = parseResponse(response);
            if (text == null || text.length() == 0) {
                callback.onError("Empty response from OpenRouter.");
            } else {
                callback.onSuccess(text);
            }
        }

        public void b(Exception exception) {
            callback.onError(exception == null ? "network error" : exception.toString());
        }

        public void abl() {
            callback.onError("Request timed out. Check Wi-Fi and OpenRouter availability.");
        }

        public void aau() {
            AiAssistantLog.write("OpenRouter request retry");
        }
    }

    private static String parseResponse(String response) {
        try {
            Class jsonValue = Class.forName("org.json.simple.JSONValue");
            Method parse = jsonValue.getMethod("parse", new Class[] { String.class });
            Object root = parse.invoke(null, new Object[] { response });
            if (!(root instanceof Map)) {
                return "";
            }
            Map map = (Map) root;
            Object error = map.get("error");
            if (error instanceof Map) {
                Object message = ((Map) error).get("message");
                return "OpenRouter error: " + String.valueOf(message);
            }
            Object choicesObject = map.get("choices");
            if (!(choicesObject instanceof List) || ((List) choicesObject).isEmpty()) {
                return "";
            }
            Object choiceObject = ((List) choicesObject).get(0);
            if (!(choiceObject instanceof Map)) {
                return "";
            }
            Object messageObject = ((Map) choiceObject).get("message");
            if (!(messageObject instanceof Map)) {
                return "";
            }
            Object content = ((Map) messageObject).get("content");
            if (content instanceof String) {
                return ((String) content).trim();
            }
            if (content != null) {
                return String.valueOf(content).trim();
            }
        } catch (Throwable t) {
            AiAssistantLog.write("response parse failed: " + t.toString());
        }
        return "";
    }
}
