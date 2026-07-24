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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
    private final OpenRouterConfig config;
    private final AiVpnSession vpnSession;
    private final Map cachedResponses = new HashMap();
    private final Map statusTexts = new HashMap();
    private final Map requestIds = new HashMap();
    private final List presetButtons = new ArrayList();
    private JTextArea output;
    private String activeMode = "";
    private String activeKey = "";
    private int requestSerial = 0;
    private int currentRequestId = 0;
    private AiProviderChainClient.Request activeRequest;
    private String activeRequestKey = "";
    private boolean sessionClosed;

    AiAssistantCard(
            ReaderSDK sdk,
            AiAssistantController.SelectionData selection,
            OpenRouterConfig config,
            AiVpnSession vpnSession) {
        this.sdk = sdk;
        this.selection = selection;
        this.config = config;
        this.vpnSession = vpnSession;
        buildUi();
    }

    void startDefault() {
        sdk.invokeLater(new Runnable() {
            public void run() {
                if (activeMode.length() == 0) {
                    selectPreset("Explain", false);
                }
            }
        });
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

    public void closeDialog() {
        closeSession();
        super.closeDialog();
    }

    public void removeNotify() {
        closeSession();
        super.removeNotify();
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
        addPreset(presets, "Wiki");
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
        button.setActionCommand(name);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
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
        presetButtons.add(button);
        panel.add(button);
    }

    private void compactButton(JButton button) {
        button.setFont(new Font("SansSerif", Font.PLAIN, CONTROL_FONT_SIZE));
        button.setMargin(new Insets(2, 4, 2, 4));
        button.setPreferredSize(new Dimension(10, BUTTON_HEIGHT));
        button.setMinimumSize(new Dimension(10, BUTTON_HEIGHT));
    }

    private void selectPreset(String name, boolean useProModel) {
        if (sessionClosed) {
            return;
        }
        String key = modeKey(name, useProModel);
        if (activeRequest != null && key.equals(activeRequestKey)) {
            return;
        }
        cancelActiveRequest();
        boolean sameMode = key.equals(activeKey);
        activeMode = name;
        activeKey = key;
        updateActiveButton();
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
            config.proRequest = useProModel;
            if (config.diagnosticsEnabled) {
                updateStatus(key, requestId, "Проверяю подключение…");
                AiConnectivityDiagnostics.run(config, vpnSession, new AiConnectivityDiagnostics.Callback() {
                    public void onUpdate(final String text) {
                        sdk.invokeLater(new Runnable() {
                            public void run() {
                                updateStatus(key, requestId, text);
                                if (text != null && text.indexOf("\nDone.") >= 0) {
                                    finishRequest(key, requestId);
                                }
                            }
                        });
                    }
                });
                return;
            }
            if (!config.isUsable()) {
                updateStatus(key, requestId,
                        "Не найдена конфигурация AI.");
                finishRequest(key, requestId);
                return;
            }

            updateStatus(key, requestId,
                    useProModel ? "DeepSeek Pro думает…" : "Gemini думает…");
            String userPrompt = AiAssistantController.buildUserPrompt(name, instruction, selection);
            final StringBuffer thinking = new StringBuffer();
            final StringBuffer content = new StringBuffer();
            final long[] lastThinkingUpdateMs = new long[] { 0L };
            final long[] lastContentUpdateMs = new long[] { 0L };
            activeRequest = AiProviderChainClient.send(
                    sdk, config, vpnSession, userPrompt, useProModel, new OpenRouterClient.Callback() {
                public void onStatus(final String message) {
                    sdk.invokeLater(new Runnable() {
                        public void run() {
                            if (content.length() > 0 && message != null
                                    && (message.indexOf("повтор") >= 0
                                    || message.indexOf("Переключаюсь") >= 0)) {
                                content.setLength(0);
                                thinking.setLength(0);
                                lastContentUpdateMs[0] = 0L;
                                lastThinkingUpdateMs[0] = 0L;
                            }
                            updateStatus(key, requestId, message);
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
                                updateStatus(key, requestId, "DeepSeek думает…\n"
                                        + displayTail(thinking.toString()));
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
                                updateStatus(key, requestId, content.toString());
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
                            String clean = text == null ? "" : text;
                            cachedResponses.put(key, clean);
                            statusTexts.remove(key);
                            if (key.equals(activeKey)) {
                                setOutput(clean);
                            }
                            finishRequest(key, requestId);
                        }
                    });
                }

                public void onError(final String message) {
                    AiAssistantLog.write("prompt error name=" + name + " message=" + message);
                    sdk.invokeLater(new Runnable() {
                        public void run() {
                            updateStatus(key, requestId, message);
                            finishRequest(key, requestId);
                        }
                    });
                }
            });
            activeRequestKey = key;
        } catch (Throwable t) {
            AiAssistantLog.write("prompt failed name=" + name + " error=" + t.toString());
            updateStatus(key, requestId, "Не удалось запустить AI.");
            finishRequest(key, requestId);
        }
    }

    private void finishRequest(String key, int requestId) {
        if (!isLatestRequest(key, requestId)) {
            return;
        }
        activeRequest = null;
        activeRequestKey = "";
    }

    private void cancelActiveRequest() {
        if (activeRequest != null) {
            activeRequest.cancel();
            activeRequest = null;
            activeRequestKey = "";
            currentRequestId = ++requestSerial;
        }
    }

    private void closeSession() {
        if (sessionClosed) {
            return;
        }
        sessionClosed = true;
        cancelActiveRequest();
        currentRequestId = ++requestSerial;
        vpnSession.close();
    }

    private void updateActiveButton() {
        for (int i = 0; i < presetButtons.size(); i++) {
            JButton button = (JButton) presetButtons.get(i);
            boolean active = activeMode.equals(button.getActionCommand());
            button.setFont(new Font(
                    "SansSerif",
                    active ? Font.BOLD : Font.PLAIN,
                    CONTROL_FONT_SIZE));
            button.setBackground(active ? Color.LIGHT_GRAY : Color.WHITE);
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
        currentRequestId = requestSerial;
        Integer id = new Integer(requestSerial);
        requestIds.put(key, id);
        cachedResponses.remove(key);
        return id.intValue();
    }

    private boolean isLatestRequest(String key, int requestId) {
        Integer latest = (Integer) requestIds.get(key);
        return requestId == currentRequestId
                && latest != null
                && latest.intValue() == requestId;
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
