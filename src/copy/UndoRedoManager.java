package paint.copy;

import java.awt.image.BufferedImage;
import java.util.ArrayDeque;

/**
 * Manages undo/redo stacks for canvas operations.
 * Encapsulates all undo/redo fields and operations.
 */
public class UndoRedoManager {

    // Undo / Redo (stack front = most recent)
    private final ArrayDeque<BufferedImage> undoStack;
    private final ArrayDeque<BufferedImage> redoStack;
    private final int maxUndoSize;

    // Callback for state propagation
    private Runnable onUndoRedoComplete = null;

    /**
     * Create a new undo/redo manager.
     * @param maxSize Maximum number of undo entries to keep
     */
    public UndoRedoManager(int maxSize) {
        this.undoStack = new ArrayDeque<>();
        this.redoStack = new ArrayDeque<>();
        this.maxUndoSize = maxSize;
    }

    // ─────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────

    /**
     * Push the current image state onto the undo stack.
     * Clears the redo stack.
     */
    public void pushUndo(BufferedImage img) {
        if (img == null) return;
        undoStack.push(deepCopy(img));
        if (undoStack.size() > maxUndoSize) {
            undoStack.pollLast();
        }
        redoStack.clear();
    }

    /**
     * Perform an undo operation.
     * @param currentImage The current working image
     * @return The restored image, or null if undo stack is empty
     */
    public BufferedImage doUndo(BufferedImage currentImage) {
        if (undoStack.isEmpty()) return null;
        if (currentImage != null) {
            redoStack.push(deepCopy(currentImage));
        }
        BufferedImage restored = undoStack.pop();
        if (onUndoRedoComplete != null) {
            onUndoRedoComplete.run();
        }
        return restored;
    }

    /**
     * Perform a redo operation.
     * @param currentImage The current working image
     * @return The restored image, or null if redo stack is empty
     */
    public BufferedImage doRedo(BufferedImage currentImage) {
        if (redoStack.isEmpty()) return null;
        if (currentImage != null) {
            undoStack.push(deepCopy(currentImage));
        }
        BufferedImage restored = redoStack.pop();
        if (onUndoRedoComplete != null) {
            onUndoRedoComplete.run();
        }
        return restored;
    }

    /**
     * Check if undo is available.
     */
    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    /**
     * Check if redo is available.
     */
    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    /**
     * Clear both undo and redo stacks.
     */
    public void clear() {
        undoStack.clear();
        redoStack.clear();
    }

    /**
     * Get the size of the undo stack (for debugging).
     */
    public int getUndoSize() {
        return undoStack.size();
    }

    /**
     * Get the size of the redo stack (for debugging).
     */
    public int getRedoSize() {
        return redoStack.size();
    }

    /**
     * Get the undo stack (for cache sync).
     */
    public ArrayDeque<BufferedImage> getUndoStack() {
        return undoStack;
    }

    /**
     * Get the redo stack (for cache sync).
     */
    public ArrayDeque<BufferedImage> getRedoStack() {
        return redoStack;
    }

    // ─────────────────────────────────────────────────────────────
    // Private Helpers
    // ─────────────────────────────────────────────────────────────

    /**
     * Deep copy a BufferedImage.
     */
    private BufferedImage deepCopy(BufferedImage src) {
        if (src == null) return null;
        BufferedImage dst = new BufferedImage(src.getWidth(), src.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g2 = dst.createGraphics();
        g2.drawImage(src, 0, 0, null);
        g2.dispose();
        return dst;
    }

    // ─────────────────────────────────────────────────────────────
    // Callback Setter
    // ─────────────────────────────────────────────────────────────

    /**
     * Set a callback to be invoked after undo/redo operations complete.
     * The callback should handle dirty flag updates, UI refresh, etc.
     */
    public void setOnUndoRedoComplete(Runnable r) {
        this.onUndoRedoComplete = r;
    }
}
