package paint;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import javax.swing.*;
import javax.swing.text.JTextComponent;

/**
 * Horizontal page-layout toolbar — docked at the bottom of the main frame,
 * directly above the {@link PaintToolbar}.
 *
 * <p>One toolbar for both canvases; it reflects the layout of whichever
 * page is currently displayed (active canvas).  When a page file is loaded
 * from a book's {@code pages/} directory, {@link #loadFromPage(File)} reads
 * its {@code .layout} manifest and populates the controls.  Every value
 * change is written back to the manifest automatically.
 *
 * <p>Sections (left → right):
 * <ol>
 *   <li><b>Ränder</b> – L / R / T / B margin spinners (mm)</li>
 *   <li><b>Seite</b>  – Kopfzeile / Fußzeile / Seitenzahl toggles</li>
 *   <li><b>Snap</b>   – Aus / Layer / Rand radio-style toggles</li>
 *   <li><b>Anwenden</b> – redraws margin guides on the current page image</li>
 * </ol>
 *
 * <p>Hidden by default; shown via the "SL" button in the top bar.
 */
class PageLayoutToolbar extends JPanel {

    static final int TOOLBAR_H = 96;   // label(14) + strut(2) + 2×spinner(24)+gap(2) + strip-pad(12) + hScrollBar(15) = 93 → 96
    private static final int BTN_W  = 52;
    private static final int BTN_H  = 24;
    private static final int GAP    = 4;

    private final SelectiveAlphaEditor ed;

    // ── State ─────────────────────────────────────────────────────────────────
    private PageLayout layout     = new PageLayout();
    private File       currentPage = null;  // page currently loaded in the toolbar

    // ── Spinners ──────────────────────────────────────────────────────────────
    private JSpinner spLeft, spRight, spTop, spBottom;

    // ── Page-decoration toggles ───────────────────────────────────────────────
    private JToggleButton btnHeader, btnFooter, btnPageNr;

    // ── Snap-mode toggles ─────────────────────────────────────────────────────
    private JToggleButton btnSnapNone, btnSnapLayer, btnSnapMargin;


    // ── Info label ────────────────────────────────────────────────────────────
    private JLabel pageNameLabel;

    // =========================================================================
    PageLayoutToolbar(SelectiveAlphaEditor ed) {
        this.ed = ed;

        setLayout(new BorderLayout());
        setBackground(AppColors.BG_TOOLBAR);
        setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, AppColors.BORDER));
        setPreferredSize(new Dimension(0, TOOLBAR_H));

        JPanel strip = buildStrip();
        JScrollPane scroll = new JScrollPane(strip,
                JScrollPane.VERTICAL_SCROLLBAR_NEVER,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(AppColors.BG_TOOLBAR);
        scroll.setBackground(AppColors.BG_TOOLBAR);
        scroll.getHorizontalScrollBar().setUnitIncrement(20);
        scroll.addMouseWheelListener(e -> {
            JScrollBar bar = scroll.getHorizontalScrollBar();
            bar.setValue(bar.getValue() + e.getUnitsToScroll() * 20);
        });
        add(scroll, BorderLayout.CENTER);

        setVisible(false);
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Loads the layout manifest for {@code pageFile} and updates all controls.
     * Call this whenever a page file is opened in the canvas.
     * Does nothing if {@code pageFile} is not a book page.
     */
    void loadFromPage(File pageFile) {
        if (!PageLayoutManifest.isBookPage(pageFile)) {
            currentPage = null;
            setEnabled(false);
            pageNameLabel.setText("Keine Buchseite");
            pageNameLabel.setForeground(AppColors.TEXT_MUTED);
            return;
        }
        currentPage = pageFile;
        layout = PageLayoutManifest.read(pageFile);
        setEnabled(true);
        pageNameLabel.setText(pageFile.getName());
        pageNameLabel.setForeground(AppColors.TEXT);
        populateControls();
    }

    /** Returns the currently active {@link PageLayout} (may be defaults if no page loaded). */
    PageLayout getPageLayout() { return layout; }

    // =========================================================================
    // Strip builder
    // =========================================================================

    private JPanel buildStrip() {
        JPanel strip = new JPanel();
        strip.setLayout(new BoxLayout(strip, BoxLayout.X_AXIS));
        strip.setBackground(AppColors.BG_TOOLBAR);
        strip.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        // Page name indicator
        pageNameLabel = new JLabel("Keine Buchseite");
        pageNameLabel.setForeground(AppColors.TEXT_MUTED);
        pageNameLabel.setFont(new Font("SansSerif", Font.ITALIC, 11));
        pageNameLabel.setAlignmentY(Component.CENTER_ALIGNMENT);
        pageNameLabel.setPreferredSize(new Dimension(130, BTN_H));
        pageNameLabel.setMaximumSize(new Dimension(130, BTN_H));
        strip.add(pageNameLabel);
        strip.add(hSep());

        strip.add(buildMarginsSection());
        strip.add(hSep());
        strip.add(buildPageSection());
        strip.add(hSep());
        strip.add(buildSnapSection());
        strip.add(hSep());
        strip.add(buildApplySection());
        strip.add(Box.createHorizontalGlue());
        return strip;
    }

    // ── Margins ───────────────────────────────────────────────────────────────
    private JPanel buildMarginsSection() {
        JPanel outer = sectionBox("Ränder");

        JPanel grid = new JPanel(new GridLayout(2, 4, 2, 2));
        grid.setOpaque(false);

        spLeft   = mmSpinner(layout.marginLeft,   "Linker Rand (mm)");
        spRight  = mmSpinner(layout.marginRight,  "Rechter Rand (mm)");
        spTop    = mmSpinner(layout.marginTop,    "Oberer Rand (mm)");
        spBottom = mmSpinner(layout.marginBottom, "Unterer Rand (mm)");

        spLeft  .addChangeListener(e -> onChange(() -> layout.marginLeft   = spinVal(spLeft)));
        spRight .addChangeListener(e -> onChange(() -> layout.marginRight  = spinVal(spRight)));
        spTop   .addChangeListener(e -> onChange(() -> layout.marginTop    = spinVal(spTop)));
        spBottom.addChangeListener(e -> onChange(() -> layout.marginBottom = spinVal(spBottom)));

        grid.add(mLabel("L:")); grid.add(spLeft);
        grid.add(mLabel("R:")); grid.add(spRight);
        grid.add(mLabel("T:")); grid.add(spTop);
        grid.add(mLabel("B:")); grid.add(spBottom);
        outer.add(grid);
        return outer;
    }

    // ── Page decoration toggles ───────────────────────────────────────────────
    private JPanel buildPageSection() {
        JPanel outer = sectionBox("Seite");

        btnHeader = barToggle("KZ",  "Kopfzeile ein/aus");
        btnFooter = barToggle("FZ",  "Fußzeile ein/aus");
        btnPageNr = barToggle(" # ", "Seitenzahl ein/aus");

        btnHeader.addActionListener(e -> onChange(() -> layout.headerVisible     = btnHeader.isSelected()));
        btnFooter.addActionListener(e -> onChange(() -> layout.footerVisible     = btnFooter.isSelected()));
        btnPageNr.addActionListener(e -> onChange(() -> layout.pageNumberVisible = btnPageNr.isSelected()));

        JPanel row = hBox();
        for (JToggleButton btn : new JToggleButton[]{ btnHeader, btnFooter, btnPageNr }) {
            row.add(btn); row.add(Box.createHorizontalStrut(GAP));
        }
        outer.add(row);
        return outer;
    }

    // ── Snap mode ─────────────────────────────────────────────────────────────
    private JPanel buildSnapSection() {
        JPanel outer = sectionBox("Snap");

        btnSnapNone   = barToggle("Aus",   "Kein Einrasten");
        btnSnapLayer  = barToggle("Layer", "An anderen Layern einrasten");
        btnSnapMargin = barToggle("Rand",  "An Seitenrändern einrasten");
        btnSnapNone.setSelected(true);
        syncSnapButtons();

        ButtonGroup grp = new ButtonGroup();
        grp.add(btnSnapNone); grp.add(btnSnapLayer); grp.add(btnSnapMargin);

        btnSnapNone  .addActionListener(e -> onChange(() -> { layout.snapMode = PageLayout.SnapMode.NONE;           syncSnapButtons(); }));
        btnSnapLayer .addActionListener(e -> onChange(() -> { layout.snapMode = PageLayout.SnapMode.SNAP_TO_LAYER;  syncSnapButtons(); }));
        btnSnapMargin.addActionListener(e -> onChange(() -> { layout.snapMode = PageLayout.SnapMode.SNAP_TO_MARGIN; syncSnapButtons(); }));

        JPanel row = hBox();
        for (JToggleButton btn : new JToggleButton[]{ btnSnapNone, btnSnapLayer, btnSnapMargin }) {
            row.add(btn); row.add(Box.createHorizontalStrut(GAP));
        }
        outer.add(row);
        return outer;
    }

    // ── Apply button ──────────────────────────────────────────────────────────
    private JPanel buildApplySection() {
        JPanel outer = sectionBox("Anwenden");

        JButton btn = new JButton("↳ Seite") {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isRollover() ? AppColors.ACCENT : AppColors.ACCENT_ACTIVE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
                super.paintComponent(g);
            }
        };
        btn.setFont(new Font("SansSerif", Font.PLAIN, 11));
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false); btn.setBorderPainted(false);
        btn.setContentAreaFilled(false); btn.setOpaque(false);
        btn.setPreferredSize(new Dimension(BTN_W + 20, BTN_H));
        btn.setMaximumSize(new Dimension(BTN_W + 20, BTN_H));
        btn.setToolTipText("Ränder und Dekoration als Hilfslinien auf die aktuelle Seite zeichnen (Undo möglich)");
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addActionListener(e -> applyToCurrentPage());
        outer.add(btn);
        return outer;
    }

    // =========================================================================
    // Apply guides to current page image
    // =========================================================================

    private void applyToCurrentPage() {
        CanvasInstance c = ed.ci();
        if (c.workingImage == null) return;

        int w  = c.workingImage.getWidth();
        int h  = c.workingImage.getHeight();
        int mL = layout.marginLeftPx();
        int mR = layout.marginRightPx();
        int mT = layout.marginTopPx();
        int mB = layout.marginBottomPx();

        ed.pushUndo();

        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.drawImage(c.workingImage, 0, 0, null);

        // Dashed content-area rectangle
        g2.setColor(new Color(0, 120, 220, 180));
        float[] dash = { 6f, 4f };
        g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, dash, 0f));
        g2.drawRect(mL, mT, w - mL - mR, h - mT - mB);

        // Header stripe
        if (layout.headerVisible) {
            int hh = Math.max(4, mT / 2);
            g2.setStroke(new BasicStroke(1f));
            g2.setColor(new Color(0, 80, 180, 45));
            g2.fillRect(mL, mT - hh, w - mL - mR, hh);
            g2.setColor(new Color(0, 80, 180, 120));
            g2.drawLine(mL, mT - hh, w - mR, mT - hh);
            g2.setFont(new Font("SansSerif", Font.PLAIN, Math.max(7, hh - 2)));
            g2.drawString("Kopfzeile", mL + 4, mT - 2);
        }

        // Footer stripe
        if (layout.footerVisible) {
            int fh = Math.max(4, mB / 2);
            g2.setStroke(new BasicStroke(1f));
            g2.setColor(new Color(0, 80, 180, 45));
            g2.fillRect(mL, h - mB, w - mL - mR, fh);
            g2.setColor(new Color(0, 80, 180, 120));
            g2.drawLine(mL, h - mB + fh, w - mR, h - mB + fh);
            g2.setFont(new Font("SansSerif", Font.PLAIN, Math.max(7, fh - 2)));
            g2.drawString("Fußzeile", mL + 4, h - mB + fh - 2);
        }

        // Page-number indicator
        if (layout.pageNumberVisible) {
            int fs = Math.max(8, mB / 4);
            g2.setColor(new Color(0, 80, 180, 160));
            g2.setFont(new Font("SansSerif", Font.PLAIN, fs));
            g2.drawString("N°", w - mR - fs * 2 - 2, h - mB + fs + 2);
        }

        g2.dispose();
        c.workingImage = img;
        c.canvasPanel.repaint();
        ed.layoutController.markDirty(ed.activeCanvasIndex);
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    /** Called on every control-value change: saves manifest, live-repaints canvas, updates book TextLayer. */
    private void onChange(Runnable update) {
        update.run();
        if (currentPage != null)
            PageLayoutManifest.write(currentPage, layout);
        // Live canvas repaint for margin overlay
        CanvasInstance c = ed.ci();
        if (c.canvasPanel != null) c.canvasPanel.repaint();
        if (ed.showRuler) { if (ed.hRuler != null) ed.hRuler.repaint(); if (ed.vRuler != null) ed.vRuler.repaint(); }
        updateBookTextLayerBounds();
    }

    /**
     * Called from ruler drag handles to update a single margin value and propagate changes.
     *
     * @param isHorizontal true = L/R margins (H ruler), false = T/B (V ruler)
     * @param isFirst      true = left/top, false = right/bottom
     * @param newMm        new margin in mm (clamped to [0, 150])
     */
    void setMarginFromRuler(boolean isHorizontal, boolean isFirst, int newMm) {
        int clamped = Math.max(0, Math.min(150, newMm));
        if (isHorizontal) {
            if (isFirst) { layout.marginLeft   = clamped; if (spLeft   != null) spLeft  .setValue(clamped); }
            else         { layout.marginRight  = clamped; if (spRight  != null) spRight .setValue(clamped); }
        } else {
            if (isFirst) { layout.marginTop    = clamped; if (spTop    != null) spTop   .setValue(clamped); }
            else         { layout.marginBottom = clamped; if (spBottom != null) spBottom.setValue(clamped); }
        }
        if (currentPage != null) PageLayoutManifest.write(currentPage, layout);
        CanvasInstance c = ed.ci();
        if (c.canvasPanel != null) c.canvasPanel.repaint();
        if (ed.showRuler) { if (ed.hRuler != null) ed.hRuler.repaint(); if (ed.vRuler != null) ed.vRuler.repaint(); }
        updateBookTextLayerBounds();
    }

    /** Repositions the book wrapping TextLayer to match current content area. */
    private void updateBookTextLayerBounds() {
        CanvasInstance c = ed.ci();
        if (c.workingImage == null) return;
        int imgW = c.workingImage.getWidth(), imgH = c.workingImage.getHeight();
        int mL = layout.marginLeftPx(), mR = layout.marginRightPx();
        int mT = layout.marginTopPx(),  mB = layout.marginBottomPx();
        int cx = mL, cy = mT;
        int cw = Math.max(1, imgW - mL - mR), ch = Math.max(1, imgH - mT - mB);
        for (int i = 0; i < c.activeElements.size(); i++) {
            Layer el = c.activeElements.get(i);
            if (el instanceof TextLayer tl && tl.isWrapping()) {
                c.activeElements.set(i, tl.withBounds(cx, cy, cw, ch));
                if (c.canvasPanel != null) c.canvasPanel.repaint();
                break;
            }
        }
    }

    /** Pushes all current layout values back into the UI controls (called on loadFromPage). */
    private void populateControls() {
        spLeft  .setValue(layout.marginLeft);
        spRight .setValue(layout.marginRight);
        spTop   .setValue(layout.marginTop);
        spBottom.setValue(layout.marginBottom);

        btnHeader.setSelected(layout.headerVisible);
        btnFooter.setSelected(layout.footerVisible);
        btnPageNr.setSelected(layout.pageNumberVisible);
        syncDecorButtons();

        btnSnapNone  .setSelected(layout.snapMode == PageLayout.SnapMode.NONE);
        btnSnapLayer .setSelected(layout.snapMode == PageLayout.SnapMode.SNAP_TO_LAYER);
        btnSnapMargin.setSelected(layout.snapMode == PageLayout.SnapMode.SNAP_TO_MARGIN);
        syncSnapButtons();

        // Sync TextToolbar from the wrapping TextLayer (if present on this page)
        if (ed.textToolbar != null) {
            CanvasInstance c = ed.ci();
            boolean found = false;
            for (Layer el : c.activeElements) {
                if (el instanceof TextLayer tl && tl.isWrapping()) {
                    ed.textToolbar.showToolbar(tl.fontName(), tl.fontSize(),
                            tl.fontBold(), tl.fontItalic(), tl.fontColor());
                    found = true;
                    break;
                }
            }
            if (!found) ed.textToolbar.hideToolbar();
        }
    }

    private void syncDecorButtons() {
        syncBtn(btnHeader, layout.headerVisible);
        syncBtn(btnFooter, layout.footerVisible);
        syncBtn(btnPageNr, layout.pageNumberVisible);
    }

    private void syncSnapButtons() {
        syncBtn(btnSnapNone,   layout.snapMode == PageLayout.SnapMode.NONE);
        syncBtn(btnSnapLayer,  layout.snapMode == PageLayout.SnapMode.SNAP_TO_LAYER);
        syncBtn(btnSnapMargin, layout.snapMode == PageLayout.SnapMode.SNAP_TO_MARGIN);
    }

    private static void syncBtn(JToggleButton btn, boolean active) {
        btn.setBackground(active ? AppColors.ACCENT_ACTIVE : AppColors.BTN_BG);
    }

    // =========================================================================
    // Widget factories
    // =========================================================================

    /** Section wrapper: small label above + horizontal content row. */
    private JPanel sectionBox(String title) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(false);
        p.setAlignmentY(Component.CENTER_ALIGNMENT);
        JLabel lbl = new JLabel(title);
        lbl.setForeground(AppColors.TEXT_MUTED);
        lbl.setFont(new Font("SansSerif", Font.PLAIN, 9));
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(lbl);
        p.add(Box.createVerticalStrut(2));
        return p;
    }

    private JSpinner mmSpinner(int value, String tooltip) {
        JSpinner sp = new JSpinner(new SpinnerNumberModel(value, 0, 150, 1));
        sp.setPreferredSize(new Dimension(42, BTN_H));
        sp.setMaximumSize(new Dimension(42, BTN_H));
        sp.setToolTipText(tooltip);
        JComponent editor = sp.getEditor();
        if (editor instanceof JSpinner.DefaultEditor de) {
            JTextComponent tf = de.getTextField();
            tf.setBackground(AppColors.BTN_BG);
            tf.setForeground(AppColors.TEXT);
            tf.setFont(new Font("SansSerif", Font.PLAIN, 11));
            if (tf instanceof JTextField jtf)
                jtf.setHorizontalAlignment(SwingConstants.CENTER);
        }
        sp.setBackground(AppColors.BTN_BG);
        return sp;
    }

    private static int spinVal(JSpinner sp) {
        Object v = sp.getValue();
        return (v instanceof Number n) ? n.intValue() : 0;
    }

    private JToggleButton barToggle(String text, String tooltip) {
        JToggleButton btn = new JToggleButton(text);
        btn.setFont(new Font("SansSerif", Font.PLAIN, 8));
        btn.setForeground(AppColors.TEXT);
        btn.setBackground(AppColors.BTN_BG);
        btn.setOpaque(true); btn.setContentAreaFilled(true);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createLineBorder(AppColors.BORDER));
        btn.setPreferredSize(new Dimension(BTN_W, BTN_H));
        btn.setMaximumSize(new Dimension(BTN_W, BTN_H));
        btn.setToolTipText(tooltip);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addItemListener(e ->
            btn.setBackground(btn.isSelected() ? AppColors.ACCENT_ACTIVE : AppColors.BTN_BG));
        return btn;
    }

    private JLabel mLabel(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(AppColors.TEXT_MUTED);
        l.setFont(new Font("SansSerif", Font.PLAIN, 10));
        return l;
    }

    /** Horizontal box panel with CENTER_ALIGNMENT. */
    private JPanel hBox() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    /** Vertical separator between toolbar sections. */
    private Component hSep() {
        int sepH = TOOLBAR_H - 24; // leave 12px top + bottom breathing room
        JPanel sep = new JPanel();
        sep.setBackground(AppColors.BORDER);
        sep.setPreferredSize(new Dimension(1, sepH));
        sep.setMaximumSize(new Dimension(1, sepH));
        JPanel w = new JPanel();
        w.setOpaque(false);
        w.setLayout(new BoxLayout(w, BoxLayout.X_AXIS));
        w.add(Box.createHorizontalStrut(6));
        w.add(sep);
        w.add(Box.createHorizontalStrut(6));
        return w;
    }
}
