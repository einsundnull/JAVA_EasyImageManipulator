package paint;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import javax.swing.*;
import javax.swing.event.ChangeListener;

/**
 * Paint toolbar – a JPanel docked at BorderLayout.SOUTH of the main frame.
 *
 * Layout (single scrollable horizontal strip, MS-Paint style):
 *
 *  ┌──────────────────────────────────────────────────────────────────────┐
 *  │ [✏][▓][╱][○][□][⌫][✦][⬚] │ ●──3  α──255 │ Fill▾ Shape▾ │        │
 *  │ [Pri/Sec Swatch] │ [██████████████ 28-color palette ██████████████] │
 *  │ [✂][⎘][⎗] │ [⊞ Raster][⌇ Lineal]                                  │
 *  └──────────────────────────────────────────────────────────────────────┘
 *
 * Visibility: hidden by default; shown only when Paint mode is active.
 * The toolbar uses a JScrollPane so it works on narrow screens too.
 */
public class PaintToolbar extends JPanel {
    // console.log("### PaintToolbar.java ###");

    // ── Constants ─────────────────────────────────────────────────────────────
    /** All tool / action buttons are this size (set globally here). */
    public static final int BTN_SIZE    = 50;
    /** Each palette swatch */
    public static final int SWATCH_W    = 22;
    public static final int SWATCH_H    = 22;
    /** Number of palette columns (2 rows) */
    private static final int PAL_COLS   = 14;
    /** Gap between items */
    private static final int GAP        = 3;
    /** Total toolbar height: two swatch rows + padding */
    public static final int TOOLBAR_H   = BTN_SIZE + 18;

    // ── MS-Paint-style 28-color palette ───────────────────────────────────────
    private static final Color[] PALETTE = {
        // Row 1
        Color.BLACK,             new Color(128,   0,   0),
        new Color(  0, 128,   0),new Color(  0,   0, 128),
        new Color(128, 128,   0),new Color(  0, 128, 128),
        new Color(128,   0, 128),new Color(128, 128, 128),
        new Color(255, 128,   0),Color.RED,
        new Color(  0, 255,   0),Color.BLUE,
        Color.YELLOW,            Color.CYAN,
        // Row 2
        Color.WHITE,             new Color(192, 192, 192),
        new Color(255, 128, 128),new Color(128, 255, 128),
        new Color(128, 128, 255),new Color(255, 255, 128),
        new Color(128, 255, 255),new Color(255, 128, 255),
        new Color( 64,  64,  64),new Color(255, 165,   0),
        new Color(128,   0,  64),new Color(  0,  64, 128),
        new Color(173, 216, 230),new Color(144, 238, 144),
    };

    // ── Callback interface ────────────────────────────────────────────────────
    public interface Callbacks {
        void onToolChanged(PaintEngine.Tool tool);
        void onColorChanged(Color primary, Color secondary);
        void onStrokeChanged(int width);
        void onFillModeChanged(PaintEngine.FillMode mode);
        void onBrushShapeChanged(PaintEngine.BrushShape shape);
        void onCut();
        void onCopy();
        void onPaste();
        void onToggleGrid(boolean show);
        void onToggleRuler(boolean show);
        BufferedImage getWorkingImage();
    }

    // ── State ─────────────────────────────────────────────────────────────────
    private final Callbacks           cb;
    private PaintEngine.Tool          activeTool     = PaintEngine.Tool.PENCIL;
    private Color                     primaryColor   = Color.BLACK;
    private Color                     secondaryColor = Color.WHITE;
    private int                       strokeWidth    = 3;
    private PaintEngine.FillMode      fillMode       = PaintEngine.FillMode.SOLID;
    private PaintEngine.BrushShape    brushShape     = PaintEngine.BrushShape.ROUND;

    // ── UI refs ───────────────────────────────────────────────────────────────
    private JLabel              colorPrimaryPreview;
    private JLabel              colorSecondaryPreview;
    private JSlider             strokeSlider;
    private JLabel              strokeLabel;
    private JSlider             alphaSlider;
    private JLabel              alphaLabel;
    private JComboBox<String>   fillModeCombo;
    private JComboBox<String>   brushShapeCombo;
    private ColorPickerPopup    colorPicker;
    private boolean             pickingSecondary = false;

    // =========================================================================
    // Constructor
    // =========================================================================
    public PaintToolbar(Window owner, Callbacks callbacks) {
        // console.log("### PaintToolbar.java constructor ###");
        this.cb = callbacks;

        setLayout(new BorderLayout());
        setBackground(AppColors.BG_TOOLBAR);
        setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, AppColors.BORDER));
        setPreferredSize(new Dimension(0, TOOLBAR_H));

        // Inner strip – all sections in one horizontal FlowLayout panel
        JPanel strip = buildStrip();

        // Wrap in a horizontal scroll pane (no vertical bar ever)
        JScrollPane scroll = new JScrollPane(strip,
                JScrollPane.VERTICAL_SCROLLBAR_NEVER,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(AppColors.BG_TOOLBAR);
        scroll.setBackground(AppColors.BG_TOOLBAR);
        // Make scroll wheel work horizontally
        scroll.getHorizontalScrollBar().setUnitIncrement(20);
        scroll.addMouseWheelListener(e -> {
            JScrollBar bar = scroll.getHorizontalScrollBar();
            bar.setValue(bar.getValue() + e.getUnitsToScroll() * 20);
        });
        add(scroll, BorderLayout.CENTER);

        // ColorPicker popup owned by the parent window
        colorPicker = new ColorPickerPopup(owner);
        colorPicker.setChangeListener(e -> {
            Color c = colorPicker.getSelectedColor();
            if (pickingSecondary) {
                secondaryColor = c;
                colorSecondaryPreview.setBackground(c);
            } else {
                primaryColor = c;
                colorPrimaryPreview.setBackground(c);
                syncAlphaSlider();
            }
            cb.onColorChanged(primaryColor, secondaryColor);
        });

        setVisible(false); // hidden until paint mode
    }

    // =========================================================================
    // Public API
    // =========================================================================
    public PaintEngine.Tool       getActiveTool()    { return activeTool; }
    public Color                  getPrimaryColor()   { return primaryColor; }
    public Color                  getSecondaryColor() { return secondaryColor; }
    public int                    getStrokeWidth()    { return strokeWidth; }
    public PaintEngine.FillMode   getFillMode()       { return fillMode; }
    public PaintEngine.BrushShape getBrushShape()     { return brushShape; }

    /** Called by eyedropper to push picked color into toolbar. */
    public void setSelectedColor(Color c) {
        // console.log("### PaintToolbar.java setSelectedColor ###");
        primaryColor = c;
        colorPrimaryPreview.setBackground(c);
        syncAlphaSlider();
        cb.onColorChanged(primaryColor, secondaryColor);
    }

    public void showToolbar() {
        // console.log("### PaintToolbar.java showToolbar ###");
        setVisible(true);
        revalidate();
        repaint();
    }

    public void hideToolbar() {
        // console.log("### PaintToolbar.java hideToolbar ###");
        setVisible(false);
        revalidate();
    }

    // =========================================================================
    // Strip builder
    // =========================================================================
    private JPanel buildStrip() {
        // console.log("### PaintToolbar.java buildStrip ###");
        JPanel strip = new JPanel();
        strip.setLayout(new BoxLayout(strip, BoxLayout.X_AXIS));
        strip.setBackground(AppColors.BG_TOOLBAR);
        strip.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

        strip.add(buildToolButtons());
        strip.add(vSep());
        strip.add(buildColorSwatches());
        strip.add(vSep());
        strip.add(buildPalette());
        strip.add(vSep());
        strip.add(buildStrokeAlpha());
        strip.add(vSep());
        strip.add(buildFillBrush());
        strip.add(vSep());
        strip.add(buildClipboard());
        strip.add(vSep());
        strip.add(buildViewToggles());
        strip.add(Box.createHorizontalGlue());

        return strip;
    }

    // ── Section: Tool buttons ─────────────────────────────────────────────────
    private JPanel buildToolButtons() {
        // console.log("### PaintToolbar.java buildToolButtons ###");
        JPanel p = hBox();
        ButtonGroup group = new ButtonGroup();

        for (PaintEngine.Tool tool : PaintEngine.Tool.values()) {
            String[] st = symbolAndTip(tool);
            JToggleButton btn = toolBtn(st[0], st[1]);
            btn.addActionListener(e -> { activeTool = tool; cb.onToolChanged(tool); });
            group.add(btn);
            p.add(btn);
            p.add(Box.createHorizontalStrut(GAP));
            if (tool == PaintEngine.Tool.PENCIL) btn.setSelected(true);
        }
        return p;
    }

    // ── Section: Primary / Secondary color swatches ───────────────────────────
    private JPanel buildColorSwatches() {
        // console.log("### PaintToolbar.java buildColorSwatches ###");
        // Overlapping layered layout: secondary behind, primary in front
        JPanel p = new JPanel(null);
        p.setOpaque(false);
        int pw = 50, ph = BTN_SIZE;
        p.setPreferredSize(new Dimension(pw, ph));
        p.setMaximumSize(new Dimension(pw, ph));
        p.setMinimumSize(new Dimension(pw, ph));

        int bigS  = 32;
        int smallS= 22;

        // Secondary (back – bottom-right)
        colorSecondaryPreview = swatchLabel(secondaryColor);
        colorSecondaryPreview.setBounds(pw - smallS - 2, ph - smallS - 2, smallS, smallS);
        colorSecondaryPreview.setToolTipText("Sekundärfarbe · Klick = Farbwähler · Rechtsklick = aus Palette");
        colorSecondaryPreview.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                pickingSecondary = true;
                colorPicker.setSelectedColor(secondaryColor);
                showColorPickerAt(colorSecondaryPreview);
            }
        });

        // Primary (front – top-left)
        colorPrimaryPreview = swatchLabel(primaryColor);
        colorPrimaryPreview.setBounds(2, (ph - bigS) / 2, bigS, bigS);
        colorPrimaryPreview.setToolTipText("Primärfarbe · Klick = Farbwähler · Linksklick aus Palette");
        colorPrimaryPreview.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                pickingSecondary = false;
                colorPicker.setSelectedColor(primaryColor);
                showColorPickerAt(colorPrimaryPreview);
            }
        });

        p.add(colorSecondaryPreview);
        p.add(colorPrimaryPreview);
        return p;
    }

    // ── Section: 28-color palette ─────────────────────────────────────────────
    private JPanel buildPalette() {
        // console.log("### PaintToolbar.java buildPalette ###");
        // Two rows of PAL_COLS swatches
        JPanel p = new JPanel(new GridLayout(2, PAL_COLS, 2, 2));
        p.setOpaque(false);
        int totalW = PAL_COLS * (SWATCH_W + 2) + 2;
        int totalH = 2 * SWATCH_H + 4;
        p.setPreferredSize(new Dimension(totalW, totalH));
        p.setMaximumSize(new Dimension(totalW, BTN_SIZE));
        p.setMinimumSize(new Dimension(totalW, totalH));

        for (Color c : PALETTE) {
            JLabel swatch = swatchLabel(c);
            swatch.setPreferredSize(new Dimension(SWATCH_W, SWATCH_H));
            swatch.setToolTipText(toHex(c));
            swatch.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    if (SwingUtilities.isRightMouseButton(e)) {
                        secondaryColor = c;
                        colorSecondaryPreview.setBackground(c);
                    } else {
                        primaryColor = withAlpha(c, alphaSlider.getValue());
                        colorPrimaryPreview.setBackground(primaryColor);
                    }
                    cb.onColorChanged(primaryColor, secondaryColor);
                }
            });
            p.add(swatch);
        }
        return p;
    }

    // ── Section: Stroke + Alpha ───────────────────────────────────────────────
    private JPanel buildStrokeAlpha() {
        // console.log("### PaintToolbar.java buildStrokeAlpha ###");
        JPanel p = new JPanel(new GridLayout(2, 3, 4, 2));
        p.setOpaque(false);
        p.setPreferredSize(new Dimension(180, BTN_SIZE));
        p.setMaximumSize(new Dimension(180, BTN_SIZE));
        p.setMinimumSize(new Dimension(180, BTN_SIZE));

        strokeSlider = styledSlider(1, 40, strokeWidth, 110);
        strokeLabel  = miniLabel(String.valueOf(strokeWidth));
        strokeSlider.addChangeListener(e -> {
            strokeWidth = strokeSlider.getValue();
            strokeLabel.setText(String.valueOf(strokeWidth));
            cb.onStrokeChanged(strokeWidth);
        });

        alphaSlider = styledSlider(0, 255, 255, 110);
        alphaLabel  = miniLabel("255");
        alphaSlider.addChangeListener(e -> {
            int a = alphaSlider.getValue();
            alphaLabel.setText(String.valueOf(a));
            primaryColor = withAlpha(primaryColor, a);
            colorPrimaryPreview.setBackground(primaryColor);
            cb.onColorChanged(primaryColor, secondaryColor);
        });

        p.add(miniLabel("● Stärke")); p.add(strokeSlider); p.add(strokeLabel);
        p.add(miniLabel("α Alpha"));  p.add(alphaSlider);  p.add(alphaLabel);
        return p;
    }

    // ── Section: Fill mode + Brush shape ─────────────────────────────────────
    private JPanel buildFillBrush() {
        // console.log("### PaintToolbar.java buildFillBrush ###");
        JPanel p = new JPanel(new GridLayout(2, 1, 0, 4));
        p.setOpaque(false);
        p.setPreferredSize(new Dimension(90, BTN_SIZE));
        p.setMaximumSize(new Dimension(90, BTN_SIZE));
        p.setMinimumSize(new Dimension(90, BTN_SIZE));

        fillModeCombo = styledCombo(new String[]{"Solid","Umriss","Verlauf"}, 86);
        fillModeCombo.addActionListener(e -> {
            fillMode = PaintEngine.FillMode.values()[fillModeCombo.getSelectedIndex()];
            cb.onFillModeChanged(fillMode);
        });

        brushShapeCombo = styledCombo(new String[]{"Rund","Eckig"}, 86);
        brushShapeCombo.addActionListener(e -> {
            brushShape = PaintEngine.BrushShape.values()[brushShapeCombo.getSelectedIndex()];
            cb.onBrushShapeChanged(brushShape);
        });

        p.add(fillModeCombo);
        p.add(brushShapeCombo);
        return p;
    }

    // ── Section: Clipboard ────────────────────────────────────────────────────
    private JPanel buildClipboard() {
        // console.log("### PaintToolbar.java buildClipboard ###");
        JPanel p = hBox();
        JButton cut   = iconBtn("✂",  "Ausschneiden (Strg+X)");
        JButton copy  = iconBtn("⎘",  "Kopieren (Strg+C)");
        JButton paste = iconBtn("⎗",  "Einfügen (Strg+V)");
        cut  .addActionListener(e -> cb.onCut());
        copy .addActionListener(e -> cb.onCopy());
        paste.addActionListener(e -> cb.onPaste());
        p.add(cut);  p.add(Box.createHorizontalStrut(GAP));
        p.add(copy); p.add(Box.createHorizontalStrut(GAP));
        p.add(paste);
        return p;
    }

    // ── Section: View toggles (grid + ruler) ──────────────────────────────────
    private JPanel buildViewToggles() {
        // console.log("### PaintToolbar.java buildViewToggles ###");
        JPanel p = hBox();
        JToggleButton grid  = toggleBtn("⊞", "Raster ein-/ausblenden");
        JToggleButton ruler = toggleBtn("⌇", "Lineal ein-/ausblenden");
        grid .addActionListener(e -> cb.onToggleGrid(grid.isSelected()));
        ruler.addActionListener(e -> cb.onToggleRuler(ruler.isSelected()));
        p.add(grid);  p.add(Box.createHorizontalStrut(GAP));
        p.add(ruler);
        return p;
    }

    // =========================================================================
    // Helpers
    // =========================================================================
    private void showColorPickerAt(JComponent anchor) {
        // console.log("### PaintToolbar.java showColorPickerAt ###");
        if (!anchor.isShowing()) return;
        Point p = anchor.getLocationOnScreen();
        int   ph = colorPicker.getHeight() > 0 ? colorPicker.getHeight() : 320;
        // Show above the toolbar (which sits at the bottom of the screen)
        colorPicker.showAt(p.x, p.y - ph - 8);
    }

    private void syncAlphaSlider() {
        // console.log("### PaintToolbar.java syncAlphaSlider ###");
        if (alphaSlider != null) {
            alphaSlider.setValue(primaryColor.getAlpha());
            alphaLabel.setText(String.valueOf(primaryColor.getAlpha()));
        }
    }

    private static Color withAlpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }

    private static String toHex(Color c) {
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }

    private String[] symbolAndTip(PaintEngine.Tool tool) {
        return switch (tool) {
            case PENCIL     -> new String[]{ "✏",  "Stift (P)"      };
            case FLOODFILL  -> new String[]{ "▓",  "Fülleimer (F)"  };
            case LINE       -> new String[]{ "╱",  "Linie (L)"      };
            case CIRCLE     -> new String[]{ "○",  "Ellipse (E)"    };
            case RECT       -> new String[]{ "□",  "Rechteck (R)"   };
            case ERASER     -> new String[]{ "⌫",  "Radierer (X)"   };
            case EYEDROPPER -> new String[]{ "✦",  "Pipette (I)"    };
            case SELECT     -> new String[]{ "⬚",  "Auswahl (S)"    };
        };
    }

    // =========================================================================
    // Widget factories (all interactive buttons: BTN_SIZE × BTN_SIZE)
    // =========================================================================

    /** Styled tool toggle button */
    private JToggleButton toolBtn(String symbol, String tooltip) {
        JToggleButton btn = new JToggleButton(symbol) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg = isSelected()            ? AppColors.BTN_ACTIVE
                         : getModel().isRollover() ? AppColors.BTN_HOVER
                         : AppColors.BTN_BG;
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                if (isSelected()) {
                    g2.setColor(AppColors.ACCENT);
                    g2.setStroke(new BasicStroke(2f));
                    g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 8, 8);
                }
                super.paintComponent(g);
            }
        };
        styleBtn(btn, symbol, tooltip);
        return btn;
    }

    /** Styled view-toggle button (grid, ruler) */
    private JToggleButton toggleBtn(String symbol, String tooltip) {
        JToggleButton btn = new JToggleButton(symbol) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(isSelected()            ? AppColors.ACCENT_ACTIVE
                           : getModel().isRollover() ? AppColors.BTN_HOVER
                           : AppColors.BTN_BG);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                super.paintComponent(g);
            }
        };
        styleBtn(btn, symbol, tooltip);
        return btn;
    }

    /** Styled action button (clipboard etc.) */
    private JButton iconBtn(String symbol, String tooltip) {
        JButton btn = new JButton(symbol) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isRollover() ? AppColors.BTN_HOVER : AppColors.BTN_BG);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                super.paintComponent(g);
            }
        };
        styleBtn(btn, symbol, tooltip);
        return btn;
    }

    /** Shared button styling – enforces BTN_SIZE × BTN_SIZE for all buttons. */
    private void styleBtn(AbstractButton btn, String symbol, String tooltip) {
        btn.setFont(new Font("SansSerif", Font.PLAIN, 18));
        btn.setForeground(AppColors.TEXT);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setOpaque(false);
        btn.setPreferredSize(new Dimension(BTN_SIZE, BTN_SIZE));
        btn.setMaximumSize(new Dimension(BTN_SIZE, BTN_SIZE));
        btn.setMinimumSize(new Dimension(BTN_SIZE, BTN_SIZE));
        btn.setToolTipText(tooltip);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private JLabel swatchLabel(Color c) {
        JLabel l = new JLabel();
        l.setOpaque(true);
        l.setBackground(c);
        l.setBorder(BorderFactory.createLineBorder(AppColors.BORDER, 1));
        l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return l;
    }

    /** Thin vertical separator */
    private Component vSep() {
        JPanel s = new JPanel();
        s.setBackground(AppColors.BORDER);
        s.setPreferredSize(new Dimension(1, BTN_SIZE - 8));
        s.setMaximumSize(new Dimension(1, BTN_SIZE - 8));
        s.setMinimumSize(new Dimension(1, BTN_SIZE - 8));
        // Wrap in a panel to get correct BoxLayout spacing
        JPanel wrapper = new JPanel();
        wrapper.setOpaque(false);
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.X_AXIS));
        wrapper.add(Box.createHorizontalStrut(6));
        wrapper.add(s);
        wrapper.add(Box.createHorizontalStrut(6));
        return wrapper;
    }

    /** Horizontal Box panel for button groups */
    private JPanel hBox() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
        p.setOpaque(false);
        p.setAlignmentY(Component.CENTER_ALIGNMENT);
        return p;
    }

    private JLabel miniLabel(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(AppColors.TEXT_MUTED);
        l.setFont(new Font("SansSerif", Font.PLAIN, 10));
        return l;
    }

    private JSlider styledSlider(int min, int max, int val, int width) {
        JSlider s = new JSlider(min, max, val);
        s.setOpaque(false);
        s.setForeground(AppColors.TEXT_MUTED);
        s.setPreferredSize(new Dimension(width, 18));
        s.setPaintTicks(false);
        s.setPaintLabels(false);
        return s;
    }

    private JComboBox<String> styledCombo(String[] items, int width) {
        JComboBox<String> cb = new JComboBox<>(items);
        cb.setBackground(AppColors.BTN_BG);
        cb.setForeground(AppColors.TEXT);
        cb.setFont(new Font("SansSerif", Font.PLAIN, 11));
        cb.setPreferredSize(new Dimension(width, 22));
        cb.setMaximumSize(new Dimension(width, 22));
        cb.setBorder(BorderFactory.createLineBorder(AppColors.BORDER));
        cb.setFocusable(false);
        return cb;
    }
}
