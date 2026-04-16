package paint;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.datatransfer.DataFlavor;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

import javax.swing.JPanel;

/**
 * Handles drag-and-drop onto the canvas and the right-drop-zone panel.
 * Extracted from SelectiveAlphaEditor.
 */
class DropController {

	private final SelectiveAlphaEditor ed;

	DropController(SelectiveAlphaEditor ed) {
		this.ed = ed;
	}

	/** Registers a drop target on a component for the given canvas index. */
	void setupDropTarget(java.awt.Component target, int idx) {
		new java.awt.dnd.DropTarget(target, java.awt.dnd.DnDConstants.ACTION_COPY,
				new java.awt.dnd.DropTargetAdapter() {
					@Override
					public void dragEnter(java.awt.dnd.DropTargetDragEvent dtde) {
						dtde.acceptDrag(java.awt.dnd.DnDConstants.ACTION_COPY);
					}

					@Override
					public void dragOver(java.awt.dnd.DropTargetDragEvent dtde) {
						dtde.acceptDrag(java.awt.dnd.DnDConstants.ACTION_COPY);
					}

					@Override
					public void drop(java.awt.dnd.DropTargetDropEvent ev) {
						try {
							ev.acceptDrop(java.awt.dnd.DnDConstants.ACTION_COPY);
							java.awt.datatransfer.Transferable t = ev.getTransferable();
							if (t.isDataFlavorSupported(ElementLayerPanel.LAYER_FLAVOR)) {
								// Case 1: LayerTile dragged onto canvas → copy as element
								Layer layer = (Layer) t.getTransferData(ElementLayerPanel.LAYER_FLAVOR);
								insertLayerCopyToCanvas(layer, idx);
								ev.dropComplete(true);
							} else if (t.isDataFlavorSupported(TileGalleryPanel.FILE_AS_ELEMENT_FLAVOR)) {
								// Case 3: right-drag from TileGallery onto canvas → insert as element
								TileGalleryPanel.FileForElement ffe = (TileGalleryPanel.FileForElement) t
										.getTransferData(TileGalleryPanel.FILE_AS_ELEMENT_FLAVOR);
								insertFileAsElement(ffe.file, idx);
								ev.dropComplete(true);
							} else if (t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
								@SuppressWarnings("unchecked")
								List<File> files = (List<File>) t.getTransferData(DataFlavor.javaFileListFlavor);
								if (!files.isEmpty()) {
									File f = files.get(0);
									if (SelectiveAlphaEditor.isSupportedFile(f))
										ed.loadFile(f, idx);
									else
										ed.showErrorDialog("Format nicht unterstützt",
												"Erlaubt: PNG, JPG, BMP, GIF\nDatei: " + f.getName());
								}
								ev.dropComplete(true);
							} else {
								ev.dropComplete(false);
							}
						} catch (Exception ex) {
							ev.dropComplete(false);
							ed.showErrorDialog("Drop-Fehler", ex.getMessage());
						}
					}
				}, true);
	}

	/** Case 1: Copy a layer onto a canvas as a new element. */
	private void insertLayerCopyToCanvas(Layer source, int targetIdx) {
		CanvasInstance c = ed.ci(targetIdx);
		if (c.workingImage == null)
			return;
		Layer copy = ed.copyLayerWithNewId(source, c.nextElementId++);
		if (copy == null)
			return;
		c.activeElements.add(copy);
		c.selectedElements.clear();
		c.selectedElements.add(copy);
		ed.markDirty(targetIdx);
		ed.refreshElementPanel();
		if (c.canvasPanel != null)
			c.canvasPanel.repaint();
		ToastNotification.show(ed, "Layer kopiert");
	}

	/** Case 3: Load an image file and insert it as a new element on the canvas. */
	private void insertFileAsElement(File f, int targetIdx) {
		CanvasInstance c = ed.ci(targetIdx);
		if (c.workingImage == null) {
			if (SelectiveAlphaEditor.isSupportedFile(f))
				ed.loadFile(f, targetIdx);
			return;
		}
		try {
			BufferedImage img;
			if (f.getName().endsWith(".txt") || f.getName().endsWith(".json")) {
				SceneImageAdapter.SceneAsImage s = SceneImageAdapter.loadSceneAsImage(f);
				img = (s != null) ? s.thumbnail : null;
			} else {
				img = javax.imageio.ImageIO.read(f);
			}
			if (img == null)
				return;
			img = ed.normalizeImage(img);
			int[] size = ed.fitElementSize(img.getWidth(), img.getHeight(), c.workingImage.getWidth(),
					c.workingImage.getHeight());
			int cx = Math.max(0, (c.workingImage.getWidth() - size[0]) / 2);
			int cy = Math.max(0, (c.workingImage.getHeight() - size[1]) / 2);
			ImageLayer layer = new ImageLayer(c.nextElementId++, img, cx, cy, size[0], size[1]);
			c.activeElements.add(layer);
			c.selectedElements.clear();
			c.selectedElements.add(layer);
			ed.markDirty(targetIdx);
			ed.refreshElementPanel();
			if (c.canvasPanel != null)
				c.canvasPanel.repaint();
			ToastNotification.show(ed, "Bild als Element eingefügt");
		} catch (Exception ex) {
			ed.showErrorDialog("Fehler", "Bild konnte nicht geladen werden: " + ex.getMessage());
		}
	}

	/** Builds the right-edge "drop to Canvas 2" overlay panel. */
	JPanel buildRightDropZone() {
		JPanel zone = new JPanel() {
			@Override
			protected void paintComponent(java.awt.Graphics g) {
				super.paintComponent(g);
				Graphics2D g2 = (Graphics2D) g;
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				int w = getWidth(), h = getHeight();
				g2.setColor(new Color(0, 0, 0, 160));
				g2.fillRoundRect(0, 0, w, h, 12, 12);
				float[] dash = { 8f, 5f };
				g2.setColor(AppColors.ACCENT);
				g2.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, dash, 0));
				g2.drawRoundRect(4, 4, w - 9, h - 9, 10, 10);
				g2.setStroke(new BasicStroke(2));
				int cx = w / 2, cy = h / 2;
				g2.drawLine(cx - 12, cy, cx + 12, cy);
				g2.drawLine(cx + 4, cy - 8, cx + 12, cy);
				g2.drawLine(cx + 4, cy + 8, cx + 12, cy);
				g2.setColor(AppColors.TEXT);
				g2.setFont(new Font("SansSerif", Font.BOLD, 11));
				String t = "2. Canvas";
				FontMetrics fm = g2.getFontMetrics();
				g2.drawString(t, cx - fm.stringWidth(t) / 2, cy + 28);
			}
		};
		zone.setOpaque(false);
		zone.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		zone.addMouseListener(new java.awt.event.MouseAdapter() {
			private javax.swing.Timer hideTimer;

			@Override
			public void mouseClicked(java.awt.event.MouseEvent e) {
				e.consume();
				ed.showQuickOpenDialog(1);
			}

			@Override
			public void mouseExited(java.awt.event.MouseEvent e) {
				hideTimer = new javax.swing.Timer(300, ev -> {
					if (zone.isVisible()) {
						zone.setVisible(false);
						if (ed.ci(0).layeredPane != null)
							ed.ci(0).layeredPane.repaint();
					}
				});
				hideTimer.setRepeats(false);
				hideTimer.start();
			}

			@Override
			public void mouseEntered(java.awt.event.MouseEvent e) {
				if (hideTimer != null) {
					hideTimer.stop();
					hideTimer = null;
				}
			}
		});

		setupDropTarget(zone, 1);
		return zone;
	}

	void repositionRightDropZone() {
		if (ed.rightDropZone == null || ed.ci(0).workingImage == null)
			return;
		int imgRightInCanvas = (int) (ed.ci(0).canvasPanel.getX()
				+ ed.ci(0).workingImage.getWidth() * ed.ci(0).zoom);
		int vpX = imgRightInCanvas - ed.ci(0).scrollPane.getViewport().getViewPosition().x;
		int x = vpX + 16;
		int y = ed.ci(0).layeredPane.getHeight() / 2 - 60;
		int w = 90, h = 120;
		if (x + w < ed.ci(0).layeredPane.getWidth()) {
			ed.rightDropZone.setBounds(x, y, w, h);
		} else {
			ed.rightDropZone.setVisible(false);
		}
	}
}
