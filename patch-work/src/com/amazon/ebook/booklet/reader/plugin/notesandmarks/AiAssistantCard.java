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
import java.util.HashMap;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

final class AiAssistantCard extends SelectionDialogCard {
    private static final int OUTPUT_FONT_SIZE = 8;
    private static final int CONTROL_FONT_SIZE = 12;
    private static final int BUTTON_HEIGHT = 48;

    private final ReaderSDK sdk;
    private final AiAssistantController.SelectionData selection;
    private final Map cachedResponses = new HashMap();
    private final Map statusTexts = new HashMap();
    private final Map requestIds = new HashMap();
    private JTextArea output;
    private String activeMode = "";
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

        output = new JTextArea("Choose a prompt.");
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
        button.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                selectPreset(name);
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

    private void selectPreset(String name) {
        boolean sameMode = name.equals(activeMode);
        activeMode = name;
        if (!sameMode) {
            String cached = (String) cachedResponses.get(name);
            if (cached != null) {
                setOutput(cached);
                return;
            }

            String status = (String) statusTexts.get(name);
            if (status != null) {
                setOutput(status);
                return;
            }
        }

        runPrompt(name, AiAssistantController.presetPrompt(name));
    }

    private void runPrompt(final String name, String instruction) {
        final int requestId = nextRequestId(name);
        try {
            AiAssistantLog.write("prompt start name=" + name);
            OpenRouterConfig config = OpenRouterConfig.load();
            if (!config.isUsable()) {
                updateStatus(name, requestId,
                        "OpenRouter config missing.\nCheck /mnt/us/native-reader-research/ai-assistant.properties");
                return;
            }

            updateStatus(name, requestId, "OpenRouter request: attempt 1.");
            String userPrompt = AiAssistantController.buildUserPrompt(name, instruction, selection);
            OpenRouterClient.send(sdk, config, userPrompt, new OpenRouterClient.Callback() {
                public void onStatus(final String message) {
                    sdk.invokeLater(new Runnable() {
                        public void run() {
                            updateStatus(name, requestId, message);
                        }
                    });
                }

                public void onSuccess(final String text) {
                    AiAssistantLog.write("prompt success name=" + name
                            + " length=" + (text == null ? 0 : text.length()));
                    sdk.invokeLater(new Runnable() {
                        public void run() {
                            if (!isLatestRequest(name, requestId)) {
                                return;
                            }
                            String clean = stripBasicMarkdown(text);
                            cachedResponses.put(name, clean);
                            statusTexts.remove(name);
                            if (name.equals(activeMode)) {
                                setOutput(clean);
                            }
                        }
                    });
                }

                public void onError(final String message) {
                    AiAssistantLog.write("prompt error name=" + name + " message=" + message);
                    sdk.invokeLater(new Runnable() {
                        public void run() {
                            updateStatus(name, requestId, "OpenRouter error after retries:\n" + message);
                        }
                    });
                }
            });
        } catch (Throwable t) {
            AiAssistantLog.write("prompt failed name=" + name + " error=" + t.toString());
            updateStatus(name, requestId, "AI failed:\n" + t.toString());
        }
    }

    private int nextRequestId(String name) {
        requestSerial++;
        Integer id = new Integer(requestSerial);
        requestIds.put(name, id);
        cachedResponses.remove(name);
        return id.intValue();
    }

    private boolean isLatestRequest(String name, int requestId) {
        Integer latest = (Integer) requestIds.get(name);
        return latest != null && latest.intValue() == requestId;
    }

    private void updateStatus(String name, int requestId, String message) {
        if (!isLatestRequest(name, requestId)) {
            return;
        }
        String text = String.valueOf(message);
        statusTexts.put(name, text);
        if (name.equals(activeMode)) {
            setOutput(text);
        }
    }

    private void setOutput(String text) {
        output.setText(text == null ? "" : text);
        output.setCaretPosition(0);
        revalidate();
        repaint();
    }

    private static String stripBasicMarkdown(String value) {
        if (value == null) {
            return "";
        }
        StringBuffer out = new StringBuffer(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ((c == '*' || c == '_') && i + 1 < value.length() && value.charAt(i + 1) == c) {
                i++;
            } else if (c == '`') {
                // Drop inline code markers, keep the text.
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
