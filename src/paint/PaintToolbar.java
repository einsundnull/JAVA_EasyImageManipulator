package paint;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;

/**
 * Paint toolbar – a JPanel docked at BorderLayout.SOUTH of the main frame.
 *
 * Sections (left → right):
 *  Tools | Color swatches | 28-color palette | Stroke+Alpha | Fill+Brush |
 *  AA toggle | Transforms (FlipH, FlipV, Rotate, Scale) |
 *  Clipboard | View toggles (Grid, Ruler, Unit)
 *
 * Visibility: hidden by default; shown only when Paint mode is active.
 */
public class PaintToolbar extends JPanel {

    // ── Constants ─────────────────────────────────────────────────────────────
    public static final int BTN_SIZE  = 50;
    public static final int SWATCH_W  = 22;
    public static final int SWATCH_H  = 22;
    private static final int PAL_COLS = 14;
    private static final int GAP      = 3;
    public static final int TOOLBAR_H = BTN_SIZE + 55;

    // ── Palette ───────────────────────────────────────────────────────────────
    private static final Color[] PALETTE = {
        Color.BLACK,             new Color(128,   0,   0),
        new Color(  0, 128,   0),new Color(  0,   0, 128),
        new Color(128, 128,   0),new Color(  0, 128, 128),
        new Color(128,   0, 128),new Color(128, 128, 128),
        new Color(255, 128,   0),Color.RED,
        new Color(  0, 255,   0),Color.BLUE,
        Color.YELLOW,            Color.CYAN,
        Color.WHITE,             new Color(192, 192, 192),
        new Color(255, 128, 128),new Color(128, 255, 128),
        new Color(128, 128, 255),new Color(255, 255, 128),
        new Color(128, 255, 255),new Color(255, 128, 255),
        new Color( 64,  64,  64),new Color(255, 165,   0),
        new Color(128,   0,  64),new Color(  0,  64, 128),
        new Color(173, 216, 230),new Color(144, 238, 144),
    };

    // ── Callbacks ─────────────────────────────────────────────────────────────
    public interface Callbacks {
        void onToolChanged(PaintEngine.Tool tool);
        void onColorChanged(Color primary, Color secondary);
        void onStrokeChanged(int width);
        void onFillModeChanged(PaintEngine.FillMode mode);
        void onBrushShapeChanged(PaintEngine.BrushShape shape);
        void onAntialiasingChanged(boolean aa);
        void onCut();
        void onCopy();
        void onPaste();
        void onToggleGrid(boolean show);
        void onToggleRuler(boolean show);
        void onRulerUnitChanged(int unitIndex); // 0=PX 1=MM 2=CM 3=INCH
        void onFlipHorizontal();
        void onFlipVertical();
        void onRotate();
        void onScale();
        void onUndo();
        void onRedo();
        BufferedImage getWorkingImage();
    }

    // ── State ─────────────────────────────────────────────────────────────────
    private final Callbacks           cb;
    private PaintEngine.Tool          activeTool   = PaintEngine.Tool.PENCIL;
    private Color                     primaryColor   = Color.BLACK;
    private Color                     secondaryColor = Color.WHITE;
    private int                       strokeWidth    = 3;
    private PaintEngine.FillMode      fillMode       = PaintEngine.FillMode.SOLID;
    private PaintEngine.BrushShape    brushShape     = PaintEngine.BrushShape.ROUND;
    private boolean                   antialias      = true;

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
    private java.util.Map<PaintEngine.Tool, javax.swing.JToggleButton> toolButtons = new java.util.HashMap<>();
    private JToggleButton rulerBtn;

    // =========================================================================
    // Constructor
    // =========================================================================
    public PaintToolbar(Window owner, Callbacks callbacks) {
        this.cb = callbacks;

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

        setVisible(false);
    }

    // =========================================================================
    // Public API
    // =========================================================================
    public PaintEngine.Tool       getActiveTool()    { return activeTool; }
    public void                   setActiveTool(PaintEngine.Tool tool) {
        activeTool = tool;
        if (cb != null) cb.onToolChanged(tool);
        toolButtons.forEach((t, b) -> b.setSelected(t == tool && tool != null));
    }
    public Color                  getPrimaryColor()   { return primaryColor; }
    public Color                  getSecondaryColor() { return secondaryColor; }
    public int                    getStrokeWidth()    { return strokeWidth; }
    public PaintEngine.FillMode   getFillMode()       { return fillMode; }
    public PaintEngine.BrushShape getBrushShape()     { return brushShape; }
    public boolean                isAntialiasing()    { return antialias; }

    public void setSelectedColor(Color c) {
        primaryColor = c;
        colorPrimaryPreview.setBackground(c);
        syncAlphaSlider();
        cb.onColorChanged(primaryColor, secondaryColor);
    }

    // Setter für Settings-Restore
    public void setAntialiasing(boolean aa) {
        antialias = aa;
        // Button-Zustand synchronisieren (über Callback)
        cb.onAntialiasingChanged(aa);
    }

    public void setPrimaryColor(Color c) {
        primaryColor = c;
        colorPrimaryPreview.setBackground(c);
        syncAlphaSlider();
        cb.onColorChanged(primaryColor, secondaryColor);
    }

    public void setSecondaryColor(Color c) {
        secondaryColor = c;
        colorSecondaryPreview.setBackground(c);
        cb.onColorChanged(primaryColor, secondaryColor);
    }

    public void setStrokeWidth(int w) {
        strokeWidth = Math.max(1, w);
        cb.onStrokeChanged(strokeWidth);
    }

    public void setActiveTool(String toolName) {
        try {
            PaintEngine.Tool t = PaintEngine.Tool.valueOf(toolName);
            activeTool = t;
            cb.onToolChanged(activeTool);
        } catch (IllegalArgumentException e) {
            System.err.println("[WARN] Unbekanntes Tool: " + toolName);
        }
    }

    public void setFillMode(String modeName) {
        try {
            PaintEngine.FillMode m = PaintEngine.FillMode.valueOf(modeName);
            fillMode = m;
        } catch (IllegalArgumentException e) {
            System.err.println("[WARN] Unbekannter FillMode: " + modeName);
        }
    }

    public void setBrushShape(String shapeName) {
        try {
            PaintEngine.BrushShape s = PaintEngine.BrushShape.valueOf(shapeName);
            brushShape = s;
        } catch (IllegalArgumentException e) {
            System.err.println("[WARN] Unbekannte BrushShape: " + shapeName);
        }
    }

    public void setRulerSelected(boolean selected) { if (rulerBtn != null) rulerBtn.setSelected(selected); }
    public boolean isRulerSelected() { return rulerBtn != null && rulerBtn.isSelected(); }

    public void showToolbar() { setVisible(true); revalidate(); repaint(); }
    public void hideToolbar() { setVisible(false); revalidate(); }

    // =========================================================================
    // Strip builder
    // =========================================================================
    private JPanel buildStrip() {
        JPanel strip = new JPanel();
        strip.setLayout(new BoxLayout(strip, BoxLayout.X_AXIS));
        strip.setBackground(AppColors.BG_TOOLBAR);
        strip.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

        strip.add(buildUndoRedo());
        strip.add(vSep());
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
        strip.add(buildAntialias());
        strip.add(vSep());
        strip.add(buildTransforms());
        strip.add(vSep());
        strip.add(buildClipboard());
        strip.add(vSep());
        strip.add(buildViewToggles());
        strip.add(Box.createHorizontalGlue());

        return strip;
    }

    // ── Undo / Redo ───────────────────────────────────────────────────────────
    private JPanel buildUndoRedo() {
        JPanel p = hBox();
        JButton undo = iconBtn("↩", "Rückgängig (Strg+Z)");
        JButton redo = iconBtn("↪", "Wiederholen (Strg+Y)");
        undo.addActionListener(e -> cb.onUndo());
        redo.addActionListener(e -> cb.onRedo());
        p.add(undo);
        p.add(Box.createHorizontalStrut(GAP));
        p.add(redo);
        return p;
    }

    // ── Tool buttons ──────────────────────────────────────────────────────────
    private JPanel buildToolButtons() {
        JPanel p = hBox();
        for (PaintEngine.Tool tool : PaintEngine.Tool.values()) {
            String[] st = symbolAndTip(tool);
            JToggleButton btn = toolBtn(st[0], st[1]);
            btn.addActionListener(e -> {
                if (activeTool == tool) {
                    activeTool = null;
                    btn.setSelected(false);
                    cb.onToolChanged(null);
                } else {
                    toolButtons.forEach((t, b) -> { if (t != tool) b.setSelected(false); });
                    activeTool = tool;
                    btn.setSelected(true);
                    cb.onToolChanged(tool);
                }
            });
            toolButtons.put(tool, btn);
            p.add(btn);
            p.add(Box.createHorizontalStrut(GAP));
            if (tool == PaintEngine.Tool.PENCIL) btn.setSelected(true);
        }
        return p;
    }

    // ── Color swatches ────────────────────────────────────────────────────────
    private JPanel buildColorSwatches() {
        JPanel p = new JPanel(null);
        p.setOpaque(false);
        int pw = 50, ph = BTN_SIZE;
        p.setPreferredSize(new Dimension(pw, ph));
        p.setMaximumSize(new Dimension(pw, ph));
        p.setMinimumSize(new Dimension(pw, ph));

        int bigS = 32, smallS = 22;

        colorSecondaryPreview = swatchLabel(secondaryColor);
        colorSecondaryPreview.setBounds(pw - smallS - 2, ph - smallS - 2, smallS, smallS);
        colorSecondaryPreview.setToolTipText("Sekundärfarbe · Klick = Farbwähler");
        colorSecondaryPreview.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                pickingSecondary = true;
                colorPicker.setSelectedColor(secondaryColor);
                showColorPickerAt(colorSecondaryPreview);
            }
        });

        colorPrimaryPreview = swatchLabel(primaryColor);
        colorPrimaryPreview.setBounds(2, (ph - bigS) / 2, bigS, bigS);
        colorPrimaryPreview.setToolTipText("Primärfarbe · Klick = Farbwähler");
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

    // ── 28-color palette ─────────────────────────────────────────────────────
    private JPanel buildPalette() {
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

    // ── Stroke + Alpha ────────────────────────────────────────────────────────
    private JPanel buildStrokeAlpha() {
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

        p.add(miniLabel("* Staerke")); p.add(strokeSlider); p.add(strokeLabel);
        p.add(miniLabel("a Alpha"));  p.add(alphaSlider);  p.add(alphaLabel);
        return p;
    }

    // ── Fill mode + Brush shape ───────────────────────────────────────────────
    private JPanel buildFillBrush() {
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

    // ── Antialiasing toggle ───────────────────────────────────────────────────
    private JPanel buildAntialias() {
        JPanel p = hBox();
        JToggleButton btn = toggleBtn("AA", "Antialiasing ein/aus (weiche Kanten)");
        btn.setSelected(true); // on by default
        btn.setFont(new Font("SansSerif", Font.BOLD, 11));
        btn.addActionListener(e -> {
            antialias = btn.isSelected();
            cb.onAntialiasingChanged(antialias);
        });
        p.add(btn);
        return p;
    }

    // ── Transform buttons ─────────────────────────────────────────────────────
    private JPanel buildTransforms() {
        JPanel p = hBox();

        JButton flipH  = iconBtn("↔",  "Horizontal spiegeln");
        JButton flipV  = iconBtn("↕",  "Vertikal spiegeln");
        JButton rotate = iconBtn("↺",  "Drehen …");
        JButton scale  = iconBtn("⤡",  "Skalieren …");

        flipH .addActionListener(e -> cb.onFlipHorizontal());
        flipV .addActionListener(e -> cb.onFlipVertical());
        rotate.addActionListener(e -> cb.onRotate());
        scale .addActionListener(e -> cb.onScale());

        p.add(flipH);  p.add(Box.createHorizontalStrut(GAP));
        p.add(flipV);  p.add(Box.createHorizontalStrut(GAP));
        p.add(rotate); p.add(Box.createHorizontalStrut(GAP));
        p.add(scale);
        return p;
    }

    // ── Clipboard ─────────────────────────────────────────────────────────────
    private JPanel buildClipboard() {
        JPanel p = hBox();
        JButton cut   = iconBtn("✂", "Ausschneiden (Strg+X)");
        JButton copy  = iconBtn("⎘", "Kopieren (Strg+C)");
        JButton paste = iconBtn("⎗", "Einfügen (Strg+V)");
        cut  .addActionListener(e -> cb.onCut());
        copy .addActionListener(e -> cb.onCopy());
        paste.addActionListener(e -> cb.onPaste());
        p.add(cut);  p.add(Box.createHorizontalStrut(GAP));
        p.add(copy); p.add(Box.createHorizontalStrut(GAP));
        p.add(paste);
        return p;
    }

    // ── View toggles: grid, ruler, unit ──────────────────────────────────────
    private JPanel buildViewToggles() {
        JPanel p = hBox();

        JToggleButton grid  = toggleBtn("⊞", "Raster ein-/ausblenden");
        rulerBtn = toggleBtn("⌇", "Lineal ein-/ausblenden");
        JToggleButton ruler = rulerBtn;
        grid .addActionListener(e -> cb.onToggleGrid(grid.isSelected()));
        ruler.addActionListener(e -> cb.onToggleRuler(ruler.isSelected()));

        JComboBox<String> unitCombo = styledCombo(new String[]{"px","mm","cm","in"}, 52);
        unitCombo.setToolTipText("Lineal-Einheit");
        unitCombo.addActionListener(e -> cb.onRulerUnitChanged(unitCombo.getSelectedIndex()));

        p.add(grid);  p.add(Box.createHorizontalStrut(GAP));
        p.add(ruler); p.add(Box.createHorizontalStrut(GAP));
        p.add(unitCombo);
        return p;
    }

    // =========================================================================
    // Helpers
    // =========================================================================
    private void showColorPickerAt(JComponent anchor) {
        if (!anchor.isShowing()) return;
        Point pt = anchor.getLocationOnScreen();
        int   ph = colorPicker.getHeight() > 0 ? colorPicker.getHeight() : 320;
        colorPicker.showAt(pt.x, pt.y - ph - 8);
    }

    private void syncAlphaSlider() {
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
            case PENCIL     -> new String[]{ "P", "Stift (P)"      };
            case FLOODFILL  -> new String[]{ "F", "Fuelleimer (F)" };
            case LINE       -> new String[]{ "/", "Linie (L)"      };
            case CIRCLE     -> new String[]{ "O", "Ellipse (E)"    };
            case RECT       -> new String[]{ "R", "Rechteck (R)"   };
            case ERASER     -> new String[]{ "⌫", "Radierer (X)"   };
            case EYEDROPPER -> new String[]{ "✦", "Pipette (I)"    };
            case SELECT     -> new String[]{ "⬚", "Auswahl (S)"    };
            case TEXT       -> new String[]{ "A", "Text (T)"       };
            case PATH       -> new String[]{ "≈", "Pfad (K)"       };
        };
    }

    // ── Widget factories ──────────────────────────────────────────────────────

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

    private Component vSep() {
        JPanel s = new JPanel();
        s.setBackground(AppColors.BORDER);
        s.setPreferredSize(new Dimension(1, BTN_SIZE - 8));
        s.setMaximumSize(new Dimension(1, BTN_SIZE - 8));
        s.setMinimumSize(new Dimension(1, BTN_SIZE - 8));
        JPanel w = new JPanel();
        w.setOpaque(false);
        w.setLayout(new BoxLayout(w, BoxLayout.X_AXIS));
        w.add(Box.createHorizontalStrut(6));
        w.add(s);
        w.add(Box.createHorizontalStrut(6));
        return w;
    }

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
        JComboBox<String> box = new JComboBox<>(items);
        box.setBackground(AppColors.BTN_BG);
        box.setForeground(AppColors.TEXT);
        box.setFont(new Font("SansSerif", Font.PLAIN, 11));
        box.setPreferredSize(new Dimension(width, 22));
        box.setMaximumSize(new Dimension(width, 22));
        box.setBorder(BorderFactory.createLineBorder(AppColors.BORDER));
        box.setFocusable(false);
        return box;
    }
}
