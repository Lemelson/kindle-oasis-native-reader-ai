package com.amazon.ebook.booklet.reader.plugin.notesandmarks;

import com.amazon.ebook.booklet.reader.sdk.ReaderSDK;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;

final class OpenRouterClient {
    private static final int MAX_ATTEMPTS = 6;
    private static final int SNAPSHOT_LIMIT = 60000;
    private static final long[] RETRY_DELAYS_MS = new long[] {
            500L, 500L, 500L, 2000L, 5000L
    };

    interface Callback {
        void onStatus(String message);
        void onThinking(String text);
        void onContent(String text);
        void onSuccess(String text);
        void onError(String message);
    }

    private OpenRouterClient() { }

    static void send(ReaderSDK sdk, OpenRouterConfig config, String userPrompt, Callback callback) {
        AiAssistantLog.writeLastPrompt(config.model, userPrompt);
        sendAttempt(sdk, config, userPrompt, callback, 0, 0L);
    }

    private static void sendAttempt(
            final ReaderSDK sdk,
            final OpenRouterConfig config,
            final String userPrompt,
            final Callback callback,
            final int attempt,
            final long delayMs) {
        Thread requestThread = new Thread(new Runnable() {
            public void run() {
                if (delayMs > 0L) {
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ignored) {
                        // Continue with the retry.
                    }
                }
                performStreamingRequest(sdk, config, userPrompt, callback, attempt);
            }
        }, "AIAssistantOpenRouterStream");
        try {
            requestThread.setDaemon(true);
        } catch (Throwable ignored) {
            // Some Kindle runtimes may not care about daemon status.
        }
        requestThread.start();
    }

    private static void performStreamingRequest(
            ReaderSDK sdk,
            OpenRouterConfig config,
            String userPrompt,
            Callback callback,
            int attempt) {
        HttpURLConnection connection = null;
        try {
            String payload = buildPayload(config, userPrompt);
            AiAssistantLog.write("streaming OpenRouter request model=" + config.model
                    + " attempt=" + (attempt + 1));
            callback.onStatus("OpenRouter request: attempt " + (attempt + 1)
                    + "/" + MAX_ATTEMPTS + ".\n\nThinking:");

            URL url = new URL(config.baseUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(config.timeoutMs);
            connection.setReadTimeout(config.timeoutMs);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Authorization", "Bearer " + config.apiKey);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "text/event-stream");
            connection.setRequestProperty("HTTP-Referer", config.referer);
            connection.setRequestProperty("X-Title", config.title);

            OutputStream output = null;
            try {
                byte[] body = payload.getBytes("UTF-8");
                connection.setRequestProperty("Content-Length", String.valueOf(body.length));
                output = connection.getOutputStream();
                output.write(body);
                output.flush();
            } finally {
                if (output != null) {
                    try {
                        output.close();
                    } catch (Throwable ignored) {
                        // Ignore close errors.
                    }
                }
            }

            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                String errorBody = readBody(connection.getErrorStream());
                String message = "HTTP " + status + ": " + shortReason(errorBody);
                if (scheduleRetry(sdk, config, userPrompt, callback, attempt, message)) {
                    return;
                }
                AiAssistantLog.writeLastError(message);
                callback.onError(message);
                return;
            }

            StreamResult result = readEventStream(connection.getInputStream(), callback);
            AiAssistantLog.writeLastResponse(result.snapshot());
            if (result.error != null && result.error.length() > 0) {
                if (scheduleRetry(sdk, config, userPrompt, callback, attempt, result.error)) {
                    return;
                }
                AiAssistantLog.writeLastError(result.error);
                callback.onError(result.error);
            } else if (result.content.length() == 0) {
                String message = "Empty final response from OpenRouter stream.";
                if (scheduleRetry(sdk, config, userPrompt, callback, attempt, message)) {
                    return;
                }
                AiAssistantLog.writeLastError(message);
                callback.onError(message);
            } else {
                callback.onSuccess(result.content.toString());
            }
        } catch (Throwable t) {
            String message = t.toString();
            AiAssistantLog.write("streaming request failed: " + message);
            if (scheduleRetry(sdk, config, userPrompt, callback, attempt, message)) {
                return;
            }
            AiAssistantLog.writeLastError(message);
            callback.onError(message);
        } finally {
            if (connection != null) {
                try {
                    connection.disconnect();
                } catch (Throwable ignored) {
                    // Ignore disconnect errors.
                }
            }
        }
    }

    private static StreamResult readEventStream(InputStream input, Callback callback) throws Exception {
        StreamResult result = new StreamResult();
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                appendSnapshot(result.raw, line);
                if (!line.startsWith("data: ")) {
                    continue;
                }

                String data = line.substring(6);
                if ("[DONE]".equals(data)) {
                    break;
                }

                Object parsed = parseJson(data);
                if (!(parsed instanceof Map)) {
                    continue;
                }

                Map root = (Map) parsed;
                Object error = root.get("error");
                if (error instanceof Map) {
                    Object message = ((Map) error).get("message");
                    result.error = "OpenRouter error: " + String.valueOf(message);
                    break;
                }

                Map choice = firstChoice(root);
                if (choice == null) {
                    continue;
                }

                Map delta = mapValue(choice.get("delta"));
                Map message = mapValue(choice.get("message"));
                Map source = delta == null ? message : delta;
                if (source == null) {
                    continue;
                }

                String reasoning = stringValue(source.get("reasoning"));
                if (reasoning.length() == 0) {
                    reasoning = reasoningDetailsText(source.get("reasoning_details"));
                }
                if (reasoning.length() > 0) {
                    result.reasoning.append(reasoning);
                    callback.onThinking(reasoning);
                }

                String content = stringValue(source.get("content"));
                if (content.length() > 0) {
                    result.content.append(content);
                    callback.onContent(content);
                }
            }
        } finally {
            try {
                reader.close();
            } catch (Throwable ignored) {
                // Ignore close errors.
            }
        }
        AiAssistantLog.write("stream complete content=" + result.content.length()
                + " reasoning=" + result.reasoning.length());
        return result;
    }

    private static String buildPayload(OpenRouterConfig config, String userPrompt) {
        StringBuffer json = new StringBuffer();
        json.append('{');
        appendField(json, "model", config.model);
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
        json.append("\"stream\":true");
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
                + "Use plain text only: no Markdown emphasis, no bold, no italics, no asterisks or "
                + "underscores for formatting; hyphen bullets are allowed. "
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
            ReaderSDK sdk,
            OpenRouterConfig config,
            String userPrompt,
            Callback callback,
            int attempt,
            String reason) {
        if (attempt + 1 >= MAX_ATTEMPTS) {
            return false;
        }

        int nextAttempt = attempt + 1;
        long delayMs = RETRY_DELAYS_MS[Math.min(attempt, RETRY_DELAYS_MS.length - 1)];
        String status = "Attempt " + (attempt + 1) + "/" + MAX_ATTEMPTS
                + " failed: " + shortReason(reason) + "\nRetry " + (nextAttempt + 1)
                + "/" + MAX_ATTEMPTS + " in " + formatDelay(delayMs) + ".";
        AiAssistantLog.write("OpenRouter retrying after attempt " + (attempt + 1)
                + ": " + shortReason(reason));
        callback.onStatus(status);
        sendAttempt(sdk, config, userPrompt, callback, nextAttempt, delayMs);
        return true;
    }

    private static String readBody(InputStream input) {
        if (input == null) {
            return "";
        }
        StringBuffer out = new StringBuffer();
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(input, "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null && out.length() < SNAPSHOT_LIMIT) {
                out.append(line).append('\n');
            }
        } catch (Throwable t) {
            return t.toString();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Throwable ignored) {
                    // Ignore close errors.
                }
            }
        }
        return out.toString();
    }

    private static void appendSnapshot(StringBuffer out, String line) {
        if (out.length() >= SNAPSHOT_LIMIT) {
            return;
        }
        out.append(line).append('\n');
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

    private static Object parseJson(String value) throws Exception {
        Class jsonValue = Class.forName("org.json.simple.JSONValue");
        Method parse = jsonValue.getMethod("parse", new Class[] { String.class });
        return parse.invoke(null, new Object[] { value });
    }

    private static Map firstChoice(Map root) {
        Object choicesObject = root.get("choices");
        if (!(choicesObject instanceof List) || ((List) choicesObject).isEmpty()) {
            return null;
        }
        Object choice = ((List) choicesObject).get(0);
        return mapValue(choice);
    }

    private static Map mapValue(Object value) {
        return value instanceof Map ? (Map) value : null;
    }

    private static String stringValue(Object value) {
        return value instanceof String ? (String) value : "";
    }

    private static String reasoningDetailsText(Object value) {
        if (!(value instanceof List)) {
            return "";
        }
        List details = (List) value;
        StringBuffer out = new StringBuffer();
        for (int i = 0; i < details.size(); i++) {
            Object item = details.get(i);
            if (!(item instanceof Map)) {
                continue;
            }
            Object text = ((Map) item).get("text");
            if (text instanceof String) {
                out.append((String) text);
            }
        }
        return out.toString();
    }

    private static final class StreamResult {
        final StringBuffer raw = new StringBuffer();
        final StringBuffer reasoning = new StringBuffer();
        final StringBuffer content = new StringBuffer();
        String error = "";

        String snapshot() {
            StringBuffer out = new StringBuffer();
            out.append("content_length=").append(content.length()).append('\n');
            out.append("reasoning_length=").append(reasoning.length()).append('\n');
            out.append("\nCONTENT:\n").append(content).append('\n');
            out.append("\nREASONING_PREVIEW:\n");
            if (reasoning.length() > SNAPSHOT_LIMIT) {
                out.append(reasoning.substring(0, SNAPSHOT_LIMIT)).append("\n[...]");
            } else {
                out.append(reasoning);
            }
            out.append("\n\nRAW_SSE_PREVIEW:\n").append(raw);
            return out.toString();
        }
    }
}
