package paint;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;

/**
 * Manages element-edit mode: entering, exiting, and committing edits.
 * Extracted from SelectiveAlphaEditor.
 */
class ElementEditController {

	private final SelectiveAlphaEditor ed;

	// ── Element-edit mode state ────────────────────────────────────────────────
	Layer elementEditSourceLayer;    // the layer being edited
	int elementEditSourceIdx;        // canvas that owns the layer
	int elementEditTargetIdx;        // canvas where the temp image is loaded

	ElementEditController(SelectiveAlphaEditor ed) {
		this.ed = ed;
	}

	// ── Bar construction ───────────────────────────────────────────────────────

	/**
	 * Builds the floating action bar shown in a canvas when it is in element-edit
	 * mode.
	 */
	JPanel buildElementEditBar(int idx) {
		JPanel bar = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 6, 6)) {
			@Override
			protected void paintComponent(java.awt.Graphics g) {
				java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
				g2.setColor(getBackground());
				g2.fillRect(0, 0, getWidth(), getHeight());
				g2.dispose();
				super.paintComponent(g);
			}
		};
		bar.setOpaque(false);
		bar.setBackground(new Color(30, 30, 30, 220));
		bar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, AppColors.BORDER));

		JButton btnNewLayer = UIComponentFactory.buildButton("Als neues Layer", AppColors.ACCENT,
				AppColors.ACCENT_HOVER);
		JButton btnThisLayer = UIComponentFactory.buildButton("In diesem Layer übernehmen", new Color(60, 140, 60),
				new Color(40, 110, 40));
		JButton btnNewImage = UIComponentFactory.buildButton("Als neues Image", AppColors.BTN_BG, AppColors.BTN_HOVER);
		JButton btnAbort = UIComponentFactory.buildButton("Abbrechen", new Color(160, 50, 50), new Color(130, 30, 30));
		JButton btnClose = UIComponentFactory.buildButton("Close", AppColors.BTN_BG, AppColors.BTN_HOVER);

		for (JButton b : new JButton[] { btnNewLayer, btnThisLayer, btnNewImage, btnAbort, btnClose })
			b.setForeground(Color.WHITE);

		btnNewLayer.addActionListener(e -> elementEditAsNewLayer(idx));
		btnThisLayer.addActionListener(e -> elementEditIntoSourceLayer(idx));
		btnNewImage.addActionListener(e -> elementEditAsNewImage(idx));
		btnAbort.addActionListener(e -> elementEditAbort(idx));
		btnClose.addActionListener(e -> elementEditClose(idx));

		bar.add(btnNewLayer);
		bar.add(btnThisLayer);
		bar.add(btnNewImage);
		bar.add(btnAbort);
		bar.add(btnClose);
		return bar;
	}

	void repositionElementEditBar(int idx) {
		CanvasInstance c = ed.ci(idx);
		if (c.elementEditBar == null)
			return;
		int w = c.layeredPane.getWidth();
		int h = c.layeredPane.getHeight();
		int bh = 44;
		c.elementEditBar.setBounds(0, h - bh, w, bh);
	}

	// ── Mode entry/exit ────────────────────────────────────────────────────────

	/**
	 * Enter element-edit mode: load the element as a temp image into the target
	 * canvas.
	 */
	void activateElementEditMode(int targetIdx, Layer sourceLayer, int sourceIdx) {
		if (elementEditSourceLayer != null) {
			ed.showErrorDialog("Bearbeitung aktiv", "Schließe zuerst die aktuelle Bearbeitung ab.");
			return;
		}
		elementEditSourceLayer = sourceLayer;
		elementEditSourceIdx = sourceIdx;
		elementEditTargetIdx = targetIdx;

		CanvasInstance c = ed.ci(targetIdx);
		repositionElementEditBar(targetIdx);
		c.elementEditBar.setVisible(true);
		c.layeredPane.revalidate();
		c.layeredPane.repaint();
	}

	/** Exit element-edit mode without any transfer. */
	private void exitElementEditMode(int targetIdx) {
		elementEditSourceLayer = null;
		CanvasInstance c = ed.ci(targetIdx);
		c.elementEditBar.setVisible(false);
		c.layeredPane.repaint();
	}

	/** Resets element drag/scale state for the given canvas when switching away. */
	void resetElementDragState(int canvasIdx) {
		CanvasInstance c = ed.ci(canvasIdx);
		c.draggingElement = false;
		c.elemDragAnchor = null;
		c.elemActiveHandle = -1;
		c.elemScaleBase = null;
		c.elemScaleStart = null;
	}

	// ── Button actions ─────────────────────────────────────────────────────────

	/**
	 * Add the current workingImage of the target canvas as a new layer on the
	 * source canvas.
	 */
	private void elementEditAsNewLayer(int targetIdx) {
		if (elementEditSourceLayer == null)
			return;
		CanvasInstance src = ed.ci(elementEditSourceIdx);
		CanvasInstance tgt = ed.ci(targetIdx);
		if (tgt.workingImage == null) {
			exitElementEditMode(targetIdx);
			return;
		}

		BufferedImage img = ed.deepCopy(tgt.workingImage);
		ImageLayer newLayer = new ImageLayer(src.nextElementId++, img, elementEditSourceLayer.x(),
				elementEditSourceLayer.y(), img.getWidth(), img.getHeight());
		src.activeElements.add(newLayer);
		src.selectedElements.clear();
		src.selectedElements.add(newLayer);
		src.hasUnsavedChanges = true;
		ed.markDirty(elementEditSourceIdx);
		ed.refreshElementPanel();
		if (src.canvasPanel != null)
			src.canvasPanel.repaint();
		exitElementEditMode(targetIdx);
		ToastNotification.show(ed, "Als neues Layer eingefügt");
	}

	/**
	 * Replace the source layer's image with the current workingImage of the target
	 * canvas.
	 */
	private void elementEditIntoSourceLayer(int targetIdx) {
		if (elementEditSourceLayer == null)
			return;
		CanvasInstance src = ed.ci(elementEditSourceIdx);
		CanvasInstance tgt = ed.ci(targetIdx);
		if (tgt.workingImage == null) {
			exitElementEditMode(targetIdx);
			return;
		}

		if (!(elementEditSourceLayer instanceof ImageLayer)) {
			ed.showErrorDialog("Nicht möglich", "Nur ImageLayer können überschrieben werden.");
			return;
		}
		BufferedImage img = ed.deepCopy(tgt.workingImage);
		List<Layer> els = src.activeElements;
		for (int i = 0; i < els.size(); i++) {
			if (els.get(i).id() == elementEditSourceLayer.id()) {
				ImageLayer old = (ImageLayer) els.get(i);
				ImageLayer replacement = new ImageLayer(old.id(), img, old.x(), old.y(), img.getWidth(),
						img.getHeight());
				els.set(i, replacement);
				for (int j = 0; j < src.selectedElements.size(); j++) {
					if (src.selectedElements.get(j).id() == old.id()) {
						src.selectedElements.set(j, replacement);
						break;
					}
				}
				break;
			}
		}
		src.hasUnsavedChanges = true;
		ed.markDirty(elementEditSourceIdx);
		ed.refreshElementPanel();
		if (src.canvasPanel != null)
			src.canvasPanel.repaint();
		exitElementEditMode(targetIdx);
		ToastNotification.show(ed, "Layer übernommen");
	}

	/**
	 * Keep the target canvas as a normal standalone image — just exit edit mode.
	 */
	private void elementEditAsNewImage(int targetIdx) {
		exitElementEditMode(targetIdx);
		ToastNotification.show(ed, "Als neues Image behalten");
	}

	/** Discard changes: clear the target canvas and exit edit mode. */
	private void elementEditAbort(int targetIdx) {
		CanvasInstance c = ed.ci(targetIdx);
		c.workingImage = null;
		c.originalImage = null;
		c.sourceFile = null;
		c.undoStack.clear();
		c.redoStack.clear();
		c.activeElements.clear();
		c.selectedElements.clear();
		c.hasUnsavedChanges = false;
		c.viewportPanel.setVisible(false);
		if (c.viewportPanel.getParent() != null)
			c.layeredPane.remove(c.viewportPanel);
		c.layeredPane.add(c.dropHintPanel, JLayeredPane.DEFAULT_LAYER);
		int w = c.layeredPane.getWidth(), h = c.layeredPane.getHeight();
		c.dropHintPanel.setBounds(0, 0, Math.max(w, 1), Math.max(h, 1));
		exitElementEditMode(targetIdx);
		ed.refreshElementPanel();
		ed.updateTitle();
		c.layeredPane.revalidate();
		c.layeredPane.repaint();
	}

	/** Close the edit bar without any action (keep the canvas as-is). */
	private void elementEditClose(int targetIdx) {
		exitElementEditMode(targetIdx);
	}

	// ── Visibility guard ───────────────────────────────────────────────────────

	/**
	 * Re-asserts element-edit bar visibility after any operation that might disturb
	 * it.
	 */
	void ensureElementEditBarVisible() {
		if (elementEditSourceLayer == null)
			return;
		CanvasInstance tc = ed.canvases[elementEditTargetIdx];
		if (tc.elementEditBar == null)
			return;
		repositionElementEditBar(elementEditTargetIdx);
		tc.elementEditBar.setVisible(true);
		tc.layeredPane.repaint();
	}
}
