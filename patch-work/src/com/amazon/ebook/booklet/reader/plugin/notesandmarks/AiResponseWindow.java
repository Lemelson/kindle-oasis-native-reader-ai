package com.amazon.ebook.booklet.reader.plugin.notesandmarks;

import com.amazon.ebook.booklet.reader.sdk.ReaderSDK;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

final class AiResponseWindow {
    private final JDialog dialog;
    private final JTextArea textArea;

    private AiResponseWindow(JDialog dialog, JTextArea textArea) {
        this.dialog = dialog;
        this.textArea = textArea;
    }

    static AiResponseWindow show(ReaderSDK sdk, String title, String initialText) {
        Component parent = null;
        try {
            parent = sdk.Jl().KM();
        } catch (Throwable ignored) {
            parent = null;
        }

        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        JDialog dialog;
        if (owner instanceof Frame) {
            dialog = new JDialog((Frame) owner, title, false);
        } else {
            dialog = new JDialog((Frame) null, title, false);
        }
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        JTextArea textArea = new JTextArea(initialText);
        textArea.setEditable(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setFont(new Font("SansSerif", Font.PLAIN, 18));

        JScrollPane scrollPane = new JScrollPane(textArea);
        JButton close = new JButton("Close");
        close.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                dialog.dispose();
            }
        });

        JPanel buttonPanel = new JPanel(new BorderLayout());
        buttonPanel.add(close, BorderLayout.EAST);

        JPanel content = new JPanel(new BorderLayout());
        content.add(scrollPane, BorderLayout.CENTER);
        content.add(buttonPanel, BorderLayout.SOUTH);
        dialog.setContentPane(content);

        placeDialog(dialog, parent);
        dialog.setVisible(true);
        return new AiResponseWindow(dialog, textArea);
    }

    void setText(String text) {
        textArea.setText(text == null ? "" : text);
        textArea.setCaretPosition(0);
        if (!dialog.isVisible()) {
            dialog.setVisible(true);
        }
    }

    private static void placeDialog(JDialog dialog, Component parent) {
        Dimension fallback = new Dimension(560, 360);
        if (parent == null) {
            dialog.setSize(fallback);
            dialog.setLocationRelativeTo(null);
            return;
        }

        Rectangle bounds = parent.getBounds();
        if (parent instanceof JComponent) {
            try {
                bounds = ((JComponent) parent).getRootPane().getBounds();
            } catch (Throwable ignored) {
                bounds = parent.getBounds();
            }
        }

        int width = Math.max(420, bounds.width - 40);
        int height = Math.max(260, bounds.height / 2);
        int x = bounds.x + 20;
        int y = bounds.y + Math.max(0, bounds.height - height - 20);
        dialog.setBounds(x, y, width, height);
    }
}
