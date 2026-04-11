package paint;

import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;

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

    // Layer state
    List<Layer> getActiveElements();
    Layer getSelectedElement();               // primary selected layer (or null)
    void setSelectedElement(Layer el);        // single-select; clears all others
    List<Layer> getSelectedElements();        // all currently selected layers
    void setSelectedElements(List<Layer> els);
    void toggleElementSelection(Layer el);    // shift-click: add/remove from selection
    void moveSelectedElements(int dx, int dy); // move all selected by image-pixel delta
    int getNextElementId();                   // generate next unique layer ID
    void addElement(Layer el);                // add a new layer to activeElements

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

    // Layer interaction state
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
    /** Creates an IMAGE_LAYER from a rendered pixel image and adds it to activeElements. */
    void commitTextAsElement(java.awt.image.BufferedImage textImg, int x, int y);

    /**
     * Creates or updates a TextLayer.
     * @param updateId  id of the existing layer to replace, or -1 to add a new one
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
    /** Called when the mouse enters/leaves a layer on the canvas. id=-1 means no layer. */
    void onCanvasElementHover(int elementId);
    void clearSelection();
    /** Lift pixels from the active selection into a floating selection (MS-Paint style). */
    void liftSelectionToFloat();
    /** True when Paint mode is active AND the Canvas sub-mode toggle is on. */
    boolean isCanvasSubMode();
    /** Lift the current selection rect directly into a new ImageLayer (Canvas sub-mode). */
    void liftSelectionToElement(Rectangle sel);
    /** Delete selected area pixels (or discard float if one is active). */
    void deleteSelection();
    void updateSelectedElement(Layer el);

    // Utilities
    int hitHandle(Point screenPt);
    Rectangle floatRectScreen();
    Rectangle elemRectScreen(Layer el);
    Rectangle[] handleRects(Rectangle screenRect);
    Rectangle getActiveSelection();
    BufferedImage deepCopy(BufferedImage src);

    // Canvas background colors
    Color getCanvasBg1();
    Color getCanvasBg2();
}
