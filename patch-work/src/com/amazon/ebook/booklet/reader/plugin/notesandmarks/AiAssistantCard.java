package com.amazon.ebook.booklet.reader.plugin.notesandmarks;

import com.amazon.ebook.booklet.reader.sdk.ReaderSDK;
import com.amazon.ebook.booklet.reader.sdk.ui.SelectionDialogCard;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

final class AiAssistantCard extends SelectionDialogCard {
    private final ReaderSDK sdk;
    private final AiAssistantController.SelectionData selection;
    private JTextArea output;
    private JTextArea customInput;

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
        setLayout(new BorderLayout(8, 8));
        setOpaque(true);
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel presets = new JPanel(new GridLayout(2, 3, 8, 8));
        presets.setOpaque(true);
        presets.setBackground(Color.WHITE);
        addPreset(presets, "Explain");
        addPreset(presets, "Translate");
        addPreset(presets, "Words");
        addPreset(presets, "Grammar");
        addPreset(presets, "Short");
        addCustomButton(presets);
        add(presets, BorderLayout.NORTH);

        output = new JTextArea("Choose a prompt.");
        output.setEditable(false);
        output.setLineWrap(true);
        output.setWrapStyleWord(true);
        output.setFont(new Font("SansSerif", Font.PLAIN, 18));
        output.setOpaque(true);
        output.setBackground(Color.WHITE);
        add(new JScrollPane(output), BorderLayout.CENTER);

        JPanel custom = new JPanel(new BorderLayout(6, 6));
        custom.setOpaque(true);
        custom.setBackground(Color.WHITE);
        customInput = new JTextArea(2, 20);
        customInput.setLineWrap(true);
        customInput.setWrapStyleWord(true);
        customInput.setFont(new Font("SansSerif", Font.PLAIN, 17));
        custom.add(new JScrollPane(customInput), BorderLayout.CENTER);

        JButton send = new JButton("Send");
        send.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                String text = customInput.getText();
                if (text != null) {
                    text = text.trim();
                }
                if (text == null || text.length() == 0) {
                    setOutput("Type a custom request first.");
                    return;
                }
                runPrompt("Custom", text);
            }
        });
        custom.add(send, BorderLayout.EAST);
        add(custom, BorderLayout.SOUTH);
    }

    private void addPreset(JPanel panel, final String name) {
        JButton button = new JButton(name);
        button.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                runPrompt(name, AiAssistantController.presetPrompt(name));
            }
        });
        panel.add(button);
    }

    private void addCustomButton(JPanel panel) {
        JButton button = new JButton("Custom");
        button.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                customInput.requestFocus();
                setOutput("Type a request below, then press Send.");
            }
        });
        panel.add(button);
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
            String userPrompt = AiAssistantController.buildUserPrompt(instruction, selection);
            OpenRouterClient.send(sdk, config, userPrompt, new OpenRouterClient.Callback() {
                public void onSuccess(final String text) {
                    AiAssistantLog.write("prompt success name=" + name
                            + " length=" + (text == null ? 0 : text.length()));
                    sdk.invokeLater(new Runnable() {
                        public void run() {
                            setOutput(text);
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
}
