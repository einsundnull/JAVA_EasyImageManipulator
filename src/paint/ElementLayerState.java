package paint;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages element layers (non-destructive layers like TextLayer, ImageLayer).
 * Encapsulates all element layer operations and multi-selection state.
 */
public class ElementLayerState {

    // Layer state
    private List<Layer> activeElements = new ArrayList<>();
    private int nextElementId = 1;
    private List<Layer> selectedElements = new ArrayList<>();

    // Drag/scale state
    private boolean draggingElement = false;
    private Point elemDragAnchor = null;
    private int elemActiveHandle = -1;
    private Rectangle elemScaleBase = null;
    private Point elemScaleStart = null;

    // Mode flag
    private boolean insertAsElement = false;

    // UI/Context references
    private BufferedImage workingImage = null;
    private AppMode appMode = AppMode.PAINT;
    private ElementLayerPanel elementLayerPanel = null;
    private CanvasPanel canvasPanel = null;

    // Callback for undo
    private Runnable onUndo = null;
    private Runnable onMarkDirty = null;

    public ElementLayerState() {
    }

    // ─────────────────────────────────────────────────────────────
    // Public API - Selection Operations
    // ─────────────────────────────────────────────────────────────

    /**
     * Get the primary selected layer (index 0), or null if none selected.
     */
    public Layer getSelectedLayer() {
        return selectedElements.isEmpty() ? null : selectedElements.get(0);
    }

    /**
     * Get all selected layers.
     */
    public List<Layer> getSelectedLayers() {
        return new ArrayList<>(selectedElements);
    }

    /**
     * Set the selected layer (clears previous selection).
     * CONSOLIDATES 5+ DUPLICATE "clear + add + refresh" patterns.
     */
    public void setSelectedLayer(Layer el) {
        selectedElements.clear();
        if (el != null) {
            selectedElements.add(el);
        }
    }

    /**
     * Toggle a layer in/out of the multi-selection.
     * New primary is put at index 0.
     */
    public void toggleSelection(Layer el) {
        for (int i = 0; i < selectedElements.size(); i++) {
            if (selectedElements.get(i).id() == el.id()) {
                selectedElements.remove(i);
                return;
            }
        }
        selectedElements.add(0, el);
    }

    /**
     * Clear all selections.
     */
    public void clearSelection() {
        selectedElements.clear();
    }

    /**
     * Get all active layers.
     */
    public List<Layer> getActiveElements() {
        return new ArrayList<>(activeElements);
    }

    /**
     * Get layer by ID.
     */
    public Layer getLayerById(int id) {
        for (Layer l : activeElements) {
            if (l.id() == id) return l;
        }
        return null;
    }

    // ─────────────────────────────────────────────────────────────
    // Public API - Layer Manipulation
    // ─────────────────────────────────────────────────────────────

    /**
     * Add a new layer.
     */
    public void addElement(Layer el) {
        if (el != null) {
            activeElements.add(el);
            if (onMarkDirty != null) onMarkDirty.run();
            refreshUI();
        }
    }

    /**
     * Update a layer in place (e.g., after position change).
     * Updates both activeElements and selectedElements.
     */
    public void updateElement(Layer el) {
        if (el == null) return;
        for (int i = 0; i < activeElements.size(); i++) {
            if (activeElements.get(i).id() == el.id()) {
                activeElements.set(i, el);
                break;
            }
        }
        for (int i = 0; i < selectedElements.size(); i++) {
            if (selectedElements.get(i).id() == el.id()) {
                selectedElements.set(i, el);
                break;
            }
        }
    }

    /**
     * Move all selected elements by (dx, dy) in image-space.
     */
    public void moveSelectedElements(int dx, int dy) {
        if (dx == 0 && dy == 0) return;
        for (int i = 0; i < selectedElements.size(); i++) {
            Layer el = selectedElements.get(i);
            Layer updated = el.withPosition(el.x() + dx, el.y() + dy);
            updateElement(updated);
        }
        if (onMarkDirty != null) onMarkDirty.run();
    }

    /**
     * Delete all selected layers without merging.
     */
    public void deleteSelectedElements() {
        if (selectedElements.isEmpty()) return;
        for (Layer el : selectedElements) {
            activeElements.removeIf(e -> e.id() == el.id());
        }
        selectedElements.clear();
        if (onMarkDirty != null) onMarkDirty.run();
        refreshUI();
    }

    /**
     * Merge all selected layers onto canvas and remove from layer list.
     */
    public void mergeSelectedElements() {
        if (selectedElements.isEmpty() || workingImage == null) return;
        if (onUndo != null) onUndo.run();
        for (Layer el : new ArrayList<>(selectedElements)) {
            if (el instanceof ImageLayer il) {
                Graphics2D mg = workingImage.createGraphics();
                mg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                mg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                ElementController.drawImageLayer(mg, il);
                mg.dispose();
            } else if (el instanceof TextLayer tl) {
                BufferedImage rendered = renderTextLayerToImage(tl);
                PaintEngine.pasteRegion(workingImage, rendered, new Point(el.x(), el.y()));
            }
            activeElements.removeIf(e -> e.id() == el.id());
        }
        selectedElements.clear();
        if (onMarkDirty != null) onMarkDirty.run();
        refreshUI();
    }

    // ─────────────────────────────────────────────────────────────
    // Public API - Layer Creation
    // ─────────────────────────────────────────────────────────────

    /**
     * Create a text layer from text parameters.
     */
    public TextLayer createTextLayer(int id, String text, String fontName,
                                      int fontSize, boolean bold, boolean italic,
                                      Color color, int x, int y) {
        return TextLayer.of(id, text, fontName, fontSize, bold, italic, color, x, y);
    }

    /**
     * Commit a text layer update (create or update).
     * @param updateId Layer ID to update, or -1 to create new
     */
    public void commitTextLayer(int updateId, String text, String fontName,
                                int fontSize, boolean bold, boolean italic,
                                Color color, int x, int y) {
        if ((text == null || text.isEmpty()) || appMode != AppMode.PAINT) return;

        Layer newLayer;
        boolean isUpdate = false;

        if (updateId >= 0) {
            // Replace existing layer
            newLayer = createTextLayer(updateId, text, fontName, fontSize, bold, italic, color, x, y);
            for (int i = 0; i < activeElements.size(); i++) {
                if (activeElements.get(i).id() == updateId) {
                    activeElements.set(i, newLayer);
                    setSelectedLayer(newLayer);
                    isUpdate = true;
                    break;
                }
            }
            if (!isUpdate) {
                activeElements.add(newLayer);
                setSelectedLayer(newLayer);
            }
        } else {
            // Create new layer with fresh ID
            newLayer = createTextLayer(nextElementId++, text, fontName, fontSize, bold, italic, color, x, y);
            activeElements.add(newLayer);
            setSelectedLayer(newLayer);
        }

        if (onMarkDirty != null) onMarkDirty.run();
        refreshUI();
    }

    /**
     * Commit a text image as a new element layer.
     */
    public void commitTextAsElement(BufferedImage textImg, int x, int y) {
        if (textImg == null || appMode != AppMode.PAINT) return;
        Layer el = new ImageLayer(nextElementId++, textImg, x, y, textImg.getWidth(), textImg.getHeight());
        activeElements.add(el);
        setSelectedLayer(el);
        if (onMarkDirty != null) onMarkDirty.run();
        refreshUI();
    }

    /**
     * Insert current selection as a new element layer.
     */
    public void insertSelectionAsElement(Rectangle selection, BufferedImage clipboard) {
        BufferedImage src = null;
        if (selection != null && workingImage != null) {
            src = PaintEngine.cropRegion(workingImage, selection);
        } else if (clipboard != null) {
            src = deepCopy(clipboard);
        }
        if (src == null) return;

        int ex = selection != null ? selection.x : 0;
        int ey = selection != null ? selection.y : 0;
        Layer el = new ImageLayer(nextElementId++, src, ex, ey, src.getWidth(), src.getHeight());
        activeElements.add(el);
        setSelectedLayer(el);
        if (onMarkDirty != null) onMarkDirty.run();
        refreshUI();
    }

    // ─────────────────────────────────────────────────────────────
    // Public API - Drag/Scale State
    // ─────────────────────────────────────────────────────────────

    public boolean isDragging() {
        return draggingElement;
    }

    public void setDragging(boolean b) {
        draggingElement = b;
    }

    public Point getDragAnchor() {
        return elemDragAnchor;
    }

    public void setDragAnchor(Point p) {
        elemDragAnchor = p;
    }

    public int getActiveHandle() {
        return elemActiveHandle;
    }

    public void setActiveHandle(int h) {
        elemActiveHandle = h;
    }

    public Rectangle getScaleBase() {
        return elemScaleBase;
    }

    public void setScaleBase(Rectangle r) {
        elemScaleBase = r;
    }

    public Point getScaleStart() {
        return elemScaleStart;
    }

    public void setScaleStart(Point p) {
        elemScaleStart = p;
    }

    // ─────────────────────────────────────────────────────────────
    // Public API - IDs & Mode
    // ─────────────────────────────────────────────────────────────

    public int getNextElementId() {
        return nextElementId++;
    }

    public boolean isInsertAsElementMode() {
        return insertAsElement;
    }

    public void setInsertAsElementMode(boolean b) {
        insertAsElement = b;
    }

    public void clear() {
        activeElements.clear();
        selectedElements.clear();
        nextElementId = 1;
        draggingElement = false;
        elemDragAnchor = null;
        elemActiveHandle = -1;
        elemScaleBase = null;
        elemScaleStart = null;
    }

    // ─────────────────────────────────────────────────────────────
    // Private Helpers
    // ─────────────────────────────────────────────────────────────

    /**
     * Render a TextLayer to a BufferedImage.
     */
    private BufferedImage renderTextLayerToImage(TextLayer tl) {
        int style = (tl.fontBold() ? Font.BOLD : 0) | (tl.fontItalic() ? Font.ITALIC : 0);
        Font font = new Font(tl.fontName(), style, Math.max(6, tl.fontSize()));
        BufferedImage dummy = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        FontMetrics fm = dummy.createGraphics().getFontMetrics(font);
        String[] lines = tl.text().split("\n", -1);
        int w = 1;
        for (String l : lines) w = Math.max(w, fm.stringWidth(l));
        int h = Math.max(1, fm.getHeight() * lines.length);
        BufferedImage img = new BufferedImage(w + TextLayer.TEXT_PADDING * 2,
                h + TextLayer.TEXT_PADDING * 2, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setFont(font);
        g2.setColor(tl.fontColor());
        for (int i = 0; i < lines.length; i++) {
            g2.drawString(lines[i], TextLayer.TEXT_PADDING,
                    TextLayer.TEXT_PADDING + fm.getHeight() * i + fm.getAscent());
        }
        g2.dispose();
        return img;
    }

    private BufferedImage deepCopy(BufferedImage src) {
        if (src == null) return null;
        BufferedImage dst = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = dst.createGraphics();
        g2.drawImage(src, 0, 0, null);
        g2.dispose();
        return dst;
    }

    private void refreshUI() {
        if (elementLayerPanel != null) elementLayerPanel.refresh(activeElements);
        if (canvasPanel != null) canvasPanel.repaint();
    }

    // ─────────────────────────────────────────────────────────────
    // Setters for Context
    // ─────────────────────────────────────────────────────────────

    public void setWorkingImage(BufferedImage img) {
        this.workingImage = img;
    }

    public void setAppMode(AppMode mode) {
        this.appMode = mode;
    }

    public void setElementLayerPanel(ElementLayerPanel panel) {
        this.elementLayerPanel = panel;
    }

    public void setCanvasPanel(CanvasPanel panel) {
        this.canvasPanel = panel;
    }

    public void setOnUndo(Runnable r) {
        this.onUndo = r;
    }

    public void setOnMarkDirty(Runnable r) {
        this.onMarkDirty = r;
    }
}
