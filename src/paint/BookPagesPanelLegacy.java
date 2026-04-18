package paint;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Image;
import java.awt.Point;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.TransferHandler;

/**
 * Sidebar panel showing the pages of the currently selected book.
 *
 * Drop target: accepts images and internal TilePanel drags.
 * On drop → shows "Neue Seite" dialog → creates page PNG → opens in canvas.
 */
class BookPagesPanelLegacy extends BaseSidebarPanel {

	static final int PANEL_W = BaseSidebarPanel.DEFAULT_PANEL_W;
	static final int THUMB_W = PANEL_W - 16;
	static final int THUMB_H = (int) (THUMB_W * 1.4142); // A4 ratio

	/** DataFlavor for intra-panel page-copy drags (left-click drag within this panel). */
	private static final DataFlavor PAGE_DRAG_FLAVOR =
			new DataFlavor(File.class, "BookPageFile");

	private final SelectiveAlphaEditor ed;
	private File   currentBookDir  = null;
	private JPanel tilesContainer;

	BookPagesPanelLegacy(SelectiveAlphaEditor ed) {
		this.ed = ed;
		setPreferredSize(new Dimension(PANEL_W, 0));
		setMaximumSize(new Dimension(PANEL_W, Integer.MAX_VALUE));
		setAlignmentY(Component.CENTER_ALIGNMENT);
		setBackground(AppColors.BG_DARK);
		setLayout(new BorderLayout());

		add(buildSidebarHeader("Seiten", this::refresh, null), BorderLayout.NORTH);

		tilesContainer = new JPanel();
		tilesContainer.setLayout(new BoxLayout(tilesContainer, BoxLayout.Y_AXIS));
		tilesContainer.setBackground(AppColors.BG_DARK);

		add(buildSidebarScrollPane(tilesContainer), BorderLayout.CENTER);

		// Drop target — accepts image drops, internal TT tile drags, and intra-panel page copies
		new DropTarget(tilesContainer, DnDConstants.ACTION_COPY, new DropTargetAdapter() {
			@Override public void dragEnter(DropTargetDragEvent e) { e.acceptDrag(DnDConstants.ACTION_COPY); }
			@Override public void dragOver(DropTargetDragEvent e)  { e.acceptDrag(DnDConstants.ACTION_COPY); }
			@Override public void drop(DropTargetDropEvent dtde) {
				dtde.acceptDrop(DnDConstants.ACTION_COPY);
				var trans = dtde.getTransferable();

				// ── Intra-panel page copy: drag a page tile within this panel ──────
				if (trans.isDataFlavorSupported(PAGE_DRAG_FLAVOR)) {
					if (currentBookDir == null) { dtde.dropComplete(false); return; }
					try {
						File srcPage = (File) trans.getTransferData(PAGE_DRAG_FLAVOR);
						BookController.copyPage(srcPage, currentBookDir);
						dtde.dropComplete(true);
						SwingUtilities.invokeLater(() -> loadBook(currentBookDir));
					} catch (Exception ex) {
						System.err.println("[BookPagesPanel] Seiten-Kopie fehlgeschlagen: " + ex.getMessage());
						dtde.dropComplete(false);
					}
					return;
				}

				// ── External image / file / TT drag → new page ────────────────────
				BufferedImage img = resolveDroppedImage(dtde);
				dtde.dropComplete(true);

				if (currentBookDir == null) {
					// No book selected yet → ask the user to pick or create one
					SwingUtilities.invokeLater(() -> {
						File book = ed.bookController.pickOrCreateBook();
						if (book == null) return;
						currentBookDir = book;
						ed.bookController.showNewPageDialog(img, book,
								() -> SwingUtilities.invokeLater(() -> loadBook(book)));
					});
				} else {
					ed.bookController.showNewPageDialog(img, currentBookDir,
							() -> SwingUtilities.invokeLater(() -> loadBook(currentBookDir)));
				}
			}
		});

		showPlaceholder("Kein Buch ausgewählt");
		setVisible(false);
	}

	// ── Public API ────────────────────────────────────────────────────────────

	void loadBook(File bookDir) {
		this.currentBookDir = bookDir;
		refresh();
	}

	@Override
	public void refresh() {
		tilesContainer.removeAll();
		if (currentBookDir == null) {
			showPlaceholder("Kein Buch ausgewählt");
			return;
		}
		List<File> pages = BookController.listPages(currentBookDir);
		if (pages.isEmpty()) {
			showPlaceholder("Noch keine Seiten\n– Bild hier ablegen –");
		} else {
			tilesContainer.add(Box.createVerticalStrut(4));
			for (File pageFile : pages) {
				tilesContainer.add(buildPageTile(pageFile));
				tilesContainer.add(Box.createVerticalStrut(4));
			}
		}
		tilesContainer.revalidate();
		tilesContainer.repaint();
	}

	// ── Tile builder ──────────────────────────────────────────────────────────

	private JPanel buildPageTile(File pageFile) {
		JPanel tile = new JPanel(new BorderLayout(0, 2));
		tile.setBackground(AppColors.BG_PANEL);
		tile.setMaximumSize(new Dimension(THUMB_W + 8, THUMB_H + 24));
		tile.setAlignmentX(Component.CENTER_ALIGNMENT);
		tile.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

		JLabel thumbLabel = new JLabel();
		thumbLabel.setHorizontalAlignment(SwingConstants.CENTER);
		thumbLabel.setPreferredSize(new Dimension(THUMB_W, THUMB_H));
		thumbLabel.setBackground(Color.WHITE);
		thumbLabel.setOpaque(true);

		SwingWorker<ImageIcon, Void> loader = new SwingWorker<>() {
			@Override protected ImageIcon doInBackground() throws Exception {
				BufferedImage img = ImageIO.read(pageFile);
				if (img == null) return null;
				double scale = Math.min((double) THUMB_W / img.getWidth(),
						                (double) THUMB_H / img.getHeight());
				int tw = (int) (img.getWidth()  * scale);
				int th = (int) (img.getHeight() * scale);
				return new ImageIcon(img.getScaledInstance(tw, th, Image.SCALE_SMOOTH));
			}
			@Override protected void done() {
				try { ImageIcon ic = get(); if (ic != null) thumbLabel.setIcon(ic); }
				catch (Exception ignored) {}
			}
		};
		loader.execute();

		String name = pageFile.getName().replaceAll("\\.png$", "");
		JLabel nameLabel = new JLabel(name, SwingConstants.CENTER);
		nameLabel.setForeground(AppColors.TEXT);
		nameLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));

		tile.add(thumbLabel, BorderLayout.CENTER);
		tile.add(nameLabel,  BorderLayout.SOUTH);
		tile.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		tile.addMouseListener(new java.awt.event.MouseAdapter() {
			@Override public void mouseClicked(java.awt.event.MouseEvent e) {
				ed.loadFile(pageFile, ed.activeCanvasIndex);
			}
			@Override public void mouseEntered(java.awt.event.MouseEvent e) {
				tile.setBackground(AppColors.BTN_HOVER);
			}
			@Override public void mouseExited(java.awt.event.MouseEvent e) {
				tile.setBackground(AppColors.BG_PANEL);
			}
		});

		// ── Intra-panel drag-to-copy: left-click drag within BookPagesPanel ───
		tile.setTransferHandler(new TransferHandler() {
			@Override public int getSourceActions(JComponent c) { return COPY; }
			@Override protected Transferable createTransferable(JComponent c) {
				return new Transferable() {
					@Override public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[]{ PAGE_DRAG_FLAVOR }; }
					@Override public boolean isDataFlavorSupported(DataFlavor f) { return PAGE_DRAG_FLAVOR.equals(f); }
					@Override public Object getTransferData(DataFlavor f) { return pageFile; }
				};
			}
		});

		Point[] dragStart = { null };
		tile.addMouseListener(new MouseAdapter() {
			@Override public void mousePressed(MouseEvent e) {
				if (SwingUtilities.isLeftMouseButton(e)) dragStart[0] = e.getPoint();
			}
			@Override public void mouseReleased(MouseEvent e) { dragStart[0] = null; }
		});
		tile.addMouseMotionListener(new MouseMotionAdapter() {
			@Override public void mouseDragged(MouseEvent e) {
				if (dragStart[0] == null) return;
				int dx = Math.abs(e.getX() - dragStart[0].x);
				int dy = Math.abs(e.getY() - dragStart[0].y);
				if (dx > 6 || dy > 6) {
					dragStart[0] = null;
					tile.getTransferHandler().exportAsDrag(tile, e, TransferHandler.COPY);
				}
			}
		});

		return tile;
	}

	private void showPlaceholder(String text) {
		tilesContainer.removeAll();
		tilesContainer.add(Box.createVerticalStrut(16));
		for (String line : text.split("\n")) {
			JLabel lbl = new JLabel(line, SwingConstants.CENTER);
			lbl.setForeground(AppColors.TEXT_MUTED);
			lbl.setFont(new Font("SansSerif", Font.ITALIC, 11));
			lbl.setAlignmentX(Component.CENTER_ALIGNMENT);
			tilesContainer.add(lbl);
		}
		tilesContainer.revalidate();
		tilesContainer.repaint();
	}

	// ── Drop helpers ──────────────────────────────────────────────────────────

	private BufferedImage resolveDroppedImage(DropTargetDropEvent dtde) {
		var trans = dtde.getTransferable();
		try {
			// Direct image flavor
			if (trans.isDataFlavorSupported(DataFlavor.imageFlavor)) {
				Image img = (Image) trans.getTransferData(DataFlavor.imageFlavor);
				if (img instanceof BufferedImage bi) return bi;
				BufferedImage bi = new BufferedImage(img.getWidth(null), img.getHeight(null),
						BufferedImage.TYPE_INT_ARGB);
				bi.createGraphics().drawImage(img, 0, 0, null);
				return bi;
			}
			// File list (external drag from Explorer or TileGallery)
			if (trans.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
				@SuppressWarnings("unchecked")
				List<File> files = (List<File>) trans.getTransferData(DataFlavor.javaFileListFlavor);
				for (File f : files)
					if (FileLoadController.isSupportedFile(f)) return ImageIO.read(f);
			}
			// Internal TT right-click-drag (BaseSidebarPanel.FILE_COPY_FLAVOR)
			if (trans.isDataFlavorSupported(BaseSidebarPanel.FILE_COPY_FLAVOR)) {
				BaseSidebarPanel.FileForCopy fc =
						(BaseSidebarPanel.FileForCopy) trans.getTransferData(BaseSidebarPanel.FILE_COPY_FLAVOR);
				if (fc != null && FileLoadController.isSupportedFile(fc.file))
					return ImageIO.read(fc.file);
			}
		} catch (Exception ignored) {}
		return null;
	}
}
