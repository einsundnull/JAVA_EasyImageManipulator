package paint;

import java.awt.Image;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.image.BufferedImage;
import java.util.ArrayList;

/**
 * Handles clipboard operations: copy, copyOutside, cut, cutOutside, paste,
 * system-clipboard I/O, and getActiveSelection. Extracted from
 * SelectiveAlphaEditor.
 */
class ClipboardController {

	private final SelectiveAlphaEditor ed;

	ClipboardController(SelectiveAlphaEditor ed) {
		this.ed = ed;
	}

	/**
	 * CTRL+C — copy INSIDE selection → Element layer (or full image if no
	 * selection).
	 */
	void doCopy() {
		CanvasInstance c = ed.ci();
		if (c.workingImage == null)
			return;

		if (!c.selectedElements.isEmpty()) {
			ed.clipboardLayers = new ArrayList<>(c.selectedElements);
			Layer first = c.selectedElements.get(0);
			if (first instanceof ImageLayer il) {
				ed.clipboard = il.image();
			} else if (first instanceof PathLayer pl) {
				ed.clipboard = PaintEngine.cropPolygon(c.workingImage, pl.absXPoints(), pl.absYPoints());
			} else if (first instanceof TextLayer tl) {
				ed.clipboard = ed.renderTextLayerToImage(tl);
			}
			if (ed.clipboard != null)
				copyToSystemClipboard(ed.clipboard);
			return;
		}

		Rectangle sel = ed.getActiveSelection();
		if (sel != null) {
			ed.clipboard = PaintEngine.cropRegion(c.workingImage, sel);
			copyToSystemClipboard(ed.clipboard);
			addElementFromClipboard(ed.clipboard, sel.x, sel.y);
			ed.clipboardLayers = new ArrayList<>(c.selectedElements);
		} else {
			ed.clipboard = ed.deepCopy(c.workingImage);
			ed.clipboardLayers = null;
			copyToSystemClipboard(ed.clipboard);
		}
	}

	/**
	 * CTRL+SHIFT+C — copy OUTSIDE selection → Element layer (full-size, inside
	 * punched out).
	 */
	void doCopyOutside() {
		CanvasInstance c = ed.ci();
		if (c.workingImage == null)
			return;

		if (!c.selectedElements.isEmpty() && c.selectedElements.get(0) instanceof PathLayer pl) {
			ed.clipboard = PaintEngine.cropOutsidePolygon(c.workingImage, pl.absXPoints(), pl.absYPoints());
			if (ed.clipboard != null)
				copyToSystemClipboard(ed.clipboard);
			return;
		}

		Rectangle sel = ed.getActiveSelection();
		if (sel != null) {
			ed.clipboard = PaintEngine.cropOutside(c.workingImage, sel);
			copyToSystemClipboard(ed.clipboard);
			addElementFromClipboard(ed.clipboard, 0, 0);
			ed.clipboardLayers = new ArrayList<>(c.selectedElements);
		} else {
			ed.clipboard = ed.deepCopy(c.workingImage);
			ed.clipboardLayers = null;
			copyToSystemClipboard(ed.clipboard);
		}
	}

	/** CTRL+X — cut INSIDE selection → Element layer + clear canvas pixels. */
	void doCut() {
		CanvasInstance c = ed.ci();
		if (c.workingImage == null)
			return;

		if (!c.selectedElements.isEmpty() && !(c.selectedElements.get(0) instanceof PathLayer)) {
			ed.pushUndo();
			ed.clipboardLayers = new ArrayList<>(c.selectedElements);
			Layer first = c.selectedElements.get(0);
			if (first instanceof ImageLayer il) {
				ed.clipboard = il.image();
			} else if (first instanceof TextLayer tl) {
				ed.clipboard = ed.renderTextLayerToImage(tl);
			}
			if (ed.clipboard != null)
				copyToSystemClipboard(ed.clipboard);
			for (Layer el : c.selectedElements) {
				c.activeElements.removeIf(e -> e.id() == el.id());
			}
			c.selectedElements.clear();
			ed.markDirty();
			ed.refreshElementPanel();
			c.canvasPanel.repaint();
			return;
		}

		if (!c.selectedElements.isEmpty() && c.selectedElements.get(0) instanceof PathLayer pl) {
			ed.pushUndo();
			ed.clipboard = PaintEngine.cropPolygon(c.workingImage, pl.absXPoints(), pl.absYPoints());
			ed.clipboardLayers = null;
			if (ed.clipboard != null)
				copyToSystemClipboard(ed.clipboard);
			PaintEngine.clearPolygon(c.workingImage, pl.absXPoints(), pl.absYPoints());
			ed.markDirty();
			return;
		}

		Rectangle sel = ed.getActiveSelection();
		if (sel != null) {
			ed.pushUndo();
			ed.clipboard = PaintEngine.cropRegion(c.workingImage, sel);
			copyToSystemClipboard(ed.clipboard);
			PaintEngine.clearRegion(c.workingImage, sel);
			ed.markDirty();
			addElementFromClipboard(ed.clipboard, sel.x, sel.y);
			ed.clipboardLayers = new ArrayList<>(c.selectedElements);
		} else {
			ed.pushUndo();
			ed.clipboard = ed.deepCopy(c.workingImage);
			ed.clipboardLayers = null;
			copyToSystemClipboard(ed.clipboard);
			PaintEngine.clearRegion(c.workingImage,
					new Rectangle(0, 0, c.workingImage.getWidth(), c.workingImage.getHeight()));
			ed.markDirty();
		}
	}

	/**
	 * CTRL+SHIFT+X — cut OUTSIDE selection → Element layer (full-size) + clear
	 * canvas outside.
	 */
	void doCutOutside() {
		CanvasInstance c = ed.ci();
		if (c.workingImage == null)
			return;

		if (!c.selectedElements.isEmpty() && c.selectedElements.get(0) instanceof PathLayer pl) {
			ed.pushUndo();
			ed.clipboard = PaintEngine.cropOutsidePolygon(c.workingImage, pl.absXPoints(), pl.absYPoints());
			if (ed.clipboard != null)
				copyToSystemClipboard(ed.clipboard);
			PaintEngine.clearOutsidePolygon(c.workingImage, pl.absXPoints(), pl.absYPoints());
			ed.markDirty();
			return;
		}

		Rectangle sel = ed.getActiveSelection();
		if (sel != null) {
			ed.pushUndo();
			ed.clipboard = PaintEngine.cropOutside(c.workingImage, sel);
			copyToSystemClipboard(ed.clipboard);
			PaintEngine.clearOutside(c.workingImage, sel);
			ed.markDirty();
			addElementFromClipboard(ed.clipboard, 0, 0);
		} else {
			doCut();
		}
	}

	/** CTRL+V — paste from clipboard as floating selection or layer. */
	void doPaste() {
		CanvasInstance c = ed.ci();
		BufferedImage fromClip = pasteFromSystemClipboard();
		if (fromClip != null)
			ed.clipboard = fromClip;

		if (ed.clipboardLayers != null && !ed.clipboardLayers.isEmpty() && c.workingImage != null) {
			ed.pushUndo();
			c.selectedElements.clear();

			for (Layer original : ed.clipboardLayers) {
				Layer newLayer;
				if (original instanceof ImageLayer il) {
					newLayer = new ImageLayer(c.nextElementId++, ed.deepCopy(il.image()), original.x(), original.y(),
							original.width(), original.height(), il.rotationAngle(), il.opacity(), il.isHidden());
				} else if (original instanceof TextLayer tl) {
					newLayer = TextLayer.of(c.nextElementId++, tl.text(), tl.fontName(), tl.fontSize(), tl.fontBold(),
							tl.fontItalic(), tl.fontColor(), original.x(), original.y(), tl.isHidden());
				} else if (original instanceof PathLayer pl) {
					newLayer = PathLayer.of(c.nextElementId++, new ArrayList<>(pl.points()), null,
							pl.isClosed(), original.x(), original.y(), pl.isHidden());
				} else {
					continue;
				}
				c.activeElements.add(newLayer);
				c.selectedElements.add(newLayer);
			}

			c.hasUnsavedChanges = true;
			ed.markDirty();
			ed.refreshElementPanel();
			c.canvasPanel.repaint();
			ed.updateTitle();
			return;
		}

		if (ed.clipboard != null && c.workingImage != null) {
			ed.pushUndo();
			c.floatingImg = ed.deepCopy(ed.clipboard);
			c.floatRect = new Rectangle(0, 0, Math.min(ed.clipboard.getWidth(), c.workingImage.getWidth()),
					Math.min(ed.clipboard.getHeight(), c.workingImage.getHeight()));
			c.isDraggingFloat = false;
			c.floatDragAnchor = null;
			c.activeHandle = -1;
			c.scaleBaseRect = null;
			c.scaleDragStart = null;
			c.selectedAreas.clear();
			c.hasUnsavedChanges = true;
			ed.updateTitle();
			c.canvasPanel.repaint();
		}
	}

	private void addElementFromClipboard(BufferedImage img, int x, int y) {
		if (img == null || ed.ci().appMode != AppMode.PAINT)
			return;
		CanvasInstance c = ed.ci();
		Layer el = new ImageLayer(c.nextElementId++, ed.deepCopy(img), x, y, img.getWidth(), img.getHeight());
		c.activeElements.add(el);
		c.selectedElements.clear();
		c.selectedElements.add(el);
		ed.markDirty();
		ed.refreshElementPanel();
		if (c.canvasPanel != null)
			c.canvasPanel.repaint();
	}

	private void copyToSystemClipboard(BufferedImage img) {
		if (img == null)
			return;
		try {
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new TransferableImage(img), null);
		} catch (Exception ignored) {
		}
	}

	private BufferedImage pasteFromSystemClipboard() {
		try {
			Transferable t = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
			if (t != null && t.isDataFlavorSupported(DataFlavor.imageFlavor)) {
				Image img = (Image) t.getTransferData(DataFlavor.imageFlavor);
				BufferedImage bi = new BufferedImage(img.getWidth(null), img.getHeight(null),
						BufferedImage.TYPE_INT_ARGB);
				bi.createGraphics().drawImage(img, 0, 0, null);
				return bi;
			}
		} catch (Exception ignored) {
		}
		return null;
	}
}
