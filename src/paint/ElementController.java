package paint;

import java.awt.AlphaComposite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.SwingWorker;

/**
 * Handles element/layer operations: rendering, persistence, selection
 * management, and element-space coordinate helpers. Extracted from
 * SelectiveAlphaEditor.
 */
class ElementController {

	private final SelectiveAlphaEditor ed;

	ElementController(SelectiveAlphaEditor ed) {
		this.ed = ed;
	}

	// ── Rendering ──────────────────────────────────────────────────────────────

	/**
	 * Renders a TextLayer to a pixel image at its natural (image-space) font size.
	 */
	BufferedImage renderTextLayerToImage(TextLayer tl) {
		int style = (tl.fontBold() ? Font.BOLD : 0) | (tl.fontItalic() ? Font.ITALIC : 0);
		Font font = new Font(tl.fontName(), style, Math.max(6, tl.fontSize()));
		BufferedImage dummy = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		FontMetrics fm = dummy.createGraphics().getFontMetrics(font);
		String[] lines = tl.text().split("\n", -1);
		int w = 1;
		for (String l : lines)
			w = Math.max(w, fm.stringWidth(l));
		int h = Math.max(1, fm.getHeight() * lines.length);
		BufferedImage img = new BufferedImage(w + TextLayer.TEXT_PADDING * 2, h + TextLayer.TEXT_PADDING * 2,
				BufferedImage.TYPE_INT_ARGB);
		java.awt.Graphics2D g2 = img.createGraphics();
		g2.setComposite(AlphaComposite.Clear);
		g2.fillRect(0, 0, img.getWidth(), img.getHeight());
		g2.setComposite(AlphaComposite.SrcOver);
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g2.setFont(font);
		g2.setColor(tl.fontColor());
		for (int i = 0; i < lines.length; i++) {
			g2.drawString(lines[i], TextLayer.TEXT_PADDING,
					TextLayer.TEXT_PADDING + fm.getHeight() * i + fm.getAscent());
		}
		g2.dispose();
		return img;
	}

	// ── Screen-space rect ──────────────────────────────────────────────────────

	/** Screen-space rectangle for a layer (uses active canvas zoom). */
	Rectangle elemRectScreen(Layer el) {
		return elemRectScreen(el, ed.ci().zoom);
	}

	/** Screen-space rectangle for a layer, with explicit zoom value. */
	Rectangle elemRectScreen(Layer el, double zoom) {
		double z = zoom;
		if (el instanceof PathLayer pl && !pl.points().isEmpty()) {
			double fMinX = Double.MAX_VALUE, fMaxX = -Double.MAX_VALUE;
			double fMinY = Double.MAX_VALUE, fMaxY = -Double.MAX_VALUE;
			for (Point3D p : pl.points()) {
				if (p.x < fMinX) fMinX = p.x;
				if (p.x > fMaxX) fMaxX = p.x;
				if (p.y < fMinY) fMinY = p.y;
				if (p.y > fMaxY) fMaxY = p.y;
			}
			int fx = (int) Math.round((pl.x() + fMinX - 8) * z);
			int fy = (int) Math.round((pl.y() + fMinY - 8) * z);
			int fw = (int) Math.round((fMaxX - fMinX + 16) * z);
			int fh = (int) Math.round((fMaxY - fMinY + 16) * z);
			return new Rectangle(fx, fy, Math.max(1, fw), Math.max(1, fh));
		}
		return new Rectangle((int) Math.round(el.x() * z), (int) Math.round(el.y() * z),
				(int) Math.round(el.width() * z), (int) Math.round(el.height() * z));
	}

	// ── Layer utilities ───────────────────────────────────────────────────────

	private static final float MAX_ELEM_RATIO = 0.40f;

	/** Creates a copy of {@code src} with a new ID. Returns null for unsupported types. */
	Layer copyLayerWithNewId(Layer src, int newId) {
		if (src instanceof ImageLayer il) {
			BufferedImage normalized = ed.normalizeImage(il.image());
			return new ImageLayer(newId, normalized, il.x(), il.y(), normalized.getWidth(), normalized.getHeight());
		} else if (src instanceof TextLayer tl) {
			return TextLayer.of(newId, tl.text(), tl.fontName(), tl.fontSize(), tl.fontBold(), tl.fontItalic(),
					tl.fontColor(), tl.x(), tl.y());
		} else if (src instanceof PathLayer pl) {
			return PathLayer.of(newId, pl.points(), pl.image(), pl.isClosed(), pl.x(), pl.y());
		}
		return null;
	}

	/** Returns {renderW, renderH} scaled to MAX_ELEM_RATIO of canvas size. Never upscales. */
	static int[] fitElementSize(int imgW, int imgH, int canvasW, int canvasH) {
		float maxW = canvasW * MAX_ELEM_RATIO;
		float maxH = canvasH * MAX_ELEM_RATIO;
		float scale = Math.min(1.0f, Math.min(maxW / imgW, maxH / imgH));
		return new int[] { Math.max(1, Math.round(imgW * scale)), Math.max(1, Math.round(imgH * scale)) };
	}

	/** Converts a visual drop index (0 = top) to a list insert index. */
	static int visualToInsertIndex(int visualIdx, int listSize) {
		return Math.max(0, Math.min(listSize, listSize - visualIdx));
	}

	// ── Element operations ─────────────────────────────────────────────────────

	void insertSelectionAsElement() {
		BufferedImage src = null;
		CanvasInstance c = ed.ci();
		Rectangle sel = ed.getActiveSelection();
		if (sel != null && c.workingImage != null) {
			src = PaintEngine.cropRegion(c.workingImage, sel);
		} else if (ed.clipboard != null) {
			src = ed.deepCopy(ed.clipboard);
		}
		if (src == null) {
			ed.showInfoDialog("Kein Inhalt", "Nichts zum Einfügen als Element.");
			return;
		}
		int ex = sel != null ? sel.x : 0;
		int ey = sel != null ? sel.y : 0;
		Layer el = new ImageLayer(c.nextElementId++, src, ex, ey, src.getWidth(), src.getHeight());
		c.activeElements.add(el);
		c.selectedElements.clear();
		c.selectedElements.add(el);
		c.selectedAreas.clear();
		ed.markDirty();
		ed.refreshElementPanel();
	}

	/** Merges all selected layers onto the canvas and removes them from the layer list. */
	void mergeSelectedElements() {
		CanvasInstance c = ed.ci();
		if (c.selectedElements.isEmpty() || c.workingImage == null)
			return;
		ed.pushUndo();
		for (Layer el : new ArrayList<>(c.selectedElements)) {
			if (el instanceof ImageLayer il) {
				BufferedImage scaled = PaintEngine.scale(il.image(), Math.max(1, el.width()), Math.max(1, el.height()));
				PaintEngine.pasteRegion(c.workingImage, scaled, new Point(el.x(), el.y()));
			} else if (el instanceof TextLayer tl) {
				BufferedImage rendered = renderTextLayerToImage(tl);
				PaintEngine.pasteRegion(c.workingImage, rendered, new Point(el.x(), el.y()));
			}
			c.activeElements.removeIf(e -> e.id() == el.id());
		}
		c.selectedElements.clear();
		ed.markDirty();
		ed.refreshElementPanel();
	}

	/** Deletes all selected layers without merging to canvas. */
	void deleteSelectedElements() {
		CanvasInstance c = ed.ci();
		if (c.selectedElements.isEmpty())
			return;
		for (Layer el : c.selectedElements)
			c.activeElements.removeIf(e -> e.id() == el.id());
		c.selectedElements.clear();
		ed.markDirty();
		ed.refreshElementPanel();
		ed.persistSceneIfActive(ed.activeCanvasIndex);
	}

	/** Toggles a layer in/out of the multi-selection. New primary goes at index 0. */
	void doToggleElementSelection(Layer el) {
		CanvasInstance c = ed.ci();
		for (int i = 0; i < c.selectedElements.size(); i++) {
			if (c.selectedElements.get(i).id() == el.id()) {
				c.selectedElements.remove(i);
				ed.refreshElementPanel();
				return;
			}
		}
		c.selectedElements.add(0, el);
		ed.refreshElementPanel();
	}

	// ── Scene persistence ──────────────────────────────────────────────────────

	/**
	 * If a scene is currently loaded, rewrite its file with the current
	 * activeElements so changes are persisted to disk.
	 */
	// ── Composite thumbnail ────────────────────────────────────────────────────

	/** Renders canvas + all active elements as a composite thumbnail image. */
	BufferedImage renderCompositeForThumbnail(CanvasInstance c) {
		if (c.workingImage == null)
			return null;
		int w = c.workingImage.getWidth(), h = c.workingImage.getHeight();
		BufferedImage comp = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2 = comp.createGraphics();
		g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g2.drawImage(c.workingImage, 0, 0, null);
		for (Layer el : c.activeElements) {
			if (el instanceof ImageLayer il) {
				if (Math.abs(il.rotationAngle()) > 0.001) {
					double cx = il.x() + il.width() / 2.0;
					double cy = il.y() + il.height() / 2.0;
					g2.rotate(Math.toRadians(il.rotationAngle()), cx, cy);
					g2.drawImage(il.image(), il.x(), il.y(), il.width(), il.height(), null);
					g2.rotate(-Math.toRadians(il.rotationAngle()), cx, cy);
				} else {
					g2.drawImage(il.image(), il.x(), il.y(), il.width(), il.height(), null);
				}
			}
		}
		g2.dispose();
		return comp;
	}

	void refreshGalleryThumbnail() { refreshGalleryThumbnail(ed.activeCanvasIndex); }

	void refreshGalleryThumbnail(int idx) {
		CanvasInstance c = ed.ci(idx);
		if (c.sourceFile == null || c.workingImage == null)
			return;
		BufferedImage thumb = renderCompositeForThumbnail(c);
		if (thumb == null)
			return;
		c.tileGallery.refreshThumbnailFor(c.sourceFile, thumb);
		if (c.activeSceneFile != null)
			c.scenesPanel.refreshThumbnailFor(c.activeSceneFile, thumb);
	}

	// ── List helpers ───────────────────────────────────────────────────────────

	/** Update a layer in both activeElements and selectedElements by ID. */
	void replaceInLists(CanvasInstance c, Layer updated) {
		for (int i = 0; i < c.activeElements.size(); i++) {
			if (c.activeElements.get(i).id() == updated.id()) { c.activeElements.set(i, updated); break; }
		}
		for (int i = 0; i < c.selectedElements.size(); i++) {
			if (c.selectedElements.get(i).id() == updated.id()) { c.selectedElements.set(i, updated); break; }
		}
	}

	// ── Open layer in other canvas ─────────────────────────────────────────────

	/** Opens an ImageLayer (or any renderable layer) in the other canvas for pixel editing. */
	void doOpenImageLayerInOtherCanvas(int sourceIdx, Layer el) {
		BufferedImage img = null;
		if (el instanceof ImageLayer il) {
			img = il.image();
		} else if (el instanceof TextLayer tl) {
			img = renderTextLayerToImage(tl);
		}
		if (img == null)
			return;
		try {
			java.io.File tmp = java.io.File.createTempFile("element_" + el.id() + "_", ".png");
			tmp.deleteOnExit();
			ImageIO.write(img, "PNG", tmp);
			int targetIdx = 1 - sourceIdx;
			ed.activeCanvasIndex = targetIdx;
			if (targetIdx == 1) {
				ed.secondCanvasBtn.setEnabled(true);
				ed.secondCanvasBtn.setSelected(true);
				ed.updateLayoutVisibility();
				if (ed.galleryWrapper != null) {
					ed.galleryWrapper.revalidate();
					ed.galleryWrapper.repaint();
				}
			}
			ed.loadFile(tmp, targetIdx);
			ed.elementEditController.activateElementEditMode(targetIdx, el, sourceIdx);
		} catch (java.io.IOException ex) {
			ed.showErrorDialog("Fehler", "Element konnte nicht geöffnet werden:\n" + ex.getMessage());
		}
	}

	// ── Layer export helpers ───────────────────────────────────────────────────

	/** Renders a layer to a BufferedImage. Returns null for PathLayer (not supported). */
	BufferedImage renderLayerToImage(Layer live) {
		if (live instanceof TextLayer tl) return renderTextLayerToImage(tl);
		if (live instanceof ImageLayer il) return PaintEngine.scale(il.image(), Math.max(1, live.width()), Math.max(1, live.height()));
		return null;
	}

	/** Returns a unique File in sourceFile's directory for exporting a layer. */
	File uniqueLayerExportFile(File sourceFile, int layerId) {
		String name = sourceFile.getName();
		int dot = name.lastIndexOf('.');
		String base = dot > 0 ? name.substring(0, dot) : name;
		String ext  = dot > 0 ? name.substring(dot)    : ".png";
		File dir    = sourceFile.getParentFile();
		File target = new File(dir, base + "_layer_" + layerId + ext);
		int counter = 1;
		while (target.exists()) target = new File(dir, base + "_layer_" + layerId + "_" + counter++ + ext);
		return target;
	}

	/** Writes img to file, adds file to the gallery of canvas idx, shows error on failure. */
	void saveLayerAsImageFile(BufferedImage img, File file, int idx) {
		try {
			javax.imageio.ImageIO.write(img, "PNG", file);
			if (ed.ci(idx).tileGallery != null)
				ed.ci(idx).tileGallery.addFiles(java.util.Arrays.asList(file));
		} catch (Exception ex) {
			System.err.println("[ERROR] Failed to export layer: " + ex.getMessage());
			ed.showErrorDialog("Fehler", "Speichern fehlgeschlagen:\n" + ex.getMessage());
		}
	}

	void persistSceneIfActive(int idx) {
		CanvasInstance c = ed.ci(idx);
		if (c.activeSceneFile == null)
			return;

		// ── GameII-Scene ──────────────────────────────────────────────────────
		if (c.gameSceneRoot != null) {
			List<Layer> layers = new ArrayList<>(c.activeElements);
			int gw = c.gameCanvasW, gh = c.gameCanvasH;
			new SwingWorker<Void, Void>() {
				@Override
				protected Void doInBackground() throws Exception {
					GameSceneWriter.writeScene(layers, gw, gh);
					return null;
				}

				@Override
				protected void done() {
					try {
						get();
					} catch (Exception ex) {
						System.err.println("[ERROR] GameII scene persist failed: " + ex.getMessage());
					}
				}
			}.execute();
			return;
		}

		// ── Standard TT-Scene ────────────────────────────────────────────────
		if (c.sourceFile == null)
			return;
		File sceneDir = c.activeSceneFile.getParentFile();
		String sceneName = c.activeSceneFile.getName().replaceAll("\\.(txt|json)$", "");
		List<Layer> layers = new ArrayList<>(c.activeElements);
		File bgFile = c.sourceFile;
		File sceneFileFinal = c.activeSceneFile;
		new SwingWorker<BufferedImage, Void>() {
			@Override
			protected BufferedImage doInBackground() throws Exception {
				SceneFileWriter.writeScene(sceneDir, sceneName, bgFile, layers);
				SceneImageAdapter.SceneAsImage updated = SceneImageAdapter.loadSceneAsImage(sceneFileFinal);
				return updated != null ? updated.thumbnail : null;
			}

			@Override
			protected void done() {
				try {
					BufferedImage thumb = get();
					if (thumb != null) {
						ed.ci(idx).scenesPanel.refreshThumbnailFor(sceneFileFinal, thumb);
					}
				} catch (Exception ex) {
					System.err.println("[ERROR] Scene persist failed: " + ex.getMessage());
				}
			}
		}.execute();
	}
}
