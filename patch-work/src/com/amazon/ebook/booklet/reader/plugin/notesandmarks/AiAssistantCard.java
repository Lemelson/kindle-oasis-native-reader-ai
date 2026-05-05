package com.amazon.ebook.booklet.reader.plugin.notesandmarks;

import com.amazon.ebook.booklet.reader.sdk.ReaderSDK;
import com.amazon.ebook.booklet.reader.sdk.ui.SelectionDialogCard;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.SwingConstants;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

final class AiAssistantCard extends SelectionDialogCard {
    private static final int OUTPUT_FONT_SIZE = 8;
    private static final int TITLE_FONT_SIZE = 12;
    private static final int BUTTON_FONT_SIZE = 11;
    private static final int CLOSE_BUTTON_HEIGHT = 34;
    private static final int THINKING_DISPLAY_LIMIT = 12000;
    private static final long STREAM_UPDATE_INTERVAL_MS = 1500L;
    private static final Map RESPONSE_CACHE = new HashMap();

    private final ReaderSDK sdk;
    private final AiAssistantController.SelectionData selection;
    private final String modeName;
    private final String cacheKey;
    private JTextPane output;
    private int requestSerial = 0;

    AiAssistantCard(ReaderSDK sdk, AiAssistantController.SelectionData selection, String modeName) {
        this.sdk = sdk;
        this.selection = selection;
        this.modeName = modeName == null ? "Explain" : modeName;
        this.cacheKey = buildCacheKey(this.modeName, selection);
        buildUi();
        start();
    }

    public String anL() {
        return "AI " + modeName;
    }

    public boolean anM() {
        return true;
    }

    public int getPriority() {
        return 1000;
    }

    public String anN() {
        return "AI_ASSISTANT_CARD";
    }

    public String asQ() {
        return "AIAssistantCard";
    }

    public String anQ() {
        return "AI_ASSISTANT";
    }

    private void buildUi() {
        setLayout(new BorderLayout(4, 4));
        setOpaque(true);
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createEmptyBorder(4, 5, 4, 5));

        JLabel title = new JLabel("AI " + modeName, SwingConstants.LEFT);
        title.setOpaque(true);
        title.setBackground(Color.WHITE);
        title.setFont(new Font("SansSerif", Font.BOLD, TITLE_FONT_SIZE));
        add(title, BorderLayout.NORTH);

        output = new WrappingTextPane();
        output.setEditable(false);
        output.setFont(new Font("SansSerif", Font.PLAIN, OUTPUT_FONT_SIZE));
        output.setOpaque(true);
        output.setBackground(Color.WHITE);
        add(new JScrollPane(output), BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(true);
        footer.setBackground(Color.WHITE);
        JButton close = new JButton("Close");
        close.setFont(new Font("SansSerif", Font.PLAIN, BUTTON_FONT_SIZE));
        close.setMargin(new Insets(2, 8, 2, 8));
        close.setPreferredSize(new Dimension(10, CLOSE_BUTTON_HEIGHT));
        close.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                closeDialog();
            }
        });
        footer.add(close, BorderLayout.EAST);
        add(footer, BorderLayout.SOUTH);
    }

    private void start() {
        String cached = (String) RESPONSE_CACHE.get(cacheKey);
        if (cached != null) {
            setMarkdownOutput(cached);
            return;
        }
        runPrompt(modeName, AiAssistantController.presetPrompt(modeName));
    }

    private void runPrompt(final String name, String instruction) {
        final int requestId = nextRequestId();
        try {
            AiAssistantLog.write("prompt start name=" + name);
            OpenRouterConfig config = OpenRouterConfig.load();
            if (!config.isUsable()) {
                setPlainOutput("OpenRouter config missing.\nCheck /mnt/us/native-reader-research/ai-assistant.properties");
                return;
            }

            setPlainOutput("Thinking:");
            String userPrompt = AiAssistantController.buildUserPrompt(name, instruction, selection);
            final StringBuffer thinking = new StringBuffer();
            final StringBuffer content = new StringBuffer();
            final long[] lastThinkingUpdateMs = new long[] { 0L };
            final long[] lastContentUpdateMs = new long[] { 0L };
            OpenRouterClient.send(sdk, config, userPrompt, new OpenRouterClient.Callback() {
                public void onStatus(final String message) {
                    sdk.invokeLater(new Runnable() {
                        public void run() {
                            if (isLatestRequest(requestId)) {
                                setPlainOutput(message);
                            }
                        }
                    });
                }

                public void onThinking(final String text) {
                    sdk.invokeLater(new Runnable() {
                        public void run() {
                            if (!isLatestRequest(requestId) || content.length() > 0) {
                                return;
                            }
                            thinking.append(text);
                            if (shouldUpdateStream(lastThinkingUpdateMs)) {
                                setPlainOutput("Thinking:\n" + displayTail(thinking.toString()));
                            }
                        }
                    });
                }

                public void onContent(final String text) {
                    sdk.invokeLater(new Runnable() {
                        public void run() {
                            if (!isLatestRequest(requestId)) {
                                return;
                            }
                            content.append(text);
                            if (shouldUpdateStream(lastContentUpdateMs)) {
                                setPlainOutput(content.toString());
                            }
                        }
                    });
                }

                public void onSuccess(final String text) {
                    AiAssistantLog.write("prompt success name=" + name
                            + " length=" + (text == null ? 0 : text.length()));
                    sdk.invokeLater(new Runnable() {
                        public void run() {
                            if (!isLatestRequest(requestId)) {
                                return;
                            }
                            String clean = text == null ? "" : text;
                            RESPONSE_CACHE.put(cacheKey, clean);
                            setMarkdownOutput(clean);
                        }
                    });
                }

                public void onError(final String message) {
                    AiAssistantLog.write("prompt error name=" + name + " message=" + message);
                    sdk.invokeLater(new Runnable() {
                        public void run() {
                            if (isLatestRequest(requestId)) {
                                setPlainOutput("OpenRouter error after retries:\n" + message);
                            }
                        }
                    });
                }
            });
        } catch (Throwable t) {
            AiAssistantLog.write("prompt failed name=" + name + " error=" + t.toString());
            setPlainOutput("AI failed:\n" + t.toString());
        }
    }

    private static boolean shouldUpdateStream(long[] lastUpdateMs) {
        long now = System.currentTimeMillis();
        if (lastUpdateMs[0] == 0L || now - lastUpdateMs[0] >= STREAM_UPDATE_INTERVAL_MS) {
            lastUpdateMs[0] = now;
            return true;
        }
        return false;
    }

    private int nextRequestId() {
        requestSerial++;
        return requestSerial;
    }

    private boolean isLatestRequest(int requestId) {
        return requestSerial == requestId;
    }

    private static String displayTail(String text) {
        if (text == null || text.length() <= THINKING_DISPLAY_LIMIT) {
            return text == null ? "" : text;
        }
        return "[...]\n" + text.substring(text.length() - THINKING_DISPLAY_LIMIT);
    }

    private void setPlainOutput(String text) {
        StyledDocument doc = output.getStyledDocument();
        try {
            doc.remove(0, doc.getLength());
            doc.insertString(0, text == null ? "" : text, baseAttrs());
            output.setCaretPosition(0);
        } catch (BadLocationException ignored) {
            output.setText(text == null ? "" : text);
        }
        revalidate();
        repaint();
    }

    private void setMarkdownOutput(String text) {
        StyledDocument doc = output.getStyledDocument();
        try {
            doc.remove(0, doc.getLength());
            renderMarkdown(doc, text == null ? "" : text);
            output.setCaretPosition(0);
        } catch (Throwable t) {
            AiAssistantLog.write("markdown render failed: " + t.toString());
            setPlainOutput(text);
            return;
        }
        revalidate();
        repaint();
    }

    private static void renderMarkdown(StyledDocument doc, String text) throws BadLocationException {
        String[] lines = text.replace('\r', '\n').split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            SimpleAttributeSet attrs = baseAttrs();
            String body = line;
            if (line.startsWith("### ")) {
                attrs = headingAttrs(10);
                body = line.substring(4);
            } else if (line.startsWith("## ")) {
                attrs = headingAttrs(11);
                body = line.substring(3);
            } else if (line.startsWith("# ")) {
                attrs = headingAttrs(12);
                body = line.substring(2);
            }
            renderInline(doc, body, attrs);
            if (i + 1 < lines.length) {
                doc.insertString(doc.getLength(), "\n", attrs);
            }
        }
    }

    private static void renderInline(StyledDocument doc, String line, SimpleAttributeSet attrs)
            throws BadLocationException {
        int pos = 0;
        while (pos < line.length()) {
            int marker = nextMarker(line, pos);
            if (marker < 0) {
                doc.insertString(doc.getLength(), line.substring(pos), attrs);
                return;
            }
            if (marker > pos) {
                doc.insertString(doc.getLength(), line.substring(pos, marker), attrs);
            }

            if (line.startsWith("**", marker) || line.startsWith("__", marker)) {
                String token = line.substring(marker, marker + 2);
                int end = line.indexOf(token, marker + 2);
                if (end > marker + 2) {
                    SimpleAttributeSet bold = copyAttrs(attrs);
                    StyleConstants.setBold(bold, true);
                    doc.insertString(doc.getLength(), line.substring(marker + 2, end), bold);
                    pos = end + 2;
                } else {
                    doc.insertString(doc.getLength(), token, attrs);
                    pos = marker + 2;
                }
            } else if (line.charAt(marker) == '`') {
                int end = line.indexOf('`', marker + 1);
                if (end > marker + 1) {
                    SimpleAttributeSet code = copyAttrs(attrs);
                    StyleConstants.setFontFamily(code, "Monospaced");
                    doc.insertString(doc.getLength(), line.substring(marker + 1, end), code);
                    pos = end + 1;
                } else {
                    doc.insertString(doc.getLength(), "`", attrs);
                    pos = marker + 1;
                }
            } else {
                char token = line.charAt(marker);
                int end = line.indexOf(token, marker + 1);
                if (end > marker + 1 && isEmphasisBoundary(line, marker, end)) {
                    SimpleAttributeSet italic = copyAttrs(attrs);
                    StyleConstants.setItalic(italic, true);
                    doc.insertString(doc.getLength(), line.substring(marker + 1, end), italic);
                    pos = end + 1;
                } else {
                    doc.insertString(doc.getLength(), String.valueOf(token), attrs);
                    pos = marker + 1;
                }
            }
        }
    }

    private static int nextMarker(String line, int start) {
        int best = -1;
        char[] markers = new char[] { '*', '_', '`' };
        for (int i = 0; i < markers.length; i++) {
            int index = line.indexOf(markers[i], start);
            if (index >= 0 && (best < 0 || index < best)) {
                best = index;
            }
        }
        return best;
    }

    private static boolean isEmphasisBoundary(String line, int start, int end) {
        if (start + 1 >= end) {
            return false;
        }
        char marker = line.charAt(start);
        if (marker == '_' && start > 0 && Character.isLetterOrDigit(line.charAt(start - 1))) {
            return false;
        }
        if (marker == '_' && end + 1 < line.length() && Character.isLetterOrDigit(line.charAt(end + 1))) {
            return false;
        }
        return true;
    }

    private static SimpleAttributeSet baseAttrs() {
        SimpleAttributeSet attrs = new SimpleAttributeSet();
        StyleConstants.setFontFamily(attrs, "SansSerif");
        StyleConstants.setFontSize(attrs, OUTPUT_FONT_SIZE);
        StyleConstants.setForeground(attrs, Color.BLACK);
        return attrs;
    }

    private static SimpleAttributeSet headingAttrs(int size) {
        SimpleAttributeSet attrs = baseAttrs();
        StyleConstants.setBold(attrs, true);
        StyleConstants.setFontSize(attrs, size);
        return attrs;
    }

    private static SimpleAttributeSet copyAttrs(SimpleAttributeSet attrs) {
        return new SimpleAttributeSet(attrs);
    }

    private static String buildCacheKey(String modeName, AiAssistantController.SelectionData selection) {
        StringBuffer key = new StringBuffer();
        key.append(modeName == null ? "" : modeName).append('|');
        if (selection != null) {
            key.append(selection.startPosition == null ? "" : selection.startPosition).append('|');
            key.append(selection.endPosition == null ? "" : selection.endPosition).append('|');
            key.append(selection.selectedText == null ? 0 : selection.selectedText.hashCode());
        }
        return key.toString();
    }

    private static final class WrappingTextPane extends JTextPane {
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }
    }
}
