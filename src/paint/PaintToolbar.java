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
import java.awt.Insets;
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
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
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

    /**
     * Knopfmaß im {@link WandPanel}-Raster.
     * <p>Breiter und höher als {@link #BTN_SIZE}, weil dort Grundform,
     * Varianten-Abzeichen <i>und</i> Beschriftung nebeneinander Platz finden
     * müssen. Das Raster ist ein eigenes Fenster — der Platz kostet nichts.
     */
    public static final int WAND_BTN_W = 66;
    public static final int WAND_BTN_H = 64;

    /**
     * Breite der Werkzeugknöpfe der Hauptleiste — <b>breiter als hoch</b>.
     * <p>Die Höhe bleibt {@link #BTN_SIZE}, damit {@link #TOOLBAR_H}
     * unverändert bleibt; die Breite richtet sich nach der längsten
     * Beschriftung („Rad. Farbe", „Farbtausch" — je zehn Zeichen). Bei 50 px
     * standen dort „Rad. Fa…" und „Farbtau…", und eine abgeschnittene
     * Beschriftung erklärt so wenig wie gar keine. Die Leiste ist waagerecht
     * scrollbar, die zusätzliche Breite kostet also nichts.
     */
    public static final int TOOL_BTN_W = 58;

    /**
     * Randloser Innenabstand der Knöpfe.
     * <p><b>Nicht weglassen:</b> Swing gibt einem {@code JButton} von sich aus
     * {@code Insets(2,14,2,14)}. Bei 50 px Knopfkante bleiben davon 22 px für
     * die Beschriftung übrig — „Stift" wurde damit als „S…" gezeichnet. Der
     * Knopf malt seinen Hintergrund ohnehin selbst, ein Rand ist also nicht
     * nur unnötig, sondern schädlich. Eine geteilte Konstante, weil sonst je
     * Knopf ein Objekt entstünde.
     */
    private static final Insets BTN_INSETS = new Insets(0, 0, 0, 0);
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
        void onRotateDeg(double deg);
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
    private int                       wandTolerance  = 20;  // 0-100 %

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
    private JToggleButton aaBtn;
    private JSlider       wandTolSlider;
    private JLabel        wandTolLabel;
    private JSlider       replaceBandSlider;
    private JLabel        replaceBandLabel;
    private JToggleButton replaceClosedBtn;

    // ── Replace-wand config state ─────────────────────────────────────────────
    private int     replaceBandWidth = 1;    // pixels
    private boolean replaceBandClosed = true;
    private PaintEngine.WandColorSource wandColorSource = PaintEngine.WandColorSource.SECONDARY;

    // ── Wand panel (floating window) ──────────────────────────────────────────
    private WandPanel wandPanel;
    /** Knopf „Stäbe" — von {@link #setWandPanelVisible(boolean)} nachgeführt. */
    private JToggleButton wandPanelBtn;

    /**
     * Farbquelle der Werkzeug-Icons — liest den <b>Live-Zustand</b> der Leiste.
     *
     * <p>Fülleimer, Pipette und die beiden Farbradierer zeigen die Farbe, mit
     * der sie arbeiten. Deshalb wird hier bewusst kein Wert kopiert: die
     * Methoden greifen bei jeder Zeichnung auf {@code primaryColor} bzw.
     * {@code secondaryColor} zu. Ein mitgeführtes Schattenfeld würde beim
     * zweiten Bedienweg (Palette gegen Farbwähler) auseinanderlaufen —
     * dieselbe Falle wie bei den Einstellungen (§31).
     *
     * <p>Damit die Icons das auch <i>zeigen</i>, ruft jede Farbänderung
     * {@link #refreshColorIcons()}.
     */
    private final PaintIcons.PaintColors iconColors = new PaintIcons.PaintColors() {
        @Override public Color primary()   { return primaryColor;   }
        @Override public Color secondary() { return secondaryColor; }
    };

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
        installMiddleMouseDragPan(scroll, strip);
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
            fireColorChanged();
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
    public int                    getWandTolerance()  { return wandTolerance; }

    public void setSelectedColor(Color c) {
        primaryColor = c;
        colorPrimaryPreview.setBackground(c);
        syncAlphaSlider();
        fireColorChanged();
    }

    // Setter für Settings-Restore – aktualisieren immer auch die UI-Widgets

    public void setAntialiasing(boolean aa) {
        antialias = aa;
        if (aaBtn != null) aaBtn.setSelected(aa);
        cb.onAntialiasingChanged(aa);
    }

    public void setPrimaryColor(Color c) {
        primaryColor = c;
        if (colorPrimaryPreview != null) colorPrimaryPreview.setBackground(c);
        syncAlphaSlider();
        fireColorChanged();
    }

    public void setSecondaryColor(Color c) {
        secondaryColor = c;
        if (colorSecondaryPreview != null) colorSecondaryPreview.setBackground(c);
        fireColorChanged();
    }

    public void setStrokeWidth(int w) {
        strokeWidth = Math.max(1, w);
        if (strokeSlider != null) { strokeSlider.setValue(strokeWidth); strokeLabel.setText(String.valueOf(strokeWidth)); }
        cb.onStrokeChanged(strokeWidth);
    }

    public void setActiveTool(String toolName) {
        try {
            setActiveTool(PaintEngine.Tool.valueOf(toolName));
        } catch (IllegalArgumentException e) {
            System.err.println("[WARN] Unbekanntes Tool: " + toolName);
        }
    }

    public void setFillMode(String modeName) {
        try {
            PaintEngine.FillMode m = PaintEngine.FillMode.valueOf(modeName);
            fillMode = m;
            if (fillModeCombo != null) fillModeCombo.setSelectedIndex(m.ordinal());
        } catch (IllegalArgumentException e) {
            System.err.println("[WARN] Unbekannter FillMode: " + modeName);
        }
    }

    public void setBrushShape(String shapeName) {
        try {
            PaintEngine.BrushShape s = PaintEngine.BrushShape.valueOf(shapeName);
            brushShape = s;
            if (brushShapeCombo != null) brushShapeCombo.setSelectedIndex(s.ordinal());
        } catch (IllegalArgumentException e) {
            System.err.println("[WARN] Unbekannte BrushShape: " + shapeName);
        }
    }

    public void setWandTolerance(int tol) {
        wandTolerance = Math.max(0, Math.min(100, tol));
        if (wandTolSlider != null) { wandTolSlider.setValue(wandTolerance); wandTolLabel.setText(wandTolerance + "%"); }
    }

    public int getWandTolerancePct() { return wandTolerance; }

    public int     getReplaceBandWidth()  { return replaceBandWidth; }
    public boolean isReplaceBandClosed()  { return replaceBandClosed; }
    public PaintEngine.WandColorSource getWandColorSource() { return wandColorSource; }

    public void setReplaceBandWidth(int w) {
        replaceBandWidth = Math.max(1, Math.min(50, w));
        if (replaceBandSlider != null) {
            replaceBandSlider.setValue(replaceBandWidth);
            replaceBandLabel.setText(replaceBandWidth + "px");
        }
        if (wandPanel != null) wandPanel.syncFromToolbar();
    }

    public void setReplaceBandClosed(boolean closed) {
        replaceBandClosed = closed;
        if (replaceClosedBtn != null) {
            replaceClosedBtn.setSelected(closed);
            replaceClosedBtn.setText(closed ? "◯ Closed" : "◯ Open");
        }
        if (wandPanel != null) wandPanel.syncFromToolbar();
    }

    public void setWandColorSource(PaintEngine.WandColorSource src) {
        if (src == null) return;
        wandColorSource = src;
        if (wandPanel != null) wandPanel.syncFromToolbar();
    }

    // ── Wand panel show/hide ─────────────────────────────────────────────────
    public WandPanel getWandPanel() {
        if (wandPanel == null) wandPanel = new WandPanel(SwingUtilities.getWindowAncestor(this), this);
        return wandPanel;
    }

    public void toggleWandPanel() {
        setWandPanelVisible(!getWandPanel().isVisible());
    }

    /**
     * Blendet das Zauberstab-Raster ein oder aus — <b>der einzige Weg</b>, damit
     * der Knopf „Stäbe" nie etwas anderes anzeigt als den echten Zustand.
     *
     * <p>Seit den Werkzeug-Kürzeln (2026-08-01) gibt es zwei Bedienwege: den
     * Knopf und die Taste {@code Z}. Stünde die Knopf-Synchronisierung wie
     * vorher nur im {@code ActionListener}, zeigte der Knopf nach einem
     * Tastendruck den falschen Zustand — dieselbe Falle wie bei den
     * Einstellungen (Univ. §12: aus dem Live-Zustand ableiten, nicht
     * mitführen).
     */
    public void setWandPanelVisible(boolean visible) {
        WandPanel p = getWandPanel();
        if (visible) p.syncFromToolbar();
        p.setVisible(visible);
        if (wandPanelBtn != null) wandPanelBtn.setSelected(p.isVisible());
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
        strip.add(buildWandPanelToggle());
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
        // ↩ ↪ bleiben als Zeichen: konventionell, eindeutig, überall
        // darstellbar. Sie zu ersetzen wäre ein unnötiger Schritt (Univ. §0).
        JButton undo = iconBtn(PaintIcons.glyph("↩"), "Zurück", "Rückgängig (Strg+Z)");
        JButton redo = iconBtn(PaintIcons.glyph("↪"), "Vor",    "Wiederholen (Strg+Y)");
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
            if (isWandTool(tool)) continue;   // wand tools live in the floating WandPanel
            JToggleButton btn = buildToolButton(tool);
            p.add(btn);
            p.add(Box.createHorizontalStrut(GAP));
            if (tool == PaintEngine.Tool.PENCIL) btn.setSelected(true);
        }
        return p;
    }

    /** Public so WandPanel can create identical tool buttons in its own container. */
    JToggleButton buildToolButton(PaintEngine.Tool tool) {
        String[] st = toolInfo(tool);
        boolean badged = PaintIcons.isBadged(tool);
        // Das Kürzel wird AUS DER REGISTRY GEHOLT, nie in den Tooltip getippt
        // (§25). Bis zum 2026-08-01 standen hier Klammer-Kürzel, die kein
        // Handler bediente — „(R)" drehte in Wahrheit das Bild.
        String combo = KeyBindings.comboFor(tool);
        String tip   = combo.isEmpty() ? st[1] : st[1] + "  ·  Taste " + combo;
        JToggleButton btn = toolBtn(
                PaintIcons.forTool(tool, iconColors),
                st[0], tip,
                badged ? WAND_BTN_W : TOOL_BTN_W,
                badged ? WAND_BTN_H : BTN_SIZE);
        btn.addActionListener(e -> {
            if (activeTool == tool) {
                activeTool = null;
                btn.setSelected(false);
                cb.onToolChanged(null);
                toolButtons.forEach((t, b) -> b.setSelected(false));
            } else {
                activeTool = tool;
                cb.onToolChanged(tool);
                toolButtons.forEach((t, b) -> b.setSelected(t == tool));
            }
        });
        toolButtons.put(tool, btn);
        return btn;
    }

    static boolean isWandTool(PaintEngine.Tool t) {
        return t == PaintEngine.Tool.WAND_I || t == PaintEngine.Tool.WAND_II
            || t == PaintEngine.Tool.WAND_III || t == PaintEngine.Tool.WAND_IV
            || t == PaintEngine.Tool.WAND_REPLACE_OUTER || t == PaintEngine.Tool.WAND_REPLACE_INNER
            || t == PaintEngine.Tool.WAND_AA_OUTER || t == PaintEngine.Tool.WAND_AA_INNER
            || t == PaintEngine.Tool.CUT_COLOR || t == PaintEngine.Tool.CUT_UNTIL_COLOR
            || t == PaintEngine.Tool.CUT_SAME_COLOR;
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
                    fireColorChanged();
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
            fireColorChanged();
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
        aaBtn = toggleBtn(PaintIcons.forAction(PaintIcons.Action.ANTIALIAS),
                          "Glätten", "Antialiasing ein/aus (weiche Kanten)");
        aaBtn.setSelected(true); // on by default
        aaBtn.addActionListener(e -> {
            antialias = aaBtn.isSelected();
            cb.onAntialiasingChanged(antialias);
        });
        p.add(aaBtn);
        return p;
    }

    // ── Wand tolerance slider (0-100 %) ──────────────────────────────────────
    @SuppressWarnings("unused")
    private JPanel buildWandTolerance() {
        JPanel p = new JPanel(new java.awt.GridLayout(2, 3, 4, 2));
        p.setOpaque(false);
        p.setPreferredSize(new Dimension(130, BTN_SIZE));
        p.setMaximumSize(new Dimension(130, BTN_SIZE));
        p.setMinimumSize(new Dimension(130, BTN_SIZE));

        wandTolSlider = styledSlider(0, 100, wandTolerance, 80);
        wandTolLabel  = miniLabel(wandTolerance + "%");
        wandTolSlider.addChangeListener(e -> {
            wandTolerance = wandTolSlider.getValue();
            wandTolLabel.setText(wandTolerance + "%");
        });

        p.add(miniLabel("% Abw."));
        p.add(wandTolSlider);
        p.add(wandTolLabel);
        // empty second row – keeps grid balanced
        p.add(new JLabel()); p.add(new JLabel()); p.add(new JLabel());
        return p;
    }

    // ── Wand panel toggle (opens the floating WandPanel) ─────────────────────
    private JPanel buildWandPanelToggle() {
        JPanel p = hBox();
        // Zauberstab MIT ZAHNRAD: der Knopf öffnet die Einstellungen der
        // Zauberstäbe, er ist selbst keiner. Vorher trug er dasselbe „⚡"
        // wie sechs Werkzeuge.
        JToggleButton btn = toggleBtn(PaintIcons.forAction(PaintIcons.Action.WAND_PANEL),
                "Stäbe",
                "Zauberstab-Panel ein-/ausblenden · alle Zauberstäbe, Toleranz, Band-Breite, Farbquelle");
        wandPanelBtn = btn;
        btn.addActionListener(e -> toggleWandPanel());   // synchronisiert sich selbst
        p.add(btn);
        return p;
    }

    // ── Replace-wand config (band width + closed/open toggle) ─────────────────
    @SuppressWarnings("unused")
    private JPanel buildReplaceWandConfig() {
        JPanel p = new JPanel(new GridLayout(2, 3, 4, 2));
        p.setOpaque(false);
        p.setPreferredSize(new Dimension(170, BTN_SIZE));
        p.setMaximumSize(new Dimension(170, BTN_SIZE));
        p.setMinimumSize(new Dimension(170, BTN_SIZE));

        replaceBandSlider = styledSlider(1, 50, replaceBandWidth, 90);
        replaceBandSlider.setToolTipText("Ringbreite für Replace-Outer/Inner (Pixel)");
        replaceBandLabel  = miniLabel(replaceBandWidth + "px");
        replaceBandSlider.addChangeListener(e -> {
            replaceBandWidth = replaceBandSlider.getValue();
            replaceBandLabel.setText(replaceBandWidth + "px");
        });

        JLabel legend = miniLabel("Band");
        legend.setToolTipText("Breite des Ring-Bands, das Replace-Outer / Replace-Inner überschreibt");

        replaceClosedBtn = toggleBtn("◯ Closed",
                "Closed: geschlossener Ring (floodfill-dicht). Open: 4-Nachbarn (n-Pixel Überlapp, Diagonalen offen).");
        replaceClosedBtn.setFont(new Font("SansSerif", Font.BOLD, 10));
        replaceClosedBtn.setSelected(replaceBandClosed);
        replaceClosedBtn.addActionListener(e -> {
            replaceBandClosed = replaceClosedBtn.isSelected();
            replaceClosedBtn.setText(replaceBandClosed ? "◯ Closed" : "◯ Open");
        });

        p.add(legend);
        p.add(replaceBandSlider);
        p.add(replaceBandLabel);
        p.add(new JLabel());
        p.add(replaceClosedBtn);
        p.add(new JLabel());
        return p;
    }

    // ── Transform buttons ─────────────────────────────────────────────────────
    private JPanel buildTransforms() {
        JPanel p = hBox();

        // ↔ ↕ ↺ ↻ behalten ihr Zeichen (Univ. §0). Die 45°-Drehungen, der
        // freie Winkel und das Skalieren bekommen ein gezeichnetes Symbol:
        // „↷" war von „↻" kaum zu unterscheiden, „⟳°" hatte das Gradzeichen
        // als Text angeklebt, „⤡" war ein bloßer Diagonalpfeil.
        // Beide Knöpfe „Spiegeln" zu nennen macht die Beschriftung wertlos —
        // dann unterscheidet sie wieder nur das Zeichen, und genau das war
        // der Ausgangsbefund.
        JButton flipH   = iconBtn(PaintIcons.glyph("↔"), "Waagr.",    "Horizontal spiegeln");
        JButton flipV   = iconBtn(PaintIcons.glyph("↕"), "Senkr.",    "Vertikal spiegeln");
        JButton rot90cw = iconBtn(PaintIcons.glyph("↻"), "90° ↻",     "90° im Uhrzeigersinn");
        JButton rot90cc = iconBtn(PaintIcons.glyph("↺"), "90° ↺",     "90° gegen Uhrzeigersinn");
        JButton rot45cw = iconBtn(PaintIcons.forAction(PaintIcons.Action.ROTATE_45_CW),  "45° ↻", "45° im Uhrzeigersinn");
        JButton rot45cc = iconBtn(PaintIcons.forAction(PaintIcons.Action.ROTATE_45_CCW), "45° ↺", "45° gegen Uhrzeigersinn");
        JButton rotFree = iconBtn(PaintIcons.forAction(PaintIcons.Action.ROTATE_FREE),   "Winkel", "Drehen (freier Winkel) …");
        JButton scale   = iconBtn(PaintIcons.forAction(PaintIcons.Action.SCALE),         "Skal.",  "Skalieren …");

        flipH  .addActionListener(e -> cb.onFlipHorizontal());
        flipV  .addActionListener(e -> cb.onFlipVertical());
        rot90cw.addActionListener(e -> cb.onRotateDeg(90));
        rot90cc.addActionListener(e -> cb.onRotateDeg(-90));
        rot45cw.addActionListener(e -> cb.onRotateDeg(45));
        rot45cc.addActionListener(e -> cb.onRotateDeg(-45));
        rotFree.addActionListener(e -> cb.onRotate());
        scale  .addActionListener(e -> cb.onScale());

        p.add(flipH);   p.add(Box.createHorizontalStrut(GAP));
        p.add(flipV);   p.add(Box.createHorizontalStrut(GAP));
        p.add(rot90cw); p.add(Box.createHorizontalStrut(GAP));
        p.add(rot90cc); p.add(Box.createHorizontalStrut(GAP));
        p.add(rot45cw); p.add(Box.createHorizontalStrut(GAP));
        p.add(rot45cc); p.add(Box.createHorizontalStrut(GAP));
        p.add(rotFree); p.add(Box.createHorizontalStrut(GAP));
        p.add(scale);
        return p;
    }

    // ── Clipboard ─────────────────────────────────────────────────────────────
    private JPanel buildClipboard() {
        JPanel p = hBox();
        // „⎘" und „⎗" sind außerhalb von Unicode-Tabellen unbekannt und werden
        // gezeichnet. Die Schere bleibt — sie ist eindeutig, seit die drei
        // CUT_-Werkzeuge im WandPanel ein Abzeichen tragen.
        JButton cut   = iconBtn(PaintIcons.glyph("✂"),                     "Ausschn.", "Ausschneiden (Strg+X)");
        JButton copy  = iconBtn(PaintIcons.forAction(PaintIcons.Action.COPY),  "Kopieren", "Kopieren (Strg+C)");
        JButton paste = iconBtn(PaintIcons.forAction(PaintIcons.Action.PASTE), "Einfügen", "Einfügen (Strg+V)");
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

        // „⊞" bleibt (Univ. §0). „⌇" ging: eine Wellenlinie bedeutet kein Lineal.
        JToggleButton grid  = toggleBtn(PaintIcons.glyph("⊞"), "Raster", "Raster ein-/ausblenden");
        rulerBtn = toggleBtn(PaintIcons.forAction(PaintIcons.Action.RULER), "Lineal", "Lineal ein-/ausblenden");
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

    /**
     * Beschriftung und Tooltip eines Werkzeugs — {@code {Beschriftung, Tooltip}}.
     *
     * <p>Das <b>Symbol</b> steht seit dem 2026-08-01 nicht mehr hier, sondern in
     * {@link PaintIcons}; diese Methode liefert nur noch Text. Vorher trug sie
     * beides, und dabei standen sechsmal „⚡" und dreimal „✂" nebeneinander.
     *
     * <p><b>Das Kürzel steht ebenfalls nicht hier.</b> Bis zum 2026-08-01
     * trugen die Tooltips von Hand gepflegte Klammer-Kürzel („Stift (P)"),
     * die kein Handler bediente — und {@code R} war in Wahrheit mit
     * „90° drehen" belegt. Wer dem Tooltip folgte, drehte sein Bild.
     *
     * <p>Seit dem Task <i>Werkzeug-Kürzel</i> (2026-08-01) sind die Tasten
     * echt, und {@link #buildToolButton} hängt sie über
     * {@link KeyBindings#comboFor} an den Tooltip an — <b>abgeleitet aus der
     * Registry, nicht getippt</b> (§25). Eine zweite Liste neben der Registry
     * ist die Ursache des Problems, nicht die Lösung.
     */
    private String[] toolInfo(PaintEngine.Tool tool) {
        return switch (tool) {
            case PENCIL     -> new String[]{ "Stift",    "Stift — freihändig malen" };
            case FLOODFILL  -> new String[]{ "Füllen",   "Fülleimer — zusammenhängende Fläche mit der Primärfarbe füllen" };
            case LINE       -> new String[]{ "Linie",    "Gerade Linie" };
            case CIRCLE     -> new String[]{ "Ellipse",  "Ellipse" };
            case RECT       -> new String[]{ "Rechteck", "Rechteck" };
            // Drei Radierer, drei verschiedene Wirkungen — die Beschriftung
            // muss sie trennen, nicht bloß numerieren.
            case ERASER       -> new String[]{ "Radierer",   "Radierer – Transparent · Rechtsklick = mit Sekundärfarbe radieren" };
            case ERASER_BG    -> new String[]{ "Rad. Farbe", "Radierer – Sekundärfarbe: malt mit Sekundärfarbe statt Transparent" };
            case ERASER_COLOR -> new String[]{ "Farbtausch", "Farbradierer (MS-Paint) · Ersetzt Primärfarbe durch Sekundärfarbe – andere Farben bleiben unberührt" };
            case EYEDROPPER -> new String[]{ "Pipette",  "Pipette — Farbe vom Bild aufnehmen" };
            case SELECT     -> new String[]{ "Auswahl",  "Auswahl — Rechteck aufziehen" };
            case TEXT       -> new String[]{ "Text",     "Text" };
            case PATH       -> new String[]{ "Pfad",     "Pfad — Bézierkurve mit Kontrollpunkten" };
            case FREE_PATH  -> new String[]{ "Freihand", "Freihand-Pfad" };
            case SMEAR      -> new String[]{ "Wischen",  "Verwischen" };

            case WAND_I     -> new String[]{ "Region",
                "Zauberstab I – Region anderer Farbe · Klick → Pfad um den flutgefüllten Bereich bis zur nächsten Farbgrenze" };
            case WAND_II    -> new String[]{ "bis Farbe",
                "Zauberstab II – bis Zielfarbe · Klick → Pfad, stoppt bei Sekundärfarbe" };
            case WAND_III   -> new String[]{ "Transp.",
                "Zauberstab III – Transparent · Klick → flutgefüllte Region wird alpha=0" };
            case WAND_IV    -> new String[]{ "Collapse",
                "Zauberstab IV – Inwards Collapse · Freihand-Polygon zeichnen, engt sich bis auf Inhalt zusammen" };
            case WAND_REPLACE_OUTER -> new String[]{ "Ring auß.",
                "Zauberstab Replace Outer · n-Pixel-Ring AUSSERHALB der angeklickten Fläche wird überschrieben" };
            case WAND_REPLACE_INNER -> new String[]{ "Ring inn.",
                "Zauberstab Replace Inner · n-Pixel-Ring INNERHALB der angeklickten Fläche wird überschrieben" };
            case WAND_AA_OUTER -> new String[]{ "AA außen",
                "Zauberstab AA Outer · n-Pixel-Ring AUSSERHALB wird antialiased eingeblendet (weiche Kante)" };
            case WAND_AA_INNER -> new String[]{ "AA innen",
                "Zauberstab AA Inner · n-Pixel-Ring INNERHALB wird antialiased eingeblendet (weiche Kante)" };
            case CUT_COLOR -> new String[]{ "Farbe",
                "Ausschneiden – Zielfarbe: alle Pixel die der Sekundärfarbe entsprechen werden pixelgenau transparent (global, kein Flood-Fill)" };
            case CUT_UNTIL_COLOR -> new String[]{ "bis Farbe",
                "Ausschneiden – bis Zielfarbe: Flood-Fill vom Klickpunkt, stoppt an Sekundärfarbe, schneidet die Region pixelgenau aus" };
            case CUT_SAME_COLOR -> new String[]{ "gleiche",
                "Ausschneiden – gleiche Farbe: Flood-Fill vom Klickpunkt, stoppt an jeder anderen Farbe, schneidet nur die angeklickte Farbregion aus" };
        };
    }

    // ── Widget factories ──────────────────────────────────────────────────────

    /** Werkzeug-Umschalter: Icon oben, Beschriftung darunter. */
    JToggleButton toolBtn(Icon icon, String caption, String tooltip, int w, int h) {
        JToggleButton btn = new JToggleButton(caption) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg = isSelected()            ? AppColors.BTN_ACTIVE
                         : getModel().isRollover() ? AppColors.BTN_HOVER
                         : AppColors.BTN_BG;
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), AppTheme.RADIUS_LG, AppTheme.RADIUS_LG);
                if (isSelected()) {
                    g2.setColor(AppColors.ACCENT);
                    g2.setStroke(AppTheme.STROKE_MEDIUM);
                    g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3,
                                     AppTheme.RADIUS_LG, AppTheme.RADIUS_LG);
                }
                super.paintComponent(g);
            }
        };
        styleBtn(btn, icon, tooltip, w, h);
        return btn;
    }

    /**
     * Ein/Aus-Schalter, der nur Text trägt — z. B. „◯ Closed" im
     * {@link WandPanel}.
     *
     * <p><b>Die Breite richtet sich nach der Beschriftung</b>, mindestens
     * {@link #BTN_SIZE}. Fest 50 px breit stand hier „…" statt „◯ Closed" —
     * ein Schalter, dessen Aufschrift man nicht lesen kann, ist genau das
     * Problem, das diese Umstellung beheben soll.
     */
    JToggleButton toggleBtn(String caption, String tooltip) {
        JToggleButton btn = newToggleBtn(caption);
        styleBtn(btn, null, tooltip, BTN_SIZE, BTN_SIZE);
        btn.setFont(AppTheme.FONT_SM_BOLD);
        int w = Math.max(BTN_SIZE,
                btn.getFontMetrics(btn.getFont()).stringWidth(caption) + AppTheme.PAD_XL * 2);
        Dimension d = new Dimension(w, BTN_SIZE);
        btn.setPreferredSize(d);
        btn.setMaximumSize(d);
        btn.setMinimumSize(d);
        return btn;
    }

    /** Ein/Aus-Schalter mit Icon und Beschriftung. */
    JToggleButton toggleBtn(Icon icon, String caption, String tooltip) {
        JToggleButton btn = newToggleBtn(caption);
        styleBtn(btn, icon, tooltip, BTN_SIZE, BTN_SIZE);
        return btn;
    }

    private JToggleButton newToggleBtn(String text) {
        return new JToggleButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(isSelected()            ? AppColors.ACCENT_ACTIVE
                           : getModel().isRollover() ? AppColors.BTN_HOVER
                           : AppColors.BTN_BG);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), AppTheme.RADIUS_LG, AppTheme.RADIUS_LG);
                super.paintComponent(g);
            }
        };
    }

    /** Auslöse-Knopf mit Icon und Beschriftung. */
    private JButton iconBtn(Icon icon, String caption, String tooltip) {
        JButton btn = new JButton(caption) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isRollover() ? AppColors.BTN_HOVER : AppColors.BTN_BG);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), AppTheme.RADIUS_LG, AppTheme.RADIUS_LG);
                super.paintComponent(g);
            }
        };
        styleBtn(btn, icon, tooltip, BTN_SIZE, BTN_SIZE);
        return btn;
    }

    /**
     * Gemeinsames Aussehen aller Knöpfe der Leiste.
     *
     * <p><b>Die Beschriftung steht unter dem Symbol, nicht daneben</b>
     * ({@code BOTTOM}/{@code CENTER}). Das ist der Kern der Umstellung vom
     * 2026-08-01: der Prompt lautete „ersetzen <i>oder ergänzen</i>", und in
     * einer Reihe gleichartiger Werkzeuge trägt die Beschriftung mehr zur
     * Erkennbarkeit bei als jedes Symbol. Der Platz war vorhanden — 50 px
     * Knopfkante fassen 30 px Symbol und eine 9-pt-Zeile.
     */
    private void styleBtn(AbstractButton btn, Icon icon, String tooltip, int w, int h) {
        if (icon != null) btn.setIcon(icon);
        btn.setFont(AppTheme.FONT_XS);
        btn.setForeground(AppColors.TEXT);
        btn.setVerticalTextPosition(SwingConstants.BOTTOM);
        btn.setHorizontalTextPosition(SwingConstants.CENTER);
        btn.setVerticalAlignment(SwingConstants.CENTER);
        btn.setIconTextGap(1);
        btn.setMargin(BTN_INSETS);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setOpaque(false);
        btn.setPreferredSize(new Dimension(w, h));
        btn.setMaximumSize(new Dimension(w, h));
        btn.setMinimumSize(new Dimension(w, h));
        btn.setToolTipText(tooltip);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    /**
     * Zeichnet die Werkzeug-Icons neu, nachdem sich eine Malfarbe geändert hat.
     *
     * <p>Nur nötig, weil {@link #iconColors} den Live-Zustand liest (Fülleimer,
     * Pipette, Farbradierer). <b>Es gibt keine Meldepflicht pro Bedienweg</b> —
     * die Methode wird an den wenigen Stellen gerufen, an denen eine Farbe
     * überhaupt gesetzt wird, und zeichnet dann pauschal alles neu. Eine
     * Liste „welches Icon hängt an welcher Farbe" wäre die nächste Stelle,
     * an der ein Fall vergessen wird (Univ. §13).
     */
    /**
     * Meldet eine geänderte Malfarbe — <b>der einzige Weg dafür</b>.
     *
     * <p>Vorher stand {@code cb.onColorChanged(...)} an sechs Stellen
     * (Farbwähler, Palette, Alpha-Regler und drei Setter für den
     * Einstellungs-Restore). Seit die Icons die Farbe <i>zeigen</i>, muss
     * jede dieser Stellen zusätzlich neu zeichnen lassen. Ein Trichter statt
     * sechs Aufrufpaaren: sonst ist die nächste hinzukommende Stelle genau
     * die, an der das Neuzeichnen vergessen wird.
     */
    private void fireColorChanged() {
        cb.onColorChanged(primaryColor, secondaryColor);
        refreshColorIcons();
    }

    private void refreshColorIcons() {
        toolButtons.values().forEach(AbstractButton::repaint);
        if (wandPanel != null) wandPanel.repaint();
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

    JPanel hBox() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
        p.setOpaque(false);
        p.setAlignmentY(Component.CENTER_ALIGNMENT);
        return p;
    }

    JLabel miniLabel(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(AppColors.TEXT_MUTED);
        l.setFont(new Font("SansSerif", Font.PLAIN, 10));
        return l;
    }

    JSlider styledSlider(int min, int max, int val, int width) {
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

    // =========================================================================
    // Middle-mouse-drag pan (fallback for broken mouse wheels)
    // =========================================================================

    /**
     * Installs a middle-mouse-button drag listener on the strip (and all its
     * descendants) that horizontally scrolls the enclosing JScrollPane.
     * Child buttons/sliders only react to the left mouse button, so middle-
     * click doesn't conflict with their own listeners.
     */
    private void installMiddleMouseDragPan(JScrollPane scroll, Component strip) {
        final Point[] dragStartInScroll = { null };
        final int[]   startValue        = { 0 };
        final Cursor  panCursor         = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR);

        MouseAdapter panAdapter = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (!SwingUtilities.isMiddleMouseButton(e)) return;
                Component src = (Component) e.getSource();
                dragStartInScroll[0] = SwingUtilities.convertPoint(src, e.getPoint(), scroll);
                startValue[0]        = scroll.getHorizontalScrollBar().getValue();
                scroll.setCursor(panCursor);
                e.consume();
            }
            @Override public void mouseDragged(MouseEvent e) {
                if (dragStartInScroll[0] == null) return;
                Component src = (Component) e.getSource();
                Point cur = SwingUtilities.convertPoint(src, e.getPoint(), scroll);
                int dx = dragStartInScroll[0].x - cur.x;
                JScrollBar bar = scroll.getHorizontalScrollBar();
                bar.setValue(startValue[0] + dx);
                e.consume();
            }
            @Override public void mouseReleased(MouseEvent e) {
                if (!SwingUtilities.isMiddleMouseButton(e)) return;
                dragStartInScroll[0] = null;
                scroll.setCursor(Cursor.getDefaultCursor());
            }
        };
        attachRecursive(strip, panAdapter);
    }

    private static void attachRecursive(Component c, MouseAdapter a) {
        c.addMouseListener(a);
        c.addMouseMotionListener(a);
        if (c instanceof java.awt.Container parent) {
            for (Component child : parent.getComponents()) attachRecursive(child, a);
        }
    }
}
