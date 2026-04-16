package paint;

import book.PaperFormat;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.*;

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

				// Add dropped image as centered ImageLayer (scaled to fit content area)
				if (droppedImage != null) {
					int contentW = wPx - mL - mR;
					int contentH = hPx - mT  - mB;
					double scale = Math.min(1.0,
							Math.min((double) contentW / droppedImage.getWidth(),
									 (double) contentH / droppedImage.getHeight()));
					int iw = (int) Math.round(droppedImage.getWidth()  * scale);
					int ih = (int) Math.round(droppedImage.getHeight() * scale);
					int ix = mL + (contentW - iw) / 2;
					int iy = mT + (contentH - ih) / 2;
					c.activeElements.add(new ImageLayer(
							c.nextElementId++, ed.deepCopy(droppedImage), ix, iy, iw, ih));
				}

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

	// ── Helpers ───────────────────────────────────────────────────────────────

	private static String nextPageName(File bookDir) {
		List<String> existing = readManifestPages(bookDir);
		return String.format("page_%03d.png", existing.size() + 1);
	}
}
