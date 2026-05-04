package com.amazon.ebook.booklet.reader.plugin.notesandmarks;

import com.amazon.ebook.booklet.reader.sdk.ReaderSDK;
import com.amazon.ebook.booklet.reader.sdk.ui.SelectionDialogCard;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

final class AiAssistantCard extends SelectionDialogCard {
    private static final int OUTPUT_FONT_SIZE = 6;
    private static final int CONTROL_FONT_SIZE = 7;
    private static final int BUTTON_HEIGHT = 14;
    private static final int INPUT_HEIGHT = 16;

    private final ReaderSDK sdk;
    private final AiAssistantController.SelectionData selection;
    private JEditorPane output;
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
        setLayout(new BorderLayout(3, 3));
        setOpaque(true);
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createEmptyBorder(3, 4, 3, 4));

        JPanel presets = new JPanel(new GridLayout(1, 3, 3, 3));
        presets.setOpaque(true);
        presets.setBackground(Color.WHITE);
        presets.setPreferredSize(new Dimension(10, BUTTON_HEIGHT));
        addPreset(presets, "Explain");
        addPreset(presets, "Short");
        addCustomButton(presets);
        add(presets, BorderLayout.NORTH);

        output = new JEditorPane();
        output.setContentType("text/html");
        output.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        output.setEditable(false);
        output.setFont(new Font("SansSerif", Font.PLAIN, OUTPUT_FONT_SIZE));
        output.setOpaque(true);
        output.setBackground(Color.WHITE);
        output.setText(toHtml("Choose a prompt."));
        add(new JScrollPane(output), BorderLayout.CENTER);

        JPanel custom = new JPanel(new BorderLayout(3, 3));
        custom.setOpaque(true);
        custom.setBackground(Color.WHITE);
        custom.setPreferredSize(new Dimension(10, INPUT_HEIGHT));

        customInput = new JTextArea(1, 20);
        customInput.setLineWrap(true);
        customInput.setWrapStyleWord(true);
        customInput.setFont(new Font("SansSerif", Font.PLAIN, CONTROL_FONT_SIZE));
        customInput.setMargin(new Insets(0, 2, 0, 2));

        JScrollPane inputScroll = new JScrollPane(customInput);
        inputScroll.setPreferredSize(new Dimension(10, INPUT_HEIGHT));
        inputScroll.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
        custom.add(inputScroll, BorderLayout.CENTER);

        JPanel customButtons = new JPanel(new GridLayout(1, 2, 2, 0));
        customButtons.setOpaque(true);
        customButtons.setBackground(Color.WHITE);
        customButtons.setPreferredSize(new Dimension(58, INPUT_HEIGHT));

        JButton send = new JButton("Send");
        compactButton(send);
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
        customButtons.add(send);

        JButton hide = new JButton("Hide");
        compactButton(hide);
        hide.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                releaseCustomInputFocus();
            }
        });
        customButtons.add(hide);

        custom.add(customButtons, BorderLayout.EAST);
        add(custom, BorderLayout.SOUTH);
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

    private void addCustomButton(JPanel panel) {
        JButton button = new JButton("Custom");
        compactButton(button);
        button.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                customInput.requestFocus();
                setOutput("Type a request below, then press Send.");
            }
        });
        panel.add(button);
    }

    private void compactButton(JButton button) {
        button.setFont(new Font("SansSerif", Font.PLAIN, CONTROL_FONT_SIZE));
        button.setMargin(new Insets(0, 2, 0, 2));
        button.setPreferredSize(new Dimension(10, BUTTON_HEIGHT));
        button.setMinimumSize(new Dimension(10, BUTTON_HEIGHT));
        button.setMaximumSize(new Dimension(999, BUTTON_HEIGHT));
    }

    private void runPrompt(final String name, String instruction) {
        try {
            AiAssistantLog.write("prompt start name=" + name);
            releaseCustomInputFocus();
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

    private void releaseCustomInputFocus() {
        try {
            customInput.setFocusable(false);
            KeyboardFocusManager.getCurrentKeyboardFocusManager().clearGlobalFocusOwner();
            output.requestFocusInWindow();
            customInput.setFocusable(true);
        } catch (Throwable t) {
            AiAssistantLog.write("focus release skipped: " + t.toString());
        }
    }

    private void setOutput(String text) {
        output.setText(toHtml(text == null ? "" : text));
        output.setCaretPosition(0);
        revalidate();
        repaint();
    }

    private static String toHtml(String markdown) {
        StringBuffer html = new StringBuffer();
        html.append("<html><body style=\"font-family:SansSerif;font-size:");
        html.append(OUTPUT_FONT_SIZE);
        html.append("pt;margin:0;padding:0;color:#000000;\">");
        html.append(markdownToHtml(markdown));
        html.append("</body></html>");
        return html.toString();
    }

    private static String markdownToHtml(String value) {
        if (value == null) {
            return "";
        }
        StringBuffer out = new StringBuffer(value.length() + 32);
        boolean bold = false;
        boolean code = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '`') {
                out.append(code ? "</code>" : "<code>");
                code = !code;
            } else if ((c == '*' || c == '_') && i + 1 < value.length() && value.charAt(i + 1) == c) {
                out.append(bold ? "</b>" : "<b>");
                bold = !bold;
                i++;
            } else if (c == '\n') {
                out.append("<br>");
            } else {
                appendEscaped(out, c);
            }
        }
        if (code) {
            out.append("</code>");
        }
        if (bold) {
            out.append("</b>");
        }
        return out.toString();
    }

    private static void appendEscaped(StringBuffer out, char c) {
        if (c == '&') {
            out.append("&amp;");
        } else if (c == '<') {
            out.append("&lt;");
        } else if (c == '>') {
            out.append("&gt;");
        } else if (c == '"') {
            out.append("&quot;");
        } else {
            out.append(c);
        }
    }
}
