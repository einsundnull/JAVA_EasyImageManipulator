package paint;

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JToggleButton;

/**
 * Horizontal control bar for the secondary preview window.
 * Can be docked (SOUTH of secWin) or floated into its own JDialog.
 */
class SecondaryControlBar extends JPanel {

    private final SelectiveAlphaEditor      ed;
    private final SecondaryWindowController ctrl;

    private JButton       previewModeBtn;
    private JButton       canvasModeBtn;
    private JButton       alwaysOnTopBtn;
    private JToggleButton fullscreenBtn;
    private JToggleButton floatBtn;
    private CardTextOptionsPopup textOptionsPopup;

    /** Wrapper panel inside secWin that holds this bar when docked. */
    private JPanel  barHolder;
    private JDialog floatDialog;
    private boolean floating = false;

    SecondaryControlBar(SelectiveAlphaEditor ed, SecondaryWindowController ctrl) {
        this.ed   = ed;
        this.ctrl = ctrl;
        build();
    }

    void setBarHolder(JPanel holder) {
        this.barHolder = holder;
    }

    // ── Build ─────────────────────────────────────────────────────────────────

    private void build() {
        setLayout(new FlowLayout(FlowLayout.LEFT, 4, 3));
        setBackground(AppColors.BG_TOOLBAR);
        setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, AppColors.BORDER));

        // Preview mode cycle
        previewModeBtn = btn("", "Preview-Modus umschalten (F2)");
        previewModeBtn.addActionListener(e -> { ed.cyclePreviewMode(); syncState(); });
        add(previewModeBtn);

        // Snapshot refresh
        JButton snapBtn = btn("Snap", "Snapshot aktualisieren (F3)");
        snapBtn.addActionListener(e -> ed.refreshSnapshot());
        add(snapBtn);

        add(vSep());

        // Canvas display mode cycle
        canvasModeBtn = btn("", "Canvas-Anzeige umschalten (F7)");
        canvasModeBtn.addActionListener(e -> { ed.cycleCanvasDisplayMode(); syncState(); });
        add(canvasModeBtn);

        add(vSep());

        // Always-on-top cycle
        alwaysOnTopBtn = btn("", "Fenster-Ebene umschalten (F5)");
        alwaysOnTopBtn.addActionListener(e -> { ed.cycleAlwaysOnTop(); syncState(); });
        add(alwaysOnTopBtn);

        add(vSep());

        // Fullscreen toggle
        fullscreenBtn = new JToggleButton();
        style(fullscreenBtn, "Vollbild umschalten (F4)");
        fullscreenBtn.addActionListener(e -> { ed.toggleSecondaryFullscreen(); syncState(); });
        add(fullscreenBtn);

        add(vSep());

        // Apply to Canvas II
        JButton applyBtn = btn("-> C2", "Vorschau auf Canvas II anwenden (F6)");
        applyBtn.addActionListener(e -> ed.applySecondaryWindowToCanvas());
        add(applyBtn);

        add(vSep());

        // Text options
        JButton textOptsBtn = btn("Textoptionen", "Schrift, Farbe und TTS-Einstellungen für Karten");
        textOptsBtn.addActionListener(e -> showTextOptions());
        add(textOptsBtn);

        add(vSep());

        // Add a new card-list panel (left or right zone)
        JButton addListBtn = btn("Liste +", "Neue Kartenliste öffnen");
        addListBtn.addActionListener(e -> showAddPanelDialog());
        add(addListBtn);

        add(vSep());

        // Float / dock toggle
        floatBtn = new JToggleButton("Float");
        style(floatBtn, "Toolbar freistellen / andocken");
        floatBtn.addActionListener(e -> setFloating(floatBtn.isSelected()));
        add(floatBtn);

        syncState();
    }

    // ── State sync ────────────────────────────────────────────────────────────

    void syncState() {
        previewModeBtn.setText(switch (ed.secMode) {
            case SNAPSHOT      -> "Snapshot";
            case LIVE_ALL      -> "Live";
            case LIVE_ALL_EDIT -> "Live+Edit";
        });
        canvasModeBtn.setText(switch (ed.secCanvasMode) {
            case SHOW_CANVAS_I_ONLY  -> "Canvas I";
            case SHOW_CANVAS_II_ONLY -> "Canvas II";
            case SHOW_ACTIVE_CANVAS  -> "Aktiv";
        });
        alwaysOnTopBtn.setText(switch (ed.secAlwaysOnTop) {
            case TO_FRONT      -> "Vorne";
            case NORMAL        -> "Normal";
            case TO_BACKGROUND -> "Hinten";
        });
        fullscreenBtn.setSelected(ed.secFullscreen);
        fullscreenBtn.setText(ed.secFullscreen ? "Fenster" : "Vollbild");
        floatBtn.setSelected(floating);
        floatBtn.setText(floating ? "Dock" : "Float");
    }

    // ── Text options popup ────────────────────────────────────────────────────

    private void showTextOptions() {
        if (textOptionsPopup == null || !textOptionsPopup.isDisplayable()) {
            java.awt.Window win = javax.swing.SwingUtilities.getWindowAncestor(this);
            textOptionsPopup = new CardTextOptionsPopup(win, () -> ctrl.applyCardDisplaySettings());
        }
        textOptionsPopup.setVisible(true);
        textOptionsPopup.toFront();
    }

    // ── Add panel dialog ──────────────────────────────────────────────────────

    private void showAddPanelDialog() {
        // Collect available language codes from MapManager
        List<String> langs = new ArrayList<>();
        langs.add("de");
        langs.add("en");
        langs.add("ja");
        langs.add("all");
        try {
            MapManager.loadAllMaps().keySet().stream()
                    .filter(k -> !langs.contains(k))
                    .forEach(langs::add);
        } catch (IOException ex) { /* use defaults */ }
        langs.add("[ Neue... ]");

        JComboBox<String> langCombo = new JComboBox<>(langs.toArray(new String[0]));
        langCombo.setSelectedItem("de");

        String[] sides = { "Links", "Rechts" };
        JComboBox<String> sideCombo = new JComboBox<>(sides);

        JPanel grid = new JPanel(new GridLayout(0, 2, 8, 6));
        grid.add(new JLabel("Sprache:")); grid.add(langCombo);
        grid.add(new JLabel("Seite:"));   grid.add(sideCombo);

        java.awt.Window owner = javax.swing.SwingUtilities.getWindowAncestor(this);
        int res = JOptionPane.showConfirmDialog(
                owner, grid, "Neue Kartenliste",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);
        if (res != JOptionPane.OK_OPTION) return;

        String lang = (String) langCombo.getSelectedItem();
        if (lang == null) return;
        if (lang.equals("[ Neue... ]")) {
            lang = JOptionPane.showInputDialog(
                    owner, "Sprachcode (z.B. de, en, ja):", "Neue Sprache",
                    JOptionPane.PLAIN_MESSAGE);
            if (lang == null || lang.isBlank()) return;
            lang = lang.trim().toLowerCase();
        }

        boolean leftSide = sideCombo.getSelectedIndex() == 0;
        ctrl.addPanel(lang, leftSide);
    }

    // ── Float / dock ──────────────────────────────────────────────────────────

    private void setFloating(boolean nowFloating) {
        if (nowFloating == floating) return;
        floating = nowFloating;

        if (nowFloating) {
            if (barHolder != null) {
                barHolder.remove(this);
                barHolder.revalidate();
                barHolder.repaint();
            }
            if (floatDialog == null) createFloatDialog();
            floatDialog.getContentPane().removeAll();
            floatDialog.getContentPane().add(this, BorderLayout.CENTER);
            floatDialog.pack();
            floatDialog.setLocationRelativeTo(ed.secWin);
            floatDialog.setVisible(true);
        } else {
            if (floatDialog != null) {
                floatDialog.getContentPane().remove(this);
                floatDialog.setVisible(false);
            }
            if (barHolder != null) {
                barHolder.add(this);
                barHolder.revalidate();
                barHolder.repaint();
            }
        }
        syncState();
    }

    private void createFloatDialog() {
        floatDialog = new JDialog(ed.secWin, "Vorschau-Steuerung", Dialog.ModalityType.MODELESS);
        floatDialog.setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
        floatDialog.setResizable(false);
        floatDialog.getContentPane().setLayout(new BorderLayout());
        floatDialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                floating = true; // trick setFloating into acting
                setFloating(false);
            }
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private JButton btn(String text, String tip) {
        JButton b = new JButton(text);
        style(b, tip);
        return b;
    }

    private void style(AbstractButton b, String tip) {
        b.setToolTipText(tip);
        b.setFont(new Font("SansSerif", Font.PLAIN, 12));
        b.setBackground(AppColors.BTN_BG);
        b.setForeground(AppColors.TEXT);
        b.setFocusPainted(false);
        b.setMargin(new java.awt.Insets(3, 8, 3, 8));
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppColors.BORDER),
                BorderFactory.createEmptyBorder(3, 8, 3, 8)));
    }

    private JSeparator vSep() {
        JSeparator s = new JSeparator(JSeparator.VERTICAL);
        s.setPreferredSize(new Dimension(1, 20));
        s.setForeground(AppColors.BORDER);
        return s;
    }
}
