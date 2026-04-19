package paint;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;

import book.PaperFormat;

/**
 * Business logic for the book/page system.
 *
 * Storage structure:
 *   %APPDATA%\TransparencyTool\books\
 *     <BookName>\
 *       <BookName>.txt   ← manifest (#Book: / #Pages: sections)
 *       pages\
 *         page_001.png
 *         page_002.png
 */
class BookController {

	private static final String BOOKS_ROOT =
			System.getProperty("user.home")
			+ File.separator + "AppData"
			+ File.separator + "Roaming"
			+ File.separator + "TransparencyTool"
			+ File.separator + "books";

	private final SelectiveAlphaEditor ed;

	BookController(SelectiveAlphaEditor ed) {
		this.ed = ed;
	}

	// ── Storage helpers ───────────────────────────────────────────────────────

	static File getBooksRoot() {
		File f = new File(BOOKS_ROOT);
		f.mkdirs();
		return f;
	}

	static File getPagesDir(File bookDir) {
		File d = new File(bookDir, "pages");
		d.mkdirs();
		return d;
	}

	static List<File> listBooks() {
		File root = getBooksRoot();
		File[] dirs = root.listFiles(File::isDirectory);
		if (dirs == null) return Collections.emptyList();
		Arrays.sort(dirs, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
		return new ArrayList<>(Arrays.asList(dirs));
	}

	static List<File> listPages(File bookDir) {
		List<String> names = readManifestPages(bookDir);
		File pagesDir = getPagesDir(bookDir);
		List<File> pages = new ArrayList<>();
		for (String name : names) {
			File f = new File(pagesDir, name);
			if (f.exists()) pages.add(f);
		}
		return pages;
	}

	// ── Manifest ──────────────────────────────────────────────────────────────

	static void writeManifest(File bookDir, String bookName, List<String> pageNames) throws IOException {
		List<String> lines = new ArrayList<>();
		lines.add("#Book:");
		lines.add("name: " + bookName);
		lines.add("");
		lines.add("#Pages:");
		lines.addAll(pageNames);
		File manifest = new File(bookDir, bookDir.getName() + ".txt");
		Files.write(manifest.toPath(), lines, StandardCharsets.UTF_8);
	}

	static List<String> readManifestPages(File bookDir) {
		File manifest = new File(bookDir, bookDir.getName() + ".txt");
		if (!manifest.exists()) return new ArrayList<>();
		try {
			List<String> lines = Files.readAllLines(manifest.toPath(), StandardCharsets.UTF_8);
			List<String> pages = new ArrayList<>();
			boolean inPages = false;
			for (String line : lines) {
				String t = line.trim();
				if (t.equals("#Pages:")) { inPages = true; continue; }
				if (t.startsWith("#")) { inPages = false; continue; }
				if (inPages && !t.isEmpty()) pages.add(t);
			}
			return pages;
		} catch (IOException e) {
			return new ArrayList<>();
		}
	}

	static File createBook(String name) throws IOException {
		File bookDir = new File(getBooksRoot(), name);
		bookDir.mkdirs();
		getPagesDir(bookDir).mkdirs();
		writeManifest(bookDir, name, new ArrayList<>());
		System.out.println("[BookController] Buch erstellt: " + bookDir.getAbsolutePath());
		return bookDir;
	}

	// ── Book selection dialog ─────────────────────────────────────────────────

	/**
	 * Shows a modal "Buch wählen oder erstellen" dialog.
	 *
	 * <ul>
	 *   <li>If books exist: shows a list of them plus a "Neues Buch" name field.</li>
	 *   <li>If no books exist: shows only the name field.</li>
	 * </ul>
	 *
	 * @return the chosen or newly-created book directory, or {@code null} if cancelled.
	 */
	File pickOrCreateBook() {
		List<File> books = listBooks();
		boolean hasBooks = !books.isEmpty();

		int dlgH = hasBooks ? 310 : 170;
		JDialog dialog = ed.createBaseDialog("Buch wählen", 360, dlgH);
		JPanel content = ed.centeredColumnPanel(10, 16, 12);

		File[] result = { null };

		// ── Existing books list (only when books are present) ─────────────────
		JList<String> bookList = null;
		if (hasBooks) {
			JLabel listLbl = new JLabel("Bestehendes Buch:");
			listLbl.setForeground(AppColors.TEXT);
			listLbl.setFont(new Font("SansSerif", Font.BOLD, 11));
			listLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
			content.add(listLbl);
			content.add(Box.createVerticalStrut(4));

			DefaultListModel<String> model = new DefaultListModel<>();
			for (File b : books) model.addElement(b.getName());
			bookList = new JList<>(model);
			bookList.setBackground(AppColors.BTN_BG);
			bookList.setForeground(AppColors.TEXT);
			bookList.setSelectionBackground(AppColors.ACCENT_ACTIVE);
			bookList.setFont(new Font("SansSerif", Font.PLAIN, 12));
			bookList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
			bookList.setSelectedIndex(0);

			JScrollPane sp = new JScrollPane(bookList);
			sp.setPreferredSize(new Dimension(316, 110));
			sp.setMaximumSize(new Dimension(316, 110));
			sp.setBorder(BorderFactory.createLineBorder(AppColors.BORDER));
			sp.setAlignmentX(Component.LEFT_ALIGNMENT);
			TileGalleryPanel.applyDarkScrollBar(sp.getVerticalScrollBar());
			content.add(sp);
			content.add(Box.createVerticalStrut(10));

			JLabel sep = new JLabel("── oder Neues Buch ──────────────────");
			sep.setForeground(AppColors.TEXT_MUTED);
			sep.setFont(new Font("SansSerif", Font.PLAIN, 10));
			sep.setAlignmentX(Component.LEFT_ALIGNMENT);
			content.add(sep);
			content.add(Box.createVerticalStrut(6));
		}

		// ── New book name row ─────────────────────────────────────────────────
		JPanel nameRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		nameRow.setOpaque(false);
		nameRow.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel nameLbl = new JLabel(hasBooks ? "Neues Buch:" : "Name:");
		nameLbl.setForeground(AppColors.TEXT);
		nameLbl.setFont(new Font("SansSerif", Font.PLAIN, 11));

		JTextField nameField = new JTextField(18);
		nameField.setBackground(AppColors.BTN_BG);
		nameField.setForeground(AppColors.TEXT);
		nameField.setCaretColor(AppColors.TEXT);
		nameField.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(AppColors.BORDER),
				BorderFactory.createEmptyBorder(2, 4, 2, 4)));
		nameField.setFont(new Font("SansSerif", Font.PLAIN, 12));

		nameRow.add(nameLbl);
		nameRow.add(nameField);
		content.add(nameRow);
		content.add(Box.createVerticalStrut(14));

		// ── Buttons ───────────────────────────────────────────────────────────
		JButton okBtn     = UIComponentFactory.buildButton("OK",        AppColors.ACCENT, AppColors.ACCENT_HOVER);
		JButton cancelBtn = UIComponentFactory.buildButton("Abbrechen", AppColors.BTN_BG, AppColors.BTN_HOVER);
		okBtn.setForeground(Color.WHITE);

		JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
		btnRow.setOpaque(false);
		btnRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		btnRow.add(okBtn);
		btnRow.add(cancelBtn);
		content.add(btnRow);

		// ── Wiring ────────────────────────────────────────────────────────────
		final JList<String> finalList  = bookList;
		final List<File>    finalBooks = books;

		Runnable confirm = () -> {
			String newName = nameField.getText().trim();
			if (!newName.isEmpty()) {
				// Create new book
				try {
					result[0] = createBook(newName);
				} catch (IOException ex) {
					ed.showErrorDialog("Fehler", "Buch konnte nicht erstellt werden:\n" + ex.getMessage());
					return;
				}
			} else if (finalList != null && finalList.getSelectedIndex() >= 0) {
				result[0] = finalBooks.get(finalList.getSelectedIndex());
			} else {
				// Nothing selected and no name typed
				nameField.requestFocusInWindow();
				return;
			}
			dialog.dispose();
		};

		okBtn    .addActionListener(e -> confirm.run());
		cancelBtn.addActionListener(e -> dialog.dispose());
		nameField.addActionListener(e -> confirm.run());

		// If a book is selected in the list, clear the name field and vice-versa
		if (bookList != null) {
			final JList<String> bl = bookList;
			bl.addListSelectionListener(e -> {
				if (!e.getValueIsAdjusting() && bl.getSelectedIndex() >= 0)
					nameField.setText("");
			});
			nameField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
				void clearList() { if (!nameField.getText().isEmpty()) bl.clearSelection(); }
				@Override public void insertUpdate (javax.swing.event.DocumentEvent e) { clearList(); }
				@Override public void removeUpdate (javax.swing.event.DocumentEvent e) { clearList(); }
				@Override public void changedUpdate(javax.swing.event.DocumentEvent e) { clearList(); }
			});
		}

		dialog.add(content, BorderLayout.CENTER);
		if (!hasBooks) nameField.requestFocusInWindow();
		dialog.setVisible(true); // blocks – modal
		return result[0];
	}

	// ── Page dialog ───────────────────────────────────────────────────────────

	/**
	 * Shows "Neue Seite" dialog.  On confirm:
	 *  1. Creates white page PNG and saves to {@code bookDir/pages/page_NNN.png}
	 *  2. Updates manifest
	 *  3. Opens page in active canvas with {@code droppedImage} as a centered ImageLayer
	 *  4. Runs {@code onAdded}
	 *
	 * @param droppedImage  image to place as layer (may be null → empty page)
	 * @param bookDir       target book directory
	 * @param onAdded       called on EDT after page is created
	 */
	void showNewPageDialog(BufferedImage droppedImage, File bookDir, Runnable onAdded) {
		PaperFormat.Format[] formats = PaperFormat.Format.values();
		String[] formatLabels = new String[formats.length];
		for (int i = 0; i < formats.length; i++) formatLabels[i] = formats[i].toString();

		JComboBox<String> formatCombo = new JComboBox<>(formatLabels);
		formatCombo.setSelectedIndex(4); // A4 default
		formatCombo.setBackground(AppColors.BTN_BG);
		formatCombo.setForeground(AppColors.TEXT);

		JComboBox<String> orientCombo = new JComboBox<>(new String[]{ "Hochformat", "Querformat" });
		orientCombo.setBackground(AppColors.BTN_BG);
		orientCombo.setForeground(AppColors.TEXT);

		JCheckBox marginsBox = new JCheckBox("Mit Rändern", true);
		marginsBox.setOpaque(false);
		marginsBox.setForeground(AppColors.TEXT);

		JPanel grid = new JPanel(new java.awt.GridLayout(3, 2, 6, 4));
		grid.setOpaque(false);
		JLabel lFmt = new JLabel("Format:");        lFmt.setForeground(AppColors.TEXT);
		JLabel lOri = new JLabel("Ausrichtung:");   lOri.setForeground(AppColors.TEXT);
		JLabel lMrg = new JLabel("");
		grid.add(lFmt); grid.add(formatCombo);
		grid.add(lOri); grid.add(orientCombo);
		grid.add(lMrg); grid.add(marginsBox);

		JDialog dialog = ed.createBaseDialog("Neue Seite", 320, 240);
		JPanel content = ed.centeredColumnPanel(16, 20, 12);
		content.add(grid);
		content.add(Box.createVerticalStrut(12));

		JPanel row = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 8, 0));
		row.setOpaque(false);
		JButton okBtn     = UIComponentFactory.buildButton("Erstellen",  AppColors.ACCENT,  AppColors.ACCENT_HOVER);
		JButton cancelBtn = UIComponentFactory.buildButton("Abbrechen",  AppColors.BTN_BG,  AppColors.BTN_HOVER);
		okBtn.setForeground(Color.WHITE);

		okBtn.addActionListener(e -> {
			PaperFormat.Format fmt = formats[formatCombo.getSelectedIndex()];
			boolean landscape   = orientCombo.getSelectedIndex() == 1;
			boolean withMargins = marginsBox.isSelected();

			final double PX = 96.0 / 25.4;
			int wPx = (int) Math.round((landscape ? fmt.getWidthLandscape()  : fmt.getWidthPortrait())  * PX);
			int hPx = (int) Math.round((landscape ? fmt.getHeightLandscape() : fmt.getHeightPortrait()) * PX);
			int mT  = (int) Math.round(fmt.getMarginTop()    * PX);
			int mB  = (int) Math.round(fmt.getMarginBottom() * PX);
			int mL  = (int) Math.round(fmt.getMarginInner()  * PX);
			int mR  = (int) Math.round(fmt.getMarginOuter()  * PX);

			// White page background
			BufferedImage page = new BufferedImage(wPx, hPx, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g2 = page.createGraphics();
			g2.setColor(Color.WHITE);
			g2.fillRect(0, 0, wPx, hPx);
			if (withMargins) {
				g2.setColor(new Color(0, 120, 220, 180));
				float[] dash = { 6f, 4f };
				g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, dash, 0f));
				g2.drawRect(mL, mT, wPx - mL - mR, hPx - mT - mB);
			}
			g2.dispose();

			try {
				// Save page PNG
				String pageName = nextPageName(bookDir);
				File pageFile = new File(getPagesDir(bookDir), pageName);
				ImageIO.write(page, "PNG", pageFile);

				// Update manifest
				List<String> pages = readManifestPages(bookDir);
				pages.add(pageName);
				writeManifest(bookDir, bookDir.getName(), pages);
				System.out.println("[BookController] Seite gespeichert: " + pageFile.getAbsolutePath());

				// Open in canvas
				CanvasInstance c = ed.ci();
				c.workingImage       = ed.normalizeImage(page);
				c.originalImage      = ed.deepCopy(c.workingImage);
				c.activeElements     = new ArrayList<>();
				c.selectedElements.clear();
				c.undoStack.clear();
				c.redoStack.clear();
				c.selectedAreas.clear();
				c.floatingImg        = null;
				c.floatRect          = null;
				c.sourceFile         = pageFile;
				c.hasUnsavedChanges  = false;

				// Auto-create book wrapping TextLayer covering the content area
				int contentX = withMargins ? mL : 0;
				int contentY = withMargins ? mT : 0;
				int contentW = withMargins ? (wPx - mL - mR) : wPx;
				int contentH = withMargins ? (hPx - mT  - mB) : hPx;
				c.activeElements.add(TextLayer.wrappingOf(
						c.nextElementId++, "", "SansSerif", 12,
						false, false, java.awt.Color.BLACK,
						contentX, contentY, contentW, contentH));

				// Add dropped image as centered ImageLayer (scaled to fit content area)
				if (droppedImage != null) {
					double scale = Math.min(1.0,
							Math.min((double) contentW / droppedImage.getWidth(),
									 (double) contentH / droppedImage.getHeight()));
					int iw = (int) Math.round(droppedImage.getWidth()  * scale);
					int ih = (int) Math.round(droppedImage.getHeight() * scale);
					int ix = contentX + (contentW - iw) / 2;
					int iy = contentY + (contentH - ih) / 2;
					c.activeElements.add(new ImageLayer(
							c.nextElementId++, ed.deepCopy(droppedImage), ix, iy, iw, ih));
				}

				// Persist initial layers immediately so reload always works
				savePageScene(pageFile, c.activeElements);
				c.activeSceneFile = getPageManifest(pageFile);

				ed.swapToImageView(ed.activeCanvasIndex);
				SwingUtilities.invokeLater(() -> ed.fitToViewport(ed.activeCanvasIndex));
				ed.refreshElementPanel();
				ed.updateTitle();
				ed.updateStatus();
				ed.setBottomButtonsEnabled(true);

				dialog.dispose();
				if (onAdded != null) SwingUtilities.invokeLater(onAdded);

			} catch (IOException ex) {
				ed.showErrorDialog("Fehler", "Seite konnte nicht gespeichert werden:\n" + ex.getMessage());
			}
		});

		cancelBtn.addActionListener(e -> dialog.dispose());
		row.add(okBtn);
		row.add(cancelBtn);
		content.add(row);
		dialog.add(content);
		dialog.setVisible(true);
	}

	// ── Immediate page creation (no dialog) ─────────────────────────────────

	/**
	 * Copies {@code src} into the book as a new page immediately, without showing any dialog.
	 * {@code layers} (may be null/empty) are saved as the page's scene layers.
	 */
	void createPageFromFile(File src, List<Layer> layers, File bookDir, Runnable onAdded) {
		try {
			String pageName = nextPageName(bookDir);
			File pageFile = new File(getPagesDir(bookDir), pageName);
			java.nio.file.Files.copy(src.toPath(), pageFile.toPath(),
					java.nio.file.StandardCopyOption.REPLACE_EXISTING);

			List<String> pages = readManifestPages(bookDir);
			pages.add(pageName);
			writeManifest(bookDir, bookDir.getName(), pages);

			BufferedImage img = ImageIO.read(pageFile);
			if (img == null) { ed.showErrorDialog("Fehler", "Bild konnte nicht geladen werden."); return; }

			CanvasInstance c = ed.ci();
			c.workingImage      = ed.normalizeImage(img);
			c.originalImage     = ed.deepCopy(c.workingImage);
			c.activeElements    = layers != null ? new ArrayList<>(layers) : new ArrayList<>();
			c.selectedElements.clear();
			c.undoStack.clear(); c.redoStack.clear();
			c.selectedAreas.clear();
			c.floatingImg = null; c.floatRect = null;
			c.sourceFile        = pageFile;
			c.hasUnsavedChanges = false;

			savePageScene(pageFile, c.activeElements);
			c.activeSceneFile = getPageManifest(pageFile);

			ed.swapToImageView(ed.activeCanvasIndex);
			SwingUtilities.invokeLater(() -> ed.fitToViewport(ed.activeCanvasIndex));
			ed.refreshElementPanel();
			ed.updateTitle();
			ed.updateStatus();
			ed.setBottomButtonsEnabled(true);

			if (onAdded != null) SwingUtilities.invokeLater(onAdded);
		} catch (IOException ex) {
			ed.showErrorDialog("Fehler", "Seite konnte nicht erstellt werden:\n" + ex.getMessage());
		}
	}

	/**
	 * Loads the scene layers associated with an image file, if a matching scene
	 * directory exists next to it. Returns an empty list when no scene is found.
	 */
	static List<Layer> loadLayersForFile(File imageFile) {
		if (imageFile == null) return new ArrayList<>();
		String name = imageFile.getName();
		int dot = name.lastIndexOf('.');
		String base = dot > 0 ? name.substring(0, dot) : name;
		File sceneDir  = new File(imageFile.getParentFile(), base);
		File manifest  = new File(sceneDir, base + ".txt");
		if (!manifest.exists()) return new ArrayList<>();
		try {
			SceneFileReader.SceneData data = SceneFileReader.readScene(sceneDir, base);
			List<Layer> all = new ArrayList<>();
			int nextId = System.identityHashCode(new Object());
			for (SceneFileReader.ImageLayerRef ref : data.imageLayers) {
				java.awt.image.BufferedImage img = ImageLoader.loadImage(ref.file);
				if (img != null) {
					int w = ref.w > 0 ? ref.w : img.getWidth();
					int h = ref.h > 0 ? ref.h : img.getHeight();
					all.add(new ImageLayer(nextId++, img, ref.x, ref.y, w, h, ref.rotation, ref.opacity));
				}
			}
			all.addAll(data.textLayers);
			all.addAll(data.pathLayers);
			return all;
		} catch (Exception e) {
			return new ArrayList<>();
		}
	}

	// ── Page copy ─────────────────────────────────────────────────────────────

	/**
	 * Copies {@code sourcePage} as a new page at the end of {@code bookDir}.
	 * Updates the manifest so the copy appears in the page list.
	 *
	 * @param sourcePage page file to duplicate (must be inside {@code bookDir/pages/})
	 * @param bookDir    book directory containing the pages/ sub-folder
	 * @return the newly created copy file
	 */
	static File copyPage(File sourcePage, File bookDir) throws IOException {
		File pagesDir = getPagesDir(bookDir);
		String newName = nextPageName(bookDir);
		File dest = new File(pagesDir, newName);
		java.nio.file.Files.copy(sourcePage.toPath(), dest.toPath());

		List<String> pages = readManifestPages(bookDir);
		pages.add(newName);
		writeManifest(bookDir, bookDir.getName(), pages);
		System.out.println("[BookController] Seite kopiert: " + dest.getAbsolutePath());
		return dest;
	}

	// ── Page rename ──────────────────────────────────────────────────────────

	static void renamePage(File pageFile, String newBaseName, File bookDir) throws IOException {
		String newFileName = newBaseName.endsWith(".png") ? newBaseName : newBaseName + ".png";
		File newFile = new File(pageFile.getParentFile(), newFileName);
		if (newFile.equals(pageFile)) return;
		if (newFile.exists()) throw new IOException("Datei existiert bereits: " + newFileName);
		java.nio.file.Files.move(pageFile.toPath(), newFile.toPath());
		// Also rename scene directory if present
		File oldSceneDir = getPageSceneDir(pageFile);
		if (oldSceneDir.exists())
			oldSceneDir.renameTo(getPageSceneDir(newFile));
		List<String> pages = readManifestPages(bookDir);
		int idx = pages.indexOf(pageFile.getName());
		if (idx >= 0) {
			pages.set(idx, newFileName);
			writeManifest(bookDir, bookDir.getName(), pages);
		}
	}

	// ── Page scene save / load ───────────────────────────────────────────────

	/**
	 * Returns the scene directory for a page PNG.
	 * e.g. books/MyBook/pages/page_001.png → books/MyBook/pages/page_001/
	 */
	static File getPageSceneDir(File pageFile) {
		String name = pageFile.getName();
		int dot = name.lastIndexOf('.');
		String base = dot > 0 ? name.substring(0, dot) : name;
		return new File(pageFile.getParentFile(), base);
	}

	/**
	 * Returns the .txt manifest file for a page.
	 * e.g. books/MyBook/pages/page_001.png → books/MyBook/pages/page_001/page_001.txt
	 */
	static File getPageManifest(File pageFile) {
		File sceneDir = getPageSceneDir(pageFile);
		String name = pageFile.getName();
		int dot = name.lastIndexOf('.');
		String base = dot > 0 ? name.substring(0, dot) : name;
		return new File(sceneDir, base + ".txt");
	}

	/** Saves all layers for a page using the standard SceneFileWriter (.txt) format. */
	static void savePageScene(File pageFile, List<Layer> layers) {
		try {
			File sceneDir = getPageSceneDir(pageFile);
			String name = pageFile.getName();
			int dot = name.lastIndexOf('.');
			String base = dot > 0 ? name.substring(0, dot) : name;
			SceneFileWriter.writeScene(sceneDir, base, pageFile, layers);
		} catch (IOException ex) {
			System.err.println("[BookController] Page-Scene speichern fehlgeschlagen: " + ex.getMessage());
		}
	}

	/**
	 * Loads layers from the scene directory next to the page PNG.
	 * Returns null if no scene exists yet.
	 */
	static SceneFileReader.SceneData loadPageScene(File pageFile) {
		File sceneDir = getPageSceneDir(pageFile);
		File manifest = getPageManifest(pageFile);
		if (!manifest.exists()) return null;
		try {
			String name = pageFile.getName();
			int dot = name.lastIndexOf('.');
			String base = dot > 0 ? name.substring(0, dot) : name;
			return SceneFileReader.readScene(sceneDir, base);
		} catch (IOException ex) {
			System.err.println("[BookController] Page-Scene laden fehlgeschlagen: " + ex.getMessage());
			return null;
		}
	}

	// ── Helpers ───────────────────────────────────────────────────────────────

	private static String nextPageName(File bookDir) {
		List<String> existing = readManifestPages(bookDir);
		return String.format("page_%03d.png", existing.size() + 1);
	}
}
