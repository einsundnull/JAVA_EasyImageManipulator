package paint;

import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Per-file canvas state (cached while switching images).
 * Stores undo/redo stacks and element layers for each loaded image.
 */
public class CanvasState {
    public BufferedImage image;
    public final ArrayDeque<BufferedImage> undoStack = new ArrayDeque<>();
    public final ArrayDeque<BufferedImage> redoStack = new ArrayDeque<>();
    public final List<Element> elements = new ArrayList<>();

    public CanvasState(BufferedImage img) {
        this.image = img;
    }
}
