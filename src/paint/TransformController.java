package paint;

import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * Handles image/element transformation operations: flip, rotate, scale.
 * Extracted from SelectiveAlphaEditor.
 */
class TransformController {

	private final SelectiveAlphaEditor ed;

	TransformController(SelectiveAlphaEditor ed) {
		this.ed = ed;
	}

	void doFlipH() {
		CanvasInstance c = ed.ci();
		if (c.workingImage == null)
			return;

		if (!c.selectedElements.isEmpty()) {
			ed.pushUndo();
			for (int i = 0; i < c.selectedElements.size(); i++) {
				Layer el = c.selectedElements.get(i);
				if (el instanceof ImageLayer il) {
					BufferedImage flipped = PaintEngine.flipHorizontal(il.image());
					ImageLayer updated = new ImageLayer(il.id(), flipped, il.x(), il.y(), il.width(), il.height());
					c.selectedElements.set(i, updated);
					for (int j = 0; j < c.activeElements.size(); j++) {
						if (c.activeElements.get(j).id() == updated.id()) {
							c.activeElements.set(j, updated);
							break;
						}
					}
				}
			}
			ed.markDirty();
			ed.refreshElementPanel();
			if (c.canvasPanel != null)
				c.canvasPanel.repaint();
			return;
		}

		Rectangle sel = (ed.ci().appMode == AppMode.PAINT) ? ed.getActiveSelection() : null;
		ed.pushUndo();
		if (sel != null) {
			PaintEngine.flipHorizontalInRegion(c.workingImage, sel);
		} else {
			c.workingImage = PaintEngine.flipHorizontal(c.workingImage);
		}
		ed.markDirty();
	}

	void doFlipV() {
		CanvasInstance c = ed.ci();
		if (c.workingImage == null)
			return;

		if (!c.selectedElements.isEmpty()) {
			ed.pushUndo();
			for (int i = 0; i < c.selectedElements.size(); i++) {
				Layer el = c.selectedElements.get(i);
				if (el instanceof ImageLayer il) {
					BufferedImage flipped = PaintEngine.flipVertical(il.image());
					ImageLayer updated = new ImageLayer(il.id(), flipped, il.x(), il.y(), il.width(), il.height());
					c.selectedElements.set(i, updated);
					for (int j = 0; j < c.activeElements.size(); j++) {
						if (c.activeElements.get(j).id() == updated.id()) {
							c.activeElements.set(j, updated);
							break;
						}
					}
				}
			}
			ed.markDirty();
			ed.refreshElementPanel();
			if (c.canvasPanel != null)
				c.canvasPanel.repaint();
			return;
		}

		Rectangle sel = (ed.ci().appMode == AppMode.PAINT) ? ed.getActiveSelection() : null;
		ed.pushUndo();
		if (sel != null) {
			PaintEngine.flipVerticalInRegion(c.workingImage, sel);
		} else {
			c.workingImage = PaintEngine.flipVertical(c.workingImage);
		}
		ed.markDirty();
	}

	void doRotate(double angleDeg) {
		CanvasInstance c = ed.ci();
		if (c.workingImage == null)
			return;

		if (!c.selectedElements.isEmpty()) {
			ed.pushUndo();
			for (int i = 0; i < c.selectedElements.size(); i++) {
				Layer el = c.selectedElements.get(i);
				if (el instanceof ImageLayer il) {
					double newAngle = il.rotationAngle() + angleDeg;
					ImageLayer updated = il.withRotation(newAngle);
					c.selectedElements.set(i, updated);
					for (int j = 0; j < c.activeElements.size(); j++) {
						if (c.activeElements.get(j).id() == updated.id()) {
							c.activeElements.set(j, updated);
							break;
						}
					}
				}
			}
			ed.markDirty();
			ed.refreshElementPanel();
			if (c.canvasPanel != null)
				c.canvasPanel.repaint();
			return;
		}

		Rectangle sel = (ed.ci().appMode == AppMode.PAINT) ? ed.getActiveSelection() : null;
		ed.pushUndo();
		if (sel != null) {
			PaintEngine.flipVerticalInRegion(c.workingImage, sel);
		} else {
			c.workingImage = PaintEngine.flipVertical(c.workingImage);
		}
		ed.markDirty();
	}

	void doRotate() {
		if (ed.ci().workingImage == null)
			return;
		JTextField angleField = new JTextField("90", 6);
		angleField.setBackground(AppColors.BTN_BG);
		angleField.setForeground(AppColors.TEXT);
		angleField.setCaretColor(AppColors.TEXT);

		JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
		panel.setBackground(AppColors.BG_PANEL);
		JLabel lbl = new JLabel("Winkel (°):");
		lbl.setForeground(AppColors.TEXT);
		panel.add(lbl);
		panel.add(angleField);

		JDialog dialog = ed.createBaseDialog("Drehen", 280, 160);
		JPanel content = ed.centeredColumnPanel(16, 24, 12);
		content.add(panel);
		content.add(Box.createVerticalStrut(12));

		JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
		row.setOpaque(false);
		JButton ok = UIComponentFactory.buildButton("OK", AppColors.ACCENT, AppColors.ACCENT_HOVER);
		JButton can = UIComponentFactory.buildButton("Abbrechen", AppColors.BTN_BG, AppColors.BTN_HOVER);
		ok.setForeground(Color.WHITE);
		final boolean rotateHasSel = (ed.ci().appMode == AppMode.PAINT && ed.getActiveSelection() != null);
		final Rectangle rotateSel = rotateHasSel ? ed.getActiveSelection() : null;
		ok.addActionListener(e -> {
			try {
				CanvasInstance c = ed.ci();
				double deg = Double.parseDouble(angleField.getText().trim());
				ed.pushUndo();
				if (rotateHasSel) {
					PaintEngine.rotateInRegion(c.workingImage, rotateSel, deg);
				} else {
					c.workingImage = PaintEngine.rotate(c.workingImage, deg);
					c.canvasWrapper.revalidate();
				}
				ed.markDirty();
			} catch (NumberFormatException ex) {
				ed.showErrorDialog("Ungültige Eingabe", "Bitte eine Zahl eingeben.");
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

	void doScale() {
		CanvasInstance c = ed.ci();
		if (c.workingImage == null)
			return;
		Rectangle scaleSel = (ed.ci().appMode == AppMode.PAINT) ? ed.getActiveSelection() : null;
		int origW = scaleSel != null ? scaleSel.width : c.workingImage.getWidth();
		int origH = scaleSel != null ? scaleSel.height : c.workingImage.getHeight();

		JTextField wField = new JTextField(String.valueOf(origW), 5);
		JTextField hField = new JTextField(String.valueOf(origH), 5);
		JTextField pctField = new JTextField("100", 5);
		JCheckBox lockAR = new JCheckBox("Proportional", true);

		for (JTextField f : new JTextField[] { wField, hField, pctField }) {
			f.setBackground(AppColors.BTN_BG);
			f.setForeground(AppColors.TEXT);
			f.setCaretColor(AppColors.TEXT);
		}
		lockAR.setOpaque(false);
		lockAR.setForeground(AppColors.TEXT);

		pctField.addActionListener(ev -> {
			try {
				double pct = Double.parseDouble(pctField.getText().trim()) / 100.0;
				wField.setText(String.valueOf((int) (origW * pct)));
				hField.setText(String.valueOf((int) (origH * pct)));
			} catch (NumberFormatException ignored) {
			}
		});
		wField.addActionListener(ev -> {
			if (lockAR.isSelected()) {
				try {
					int nw = Integer.parseInt(wField.getText().trim());
					hField.setText(String.valueOf((int) (origH * ((double) nw / origW))));
					pctField.setText(String.format("%.1f", 100.0 * nw / origW));
				} catch (NumberFormatException ignored) {
				}
			}
		});

		JPanel grid = new JPanel(new GridLayout(4, 2, 6, 4));
		grid.setOpaque(false);
		grid.removeAll();
		String[] labels = { "Breite (px):", "Höhe (px):", "Prozent:", "" };
		JComponent[] fields = { wField, hField, pctField, lockAR };
		for (int i = 0; i < labels.length; i++) {
			JLabel l = new JLabel(labels[i]);
			l.setForeground(AppColors.TEXT);
			grid.add(l);
			grid.add(fields[i]);
		}

		JDialog dialog = ed.createBaseDialog("Skalieren", 300, 230);
		JPanel content = ed.centeredColumnPanel(16, 20, 12);
		content.add(grid);
		content.add(Box.createVerticalStrut(12));

		JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
		row.setOpaque(false);
		JButton ok = UIComponentFactory.buildButton("OK", AppColors.ACCENT, AppColors.ACCENT_HOVER);
		JButton can = UIComponentFactory.buildButton("Abbrechen", AppColors.BTN_BG, AppColors.BTN_HOVER);
		ok.setForeground(Color.WHITE);
		ok.addActionListener(e -> {
			try {
				int nw = Integer.parseInt(wField.getText().trim());
				int nh = Integer.parseInt(hField.getText().trim());
				ed.pushUndo();
				if (scaleSel != null) {
					Rectangle newSel = PaintEngine.scaleInRegion(c.workingImage, scaleSel, nw, nh);
					c.selectedAreas.clear();
					c.selectedAreas.add(newSel);
				} else {
					c.workingImage = PaintEngine.scale(c.workingImage, nw, nh);
					c.canvasWrapper.revalidate();
				}
				ed.markDirty();
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
}
