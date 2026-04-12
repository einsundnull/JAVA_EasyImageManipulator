package paint.copy;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;

import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Callbacks required by EditorDialogs to interact with SelectiveAlphaEditor.
 * Dialogs use these callbacks to perform actions like pushing undo, marking dirty, etc.
 */
public interface EditorDialogCallbacks {
    // Image state access
    BufferedImage getWorkingImage();
    void setWorkingImage(BufferedImage img);

    BufferedImage getOriginalImage();
    void setOriginalImage(BufferedImage img);

    File getSourceFile();
    void setSourceFile(File f);

    JPanel getCanvasWrapper();
    JPanel getCanvasPanel();
    JLabel getZoomLabel();

    // Canvas colors
    java.awt.Color getCanvasBg1();
    java.awt.Color getCanvasBg2();
    void setCanvasBg1(java.awt.Color c);
    void setCanvasBg2(java.awt.Color c);

    // State management
    void pushUndo();
    void markDirty();
    void saveCurrentState();
    BufferedImage deepCopy(BufferedImage img);
    void swapToImageView();
    void fitToViewport();
    void centerCanvas();
    void updateTitle();
    void updateStatus();
    void setBottomButtonsEnabled(boolean enabled);
    void repaintCanvas();
    void setZoom(double zoom, java.awt.Point anchor);
    ArrayList<javax.swing.JFrame> getUndoStack();
    ArrayList<javax.swing.JFrame> getRedoStack();

    // Dialog helpers
    void showErrorDialog(String title, String message);
    void showInfoDialog(String title, String message);
}
