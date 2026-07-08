package com.amazon.ebook.booklet.reader.plugin.notesandmarks;

import com.amazon.ebook.booklet.reader.sdk.ReaderSDK;
import com.amazon.ebook.booklet.reader.sdk.ui.SelectionDialogCard;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.Timer;

final class AiAssistantCard extends SelectionDialogCard {
    private static final int OUTPUT_FONT_SIZE = 8;
    private static final int CONTROL_FONT_SIZE = 12;
    private static final int BUTTON_HEIGHT = 48;
    private static final int THINKING_DISPLAY_LIMIT = 12000;
    private static final int LONG_PRESS_MS = 800;
    private static final long STREAM_UPDATE_INTERVAL_MS = 1500L;

    private final ReaderSDK sdk;
    private final AiAssistantController.SelectionData selection;
    private final Map cachedResponses = new HashMap();
    private final Map statusTexts = new HashMap();
    private final Map requestIds = new HashMap();
    private JTextArea output;
    private String activeMode = "";
    private String activeKey = "";
    private int requestSerial = 0;

    AiAssistantCard(ReaderSDK sdk, AiAssistantController.SelectionData selection) {
        this.sdk = sdk;
        this.selection = selection;
        buildUi();
    }

    public String anL() {
        return "AI";
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

        JPanel presets = new JPanel(new GridLayout(1, 5, 4, 4));
        presets.setOpaque(true);
        presets.setBackground(Color.WHITE);
        presets.setPreferredSize(new Dimension(10, BUTTON_HEIGHT));
        addPreset(presets, "Explain");
        addPreset(presets, "Words");
        addPreset(presets, "Recall");
        addPreset(presets, "Why");
        addPreset(presets, "Deep");
        add(presets, BorderLayout.NORTH);

        output = new JTextArea("");
        output.setEditable(false);
        output.setLineWrap(true);
        output.setWrapStyleWord(true);
        output.setFont(new Font("SansSerif", Font.PLAIN, OUTPUT_FONT_SIZE));
        output.setOpaque(true);
        output.setBackground(Color.WHITE);
        add(new JScrollPane(output), BorderLayout.CENTER);
    }

    private void addPreset(JPanel panel, final String name) {
        JButton button = new JButton(name);
        compactButton(button);
        button.addMouseListener(new MouseAdapter() {
            private Timer longPressTimer;
            private boolean longPressFired;

            public void mousePressed(MouseEvent event) {
                longPressFired = false;
                longPressTimer = new Timer(LONG_PRESS_MS, new ActionListener() {
                    public void actionPerformed(ActionEvent timerEvent) {
                        longPressFired = true;
                        selectPreset(name, true);
                    }
                });
                longPressTimer.setRepeats(false);
                longPressTimer.start();
            }

            public void mouseReleased(MouseEvent event) {
                if (longPressTimer != null && longPressTimer.isRunning()) {
                    longPressTimer.stop();
                }
                longPressTimer = null;
                if (!longPressFired) {
                    selectPreset(name, false);
                }
            }

            public void mouseExited(MouseEvent event) {
                if (longPressTimer != null && longPressTimer.isRunning()) {
                    longPressTimer.stop();
                    longPressTimer = null;
                }
            }
        });
        panel.add(button);
    }

    private void compactButton(JButton button) {
        button.setFont(new Font("SansSerif", Font.PLAIN, CONTROL_FONT_SIZE));
        button.setMargin(new Insets(2, 4, 2, 4));
        button.setPreferredSize(new Dimension(10, BUTTON_HEIGHT));
        button.setMinimumSize(new Dimension(10, BUTTON_HEIGHT));
    }

    private void selectPreset(String name, boolean useProModel) {
        String key = modeKey(name, useProModel);
        boolean sameMode = key.equals(activeKey);
        activeMode = name;
        activeKey = key;
        if (!sameMode) {
            String cached = (String) cachedResponses.get(key);
            if (cached != null) {
                setOutput(cached);
                return;
            }

            String status = (String) statusTexts.get(key);
            if (status != null) {
                setOutput(status);
                return;
            }
        }

        runPrompt(name, AiAssistantController.presetPrompt(name), useProModel);
    }

    private void runPrompt(final String name, String instruction, boolean useProModel) {
        final String key = modeKey(name, useProModel);
        final int requestId = nextRequestId(key);
        try {
            AiAssistantLog.write("prompt start name=" + name
                    + " modelTier=" + (useProModel ? "pro" : "flash"));
            OpenRouterConfig config = OpenRouterConfig.load();
            config.proRequest = useProModel;
            if (useProModel) {
                config.model = config.proModel;
                config.fallbackModel = config.proFallbackModel;
            } else {
                config.fallbackModel = config.proModel;
            }
            if (!config.isUsable()) {
                updateStatus(key, requestId,
                        "OpenRouter config missing.\nCheck /mnt/us/native-reader-research/ai-assistant.properties");
                return;
            }

            updateStatus(key, requestId, statusMessage(useProModel, "Thinking:"));
            String userPrompt = AiAssistantController.buildUserPrompt(name, instruction, selection);
            final StringBuffer thinking = new StringBuffer();
            final StringBuffer content = new StringBuffer();
            final String[] fallbackNotice = new String[] { "" };
            final long[] lastThinkingUpdateMs = new long[] { 0L };
            final long[] lastContentUpdateMs = new long[] { 0L };
            OpenRouterClient.send(sdk, config, userPrompt, new OpenRouterClient.Callback() {
                public void onStatus(final String message) {
                    sdk.invokeLater(new Runnable() {
                        public void run() {
                            String notice = fallbackNotice(message);
                            if (notice.length() > 0) {
                                fallbackNotice[0] = notice;
                            }
                            updateStatus(key, requestId,
                                    statusMessage(useProModel, withNotice(fallbackNotice[0], message)));
                        }
                    });
                }

                public void onThinking(final String text) {
                    sdk.invokeLater(new Runnable() {
                        public void run() {
                            if (!isLatestRequest(key, requestId) || content.length() > 0) {
                                return;
                            }
                            thinking.append(text);
                            if (shouldUpdateStream(lastThinkingUpdateMs)) {
                                updateStatus(key, requestId,
                                        statusMessage(useProModel, withNotice(
                                                fallbackNotice[0],
                                                "Thinking:\n" + displayTail(thinking.toString()))));
                            }
                        }
                    });
                }

                public void onContent(final String text) {
                    sdk.invokeLater(new Runnable() {
                        public void run() {
                            if (!isLatestRequest(key, requestId)) {
                                return;
                            }
                            content.append(text);
                            if (shouldUpdateStream(lastContentUpdateMs)) {
                                updateStatus(key, requestId,
                                        withNotice(fallbackNotice[0], content.toString()));
                            }
                        }
                    });
                }

                public void onSuccess(final String text) {
                    AiAssistantLog.write("prompt success name=" + name
                            + " length=" + (text == null ? 0 : text.length()));
                    sdk.invokeLater(new Runnable() {
                        public void run() {
                            if (!isLatestRequest(key, requestId)) {
                                return;
                            }
                            String clean = withNotice(fallbackNotice[0], text == null ? "" : text);
                            cachedResponses.put(key, clean);
                            statusTexts.remove(key);
                            if (key.equals(activeKey)) {
                                setOutput(clean);
                            }
                        }
                    });
                }

                public void onError(final String message) {
                    AiAssistantLog.write("prompt error name=" + name + " message=" + message);
                    sdk.invokeLater(new Runnable() {
                        public void run() {
                            updateStatus(key, requestId, withNotice(
                                    fallbackNotice[0],
                                    "OpenRouter error after retries:\n" + message));
                        }
                    });
                }
            });
        } catch (Throwable t) {
            AiAssistantLog.write("prompt failed name=" + name + " error=" + t.toString());
            updateStatus(key, requestId, "AI failed:\n" + t.toString());
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

    private int nextRequestId(String key) {
        requestSerial++;
        Integer id = new Integer(requestSerial);
        requestIds.put(key, id);
        cachedResponses.remove(key);
        return id.intValue();
    }

    private boolean isLatestRequest(String key, int requestId) {
        Integer latest = (Integer) requestIds.get(key);
        return latest != null && latest.intValue() == requestId;
    }

    private void updateStatus(String key, int requestId, String message) {
        if (!isLatestRequest(key, requestId)) {
            return;
        }
        String text = String.valueOf(message);
        statusTexts.put(key, text);
        if (key.equals(activeKey)) {
            setOutput(text);
        }
    }

    private static String modeKey(String name, boolean useProModel) {
        return (useProModel ? "pro" : "flash") + "|" + String.valueOf(name);
    }

    private static String statusMessage(boolean useProModel, String message) {
        if (!useProModel) {
            return message;
        }
        return "Request sent to DeepSeek V4 Pro.\n" + (message == null ? "" : message);
    }

    private static String fallbackNotice(String message) {
        if (message == null) {
            return "";
        }
        int index = message.indexOf("Final fallback retry with ");
        if (index < 0) {
            index = message.indexOf("Extra retry with ");
        }
        if (index < 0) {
            return "";
        }
        int end = message.indexOf('\n', index);
        if (end < 0) {
            end = message.length();
        }
        return message.substring(index, end).trim();
    }

    private static String withNotice(String notice, String body) {
        if (notice == null || notice.length() == 0) {
            return body == null ? "" : body;
        }
        if (body == null || body.length() == 0) {
            return notice;
        }
        if (body.indexOf(notice) == 0) {
            return body;
        }
        return notice + "\n\n" + body;
    }

    private static String displayTail(String text) {
        if (text == null || text.length() <= THINKING_DISPLAY_LIMIT) {
            return text == null ? "" : text;
        }
        return "[...]\n" + text.substring(text.length() - THINKING_DISPLAY_LIMIT);
    }

    private void setOutput(String text) {
        output.setText(text == null ? "" : text);
        output.setCaretPosition(0);
        revalidate();
        repaint();
    }

}
