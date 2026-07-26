package com.amazon.ebook.booklet.reader.plugin.notesandmarks;

import java.awt.Dimension;
import java.awt.Rectangle;

public final class AiAssistantControllerGeometryTest {
    private AiAssistantControllerGeometryTest() { }

    public static void main(String[] args) {
        doublesHeightWithoutChangingWidth();
        capsHeightToTheScreen();
        keepsTheExpandedRectangleOnScreen();
        System.out.println("AiAssistantControllerGeometryTest OK");
    }

    private static void doublesHeightWithoutChangingWidth() {
        Rectangle original = new Rectangle(42, 24, 999, 649);
        Rectangle expanded = AiAssistantController.expandedDialogBounds(
                original, new Dimension(1264, 1680));

        assertEquals(42, expanded.x, "x");
        assertEquals(0, expanded.y, "y");
        assertEquals(999, expanded.width, "width");
        assertEquals(1298, expanded.height, "height");
    }

    private static void capsHeightToTheScreen() {
        Rectangle original = new Rectangle(10, 500, 900, 700);
        Rectangle expanded = AiAssistantController.expandedDialogBounds(
                original, new Dimension(1264, 1000));

        assertEquals(0, expanded.y, "capped y");
        assertEquals(1000, expanded.height, "capped height");
    }

    private static void keepsTheExpandedRectangleOnScreen() {
        Rectangle original = new Rectangle(10, 900, 900, 300);
        Rectangle expanded = AiAssistantController.expandedDialogBounds(
                original, new Dimension(1264, 1264));

        assertEquals(600, expanded.y, "bottom-clamped y");
        assertEquals(600, expanded.height, "landscape height");
        assertTrue(expanded.y + expanded.height <= 1264, "expanded rectangle overflows screen");
    }

    private static void assertEquals(int expected, int actual, String label) {
        if (expected != actual) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }

    private static void assertTrue(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }
}
