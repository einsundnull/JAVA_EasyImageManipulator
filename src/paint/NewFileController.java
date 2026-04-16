package paint;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import javax.imageio.ImageIO;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import book.PaperFormat;

/**
 * Handles creation of new bitmaps/book-sheets and canvas background dialog.
 * Extracted from SelectiveAlphaEditor.
 */
class NewFileController {

	private final SelectiveAlphaEditor ed;

	NewFileController(SelectiveAlphaEditor ed) {
		this.ed = ed;
	}

	/** Creates a new blank ARGB bitmap after asking for dimensions. */
	void doNewBitmap() {
		if (ed.bookModeBtn.isSelected()) {
			doNewBookSheet();
			return;
		}

		JTextField wField = new JTextField("1024", 5);
		JTextField hField = new JTextField("1024", 5);
		for (JTextField f : new JTextField[] { wField, hField }) {
			f.setBackground(AppColors.BTN_BG);
			f.setForeground(AppColors.TEXT);
			f.setCaretColor(AppColors.TEXT);
		}
		JPanel grid = new JPanel(new GridLayout(2, 2, 6, 4));
		grid.setOpaque(false);
		JLabel wl = new JLabel("Breite (px):");
		wl.setForeground(AppColors.TEXT);
		JLabel hl = new JLabel("Höhe  (px):");
		hl.setForeground(AppColors.TEXT);
		grid.add(wl);
		grid.add(wField);
		grid.add(hl);
		grid.add(hField);

		JDialog dialog = ed.createBaseDialog("Neue Bitmap", 300, 200);
		JPanel content = ed.centeredColumnPanel(16, 20, 12);
		content.add(grid);
		content.add(Box.createVerticalStrut(14));
		JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
		row.setOpaque(false);
		JButton ok = UIComponentFactory.buildButton("Erstellen", AppColors.ACCENT, AppColors.ACCENT_HOVER);
		JButton can = UIComponentFactory.buildButton("Abbrechen", AppColors.BTN_BG, AppColors.BTN_HOVER);
		ok.setForeground(Color.WHITE);
		ok.addActionListener(e -> {
			try {
				int nw = Math.max(1, Integer.parseInt(wField.getText().trim()));
				int nh = Math.max(1, Integer.parseInt(hField.getText().trim()));
				CanvasInstance c = ed.ci();
				if (c.sourceFile != null)
					ed.fileLoader.saveCurrentState();
				c.workingImage = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
				c.originalImage = ed.deepCopy(c.workingImage);
				c.activeElements = new ArrayList<>();
				c.selectedElements.clear();
				c.undoStack.clear();
				c.redoStack.clear();
				c.selectedAreas.clear();
				c.floatingImg = null;
				c.floatRect = null;

				File saveDir = c.lastIndexedDir != null ? c.lastIndexedDir
						: new File(System.getProperty("user.home"));
				int counter = 1;
				File newFile;
				do {
					newFile = new File(saveDir, "Untitled_" + counter + ".png");
					counter++;
				} while (newFile.exists());

				try {
					ImageIO.write(c.workingImage, "PNG", newFile);
					c.sourceFile = newFile;
					c.hasUnsavedChanges = false;
					ed.dirtyFiles.remove(c.sourceFile);

					if (!c.directoryImages.contains(newFile)) {
						c.directoryImages.add(newFile);
						c.tileGallery.addFiles(Arrays.asList(newFile));
					}
					c.tileGallery.setActiveFile(newFile);
					c.currentImageIndex = c.directoryImages.indexOf(newFile);
				} catch (IOException ex) {
					ed.showErrorDialog("Speicherfehler",
							"Neue Bitmap konnte nicht gespeichert werden:\n" + ex.getMessage());
					return;
				}

				ed.swapToImageView(ed.activeCanvasIndex);
				SwingUtilities.invokeLater(() -> ed.fitToViewport(ed.activeCanvasIndex));
				ed.updateTitle();
				ed.updateStatus();
				ed.updateDirtyUI();
				ed.setBottomButtonsEnabled(true);
			} catch (NumberFormatException ex) {
				ed.showErrorDialog("Ungültige Eingabe", "Bitte ganzzahlige Pixelwerte eingeben.");
			}
			dialog.dispose();
		});
		can.addActionListener(e -> dialog.dispose());
		row.add(ok);
		row.add(can);
		content.add(row);
		dialog.add(content);
		dialog.setVisible(true);
	}

	/** Creates a new sheet with paper format, orientation, and margin guides. */
	void doNewBookSheet() {
		PaperFormat.Format[] formats = PaperFormat.Format.values();
		String[] formatLabels = new String[formats.length];
		for (int i = 0; i < formats.length; i++)
			formatLabels[i] = formats[i].toString();
		JComboBox<String> formatCombo = new JComboBox<>(formatLabels);
		formatCombo.setSelectedIndex(4);
		formatCombo.setBackground(AppColors.BTN_BG);
		formatCombo.setForeground(AppColors.TEXT);

		String[] orientations = { "Hochformat", "Querformat" };
		JComboBox<String> orientCombo = new JComboBox<>(orientations);
		orientCombo.setSelectedIndex(0);
		orientCombo.setBackground(AppColors.BTN_BG);
		orientCombo.setForeground(AppColors.TEXT);

		JCheckBox marginsCheckBox = new JCheckBox("Mit Rändern", true);
		marginsCheckBox.setOpaque(false);
		marginsCheckBox.setForeground(AppColors.TEXT);

		JPanel grid = new JPanel(new GridLayout(3, 2, 6, 4));
		grid.setOpaque(false);
		JLabel formatLabel = new JLabel("Format:");
		formatLabel.setForeground(AppColors.TEXT);
		grid.add(formatLabel);
		grid.add(formatCombo);
		JLabel orientLabel = new JLabel("Ausrichtung:");
		orientLabel.setForeground(AppColors.TEXT);
		grid.add(orientLabel);
		grid.add(orientCombo);
		JLabel marginsLabel = new JLabel("");
		marginsLabel.setForeground(AppColors.TEXT);
		grid.add(marginsLabel);
		grid.add(marginsCheckBox);

		JDialog dialog = ed.createBaseDialog("Neues Blatt", 320, 240);
		JPanel content = ed.centeredColumnPanel(16, 20, 12);
		content.add(grid);
		content.add(Box.createVerticalStrut(12));

		JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
		row.setOpaque(false);
		JButton okBtn = UIComponentFactory.buildButton("Erstellen", AppColors.ACCENT, AppColors.ACCENT_HOVER);
		JButton cancelBtn = UIComponentFactory.buildButton("Abbrechen", AppColors.BTN_BG, AppColors.BTN_HOVER);
		okBtn.setForeground(Color.WHITE);

		okBtn.addActionListener(e -> {
			PaperFormat.Format selectedFormat = formats[formatCombo.getSelectedIndex()];
			boolean landscape = orientCombo.getSelectedIndex() == 1;
			boolean withMargins = marginsCheckBox.isSelected();

			final double PX_PER_MM = 96.0 / 25.4;
			int wPx = (int) Math.round(
					(landscape ? selectedFormat.getWidthLandscape() : selectedFormat.getWidthPortrait()) * PX_PER_MM);
			int hPx = (int) Math.round(
					(landscape ? selectedFormat.getHeightLandscape() : selectedFormat.getHeightPortrait()) * PX_PER_MM);

			BufferedImage img = new BufferedImage(wPx, hPx, BufferedImage.TYPE_INT_ARGB);
			java.awt.Graphics2D g2 = img.createGraphics();
			g2.setColor(Color.WHITE);
			g2.fillRect(0, 0, wPx, hPx);

			if (withMargins) {
				int mTop = (int) Math.round(selectedFormat.getMarginTop() * PX_PER_MM);
				int mBottom = (int) Math.round(selectedFormat.getMarginBottom() * PX_PER_MM);
				int mLeft = (int) Math.round(selectedFormat.getMarginInner() * PX_PER_MM);
				int mRight = (int) Math.round(selectedFormat.getMarginOuter() * PX_PER_MM);
				g2.setColor(new Color(0, 120, 220, 180));
				float[] dash = { 6f, 4f };
				g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, dash, 0f));
				g2.drawRect(mLeft, mTop, wPx - mLeft - mRight, hPx - mTop - mBottom);
			}
			g2.dispose();

			CanvasInstance c = ed.ci();
			c.workingImage = img;
			c.originalImage = ed.deepCopy(img);
			c.activeElements = new ArrayList<>();
			c.selectedElements.clear();
			c.undoStack.clear();
			c.redoStack.clear();
			c.selectedAreas.clear();
			c.floatingImg = null;
			c.floatRect = null;

			ed.swapToImageView(ed.activeCanvasIndex);
			SwingUtilities.invokeLater(() -> ed.fitToViewport(ed.activeCanvasIndex));
			ed.updateTitle();
			ed.updateStatus();
			ed.setBottomButtonsEnabled(true);
			dialog.dispose();
		});

		cancelBtn.addActionListener(e -> dialog.dispose());
		row.add(okBtn);
		row.add(cancelBtn);
		content.add(row);
		dialog.add(content);
		dialog.setVisible(true);
	}

	/** Lets the user choose one or both checkerboard background colors. */
	void showCanvasBgDialog() {
		JPanel preview = new JPanel() {
			@Override
			protected void paintComponent(Graphics g) {
				int cell = 16;
				for (int r = 0; r < getHeight(); r += cell)
					for (int c = 0; c < getWidth(); c += cell) {
						boolean even = ((r / cell) + (c / cell)) % 2 == 0;
						g.setColor(even ? ed.canvasBg1 : ed.canvasBg2);
						g.fillRect(c, r, Math.min(cell, getWidth() - c), Math.min(cell, getHeight() - r));
					}
			}
		};
		preview.setPreferredSize(new Dimension(120, 60));

		JButton btn1 = UIComponentFactory.buildButton("Farbe 1", AppColors.BTN_BG, AppColors.BTN_HOVER);
		JButton btn2 = UIComponentFactory.buildButton("Farbe 2", AppColors.BTN_BG, AppColors.BTN_HOVER);
		JButton btnBoth = UIComponentFactory.buildButton("Beide", AppColors.BTN_BG, AppColors.BTN_HOVER);

		btn1.addActionListener(e -> {
			Color c = javax.swing.JColorChooser.showDialog(ed, "Hintergrundfarbe 1", ed.canvasBg1);
			if (c != null) {
				ed.canvasBg1 = c;
				preview.repaint();
				ed.ci().canvasPanel.repaint();
			}
		});
		btn2.addActionListener(e -> {
			Color c = javax.swing.JColorChooser.showDialog(ed, "Hintergrundfarbe 2", ed.canvasBg2);
			if (c != null) {
				ed.canvasBg2 = c;
				preview.repaint();
				ed.ci().canvasPanel.repaint();
			}
		});
		btnBoth.addActionListener(e -> {
			Color c = javax.swing.JColorChooser.showDialog(ed, "Hintergrundfarbe", ed.canvasBg1);
			if (c != null) {
				ed.canvasBg1 = c;
				ed.canvasBg2 = c;
				preview.repaint();
				ed.ci().canvasPanel.repaint();
			}
		});

		JDialog dialog = ed.createBaseDialog("Canvas-Hintergrund", 380, 240);
		JPanel content = ed.centeredColumnPanel(16, 20, 12);
		content.add(preview);
		content.add(Box.createVerticalStrut(12));
		JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
		row.setOpaque(false);
		row.add(btn1);
		row.add(btn2);
		row.add(btnBoth);
		content.add(row);
		content.add(Box.createVerticalStrut(12));
		JButton closeBtn = UIComponentFactory.buildButton("Schließen", AppColors.BTN_BG, AppColors.BTN_HOVER);
		closeBtn.setAlignmentX(javax.swing.JComponent.CENTER_ALIGNMENT);
		closeBtn.addActionListener(e -> dialog.dispose());
		content.add(closeBtn);
		dialog.add(content);
		dialog.setVisible(true);
	}

	void toggleQuickBG() {
		if (ed.canvasBg1Backup == null) {
			ed.canvasBg1Backup = ed.canvasBg1;
			ed.canvasBg1 = ed.canvasBg2;
		} else {
			ed.canvasBg1 = ed.canvasBg1Backup;
			ed.canvasBg1Backup = null;
		}
		if (ed.ci() != null && ed.ci().canvasPanel != null)
			ed.ci().canvasPanel.repaint();
		if (ed.secPanel != null)
			ed.secPanel.repaint();
	}
}
