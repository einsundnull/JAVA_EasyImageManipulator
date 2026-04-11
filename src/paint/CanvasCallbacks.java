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
    Element getSelectedElement();
    void setSelectedElement(Element el);

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

    // Actions
    void pushUndo();
    void markDirty();
    void performFloodfill(Point screenPt);
    void paintDot(Point imagePt);
    void commitFloat();
    void repaintCanvas();
    void updateSelectedElement(Element el);

    // Utilities
    int hitHandle(Point screenPt);
    Rectangle floatRectScreen();
    Rectangle elemRectScreen(Element el);
    Rectangle[] handleRects(Rectangle screenRect);
    Rectangle getActiveSelection();
    BufferedImage deepCopy(BufferedImage src);
}
