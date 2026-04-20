package paint;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Window;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSlider;
import javax.swing.JToggleButton;

/**
 * Floating panel with all eight magic-wand tools and their shared options.
 * Toggled on/off via the wand icon on the main toolbar. All wand tool buttons
 * stay in sync with the main toolbar's tool-button registry.
 */
public class WandPanel extends JDialog {

    private final PaintToolbar toolbar;

    private JSlider       tolSlider;
    private JLabel        tolLabel;
    private JSlider       widthSlider;
    private JLabel        widthLabel;
    private JToggleButton closedBtn;
    private JRadioButton  srcSecondary;
    private JRadioButton  srcClicked;
    private JRadioButton  srcSurrounding;

    public WandPanel(Window owner, PaintToolbar toolbar) {
        super(owner, "Zauberstäbe", ModalityType.MODELESS);
        this.toolbar = toolbar;
        setResizable(false);
        setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        root.setBackground(AppColors.BG_TOOLBAR);
        root.add(buildToolGrid(),   BorderLayout.NORTH);
        root.add(buildOptionsPane(), BorderLayout.CENTER);
        setContentPane(root);
        pack();
        setLocationRelativeTo(owner);
    }

    private JPanel buildToolGrid() {
        JPanel p = new JPanel(new GridLayout(3, 4, 6, 6));
        p.setOpaque(false);
        p.setBorder(BorderFactory.createTitledBorder("Werkzeuge"));

        for (PaintEngine.Tool t : new PaintEngine.Tool[]{
                PaintEngine.Tool.WAND_I,
                PaintEngine.Tool.WAND_II,
                PaintEngine.Tool.WAND_III,
                PaintEngine.Tool.WAND_IV,
                PaintEngine.Tool.WAND_REPLACE_OUTER,
                PaintEngine.Tool.WAND_REPLACE_INNER,
                PaintEngine.Tool.WAND_AA_OUTER,
                PaintEngine.Tool.WAND_AA_INNER,
                PaintEngine.Tool.CUT_COLOR,
                PaintEngine.Tool.CUT_UNTIL_COLOR,
                PaintEngine.Tool.CUT_SAME_COLOR
        }) {
            JToggleButton btn = toolbar.buildToolButton(t);
            p.add(btn);
        }
        return p;
    }

    private JPanel buildOptionsPane() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new javax.swing.BoxLayout(p, javax.swing.BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createTitledBorder("Optionen"));

        p.add(buildTolerance());
        p.add(buildBandWidth());
        p.add(buildClosedToggle());
        p.add(buildColorSource());
        return p;
    }

    private JPanel buildTolerance() {
        JPanel p = row("Toleranz (%)");
        tolSlider = toolbar.styledSlider(0, 100, toolbar.getWandTolerance(), 160);
        tolSlider.setToolTipText("Flood-Fill-Toleranz in % – bestimmt die Region, auf die der Zauberstab wirkt");
        tolLabel  = miniLabel(toolbar.getWandTolerance() + "%");
        tolSlider.addChangeListener(e -> {
            toolbar.setWandTolerance(tolSlider.getValue());
            tolLabel.setText(toolbar.getWandTolerance() + "%");
        });
        p.add(tolSlider);
        p.add(tolLabel);
        return p;
    }

    private JPanel buildBandWidth() {
        JPanel p = row("Ring-Breite (px)");
        widthSlider = toolbar.styledSlider(1, 50, toolbar.getReplaceBandWidth(), 160);
        widthSlider.setToolTipText("Breite des Ring-Bands in Pixeln – gilt für Replace Outer/Inner und AA Outer/Inner");
        widthLabel  = miniLabel(toolbar.getReplaceBandWidth() + "px");
        widthSlider.addChangeListener(e -> {
            toolbar.setReplaceBandWidth(widthSlider.getValue());
            widthLabel.setText(toolbar.getReplaceBandWidth() + "px");
        });
        p.add(widthSlider);
        p.add(widthLabel);
        return p;
    }

    private JPanel buildClosedToggle() {
        JPanel p = row("Rand");
        closedBtn = toolbar.toggleBtn(
                toolbar.isReplaceBandClosed() ? "◯ Closed" : "◯ Open",
                "Closed: 8-Nachbarn (watertight, kein Durchstoß). Open: 4-Nachbarn (diagonale Öffnungen, n-Pixel-Überlapp).");
        closedBtn.setFont(new Font("SansSerif", Font.BOLD, 11));
        closedBtn.setSelected(toolbar.isReplaceBandClosed());
        closedBtn.addActionListener(e -> {
            toolbar.setReplaceBandClosed(closedBtn.isSelected());
            closedBtn.setText(closedBtn.isSelected() ? "◯ Closed" : "◯ Open");
        });
        p.add(closedBtn);
        return p;
    }

    private JPanel buildColorSource() {
        JPanel p = row("Farbquelle");
        srcSecondary   = radio("Sekundärfarbe",
                "Ring wird mit der Sekundärfarbe aus dem Farbwähler gefüllt");
        srcClicked     = radio("Angeklickte Farbe",
                "Ring wird mit der Farbe des angeklickten Pixels gefüllt");
        srcSurrounding = radio("Umgebungsfarbe",
                "Ring wird mit dem Durchschnitt der Farbe AUF DER GEGENÜBERLIEGENDEN Seite der Grenze gefüllt");

        ButtonGroup grp = new ButtonGroup();
        grp.add(srcSecondary);
        grp.add(srcClicked);
        grp.add(srcSurrounding);

        switch (toolbar.getWandColorSource()) {
            case SECONDARY   -> srcSecondary.setSelected(true);
            case CLICKED     -> srcClicked.setSelected(true);
            case SURROUNDING -> srcSurrounding.setSelected(true);
        }

        srcSecondary.addActionListener(e -> toolbar.setWandColorSource(PaintEngine.WandColorSource.SECONDARY));
        srcClicked.addActionListener(e -> toolbar.setWandColorSource(PaintEngine.WandColorSource.CLICKED));
        srcSurrounding.addActionListener(e -> toolbar.setWandColorSource(PaintEngine.WandColorSource.SURROUNDING));

        JPanel stack = new JPanel();
        stack.setOpaque(false);
        stack.setLayout(new javax.swing.BoxLayout(stack, javax.swing.BoxLayout.Y_AXIS));
        stack.add(srcSecondary);
        stack.add(srcClicked);
        stack.add(srcSurrounding);
        p.add(stack);
        return p;
    }

    /** Called after setters on PaintToolbar to push new state into the widgets. */
    public void syncFromToolbar() {
        if (tolSlider != null && tolSlider.getValue() != toolbar.getWandTolerance()) {
            tolSlider.setValue(toolbar.getWandTolerance());
            tolLabel.setText(toolbar.getWandTolerance() + "%");
        }
        if (widthSlider != null && widthSlider.getValue() != toolbar.getReplaceBandWidth()) {
            widthSlider.setValue(toolbar.getReplaceBandWidth());
            widthLabel.setText(toolbar.getReplaceBandWidth() + "px");
        }
        if (closedBtn != null && closedBtn.isSelected() != toolbar.isReplaceBandClosed()) {
            closedBtn.setSelected(toolbar.isReplaceBandClosed());
            closedBtn.setText(toolbar.isReplaceBandClosed() ? "◯ Closed" : "◯ Open");
        }
        if (srcSecondary != null) switch (toolbar.getWandColorSource()) {
            case SECONDARY   -> srcSecondary.setSelected(true);
            case CLICKED     -> srcClicked.setSelected(true);
            case SURROUNDING -> srcSurrounding.setSelected(true);
        }
    }

    private JPanel row(String label) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel l = new JLabel(label);
        l.setForeground(AppColors.TEXT);
        l.setPreferredSize(new Dimension(130, 22));
        l.setFont(new Font("SansSerif", Font.PLAIN, 12));
        p.add(l);
        return p;
    }

    private JLabel miniLabel(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(AppColors.TEXT_MUTED);
        l.setFont(new Font("SansSerif", Font.PLAIN, 11));
        return l;
    }

    private JRadioButton radio(String text, String tip) {
        JRadioButton rb = new JRadioButton(text);
        rb.setToolTipText(tip);
        rb.setOpaque(false);
        rb.setForeground(AppColors.TEXT);
        rb.setFont(new Font("SansSerif", Font.PLAIN, 12));
        return rb;
    }

    @SuppressWarnings("unused")
    private static Color withAlpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }
}
