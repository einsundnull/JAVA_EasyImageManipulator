package paint;

import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.KeyStroke;

/**
 * Registers all keyboard shortcuts (InputMap/ActionMap) and the global F-key
 * dispatcher for the secondary preview window. Extracted from
 * SelectiveAlphaEditor.setupKeyBindings().
 */
class KeyboardShortcutManager {

	private final SelectiveAlphaEditor ed;

	KeyboardShortcutManager(SelectiveAlphaEditor ed) {
		this.ed = ed;
	}

	void setup() {
		JPanel root = (JPanel) ed.getContentPane();
		InputMap im = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
		ActionMap am = root.getActionMap();

		int CTRL = InputEvent.CTRL_DOWN_MASK;
		int CTRL_SHIFT = InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK;
		int CTRL_ALT = InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK;
		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, CTRL), "copy");
		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, CTRL_SHIFT), "copyOutside");
		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_X, CTRL), "cut");
		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_X, CTRL_SHIFT), "cutOutside");
		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_V, CTRL), "paste");
		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_A, CTRL), "selectAll");
		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.SHIFT_DOWN_MASK | InputEvent.ALT_DOWN_MASK),
				"selectAllElements");
		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, CTRL), "undo");
		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, CTRL), "redo");
		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, CTRL), "save");
		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, CTRL_ALT), "saveOriginal");
		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, CTRL | InputEvent.SHIFT_DOWN_MASK), "saveBurnedCopy");
		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, CTRL_ALT | InputEvent.SHIFT_DOWN_MASK), "saveBurnedOriginal");
		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_R, 0), "rotateCW");
		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.SHIFT_DOWN_MASK), "rotateCCW");
		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.SHIFT_DOWN_MASK), "toggleVis");
		// Umbruch der Mal-Leiste — Belegung steht in KeyBindings, nicht hier (§25).
		im.put(KeyStroke.getKeyStroke(KeyBindings.ROW_WRAP_KEY, KeyBindings.ROW_WRAP_MODIFIERS),
				"togglePaintBarWrap");
		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "escape");
		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "deleteInside");
		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), "deleteOutside");
		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "mergeElement");

		am.put("togglePaintBarWrap", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				// Derselbe Wachposten wie bei den Werkzeug-Tasten: ohne
				// sichtbare Leiste gäbe es keinen Knopf, der die Wirkung
				// zeigt (Univ. §13, eine Feststellung an der Wurzel).
				if (ed.paintToolbar == null || !ed.paintToolbar.isVisible()) return;
				ed.paintToolbar.toggleWrapRows();
			}
		});
		am.put("copy", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				ed.doCopy();
			}
		});
		am.put("copyOutside", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				ed.doCopyOutside();
			}
		});
		am.put("cut", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				ed.doCut();
			}
		});
		am.put("cutOutside", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				ed.doCutOutside();
			}
		});
		am.put("paste", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				ed.doPaste();
			}
		});
		am.put("selectAll", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (ed.ci().appMode == AppMode.PAINT) {
					CanvasInstance c = ed.ci();
					c.selectedElements.clear();
					c.selectedElements.addAll(c.activeElements);
					if (c.canvasPanel != null)
						c.canvasPanel.repaint();
					ed.refreshElementPanel();
				}
			}
		});
		am.put("selectAllElements", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				CanvasInstance c = ed.ci();
				c.selectedElements.clear();
				c.selectedElements.addAll(c.activeElements);
				if (c.canvasPanel != null)
					c.canvasPanel.repaint();
				ed.refreshElementPanel();
			}
		});
		am.put("undo", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				CanvasInstance c = ed.ci();
				if (c.floatingImg != null)
					ed.cancelFloat();
				else
					ed.doUndo();
			}
		});
		am.put("redo", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				ed.doRedo();
			}
		});
		am.put("save", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				ed.saveImageSilent();
			}
		});
		am.put("saveOriginal", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				ed.saveImageToOriginal();
			}
		});
		am.put("saveBurnedCopy", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				ed.saveBurnedElementsCopy();
			}
		});
		am.put("saveBurnedOriginal", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				ed.saveBurnedElementsOriginal();
			}
		});
		am.put("rotateCW", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (isEditingText()) return;
				ed.doRotate(90.0);
			}
		});
		am.put("rotateCCW", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (isEditingText()) return;
				ed.doRotate(-90.0);
			}
		});
		am.put("toggleVis", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (isEditingText()) return;
				CanvasInstance c = ed.ci();
				if (c.workingImage == null)
					return;
				List<Layer> toToggle = c.selectedElements.isEmpty() ? java.util.List.of() : c.selectedElements;
				// EIN pushUndo pro Benutzeraktion, nicht pro Element (§29).
				// Vorher stand der Aufruf in der Schleife: 50 selektierte Layer
				// erzeugten 50 Schnappschüsse und verdrängten damit bei
				// MAX_UNDO = 50 die gesamte echte Pixel-Historie.
				if (!toToggle.isEmpty())
					ed.pushUndo();
				for (Layer el : toToggle) {
					Layer updated = null;
					if (el instanceof ImageLayer il) {
						updated = il.withHidden(!il.isHidden());
					} else if (el instanceof TextLayer tl) {
						updated = tl.withHidden(!tl.isHidden());
					} else if (el instanceof PathLayer pl) {
						updated = pl.withHidden(!pl.isHidden());
					}
					if (updated != null) {
						for (int i = 0; i < c.activeElements.size(); i++) {
							if (c.activeElements.get(i).id() == updated.id()) {
								c.activeElements.set(i, updated);
								break;
							}
						}
						for (int i = 0; i < c.selectedElements.size(); i++) {
							if (c.selectedElements.get(i).id() == updated.id()) {
								c.selectedElements.set(i, updated);
								break;
							}
						}
					}
				}
				if (!toToggle.isEmpty()) {
					ed.markDirty();
					ed.refreshElementPanel();
					if (c.canvasPanel != null)
						c.canvasPanel.repaint();
				}
			}
		});
		am.put("escape", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (ed.rightDropZone != null && ed.rightDropZone.isVisible()) {
					ed.rightDropZone.setVisible(false);
					ed.ci(0).layeredPane.repaint();
				}

				CanvasInstance c = ed.ci();
				if (c.floatingImg != null) {
					ed.cancelFloat();
				} else if (!c.selectedElements.isEmpty()) {
					c.selectedElements.clear();
					c.canvasPanel.repaint();
				} else {
					c.selectedAreas.clear();
					c.isSelecting = false;
					c.selectionStart = null;
					c.selectionEnd = null;
					c.canvasPanel.repaint();
				}
			}
		});
		am.put("deleteInside", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				CanvasInstance c = ed.ci();
				if (!c.selectedElements.isEmpty() && c.selectedElements.get(0) instanceof PathLayer pl) {
					ed.pushUndo();
					PaintEngine.clearPolygon(c.workingImage, pl.absXPoints(), pl.absYPoints());
					ed.markDirty();
				} else if (!c.selectedElements.isEmpty()) {
					ed.deleteSelectedElements();
				} else if (!c.selectedAreas.isEmpty() && c.workingImage != null) {
					ed.pushUndo();
					for (Rectangle r : c.selectedAreas)
						PaintEngine.clearRegion(c.workingImage, r);
					c.selectedAreas.clear();
					c.isSelecting = false;
					c.selectionStart = null;
					c.selectionEnd = null;
					ed.markDirty();
				}
			}
		});
		am.put("deleteOutside", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				CanvasInstance c = ed.ci();
				if (c.workingImage == null)
					return;
				Rectangle sel = ed.getActiveSelection();
				if (sel != null) {
					ed.pushUndo();
					PaintEngine.clearOutside(c.workingImage, sel);
					ed.markDirty();
				}
			}
		});
		am.put("mergeElement", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (!ed.ci().selectedElements.isEmpty())
					ed.mergeSelectedElements();
			}
		});

		setupToolKeys(im, am);

		// Global F1–F7 key dispatcher for secondary preview window
		// Jede hier vergebene Taste MUSS in KeyBindings.ALL stehen (§25).
		java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
			if (e.getID() != KeyEvent.KEY_PRESSED)
				return false;
			// Umschalt+F1: Übersicht aller Tasten und Gesten.
			// Muss VOR der F1-Abfrage stehen — sonst schluckt F1 den Fall und
			// die Hilfe wäre nie erreichbar.
			if (e.getKeyCode() == KeyEvent.VK_F1
					&& (e.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) != 0) {
				KeyBindingsDialog.show(ed);
				return true;
			}
			// Alt+T: Textfeld im sekundären Fenster anzeigen
			if (e.getKeyCode() == KeyEvent.VK_T
					&& (e.getModifiersEx() & InputEvent.ALT_DOWN_MASK) != 0
					&& ed.secWin != null && ed.secWin.isVisible()) {
				ed.showSecondaryTextInput();
				return true;
			}
			// Alt+P: PaintBar als schwebendes Fenster ein-/ausblenden
			if (e.getKeyCode() == KeyEvent.VK_P
					&& (e.getModifiersEx() & InputEvent.ALT_DOWN_MASK) != 0) {
				ed.toggleFloatingPaintBar();
				return true;
			}
			switch (e.getKeyCode()) {
			case KeyEvent.VK_F1 -> {
				ed.toggleSecondaryWindow();
				return true;
			}
			case KeyEvent.VK_F2 -> {
				ed.cyclePreviewMode();
				return true;
			}
			case KeyEvent.VK_F3 -> {
				ed.refreshSnapshot();
				return true;
			}
			case KeyEvent.VK_F4 -> {
				ed.toggleSecondaryFullscreen();
				return true;
			}
			case KeyEvent.VK_F5 -> {
				ed.cycleAlwaysOnTop();
				return true;
			}
			case KeyEvent.VK_F6 -> {
				ed.applySecondaryWindowToCanvas();
				return true;
			}
			case KeyEvent.VK_F7 -> {
				ed.cycleCanvasDisplayMode();
				return true;
			}
			}
			return false;
		});
	}

	// =========================================================================
	// Werkzeug-Kürzel (§25) — Belegung steht in KeyBindings.ALL, Scope TOOL
	// =========================================================================

	/**
	 * Verdrahtet die 25 Werkzeug-Tasten und {@code Z} für das Zauberstab-Raster.
	 *
	 * <p><b>Die Belegung steht in {@link KeyBindings#ALL}, Scope
	 * {@code TOOL} — dort zuerst nachtragen, dann hier</b> (§25). Bis zum
	 * 2026-08-01 versprachen die Tooltips Kürzel, die es nie gab.
	 *
	 * <p><b>Die Taste wählt, sie schaltet nicht ab.</b> Der Knopf schaltet beim
	 * zweiten Klick auf „kein Werkzeug" ({@code PaintToolbar#buildToolButton});
	 * bei einer Taste sähe dasselbe wie „nichts passiert" aus.
	 */
	private void setupToolKeys(InputMap im, ActionMap am) {
		// *** KEINE zweite Liste. *** Die Belegung steht vollständig in
		// KeyBindings.TOOL_KEYS; hier wird sie nur verdrahtet. Genau die
		// zweite, handgepflegte Liste war der Fehler, der diesen Task
		// ausgelöst hat (§25).
		for (KeyBindings.ToolKey tk : KeyBindings.TOOL_KEYS)
			toolKey(im, am, tk.keyCode(), tk.modifiers(), tk.tool());

		// ── Das Raster selbst — kein Werkzeug, deshalb eigener Eintrag ────────
		im.put(KeyStroke.getKeyStroke(KeyBindings.WAND_PANEL_KEY, 0), "toggleWandPanel");
		am.put("toggleWandPanel", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (!toolKeysActive()) return;
				ed.paintToolbar.toggleWandPanel();
			}
		});
	}

	/** Eine Werkzeug-Taste: Belegung eintragen und mit dem Wachposten verdrahten. */
	private void toolKey(InputMap im, ActionMap am, int keyCode, int modifiers,
			PaintEngine.Tool tool) {
		String name = "tool_" + tool + "_" + modifiers;
		im.put(KeyStroke.getKeyStroke(keyCode, modifiers), name);
		am.put(name, new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (!toolKeysActive()) return;
				ed.paintToolbar.setActiveTool(tool);
				// Die elf Zauberstäbe haben ihren Knopf NUR im Raster. Bliebe
				// es zu, wäre die Wahl unsichtbar — genau der Zustand, den die
				// gezeichneten Symbole am 2026-08-01 beseitigt haben.
				if (PaintToolbar.isWandTool(tool))
					ed.paintToolbar.setWandPanelVisible(true);
			}
		});
	}

	/**
	 * Der Wachposten — <b>eine</b> Feststellung an der Wurzel statt einer
	 * Meldepflicht je Handler (Univ. §13).
	 *
	 * <p>Werkzeug-Tasten wirken nur, solange die Mal-Leiste sichtbar ist
	 * (angedockt oder schwebend). Sonst wären sie im Buch-, Szenen- und
	 * Alpha-Modus stille Nebenwirkungen ohne sichtbaren Knopf.
	 */
	private boolean toolKeysActive() {
		return ed.paintToolbar != null && ed.paintToolbar.isVisible() && !isEditingText();
	}

	/**
	 * Ob der Benutzer gerade Text tippt.
	 *
	 * <p><b>Warum das nötig ist:</b> Belegungen der {@code InputMap}
	 * {@code WHEN_IN_FOCUSED_WINDOW} feuern auf {@code KEY_PRESSED}, die
	 * Texteingabe des {@code CanvasPanel} nimmt Zeichen aber erst in
	 * {@code keyTyped()} entgegen — also später. Ein einfacher Buchstabe löst
	 * deshalb <b>beides</b> aus. Am 2026-08-01 mit einem Wegwerf-Programm
	 * nachgewiesen: ein „r" im Text drehte zugleich das Bild um 90 Grad.
	 * Protokoll: {@code doc/progress_2026-08-01_werkzeug-kuerzel.txt}.
	 *
	 * <p>Deshalb fragen das <b>auch</b> die drei älteren Buchstaben-Belegungen
	 * ab ({@code R}, {@code Umschalt+R}, {@code Umschalt+V}) — sie trugen den
	 * Fehler schon vorher.
	 */
	private boolean isEditingText() {
		CanvasInstance c = ed.ci();
		return c != null && c.canvasPanel != null && c.canvasPanel.isEditingText();
	}
}
