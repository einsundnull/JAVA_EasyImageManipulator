package com.spriteanimator.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.border.TitledBorder;

import com.spriteanimator.model.AppState;
import com.spriteanimator.model.Tile;

/**
 * Sidebar: tile list + per-tile motion settings including start-direction.
 */
public class TilePanel extends JPanel {

    private final AppState state;
    private final DefaultListModel<Tile> listModel = new DefaultListModel<>();
    private final JList<Tile> tileList;

    // Detail controls
    private final JTextField   nameField     = new JTextField(12);
    private final JComboBox<Tile.MotionType> motionCombo =
            new JComboBox<>(Tile.MotionType.values());
    private final JSlider intensityXSlider   = new JSlider(0, 20, 2);
    private final JSlider intensityYSlider   = new JSlider(0, 20, 6);
    private final JCheckBox visibleCheck     = new JCheckBox("Sichtbar", true);
    private final JButton   colorBtn         = new JButton("Farbe");
    private final JLabel    colorSwatch      = new JLabel("  ");

    // Direction buttons  ↑ → ↓ ←
    private final JButton[] dirButtons = new JButton[4];
    private static final double[] DIR_ANGLES = {
        Math.PI,        // ↑  UP    = phase PI → starts moving upward
        0,              // →  RIGHT = phase 0  → starts moving right
        0,              // ↓  DOWN  = phase 0  → starts moving down
        Math.PI,        // ←  LEFT  = phase PI → starts moving left
    };
    private static final String[] DIR_LABELS = { "↑", "→", "↓", "←" };

    private boolean updating = false;

    public TilePanel(AppState state) {
        this.state = state;
        setLayout(new BorderLayout(4, 4));
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        setPreferredSize(new Dimension(230, 600));

        // ── List ──────────────────────────────────────────────────────────────
        tileList = new JList<>(listModel);
        tileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tileList.setCellRenderer(new TileCellRenderer());
        tileList.setBackground(new Color(50, 50, 50));
        tileList.setForeground(Color.WHITE);

        tileList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting() || updating) return;
            Tile t = tileList.getSelectedValue();
            if (t != null) {
                updating = true;
                try {
                    state.setActiveTile(t);
                    populateDetail(t);
                } finally {
                    updating = false;
                }
            }
        });

        JScrollPane listScroll = new JScrollPane(tileList);
        listScroll.setPreferredSize(new Dimension(220, 180));

        JButton addBtn    = styledButton("+ Tile", new Color(60, 120, 60));
        JButton removeBtn = styledButton("- Tile", new Color(120, 60, 60));
        addBtn.addActionListener(e -> addNewTile());
        removeBtn.addActionListener(e -> removeSelected());

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        btnRow.setOpaque(false);
        btnRow.add(addBtn);
        btnRow.add(removeBtn);

        JPanel topSection = new JPanel(new BorderLayout(2, 2));
        topSection.setOpaque(false);
        topSection.setBorder(titledBorder("Tiles"));
        topSection.add(listScroll, BorderLayout.CENTER);
        topSection.add(btnRow, BorderLayout.SOUTH);
        add(topSection, BorderLayout.NORTH);
        add(buildDetailPanel(), BorderLayout.CENTER);

        // ── State listener ────────────────────────────────────────────────────
        state.addListener(() -> {
            if (updating) return;
            updating = true;
            try {
                refreshList();
                Tile active = state.getActiveTile();
                if (active != null) {
                    tileList.setSelectedValue(active, true);
                    populateDetail(active);
                }
            } finally {
                updating = false;
            }
        });

        createDefaultTiles();
    }

    // ── Default tiles ─────────────────────────────────────────────────────────

    private void createDefaultTiles() {
        Object[][] defs = {
            { "Kopf",        Tile.MotionType.SWING, 0f,  2f, Math.PI     },
            { "Torso",       Tile.MotionType.BOB,   0f,  2f, 0.0         },
            { "Arm Links",   Tile.MotionType.SWING, 2f,  4f, 0.0         },
            { "Arm Rechts",  Tile.MotionType.SWING, 2f,  4f, Math.PI     },
            { "Bein Links",  Tile.MotionType.SWING, 2f,  6f, 0.0         },
            { "Bein Rechts", Tile.MotionType.SWING, 2f,  6f, Math.PI     },
        };
        for (int i = 0; i < defs.length; i++) {
            Color c = AppState.DEFAULT_TILE_COLORS[i % AppState.DEFAULT_TILE_COLORS.length];
            state.addTile(new Tile(i + 1,
                (String) defs[i][0], c,
                (Tile.MotionType) defs[i][1],
                (float) defs[i][2],
                (float) defs[i][3],
                (double) defs[i][4]));
        }
    }

    // ── List management ───────────────────────────────────────────────────────

    private void refreshList() {
        listModel.clear();
        state.getTiles().forEach(listModel::addElement);
    }

    private void addNewTile() {
        int id = state.getTiles().size() + 1;
        Color c = AppState.DEFAULT_TILE_COLORS[(id - 1) % AppState.DEFAULT_TILE_COLORS.length];
        state.addTile(new Tile(id, "Tile " + id, c, Tile.MotionType.SWING, 2, 4, 0));
        tileList.setSelectedValue(state.getActiveTile(), true);
    }

    private void removeSelected() {
        Tile t = tileList.getSelectedValue();
        if (t != null) state.removeTile(t);
    }

    // ── Detail panel ──────────────────────────────────────────────────────────

    private JPanel buildDetailPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setOpaque(false);
        p.setBorder(titledBorder("Tile-Eigenschaften"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 3, 3, 3);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;

        // Name
        addRow(p, c, 0, "Name:", nameField);
        nameField.addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) { applyDetail(); }
        });

        // Motion type
        addRow(p, c, 1, "Bewegung:", motionCombo);
        motionCombo.addActionListener(e -> { if (!updating) applyDetail(); });

        // Intensity X
        styleSlider(intensityXSlider);
        addRow(p, c, 2, "Intensität X:", intensityXSlider);
        intensityXSlider.addChangeListener(e -> { if (!updating) applyDetail(); });

        // Intensity Y
        styleSlider(intensityYSlider);
        addRow(p, c, 3, "Intensität Y:", intensityYSlider);
        intensityYSlider.addChangeListener(e -> { if (!updating) applyDetail(); });

        // Direction buttons
        JLabel dirLabel = new JLabel("Startrichtung:");
        dirLabel.setForeground(Color.WHITE);
        c.gridx = 0; c.gridy = 4; c.gridwidth = 1;
        p.add(dirLabel, c);

        JPanel dirPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        dirPanel.setOpaque(false);
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            dirButtons[i] = new JButton(DIR_LABELS[i]);
            dirButtons[i].setFont(new Font("SansSerif", Font.PLAIN, 13));
            dirButtons[i].setPreferredSize(new Dimension(32, 28));
            dirButtons[i].setBackground(new Color(60, 60, 80));
            dirButtons[i].setForeground(Color.WHITE);
            dirButtons[i].setFocusPainted(false);
            dirButtons[i].setBorderPainted(true);
            dirButtons[i].addActionListener(e -> {
                Tile t = state.getActiveTile();
                if (t != null) {
                    t.setStartAngle(DIR_ANGLES[idx]);
                    highlightDirButton(idx);
                    state.fireChanged();
                }
            });
            dirPanel.add(dirButtons[i]);
        }
        c.gridx = 1; c.gridy = 4;
        p.add(dirPanel, c);

        // Visible
        c.gridx = 0; c.gridy = 5; c.gridwidth = 2;
        visibleCheck.setOpaque(false);
        visibleCheck.setForeground(Color.WHITE);
        visibleCheck.addActionListener(e -> { if (!updating) applyDetail(); });
        p.add(visibleCheck, c);

        // Color
        colorSwatch.setOpaque(true);
        colorSwatch.setPreferredSize(new Dimension(28, 20));
        colorSwatch.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        colorBtn.setBackground(new Color(60, 60, 80));
        colorBtn.setForeground(Color.WHITE);
        colorBtn.setFocusPainted(false);
        colorBtn.addActionListener(e -> pickColor());

        JPanel colorRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        colorRow.setOpaque(false);
        colorRow.add(colorSwatch);
        colorRow.add(colorBtn);
        c.gridx = 0; c.gridy = 6; c.gridwidth = 2;
        p.add(colorRow, c);

        return p;
    }

    private void addRow(JPanel p, GridBagConstraints c, int row, String lbl, JComponent field) {
        c.gridx = 0; c.gridy = row; c.gridwidth = 1; c.weightx = 0;
        JLabel l = new JLabel(lbl); l.setForeground(Color.WHITE);
        p.add(l, c);
        c.gridx = 1; c.weightx = 1;
        p.add(field, c);
    }

    private void styleSlider(JSlider s) {
        s.setOpaque(false);
        s.setMajorTickSpacing(5);
        s.setPaintTicks(true);
    }

    private void populateDetail(Tile t) {
        updating = true;
        nameField.setText(t.getName());
        motionCombo.setSelectedItem(t.getMotionType());
        intensityXSlider.setValue((int) t.getIntensityX());
        intensityYSlider.setValue((int) t.getIntensityY());
        visibleCheck.setSelected(t.isVisible());
        colorSwatch.setBackground(t.getMaskColor());
        // highlight closest direction button
        double a = t.getStartAngle() % (2 * Math.PI);
        if (a < 0) a += 2 * Math.PI;
        int closest = (a < Math.PI / 2 || a >= 3 * Math.PI / 2) ? 1 : 3; // right or left
        highlightDirButton(closest);
        updating = false;
    }

    private void applyDetail() {
        Tile t = state.getActiveTile();
        if (t == null || updating) return;
        t.setName(nameField.getText().trim());
        t.setMotionType((Tile.MotionType) motionCombo.getSelectedItem());
        t.setIntensityX(intensityXSlider.getValue());
        t.setIntensityY(intensityYSlider.getValue());
        t.setVisible(visibleCheck.isSelected());
        state.fireChanged();
    }

    private void pickColor() {
        Tile t = state.getActiveTile();
        if (t == null) return;
        Color chosen = JColorChooser.showDialog(this, "Masken-Farbe wählen", t.getMaskColor());
        if (chosen != null) {
            t.setMaskColor(chosen);
            colorSwatch.setBackground(chosen);
            state.fireChanged();
        }
    }

    private void highlightDirButton(int activeIdx) {
        for (int i = 0; i < dirButtons.length; i++) {
            dirButtons[i].setBackground(i == activeIdx
                ? new Color(60, 120, 200)
                : new Color(60, 60, 80));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private TitledBorder titledBorder(String title) {
        TitledBorder b = BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(80, 80, 80)), title);
        b.setTitleColor(Color.LIGHT_GRAY);
        return b;
    }

    private JButton styledButton(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        return btn;
    }

    // ── Cell renderer ─────────────────────────────────────────────────────────

    private static class TileCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean selected, boolean focus) {
            super.getListCellRendererComponent(list, value, index, selected, focus);
            if (value instanceof Tile t) {
                setText(t.getName());
                setIcon(new ColorIcon(t.getMaskColor(), 14));
                setBackground(selected ? new Color(70, 100, 140) : new Color(50, 50, 50));
                setForeground(Color.WHITE);
                setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
            }
            return this;
        }
    }

    private record ColorIcon(Color color, int size) implements Icon {
        @Override public void paintIcon(Component c, Graphics g, int x, int y) {
            g.setColor(color);
            g.fillRect(x + 1, y + 1, size - 2, size - 2);
            g.setColor(Color.DARK_GRAY);
            g.drawRect(x, y, size - 1, size - 1);
        }
        @Override public int getIconWidth()  { return size; }
        @Override public int getIconHeight() { return size; }
    }
}
