package paint;

import java.awt.AlphaComposite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

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
