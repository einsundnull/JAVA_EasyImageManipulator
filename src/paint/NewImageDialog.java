package paint;

import book.PaperFormat;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.event.ChangeListener;

/**
 * Dialog for creating a new image. Supports px/mm/cm/inch units, paper format
 * presets, portrait/landscape orientation, and optional margin editor with
 * interactive drag handles that snap to 1 mm.
 */
class NewImageDialog extends JDialog {

	static final double PX_PER_MM = 96.0 / 25.4;

	record Result(int widthPx, int heightPx,
			boolean withMargins,
			int mTopMm, int mBottomMm, int mLeftMm, int mRightMm) {}

	private Result result;

	// ── Dialog state ──────────────────────────────────────────────────────────
	private int     widthPx    = 1024;
	private int     heightPx   = 1024;
	private int     unitIndex  = 0;       // 0=px  1=mm  2=cm  3=inch
	private boolean landscape  = false;
	private boolean withMargins = false;
	private int mTopMm = 25, mBottomMm = 25, mLeftMm = 25, mRightMm = 25;
	private boolean suppress   = false;

	// ── Controls ──────────────────────────────────────────────────────────────
	private JTextField        widthField, heightField;
	private JComboBox<String> unitCombo, formatCombo;
	private JToggleButton     portraitBtn, landscapeBtn;
	private JCheckBox         marginsCheck;
	private JSpinner          mTopSpin, mBotSpin, mLeftSpin, mRightSpin;
	private JPanel            marginSection;
	private MarginPreviewPanel preview;

	// ── Constructor ───────────────────────────────────────────────────────────
	NewImageDialog(Window owner) {
		super(owner, "Neue Datei", ModalityType.APPLICATION_MODAL);
		buildUI();
		pack();
		setMinimumSize(new Dimension(480, 300));
		setLocationRelativeTo(owner);
	}

	/** Shows the dialog and returns the user's choice, or {@code null} if cancelled. */
	Result showAndGet() {
		setVisible(true);
		return result;
	}

	// ── UI construction ───────────────────────────────────────────────────────
	private void buildUI() {
		JPanel root = new JPanel();
		root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
		root.setBackground(AppColors.BG_DARK);
		root.setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));

		// ── Format + orientation ──────────────────────────────────────────────
		PaperFormat.Format[] fmts = PaperFormat.Format.values();
		String[] fmtNames = new String[fmts.length + 1];
		fmtNames[0] = "Benutzerdefiniert";
		for (int i = 0; i < fmts.length; i++) fmtNames[i + 1] = fmts[i].name();
		formatCombo = combo(fmtNames, 160);
		formatCombo.addActionListener(e -> onFormatSelected());

		ButtonGroup og = new ButtonGroup();
		portraitBtn  = toggleBtn("Hochformat");
		landscapeBtn = toggleBtn("Querformat");
		og.add(portraitBtn);
		og.add(landscapeBtn);
		portraitBtn.setSelected(true);
		portraitBtn.addActionListener(e  -> { if  (landscape)  { landscape = false; onOrientSwap(); } });
		landscapeBtn.addActionListener(e -> { if (!landscape)  { landscape = true;  onOrientSwap(); } });

		JPanel fmtRow = row();
		fmtRow.add(lbl("Format:"));
		fmtRow.add(Box.createHorizontalStrut(6));
		fmtRow.add(formatCombo);
		fmtRow.add(Box.createHorizontalStrut(12));
		fmtRow.add(portraitBtn);
		fmtRow.add(Box.createHorizontalStrut(4));
		fmtRow.add(landscapeBtn);
		fmtRow.add(Box.createHorizontalGlue());
		root.add(fmtRow);
		root.add(Box.createVerticalStrut(8));

		// ── Dimensions + unit ─────────────────────────────────────────────────
		widthField  = dimTF("1024");
		heightField = dimTF("1024");
		widthField.addFocusListener(new FocusAdapter() {
			@Override public void focusLost(FocusEvent e) { parseDim(widthField,  true); }
		});
		heightField.addFocusListener(new FocusAdapter() {
			@Override public void focusLost(FocusEvent e) { parseDim(heightField, false); }
		});
		widthField.addActionListener(e  -> parseDim(widthField,  true));
		heightField.addActionListener(e -> parseDim(heightField, false));

		unitCombo = combo(new String[]{"px", "mm", "cm", "inch"}, 65);
		unitCombo.addActionListener(e -> onUnitChanged());

		JPanel dimRow = row();
		dimRow.add(lbl("Breite:"));
		dimRow.add(Box.createHorizontalStrut(6));
		dimRow.add(widthField);
		dimRow.add(Box.createHorizontalStrut(14));
		dimRow.add(lbl("Höhe:"));
		dimRow.add(Box.createHorizontalStrut(6));
		dimRow.add(heightField);
		dimRow.add(Box.createHorizontalStrut(8));
		dimRow.add(unitCombo);
		dimRow.add(Box.createHorizontalGlue());
		root.add(dimRow);
		root.add(Box.createVerticalStrut(10));

		// ── Mit Rändern checkbox ──────────────────────────────────────────────
		marginsCheck = new JCheckBox("Mit Rändern");
		marginsCheck.setOpaque(false);
		marginsCheck.setForeground(AppColors.TEXT);
		marginsCheck.setFocusable(false);
		marginsCheck.addActionListener(e -> {
			withMargins = marginsCheck.isSelected();
			marginSection.setVisible(withMargins);
			pack();
			revalidate();
		});
		JPanel cbRow = row();
		cbRow.add(marginsCheck);
		cbRow.add(Box.createHorizontalGlue());
		root.add(cbRow);
		root.add(Box.createVerticalStrut(6));

		// ── Margin section ────────────────────────────────────────────────────
		marginSection = new JPanel();
		marginSection.setLayout(new BoxLayout(marginSection, BoxLayout.Y_AXIS));
		marginSection.setBackground(AppColors.BG_DARK);
		marginSection.setAlignmentX(Component.LEFT_ALIGNMENT);
		marginSection.setVisible(false);

		mTopSpin  = mmSpin(mTopMm);
		mBotSpin  = mmSpin(mBottomMm);
		mLeftSpin = mmSpin(mLeftMm);
		mRightSpin = mmSpin(mRightMm);
		ChangeListener cl = e -> {
			if (!suppress) {
				mTopMm    = (int) mTopSpin.getValue();
				mBottomMm = (int) mBotSpin.getValue();
				mLeftMm   = (int) mLeftSpin.getValue();
				mRightMm  = (int) mRightSpin.getValue();
				if (preview != null) preview.repaint();
			}
		};
		mTopSpin.addChangeListener(cl);
		mBotSpin.addChangeListener(cl);
		mLeftSpin.addChangeListener(cl);
		mRightSpin.addChangeListener(cl);

		JPanel mRow1 = row();
		mRow1.add(lbl("Oben:"));   mRow1.add(Box.createHorizontalStrut(4)); mRow1.add(mTopSpin);
		mRow1.add(Box.createHorizontalStrut(14));
		mRow1.add(lbl("Unten:"));  mRow1.add(Box.createHorizontalStrut(4)); mRow1.add(mBotSpin);
		mRow1.add(Box.createHorizontalGlue());

		JPanel mRow2 = row();
		mRow2.add(lbl("Links:"));  mRow2.add(Box.createHorizontalStrut(4)); mRow2.add(mLeftSpin);
		mRow2.add(Box.createHorizontalStrut(14));
		mRow2.add(lbl("Rechts:")); mRow2.add(Box.createHorizontalStrut(4)); mRow2.add(mRightSpin);
		mRow2.add(Box.createHorizontalGlue());

		preview = new MarginPreviewPanel();
		preview.setAlignmentX(Component.LEFT_ALIGNMENT);

		marginSection.add(mRow1);
		marginSection.add(Box.createVerticalStrut(4));
		marginSection.add(mRow2);
		marginSection.add(Box.createVerticalStrut(8));
		marginSection.add(preview);
		marginSection.add(Box.createVerticalStrut(4));
		root.add(marginSection);
		root.add(Box.createVerticalStrut(10));

		// ── Buttons ───────────────────────────────────────────────────────────
		JButton okBtn  = UIComponentFactory.buildButton("Erstellen",  AppColors.ACCENT,  AppColors.ACCENT_HOVER);
		JButton canBtn = UIComponentFactory.buildButton("Abbrechen", AppColors.BTN_BG, AppColors.BTN_HOVER);
		okBtn.setForeground(Color.WHITE);
		okBtn.addActionListener(e -> {
			result = new Result(widthPx, heightPx, withMargins, mTopMm, mBottomMm, mLeftMm, mRightMm);
			dispose();
		});
		canBtn.addActionListener(e -> dispose());
		JPanel btnRow = row();
		btnRow.add(Box.createHorizontalGlue());
		btnRow.add(okBtn);
		btnRow.add(Box.createHorizontalStrut(8));
		btnRow.add(canBtn);
		root.add(btnRow);

		getContentPane().setBackground(AppColors.BG_DARK);
		getContentPane().add(root);
	}

	// ── Event handlers ────────────────────────────────────────────────────────

	private void onFormatSelected() {
		int sel = formatCombo.getSelectedIndex();
		if (sel == 0) return;
		PaperFormat.Format fmt = PaperFormat.Format.values()[sel - 1];
		suppress = true;
		int wMm = landscape ? fmt.getWidthLandscape()  : fmt.getWidthPortrait();
		int hMm = landscape ? fmt.getHeightLandscape() : fmt.getHeightPortrait();
		widthPx   = mmToPx(wMm);
		heightPx  = mmToPx(hMm);
		mTopMm    = fmt.getMarginTop();
		mBottomMm = fmt.getMarginBottom();
		mLeftMm   = fmt.getMarginInner();
		mRightMm  = fmt.getMarginOuter();
		mTopSpin.setValue(mTopMm);
		mBotSpin.setValue(mBottomMm);
		mLeftSpin.setValue(mLeftMm);
		mRightSpin.setValue(mRightMm);
		suppress = false;
		syncDimFields();
		if (preview != null) preview.repaint();
	}

	private void onOrientSwap() {
		int tmp = widthPx; widthPx = heightPx; heightPx = tmp;
		syncDimFields();
		if (preview != null) preview.repaint();
	}

	private void onUnitChanged() {
		unitIndex = unitCombo.getSelectedIndex();
		suppress = true;
		syncDimFields();
		suppress = false;
	}

	private void parseDim(JTextField f, boolean isW) {
		if (suppress) return;
		try {
			double v = Double.parseDouble(f.getText().trim().replace(',', '.'));
			int px = toPx(v);
			if (isW) widthPx  = Math.max(1, px);
			else     heightPx = Math.max(1, px);
			formatCombo.setSelectedIndex(0);
			if (preview != null) preview.repaint();
		} catch (NumberFormatException ignored) {
			syncDimFields();
		}
	}

	private void syncDimFields() {
		widthField.setText(fmtDim(widthPx));
		heightField.setText(fmtDim(heightPx));
	}

	// ── Unit helpers ──────────────────────────────────────────────────────────

	static int mmToPx(double mm) { return (int) Math.round(mm * PX_PER_MM); }

	private int toPx(double v) {
		return switch (unitIndex) {
			case 0  -> (int) Math.round(v);
			case 1  -> (int) Math.round(v * PX_PER_MM);
			case 2  -> (int) Math.round(v * PX_PER_MM * 10);
			default -> (int) Math.round(v * 96);
		};
	}

	private String fmtDim(int px) {
		return switch (unitIndex) {
			case 0  -> String.valueOf(px);
			case 1  -> String.format("%.1f", px / PX_PER_MM);
			case 2  -> String.format("%.2f", px / (PX_PER_MM * 10));
			default -> String.format("%.2f", px / 96.0);
		};
	}

	// ── Widget helpers ────────────────────────────────────────────────────────

	private JPanel row() {
		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
		p.setBackground(AppColors.BG_DARK);
		p.setAlignmentX(Component.LEFT_ALIGNMENT);
		return p;
	}

	private JLabel lbl(String t) {
		JLabel l = new JLabel(t);
		l.setForeground(AppColors.TEXT);
		l.setFont(new Font("SansSerif", Font.PLAIN, 11));
		return l;
	}

	private JTextField dimTF(String init) {
		JTextField f = new JTextField(init, 6);
		f.setBackground(new Color(50, 50, 50));
		f.setForeground(AppColors.TEXT);
		f.setCaretColor(AppColors.TEXT);
		f.setFont(new Font("SansSerif", Font.PLAIN, 11));
		f.setPreferredSize(new Dimension(80, 26));
		f.setMaximumSize(new Dimension(80, 28));
		return f;
	}

	private JComboBox<String> combo(String[] items, int w) {
		JComboBox<String> c = new JComboBox<>(items);
		c.setBackground(new Color(50, 50, 50));
		c.setForeground(AppColors.TEXT);
		c.setFont(new Font("SansSerif", Font.PLAIN, 11));
		c.setPreferredSize(new Dimension(w, 26));
		c.setMaximumSize(new Dimension(w, 28));
		c.setFocusable(false);
		return c;
	}

	private JToggleButton toggleBtn(String text) {
		JToggleButton b = new JToggleButton(text);
		b.setFont(new Font("SansSerif", Font.PLAIN, 11));
		b.setForeground(AppColors.TEXT);
		b.setBackground(new Color(55, 55, 55));
		b.setPreferredSize(new Dimension(95, 26));
		b.setMaximumSize(new Dimension(95, 28));
		b.setFocusable(false);
		return b;
	}

	private JSpinner mmSpin(int init) {
		JSpinner s = new JSpinner(new SpinnerNumberModel(init, 0, 500, 1));
		s.setPreferredSize(new Dimension(60, 26));
		s.setMaximumSize(new Dimension(60, 28));
		s.setFont(new Font("SansSerif", Font.PLAIN, 11));
		if (s.getEditor() instanceof JSpinner.DefaultEditor de) {
			de.getTextField().setBackground(new Color(50, 50, 50));
			de.getTextField().setForeground(AppColors.TEXT);
			de.getTextField().setCaretColor(AppColors.TEXT);
		}
		return s;
	}

	// ── MarginPreviewPanel ────────────────────────────────────────────────────

	private class MarginPreviewPanel extends JPanel {
		private static final int HR = 5; // handle radius
		private int dragging = -1;

		MarginPreviewPanel() {
			setBackground(AppColors.BG_DARK);
			setPreferredSize(new Dimension(320, 200));
			addMouseListener(new MouseAdapter() {
				@Override public void mousePressed(MouseEvent e)  { dragging = hit(e.getX(), e.getY()); }
				@Override public void mouseReleased(MouseEvent e) { dragging = -1; repaint(); }
			});
			addMouseMotionListener(new MouseMotionAdapter() {
				@Override public void mouseDragged(MouseEvent e) { doDrag(e); }
				@Override public void mouseMoved(MouseEvent e)   { updateCursor(e); }
			});
		}

		/** Returns [ox, oy, sw, sh, scale*1e6] or null if dimensions are invalid. */
		private int[] dims() {
			if (widthPx <= 0 || heightPx <= 0) return null;
			int pw = getWidth() - 24, ph = getHeight() - 16;
			double sx = (double) pw / widthPx, sy = (double) ph / heightPx;
			double sc = Math.min(sx, sy);
			int sw = (int)(widthPx * sc), sh = (int)(heightPx * sc);
			return new int[]{(getWidth()-sw)/2, (getHeight()-sh)/2, sw, sh, (int)(sc * 1_000_000)};
		}

		private static double scale(int[] d) { return d[4] / 1_000_000.0; }

		/** Screen coords of drag handles: 0=top, 1=bottom, 2=left, 3=right. */
		private int[][] handles(int[] d) {
			if (d == null) return null;
			double sc = scale(d);
			int topY   = d[1] + (int)(mTopMm    * PX_PER_MM * sc);
			int botY   = d[1] + d[3] - (int)(mBottomMm * PX_PER_MM * sc);
			int leftX  = d[0] + (int)(mLeftMm   * PX_PER_MM * sc);
			int rightX = d[0] + d[2] - (int)(mRightMm * PX_PER_MM * sc);
			int cx = d[0] + d[2]/2, cy = d[1] + d[3]/2;
			return new int[][]{{cx, topY}, {cx, botY}, {leftX, cy}, {rightX, cy}};
		}

		private int hit(int mx, int my) {
			int[][] pts = handles(dims());
			if (pts == null) return -1;
			for (int i = 0; i < pts.length; i++) {
				int dx = mx - pts[i][0], dy = my - pts[i][1];
				if (dx*dx + dy*dy <= (HR+4)*(HR+4)) return i;
			}
			return -1;
		}

		private void updateCursor(MouseEvent e) {
			int h = hit(e.getX(), e.getY());
			if      (h < 0)   setCursor(Cursor.getDefaultCursor());
			else if (h <= 1)  setCursor(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR));
			else              setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
		}

		private void doDrag(MouseEvent e) {
			if (dragging < 0) return;
			int[] d = dims();
			if (d == null) return;
			double sc = scale(d);
			int maxH = (int)(heightPx / PX_PER_MM), maxW = (int)(widthPx / PX_PER_MM);
			switch (dragging) {
				case 0 -> mTopMm    = clamp(snap((int)Math.round((e.getY()-d[1])         / (sc*PX_PER_MM))), 0, maxH - mBottomMm - 5);
				case 1 -> mBottomMm = clamp(snap((int)Math.round((d[1]+d[3]-e.getY())   / (sc*PX_PER_MM))), 0, maxH - mTopMm    - 5);
				case 2 -> mLeftMm   = clamp(snap((int)Math.round((e.getX()-d[0])         / (sc*PX_PER_MM))), 0, maxW - mRightMm  - 5);
				case 3 -> mRightMm  = clamp(snap((int)Math.round((d[0]+d[2]-e.getX())   / (sc*PX_PER_MM))), 0, maxW - mLeftMm   - 5);
				default -> { return; }
			}
			suppress = true;
			mTopSpin.setValue(mTopMm);
			mBotSpin.setValue(mBottomMm);
			mLeftSpin.setValue(mLeftMm);
			mRightSpin.setValue(mRightMm);
			suppress = false;
			repaint();
		}

		private static int snap(int mm)             { return Math.max(0, mm); } // natural 1 mm steps
		private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);
			int[] d = dims();
			if (d == null) return;
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

			// Page
			g2.setColor(Color.WHITE);
			g2.fillRect(d[0], d[1], d[2], d[3]);
			g2.setColor(new Color(90, 90, 90));
			g2.setStroke(new BasicStroke(1f));
			g2.drawRect(d[0], d[1], d[2]-1, d[3]-1);

			// Margin guide lines (dashed blue)
			double sc = scale(d);
			int topY   = d[1] + (int)(mTopMm    * PX_PER_MM * sc);
			int botY   = d[1] + d[3] - (int)(mBottomMm * PX_PER_MM * sc);
			int leftX  = d[0] + (int)(mLeftMm   * PX_PER_MM * sc);
			int rightX = d[0] + d[2] - (int)(mRightMm * PX_PER_MM * sc);
			float[] dash = {4f, 3f};
			g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, dash, 0f));
			g2.setColor(new Color(0, 120, 220, 170));
			g2.drawLine(d[0],   topY,  d[0]+d[2], topY);
			g2.drawLine(d[0],   botY,  d[0]+d[2], botY);
			g2.drawLine(leftX,  d[1],  leftX,  d[1]+d[3]);
			g2.drawLine(rightX, d[1],  rightX, d[1]+d[3]);

			// Handles
			int[][] pts = handles(d);
			if (pts != null) {
				g2.setStroke(new BasicStroke(1f));
				for (int i = 0; i < 4; i++) {
					g2.setColor(dragging == i ? AppColors.ACCENT_HOVER : AppColors.ACCENT);
					g2.fillOval(pts[i][0]-HR, pts[i][1]-HR, HR*2, HR*2);
					g2.setColor(Color.WHITE);
					g2.drawOval(pts[i][0]-HR, pts[i][1]-HR, HR*2, HR*2);
				}
			}

			// Margin labels
			g2.setColor(new Color(0, 140, 255));
			g2.setFont(new Font("SansSerif", Font.PLAIN, 9));
			g2.setStroke(new BasicStroke(1f));
			if (topY  > d[1]      + 4)  g2.drawString(mTopMm    + "mm", d[0]+2,     topY-2);
			if (botY  < d[1]+d[3] - 4)  g2.drawString(mBottomMm + "mm", d[0]+2,     botY+10);
			if (leftX > d[0]      + 20) g2.drawString(mLeftMm   + "mm", leftX+2,    d[1]+11);
			if (rightX< d[0]+d[2] - 20) g2.drawString(mRightMm  + "mm", rightX+2,   d[1]+11);

			g2.dispose();
		}
	}
}
