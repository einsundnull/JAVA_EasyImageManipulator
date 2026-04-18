package paint;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.text.JTextComponent;

/**
 * LEGACY – vertical sidebar version of the page-layout toolbar.
 * Kept for reference; the active implementation is {@link PageLayoutToolbar}.
 *
 * Original concept: one instance per canvas side (left of Canvas I, right of
 * Canvas II), docked inside the galleryWrapper.
 */
class PageLayoutToolbarLegacy extends JPanel {

    static final int TOOLBAR_W = 80;

    private final SelectiveAlphaEditor ed;
    private final int                  canvasIdx;
    private final PageLayout           layout = new PageLayout();

    private JSpinner spLeft, spRight, spTop, spBottom;
    private JToggleButton btnHeader, btnFooter, btnPageNr;
    private JToggleButton btnSnapNone, btnSnapLayer, btnSnapMargin;

    PageLayoutToolbarLegacy(SelectiveAlphaEditor ed, int canvasIdx) {
        this.ed        = ed;
        this.canvasIdx = canvasIdx;

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(AppColors.BG_TOOLBAR);
        int lBorder = (canvasIdx == 0) ? 0 : 1;
        int rBorder = (canvasIdx == 0) ? 1 : 0;
        setBorder(BorderFactory.createMatteBorder(0, lBorder, 0, rBorder, AppColors.BORDER));
        setPreferredSize(new Dimension(TOOLBAR_W, 0));
        setMaximumSize(new Dimension(TOOLBAR_W, Integer.MAX_VALUE));
        setMinimumSize(new Dimension(TOOLBAR_W, 0));

        add(buildHeader());
        add(vGap(4));
        add(buildSection("Ränder",  buildMarginsPanel()));
        add(vSep());
        add(buildSection("Seite",   buildPageTogglesPanel()));
        add(vSep());
        add(buildSection("Snap",    buildSnapPanel()));
        add(vSep());
        add(buildApplyPanel());
        add(Box.createVerticalGlue());
        setVisible(false);
    }

    PageLayout getPageLayout() { return layout; }

    private JPanel buildHeader() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(new Color(42, 42, 42));
        p.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, AppColors.BORDER));
        p.setMaximumSize(new Dimension(TOOLBAR_W, 24));
        p.setPreferredSize(new Dimension(TOOLBAR_W, 24));
        JLabel title = new JLabel("Layout", SwingConstants.CENTER);
        title.setForeground(AppColors.TEXT_MUTED);
        title.setFont(new Font("SansSerif", Font.BOLD, 10));
        p.add(title, BorderLayout.CENTER);
        JLabel closeBtn = new JLabel("×");
        closeBtn.setForeground(AppColors.TEXT_MUTED);
        closeBtn.setFont(new Font("SansSerif", Font.BOLD, 13));
        closeBtn.setBorder(BorderFactory.createEmptyBorder(2, 3, 2, 3));
        closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeBtn.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                setVisible(false);
                ed.galleryWrapper.revalidate();
                ed.galleryWrapper.repaint();
            }
            @Override public void mouseEntered(java.awt.event.MouseEvent e) { closeBtn.setForeground(Color.WHITE); }
            @Override public void mouseExited (java.awt.event.MouseEvent e) { closeBtn.setForeground(AppColors.TEXT_MUTED); }
        });
        p.add(closeBtn, BorderLayout.EAST);
        return p;
    }

    private JPanel buildSection(String label, JPanel content) {
        JPanel s = new JPanel();
        s.setLayout(new BoxLayout(s, BoxLayout.Y_AXIS));
        s.setOpaque(false);
        s.setMaximumSize(new Dimension(TOOLBAR_W, Integer.MAX_VALUE));
        JLabel lbl = new JLabel(label);
        lbl.setForeground(AppColors.TEXT_MUTED);
        lbl.setFont(new Font("SansSerif", Font.PLAIN, 9));
        lbl.setBorder(BorderFactory.createEmptyBorder(2, 6, 0, 0));
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        s.add(lbl);
        content.setAlignmentX(Component.LEFT_ALIGNMENT);
        s.add(content);
        return s;
    }

    private JPanel buildMarginsPanel() {
        JPanel p = new JPanel(new GridLayout(4, 2, 2, 2));
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(2, 4, 4, 4));
        p.setMaximumSize(new Dimension(TOOLBAR_W, 84));
        spLeft   = mmSpinner(layout.marginLeft);
        spRight  = mmSpinner(layout.marginRight);
        spTop    = mmSpinner(layout.marginTop);
        spBottom = mmSpinner(layout.marginBottom);
        spLeft  .addChangeListener(e -> layout.marginLeft   = spinVal(spLeft));
        spRight .addChangeListener(e -> layout.marginRight  = spinVal(spRight));
        spTop   .addChangeListener(e -> layout.marginTop    = spinVal(spTop));
        spBottom.addChangeListener(e -> layout.marginBottom = spinVal(spBottom));
        p.add(mLabel("L:")); p.add(spLeft);
        p.add(mLabel("R:")); p.add(spRight);
        p.add(mLabel("T:")); p.add(spTop);
        p.add(mLabel("B:")); p.add(spBottom);
        return p;
    }

    private JPanel buildPageTogglesPanel() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(2, 4, 4, 4));
        btnHeader = sideToggle("KZ",  "Kopfzeile ein/aus");
        btnFooter = sideToggle("FZ",  "Fußzeile ein/aus");
        btnPageNr = sideToggle(" # ", "Seitenzahl ein/aus");
        btnHeader.addActionListener(e -> layout.headerVisible     = btnHeader.isSelected());
        btnFooter.addActionListener(e -> layout.footerVisible     = btnFooter.isSelected());
        btnPageNr.addActionListener(e -> layout.pageNumberVisible = btnPageNr.isSelected());
        for (JToggleButton btn : new JToggleButton[]{ btnHeader, btnFooter, btnPageNr }) {
            btn.setAlignmentX(Component.LEFT_ALIGNMENT); p.add(btn); p.add(vGap(2));
        }
        return p;
    }

    private JPanel buildSnapPanel() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(2, 4, 4, 4));
        btnSnapNone   = sideToggle("Aus",   "Kein Einrasten");
        btnSnapLayer  = sideToggle("Layer", "An anderen Layern einrasten");
        btnSnapMargin = sideToggle("Rand",  "An Seitenrändern einrasten");
        btnSnapNone.setSelected(true);
        btnSnapNone.setBackground(AppColors.ACCENT_ACTIVE);
        ButtonGroup grp = new ButtonGroup();
        grp.add(btnSnapNone); grp.add(btnSnapLayer); grp.add(btnSnapMargin);
        btnSnapNone  .addActionListener(e -> { layout.snapMode = PageLayout.SnapMode.NONE;           refreshSnap(); });
        btnSnapLayer .addActionListener(e -> { layout.snapMode = PageLayout.SnapMode.SNAP_TO_LAYER;  refreshSnap(); });
        btnSnapMargin.addActionListener(e -> { layout.snapMode = PageLayout.SnapMode.SNAP_TO_MARGIN; refreshSnap(); });
        for (JToggleButton btn : new JToggleButton[]{ btnSnapNone, btnSnapLayer, btnSnapMargin }) {
            btn.setAlignmentX(Component.LEFT_ALIGNMENT); p.add(btn); p.add(vGap(2));
        }
        return p;
    }

    private void refreshSnap() {
        btnSnapNone  .setBackground(layout.snapMode == PageLayout.SnapMode.NONE           ? AppColors.ACCENT_ACTIVE : AppColors.BTN_BG);
        btnSnapLayer .setBackground(layout.snapMode == PageLayout.SnapMode.SNAP_TO_LAYER  ? AppColors.ACCENT_ACTIVE : AppColors.BTN_BG);
        btnSnapMargin.setBackground(layout.snapMode == PageLayout.SnapMode.SNAP_TO_MARGIN ? AppColors.ACCENT_ACTIVE : AppColors.BTN_BG);
    }

    private JPanel buildApplyPanel() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 4));
        p.setOpaque(false);
        p.setMaximumSize(new Dimension(TOOLBAR_W, 34));
        JButton btn = UIComponentFactory.buildButton("Anwenden", AppColors.ACCENT, AppColors.ACCENT_HOVER);
        btn.setForeground(Color.WHITE);
        btn.setPreferredSize(new Dimension(TOOLBAR_W - 10, 22));
        btn.addActionListener(e -> applyToCurrentPage());
        p.add(btn);
        return p;
    }

    private void applyToCurrentPage() {
        CanvasInstance c = ed.ci(canvasIdx);
        if (c.workingImage == null) return;
        int w = c.workingImage.getWidth(), h = c.workingImage.getHeight();
        int mL = layout.marginLeftPx(), mR = layout.marginRightPx();
        int mT = layout.marginTopPx(),  mB = layout.marginBottomPx();
        ed.pushUndo(canvasIdx);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.drawImage(c.workingImage, 0, 0, null);
        g2.setColor(new Color(0, 120, 220, 180));
        float[] dash = { 6f, 4f };
        g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, dash, 0f));
        g2.drawRect(mL, mT, w - mL - mR, h - mT - mB);
        if (layout.headerVisible) {
            int hh = Math.max(4, mT / 2);
            g2.setStroke(new BasicStroke(1f));
            g2.setColor(new Color(0, 80, 180, 50));
            g2.fillRect(mL, mT - hh, w - mL - mR, hh);
        }
        if (layout.footerVisible) {
            int fh = Math.max(4, mB / 2);
            g2.setStroke(new BasicStroke(1f));
            g2.setColor(new Color(0, 80, 180, 50));
            g2.fillRect(mL, h - mB, w - mL - mR, fh);
        }
        if (layout.pageNumberVisible) {
            int fs = Math.max(8, mB / 4);
            g2.setColor(new Color(0, 80, 180, 160));
            g2.setFont(new Font("SansSerif", Font.PLAIN, fs));
            g2.drawString("N°", w - mR - fs * 2, h - mB + fs);
        }
        g2.dispose();
        c.workingImage = img;
        c.canvasPanel.repaint();
        ed.layoutController.markDirty(canvasIdx);
    }

    private JSpinner mmSpinner(int value) {
        JSpinner sp = new JSpinner(new SpinnerNumberModel(value, 0, 150, 1));
        sp.setPreferredSize(new Dimension(38, 18));
        sp.setMaximumSize(new Dimension(38, 18));
        JComponent editor = sp.getEditor();
        if (editor instanceof JSpinner.DefaultEditor de) {
            JTextComponent tf = de.getTextField();
            tf.setBackground(AppColors.BTN_BG); tf.setForeground(AppColors.TEXT);
            tf.setFont(new Font("SansSerif", Font.PLAIN, 10));
            if (tf instanceof JTextField jtf) jtf.setHorizontalAlignment(SwingConstants.CENTER);
        }
        sp.setBackground(AppColors.BTN_BG);
        return sp;
    }

    private static int spinVal(JSpinner sp) {
        Object v = sp.getValue(); return (v instanceof Number n) ? n.intValue() : 0;
    }

    private JToggleButton sideToggle(String text, String tooltip) {
        JToggleButton btn = new JToggleButton(text);
        btn.setFont(new Font("SansSerif", Font.PLAIN, 10));
        btn.setForeground(AppColors.TEXT); btn.setBackground(AppColors.BTN_BG);
        btn.setOpaque(true); btn.setContentAreaFilled(true); btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createLineBorder(AppColors.BORDER));
        btn.setPreferredSize(new Dimension(TOOLBAR_W - 8, 20));
        btn.setMaximumSize(new Dimension(TOOLBAR_W - 8, 20));
        btn.setToolTipText(tooltip);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addItemListener(e -> btn.setBackground(btn.isSelected() ? AppColors.ACCENT_ACTIVE : AppColors.BTN_BG));
        return btn;
    }

    private JLabel mLabel(String text) {
        JLabel l = new JLabel(text); l.setForeground(AppColors.TEXT_MUTED);
        l.setFont(new Font("SansSerif", Font.PLAIN, 10)); return l;
    }

    private static Component vGap(int h) { return Box.createRigidArea(new Dimension(0, h)); }

    private static Component vSep() {
        JPanel sep = new JPanel(); sep.setBackground(AppColors.BORDER);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        sep.setPreferredSize(new Dimension(1, 1));
        JPanel w = new JPanel(); w.setOpaque(false);
        w.setMaximumSize(new Dimension(Integer.MAX_VALUE, 7));
        w.setLayout(new BoxLayout(w, BoxLayout.Y_AXIS));
        w.add(Box.createVerticalStrut(3)); w.add(sep); w.add(Box.createVerticalStrut(3));
        return w;
    }
}
