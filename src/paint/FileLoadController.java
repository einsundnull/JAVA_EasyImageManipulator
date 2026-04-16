package paint;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.JLayeredPane;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;

/**
 * Handles file loading, directory indexing, navigation, canvas viewport
 * fitting, and related canvas-state initialization. Extracted from
 * SelectiveAlphaEditor.
 */
class FileLoadController {

	private final SelectiveAlphaEditor ed;

	FileLoadController(SelectiveAlphaEditor ed) {
		this.ed = ed;
	}

	private static final String[] SUPPORTED_EXTENSIONS = { "png", "jpg", "jpeg", "bmp", "gif" };

	static boolean isSupportedFile(File f) {
		if (f == null || !f.isFile())
			return false;
		String n = f.getName().toLowerCase();
		for (String e : SUPPORTED_EXTENSIONS)
			if (n.endsWith("." + e))
				return true;
		return false;
	}

	/** Converts image to TYPE_INT_ARGB (clean ARGB copy). */
	BufferedImage normalizeImage(BufferedImage src) {
		if (src.getType() == BufferedImage.TYPE_INT_ARGB)
			return ed.deepCopy(src);
		BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
		java.awt.Graphics2D g2 = out.createGraphics();
		g2.drawImage(src, 0, 0, null);
		g2.dispose();
		return out;
	}

	/** Convenience: loads into active canvas. */
	void loadFile(File file) {
		loadFile(file, ed.activeCanvasIndex);
	}

	/** Core version: loads into specified canvas. */
	void loadFile(File file, int idx) {
		CanvasInstance c = ed.ci(idx);

		if (c.sourceFile != null)
			saveCurrentState(idx);

		CanvasInstance.CanvasFileState cached = c.fileCache.get(file);
		if (cached != null) {
			c.workingImage = cached.image;
			c.undoStack.clear();
			c.undoStack.addAll(cached.undoStack);
			c.redoStack.clear();
			c.redoStack.addAll(cached.redoStack);
			c.activeElements = new ArrayList<>(cached.elements);
		} else {
			try {
				BufferedImage img = ImageIO.read(file);
				if (img == null) {
					ed.showErrorDialog("Ladefehler", "Bild konnte nicht gelesen werden:\n" + file.getName());
					return;
				}
				c.originalImage = img;
				c.workingImage = ed.normalizeImage(c.originalImage);
				c.undoStack.clear();
				c.redoStack.clear();
				c.activeElements = new ArrayList<>();
				c.selectedElements.clear();
				CanvasInstance.CanvasFileState cs = new CanvasInstance.CanvasFileState(c.workingImage);
				c.fileCache.put(file, cs);
			} catch (IOException e) {
				ed.showErrorDialog("Ladefehler", "Fehler:\n" + e.getMessage());
				return;
			}
		}

		c.sourceFile = file;
		c.hasUnsavedChanges = ed.dirtyFiles.contains(file);
		c.selectedAreas.clear();
		c.isSelecting = false;
		c.selectionStart = null;
		c.selectionEnd = null;
		c.lastPaintPoint = null;
		c.shapeStartPoint = null;
		c.paintSnapshot = null;
		c.floatingImg = null;
		c.floatRect = null;
		c.isDraggingFloat = false;
		c.floatDragAnchor = null;
		c.activeHandle = -1;
		c.scaleBaseRect = null;
		c.scaleDragStart = null;
		c.selectedElements.clear();
		c.activeSceneFile = null;
		c.draggingElement = false;
		c.elemDragAnchor = null;
		c.elemActiveHandle = -1;
		c.elemScaleBase = null;
		c.elemScaleStart = null;
		if (c.canvasPanel != null)
			c.canvasPanel.resetInputState();

		c.appMode = ed.defaultAppMode;

		try {
			if (ed.projectManager.getProjectName() != null) {
				List<Layer> savedLayers = ed.projectManager.loadScene(file);
				if (savedLayers != null && !savedLayers.isEmpty()) {
					c.activeElements = savedLayers;
					c.selectedElements.clear();
				}
				double savedZoom = ed.projectManager.loadSceneZoom(file);
				if (savedZoom > 0) {
					c.zoom = savedZoom;
					c.userHasManuallyZoomed = true;
				}
				AppMode savedMode = ed.projectManager.loadSceneMode(file);
				if (savedMode != null)
					c.appMode = savedMode;
			}
		} catch (IOException e) {
			System.err.println("[WARN] Fehler beim Laden der Szenen-Daten: " + e.getMessage());
		}

		ed.activeCanvasIndex = idx;
		ed.updateCanvasFocusBorder();

		if (!file.getParentFile().equals(new File(System.getProperty("java.io.tmpdir"))))
			indexDirectory(file, idx);
		swapToImageView(idx);

		CanvasInstance.PreloadedFileState preloaded = c.preloadCache.get(file);
		if (preloaded != null && preloaded.image != null)
			c.workingImage = preloaded.image;

		SwingUtilities.invokeLater(() -> fitToViewport(idx));

		ed.refreshElementPanel();
		ed.updateNavigationButtons();
		ed.updateTitle();
		ed.updateStatus();
		ed.setBottomButtonsEnabled(true);
		ed.preloadNextImages(idx);
	}

	/**
	 * Loads a scene background image onto the canvas WITHOUT touching
	 * TileGalleryPanel. Used exclusively when clicking a scene tile.
	 */
	void loadSceneBackground(File file, int idx) {
		CanvasInstance c = ed.ci(idx);
		if (c.sourceFile != null)
			saveCurrentState(idx);
		try {
			BufferedImage img = ImageIO.read(file);
			if (img == null)
				return;
			c.originalImage = img;
			c.workingImage = ed.normalizeImage(c.originalImage);
			c.undoStack.clear();
			c.redoStack.clear();
			c.activeElements = new ArrayList<>();
			c.selectedElements.clear();
			c.fileCache.put(file, new CanvasInstance.CanvasFileState(c.workingImage));
		} catch (IOException e) {
			System.err.println("[ERROR] loadSceneBackground: " + e.getMessage());
			return;
		}
		c.sourceFile = file;
		c.activeSceneFile = null;
		c.hasUnsavedChanges = false;
		c.selectedAreas.clear();
		c.isSelecting = false;
		c.floatingImg = null;
		c.floatRect = null;
		c.lastPaintPoint = null;
		c.shapeStartPoint = null;
		c.paintSnapshot = null;
		ed.activeCanvasIndex = idx;
		ed.updateCanvasFocusBorder();
		swapToImageView(idx);
		SwingUtilities.invokeLater(() -> fitToViewport(idx));
		ed.refreshElementPanel();
		ed.updateTitle();
		ed.updateStatus();
		ed.setBottomButtonsEnabled(true);
	}

	/** Saves the current canvas state back into the file cache. */
	void saveCurrentState() {
		saveCurrentState(ed.activeCanvasIndex);
	}

	void saveCurrentState(int idx) {
		CanvasInstance c = ed.ci(idx);
		if (c.sourceFile == null || c.workingImage == null)
			return;
		CanvasInstance.CanvasFileState cs = c.fileCache.computeIfAbsent(c.sourceFile,
				k -> new CanvasInstance.CanvasFileState(c.workingImage));
		cs.image = c.workingImage;
		cs.undoStack.clear();
		cs.undoStack.addAll(c.undoStack);
		cs.redoStack.clear();
		cs.redoStack.addAll(c.redoStack);
		cs.elements.clear();
		cs.elements.addAll(c.activeElements);
	}

	/** Recalculates zoom/centering after layout changes without reloading. */
	void reloadCurrentImage(int idx) {
		CanvasInstance c = ed.ci(idx);
		if (c.workingImage == null || !c.viewportPanel.isVisible())
			return;
		fitToViewport(idx);
	}

	void indexDirectory(File file) {
		indexDirectory(file, ed.activeCanvasIndex, LastProjectsManager.CAT_IMAGES);
	}

	void indexDirectory(File file, int idx) {
		indexDirectory(file, idx, LastProjectsManager.CAT_IMAGES);
	}

	void indexDirectory(File file, int idx, String category) {
		CanvasInstance c = ed.ci(idx);
		File dir = file.getParentFile();
		if (dir == null)
			return;
		boolean sameDir = dir.equals(c.lastIndexedDir);
		if (!sameDir) {
			File[] files = dir.listFiles(f -> f.isFile() && SelectiveAlphaEditor.isSupportedFile(f));
			if (files == null)
				return;
			Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
			c.directoryImages = new ArrayList<>(Arrays.asList(files));
			c.lastIndexedDir = dir;
			c.tileGallery.setFiles(c.directoryImages, file);
			try {
				LastProjectsManager.addRecent(category, dir.getAbsolutePath());
			} catch (IOException e) {
				System.err.println("[WARN] Konnte lastProjects nicht speichern: " + e.getMessage());
			}
		} else {
			c.tileGallery.setActiveFile(file);
		}
		c.currentImageIndex = c.directoryImages.indexOf(file);
		c.tileGallery.setVisible(true);
	}

	void navigateImage(int dir) {
		navigateImage(dir, ed.activeCanvasIndex);
	}

	void navigateImage(int dir, int idx) {
		CanvasInstance c = ed.ci(idx);
		if (c.directoryImages.isEmpty())
			return;
		int ni = c.currentImageIndex + dir;
		if (ni < 0 || ni >= c.directoryImages.size())
			return;
		c.currentImageIndex = ni;
		loadFile(c.directoryImages.get(c.currentImageIndex), idx);
		c.tileGallery.scrollToActive();
		ed.preloadNextImages(idx);
	}

	/**
	 * Synchronizes ScenesPanel visibility with button state. Single source of
	 * truth to prevent button/panel mismatches.
	 */
	void setScenesPanelVisible(int idx, boolean visible) {
		CanvasInstance c = ed.ci(idx);
		if (idx == 0) {
			ed.scenesBtn.setSelected(visible);
		} else if (idx == 1) {
			ed.secondScenesBtn.setSelected(visible);
		}
		c.scenesPanel.setVisible(visible);
		if (visible)
			ed.refreshSceneFiles(idx);
		ed.updateLayoutVisibility();
		SwingUtilities.invokeLater(() -> SwingUtilities.invokeLater(() -> reloadCurrentImage(idx)));
	}

	public void swapToImageView(int idx) {
		CanvasInstance c = ed.ci(idx);
		if (c.dropHintPanel.getParent() == c.layeredPane)
			c.layeredPane.remove(c.dropHintPanel);
		if (c.viewportPanel.getParent() == null) {
			int w = Math.max(c.layeredPane.getWidth(), 430);
			int h = Math.max(c.layeredPane.getHeight(), 560);
			c.viewportPanel.setBounds(0, 0, w, h);
			c.layeredPane.add(c.viewportPanel, JLayeredPane.DEFAULT_LAYER);
			ed.setupDropTarget(c.viewportPanel, idx);
			ed.setupDropTarget(c.canvasPanel, idx);
		}
		c.viewportPanel.setVisible(true);

		if (idx == 0) {
			ed.firstElementsBtn.setEnabled(true);
			ed.firstCanvasBtn.setEnabled(true);
		}

		if (idx == 1) {
			boolean firstActivation = !ed.secondCanvasBtn.isEnabled();
			ed.rightDropZone.setVisible(false);

			if (firstActivation) {
				ed.secondCanvasBtn.setSelected(true);
			} else if (!ed.secondCanvasBtn.isSelected()) {
				ed.secondCanvasBtn.setSelected(true);
			}

			ed.updateLayoutVisibility();
			ed.updateCanvasFocusBorder();
			ed.secondCanvasBtn.setEnabled(true);
			ed.secondGalleryBtn.setEnabled(true);
			ed.secondElementsBtn.setEnabled(true);
			ed.secondScenesBtn.setEnabled(true);
		}

		ed.repositionNavButtons(idx);
		c.layeredPane.revalidate();
		c.layeredPane.repaint();
	}

	public void fitToViewport(int idx) {
		CanvasInstance c = ed.ci(idx);
		if (c.workingImage == null || c.scrollPane == null)
			return;
		Dimension vd = c.scrollPane.getViewport().getSize();
		if (vd.width <= 0 || vd.height <= 0) {
			SwingUtilities.invokeLater(() -> fitToViewport(idx));
			return;
		}

		if (c.zoomTimer != null) {
			c.zoomTimer.stop();
			c.zoomTimer = null;
		}
		c.userHasManuallyZoomed = false;
		c.zoomImgPt = null;
		c.zoomVpMouse = null;

		double nz = Math.max(ed.ZOOM_MIN, Math.min(ed.ZOOM_MAX,
				Math.min((vd.width - 80.0) / c.workingImage.getWidth(),
						(vd.height - 80.0) / c.workingImage.getHeight()) * 0.98));
		c.zoom = c.zoomTarget = nz;
		c.canvasWrapper.revalidate();

		SwingUtilities.invokeLater(() -> {
			if (c.workingImage == null || c.scrollPane == null)
				return;
			c.canvasWrapper.revalidate();
			c.canvasWrapper.validate();
			JViewport vp = c.scrollPane.getViewport();
			Dimension vpSz = vp.getSize();
			int cw = (int) Math.ceil(c.workingImage.getWidth() * c.zoom);
			int ch = (int) Math.ceil(c.workingImage.getHeight() * c.zoom);
			vp.setViewPosition(
					new Point(Math.max(0, (cw - vpSz.width) / 2), Math.max(0, (ch - vpSz.height) / 2)));
			c.canvasWrapper.repaint();
			if (ed.zoomLabel != null)
				ed.zoomLabel.setText(Math.round(c.zoom * 100) + "%");
		});
	}

	public void centerCanvas(int idx) {
		CanvasInstance c = ed.ci(idx);
		if (c.scrollPane == null || c.workingImage == null)
			return;
		SwingUtilities.invokeLater(() -> {
			c.canvasWrapper.revalidate();
			c.canvasWrapper.validate();
			JViewport vp = c.scrollPane.getViewport();
			Dimension vpSz = vp.getSize();
			int cw = (int) Math.ceil(c.workingImage.getWidth() * c.zoom);
			int ch = (int) Math.ceil(c.workingImage.getHeight() * c.zoom);
			int viewX = Math.max(0, (cw - vpSz.width) / 2);
			int viewY = Math.max(0, (ch - vpSz.height) / 2);
			vp.setViewPosition(new Point(viewX, viewY));
		});
	}

	/** Centers the viewport horizontally only (keeps vertical position). */
	void centerCanvasX(int idx) {
		CanvasInstance c = ed.ci(idx);
		if (c.scrollPane == null || c.workingImage == null)
			return;
		SwingUtilities.invokeLater(() -> {
			c.canvasWrapper.revalidate();
			c.canvasWrapper.validate();
			JViewport vp = c.scrollPane.getViewport();
			Dimension vpSz = vp.getSize();
			int cw = (int) Math.ceil(c.workingImage.getWidth() * c.zoom);
			int viewX = Math.max(0, (cw - vpSz.width) / 2);
			int viewY = vp.getViewPosition().y;
			vp.setViewPosition(new Point(viewX, viewY));
		});
	}

	void setZoomInstant(double nz, int idx) {
		CanvasInstance c = ed.ci(idx);
		c.userHasManuallyZoomed = false;
		if (c.zoomTimer != null) {
			c.zoomTimer.stop();
			c.zoomTimer = null;
		}
		c.zoom = c.zoomTarget = Math.max(ed.ZOOM_MIN, Math.min(ed.ZOOM_MAX, nz));
		c.zoomImgPt = null;
		c.zoomVpMouse = null;
		if (c.canvasWrapper != null) {
			c.canvasWrapper.revalidate();
			c.canvasWrapper.repaint();
		}
		SwingUtilities.invokeLater(() -> c.scrollPane.getViewport().setViewPosition(new Point(0, 0)));
	}
}
