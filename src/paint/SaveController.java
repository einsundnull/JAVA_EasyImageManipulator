package paint;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Handles save, undo/redo, and alpha-editor canvas operations.
 * Extracted from SelectiveAlphaEditor.
 */
class SaveController {

	private final SelectiveAlphaEditor ed;

	SaveController(SelectiveAlphaEditor ed) {
		this.ed = ed;
	}

	// ── Undo / Redo ────────────────────────────────────────────────────────────

	static final int MAX_UNDO = 50;

	void clearUndoRedo() {
		ed.ci().undoStack.clear();
		ed.ci().redoStack.clear();
	}

	public void performFloodfill(java.awt.Point screenPt) {
		CanvasInstance c = ed.ci();
		java.awt.Point ip = ed.screenToImage(screenPt);
		int tc = c.workingImage.getRGB(ip.x, ip.y);
		if (((tc >> 24) & 0xFF) == 0) {
			ed.showInfoDialog("Bereits transparent", "Klicke auf eine sichtbare Farbe.");
			return;
		}
		PaintEngine.floodFill(c.workingImage, ip.x, ip.y, new Color(0, 0, 0, 0), 30);
		ed.markDirty();
	}

	void pushUndo(int idx) {
		CanvasInstance c = ed.ci(idx);
		if (c.workingImage == null)
			return;
		c.undoStack.push(ed.deepCopy(c.workingImage));
		if (c.undoStack.size() > MAX_UNDO)
			c.undoStack.pollLast();
		c.redoStack.clear();
	}

	void doUndo(int idx) {
		CanvasInstance c = ed.ci(idx);
		if (c.undoStack.isEmpty())
			return;
		c.redoStack.push(ed.deepCopy(c.workingImage));
		c.workingImage = c.undoStack.pop();
		afterUndoRedo(idx);
	}

	void doRedo(int idx) {
		CanvasInstance c = ed.ci(idx);
		if (c.redoStack.isEmpty())
			return;
		c.undoStack.push(ed.deepCopy(c.workingImage));
		c.workingImage = c.redoStack.pop();
		afterUndoRedo(idx);
	}

	private void afterUndoRedo(int idx) {
		CanvasInstance c = ed.ci(idx);
		if (c.undoStack.isEmpty()) {
			c.hasUnsavedChanges = false;
			if (c.sourceFile != null)
				ed.dirtyFiles.remove(c.sourceFile);
		} else {
			c.hasUnsavedChanges = true;
			if (c.sourceFile != null)
				ed.dirtyFiles.add(c.sourceFile);
		}
		ed.updateTitle();
		ed.updateDirtyUI();
		ed.refreshElementPanel();
		ed.refreshGalleryThumbnail();
		c.canvasWrapper.revalidate();
		c.canvasPanel.repaint();
		if (ed.showRuler && idx == 0) {
			ed.hRuler.repaint();
			ed.vRuler.repaint();
		}
	}

	// ── Save ───────────────────────────────────────────────────────────────────

	String getSaveSuffix() {
		return (ed.ci().appMode == AppMode.PAINT) ? "_painted"
				: ed.floodfillMode ? "_floodfill_alpha" : "_selective_alpha";
	}

	void saveImage() {
		CanvasInstance c = ed.ci();
		if (c.sourceFile == null)
			return;
		try {
			String suffix = getSaveSuffix();
			String outPath = WhiteToAlphaConverter.getOutputPath(c.sourceFile, suffix);
			File outFile = new File(outPath);
			ImageIO.write(c.workingImage, "PNG", outFile);

			if (ed.projectManager.getProjectName() != null) {
				ed.projectManager.saveScene(c.sourceFile, c.activeElements, c.zoom, c.appMode,
						c.workingImage.getWidth(), c.workingImage.getHeight());
			}

			c.hasUnsavedChanges = false;
			ed.dirtyFiles.remove(c.sourceFile);
			ed.updateTitle();
			ed.updateDirtyUI();
			ed.refreshGalleryThumbnail();
			ed.showInfoDialog("Gespeichert", "Gespeichert als:\n" + outFile.getName());
		} catch (IOException e) {
			ed.showErrorDialog("Speicherfehler", e.getMessage());
		}
	}

	void saveImageToOriginal() {
		CanvasInstance c = ed.ci();
		if (c.sourceFile == null || c.workingImage == null)
			return;
		try {
			ImageIO.write(c.workingImage, "PNG", c.sourceFile);

			// Book pages: persist layers via scene mechanism (writes .txt + images/ + texts/)
			if (PageLayoutManifest.isBookPage(c.sourceFile)) {
				if (c.activeSceneFile == null)
					c.activeSceneFile = BookController.getPageManifest(c.sourceFile);
				ed.persistSceneIfActive(ed.activeCanvasIndex);
			}

			if (ed.projectManager.getProjectName() != null) {
				ed.projectManager.saveScene(c.sourceFile, c.activeElements, c.zoom, c.appMode,
						c.workingImage.getWidth(), c.workingImage.getHeight());
			}

			c.hasUnsavedChanges = false;
			ed.dirtyFiles.remove(c.sourceFile);
			ed.updateTitle();
			ed.updateDirtyUI();
			ed.refreshGalleryThumbnail();
			ToastNotification.show(ed, "Gespeichert: " + c.sourceFile.getName());
			SwingUtilities.invokeLater(ed::ensureElementEditBarVisible);
		} catch (IOException e) {
			ed.showErrorDialog("Speicherfehler", e.getMessage());
		}
	}

	// ── Alpha-editor ops ───────────────────────────────────────────────────────

	void applySelectionsToAlpha() {
		CanvasInstance c = ed.ci();
		if (c.selectedAreas.isEmpty()) {
			ed.showInfoDialog("Keine Auswahl", "Noch keine Bereiche ausgewählt.");
			return;
		}
		ed.pushUndo();
		for (Rectangle r : c.selectedAreas)
			PaintEngine.clearRegion(c.workingImage, r);
		c.selectedAreas.clear();
		ed.markDirty();
		ed.showInfoDialog("Erledigt", "Ausgewählte Bereiche wurden transparent gemacht.");
	}

	void clearSelections() {
		ed.ci().selectedAreas.clear();
		ed.ci().canvasPanel.repaint();
	}

	// ── Silent / burn saves ────────────────────────────────────────────────────

	/** CTRL+S: save silently without any confirmation dialog. */
	void saveImageSilent() {
		CanvasInstance c = ed.ci();
		if (c.sourceFile == null)
			return;
		try {
			String suffix = getSaveSuffix();
			String outPath = WhiteToAlphaConverter.getOutputPath(c.sourceFile, suffix);
			ImageIO.write(c.workingImage, "PNG", new File(outPath));

			if (ed.projectManager.getProjectName() != null) {
				ed.projectManager.saveScene(c.sourceFile, c.activeElements, c.zoom, c.appMode,
						c.workingImage.getWidth(), c.workingImage.getHeight());
			}

			c.hasUnsavedChanges = false;
			ed.dirtyFiles.remove(c.sourceFile);
			ed.updateTitle();
			ed.updateDirtyUI();
			ed.refreshGalleryThumbnail();
			ToastNotification.show(ed, "Gespeichert");
			SwingUtilities.invokeLater(ed.elementEditController::ensureElementEditBarVisible);
		} catch (IOException e) {
			ed.showErrorDialog("Speicherfehler", e.getMessage());
		}
	}

	/** CTRL+SHIFT+S: burn visible elements, save as copy with new name. */
	void saveBurnedElementsCopy() {
		CanvasInstance c = ed.ci();
		if (c.sourceFile == null || c.workingImage == null)
			return;

		BufferedImage burned = burnVisibleElements();
		if (burned == null)
			return;

		String suffix = "_burned";
		String outPath = WhiteToAlphaConverter.getOutputPath(c.sourceFile, suffix);
		File suggestedFile = new File(outPath);

		JFileChooser chooser = new JFileChooser();
		chooser.setSelectedFile(suggestedFile);
		chooser.setAcceptAllFileFilterUsed(false);
		chooser.addChoosableFileFilter(new FileNameExtensionFilter("PNG Images", "png"));

		int result = chooser.showSaveDialog(ed);
		if (result == JFileChooser.APPROVE_OPTION) {
			try {
				File target = chooser.getSelectedFile();
				if (!target.getName().toLowerCase().endsWith(".png")) {
					target = new File(target.getAbsolutePath() + ".png");
				}
				ImageIO.write(burned, "PNG", target);
				ToastNotification.show(ed, "Mit Elementen gespeichert: " + target.getName());
			} catch (IOException e) {
				ed.showErrorDialog("Speicherfehler", e.getMessage());
			}
		}
	}

	/** CTRL+SHIFT+ALT+S: burn visible elements, save with same name (overwrite). */
	void saveBurnedElementsOriginal() {
		CanvasInstance c = ed.ci();
		if (c.sourceFile == null || c.workingImage == null)
			return;

		BufferedImage burned = burnVisibleElements();
		if (burned == null)
			return;

		int result = JOptionPane.showConfirmDialog(ed,
				"Elemente einbrennen und Originaldatei überschreiben?\n" + c.sourceFile.getName(), "Bestätigung",
				JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);

		if (result == JOptionPane.OK_OPTION) {
			try {
				ImageIO.write(burned, "PNG", c.sourceFile);
				c.hasUnsavedChanges = false;
				ed.dirtyFiles.remove(c.sourceFile);
				ed.updateTitle();
				ed.updateDirtyUI();
				ed.refreshGalleryThumbnail();
				ToastNotification.show(ed, "Mit Elementen gespeichert: " + c.sourceFile.getName());
			} catch (IOException e) {
				ed.showErrorDialog("Speicherfehler", e.getMessage());
			}
		}
	}

	/**
	 * Burns all active elements into the canvas image. Returns a new BufferedImage
	 * with burned elements, or null on error.
	 */
	private BufferedImage burnVisibleElements() {
		CanvasInstance c = ed.ci();
		if (c.workingImage == null || c.activeElements.isEmpty()) {
			return c.workingImage;
		}

		try {
			BufferedImage result = new BufferedImage(c.workingImage.getWidth(), c.workingImage.getHeight(),
					BufferedImage.TYPE_INT_ARGB);
			Graphics2D g2 = result.createGraphics();
			g2.drawImage(c.workingImage, 0, 0, null);

			for (Layer el : c.activeElements) {
				if (el instanceof ImageLayer il) {
					ElementController.drawImageLayer(g2, il);
				} else if (el instanceof TextLayer tl) {
					if (tl.isHidden()) continue;
					int style = (tl.fontBold() ? Font.BOLD : 0) | (tl.fontItalic() ? Font.ITALIC : 0);
					Font font = new Font(tl.fontName(), style, tl.fontSize());
					g2.setFont(font);
					g2.setColor(tl.fontColor());
					String[] lines = tl.text().split("\n", -1);
					FontMetrics fm = g2.getFontMetrics();
					for (int i = 0; i < lines.length; i++) {
						g2.drawString(lines[i], tl.x() + 4, tl.y() + fm.getAscent() + i * fm.getHeight() + 4);
					}
				} else if (el instanceof PathLayer pl) {
					if (pl.isHidden()) continue;
					List<Point3D> points = pl.points();
					if (!points.isEmpty()) {
						int[] xs = pl.absXPoints();
						int[] ys = pl.absYPoints();
						g2.setColor(new Color(100, 150, 200));
						g2.setStroke(new BasicStroke(2));
						if (pl.isClosed()) {
							g2.drawPolygon(xs, ys, points.size());
						} else {
							g2.drawPolyline(xs, ys, points.size());
						}
					}
				}
			}

			g2.dispose();
			return result;
		} catch (Exception ex) {
			System.err.println("[ERROR] Failed to burn elements: " + ex.getMessage());
			ex.printStackTrace();
			return null;
		}
	}

	void resetImage() {
		CanvasInstance c = ed.ci();
		if (c.originalImage == null)
			return;
		c.workingImage = ed.deepCopy(c.originalImage);
		c.undoStack.clear();
		c.redoStack.clear();
		c.selectedAreas.clear();
		c.floatingImg = null;
		c.floatRect = null;
		c.activeElements.clear();
		c.selectedElements.clear();

		c.hasUnsavedChanges = false;
		if (c.sourceFile != null)
			ed.dirtyFiles.remove(c.sourceFile);
		ed.updateTitle();
		ed.updateDirtyUI();
		ed.refreshElementPanel();
		ed.refreshGalleryThumbnail();
		c.canvasPanel.repaint();
	}
}
