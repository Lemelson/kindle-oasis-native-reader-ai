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
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

final class AiAssistantCard extends SelectionDialogCard {
    private static final int OUTPUT_FONT_SIZE = 8;
    private static final int CONTROL_FONT_SIZE = 11;
    private static final int BUTTON_HEIGHT = 32;

    private final ReaderSDK sdk;
    private final AiAssistantController.SelectionData selection;
    private JTextArea output;

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

        JPanel presets = new JPanel(new GridLayout(1, 4, 4, 4));
        presets.setOpaque(true);
        presets.setBackground(Color.WHITE);
        presets.setPreferredSize(new Dimension(10, BUTTON_HEIGHT));
        addPreset(presets, "Explain");
        addPreset(presets, "Short");
        addPreset(presets, "Point");
        addPreset(presets, "Recall");
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
                runPrompt(name, AiAssistantController.presetPrompt(name));
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

    private void runPrompt(final String name, String instruction) {
        try {
            AiAssistantLog.write("prompt start name=" + name);
            OpenRouterConfig config = OpenRouterConfig.load();
            if (!config.isUsable()) {
                setOutput("OpenRouter config missing.\nCheck /mnt/us/native-reader-research/ai-assistant.properties");
                return;
            }

            setOutput("Sending request to OpenRouter...");
            String userPrompt = AiAssistantController.buildUserPrompt(name, instruction, selection);
            OpenRouterClient.send(sdk, config, userPrompt, new OpenRouterClient.Callback() {
                public void onSuccess(final String text) {
                    AiAssistantLog.write("prompt success name=" + name
                            + " length=" + (text == null ? 0 : text.length()));
                    sdk.invokeLater(new Runnable() {
                        public void run() {
                            setOutput(stripBasicMarkdown(text));
                        }
                    });
                }

                public void onError(final String message) {
                    AiAssistantLog.write("prompt error name=" + name + " message=" + message);
                    sdk.invokeLater(new Runnable() {
                        public void run() {
                            setOutput("OpenRouter error:\n" + message);
                        }
                    });
                }
            });
        } catch (Throwable t) {
            AiAssistantLog.write("prompt failed name=" + name + " error=" + t.toString());
            setOutput("AI failed:\n" + t.toString());
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
