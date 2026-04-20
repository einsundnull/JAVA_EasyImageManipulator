package paint;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.ComponentOrientation;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JScrollBar;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Sidebar panel showing an editable list of TranslationMap cards.
 * Each card displays textI and textII with inline editing and per-field TTS.
 * Multiple independent instances can be created for different languages.
 * Extends BaseSidebarPanel for consistent header, scroll pane and theming.
 */
class TranslationMapListPanel extends BaseSidebarPanel {

    /** Called by owner when this panel requests removal. */
    @FunctionalInterface
    interface CloseCallback { void onClose(TranslationMapListPanel p); }

    // ── State ─────────────────────────────────────────────────────────────────

    private String         language;      // null/"all" = all languages
    private String         ttsLangII;     // TTS language for textII (textI uses map.language())
    private Color          bgColor;
    private final CloseCallback onClose;

    private final JPanel      cardsContainer;
    private final JScrollPane scrollPane;
    private final JPanel      header;
    private List<TranslationMap> maps = new ArrayList<>();
    private MapCardWidget     selectedCard;

    // ── Constructor ───────────────────────────────────────────────────────────

    TranslationMapListPanel(String language, Color bgColor, CloseCallback onClose) {
        this.language  = language == null || language.isBlank() ? "de" : language;
        this.bgColor   = bgColor;
        this.onClose   = onClose;
        this.ttsLangII = "en";

        setLayout(new BorderLayout(0, 0));
        setPreferredSize(new Dimension(240, 0));
        setMinimumSize(new Dimension(80, 0));

        // ── Header via BaseSidebarPanel ───────────────────────────────────────
        header = buildSidebarHeader(
                this.language,
                this::refresh,
                null,
                onClose != null ? () -> onClose.onClose(this) : null
        );
        addAddButton(header, this::addCard);
        addQuickOpenButton(header, this::pickLanguage);
        add(header, BorderLayout.NORTH);

        // ── Cards container ───────────────────────────────────────────────────
        cardsContainer = new JPanel();
        cardsContainer.setLayout(new BoxLayout(cardsContainer, BoxLayout.Y_AXIS));
        cardsContainer.setBackground(bgColor);

        scrollPane = buildSidebarScrollPane(cardsContainer);
        scrollPane.getViewport().setBackground(bgColor);
        add(scrollPane, BorderLayout.CENTER);

        // DEL → delete selected card (only when focus is not in a textarea)
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "del");
        getActionMap().put("del", new javax.swing.AbstractAction() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (!(java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager()
                        .getFocusOwner() instanceof JTextArea)) {
                    if (selectedCard != null) selectedCard.delete();
                }
            }
        });
        setFocusable(true);

        refresh();
    }

    // ── BaseSidebarPanel ──────────────────────────────────────────────────────

    @Override
    public void refresh() {
        try {
            if ("all".equalsIgnoreCase(language)) {
                maps = new ArrayList<>();
                MapManager.loadAllMaps().values().forEach(maps::addAll);
            } else {
                maps = new ArrayList<>(MapManager.loadMapsForLanguage(language).values());
            }
        } catch (IOException ex) {
            System.err.println("[TranslationMapListPanel] load: " + ex.getMessage());
            maps = new ArrayList<>();
        }
        rebuildCards();
    }

    // ── Card management ───────────────────────────────────────────────────────

    private void addCard() {
        String lang = "all".equalsIgnoreCase(language) ? "de" : language;
        TranslationMap m = new TranslationMap(
                MapManager.generateMapId(), lang, "Neu", "", "");
        try {
            MapManager.addOrUpdateMap(m);
            maps.add(m);
            rebuildCards();
            SwingUtilities.invokeLater(() -> {
                JScrollBar sb = scrollPane.getVerticalScrollBar();
                sb.setValue(sb.getMaximum());
            });
        } catch (IOException ex) {
            System.err.println("[TranslationMapListPanel] add: " + ex.getMessage());
        }
    }

    void setSelected(MapCardWidget w) {
        if (selectedCard != null && selectedCard != w) selectedCard.setHighlight(false);
        selectedCard = w;
        if (w != null) w.setHighlight(true);
    }

    private void rebuildCards() {
        cardsContainer.removeAll();
        cardsContainer.add(Box.createRigidArea(new Dimension(0, 4)));
        for (TranslationMap m : maps) {
            MapCardWidget w = new MapCardWidget(m);
            cardsContainer.add(w);
            cardsContainer.add(Box.createRigidArea(new Dimension(0, 4)));
        }
        cardsContainer.add(Box.createVerticalGlue());
        cardsContainer.revalidate();
        cardsContainer.repaint();
    }

    // ── Language picker ───────────────────────────────────────────────────────

    private void pickLanguage() {
        // Collect available languages
        List<String> langs = new ArrayList<>();
        langs.add("all");
        try {
            langs.addAll(MapManager.loadAllMaps().keySet());
        } catch (IOException ex) { /* ignore */ }

        // Add option for new language
        String newLangOpt = "[ Neue Sprache... ]";
        langs.add(newLangOpt);

        javax.swing.JComboBox<String> combo = new javax.swing.JComboBox<>(langs.toArray(new String[0]));
        combo.setSelectedItem(language);

        int res = javax.swing.JOptionPane.showConfirmDialog(
                this, combo, "Sprache wählen",
                javax.swing.JOptionPane.OK_CANCEL_OPTION,
                javax.swing.JOptionPane.PLAIN_MESSAGE);

        if (res != javax.swing.JOptionPane.OK_OPTION) return;
        String chosen = (String) combo.getSelectedItem();
        if (chosen == null) return;

        if (chosen.equals(newLangOpt)) {
            String name = javax.swing.JOptionPane.showInputDialog(
                    this, "Sprachcode (z.B. de, en, ja):", "Neue Sprache",
                    javax.swing.JOptionPane.PLAIN_MESSAGE);
            if (name == null || name.isBlank()) return;
            chosen = name.trim().toLowerCase();
        }

        language = chosen;
        // Update header title label
        for (java.awt.Component c : header.getComponents()) {
            if (c instanceof javax.swing.JLabel lbl && lbl.getText().startsWith("  ")) {
                lbl.setText("  " + language);
                break;
            }
        }
        refresh();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Color cardBg() {
        int r = Math.min(255, bgColor.getRed()   + 14);
        int g = Math.min(255, bgColor.getGreen() + 14);
        int b = Math.min(255, bgColor.getBlue()  + 14);
        return new Color(r, g, b);
    }

    private Font cardFont() {
        AppSettings s = AppSettings.getInstance();
        return new Font(s.getCardFontFamily(), Font.PLAIN, s.getCardFontSize());
    }

    private Color cardFontColor() {
        return new Color(AppSettings.getInstance().getCardFontColor());
    }

    // ── Inner: MapCardWidget ──────────────────────────────────────────────────

    class MapCardWidget extends JPanel {

        private TranslationMap map;
        private final JTextField sectionField;
        private final JTextArea  taI, taII;
        private final JButton    btnPlayI, btnPlayII;
        private final javax.swing.Timer saveTimer;

        MapCardWidget(TranslationMap m) {
            this.map = m;
            saveTimer = new javax.swing.Timer(600, e -> doSave());
            saveTimer.setRepeats(false);

            setLayout(new BorderLayout(0, 0));
            setOpaque(true);
            setBackground(cardBg());
            setAlignmentX(LEFT_ALIGNMENT);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
            applyBorder(false);

            // ── Top bar: [lang] section ················ [×] ─────────────────
            JPanel topBar = new JPanel(new BorderLayout(4, 0));
            topBar.setOpaque(false);
            topBar.setBorder(BorderFactory.createEmptyBorder(3, 6, 2, 4));

            javax.swing.JLabel langLbl = new javax.swing.JLabel("[" + m.language() + "]");
            langLbl.setFont(new Font("SansSerif", Font.PLAIN, 10));
            langLbl.setForeground(AppColors.TEXT_MUTED);

            sectionField = new JTextField(m.section());
            sectionField.setFont(new Font("SansSerif", Font.BOLD, 11));
            sectionField.setForeground(AppColors.TEXT);
            sectionField.setBackground(cardBg());
            sectionField.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
            sectionField.getDocument().addDocumentListener(doc(() -> saveTimer.restart()));

            JButton delBtn = smallBtn("×", AppColors.DANGER);
            delBtn.addActionListener(e -> delete());

            JPanel topLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
            topLeft.setOpaque(false);
            topLeft.add(langLbl);
            topLeft.add(sectionField);

            topBar.add(topLeft,  BorderLayout.CENTER);
            topBar.add(delBtn,   BorderLayout.EAST);

            // ── TextI row ─────────────────────────────────────────────────────
            taI    = makeTextArea(m.textI());
            btnPlayI = makePlayBtn();
            btnPlayI.addActionListener(e -> togglePlay(true));

            // ── TextII row ────────────────────────────────────────────────────
            taII   = makeTextArea(m.textII());
            btnPlayII = makePlayBtn();
            btnPlayII.addActionListener(e -> togglePlay(false));

            // ── Layout ───────────────────────────────────────────────────────
            JPanel body = new JPanel();
            body.setOpaque(false);
            body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
            body.add(topBar);
            body.add(sep());
            body.add(row(taI,  btnPlayI));
            body.add(sep());
            body.add(row(taII, btnPlayII));

            add(body, BorderLayout.CENTER);

            // Click → select
            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mousePressed(java.awt.event.MouseEvent e) {
                    setSelected(MapCardWidget.this);
                }
            });

            // Auto-height when width changes
            addComponentListener(new ComponentAdapter() {
                @Override public void componentResized(ComponentEvent e) { updateHeights(); }
            });
        }

        // ── Helpers ───────────────────────────────────────────────────────────

        private JTextArea makeTextArea(String text) {
            JTextArea ta = new JTextArea(text) {
                @Override public boolean getScrollableTracksViewportWidth() { return true; }
            };
            ta.setFont(cardFont());
            ta.setForeground(cardFontColor());
            ta.setCaretColor(cardFontColor());
            ta.setLineWrap(true);
            ta.setWrapStyleWord(true);
            ta.setOpaque(false);
            ta.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 4));
            ta.getDocument().addDocumentListener(doc(() -> saveTimer.restart()));
            ta.addFocusListener(new java.awt.event.FocusAdapter() {
                @Override public void focusGained(java.awt.event.FocusEvent e) {
                    setSelected(MapCardWidget.this);
                }
            });
            ta.addKeyListener(new KeyAdapter() {
                @Override public void keyPressed(KeyEvent ke) {
                    if (ke.getKeyCode() == KeyEvent.VK_DELETE && ta.getText().isEmpty()) {
                        delete(); ke.consume();
                    }
                }
            });
            return ta;
        }

        private JButton makePlayBtn() {
            JButton b = new JButton("▶");
            b.setFont(new Font("SansSerif", Font.PLAIN, 10));
            b.setForeground(AppColors.TEXT_MUTED);
            b.setBackground(cardBg());
            b.setBorder(BorderFactory.createLineBorder(AppColors.BORDER));
            b.setFocusPainted(false);
            b.setPreferredSize(new Dimension(24, 24));
            b.setMargin(new Insets(0, 0, 0, 0));
            return b;
        }

        private JButton smallBtn(String text, Color fg) {
            JButton b = new JButton(text);
            b.setFont(new Font("SansSerif", Font.BOLD, 13));
            b.setForeground(fg);
            b.setBackground(cardBg());
            b.setBorder(null);
            b.setFocusPainted(false);
            b.setMargin(new Insets(0, 3, 0, 3));
            b.setOpaque(false);
            return b;
        }

        private JPanel row(JTextArea ta, JButton btn) {
            JPanel p = new JPanel(new BorderLayout(2, 0));
            p.setOpaque(false);
            p.add(ta, BorderLayout.CENTER);
            JPanel wrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 2, 4));
            wrap.setOpaque(false);
            wrap.add(btn);
            p.add(wrap, BorderLayout.EAST);
            return p;
        }

        private JSeparator sep() {
            JSeparator s = new JSeparator();
            s.setForeground(AppColors.BORDER);
            s.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
            return s;
        }

        // ── Auto-height ───────────────────────────────────────────────────────

        private void updateHeights() {
            int w = getWidth() - 34;
            if (w <= 0) return;
            for (JTextArea ta : new JTextArea[]{ taI, taII }) {
                ta.setSize(w, Short.MAX_VALUE);
                int h = Math.max(ta.getPreferredSize().height, 28);
                ta.setPreferredSize(new Dimension(0, h));
            }
            revalidate();
            if (getParent() != null) getParent().revalidate();
        }

        // ── Persistence ───────────────────────────────────────────────────────

        private void doSave() {
            String sec = sectionField.getText().isBlank() ? "—" : sectionField.getText().trim();
            TranslationMap updated = new TranslationMap(
                    map.id(), map.language(), sec,
                    taI.getText(), taII.getText(), map.createdAt());
            updated.setModifiedTime(System.currentTimeMillis());
            map = updated;
            try { MapManager.addOrUpdateMap(map); }
            catch (IOException ex) { System.err.println("[MapCard] save: " + ex.getMessage()); }
        }

        void delete() {
            saveTimer.stop();
            try { MapManager.deleteMap(map.language(), map.id()); }
            catch (IOException ex) { System.err.println("[MapCard] del: " + ex.getMessage()); }
            maps.remove(map);
            if (selectedCard == this) selectedCard = null;
            rebuildCards();
        }

        // ── TTS ───────────────────────────────────────────────────────────────

        private void togglePlay(boolean isI) {
            String cardId = map.id() + (isI ? ":I" : ":II");
            JButton btn   = isI ? btnPlayI : btnPlayII;
            if (CardTtsPlayer.isPlaying(cardId)) {
                CardTtsPlayer.stop();
                setPlayState(btn, false);
            } else {
                String text = isI ? taI.getText() : taII.getText();
                String lang = isI ? map.language() : ttsLangII;
                CardTtsPlayer.play(cardId, text, lang,
                        () -> SwingUtilities.invokeLater(() -> setPlayState(btn, false)));
                setPlayState(btn, true);
            }
        }

        private void setPlayState(JButton btn, boolean playing) {
            btn.setText(playing ? "⏹" : "▶");
            btn.setForeground(playing ? AppColors.ACCENT : AppColors.TEXT_MUTED);
        }

        // ── Selection highlight ───────────────────────────────────────────────

        void setHighlight(boolean on) {
            applyBorder(on);
            repaint();
        }

        private void applyBorder(boolean selected) {
            if (selected) {
                setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(AppColors.ACCENT, 2),
                        BorderFactory.createEmptyBorder(2, 2, 2, 2)));
            } else {
                setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(AppColors.BORDER, 1),
                        BorderFactory.createEmptyBorder(3, 3, 3, 3)));
            }
        }

        // ── Document helper ───────────────────────────────────────────────────

        private DocumentListener doc(Runnable r) {
            return new DocumentListener() {
                public void insertUpdate(DocumentEvent e)  { r.run(); }
                public void removeUpdate(DocumentEvent e)  { r.run(); }
                public void changedUpdate(DocumentEvent e) {}
            };
        }
    }
}
