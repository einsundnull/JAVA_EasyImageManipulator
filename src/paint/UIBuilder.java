package paint;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

/**
 * Builds the top bar, center area (canvas areas, ruler, galleries), and bottom
 * bar of SelectiveAlphaEditor.  All assignments go via ed.field = ... so the
 * SAE fields stay authoritative.
 */
class UIBuilder {

	private final SelectiveAlphaEditor ed;

	UIBuilder(SelectiveAlphaEditor ed) {
		this.ed = ed;
	}

	// ── Top bar ───────────────────────────────────────────────────────────────

	JPanel buildTopBar() {
		JPanel bar = new JPanel(new BorderLayout());
		bar.setBackground(AppColors.BG_PANEL);
		bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, AppColors.BORDER));

		// SI – Scenes I
		ed.scenesBtn = UIComponentFactory.buildModeToggleBtn("SI", "Szenen I ein-/ausblenden");
		ed.scenesBtn.setPreferredSize(new Dimension(ed.TOPBAR_BTN_W, ed.TOPBAR_BTN_H));
		ed.scenesBtn.setSelected(false);
		ed.scenesBtn.addActionListener(e -> ed.setScenesPanelVisible(0, ed.scenesBtn.isSelected()));

		// II – Images I (filmstrip)
		ed.filmstripBtn = UIComponentFactory.buildModeToggleBtn("II", "Bilder I ein-/ausblenden");
		ed.filmstripBtn.setPreferredSize(new Dimension(ed.TOPBAR_BTN_W, ed.TOPBAR_BTN_H));
		ed.filmstripBtn.setSelected(true);
		ed.filmstripBtn.addActionListener(e -> {
			ed.ci(0).tileGallery.setVisible(ed.filmstripBtn.isSelected());
			ed.updateLayoutVisibility();
			SwingUtilities.invokeLater(() -> SwingUtilities.invokeLater(() -> ed.reloadCurrentImage(0)));
		});

		// EI – Elements I
		ed.firstElementsBtn = UIComponentFactory.buildModeToggleBtn("EI", "Ebenen I ein-/ausblenden");
		ed.firstElementsBtn.setPreferredSize(new Dimension(ed.TOPBAR_BTN_W, ed.TOPBAR_BTN_H));
		ed.firstElementsBtn.setSelected(false);
		ed.firstElementsBtn.setEnabled(false);
		ed.firstElementsBtn.addActionListener(e -> {
			if (ed.elementLayerPanel != null) ed.elementLayerPanel.setVisible(ed.firstElementsBtn.isSelected());
			ed.updateLayoutVisibility();
			SwingUtilities.invokeLater(
					() -> SwingUtilities.invokeLater(() -> ed.reloadCurrentImage(ed.activeCanvasIndex)));
		});

		// FI – Open folder → Canvas I
		ed.quickOpenBtn = UIComponentFactory.buildButton("\uD83D\uDCC2 I", AppColors.BTN_BG, AppColors.BTN_HOVER);
		ed.quickOpenBtn.setToolTipText("Ordner öffnen / Recent Projekte (Canvas I)");
		ed.quickOpenBtn.setPreferredSize(new Dimension(ed.TOPBAR_BTN_W, ed.TOPBAR_BTN_H));
		ed.quickOpenBtn.setForeground(AppColors.TEXT);
		ed.quickOpenBtn.addActionListener(e -> ed.showQuickOpenDialog(0));

		// Canvas buttons
		ed.firstCanvasBtn = UIComponentFactory.buildModeToggleBtn("1", "1. Canvas ein-/ausblenden");
		ed.firstCanvasBtn.setPreferredSize(new Dimension(ed.TOPBAR_BTN_W, ed.TOPBAR_BTN_H));
		ed.firstCanvasBtn.setSelected(true);
		ed.firstCanvasBtn.setEnabled(false);
		ed.firstCanvasBtn.addActionListener(e -> ed.updateLayoutVisibility());

		ed.secondCanvasBtn = UIComponentFactory.buildModeToggleBtn("2", "2. Canvas ein-/ausblenden");
		ed.secondCanvasBtn.setPreferredSize(new Dimension(ed.TOPBAR_BTN_W, ed.TOPBAR_BTN_H));
		ed.secondCanvasBtn.setSelected(false);
		ed.secondCanvasBtn.setEnabled(false);
		ed.secondCanvasBtn.addActionListener(e -> ed.updateLayoutVisibility());

		// III – Images II
		ed.secondGalleryBtn = UIComponentFactory.buildModeToggleBtn("III", "Bilder II ein-/ausblenden");
		ed.secondGalleryBtn.setPreferredSize(new Dimension(ed.TOPBAR_BTN_W, ed.TOPBAR_BTN_H));
		ed.secondGalleryBtn.setSelected(false);
		ed.secondGalleryBtn.setEnabled(false);
		ed.secondGalleryBtn.addActionListener(e -> {
			if (ed.ci(1).tileGallery != null) ed.ci(1).tileGallery.setVisible(ed.secondGalleryBtn.isSelected());
			ed.updateLayoutVisibility();
			SwingUtilities.invokeLater(() -> SwingUtilities.invokeLater(() -> ed.reloadCurrentImage(1)));
		});

		// SII – Scenes II
		ed.secondScenesBtn = UIComponentFactory.buildModeToggleBtn("SII", "Szenen II ein-/ausblenden");
		ed.secondScenesBtn.setPreferredSize(new Dimension(ed.TOPBAR_BTN_W, ed.TOPBAR_BTN_H));
		ed.secondScenesBtn.setSelected(false);
		ed.secondScenesBtn.setEnabled(true);
		ed.secondScenesBtn.addActionListener(e -> ed.setScenesPanelVisible(1, ed.secondScenesBtn.isSelected()));

		// EII – Elements II
		ed.secondElementsBtn = UIComponentFactory.buildModeToggleBtn("EII", "Ebenen II ein-/ausblenden");
		ed.secondElementsBtn.setPreferredSize(new Dimension(ed.TOPBAR_BTN_W, ed.TOPBAR_BTN_H));
		ed.secondElementsBtn.setSelected(false);
		ed.secondElementsBtn.setEnabled(false);
		ed.secondElementsBtn.addActionListener(e -> {
			if (ed.elementLayerPanel2 != null) ed.elementLayerPanel2.setVisible(ed.secondElementsBtn.isSelected());
			ed.updateLayoutVisibility();
			SwingUtilities.invokeLater(
					() -> SwingUtilities.invokeLater(() -> ed.reloadCurrentImage(ed.activeCanvasIndex)));
		});

		// M – Maps
		ed.mapsBtn = UIComponentFactory.buildModeToggleBtn("M", "Maps ein-/ausblenden");
		ed.mapsBtn.setPreferredSize(new Dimension(ed.TOPBAR_BTN_W, ed.TOPBAR_BTN_H));
		ed.mapsBtn.setSelected(false);
		ed.mapsBtn.addActionListener(e -> {
			if (ed.mapsPanel != null) ed.mapsPanel.setVisible(ed.mapsBtn.isSelected());
			ed.updateLayoutVisibility();
			SwingUtilities
					.invokeLater(() -> SwingUtilities.invokeLater(() -> ed.reloadCurrentImage(ed.activeCanvasIndex)));
		});

		// Drop zone toggle
		ed.toggleDropZoneBtn = UIComponentFactory.buildModeToggleBtn("\u2193", "Drop-Feld");
		ed.toggleDropZoneBtn.setPreferredSize(new Dimension(ed.TOPBAR_BTN_W, ed.TOPBAR_BTN_H));
		ed.toggleDropZoneBtn.setSelected(false);
		ed.toggleDropZoneBtn.addActionListener(
				e -> { if (ed.rightDropZone != null) ed.rightDropZone.setVisible(ed.toggleDropZoneBtn.isSelected()); });

		// Apply / clear selections buttons
		ed.applyButton = UIComponentFactory.buildButton("\u2713", AppColors.ACCENT, AppColors.ACCENT_HOVER);
		ed.applyButton.setForeground(Color.WHITE);
		ed.applyButton.setToolTipText("Auswahl auf Alpha anwenden");
		ed.applyButton.addActionListener(e -> ed.saveController.applySelectionsToAlpha());
		ed.applyButton.setEnabled(false);
		ed.applyButton.setPreferredSize(new Dimension(ed.TOPBAR_BTN_W, ed.TOPBAR_BTN_H));

		ed.clearSelectionsButton = UIComponentFactory.buildButton("\u2715", AppColors.BTN_BG, AppColors.BTN_HOVER);
		ed.clearSelectionsButton.setToolTipText("Auswahl löschen");
		ed.clearSelectionsButton.addActionListener(e -> ed.saveController.clearSelections());
		ed.clearSelectionsButton.setEnabled(false);
		ed.clearSelectionsButton.setPreferredSize(new Dimension(ed.TOPBAR_BTN_W, ed.TOPBAR_BTN_H));

		ed.actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		ed.actionPanel.setOpaque(false);

		// RL – Reload
		JButton resetButton = UIComponentFactory.buildButton("RL", AppColors.BTN_BG, AppColors.BTN_HOVER);
		resetButton.setName("resetButton");
		resetButton.setToolTipText("Bild neu laden / zurücksetzen");
		resetButton.addActionListener(e -> ed.saveController.resetImage());
		resetButton.setEnabled(false);
		resetButton.setPreferredSize(new Dimension(ed.TOPBAR_BTN_W, ed.TOPBAR_BTN_H));

		// SV – Save
		JButton saveButton = UIComponentFactory.buildButton("SV", AppColors.SUCCESS, AppColors.SUCCESS_HOVER);
		saveButton.setName("saveButton");
		saveButton.setForeground(Color.WHITE);
		saveButton.setToolTipText("Bild speichern (STRG+S)");
		saveButton.addActionListener(e -> ed.saveController.saveImage());
		saveButton.setEnabled(false);
		saveButton.setPreferredSize(new Dimension(ed.TOPBAR_BTN_W, ed.TOPBAR_BTN_H));

		ed.actionPanel.add(resetButton);
		ed.actionPanel.add(saveButton);

		// CM – Canvas Mode
		ed.canvasModeBtn = UIComponentFactory.buildModeToggleBtn("CM",
				"Canvas-Modus: Layer-Verwaltung (STRG+A = Alle auswählen)");
		ed.canvasModeBtn.setPreferredSize(new Dimension(ed.TOPBAR_BTN_W, ed.TOPBAR_BTN_H));
		ed.canvasModeBtn.setEnabled(false);
		ed.canvasModeBtn.addActionListener(e -> ed.toggleCanvasMode());

		// PT – Paint toolbar
		ed.paintModeBtn = UIComponentFactory.buildModeToggleBtn("PT", "Paint-Leiste ein-/ausblenden");
		ed.paintModeBtn.setPreferredSize(new Dimension(ed.TOPBAR_BTN_W, ed.TOPBAR_BTN_H));
		ed.paintModeBtn.addActionListener(e -> ed.togglePaintMode());

		// BK – Book mode
		ed.bookModeBtn = UIComponentFactory.buildModeToggleBtn("BK", "Buch-Modus");
		ed.bookModeBtn.setPreferredSize(new Dimension(ed.TOPBAR_BTN_W, ed.TOPBAR_BTN_H));
		ed.bookModeBtn.addActionListener(e -> ed.toggleBookMode());

		// SC – Scene mode
		ed.sceneModeBtn = UIComponentFactory.buildModeToggleBtn("SC", "Szenen-Modus");
		ed.sceneModeBtn.setPreferredSize(new Dimension(ed.TOPBAR_BTN_W, ed.TOPBAR_BTN_H));
		ed.sceneModeBtn.addActionListener(e -> ed.toggleSceneMode());

		// Labels
		ed.modeLabel = new javax.swing.JLabel("");
		ed.modeLabel.setForeground(AppColors.TEXT_MUTED);
		ed.modeLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));

		ed.statusLabel = new javax.swing.JLabel("");
		ed.statusLabel.setForeground(AppColors.TEXT_MUTED);
		ed.statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));

		// Zoom controls
		JButton zoomInBtn   = UIComponentFactory.buildButton("+",      AppColors.BTN_BG, AppColors.BTN_HOVER);
		JButton zoomOutBtn  = UIComponentFactory.buildButton("\u2212", AppColors.BTN_BG, AppColors.BTN_HOVER);
		JButton zoomFitBtn  = UIComponentFactory.buildButton("Fit",    AppColors.BTN_BG, AppColors.BTN_HOVER);
		JButton zoomResetBtn = UIComponentFactory.buildButton("1:1",   AppColors.BTN_BG, AppColors.BTN_HOVER);

		ed.zoomLabel = new javax.swing.JLabel("100%");
		ed.zoomLabel.setForeground(AppColors.TEXT_MUTED);
		ed.zoomLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
		ed.zoomLabel.setPreferredSize(new Dimension(46, 20));
		ed.zoomLabel.setHorizontalAlignment(javax.swing.JLabel.CENTER);
		ed.zoomLabel.setToolTipText("Doppelklick: Zoom eingeben");
		ed.zoomLabel.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.TEXT_CURSOR));
		ed.zoomLabel.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2) ed.editorDialogs.showZoomInput();
			}
		});

		zoomInBtn.setPreferredSize(new Dimension(ed.TOPBAR_ZOOM_BTN_W, ed.TOPBAR_ZOOM_BTN_H));
		zoomOutBtn.setPreferredSize(new Dimension(ed.TOPBAR_ZOOM_BTN_W, ed.TOPBAR_ZOOM_BTN_H));
		zoomFitBtn.setPreferredSize(new Dimension(ed.TOPBAR_ZOOM_BTN_W, ed.TOPBAR_ZOOM_BTN_H));
		zoomResetBtn.setPreferredSize(new Dimension(ed.TOPBAR_ZOOM_BTN_W, ed.TOPBAR_ZOOM_BTN_H));
		zoomInBtn.addActionListener(e  -> ed.setZoom(ed.ci().zoom + ed.ZOOM_STEP, null));
		zoomOutBtn.addActionListener(e -> ed.setZoom(ed.ci().zoom - ed.ZOOM_STEP, null));
		zoomResetBtn.addActionListener(e -> ed.setZoom(1.0, null));
		zoomFitBtn.addActionListener(e  -> ed.fitToViewport());

		// BG – Background color
		JButton bgColorBtn = UIComponentFactory.buildButton("BG", AppColors.BTN_BG, AppColors.BTN_HOVER);
		bgColorBtn.setToolTipText("Canvas-Hintergrundfarbe");
		bgColorBtn.addActionListener(e -> ed.newFileController.showCanvasBgDialog());
		bgColorBtn.setPreferredSize(new Dimension(ed.TOPBAR_BTN_W, ed.TOPBAR_BTN_H));

		// Qu – Quick BG toggle
		JButton quickBgBtn = UIComponentFactory.buildButton("Qu", AppColors.BTN_BG, AppColors.BTN_HOVER);
		quickBgBtn.setToolTipText("BG Color temporär aus-/einblenden");
		quickBgBtn.addActionListener(e -> ed.newFileController.toggleQuickBG());
		quickBgBtn.setPreferredSize(new Dimension(ed.TOPBAR_BTN_W, ed.TOPBAR_BTN_H));

		// FII – Open folder → Canvas II
		JButton openFolderII = UIComponentFactory.buildButton("\uD83D\uDCC2 II", AppColors.BTN_BG, AppColors.BTN_HOVER);
		openFolderII.setToolTipText("Ordner öffnen (Canvas II)");
		openFolderII.setForeground(AppColors.TEXT);
		openFolderII.setPreferredSize(new Dimension(ed.TOPBAR_BTN_W, ed.TOPBAR_BTN_H));
		openFolderII.addActionListener(e -> ed.showQuickOpenDialog(1));

		// Assemble bar
		JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 5));
		left.setOpaque(false);
		left.add(ed.scenesBtn);
		left.add(ed.filmstripBtn);
		left.add(ed.firstElementsBtn);
		left.add(ed.quickOpenBtn);
		left.add(Box.createHorizontalStrut(12));
		bar.add(left, BorderLayout.WEST);

		JPanel center = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 5));
		center.setOpaque(false);
		center.add(zoomInBtn);
		center.add(ed.zoomLabel);
		center.add(zoomOutBtn);
		center.add(zoomFitBtn);
		center.add(Box.createHorizontalStrut(6));
		center.add(bgColorBtn);
		center.add(quickBgBtn);
		center.add(Box.createHorizontalStrut(6));
		center.add(resetButton);
		center.add(saveButton);
		center.add(Box.createHorizontalStrut(6));
		center.add(ed.canvasModeBtn);
		center.add(ed.paintModeBtn);
		center.add(ed.bookModeBtn);
		bar.add(center, BorderLayout.CENTER);

		JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 5));
		right.setOpaque(false);
		right.add(Box.createHorizontalStrut(12));
		right.add(openFolderII);
		right.add(ed.secondElementsBtn);
		right.add(ed.secondGalleryBtn);
		right.add(ed.secondScenesBtn);
		bar.add(right, BorderLayout.EAST);

		return bar;
	}

	// ── Center: ruler + galleries + canvases ──────────────────────────────────

	JPanel buildCenter() {
		buildCanvasArea(0);
		buildCanvasArea(1);

		ed.updateCanvasFocusBorder();

		ed.hRuler = new HRulerPanel(ed);
		ed.vRuler = new VRulerPanel(ed);
		ed.rulerCorner = new JPanel();
		ed.rulerCorner.setBackground(new Color(50, 50, 50));
		ed.rulerCorner.setPreferredSize(new Dimension(ed.RULER_THICK, ed.RULER_THICK));
		ed.rulerCorner.setOpaque(true);

		ed.rulerNorthBar = new JPanel(new BorderLayout());
		ed.rulerNorthBar.setOpaque(false);
		ed.rulerNorthBar.add(ed.rulerCorner, BorderLayout.WEST);
		ed.rulerNorthBar.add(ed.hRuler, BorderLayout.CENTER);

		ed.elementLayerPanel  = new ElementLayerPanel(ed.buildElementLayerCallbacks(0));
		ed.elementLayerPanel.setVisible(false);
		ed.elementLayerPanel2 = new ElementLayerPanel(ed.buildElementLayerCallbacks(1));
		ed.elementLayerPanel2.setVisible(false);

		ed.mapsPanel = new MapsPanel(new MapsPanel.Callbacks() {
			@Override public void onMapSelected(TranslationMap map) {}
			@Override public void onMapDeleted(String language, String mapId) {}
			@Override public void onMapEdited(TranslationMap oldMap, TranslationMap newMap) {}
		});
		ed.mapsPanel.setVisible(false);
		ed.mapsPanel.setPreferredSize(new Dimension(250, 400));
		ed.mapsPanel.setMaximumSize(new Dimension(250, Integer.MAX_VALUE));

		ed.ci(0).layeredPane.setMinimumSize(new Dimension(0, 0));
		ed.ci(0).layeredPane.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
		ed.ci(0).layeredPane.setAlignmentY(Component.CENTER_ALIGNMENT);

		ed.ci(1).layeredPane.setMinimumSize(new Dimension(0, 0));
		ed.ci(1).layeredPane.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
		ed.ci(1).layeredPane.setVisible(false);
		ed.ci(1).layeredPane.setAlignmentY(Component.CENTER_ALIGNMENT);

		ed.ci(0).scenesPanel.setVisible(false);
		if (SelectiveAlphaEditor.SHRINK_GALLERY)
			ed.ci(0).scenesPanel.setMaximumSize(new Dimension(TileGalleryPanel.GALLERY_W, Integer.MAX_VALUE));
		ed.ci(0).scenesPanel.setAlignmentY(Component.CENTER_ALIGNMENT);

		ed.ci(0).tileGallery.setVisible(true);
		if (SelectiveAlphaEditor.SHRINK_GALLERY)
			ed.ci(0).tileGallery.setMaximumSize(new Dimension(TileGalleryPanel.GALLERY_W, Integer.MAX_VALUE));
		ed.ci(0).tileGallery.setAlignmentY(Component.CENTER_ALIGNMENT);

		if (SelectiveAlphaEditor.SHRINK_GALLERY)
			ed.elementLayerPanel.setMaximumSize(new Dimension(ElementLayerPanel.PANEL_W, Integer.MAX_VALUE));
		ed.elementLayerPanel.setAlignmentY(Component.CENTER_ALIGNMENT);

		if (SelectiveAlphaEditor.SHRINK_GALLERY)
			ed.elementLayerPanel2.setMaximumSize(new Dimension(ElementLayerPanel.PANEL_W, Integer.MAX_VALUE));
		ed.elementLayerPanel2.setAlignmentY(Component.CENTER_ALIGNMENT);

		ed.ci(1).scenesPanel.setVisible(false);
		if (SelectiveAlphaEditor.SHRINK_GALLERY)
			ed.ci(1).scenesPanel.setMaximumSize(new Dimension(TileGalleryPanel.GALLERY_W, Integer.MAX_VALUE));
		ed.ci(1).scenesPanel.setAlignmentY(Component.CENTER_ALIGNMENT);

		ed.ci(1).tileGallery.setVisible(false);
		if (SelectiveAlphaEditor.SHRINK_GALLERY)
			ed.ci(1).tileGallery.setMaximumSize(new Dimension(TileGalleryPanel.GALLERY_W, Integer.MAX_VALUE));
		ed.ci(1).tileGallery.setAlignmentY(Component.CENTER_ALIGNMENT);

		ed.mainDividerPanel = new JPanel();
		ed.mainDividerPanel.setBackground(AppColors.BORDER);
		ed.mainDividerPanel.setPreferredSize(new Dimension(2, 0));
		ed.mainDividerPanel.setMaximumSize(new Dimension(2, Integer.MAX_VALUE));
		ed.mainDividerPanel.setMinimumSize(new Dimension(2, 0));
		ed.mainDividerPanel.setAlignmentY(Component.CENTER_ALIGNMENT);
		ed.mainDividerPanel.setVisible(false);

		ed.rightDropZone = ed.dropController.buildRightDropZone();
		ed.ci(0).layeredPane.add(ed.rightDropZone, JLayeredPane.PALETTE_LAYER);
		ed.rightDropZone.setVisible(false);

		ed.galleryWrapper = new JPanel();
		ed.galleryWrapper.setLayout(new BoxLayout(ed.galleryWrapper, BoxLayout.X_AXIS));
		ed.galleryWrapper.setBackground(AppColors.BG_DARK);

		ed.galleryWrapper.add(ed.ci(0).scenesPanel);
		ed.galleryWrapper.add(ed.ci(0).tileGallery);
		ed.galleryWrapper.add(ed.elementLayerPanel);
		ed.galleryWrapper.add(ed.ci(0).layeredPane);
		ed.galleryWrapper.add(ed.mainDividerPanel);
		ed.galleryWrapper.add(ed.ci(1).layeredPane);
		ed.galleryWrapper.add(ed.elementLayerPanel2);
		ed.galleryWrapper.add(ed.ci(1).tileGallery);
		ed.galleryWrapper.add(ed.ci(1).scenesPanel);
		ed.galleryWrapper.add(ed.mapsPanel);

		ed.updateLayoutVisibility();

		ed.galleryWrapper.addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				CanvasInstance activeCanvas = ed.ci();
				if (activeCanvas.workingImage != null && activeCanvas.viewportPanel.isVisible()
						&& !activeCanvas.userHasManuallyZoomed) {
					SwingUtilities.invokeLater(() -> ed.fitToViewport(ed.activeCanvasIndex));
				}
			}
		});

		return ed.galleryWrapper;
	}

	// ── Canvas area ───────────────────────────────────────────────────────────

	void buildCanvasArea(int idx) {
		CanvasInstance c = ed.canvases[idx];

		c.canvasPanel = new CanvasPanel(ed.buildCanvasCallbacks(idx));

		c.canvasWrapper = new JPanel(null) {
			@Override
			public Dimension getPreferredSize() {
				if (c.workingImage == null)
					return new Dimension(1, 1);
				int cw = (int) Math.ceil(c.workingImage.getWidth() * c.zoom);
				int ch = (int) Math.ceil(c.workingImage.getHeight() * c.zoom);
				Dimension vd = c.scrollPane != null ? c.scrollPane.getViewport().getSize()
						: new Dimension(cw, ch);
				return new Dimension(Math.max(cw, vd.width), Math.max(ch, vd.height));
			}

			@Override
			public void doLayout() {
				if (c.canvasPanel == null)
					return;
				Dimension cs = c.canvasPanel.getPreferredSize();
				Dimension ws = getSize();
				int x = Math.max(0, (ws.width - cs.width) / 2);
				int y = Math.max(0, (ws.height - cs.height) / 2);
				c.canvasPanel.setBounds(x, y, cs.width, cs.height);
			}
		};
		c.canvasWrapper.setBackground(AppColors.BG_DARK);
		c.canvasWrapper.setOpaque(true);
		c.canvasWrapper.add(c.canvasPanel);

		c.scrollPane = new JScrollPane(c.canvasWrapper);
		c.scrollPane.setBorder(null);
		if (idx == 0) {
			c.scrollPane.getVerticalScrollBar().setUnitIncrement(16);
			c.scrollPane.getHorizontalScrollBar().setUnitIncrement(16);
		}
		TileGalleryPanel.applyDarkScrollBar(c.scrollPane.getVerticalScrollBar());
		TileGalleryPanel.applyDarkScrollBar(c.scrollPane.getHorizontalScrollBar());
		c.scrollPane.getViewport().setBackground(AppColors.BG_DARK);
		c.scrollPane.setBackground(AppColors.BG_DARK);
		c.scrollPane.getViewport().addChangeListener(e -> {
			if (ed.showRuler && idx == 0) {
				ed.hRuler.repaint();
				ed.vRuler.repaint();
			}
			c.canvasWrapper.revalidate();
		});

		c.viewportPanel = new JPanel(new BorderLayout());
		c.viewportPanel.setBackground(AppColors.BG_DARK);
		c.viewportPanel.setVisible(false);
		c.viewportPanel.add(c.scrollPane, BorderLayout.CENTER);

		JPanel scrollSpacer = new JPanel();
		scrollSpacer.setOpaque(true);
		scrollSpacer.setBackground(AppColors.BG_DARK);
		scrollSpacer.setPreferredSize(new Dimension(0, 16));
		c.viewportPanel.add(scrollSpacer, BorderLayout.SOUTH);

		c.viewportPanel.addComponentListener(new ComponentAdapter() {
			@Override
			public void componentShown(ComponentEvent e) {
				SwingUtilities.invokeLater(() -> {
					Dimension vd = c.scrollPane.getViewport().getSize();
					if (vd.width > 0 && vd.height > 0 && c.workingImage != null && !c.userHasManuallyZoomed) {
						ed.fitToViewport(idx);
					}
				});
			}

			@Override
			public void componentResized(ComponentEvent e) {
				if (c.workingImage != null && !c.userHasManuallyZoomed && c.viewportPanel.isVisible()) {
					SwingUtilities.invokeLater(() -> ed.fitToViewport(idx));
				}
			}
		});

		c.layeredPane = new JLayeredPane();
		c.layeredPane.setBackground(AppColors.BG_DARK);
		c.layeredPane.setOpaque(true);
		c.layeredPane.setPreferredSize(new Dimension(860, 560));

		c.dropHintPanel = buildDropHintPanel();
		c.dropHintPanel.setBounds(0, 0, 860, 560);
		c.layeredPane.add(c.dropHintPanel, JLayeredPane.DEFAULT_LAYER);

		c.prevNavButton = UIComponentFactory.buildNavButton("‹");
		c.nextNavButton = UIComponentFactory.buildNavButton("›");
		c.prevNavButton.setEnabled(false);
		c.nextNavButton.setEnabled(false);
		c.prevNavButton.addActionListener(e -> ed.navigateImage(-1, idx));
		c.nextNavButton.addActionListener(e -> ed.navigateImage(+1, idx));
		c.layeredPane.add(c.prevNavButton, JLayeredPane.PALETTE_LAYER);
		c.layeredPane.add(c.nextNavButton, JLayeredPane.PALETTE_LAYER);

		c.elementEditBar = ed.elementEditController.buildElementEditBar(idx);
		c.elementEditBar.setVisible(false);
		c.layeredPane.add(c.elementEditBar, JLayeredPane.MODAL_LAYER);

		c.layeredPane.addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				int w = c.layeredPane.getWidth(), h = c.layeredPane.getHeight();
				c.dropHintPanel.setBounds(0, 0, w, h);
				if (c.viewportPanel.getParent() == c.layeredPane)
					c.viewportPanel.setBounds(0, 0, w, h);
				ed.repositionNavButtons(idx);
				ed.elementEditController.repositionElementEditBar(idx);
				if (idx == 0)
					ed.repositionRightDropZone();
				if (c.workingImage != null && !c.userHasManuallyZoomed && c.viewportPanel.isVisible()) {
					SwingUtilities.invokeLater(() -> ed.fitToViewport(idx));
				}
			}
		});

		c.canvasPanel.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				if (ed.activeCanvasIndex != idx)
					ed.elementEditController.resetElementDragState(ed.activeCanvasIndex);
				ed.activeCanvasIndex = idx;
				ed.updateCanvasFocusBorder();
			}
		});

		c.tileGallery = new TileGalleryPanel(ed.buildGalleryCallbacks(idx), ed.buildGalleryPreloadCallback(idx));
		c.tileGallery.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				if (ed.activeCanvasIndex != idx)
					ed.elementEditController.resetElementDragState(ed.activeCanvasIndex);
				ed.activeCanvasIndex = idx;
				ed.updateCanvasFocusBorder();
			}
		});

		// Load last image directory
		try {
			List<String> recentImages = LastProjectsManager.load(LastProjectsManager.CAT_IMAGES);
			if (!recentImages.isEmpty()) {
				File lastDir = new File(recentImages.get(0));
				if (lastDir.exists() && lastDir.isDirectory()) {
					File[] files = lastDir.listFiles(f -> f.isFile() && SelectiveAlphaEditor.isSupportedFile(f));
					if (files != null && files.length > 0) {
						java.util.Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
						c.directoryImages = new java.util.ArrayList<>(java.util.Arrays.asList(files));
						c.lastIndexedDir = lastDir;
						c.tileGallery.setFiles(c.directoryImages, files[0]);
					}
				}
			}
		} catch (IOException ex) {
			System.out.println("[INFO] Keine letzten Bilder gefunden: " + ex.getMessage());
		}

		c.scenesPanel = new TileGalleryPanel(ed.buildScenesCallbacks(idx), null, "Szenen",
				() -> ed.setScenesPanelVisible(idx, false),
				() -> ed.refreshSceneFiles(idx));
		c.scenesPanel.setFileDropOverride(files -> ed.createSceneFromDrop(files, idx));
		c.scenesPanel.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				if (ed.activeCanvasIndex != idx)
					ed.elementEditController.resetElementDragState(ed.activeCanvasIndex);
				ed.activeCanvasIndex = idx;
				ed.updateCanvasFocusBorder();
			}
		});

		ed.setupDropTarget(c.dropHintPanel, idx);
	}

	// ── Bottom bar ────────────────────────────────────────────────────────────

	JPanel buildBottomBar() {
		ed.paintToolbar = new PaintToolbar(ed, ed.buildPaintCallbacks());
		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setBackground(AppColors.BG_DARK);
		wrapper.add(ed.paintToolbar, BorderLayout.NORTH);
		return wrapper;
	}

	// ── Drop hint panel ───────────────────────────────────────────────────────

	JPanel buildDropHintPanel() {
		return new JPanel() {
			@Override
			protected void paintComponent(Graphics g) {
				super.paintComponent(g);
				Graphics2D g2 = (Graphics2D) g;
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				int w = getWidth(), h = getHeight();
				float[] dash = { 10f, 6f };
				g2.setColor(AppColors.BORDER);
				g2.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, dash, 0));
				g2.drawRoundRect(20, 20, w - 41, h - 41, 20, 20);
				g2.setStroke(new BasicStroke(3));
				int is = 64, ix = w / 2 - is / 2, iy = h / 2 - 70;
				g2.setColor(AppColors.ACCENT);
				g2.drawRoundRect(ix, iy, is, is, 10, 10);
				int ax = w / 2;
				g2.drawLine(ax, iy + 10, ax, iy + is - 10);
				g2.drawLine(ax - 12, iy + is - 24, ax, iy + is - 10);
				g2.drawLine(ax + 12, iy + is - 24, ax, iy + is - 10);
				g2.setStroke(new BasicStroke(1));
				g2.setColor(AppColors.TEXT);
				g2.setFont(new Font("SansSerif", Font.BOLD, 18));
				String t = "Bilddatei hier ablegen";
				FontMetrics fm = g2.getFontMetrics();
				g2.drawString(t, w / 2 - fm.stringWidth(t) / 2, iy + is + 36);
				g2.setColor(AppColors.TEXT_MUTED);
				g2.setFont(new Font("SansSerif", Font.PLAIN, 13));
				String s = "PNG · JPG · BMP · GIF   |   STRG+Rad = Zoom · Mittelmaus = Pan";
				fm = g2.getFontMetrics();
				g2.drawString(s, w / 2 - fm.stringWidth(s) / 2, iy + is + 60);
			}

			{
				setBackground(AppColors.BG_DARK);
			}
		};
	}
}
