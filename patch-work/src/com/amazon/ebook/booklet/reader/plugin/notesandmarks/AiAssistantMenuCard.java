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
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

final class AiAssistantMenuCard extends SelectionDialogCard {
    private static final int TITLE_FONT_SIZE = 12;
    private static final int BUTTON_FONT_SIZE = 12;
    private static final int BUTTON_HEIGHT = 40;

    private final ReaderSDK sdk;
    private final AiAssistantController.SelectionData selection;

    AiAssistantMenuCard(ReaderSDK sdk, AiAssistantController.SelectionData selection) {
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
        return 990;
    }

    public String anN() {
        return "AI_ASSISTANT_MENU_CARD";
    }

    public String asQ() {
        return "AIAssistantMenuCard";
    }

    public String anQ() {
        return "AI_ASSISTANT_MENU";
    }

    private void buildUi() {
        setLayout(new BorderLayout(3, 3));
        setOpaque(true);
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JLabel title = new JLabel("AI", SwingConstants.LEFT);
        title.setOpaque(true);
        title.setBackground(Color.WHITE);
        title.setFont(new Font("SansSerif", Font.BOLD, TITLE_FONT_SIZE));
        add(title, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(3, 2, 2, 2));
        grid.setOpaque(true);
        grid.setBackground(Color.WHITE);
        grid.add(modeButton("Explain"));
        grid.add(modeButton("Words"));
        grid.add(modeButton("Recall"));
        grid.add(modeButton("Why"));
        grid.add(modeButton("Deep"));
        grid.add(closeButton());
        add(grid, BorderLayout.CENTER);
    }

    private JButton modeButton(final String modeName) {
        JButton button = new JButton(modeName);
        button.setFont(new Font("SansSerif", Font.BOLD, BUTTON_FONT_SIZE));
        button.setMargin(new Insets(2, 4, 2, 4));
        button.setPreferredSize(new Dimension(120, BUTTON_HEIGHT));
        button.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                AiAssistantController.openDirect(sdk, modeName, selection);
            }
        });
        return button;
    }

    private JButton closeButton() {
        JButton button = new JButton("Close");
        button.setFont(new Font("SansSerif", Font.PLAIN, BUTTON_FONT_SIZE));
        button.setMargin(new Insets(2, 4, 2, 4));
        button.setPreferredSize(new Dimension(120, BUTTON_HEIGHT));
        button.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                try {
                    closeDialog();
                } catch (Throwable t) {
                    AiAssistantLog.write("ai menu close failed: " + t.toString());
                }
            }
        });
        return button;
    }
}
