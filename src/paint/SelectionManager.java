package paint;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages rectangular selection state and operations.
 * Encapsulates all selection-related fields and logic.
 */
public class SelectionManager {

    // Selection state
    private List<Rectangle> selectedAreas = new ArrayList<>();
    private boolean isSelecting = false;
    private Point selectionStart = null;
    private Point selectionEnd = null;

    // Callback for UI updates
    private Runnable onSelectionChanged = null;

    public SelectionManager() {
    }

    // ─────────────────────────────────────────────────────────────
    // Public API - Selection Operations
    // ─────────────────────────────────────────────────────────────

    /**
     * Begin a new selection drag.
     */
    public void beginSelection(Point startPoint) {
        isSelecting = true;
        selectionStart = startPoint;
        selectionEnd = startPoint;
    }

    /**
     * Update the selection during a drag.
     */
    public void updateSelection(Point currentPoint) {
        if (!isSelecting) return;
        selectionEnd = currentPoint;
    }

    /**
     * End the current selection drag and add to selection list.
     * @return The final selection rectangle, or null if too small
     */
    public Rectangle endSelection() {
        if (!isSelecting) return null;
        isSelecting = false;

        if (selectionStart == null || selectionEnd == null) return null;

        int x = Math.min(selectionStart.x, selectionEnd.x);
        int y = Math.min(selectionStart.y, selectionEnd.y);
        int w = Math.abs(selectionEnd.x - selectionStart.x);
        int h = Math.abs(selectionEnd.y - selectionStart.y);

        if (w <= 0 || h <= 0) return null;

        Rectangle rect = new Rectangle(x, y, w, h);
        selectedAreas.add(rect);
        notifyChanged();
        return rect;
    }

    /**
     * Get the current selection rectangle (last in list).
     */
    public Rectangle getActiveSelection() {
        if (selectedAreas.isEmpty()) return null;
        return selectedAreas.get(selectedAreas.size() - 1);
    }

    /**
     * Get all selected areas.
     */
    public List<Rectangle> getAll() {
        return new ArrayList<>(selectedAreas);
    }

    /**
     * Clear all selections.
     */
    public void clearAll() {
        selectedAreas.clear();
        isSelecting = false;
        selectionStart = null;
        selectionEnd = null;
        notifyChanged();
    }

    /**
     * Check if currently dragging to create a selection.
     */
    public boolean isSelecting() {
        return isSelecting;
    }

    /**
     * Get the current drag start point (null if not dragging).
     */
    public Point getDragStart() {
        return selectionStart;
    }

    /**
     * Get the current drag end point (null if not dragging).
     */
    public Point getDragEnd() {
        return selectionEnd;
    }

    /**
     * Get the rectangle that would result from current drag.
     * Returns null if not currently dragging.
     */
    public Rectangle getCurrentDragRect() {
        if (!isSelecting || selectionStart == null || selectionEnd == null) return null;

        int x = Math.min(selectionStart.x, selectionEnd.x);
        int y = Math.min(selectionStart.y, selectionEnd.y);
        int w = Math.abs(selectionEnd.x - selectionStart.x);
        int h = Math.abs(selectionEnd.y - selectionStart.y);

        return (w > 0 && h > 0) ? new Rectangle(x, y, w, h) : null;
    }

    /**
     * Check if any selections exist.
     */
    public boolean hasSelections() {
        return !selectedAreas.isEmpty();
    }

    /**
     * Get the count of selections.
     */
    public int getSelectionCount() {
        return selectedAreas.size();
    }

    /**
     * Check if a point is inside any selection.
     */
    public boolean containsPoint(Point pt) {
        for (Rectangle r : selectedAreas) {
            if (r.contains(pt)) return true;
        }
        return false;
    }

    /**
     * Add a selection rectangle directly (used for programmatic updates like scale).
     */
    public void addSelection(Rectangle rect) {
        if (rect != null && rect.width > 0 && rect.height > 0) {
            selectedAreas.add(rect);
            notifyChanged();
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Callback
    // ─────────────────────────────────────────────────────────────

    /**
     * Set a callback for when selection changes.
     */
    public void setOnSelectionChanged(Runnable r) {
        this.onSelectionChanged = r;
    }

    /**
     * Notify listeners that selection changed.
     */
    private void notifyChanged() {
        if (onSelectionChanged != null) {
            onSelectionChanged.run();
        }
    }
}
