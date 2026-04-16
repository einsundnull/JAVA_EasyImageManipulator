package paint;

import javax.swing.SwingUtilities;

/**
 * Handles all mode-toggle operations: Alpha, Paint, Canvas sub-mode, Book, and
 * Scene modes; element panel visibility; and the mode label. Extracted from
 * SelectiveAlphaEditor.
 */
class ModeController {

	private final SelectiveAlphaEditor ed;

	ModeController(SelectiveAlphaEditor ed) {
		this.ed = ed;
	}

	void toggleAlphaMode() {
		CanvasInstance c = ed.ci();
		if (ed.ci().appMode != AppMode.ALPHA_EDITOR)
			return;
		if (!ed.floodfillMode && !ed.alphaPaintMode) {
			ed.floodfillMode = true;
			ed.modeLabel.setText("Modus: Floodfill");
		} else if (ed.floodfillMode && !ed.alphaPaintMode) {
			ed.floodfillMode = false;
			ed.alphaPaintMode = true;
			ed.modeLabel.setText("Modus: Alpha Paint (Pinsel)");
		} else {
			ed.alphaPaintMode = false;
			ed.modeLabel.setText("Modus: Selective Alpha");
		}
		boolean sel = !ed.floodfillMode && !ed.alphaPaintMode;
		ed.applyButton.setEnabled(sel && c.sourceFile != null);
		ed.clearSelectionsButton.setEnabled(sel && c.sourceFile != null);
		c.selectedAreas.clear();
		c.lastPaintPoint = null;
		c.canvasPanel.repaint();
	}

	void togglePaintMode() {
		CanvasInstance c = ed.ci();
		boolean entering = ed.paintModeBtn.isSelected();
		if (entering) {
			ed.ci().appMode = AppMode.PAINT;
		} else {
			if (ed.canvasModeBtn.isSelected()) {
				ed.canvasModeBtn.setSelected(false);
				setElementPanelVisible(false);
			}
			ed.ci().appMode = AppMode.ALPHA_EDITOR;
		}
		ed.canvasModeBtn.setEnabled(entering);
		updateModeLabel();
		if (entering) {
			ed.paintToolbar.showToolbar();
			ed.applyButton.setEnabled(false);
			ed.clearSelectionsButton.setEnabled(false);
		} else {
			ed.paintToolbar.hideToolbar();
			boolean sel = !ed.floodfillMode;
			ed.applyButton.setEnabled(sel && c.sourceFile != null);
			ed.clearSelectionsButton.setEnabled(sel && c.sourceFile != null);
		}
		c.selectedAreas.clear();
		c.lastPaintPoint = null;
		c.shapeStartPoint = null;
		SwingUtilities.invokeLater(() -> SwingUtilities.invokeLater(() -> {
			if (!c.userHasManuallyZoomed) {
				ed.fitToViewport(ed.activeCanvasIndex);
			} else {
				ed.centerCanvasX();
			}
			c.canvasPanel.repaint();
		}));
		c.paintSnapshot = null;
		c.canvasPanel.setCursor(entering
				? java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.CROSSHAIR_CURSOR)
				: java.awt.Cursor.getDefaultCursor());
		c.canvasPanel.repaint();
	}

	void toggleCanvasMode() {
		boolean entering = ed.canvasModeBtn.isSelected();
		if (entering) {
			setElementPanelVisible(true);
		} else {
			setElementPanelVisible(false);
		}
		updateModeLabel();
		ed.ci().canvasPanel.repaint();
	}

	void toggleBookMode() {
		boolean entering = ed.bookModeBtn.isSelected();
		if (entering && ed.sceneModeBtn.isSelected()) {
			ed.sceneModeBtn.setSelected(false);
		}
		updateModeLabel();
		ed.ci().canvasPanel.repaint();
	}

	void toggleSceneMode() {
		boolean entering = ed.sceneModeBtn.isSelected();
		if (entering && ed.bookModeBtn.isSelected()) {
			ed.bookModeBtn.setSelected(false);
		}
		updateModeLabel();
		ed.ci().canvasPanel.repaint();
	}

	void setElementPanelVisible(boolean visible) {
		if (visible) {
			if (ed.activeCanvasIndex == 0) {
				ed.elementLayerPanel.setVisible(true);
				if (ed.elementLayerPanel2 != null)
					ed.elementLayerPanel2.setVisible(false);
			} else {
				ed.elementLayerPanel.setVisible(false);
				if (ed.elementLayerPanel2 != null)
					ed.elementLayerPanel2.setVisible(true);
			}
			ed.refreshElementPanel();
		} else {
			ed.elementLayerPanel.setVisible(false);
			if (ed.elementLayerPanel2 != null)
				ed.elementLayerPanel2.setVisible(false);
		}
		ed.galleryWrapper.revalidate();
		ed.galleryWrapper.repaint();
		SwingUtilities.invokeLater(
				() -> SwingUtilities.invokeLater(() -> ed.reloadCurrentImage(ed.activeCanvasIndex)));
	}

	void updateModeLabel() {
		if (ed.ci().appMode == AppMode.PAINT) {
			StringBuilder sb = new StringBuilder("Modus: Paint");
			if (ed.canvasModeBtn.isSelected())
				sb.append(" / Canvas");
			if (ed.bookModeBtn.isSelected())
				sb.append(" / Buch");
			if (ed.sceneModeBtn.isSelected())
				sb.append(" / Szene");
			ed.modeLabel.setText(sb.toString());
		} else {
			StringBuilder sb = new StringBuilder(
					"Modus: " + (ed.floodfillMode ? "Floodfill" : "Selective Alpha"));
			if (ed.bookModeBtn.isSelected())
				sb.append(" / Buch");
			if (ed.sceneModeBtn.isSelected())
				sb.append(" / Szene");
			ed.modeLabel.setText(sb.toString());
		}
	}
}
