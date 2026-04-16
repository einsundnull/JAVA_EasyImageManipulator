package paint;

import java.awt.*;
import java.io.File;
import java.util.List;

import javax.swing.*;

/**
 * Sidebar panel listing all books in the books root directory.
 * Clicking a book tile loads its pages into the linked {@link BookPagesPanel}.
 */
class BookListPanel extends BaseSidebarPanel {

	static final int PANEL_W  = BaseSidebarPanel.DEFAULT_PANEL_W;
	static final int TILE_H   = 28;

	private final SelectiveAlphaEditor ed;
	private final BookPagesPanel       linkedPages;
	private JPanel                     tilesContainer;

	BookListPanel(SelectiveAlphaEditor ed, BookPagesPanel linkedPages) {
		this.ed          = ed;
		this.linkedPages = linkedPages;
		setPreferredSize(new Dimension(PANEL_W, 0));
		setMaximumSize(new Dimension(PANEL_W, Integer.MAX_VALUE));
		setAlignmentY(Component.CENTER_ALIGNMENT);
		setBackground(AppColors.BG_DARK);
		setLayout(new BorderLayout());

		// onRefresh = ⟳ button → repurposed as "Neues Buch"
		add(buildSidebarHeader("Bücher", this::showNewBookDialog, null), BorderLayout.NORTH);

		tilesContainer = new JPanel();
		tilesContainer.setLayout(new BoxLayout(tilesContainer, BoxLayout.Y_AXIS));
		tilesContainer.setBackground(AppColors.BG_DARK);
		add(buildSidebarScrollPane(tilesContainer), BorderLayout.CENTER);

		refresh();
		setVisible(false);
	}

	// ── Public API ────────────────────────────────────────────────────────────

	@Override
	public void refresh() {
		tilesContainer.removeAll();
		List<File> books = BookController.listBooks();
		if (books.isEmpty()) {
			JLabel empty = new JLabel("Keine Bücher", SwingConstants.CENTER);
			empty.setForeground(AppColors.TEXT_MUTED);
			empty.setFont(new Font("SansSerif", Font.ITALIC, 11));
			empty.setAlignmentX(Component.CENTER_ALIGNMENT);
			tilesContainer.add(Box.createVerticalStrut(12));
			tilesContainer.add(empty);
		} else {
			tilesContainer.add(Box.createVerticalStrut(2));
			for (File bookDir : books) {
				tilesContainer.add(buildBookTile(bookDir));
				tilesContainer.add(Box.createVerticalStrut(2));
			}
		}
		tilesContainer.revalidate();
		tilesContainer.repaint();
	}

	// ── Tile builder ──────────────────────────────────────────────────────────

	private JPanel buildBookTile(File bookDir) {
		JPanel tile = new JPanel(new BorderLayout());
		tile.setBackground(AppColors.BG_PANEL);
		tile.setMaximumSize(new Dimension(PANEL_W - 8, TILE_H));
		tile.setAlignmentX(Component.CENTER_ALIGNMENT);
		tile.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 4));

		int pageCount = BookController.readManifestPages(bookDir).size();
		JLabel name = new JLabel(bookDir.getName());
		name.setForeground(AppColors.TEXT);
		name.setFont(new Font("SansSerif", Font.PLAIN, 12));

		JLabel count = new JLabel(pageCount + "S");
		count.setForeground(AppColors.TEXT_MUTED);
		count.setFont(new Font("SansSerif", Font.PLAIN, 10));

		tile.add(name,  BorderLayout.CENTER);
		tile.add(count, BorderLayout.EAST);
		tile.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		tile.addMouseListener(new java.awt.event.MouseAdapter() {
			@Override public void mouseClicked(java.awt.event.MouseEvent e) {
				linkedPages.loadBook(bookDir);
			}
			@Override public void mouseEntered(java.awt.event.MouseEvent e) {
				tile.setBackground(AppColors.BTN_HOVER);
			}
			@Override public void mouseExited(java.awt.event.MouseEvent e) {
				tile.setBackground(AppColors.BG_PANEL);
			}
		});
		return tile;
	}

	// ── New book dialog ───────────────────────────────────────────────────────

	private void showNewBookDialog() {
		String name = JOptionPane.showInputDialog(
				ed, "Name des neuen Buches:", "Neues Buch", JOptionPane.PLAIN_MESSAGE);
		if (name == null || name.isBlank()) return;
		try {
			File bookDir = BookController.createBook(name.trim());
			refresh();
			linkedPages.loadBook(bookDir);
		} catch (Exception ex) {
			ed.showErrorDialog("Fehler", "Buch konnte nicht erstellt werden:\n" + ex.getMessage());
		}
	}
}
