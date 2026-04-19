package paint;

import java.awt.Color;

import javax.swing.SwingUtilities;

/**
 * Handles layout-level updates: ruler strip, nav button positioning,
 * canvas focus borders, and panel visibility. Extracted from SelectiveAlphaEditor.
 */
class LayoutController {

	private final SelectiveAlphaEditor ed;

	LayoutController(SelectiveAlphaEditor ed) {
		this.ed = ed;
	}

	// ── Ruler ─────────────────────────────────────────────────────────────────

	/** Re-builds the ruler strip layout around the scrollPane. */
	void buildRulerLayout() {
		ed.ci(0).viewportPanel.remove(ed.rulerNorthBar);
		ed.ci(0).viewportPanel.remove(ed.vRuler);
		if (ed.showRuler) {
			ed.ci(0).viewportPanel.add(ed.rulerNorthBar, java.awt.BorderLayout.NORTH);
			ed.ci(0).viewportPanel.add(ed.vRuler, java.awt.BorderLayout.WEST);
		}
		ed.ci(0).viewportPanel.revalidate();
		ed.ci(0).viewportPanel.repaint();
	}

	// ── Nav buttons ───────────────────────────────────────────────────────────

	void repositionNavButtons(int idx) {
		CanvasInstance c = ed.ci(idx);
		if (c.prevNavButton == null)
			return;
		int h = c.layeredPane.getHeight(), bh = 80, bw = 36;
		int y = Math.max(0, (h - bh) / 2);
		c.prevNavButton.setBounds(8, y, bw, bh);
		c.nextNavButton.setBounds(c.layeredPane.getWidth() - bw - 8, y, bw, bh);
	}

	// ── Focus borders ─────────────────────────────────────────────────────────

	/**
	 * Updates focus borders and element panel: active canvas gets green border only
	 * when second canvas is visible.
	 */
	void updateCanvasFocusBorder() {
		boolean showBorder = ed.secondCanvasBtn.isEnabled() && ed.firstCanvasBtn.isSelected() && ed.secondCanvasBtn.isSelected();
		for (int i = 0; i < 2; i++) {
			CanvasInstance c = ed.ci(i);
			if (c.layeredPane == null || c.viewportPanel == null)
				continue;
			if (showBorder && i == ed.activeCanvasIndex) {
				javax.swing.border.Border greenBorder = new javax.swing.border.LineBorder(new Color(0, 220, 0), 3, false);
				c.viewportPanel.setBorder(greenBorder);
				c.layeredPane.setBorder(greenBorder);
			} else {
				c.viewportPanel.setBorder(null);
				c.layeredPane.setBorder(null);
			}
		}
		boolean isPaint = ed.ci().appMode == AppMode.PAINT;
		ed.paintModeBtn.setSelected(isPaint);
		ed.canvasModeBtn.setEnabled(isPaint);
		// Toolbar-Sichtbarkeit immer mit dem Button-Zustand synchronisieren
		if (ed.paintToolbar != null && !ed.paintBarFloating) {
			if (isPaint) ed.paintToolbar.showToolbar();
			else         ed.paintToolbar.hideToolbar();
		}
		ed.updateModeLabel();
		ed.refreshElementPanel();
	}

	// ── Panel visibility ──────────────────────────────────────────────────────

	// ── Status / navigation UI ────────────────────────────────────────────────

	void updateNavigationButtons() {
		CanvasInstance c = ed.ci();
		c.prevNavButton.setEnabled(c.currentImageIndex > 0);
		c.nextNavButton.setEnabled(c.currentImageIndex < c.directoryImages.size() - 1);
	}

	public void updateTitle() {
		CanvasInstance c = ed.ci();
		if (c.sourceFile == null) {
			ed.setTitle("Selective Alpha Editor");
			return;
		}
		String dirty = c.hasUnsavedChanges ? " •" : "";
		String fileName = c.sourceFile.getName();
		String size = c.workingImage != null ? c.workingImage.getWidth() + "x" + c.workingImage.getHeight() + "px" : "?x?";
		String imageCount = (c.currentImageIndex + 1) + "/" + c.directoryImages.size();
		ed.setTitle("Selective Alpha Editor  |  " + fileName + "  |  " + size + "  |  " + imageCount + dirty);
	}

	public void updateStatus() {
		CanvasInstance c = ed.ci();
		if (c.sourceFile == null) {
			ed.statusLabel.setText("Keine Datei geladen");
			return;
		}
		ed.statusLabel.setText(c.sourceFile.getName() + "   |   " + (c.currentImageIndex + 1) + " / "
				+ c.directoryImages.size() + "   |   " + c.workingImage.getWidth() + " × "
				+ c.workingImage.getHeight() + " px");
	}

	public void setBottomButtonsEnabled(boolean enabled) {
		boolean sel = !ed.floodfillMode && ed.ci().appMode == AppMode.ALPHA_EDITOR;
		ed.applyButton.setEnabled(enabled && sel);
		ed.clearSelectionsButton.setEnabled(enabled && sel);
		if (ed.actionPanel == null)
			return;
		for (java.awt.Component c : ed.actionPanel.getComponents())
			if (c instanceof javax.swing.JButton btn && ("resetButton".equals(btn.getName()) || "saveButton".equals(btn.getName())))
				btn.setEnabled(enabled);
	}

	/** Updates visibility of all layout elements independently. */
	void updateLayoutVisibility() {
		if (ed.ci(0).layeredPane != null) {
			boolean show0 = ed.firstCanvasBtn.isSelected();
			ed.ci(0).layeredPane.setVisible(show0);
			if (ed.ci(0).viewportPanel != null && ed.ci(0).workingImage != null) {
				ed.ci(0).viewportPanel.setVisible(show0);
				if (show0 && !ed.canvasWasVisible[0] && ed.ci(0).sourceFile != null)
					SwingUtilities.invokeLater(() -> ed.loadFile(ed.ci(0).sourceFile, 0));
			}
			ed.canvasWasVisible[0] = show0;
		}
		if (ed.ci(1).layeredPane != null) {
			boolean show1 = ed.secondCanvasBtn.isSelected();
			ed.ci(1).layeredPane.setVisible(show1);
			if (ed.ci(1).viewportPanel != null && ed.ci(1).workingImage != null) {
				ed.ci(1).viewportPanel.setVisible(show1);
				if (show1 && !ed.canvasWasVisible[1] && ed.ci(1).sourceFile != null)
					SwingUtilities.invokeLater(() -> ed.loadFile(ed.ci(1).sourceFile, 1));
			}
			ed.canvasWasVisible[1] = show1;
		}
		if (ed.mainDividerPanel != null)
			ed.mainDividerPanel.setVisible(ed.firstCanvasBtn.isSelected() && ed.secondCanvasBtn.isSelected());
		updateCanvasFocusBorder();
		if (ed.galleryWrapper != null) {
			ed.galleryWrapper.revalidate();
			ed.galleryWrapper.repaint();
		}
		// Re-center visible canvases at their current zoom after layout change
		SwingUtilities.invokeLater(() -> SwingUtilities.invokeLater(() -> {
			if (ed.ci(0).workingImage != null && ed.firstCanvasBtn.isSelected())
				ed.centerCanvas(0);
			if (ed.ci(1).workingImage != null && ed.secondCanvasBtn.isSelected())
				ed.centerCanvas(1);
		}));
		syncToggleButtons();
	}

	// ── Button ↔ visibility sync ─────────────────────────────────────────────

	/**
	 * Reads the actual visibility of every panel and writes it into the
	 * corresponding toggle button's selected-state.  Call this after any
	 * code path that changes panel visibility without going through a button.
	 * Safe to call programmatically: setSelected() never fires ActionListeners.
	 */
	void syncToggleButtons() {
		// Canvas I / II
		if (ed.ci(0).layeredPane != null)
			ed.firstCanvasBtn.setSelected(ed.ci(0).layeredPane.isVisible());
		if (ed.ci(1).layeredPane != null)
			ed.secondCanvasBtn.setSelected(ed.ci(1).layeredPane.isVisible());
		// Gallery I / II (primary + secondary)
		if (ed.ci(0).tileGallery != null)
			ed.filmstripBtn.setSelected(ed.ci(0).tileGallery.isVisible());
		if (ed.ci(0).tileGallery2 != null && ed.filmstripBtn2 != null)
			ed.filmstripBtn2.setSelected(ed.ci(0).tileGallery2.isVisible());
		if (ed.ci(1).tileGallery != null)
			ed.secondGalleryBtn.setSelected(ed.ci(1).tileGallery.isVisible());
		if (ed.ci(1).tileGallery2 != null && ed.secondGalleryBtn2 != null)
			ed.secondGalleryBtn2.setSelected(ed.ci(1).tileGallery2.isVisible());
		// Scenes I / II
		if (ed.ci(0).scenesPanel != null)
			ed.scenesBtn.setSelected(ed.ci(0).scenesPanel.isVisible());
		if (ed.ci(1).scenesPanel != null)
			ed.secondScenesBtn.setSelected(ed.ci(1).scenesPanel.isVisible());
		// Elements I / II
		if (ed.elementLayerPanel != null)
			ed.firstElementsBtn.setSelected(ed.elementLayerPanel.isVisible());
		if (ed.elementLayerPanel2 != null)
			ed.secondElementsBtn.setSelected(ed.elementLayerPanel2.isVisible());
		// Maps
		if (ed.mapsPanel != null)
			ed.mapsBtn.setSelected(ed.mapsPanel.isVisible());
		// Drop-zone (only sync if not inside a drag, i.e. not temporarily forced)
		if (ed.rightDropZone != null)
			ed.toggleDropZoneBtn.setSelected(ed.rightDropZone.isVisible());
		// Book panels
		if (ed.bookListPanel   != null) ed.bookListIBtn  .setSelected(ed.bookListPanel  .isVisible());
		if (ed.bookPagesPanel  != null) ed.bookPagesIBtn .setSelected(ed.bookPagesPanel .isVisible());
		if (ed.bookListPanel2  != null) ed.bookListIIBtn .setSelected(ed.bookListPanel2 .isVisible());
		if (ed.bookPagesPanel2 != null) ed.bookPagesIIBtn.setSelected(ed.bookPagesPanel2.isVisible());
		// Page-layout toolbar
		if (ed.pageLayoutBtn    != null && ed.pageLayoutToolbar != null)
			ed.pageLayoutBtn.setSelected(ed.pageLayoutToolbar.isVisible());
	}

	// ── Dirty / refresh ───────────────────────────────────────────────────────

	void updateDirtyUI() {
		for (int i = 0; i < 2; i++) {
			CanvasInstance c = ed.ci(i);
			if (c.tileGallery  != null) c.tileGallery .setDirtyFiles(ed.dirtyFiles);
			if (c.tileGallery2 != null) c.tileGallery2.setDirtyFiles(ed.dirtyFiles);
		}
	}

	/** Rebuilds the element layer panel tiles from the current activeElements. */
	void refreshElementPanel() {
		if (ed.elementLayerPanel != null)
			ed.elementLayerPanel.refresh(ed.ci(0).activeElements);
		if (ed.elementLayerPanel2 != null)
			ed.elementLayerPanel2.refresh(ed.ci(1).activeElements);
	}

	public void markDirty(int idx) {
		CanvasInstance c = ed.ci(idx);
		c.hasUnsavedChanges = true;
		if (c.sourceFile != null)
			ed.dirtyFiles.add(c.sourceFile);
		updateTitle();
		updateDirtyUI();
		refreshElementPanel();
		ed.refreshGalleryThumbnail(idx);
		if (ed.mapsPanel != null) {
			try {
				ed.mapsPanel.refreshMapsList();
			} catch (Exception ex) {
				System.err.println("[WARN] Failed to refresh maps panel: " + ex.getMessage());
			}
		}
		c.canvasPanel.repaint();
		if (ed.showRuler && idx == 0) {
			ed.hRuler.repaint();
			ed.vRuler.repaint();
		}
	}
}
