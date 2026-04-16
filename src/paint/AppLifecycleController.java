package paint;

import java.awt.Color;
import java.io.IOException;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/**
 * Handles application startup (settings load) and shutdown (settings save).
 * Extracted from SelectiveAlphaEditor.initializeUI / onApplicationClosing.
 */
class AppLifecycleController {

	private final SelectiveAlphaEditor ed;

	AppLifecycleController(SelectiveAlphaEditor ed) {
		this.ed = ed;
	}

	// ── Startup ───────────────────────────────────────────────────────────────

	/**
	 * Wires up window-close/state listeners, secondary-window init, and startup
	 * dialog. Called at the end of initializeUI().
	 */
	void setupWindowBehavior() {
		ed.addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosing(java.awt.event.WindowEvent e) {
				saveOnClose();
			}
		});

		ed.addWindowStateListener((java.awt.event.WindowEvent e) -> {
			boolean wasMax = (e.getOldState() & JFrame.MAXIMIZED_BOTH) == JFrame.MAXIMIZED_BOTH;
			boolean isMax  = (e.getNewState() & JFrame.MAXIMIZED_BOTH) == JFrame.MAXIMIZED_BOTH;
			if (wasMax != isMax && ed.ci(0).workingImage != null) {
				SwingUtilities.invokeLater(() -> {
					if (!ed.ci(0).userHasManuallyZoomed) {
						ed.fitToViewport(0);
					} else {
						ed.centerCanvasX(0);
					}
					if (ed.ci(0).canvasPanel != null)
						ed.ci(0).canvasPanel.repaint();
				});
			}
		});

		ed.secWinController.initSecondaryWindow();

		SwingUtilities.invokeLater(() -> {
			try {
				java.util.Map<String, java.util.List<String>> recent = LastProjectsManager.loadAll();
				StartupDialog dlg = new StartupDialog(ed, recent);
				dlg.setVisible(true);
				java.io.File chosen = dlg.getSelectedPath();
				if (chosen != null && chosen.isDirectory()) {
					java.io.File[] images = chosen.listFiles(f -> f.isFile() && SelectiveAlphaEditor.isSupportedFile(f));
					if (images != null && images.length > 0) {
						java.util.Arrays.sort(images, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
						ed.indexDirectory(images[0], ed.activeCanvasIndex);
						ed.filmstripBtn.setSelected(true);
						ed.ci(ed.activeCanvasIndex).tileGallery.setVisible(true);
						ed.updateLayoutVisibility();
					}
				}
			} catch (IOException e) {
				System.err.println("[WARN] Konnte lastProjects nicht laden: " + e.getMessage());
			}
		});
	}

	/** Loads persisted settings and applies them to the editor after UI is built. */
	void loadSettings() {
		try {
			AppSettings.load();
			AppSettings settings = AppSettings.getInstance();

			// Fensterposition
			ed.setLocation(settings.getWindowX(), settings.getWindowY());
			ed.setSize(settings.getWindowWidth(), settings.getWindowHeight());
			if (settings.isWindowMaximized()) {
				ed.setExtendedState(JFrame.MAXIMIZED_BOTH);
			}

			// Canvas-Farben
			ed.canvasBg1 = new Color(settings.getBg1());
			ed.canvasBg2 = new Color(settings.getBg2());

			// View-Optionen
			ed.ci(0).showGrid = settings.isShowGrid();
			ed.ci(1).showGrid = settings.isShowGrid();
			ed.showRuler = settings.isShowRuler();
			ed.rulerUnit = RulerUnit.valueOf(settings.getRulerUnit());

			// Zoom-Einstellungen
			ed.ZOOM_MIN    = settings.getZoomMin();
			ed.ZOOM_MAX    = settings.getZoomMax();
			ed.ZOOM_STEP   = settings.getZoomStep();
			ed.ZOOM_FACTOR = settings.getZoomFactor();

			// App-Modus
			try {
				ed.defaultAppMode = AppMode.valueOf(settings.getAppMode());
			} catch (IllegalArgumentException e) {
				ed.defaultAppMode = AppMode.ALPHA_EDITOR;
			}

			// PaintToolbar-Einstellungen
			if (ed.paintToolbar != null) {
				ed.paintToolbar.setPrimaryColor(new Color(settings.getPrimaryColor(), true));
				ed.paintToolbar.setSecondaryColor(new Color(settings.getSecondaryColor(), true));
				ed.paintToolbar.setStrokeWidth(settings.getStrokeWidth());
				ed.paintToolbar.setAntialiasing(settings.isAntialias());
				try {
					ed.paintToolbar.setFillMode(settings.getFillMode());
					ed.paintToolbar.setBrushShape(settings.getBrushShape());
					ed.paintToolbar.setActiveTool(settings.getActiveTool());
				} catch (Exception e) {
					System.err.println("[WARN] Fehler beim Restore von Paint-Einstellungen: " + e.getMessage());
				}
			}

			// Text-Tool-Einstellungen
			if (ed.ci(0).canvasPanel != null) {
				ed.ci(0).canvasPanel.setTextFontName(settings.getFontName());
				ed.ci(0).canvasPanel.setTextFontSize(settings.getFontSize());
				ed.ci(0).canvasPanel.setTextBold(settings.isTextBold());
				ed.ci(0).canvasPanel.setTextItalic(settings.isTextItalic());
				ed.ci(0).canvasPanel.setTextColor(new Color(settings.getFontColor(), true));
			}

		} catch (IOException e) {
			System.err.println("[WARN] Fehler beim Laden der Einstellungen: " + e.getMessage());
			ed.setLocationRelativeTo(null); // Fallback
		}
	}

	// ── Shutdown ──────────────────────────────────────────────────────────────

	/** Persists all settings and shuts down the JVM. */
	void saveOnClose() {
		try {
			// Speichere aktuelle Szene
			if (ed.ci(0).sourceFile != null && ed.ci(0).workingImage != null) {
				ed.projectManager.saveScene(ed.ci(0).sourceFile, ed.ci(0).activeElements, ed.ci(0).zoom,
						ed.ci(0).appMode, ed.ci(0).workingImage.getWidth(), ed.ci(0).workingImage.getHeight());
			}

			// Speichere globale Einstellungen
			AppSettings settings = AppSettings.getInstance();
			settings.setBg1(ed.canvasBg1.getRGB());
			settings.setBg2(ed.canvasBg2.getRGB());
			settings.setShowGrid(ed.ci().showGrid);
			settings.setShowRuler(ed.showRuler);
			settings.setRulerUnit(ed.rulerUnit.toString());
			settings.setAppMode(ed.ci().appMode.toString());
			settings.setZoomMin(ed.ZOOM_MIN);
			settings.setZoomMax(ed.ZOOM_MAX);
			settings.setZoomStep(ed.ZOOM_STEP);
			settings.setZoomFactor(ed.ZOOM_FACTOR);

			// Fensterposition
			settings.setWindowX(ed.getX());
			settings.setWindowY(ed.getY());
			settings.setWindowWidth(ed.getWidth());
			settings.setWindowHeight(ed.getHeight());
			settings.setWindowMaximized((ed.getExtendedState() & JFrame.MAXIMIZED_BOTH) == JFrame.MAXIMIZED_BOTH);

			// PaintToolbar
			if (ed.paintToolbar != null) {
				settings.setPrimaryColor(ed.paintToolbar.getPrimaryColor().getRGB());
				settings.setSecondaryColor(ed.paintToolbar.getSecondaryColor().getRGB());
				settings.setStrokeWidth(ed.paintToolbar.getStrokeWidth());
				settings.setAntialias(ed.paintToolbar.isAntialiasing());
				if (ed.paintToolbar.getActiveTool() != null)
					settings.setActiveTool(ed.paintToolbar.getActiveTool().toString());
				settings.setFillMode(ed.paintToolbar.getFillMode().toString());
				settings.setBrushShape(ed.paintToolbar.getBrushShape().toString());
			}

			// Text-Tool
			if (ed.ci(0).canvasPanel != null) {
				settings.setFontName(ed.ci(0).canvasPanel.getTextFontName());
				settings.setFontSize(ed.ci(0).canvasPanel.getTextFontSize());
				settings.setTextBold(ed.ci(0).canvasPanel.isTextBold());
				settings.setTextItalic(ed.ci(0).canvasPanel.isTextItalic());
				settings.setFontColor(ed.ci(0).canvasPanel.getTextColor().getRGB());
			}

			settings.save();
		} catch (IOException e) {
			System.err.println("[ERROR] Fehler beim Speichern der Einstellungen: " + e.getMessage());
		}

		// Stop secondary window timer
		if (ed.secTimer != null && ed.secTimer.isRunning()) {
			ed.secTimer.stop();
		}
		if (ed.secWin != null) {
			ed.secWin.dispose();
		}

		System.exit(0);
	}
}
