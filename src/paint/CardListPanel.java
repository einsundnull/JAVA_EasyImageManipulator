package paint;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Abstract base panel: scrollable list of editable text cards with TTS play/stop.
 * Subclasses specify which CardEntry field to show and how to persist bg color.
 */
abstract class CardListPanel extends JPanel {

    // ── Abstract contract ─────────────────────────────────────────────────────

    abstract String getTitle();
    abstract String getDisplayText(CardEntry e);
    abstract void   setDisplayText(CardEntry e, String text);
    abstract Color  loadBgColor();
    abstract void   persistBgColor(Color c);
    abstract String getTtsLang();

    // ── State ─────────────────────────────────────────────────────────────────

    protected final List<CardEntry> entries = CardListStore.get().entries();
    private final JPanel      cardsContainer;
    private final JScrollPane scrollPane;
    private final JButton     colorSwatch;
    private final JLabel      folderLabel;
    private Color             listBg;
    private CardWidget        selectedCard;
    private final List<CardWidget> widgets = new ArrayList<>();

    // ── Constructor ───────────────────────────────────────────────────────────

    CardListPanel() {
        listBg = loadBgColor();
        setLayout(new BorderLayout(0, 0));
        setPreferredSize(new Dimension(240, 0));

        // ── Header row 1: title + color swatch ───────────────────────────────
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 3));
        row1.setBackground(AppColors.BG_TOOLBAR);

        JLabel title = new JLabel(getTitle());
        title.setFont(new Font("SansSerif", Font.BOLD, 12));
        title.setForeground(AppColors.TEXT);

        colorSwatch = smallBtn("  ", "Hintergrundfarbe ändern");
        colorSwatch.setBackground(listBg);
        colorSwatch.setOpaque(true);
        colorSwatch.setPreferredSize(new Dimension(22, 22));
        colorSwatch.addActionListener(e -> chooseColor());

        row1.add(title);
        row1.add(colorSwatch);

        // ── Header row 2: +, folder ───────────────────────────────────────────
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 3));
        row2.setBackground(AppColors.BG_TOOLBAR);

        JButton addBtn = smallBtn("+", "Neue Karte hinzufügen");
        addBtn.addActionListener(e -> addCard());

        JButton folderBtn = smallBtn("\uD83D\uDCC2", "Ordner wechseln / erstellen");
        folderBtn.addActionListener(e -> openFolderDialog());

        folderLabel = new JLabel(CardListStore.get().currentFolder());
        folderLabel.setFont(new Font("SansSerif", Font.ITALIC, 11));
        folderLabel.setForeground(AppColors.TEXT_MUTED);

        row2.add(addBtn);
        row2.add(folderBtn);
        row2.add(folderLabel);

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBackground(AppColors.BG_TOOLBAR);
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, AppColors.BORDER));
        header.add(row1);
        header.add(row2);
        add(header, BorderLayout.NORTH);

        // ── Cards container ───────────────────────────────────────────────────
        cardsContainer = new JPanel();
        cardsContainer.setLayout(new BoxLayout(cardsContainer, BoxLayout.Y_AXIS));
        cardsContainer.setBackground(listBg);

        scrollPane = new JScrollPane(cardsContainer);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getViewport().setBackground(listBg);
        scrollPane.setBorder(null);
        TileGalleryPanel.applyDarkScrollBar(scrollPane.getVerticalScrollBar());
        add(scrollPane, BorderLayout.CENTER);

        // DEL key (only when focus is NOT in a textarea)
        setFocusable(true);
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "del");
        getActionMap().put("del", new AbstractAction() {
            public void actionPerformed(java.awt.event.ActionEvent ev) {
                java.awt.Component f =
                        java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
                if (!(f instanceof JTextArea)) deleteSelected();
            }
        });

        refreshCards();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Called by the other panel after a folder switch so both lists refresh. */
    void reloadFolder() {
        folderLabel.setText(CardListStore.get().currentFolder());
        refreshCards();
    }

    /** Re-applies font/color settings to all visible cards. */
    void applyDisplaySettings() {
        refreshCards();
    }

    // ── Card management ───────────────────────────────────────────────────────

    private void addCard() {
        CardEntry e = new CardEntry(UUID.randomUUID().toString(), "", "");
        entries.add(e);
        CardListStore.get().save();
        refreshCards();
        SwingUtilities.invokeLater(() -> {
            JScrollBar sb = scrollPane.getVerticalScrollBar();
            sb.setValue(sb.getMaximum());
            if (!widgets.isEmpty()) widgets.get(widgets.size() - 1).focusTextArea();
        });
    }

    void removeCard(CardWidget w) {
        entries.remove(w.entry);
        CardListStore.get().save();
        if (selectedCard == w) selectedCard = null;
        refreshCards();
    }

    private void deleteSelected() {
        if (selectedCard != null) removeCard(selectedCard);
    }

    void setSelected(CardWidget w) {
        if (selectedCard != null && selectedCard != w) selectedCard.setSelected(false);
        selectedCard = w;
        if (w != null) w.setSelected(true);
    }

    private void refreshCards() {
        cardsContainer.removeAll();
        widgets.clear();
        cardsContainer.add(Box.createRigidArea(new Dimension(0, 4)));
        for (CardEntry e : entries) {
            CardWidget w = new CardWidget(e, this);
            widgets.add(w);
            cardsContainer.add(w);
            cardsContainer.add(Box.createRigidArea(new Dimension(0, 4)));
        }
        cardsContainer.add(Box.createVerticalGlue());
        cardsContainer.revalidate();
        cardsContainer.repaint();
    }

    // ── Color chooser ─────────────────────────────────────────────────────────

    private void chooseColor() {
        Color chosen = JColorChooser.showDialog(this, "Hintergrundfarbe", listBg);
        if (chosen == null) return;
        listBg = chosen;
        cardsContainer.setBackground(chosen);
        scrollPane.getViewport().setBackground(chosen);
        colorSwatch.setBackground(chosen);
        persistBgColor(chosen);
        try { AppSettings.getInstance().save(); } catch (Exception ex) { /* ignore */ }
        refreshCards(); // re-render card backgrounds
    }

    // ── Folder dialog ─────────────────────────────────────────────────────────

    private void openFolderDialog() {
        Window win = SwingUtilities.getWindowAncestor(this);
        CardFolderDialog dlg = new CardFolderDialog(win, () -> {
            folderLabel.setText(CardListStore.get().currentFolder());
            refreshCards();
        });
        dlg.setVisible(true);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static JButton smallBtn(String text, String tip) {
        JButton b = new JButton(text);
        b.setToolTipText(tip);
        b.setFont(new Font("SansSerif", Font.BOLD, 12));
        b.setForeground(AppColors.TEXT);
        b.setBackground(AppColors.BTN_BG);
        b.setFocusPainted(false);
        b.setMargin(new Insets(1, 5, 1, 5));
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppColors.BORDER),
                BorderFactory.createEmptyBorder(1, 5, 1, 5)));
        return b;
    }

    private Color cardBg() {
        // Slightly lighter than list bg for contrast
        int r = Math.min(255, listBg.getRed()   + 12);
        int g = Math.min(255, listBg.getGreen() + 12);
        int bl= Math.min(255, listBg.getBlue()  + 12);
        return new Color(r, g, bl);
    }

    // ── Inner: CardWidget ─────────────────────────────────────────────────────

    class CardWidget extends JPanel {

        final CardEntry entry;
        private final JTextArea textArea;
        private final JButton   playBtn;
        private boolean playing = false;

        CardWidget(CardEntry e, CardListPanel owner) {
            this.entry = e;
            AppSettings s = AppSettings.getInstance();

            setLayout(new BorderLayout(0, 0));
            setOpaque(true);
            setBackground(cardBg());
            setAlignmentX(LEFT_ALIGNMENT);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
            applyBorder(false);

            // ── Textarea ──────────────────────────────────────────────────────
            textArea = new JTextArea(getDisplayText(e)) {
                @Override public boolean getScrollableTracksViewportWidth() { return true; }
            };
            textArea.setFont(new Font(s.getCardFontFamily(), Font.PLAIN, s.getCardFontSize()));
            textArea.setForeground(new Color(s.getCardFontColor()));
            textArea.setCaretColor(new Color(s.getCardFontColor()));
            textArea.setLineWrap(true);
            textArea.setWrapStyleWord(true);
            textArea.setOpaque(false);
            textArea.setBorder(BorderFactory.createEmptyBorder(5, 7, 5, 7));
            // Minimum 1 row; grows with content via ComponentListener below

            // Live-save
            textArea.getDocument().addDocumentListener(new DocumentListener() {
                public void insertUpdate(DocumentEvent ev) { commit(); }
                public void removeUpdate(DocumentEvent ev) { commit(); }
                public void changedUpdate(DocumentEvent ev) {}
                void commit() {
                    setDisplayText(entry, textArea.getText());
                    CardListStore.get().save();
                    updateHeight();
                }
            });

            // DEL when textarea is empty at end → delete card
            textArea.addKeyListener(new KeyAdapter() {
                @Override public void keyPressed(KeyEvent ke) {
                    if (ke.getKeyCode() == KeyEvent.VK_DELETE
                            && textArea.getText().isEmpty()) {
                        owner.removeCard(CardWidget.this);
                        ke.consume();
                    }
                }
            });

            textArea.addFocusListener(new FocusAdapter() {
                @Override public void focusGained(FocusEvent fe) {
                    owner.setSelected(CardWidget.this);
                }
            });

            // ── Play/Stop button (EAST) ───────────────────────────────────────
            playBtn = new JButton("▶");
            playBtn.setFont(new Font("SansSerif", Font.PLAIN, 11));
            playBtn.setForeground(AppColors.TEXT);
            playBtn.setBackground(AppColors.BTN_BG);
            playBtn.setFocusPainted(false);
            playBtn.setToolTipText("Text vorlesen");
            playBtn.setPreferredSize(new Dimension(28, 28));
            playBtn.setMargin(new Insets(0, 0, 0, 0));
            playBtn.setBorder(BorderFactory.createLineBorder(AppColors.BORDER));
            playBtn.addActionListener(ev -> togglePlay());

            JPanel east = new JPanel(new FlowLayout(FlowLayout.CENTER, 2, 4));
            east.setOpaque(false);
            east.add(playBtn);

            // Click on card → select + focus
            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent me) {
                    owner.setSelected(CardWidget.this);
                    textArea.requestFocusInWindow();
                }
            });

            add(textArea, BorderLayout.CENTER);
            add(east, BorderLayout.EAST);

            // Auto-height: recompute when width changes
            addComponentListener(new ComponentAdapter() {
                @Override public void componentResized(ComponentEvent ce) { updateHeight(); }
            });
        }

        void focusTextArea() { textArea.requestFocusInWindow(); }

        void setSelected(boolean sel) { applyBorder(sel); repaint(); }

        // ── Auto-height ───────────────────────────────────────────────────────

        private void updateHeight() {
            int availW = getWidth() - 36; // subtract play button + borders
            if (availW <= 0) return;
            textArea.setSize(availW, Short.MAX_VALUE);
            int prefH = Math.max(textArea.getPreferredSize().height, 30);
            textArea.setPreferredSize(new Dimension(0, prefH));
            revalidate();
            if (getParent() != null) getParent().revalidate();
        }

        // ── Play / Stop ───────────────────────────────────────────────────────

        private void togglePlay() {
            if (playing) {
                CardTtsPlayer.stop();
                setPlaying(false);
            } else {
                String text = getDisplayText(entry);
                String lang = getTtsLang();
                CardTtsPlayer.play(entry.id, text, lang,
                        () -> SwingUtilities.invokeLater(() -> setPlaying(false)));
                setPlaying(true);
            }
        }

        private void setPlaying(boolean p) {
            playing = p;
            playBtn.setText(p ? "⏹" : "▶");
            playBtn.setForeground(p ? AppColors.ACCENT : AppColors.TEXT);
        }

        // ── Border ────────────────────────────────────────────────────────────

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
    }
}
