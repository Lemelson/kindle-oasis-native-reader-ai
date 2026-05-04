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
    private static final int MAX_ATTEMPTS = 6;
    private static final long[] RETRY_DELAYS_MS = new long[] {
            500L, 500L, 500L, 2000L, 5000L
    };

    interface Callback {
        void onStatus(String message);
        void onSuccess(String text);
        void onError(String message);
    }

    private OpenRouterClient() { }

    static void send(ReaderSDK sdk, OpenRouterConfig config, String userPrompt, Callback callback) {
        AiAssistantLog.writeLastPrompt(config.model, userPrompt);
        sendAttempt(sdk, config, userPrompt, callback, 0);
    }

    private static void sendAttempt(
            ReaderSDK sdk,
            OpenRouterConfig config,
            String userPrompt,
            Callback callback,
            int attempt) {
        try {
            Map headers = new HashMap();
            headers.put("Authorization", "Bearer " + config.apiKey);
            headers.put("Content-Type", "application/json");
            headers.put("Accept", "application/json");
            headers.put("HTTP-Referer", config.referer);
            headers.put("X-Title", config.title);

            String payload = buildPayload(config, userPrompt);
            AiAssistantLog.write("sending OpenRouter request model=" + config.model
                    + " attempt=" + (attempt + 1));
            callback.onStatus("OpenRouter request: attempt " + (attempt + 1)
                    + "/" + MAX_ATTEMPTS + ".");

            HttpRequest request = new HttpRequestBuilder()
                    .pA(config.baseUrl)
                    .pB(payload)
                    .al(headers)
                    .hr(config.timeoutMs)
                    .hs(0)
                    .fY(false)
                    .fZ(true)
                    .a(new AiHttpCallback(sdk, config, userPrompt, callback, attempt),
                            "KINDLEAPP:com.lab126.reader.AIAssistant");
            request.aKa();
        } catch (Throwable t) {
            AiAssistantLog.write("request setup failed: " + t.toString());
            if (scheduleRetry(sdk, config, userPrompt, callback, attempt, t.toString())) {
                return;
            }
            AiAssistantLog.writeLastError(t.toString());
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
        json.append(quote(systemPrompt()));
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

    private static String systemPrompt() {
        return "You are a spoiler-safe reading assistant running on a Kindle.\n\n"
                + "The user is a Russian-speaking reader, often reading English books to improve English. "
                + "Always answer in Russian.\n\n"
                + "Your job is to explain the selected fragment, not the whole context. Use the book "
                + "metadata and surrounding context to infer the text type, situation, speaker, argument, "
                + "tone, and likely reason the user selected the fragment.\n\n"
                + "Think adaptively:\n"
                + "- If this is fiction, drama, memoir, narrative biography, mystery, or a classic novel, "
                + "explain scene meaning, speaker intent, subtext, relationship, mood, conflict, or earlier "
                + "relevant context.\n"
                + "- If this is philosophy, theory, or a dense essay, simplify the idea, define key terms, "
                + "reconstruct the logical move, and give a simple example if useful.\n"
                + "- If this is popular nonfiction, business, history, psychology, or biography, explain "
                + "the claim, concept, person, event, evidence, implication, or why it matters here.\n"
                + "- If the selected fragment is difficult because of language, explain idiom, syntax, "
                + "metaphor, tone, register, irony, implied meaning, or pronoun/reference structure.\n\n"
                + "Spoiler policy:\n"
                + "Assume the user has read BEFORE_CONTEXT and SELECTED_FRAGMENT. AFTER_CONTEXT may be unread. "
                + "For plot-driven or chronological texts, never reveal, hint at, or rely on future events, "
                + "twists, character fates, endings, later scenes, or later explanations unless they are "
                + "already present in BEFORE_CONTEXT or SELECTED_FRAGMENT. Outside knowledge is allowed for "
                + "language, public background, historical/philosophical context, and general author ideas, "
                + "but not for future plot spoilers.\n\n"
                + "Style:\n"
                + "Be concise, accurate, and useful. Start with the direct answer. Use simple Russian. "
                + "Do not sound like an academic essay unless the selected mode is Deep. If uncertain, say "
                + "\"по контексту, вероятно...\" instead of inventing. Do not show your internal reasoning.";
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

    private static boolean scheduleRetry(
            final ReaderSDK sdk,
            final OpenRouterConfig config,
            final String userPrompt,
            final Callback callback,
            final int attempt,
            String reason) {
        if (attempt + 1 >= MAX_ATTEMPTS) {
            return false;
        }

        final int nextAttempt = attempt + 1;
        final long delayMs = RETRY_DELAYS_MS[Math.min(attempt, RETRY_DELAYS_MS.length - 1)];
        String status = "Attempt " + (attempt + 1) + "/" + MAX_ATTEMPTS
                + " failed: " + shortReason(reason) + "\nRetry " + (nextAttempt + 1)
                + "/" + MAX_ATTEMPTS + " in " + formatDelay(delayMs) + ".";
        AiAssistantLog.write("OpenRouter retrying after attempt " + (attempt + 1)
                + ": " + shortReason(reason));
        callback.onStatus(status);

        Thread retryThread = new Thread(new Runnable() {
            public void run() {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ignored) {
                    // Continue with the retry.
                }
                sendAttempt(sdk, config, userPrompt, callback, nextAttempt);
            }
        }, "AIAssistantOpenRouterRetry");
        try {
            retryThread.setDaemon(true);
        } catch (Throwable ignored) {
            // Some Kindle runtimes may not care about daemon status.
        }
        retryThread.start();
        return true;
    }

    private static String formatDelay(long delayMs) {
        if (delayMs == 500L) {
            return "0.5s";
        }
        return String.valueOf(delayMs / 1000L) + "s";
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

    private static final class AiHttpCallback implements HttpRequestCallback {
        private final ReaderSDK sdk;
        private final OpenRouterConfig config;
        private final String userPrompt;
        private final Callback callback;
        private final int attempt;

        AiHttpCallback(
                ReaderSDK sdk,
                OpenRouterConfig config,
                String userPrompt,
                Callback callback,
                int attempt) {
            this.sdk = sdk;
            this.config = config;
            this.userPrompt = userPrompt;
            this.callback = callback;
            this.attempt = attempt;
        }

        public void jg(String response) {
            AiAssistantLog.write("OpenRouter response received length="
                    + (response == null ? 0 : response.length()));
            AiAssistantLog.writeLastResponse(response);
            ResponseResult result = parseResponse(response);
            if (result.error != null && result.error.length() > 0) {
                if (retry("OpenRouter response error: " + result.error)) {
                    return;
                }
                AiAssistantLog.writeLastError(result.error);
                callback.onError(result.error);
            } else if (result.text == null || result.text.length() == 0) {
                if (retry("Empty response from OpenRouter.")) {
                    return;
                }
                AiAssistantLog.writeLastError("Empty response from OpenRouter.");
                callback.onError("Empty response from OpenRouter.");
            } else {
                callback.onSuccess(result.text);
            }
        }

        public void b(Exception exception) {
            String message = exception == null ? "network error" : exception.toString();
            AiAssistantLog.write("OpenRouter request exception=" + message);
            if (retry(message)) {
                return;
            }
            AiAssistantLog.writeLastError(message);
            callback.onError(message);
        }

        public void abl() {
            AiAssistantLog.write("OpenRouter request timeout");
            if (retry("Request timed out.")) {
                return;
            }
            AiAssistantLog.writeLastError("Request timed out. Check Wi-Fi and OpenRouter availability.");
            callback.onError("Request timed out. Check Wi-Fi and OpenRouter availability.");
        }

        public void aau() {
            AiAssistantLog.write("OpenRouter request retry");
        }

        private boolean retry(String reason) {
            return scheduleRetry(sdk, config, userPrompt, callback, attempt, reason);
        }
    }

    private static final class ResponseResult {
        String text = "";
        String error = "";
    }

    private static ResponseResult parseResponse(String response) {
        ResponseResult result = new ResponseResult();
        try {
            Class jsonValue = Class.forName("org.json.simple.JSONValue");
            Method parse = jsonValue.getMethod("parse", new Class[] { String.class });
            Object root = parse.invoke(null, new Object[] { response });
            if (!(root instanceof Map)) {
                return result;
            }
            Map map = (Map) root;
            Object error = map.get("error");
            if (error instanceof Map) {
                Object message = ((Map) error).get("message");
                result.error = "OpenRouter error: " + String.valueOf(message);
                return result;
            }
            Object choicesObject = map.get("choices");
            if (!(choicesObject instanceof List) || ((List) choicesObject).isEmpty()) {
                return result;
            }
            Object choiceObject = ((List) choicesObject).get(0);
            if (!(choiceObject instanceof Map)) {
                return result;
            }
            Object messageObject = ((Map) choiceObject).get("message");
            if (!(messageObject instanceof Map)) {
                return result;
            }
            Object content = ((Map) messageObject).get("content");
            if (content instanceof String) {
                result.text = ((String) content).trim();
            } else if (content != null) {
                result.text = String.valueOf(content).trim();
            }
        } catch (Throwable t) {
            AiAssistantLog.write("response parse failed: " + t.toString());
            result.error = "Response parse failed: " + t.toString();
        }
        return result;
    }
}
