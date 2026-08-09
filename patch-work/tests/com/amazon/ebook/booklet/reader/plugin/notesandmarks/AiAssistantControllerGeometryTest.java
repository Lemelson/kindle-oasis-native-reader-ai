package com.amazon.ebook.booklet.reader.plugin.notesandmarks;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.lang.reflect.Method;

public final class AiAssistantControllerGeometryTest {
    private AiAssistantControllerGeometryTest() { }

    public static void main(String[] args) {
        doublesHeightWithoutChangingWidth();
        capsHeightToTheScreen();
        keepsTheExpandedRectangleOnScreen();
        liveResizeDoesNotForceAnExtraFullFrameRepaint();
        lateStockBoundsAreCorrectedOnce();
        alreadyExpandedBoundsDoNotTriggerAnotherLayout();
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

    private static void liveResizeDoesNotForceAnExtraFullFrameRepaint() {
        CountingComponent frame = new CountingComponent();
        FakeFrameDialog dialog = new FakeFrameDialog(frame);
        Rectangle target = new Rectangle(48, 159, 1159, 1504);

        try {
            Method resize = AiAssistantController.class.getDeclaredMethod(
                    "resizeLiveFrame", new Class[] { Object.class, Rectangle.class });
            resize.setAccessible(true);
            Rectangle actual = (Rectangle) resize.invoke(
                    null, new Object[] { dialog, target });

            assertEquals(target.height, actual.height, "live frame height");
            assertEquals(0, frame.repaintCalls, "forced frame repaints");
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("could not exercise live frame resize", e);
        }
    }

    private static void lateStockBoundsAreCorrectedOnce() {
        Rectangle stock = new Rectangle(48, 911, 1159, 752);
        Rectangle target = new Rectangle(48, 159, 1159, 1504);
        CountingComponent frame = new CountingComponent();
        frame.setBounds(stock);
        frame.resetCounts();

        Rectangle actual = ensureLiveFrameBounds(new FakeFrameDialog(frame), target);

        assertEquals(target.height, actual.height, "corrected live frame height");
        assertEquals(1, frame.setBoundsCalls, "late correction count");
    }

    private static void alreadyExpandedBoundsDoNotTriggerAnotherLayout() {
        Rectangle target = new Rectangle(48, 159, 1159, 1504);
        CountingComponent frame = new CountingComponent();
        frame.setBounds(target);
        frame.resetCounts();

        Rectangle actual = ensureLiveFrameBounds(new FakeFrameDialog(frame), target);

        assertEquals(target.height, actual.height, "stable live frame height");
        assertEquals(0, frame.setBoundsCalls, "redundant resize count");
    }

    private static Rectangle ensureLiveFrameBounds(
            Object dialog, Rectangle target) {
        try {
            Method ensure = AiAssistantController.class.getDeclaredMethod(
                    "ensureLiveFrameBounds",
                    new Class[] { Object.class, Rectangle.class });
            ensure.setAccessible(true);
            return (Rectangle) ensure.invoke(null, new Object[] { dialog, target });
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("could not verify delayed live frame bounds", e);
        }
    }

    private static final class FakeFrameDialog {
        private final Component frame;

        FakeFrameDialog(Component frame) {
            this.frame = frame;
        }

        public Component getFrame() {
            return frame;
        }
    }

    private static final class CountingComponent extends Component {
        int repaintCalls;
        int setBoundsCalls;

        public void repaint() {
            repaintCalls++;
        }

        public void setBounds(Rectangle bounds) {
            setBoundsCalls++;
            super.setBounds(bounds);
        }

        void resetCounts() {
            repaintCalls = 0;
            setBoundsCalls = 0;
        }
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
