package paint;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Sidebar panel showing an editable list of TranslationMap cards.
 * Multiple independent instances can be created for different languages.
 * Header per card: [LangI▼][💬][▶] | [LangII▼][💬][▶] [×]
 * LangII is stored in the section field of TranslationMap.
 * Transliteration rows (💬) are in-memory only (not persisted).
 */
class TranslationMapListPanel extends BaseSidebarPanel {

    @FunctionalInterface
    interface CloseCallback { void onClose(TranslationMapListPanel p); }

    /** Per-card style overrides (in-memory, keyed by map ID). */
    static class CardStyle {
        String  fontFamilyI, fontFamilyII;
        Integer fontSizeI, fontSizeII;       // null = global
        Integer fontColorI, fontColorII;     // null = global
        Integer bgColor;                     // null = global
    }

    private String          language;
    private final CloseCallback onClose;

    private final JPanel      cardsContainer;
    private final JScrollPane scrollPane;
    private final JPanel      header;
    private List<TranslationMap>   maps        = new ArrayList<>();
    private List<MapCardWidget>    cardWidgets = new ArrayList<>();
    private final java.util.Map<String, CardStyle> cardStyles = new java.util.HashMap<>();
    private MapCardWidget     selectedCard;

    // Auto-font-size
    private boolean autoFontSize = false;
    private int     lastPanelWidth = 240;

    TranslationMapListPanel(String language, Color bgColor, CloseCallback onClose) {
        this.language = (language == null || language.isBlank()) ? "de" : language;
        this.onClose  = onClose;

        setLayout(new BorderLayout(0, 0));
        setPreferredSize(new Dimension(240, 0));
        setMinimumSize(new Dimension(80, 0));

        header = buildSidebarHeader(
                this.language, this::refresh, null,
                onClose != null ? () -> onClose.onClose(this) : null);
        addAddButton(header, this::addCard);
        addQuickOpenButton(header, this::pickLanguage);
        addAutoFontToggle(header);
        add(header, BorderLayout.NORTH);

        // Auto-font: recompute when panel width changes
        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                int w = getWidth();
                if (w > 0 && w != lastPanelWidth) {
                    lastPanelWidth = w;
                    if (autoFontSize) refreshAllFonts();
                }
            }
        });

        cardsContainer = new JPanel();
        cardsContainer.setLayout(new BoxLayout(cardsContainer, BoxLayout.Y_AXIS));
        cardsContainer.setBackground(panelBg());

        scrollPane = buildSidebarScrollPane(cardsContainer);
        scrollPane.getViewport().setBackground(panelBg());
        add(scrollPane, BorderLayout.CENTER);

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

    private void addCard() {
        AppSettings s = AppSettings.getInstance();
        String langI  = "all".equalsIgnoreCase(language) ? s.getCardTtsLanguageLeft() : language;
        String langII = s.getCardTtsLanguageRight();   // stored in section field
        TranslationMap m = new TranslationMap(MapManager.generateMapId(), langI, langII, "", "");
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
        cardWidgets.clear();
        cardsContainer.add(Box.createRigidArea(new Dimension(0, 4)));
        for (TranslationMap m : maps) {
            MapCardWidget w = new MapCardWidget(m);
            cardWidgets.add(w);
            cardsContainer.add(w);
            cardsContainer.add(Box.createRigidArea(new Dimension(0, 4)));
        }
        cardsContainer.add(Box.createVerticalGlue());
        cardsContainer.revalidate();
        cardsContainer.repaint();
    }

    private void pickLanguage() {
        // Use the standard recent-projects dialog, filtered to the maps category
        java.awt.Window win = SwingUtilities.getWindowAncestor(this);
        javax.swing.JFrame owner = (win instanceof javax.swing.JFrame jf) ? jf : null;
        if (owner == null) { pickLanguageFallback(); return; }
        try {
            Map<String, List<String>> recent = LastProjectsManager.loadAll();
            StartupDialog dlg = new StartupDialog(owner, recent,
                    StartupDialog.Mode.QUICK_OPEN, 0, LastProjectsManager.CAT_MAPS);
            dlg.setVisible(true);
            File chosen = dlg.getSelectedPath();
            if (chosen == null) return;
            // Interpret the chosen file: if it's a .json in the maps dir, its basename = language
            if (chosen.isFile() && chosen.getName().endsWith(".json")) {
                String lang = chosen.getName().replace(".json", "");
                setLanguage(lang);
            } else if (chosen.isDirectory()) {
                // directory selected — use folder name as language code hint
                setLanguage(chosen.getName().toLowerCase());
            }
        } catch (IOException ex) {
            pickLanguageFallback();
        }
    }

    private void pickLanguageFallback() {
        List<String> langs = new ArrayList<>();
        langs.add("all");
        try { langs.addAll(MapManager.loadAllMaps().keySet()); }
        catch (IOException ex) { /* ignore */ }
        langs.add("[ Neue Sprache... ]");
        javax.swing.JComboBox<String> combo =
                new javax.swing.JComboBox<>(langs.toArray(new String[0]));
        combo.setSelectedItem(language);
        int res = JOptionPane.showConfirmDialog(this, combo, "Sprache wählen",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (res != JOptionPane.OK_OPTION) return;
        String chosen = (String) combo.getSelectedItem();
        if (chosen == null) return;
        if (chosen.equals("[ Neue Sprache... ]")) {
            String name = JOptionPane.showInputDialog(this,
                    "Sprachcode (z.B. de, en, ja):", "Neue Sprache", JOptionPane.PLAIN_MESSAGE);
            if (name == null || name.isBlank()) return;
            chosen = name.trim().toLowerCase();
        }
        setLanguage(chosen);
    }

    private void setLanguage(String lang) {
        language = lang;
        for (java.awt.Component c : header.getComponents()) {
            if (c instanceof JLabel lbl && lbl.getText().startsWith("  ")) {
                lbl.setText("  " + language); break;
            }
        }
        refresh();
    }

    // ── Auto-font toggle (added to header east panel) ─────────────────────────

    private void addAutoFontToggle(JPanel hdr) {
        Object prop = hdr.getClientProperty("eastPanel");
        if (!(prop instanceof JPanel east)) return;
        javax.swing.JToggleButton btn = new javax.swing.JToggleButton("A");
        btn.setFont(new Font("SansSerif", Font.BOLD, 10));
        btn.setForeground(AppColors.TEXT_MUTED);
        btn.setBackground(new java.awt.Color(42, 42, 42));
        btn.setBorder(BorderFactory.createLineBorder(AppColors.BORDER));
        btn.setFocusPainted(false);
        btn.setMargin(new Insets(1, 3, 1, 3));
        btn.setPreferredSize(new Dimension(20, 16));
        btn.setToolTipText("Schriftgröße automatisch an Kartenbreite anpassen");
        btn.addActionListener(e -> {
            autoFontSize = btn.isSelected();
            btn.setForeground(autoFontSize ? AppColors.ACCENT : AppColors.TEXT_MUTED);
            if (autoFontSize) refreshAllFonts();
            else              refreshAllFonts(); // reset to global too
        });
        east.add(btn, 0);
        east.revalidate();
        east.repaint();
    }

    /** Recompute font size for auto-mode or revert to global, apply to all live cards. */
    private void refreshAllFonts() {
        for (MapCardWidget w : cardWidgets) w.refreshFonts();
    }

    int computeAutoFontSize() {
        if (!autoFontSize || lastPanelWidth <= 0) return AppSettings.getInstance().getCardFontSize();
        int base = AppSettings.getInstance().getCardFontSize();
        float scale = Math.max(0.35f, Math.min(3.0f, lastPanelWidth / 240f));
        return Math.max(6, Math.min(72, Math.round(base * scale)));
    }

    // ── Per-card style resolution ─────────────────────────────────────────────

    Font effectiveFont(CardStyle st, boolean isI) {
        String family = (st != null && (isI ? st.fontFamilyI : st.fontFamilyII) != null)
                ? (isI ? st.fontFamilyI : st.fontFamilyII)
                : AppSettings.getInstance().getCardFontFamily();
        Integer overSz = st != null ? (isI ? st.fontSizeI : st.fontSizeII) : null;
        int size = overSz != null ? overSz : computeAutoFontSize();
        return cjkCapableFont(family, size);
    }

    Color effectiveColor(CardStyle st, boolean isI) {
        Integer c = st != null ? (isI ? st.fontColorI : st.fontColorII) : null;
        return c != null ? new Color(c) : new Color(AppSettings.getInstance().getCardFontColor());
    }

    Color effectiveBg(CardStyle st) {
        return (st != null && st.bgColor != null) ? new Color(st.bgColor) : cardBg();
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    private Color panelBg() {
        return new Color(AppSettings.getInstance().getCardBgColor());
    }

    private Color cardBg() {
        Color base = panelBg();
        int r = Math.min(255, base.getRed()   + 14);
        int g = Math.min(255, base.getGreen() + 14);
        int b = Math.min(255, base.getBlue()  + 14);
        return new Color(r, g, b);
    }

    /**
     * Returns a font for card text that is guaranteed to display Japanese (and other CJK).
     * Tries the user-configured family first; if it cannot display U+65E5 ('日') we walk
     * through known CJK-capable families before falling back to the logical "Dialog" font
     * which always maps to a system Unicode font on Windows/Mac/Linux.
     */
    private static Font cjkCapableFont(String family, int size) {
        Font f = new Font(family, Font.PLAIN, size);
        if (f.canDisplay('\u65E5')) return f;           // configured font works
        // Try common CJK families available on Windows / macOS / Linux
        for (String cjk : new String[]{
                "MS Gothic", "Meiryo", "Yu Gothic", "MS Mincho",
                "Noto Sans CJK JP", "Noto Sans JP", "IPAGothic",
                "Hiragino Kaku Gothic ProN", "Hiragino Sans",
                "SimSun", "NSimSun", "DengXian", "FangSong"}) {
            Font candidate = new Font(cjk, Font.PLAIN, size);
            if (!candidate.getFamily().equals("Dialog") && candidate.canDisplay('\u65E5'))
                return candidate;
        }
        return new Font("Dialog", Font.PLAIN, size);   // logical font = always works
    }

    private Font cardFont() {
        AppSettings s = AppSettings.getInstance();
        return cjkCapableFont(s.getCardFontFamily(), s.getCardFontSize());
    }

    private Color cardFontColor() {
        return new Color(AppSettings.getInstance().getCardFontColor());
    }

    private List<String> availableLanguages() {
        List<String> langs = new ArrayList<>(List.of("de", "en", "ja", "fr", "es", "zh", "ko", "ru"));
        try {
            MapManager.loadAllMaps().keySet().stream()
                    .filter(k -> !langs.contains(k)).forEach(langs::add);
        } catch (IOException ex) { /* use defaults */ }
        return langs;
    }

    // ── Inner: MapCardWidget ──────────────────────────────────────────────────

    class MapCardWidget extends JPanel {

        private TranslationMap map;

        // Per-card languages (langII stored in map.section())
        private String langI;
        private String langII;

        // UI state
        private boolean showTranslitI  = false;
        private boolean showTranslitII = false;

        // Header buttons
        private final JButton       langIBtn, langIIBtn;
        private final JToggleButton translitIBtn, translitIIBtn;
        private final JButton       playIBtn, playIIBtn;

        // Text areas
        private final JTextArea taI, taII;
        private final JTextArea taTranslitI, taTranslitII;

        private final javax.swing.Timer saveTimer;

        MapCardWidget(TranslationMap m) {
            this.map  = m;
            this.langI = m.language();

            // section field repurposed: if it looks like a lang code use it, else default
            String sec = m.section();
            this.langII = (sec != null && !sec.isBlank() && sec.length() <= 10
                    && !sec.equals("Neu") && !sec.equals("—"))
                    ? sec
                    : AppSettings.getInstance().getCardTtsLanguageRight();

            saveTimer = new javax.swing.Timer(600, e -> doSave());
            saveTimer.setRepeats(false);

            setLayout(new BorderLayout(0, 0));
            setOpaque(true);
            setBackground(cardBg());
            setAlignmentX(LEFT_ALIGNMENT);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
            applyBorder(false);

            // ── Compact header row ────────────────────────────────────────────
            langIBtn = langBtn(langI);
            langIBtn.addActionListener(e -> showLangPopup(langIBtn, true));

            translitIBtn = translitBtn();
            translitIBtn.addActionListener(e -> toggleTranslit(true));

            playIBtn = playBtn();
            playIBtn.addActionListener(e -> togglePlay(true));

            langIIBtn = langBtn(langII);
            langIIBtn.addActionListener(e -> showLangPopup(langIIBtn, false));

            translitIIBtn = translitBtn();
            translitIIBtn.addActionListener(e -> toggleTranslit(false));

            playIIBtn = playBtn();
            playIIBtn.addActionListener(e -> togglePlay(false));

            JButton editBtn = new JButton("✎");
            editBtn.setFont(new Font("Dialog", Font.PLAIN, 11));
            editBtn.setForeground(AppColors.TEXT_MUTED);
            editBtn.setBackground(cardBg());
            editBtn.setBorder(null);
            editBtn.setFocusPainted(false);
            editBtn.setMargin(new Insets(0, 3, 0, 1));
            editBtn.setOpaque(false);
            editBtn.setToolTipText("Texteigenschaften dieser Karte bearbeiten");
            editBtn.addActionListener(e -> showEditPopup());

            JButton delBtn = new JButton("×");
            delBtn.setFont(new Font("SansSerif", Font.BOLD, 12));
            delBtn.setForeground(AppColors.DANGER);
            delBtn.setBackground(cardBg());
            delBtn.setBorder(null);
            delBtn.setFocusPainted(false);
            delBtn.setMargin(new Insets(0, 3, 0, 2));
            delBtn.setOpaque(false);
            delBtn.addActionListener(e -> delete());

            JLabel pipe = new JLabel(" | ");
            pipe.setFont(new Font("SansSerif", Font.PLAIN, 10));
            pipe.setForeground(AppColors.BORDER);

            JPanel leftCluster = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
            leftCluster.setOpaque(false);
            leftCluster.add(langIBtn); leftCluster.add(translitIBtn); leftCluster.add(playIBtn);
            leftCluster.add(pipe);
            leftCluster.add(langIIBtn); leftCluster.add(translitIIBtn); leftCluster.add(playIIBtn);

            JPanel rightBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            rightBtns.setOpaque(false);
            rightBtns.add(editBtn);
            rightBtns.add(delBtn);

            JPanel compactHeader = new JPanel(new BorderLayout(0, 0));
            compactHeader.setOpaque(false);
            compactHeader.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 2));
            compactHeader.add(leftCluster, BorderLayout.CENTER);
            compactHeader.add(rightBtns,   BorderLayout.EAST);

            // ── Text areas ────────────────────────────────────────────────────
            taI         = makeTA(m.textI(), true);
            taTranslitI = makeTranslitTA();
            taTranslitI.setVisible(false);

            taII         = makeTA(m.textII(), false);
            taTranslitII = makeTranslitTA();
            taTranslitII.setVisible(false);

            // ── Body layout ───────────────────────────────────────────────────
            JPanel body = new JPanel();
            body.setOpaque(false);
            body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
            body.add(compactHeader);
            body.add(makeSep());
            body.add(taI);
            body.add(taTranslitI);
            body.add(makeSep());
            body.add(taII);
            body.add(taTranslitII);

            add(body, BorderLayout.CENTER);

            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mousePressed(java.awt.event.MouseEvent e) {
                    setSelected(MapCardWidget.this);
                }
            });

            addComponentListener(new ComponentAdapter() {
                @Override public void componentResized(ComponentEvent e) {
                    // Invalidate TAs so getPreferredSize() re-measures at new width
                    taI.invalidate(); taII.invalidate();
                    taTranslitI.invalidate(); taTranslitII.invalidate();
                    // No extra revalidate needed — the resize itself triggers a full re-layout
                }
            });
        }

        // ── Language popup ────────────────────────────────────────────────────

        private void showLangPopup(JButton btn, boolean isI) {
            JPopupMenu popup = new JPopupMenu();
            for (String l : availableLanguages()) {
                JMenuItem item = new JMenuItem(l);
                item.setFont(new Font("SansSerif", Font.PLAIN, 11));
                item.addActionListener(e -> { if (isI) setLangI(l); else setLangII(l); });
                popup.add(item);
            }
            popup.addSeparator();
            JMenuItem newOpt = new JMenuItem("Neue Sprache...");
            newOpt.setFont(new Font("SansSerif", Font.PLAIN, 11));
            newOpt.addActionListener(e -> {
                java.awt.Window win = SwingUtilities.getWindowAncestor(
                        TranslationMapListPanel.this);
                String input = JOptionPane.showInputDialog(win,
                        "Sprachcode (z.B. de, en, ja):", "Neue Sprache",
                        JOptionPane.PLAIN_MESSAGE);
                if (input != null && !input.isBlank()) {
                    if (isI) setLangI(input.trim().toLowerCase());
                    else     setLangII(input.trim().toLowerCase());
                }
            });
            popup.add(newOpt);
            popup.show(btn, 0, btn.getHeight());
        }

        private void setLangI(String lang) {
            langI = lang;
            langIBtn.setText(lang);
            saveTimer.restart();
        }

        private void setLangII(String lang) {
            langII = lang;
            langIIBtn.setText(lang);
            saveTimer.restart();
        }

        // ── Transliteration toggle ─────────────────────────────────────────────

        private void toggleTranslit(boolean isI) {
            if (isI) {
                showTranslitI = !showTranslitI;
                taTranslitI.setVisible(showTranslitI);
                translitIBtn.setSelected(showTranslitI);
            } else {
                showTranslitII = !showTranslitII;
                taTranslitII.setVisible(showTranslitII);
                translitIIBtn.setSelected(showTranslitII);
            }
            updateHeights();
        }

        // ── TTS ───────────────────────────────────────────────────────────────

        private void togglePlay(boolean isI) {
            String cardId = map.id() + (isI ? ":I" : ":II");
            JButton btn   = isI ? playIBtn : playIIBtn;
            if (CardTtsPlayer.isPlaying(cardId)) {
                CardTtsPlayer.stop();
                setPlayState(btn, false);
            } else {
                String text = isI ? taI.getText() : taII.getText();
                String lang = isI ? langI : langII;
                CardTtsPlayer.play(cardId, text, lang,
                        () -> SwingUtilities.invokeLater(() -> setPlayState(btn, false)));
                setPlayState(btn, true);
            }
        }

        private void setPlayState(JButton btn, boolean playing) {
            btn.setText(playing ? "⏹" : "▶");
            btn.setForeground(playing ? AppColors.ACCENT : AppColors.TEXT_MUTED);
        }

        // ── Persistence ───────────────────────────────────────────────────────

        private void doSave() {
            // langII stored in section field
            TranslationMap updated = new TranslationMap(
                    map.id(), langI, langII, taI.getText(), taII.getText(), map.createdAt());
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

        // ── Auto-height ───────────────────────────────────────────────────────

        private void updateHeights() {
            taI.invalidate(); taII.invalidate();
            taTranslitI.invalidate(); taTranslitII.invalidate();
            invalidate();
            cardsContainer.validate();
            cardsContainer.repaint();
        }

        // ── Font refresh (called on auto-size change or after edit popup) ────────

        void refreshFonts() {
            CardStyle st = cardStyles.get(map.id());
            Font fI  = effectiveFont(st, true);
            Font fII = effectiveFont(st, false);
            Color cI  = effectiveColor(st, true);
            Color cII = effectiveColor(st, false);
            taI.setFont(fI);   taI.setForeground(cI);   taI.setCaretColor(cI);
            taII.setFont(fII); taII.setForeground(cII); taII.setCaretColor(cII);
            int transSize = Math.max(6, fI.getSize() - 2);
            taTranslitI.setFont(new Font("Dialog", Font.ITALIC, transSize));
            taTranslitII.setFont(new Font("Dialog", Font.ITALIC, transSize));
            setBackground(effectiveBg(st));
            // After setFont the View's cached metrics are stale — invalidate explicitly,
            // then use synchronous validate() so the new preferred sizes are applied
            // before the next repaint rather than after the next EDT round-trip.
            taI.invalidate(); taII.invalidate();
            taTranslitI.invalidate(); taTranslitII.invalidate();
            invalidate();
            cardsContainer.validate();   // synchronous — applies new preferred sizes immediately
            cardsContainer.repaint();
        }

        // ── Per-card edit popup ───────────────────────────────────────────────

        private void showEditPopup() {
            CardStyle st = cardStyles.computeIfAbsent(map.id(), k -> new CardStyle());
            java.awt.Window win = SwingUtilities.getWindowAncestor(TranslationMapListPanel.this);
            javax.swing.JFrame owner = (win instanceof javax.swing.JFrame jf) ? jf
                    : (win instanceof javax.swing.JDialog jd && jd.getOwner() instanceof javax.swing.JFrame f) ? f : null;

            javax.swing.JDialog dlg = new javax.swing.JDialog(owner,
                    "Karte – Texteigenschaften", false);
            dlg.setDefaultCloseOperation(javax.swing.JDialog.DISPOSE_ON_CLOSE);

            String[] allFonts = java.awt.GraphicsEnvironment
                    .getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
            String[] ttsLangs = CardTtsPlayer.availableLanguages();

            AppSettings s = AppSettings.getInstance();

            JPanel root = new JPanel(new BorderLayout(0, 8));
            root.setBackground(AppColors.BG_PANEL);
            root.setBorder(BorderFactory.createEmptyBorder(10, 12, 8, 12));

            JPanel grid = new JPanel(new java.awt.GridLayout(0, 3, 8, 6));
            grid.setOpaque(false);

            // Header row
            addGridLbl(grid, "");
            addGridLbl(grid, "Seite I");
            addGridLbl(grid, "Seite II");

            // Font family
            addGridLbl(grid, "Schriftart");
            javax.swing.JComboBox<String> famI  = fontCombo(allFonts, st.fontFamilyI,  s.getCardFontFamily());
            javax.swing.JComboBox<String> famII = fontCombo(allFonts, st.fontFamilyII, s.getCardFontFamily());
            grid.add(famI); grid.add(famII);

            // Font size
            addGridLbl(grid, "Schriftgröße");
            javax.swing.JSpinner szI  = sizeSpinner(st.fontSizeI  != null ? st.fontSizeI  : s.getCardFontSize());
            javax.swing.JSpinner szII = sizeSpinner(st.fontSizeII != null ? st.fontSizeII : s.getCardFontSize());
            grid.add(szI); grid.add(szII);

            // Font color
            addGridLbl(grid, "Schriftfarbe");
            Color defC = new Color(s.getCardFontColor());
            JButton colI  = colorSwatch(st.fontColorI  != null ? new Color(st.fontColorI)  : defC);
            JButton colII = colorSwatch(st.fontColorII != null ? new Color(st.fontColorII) : defC);
            colI.addActionListener(e -> {
                Color c = javax.swing.JColorChooser.showDialog(dlg, "Schriftfarbe I", colI.getBackground());
                if (c != null) refreshSwatch(colI, c);
            });
            colII.addActionListener(e -> {
                Color c = javax.swing.JColorChooser.showDialog(dlg, "Schriftfarbe II", colII.getBackground());
                if (c != null) refreshSwatch(colII, c);
            });
            grid.add(colI); grid.add(colII);

            // Background color (shared)
            addGridLbl(grid, "Kartenhintergrund");
            Color defBg = effectiveBg(null);
            JButton bgBtn = colorSwatch(st.bgColor != null ? new Color(st.bgColor) : defBg);
            bgBtn.addActionListener(e -> {
                Color c = javax.swing.JColorChooser.showDialog(dlg, "Kartenhintergrund", bgBtn.getBackground());
                if (c != null) refreshSwatch(bgBtn, c);
            });
            JPanel bgRow = new JPanel(new BorderLayout());
            bgRow.setOpaque(false);
            bgRow.add(bgBtn, BorderLayout.WEST);
            grid.add(bgRow); grid.add(new JPanel()); // span 2 cols

            root.add(grid, BorderLayout.CENTER);

            // Buttons
            JPanel foot = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
            foot.setOpaque(false);

            JButton resetBtn = footBtn("Zurücksetzen");
            resetBtn.addActionListener(e -> {
                cardStyles.remove(map.id());
                refreshFonts();
                dlg.dispose();
            });

            JButton okBtn = footBtn("OK");
            okBtn.addActionListener(e -> {
                st.fontFamilyI  = (String) famI.getSelectedItem();
                st.fontFamilyII = (String) famII.getSelectedItem();
                st.fontSizeI    = (Integer) szI.getValue();
                st.fontSizeII   = (Integer) szII.getValue();
                st.fontColorI   = colI.getBackground().getRGB();
                st.fontColorII  = colII.getBackground().getRGB();
                st.bgColor      = bgBtn.getBackground().getRGB();
                cardStyles.put(map.id(), st);
                refreshFonts();
                dlg.dispose();
            });

            foot.add(resetBtn);
            foot.add(okBtn);
            root.add(foot, BorderLayout.SOUTH);

            dlg.setContentPane(root);
            dlg.pack();
            dlg.setLocationRelativeTo(TranslationMapListPanel.this);
            dlg.setVisible(true);
        }

        private void addGridLbl(JPanel p, String txt) {
            JLabel l = new JLabel(txt);
            l.setFont(new Font("SansSerif", Font.PLAIN, 11));
            l.setForeground(AppColors.TEXT_MUTED);
            p.add(l);
        }
        private javax.swing.JComboBox<String> fontCombo(String[] items, String override, String def) {
            javax.swing.JComboBox<String> cb = new javax.swing.JComboBox<>(items);
            cb.setSelectedItem(override != null ? override : def);
            cb.setFont(new Font("SansSerif", Font.PLAIN, 11));
            cb.setBackground(AppColors.BTN_BG);
            cb.setForeground(AppColors.TEXT);
            return cb;
        }
        private javax.swing.JSpinner sizeSpinner(int val) {
            javax.swing.JSpinner sp = new javax.swing.JSpinner(
                    new javax.swing.SpinnerNumberModel(val, 6, 72, 1));
            sp.setBackground(AppColors.BTN_BG);
            sp.getEditor().getComponent(0).setBackground(AppColors.BTN_BG);
            ((javax.swing.JSpinner.DefaultEditor) sp.getEditor())
                    .getTextField().setForeground(AppColors.TEXT);
            return sp;
        }
        private JButton colorSwatch(Color c) {
            JButton b = new JButton();
            b.setPreferredSize(new Dimension(60, 20));
            b.setOpaque(true); b.setFocusPainted(false);
            b.setBorder(BorderFactory.createLineBorder(AppColors.BORDER));
            refreshSwatch(b, c);
            return b;
        }
        private void refreshSwatch(JButton b, Color c) {
            b.setBackground(c);
            b.setForeground(c.getRed() + c.getGreen() + c.getBlue() > 382 ? Color.BLACK : Color.WHITE);
            b.setText(c.getRed() + "," + c.getGreen() + "," + c.getBlue());
        }
        private JButton footBtn(String text) {
            JButton b = new JButton(text);
            b.setFont(new Font("SansSerif", Font.PLAIN, 11));
            b.setBackground(AppColors.BTN_BG);
            b.setForeground(AppColors.TEXT);
            b.setFocusPainted(false);
            return b;
        }

        // ── Selection highlight ───────────────────────────────────────────────

        void setHighlight(boolean on) {
            applyBorder(on); repaint();
        }

        private void applyBorder(boolean sel) {
            if (sel) {
                setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(AppColors.ACCENT, 2),
                        BorderFactory.createEmptyBorder(2, 2, 2, 2)));
            } else {
                setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(AppColors.BORDER, 1),
                        BorderFactory.createEmptyBorder(3, 3, 3, 3)));
            }
        }

        // ── Widget factory helpers ────────────────────────────────────────────

        private JButton langBtn(String lang) {
            JButton b = new JButton(lang);
            b.setFont(new Font("SansSerif", Font.BOLD, 10));
            b.setForeground(AppColors.TEXT_MUTED);
            b.setBackground(cardBg());
            b.setBorder(BorderFactory.createLineBorder(AppColors.BORDER));
            b.setFocusPainted(false);
            b.setMargin(new Insets(1, 3, 1, 3));
            b.setPreferredSize(new Dimension(30, 18));
            return b;
        }

        private JToggleButton translitBtn() {
            JToggleButton b = new JToggleButton("\uD83D\uDCAC"); // 💬
            b.setFont(new Font("Dialog", Font.PLAIN, 10));
            b.setForeground(AppColors.TEXT_MUTED);
            b.setBackground(cardBg());
            b.setBorder(BorderFactory.createLineBorder(AppColors.BORDER));
            b.setFocusPainted(false);
            b.setMargin(new Insets(1, 2, 1, 2));
            b.setPreferredSize(new Dimension(22, 18));
            return b;
        }

        private JButton playBtn() {
            JButton b = new JButton("▶");
            b.setFont(new Font("SansSerif", Font.PLAIN, 10));
            b.setForeground(AppColors.TEXT_MUTED);
            b.setBackground(cardBg());
            b.setBorder(BorderFactory.createLineBorder(AppColors.BORDER));
            b.setFocusPainted(false);
            b.setMargin(new Insets(1, 2, 1, 2));
            b.setPreferredSize(new Dimension(22, 18));
            return b;
        }

        private JTextArea makeTA(String text, boolean isI) {
            // Override getPreferredSize to always compute height from actual rendered width.
            // This is the only reliable way to auto-size a line-wrapping JTextArea in BoxLayout.
            JTextArea ta = new JTextArea(text) {
                @Override
                public Dimension getPreferredSize() {
                    int w = getWidth();
                    if (w <= 0) return new Dimension(100, 40);
                    // Use the View hierarchy directly — bypasses Swing's component-size cache
                    // and always picks up the currently set font metrics.
                    try {
                        javax.swing.text.View v = getUI().getRootView(this);
                        v.setSize(w, Short.MAX_VALUE);
                        int h = (int) Math.ceil(v.getPreferredSpan(javax.swing.text.View.Y_AXIS));
                        java.awt.Insets ins = getInsets();
                        return new Dimension(w, Math.max(h + ins.top + ins.bottom, 24));
                    } catch (Exception ex) {
                        setSize(w, Short.MAX_VALUE);
                        return new Dimension(w, Math.max(super.getPreferredSize().height, 24));
                    }
                }
                @Override
                public Dimension getMaximumSize() {
                    return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
                }
                @Override
                public boolean getScrollableTracksViewportWidth() { return true; }
            };
            CardStyle st = cardStyles.get(map.id());
            Font ef = effectiveFont(st, isI);
            Color ec = effectiveColor(st, isI);
            ta.setFont(ef);
            ta.setForeground(ec);
            ta.setCaretColor(ec);
            ta.setLineWrap(true);
            ta.setWrapStyleWord(true);
            ta.setOpaque(false);
            AppSettings _s = AppSettings.getInstance();
            ta.setBorder(BorderFactory.createEmptyBorder(
                    _s.getCardPaddingTop(), _s.getCardPaddingLeft(),
                    _s.getCardPaddingBottom(), _s.getCardPaddingRight()));
            ta.setAlignmentX(LEFT_ALIGNMENT);
            ta.getDocument().addDocumentListener(doc(() -> {
                saveTimer.restart();
                ta.invalidate();
                SwingUtilities.invokeLater(() -> updateHeights());
            }));
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

        private JTextArea makeTranslitTA() {
            JTextArea ta = new JTextArea() {
                @Override public boolean getScrollableTracksViewportWidth() { return true; }
            };
            int sz = Math.max(9, AppSettings.getInstance().getCardFontSize() - 2);
            ta.setFont(new Font("Dialog", Font.ITALIC, sz));
            ta.setForeground(AppColors.TEXT_MUTED);
            ta.setCaretColor(AppColors.TEXT_MUTED);
            ta.setLineWrap(true);
            ta.setWrapStyleWord(true);
            ta.setOpaque(false);
            ta.setBorder(BorderFactory.createEmptyBorder(2, 12, 2, 6));
            ta.setAlignmentX(LEFT_ALIGNMENT);
            return ta;
        }

        private JSeparator makeSep() {
            JSeparator s = new JSeparator();
            s.setForeground(AppColors.BORDER);
            s.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
            return s;
        }

        private DocumentListener doc(Runnable r) {
            return new DocumentListener() {
                public void insertUpdate(DocumentEvent e)  { r.run(); }
                public void removeUpdate(DocumentEvent e)  { r.run(); }
                public void changedUpdate(DocumentEvent e) {}
            };
        }
    }
}
