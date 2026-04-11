package paint;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

/**
 * Callbacks required by CanvasPanel to interact with SelectiveAlphaEditor.
 * CanvasPanel uses these to access editor state and trigger actions.
 */
public interface CanvasCallbacks {
    // State accessors
    BufferedImage getWorkingImage();
    AppMode getAppMode();
    boolean isFloodfillMode();
    double getZoom();
    void setZoom(double newZoom, Point anchorCanvas);
    JScrollPane getScrollPane();

    // Image-space utilities
    Point screenToImage(Point screenPt);

    // Selection state
    List<Rectangle> getSelectedAreas();
    boolean isSelecting();
    void setSelecting(boolean selecting);
    Point getSelectionStart();
    void setSelectionStart(Point p);
    Point getSelectionEnd();
    void setSelectionEnd(Point p);

    // Element state
    List<Element> getActiveElements();
    Element getSelectedElement();          // primary selected element (or null)
    void setSelectedElement(Element el);   // single-select; clears all others
    List<Element> getSelectedElements();   // all currently selected elements
    void setSelectedElements(List<Element> els);
    void toggleElementSelection(Element el); // shift-click: add/remove from selection
    void moveSelectedElements(int dx, int dy); // move all selected by image-pixel delta

    // Floating selection state
    BufferedImage getFloatingImage();
    Rectangle getFloatRect();
    boolean isDraggingFloat();
    void setDraggingFloat(boolean dragging);
    Point getFloatDragAnchor();
    void setFloatDragAnchor(Point p);
    int getActiveHandle();
    void setActiveHandle(int handle);
    Rectangle getScaleBaseRect();
    void setScaleBaseRect(Rectangle r);
    Point getScaleDragStart();
    void setScaleDragStart(Point p);

    // Paint mode state
    Point getLastPaintPoint();
    void setLastPaintPoint(Point p);
    Point getShapeStartPoint();
    void setShapeStartPoint(Point p);
    BufferedImage getPaintSnapshot();
    void setPaintSnapshot(BufferedImage img);

    // Element layer state
    int getElemActiveHandle();
    void setElemActiveHandle(int handle);
    Rectangle getElemScaleBase();
    void setElemScaleBase(Rectangle r);
    Point getElemScaleStart();
    void setElemScaleStart(Point p);
    boolean isDraggingElement();
    void setDraggingElement(boolean dragging);
    Point getElemDragAnchor();
    void setElemDragAnchor(Point p);

    // Toolbar/Tools
    PaintToolbar getPaintToolbar();

    // Layer panel state
    /** True when the ElementLayerPanel "show all outlines" toggle is active. */
    boolean isShowAllLayerOutlines();
    /** Creates an IMAGE_LAYER Element from a rendered pixel image and adds it to activeElements. */
    void commitTextAsElement(java.awt.image.BufferedImage textImg, int x, int y);

    /**
     * Creates or updates a TEXT_LAYER Element.
     * @param updateId  id of the existing element to replace, or -1 to add a new one
     */
    void commitTextLayer(int updateId, String text, String fontName, int fontSize,
                         boolean bold, boolean italic, java.awt.Color color, int x, int y);

    // Actions
    void pushUndo();
    void markDirty();
    void performFloodfill(Point screenPt);
    void paintDot(Point imagePt);
    void commitFloat();
    void repaintCanvas();
    /** Called when the mouse enters/leaves an element on the canvas. id=-1 means no element. */
    void onCanvasElementHover(int elementId);
    void clearSelection();
    /** Lift pixels from the active selection into a floating selection (MS-Paint style). */
    void liftSelectionToFloat();
    /** True when Paint mode is active AND the Canvas sub-mode toggle is on. */
    boolean isCanvasSubMode();
    /** Lift the current selection rect directly into a new Element layer (Canvas sub-mode). */
    void liftSelectionToElement(Rectangle sel);
    /** Delete selected area pixels (or discard float if one is active). */
    void deleteSelection();
    void updateSelectedElement(Element el);

    // Utilities
    int hitHandle(Point screenPt);
    Rectangle floatRectScreen();
    Rectangle elemRectScreen(Element el);
    Rectangle[] handleRects(Rectangle screenRect);
    Rectangle getActiveSelection();
    BufferedImage deepCopy(BufferedImage src);
}
