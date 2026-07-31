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
				requestExit();
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
			if (ed.paintToolbar != null) ed.paintToolbar.setRulerSelected(ed.showRuler);
			if (ed.showRuler) SwingUtilities.invokeLater(() -> ed.buildRulerLayout());

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
				ed.paintToolbar.setWandTolerance(settings.getWandTolerance());
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

	// ── Beenden ───────────────────────────────────────────────────────────────

	/**
	 * Fenster-X: <b>erst fragen, dann beenden.</b>
	 * <p>
	 * Vorher rief {@code windowClosing} direkt {@link #saveOnClose()} — und das
	 * sichert nur Szene-Metadaten und Einstellungen, <b>nie</b> das
	 * {@code workingImage}. Zusammen mit {@code EXIT_ON_CLOSE} beendete ein
	 * Klick auf das X die JVM sofort und verwarf jede ungespeicherte
	 * Bildänderung ohne Rückfrage (Audit-Befund <b>F01</b>,
	 * {@code doc/Audit_Schwachstellen_2026-07-31.md}).
	 * <p>
	 * Der Dirty-Zustand wurde dabei bereits geführt ({@code dirtyFiles},
	 * {@code hasUnsavedChanges}, Titelmarker) — er wurde nur nie abgefragt.
	 * Auch der Dialog existierte schon
	 * ({@link EditorDialogs#showUnsavedChangesDialog()}), war aber im gesamten
	 * Quellbaum ohne einen einzigen Aufrufer.
	 */
	void requestExit() {
		java.util.List<String> unsaved = collectUnsavedNames();
		if (unsaved.isEmpty()) {
			finishExit();
			return;
		}

		java.util.List<String> shown = unsaved.size() <= 5 ? unsaved : unsaved.subList(0, 5);
		String list = String.join("<br>", shown);
		if (unsaved.size() > shown.size())
			list += "<br>… und " + (unsaved.size() - shown.size()) + " weitere";

		int answer = ed.showUnsavedChangesDialog(
				"Ungespeicherte Änderungen an " + unsaved.size()
						+ (unsaved.size() == 1 ? " Datei" : " Dateien") + ":<br><br><b>"
						+ list + "</b><br><br>Was möchtest du tun?");

		switch (answer) {
		case 1 -> finishExit();                 // Verwerfen
		case 0 -> {                              // Speichern
			// Bewusst dieselbe Wirkung wie Strg+S — der Benutzer soll beim
			// Beenden nicht überraschend ein anderes Ziel beschrieben bekommen.
			ed.saveController.saveImageSilent();
			java.util.List<String> rest = collectUnsavedNames();
			if (rest.isEmpty()) {
				finishExit();
			} else {
				// NICHT beenden: saveImageSilent speichert nur den AKTIVEN
				// Canvas — und bei einem neu angelegten Bild ohne sourceFile
				// gar nichts. Jetzt zu schließen hieße, genau den Verlust zu
				// verursachen, den dieser Dialog verhindern soll.
				ed.showInfoDialog("Noch nicht alles gespeichert",
						"Es sind weiterhin ungespeicherte Änderungen vorhanden:\n"
								+ String.join("\n", rest)
								+ "\n\nEin neu angelegtes Bild braucht \"Speichern unter\".\n"
								+ "Das Programm bleibt geöffnet.");
			}
		}
		default -> { /* 2 = Abbrechen, auch beim Schließen des Dialogs: offen lassen */ }
		}
	}

	/**
	 * Namen aller Dateien mit ungespeicherten Änderungen — Grundlage der
	 * Rückfrage in {@link #requestExit()}.
	 * <p>
	 * Zwei Quellen, weil keine allein vollständig ist: {@code dirtyFiles}
	 * kennt auch Dateien, die gerade in <b>keinem</b> Canvas offen sind (man
	 * bearbeitet A, blättert zu B — A bleibt schmutzig), trägt aber laut
	 * {@code LayoutController.markDirty} nur ein, wenn {@code sourceFile != null}.
	 * Ein <b>neu angelegtes, nie gespeichertes</b> Bild steht deshalb nur über
	 * {@code hasUnsavedChanges} am Canvas.
	 */
	private java.util.List<String> collectUnsavedNames() {
		java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
		for (java.io.File f : ed.dirtyFiles)
			if (f != null)
				names.add(f.getName());
		for (int i = 0; i < ed.canvases.length; i++) {
			CanvasInstance c = ed.ci(i);
			if (c != null && c.hasUnsavedChanges && c.workingImage != null && c.sourceFile == null)
				names.add("(neues Bild, Canvas " + (i == 0 ? "I" : "II") + ")");
		}
		return new java.util.ArrayList<>(names);
	}

	/**
	 * Einstellungen/Szene sichern und die Anwendung wirklich beenden.
	 * <p>
	 * <b>Das {@code System.exit(0)} steht bewusst hier und nicht mehr am Ende
	 * von {@link #saveOnClose()}</b>: eine Methode, die „speichern" heißt und
	 * unangekündigt die JVM beendet, ist genau die Sorte versteckter
	 * Nebenwirkung, die F01 so lange unentdeckt gelassen hat. {@code saveOnClose}
	 * speichert jetzt nur noch.
	 * <p>
	 * Explizit beendet wird, weil das Hauptfenster auf
	 * {@code DO_NOTHING_ON_CLOSE} steht und Zweitfenster bzw. schwebende
	 * Malleiste die JVM sonst am Leben halten können.
	 */
	private void finishExit() {
		saveOnClose();
		ed.dispose();
		System.exit(0);
	}

	/**
	 * Sichert Szene-Metadaten und Einstellungen. <b>Speichert bewusst NICHT das
	 * {@code workingImage}</b> — Bildspeichern ist eine Benutzerentscheidung
	 * (siehe {@link #requestExit()}) — und beendet die JVM <b>nicht</b> mehr.
	 */
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
				settings.setWandTolerance(ed.paintToolbar.getWandTolerancePct());
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
		// KEIN System.exit(0) mehr an dieser Stelle — es steht in finishExit().
	}
}
