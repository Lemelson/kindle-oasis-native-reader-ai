package com.amazon.ebook.booklet.reader.plugin.notesandmarks;

import java.io.ByteArrayInputStream;

public final class AiProviderChainClientStreamTest {
    public static void main(String[] args) throws Exception {
        String sse =
                "data: {\"candidates\":[{\"content\":{\"parts\":["
                + "{\"thought\":true,\"text\":\"hidden reasoning\"},"
                + "{\"text\":\"Первый\"}]}}]}\n\n"
                + "data: {\"candidates\":[{\"content\":{\"parts\":["
                + "{\"text\":\" ответ\"}]}}]}\n\n";
        final StringBuffer streamed = new StringBuffer();
        String answer = AiProviderChainClient.readGeminiEventStream(
                new ByteArrayInputStream(sse.getBytes("UTF-8")),
                new OpenRouterClient.Callback() {
                    public void onStatus(String message) { }
                    public void onThinking(String text) {
                        throw new AssertionError("Gemini thoughts must stay hidden.");
                    }
                    public void onContent(String text) {
                        streamed.append(text);
                    }
                    public void onSuccess(String text) { }
                    public void onError(String message) {
                        throw new AssertionError(message);
                    }
                });
        assertEquals("Первый ответ", answer, "combined answer");
        assertEquals(answer, streamed.toString(), "streamed chunks");
        if (answer.indexOf("hidden reasoning") >= 0) {
            throw new AssertionError("Thought text leaked into final answer.");
        }
        testCancelledGeminiStream();
        testOpenRouterStream();
        testVpnDisabledSession();
        System.out.println("AiProviderChainClientStreamTest OK");
    }

    private static void testCancelledGeminiStream() throws Exception {
        String sse = "data: {\"candidates\":[{\"content\":{\"parts\":["
                + "{\"text\":\"must not be delivered\"}]}}]}\n\n";
        AiProviderChainClient.Request request = new AiProviderChainClient.Request();
        request.cancel();
        final StringBuffer streamed = new StringBuffer();
        String answer = AiProviderChainClient.readGeminiEventStream(
                new ByteArrayInputStream(sse.getBytes("UTF-8")),
                callback(streamed),
                request);
        assertEquals("", answer, "cancelled Gemini answer");
        assertEquals("", streamed.toString(), "cancelled Gemini callback");
    }

    private static void testOpenRouterStream() throws Exception {
        String sse =
                "data: {\"choices\":[{\"delta\":{\"reasoning\":\"thinking\"}}]}\n\n"
                + "data: {\"choices\":[{\"delta\":{\"content\":\"Готово\"}}]}\n\n"
                + "data: [DONE]\n\n";
        final StringBuffer reasoning = new StringBuffer();
        final StringBuffer streamed = new StringBuffer();
        String answer = OpenRouterClient.readContentEventStream(
                new ByteArrayInputStream(sse.getBytes("UTF-8")),
                new OpenRouterClient.Callback() {
                    public void onStatus(String message) { }
                    public void onThinking(String text) {
                        reasoning.append(text);
                    }
                    public void onContent(String text) {
                        streamed.append(text);
                    }
                    public void onSuccess(String text) { }
                    public void onError(String message) {
                        throw new AssertionError(message);
                    }
                });
        assertEquals("Готово", answer, "OpenRouter answer");
        assertEquals(answer, streamed.toString(), "OpenRouter streamed chunks");
        assertEquals("thinking", reasoning.toString(), "OpenRouter reasoning");
    }

    private static void testVpnDisabledSession() throws Exception {
        OpenRouterConfig config = new OpenRouterConfig();
        config.vpnEnabled = false;
        AiVpnSession session = AiVpnSession.start(config);
        session.awaitReady();
        session.close();
    }

    private static OpenRouterClient.Callback callback(final StringBuffer streamed) {
        return new OpenRouterClient.Callback() {
            public void onStatus(String message) { }
            public void onThinking(String text) { }
            public void onContent(String text) {
                streamed.append(text);
            }
            public void onSuccess(String text) { }
            public void onError(String message) {
                throw new AssertionError(message);
            }
        };
    }

    private static void assertEquals(String expected, String actual, String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + ": expected=" + expected + " actual=" + actual);
        }
    }
}
