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
		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "escape");
		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "deleteInside");
		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), "deleteOutside");
		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "mergeElement");

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
				ed.doRotate(90.0);
			}
		});
		am.put("rotateCCW", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				ed.doRotate(-90.0);
			}
		});
		am.put("toggleVis", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				CanvasInstance c = ed.ci();
				if (c.workingImage == null)
					return;
				List<Layer> toToggle = c.selectedElements.isEmpty() ? java.util.List.of() : c.selectedElements;
				for (Layer el : toToggle) {
					ed.pushUndo();
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

		// Global F1–F7 key dispatcher for secondary preview window
		java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
			if (e.getID() != KeyEvent.KEY_PRESSED)
				return false;
			// Alt+T: Textfeld im sekundären Fenster anzeigen
			if (e.getKeyCode() == KeyEvent.VK_T
					&& (e.getModifiersEx() & InputEvent.ALT_DOWN_MASK) != 0
					&& ed.secWin != null && ed.secWin.isVisible()) {
				ed.showSecondaryTextInput();
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
}
