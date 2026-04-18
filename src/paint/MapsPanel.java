package paint;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.io.IOException;
import java.util.LinkedHashMap;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

/**
 * Panel displaying all translation maps in a list view.
 * Shows language, section, and allows viewing/editing/deleting maps.
 */
public class MapsPanel extends BaseSidebarPanel {

    public interface Callbacks {
        void onMapSelected(TranslationMap map);
        void onMapDeleted(String language, String mapId);
        void onMapEdited(TranslationMap oldMap, TranslationMap newMap);
    }

    private static class MapsScrollablePanel extends JPanel implements javax.swing.Scrollable {
        @Override public java.awt.Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(java.awt.Rectangle r, int o, int d) { return 14; }
        @Override public int getScrollableBlockIncrement(java.awt.Rectangle r, int o, int d) { return 100; }
        @Override public boolean getScrollableTracksViewportWidth()  { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    private final Callbacks cb;
    private final MapsScrollablePanel mapsContainer;
    private final JScrollPane scrollPane;
    private java.util.Map<String, java.util.List<TranslationMap>> allMaps = new LinkedHashMap<>();
    private boolean showAll = false;
    private TranslationMap linkedMap = null;

    public MapsPanel(Callbacks cb) {
        this.cb = cb;
        setLayout(new BorderLayout());
        setBackground(AppColors.BG_PANEL);

        // Header with All/Only toggle, refresh button
        JPanel header = buildSidebarHeader("Translation Maps", this::refreshMaps, isAll -> {
            showAll = isAll;
            refreshMapsList();
        }, null);
        add(header, BorderLayout.NORTH);

        mapsContainer = new MapsScrollablePanel();
        mapsContainer.setLayout(new BoxLayout(mapsContainer, BoxLayout.Y_AXIS));
        mapsContainer.setBackground(AppColors.BG_PANEL);

        // ScrollPane using base class builder
        scrollPane = buildSidebarScrollPane(mapsContainer);
        add(scrollPane, BorderLayout.CENTER);

        refreshMapsList();
    }

    @Override
    public void refresh() {
        refreshMapsList();
    }

    /**
     * Refresh Maps-Liste vom Filesystem (called by refresh button in header).
     */
    public void refreshMaps() {
        refresh();
    }

    /**
     * Set the linked map for the All/Only toggle.
     */
    public void setLinkedMap(TranslationMap map) {
        this.linkedMap = map;
        refreshMapsList();
    }

    /**
     * Reloads all maps from disk and refreshes the display.
     */
    public void refreshMapsList() {
        try {
            allMaps = MapManager.loadAllMaps();
        } catch (IOException ex) {
            System.err.println("[ERROR] Failed to load maps: " + ex.getMessage());
            allMaps.clear();
        }

        mapsContainer.removeAll();

        if (allMaps.isEmpty()) {
            JLabel emptyLbl = new JLabel("Keine Maps vorhanden");
            emptyLbl.setForeground(AppColors.TEXT_MUTED);
            emptyLbl.setAlignmentX(CENTER_ALIGNMENT);
            mapsContainer.add(emptyLbl);
        } else {
            for (String language : allMaps.keySet()) {
                java.util.List<TranslationMap> maps = allMaps.get(language);
                if (!maps.isEmpty()) {
                    // Language header
                    JLabel langHeader = new JLabel(language.toUpperCase());
                    langHeader.setForeground(AppColors.ACCENT);
                    langHeader.setFont(new Font("SansSerif", Font.BOLD, 11));
                    langHeader.setBorder(BorderFactory.createEmptyBorder(6, 6, 2, 6));
                    langHeader.setAlignmentX(LEFT_ALIGNMENT);
                    langHeader.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
                    mapsContainer.add(langHeader);

                    for (TranslationMap map : maps) {
                        MapTile tile = new MapTile(map);
                        mapsContainer.add(tile);
                    }
                }
            }
        }

        mapsContainer.revalidate();
        mapsContainer.repaint();
    }

    /**
     * Inner class: single map tile showing language, section, and text previews.
     */
    private class MapTile extends JPanel {
        private final TranslationMap map;
        private final boolean isShowAll;
        private final TranslationMap linkedMapRef;

        MapTile(TranslationMap map) {
            this.map = map;
            this.isShowAll = showAll;
            this.linkedMapRef = linkedMap;
            setLayout(null);
            setPreferredSize(new Dimension(140, 110));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));
            setOpaque(true);
            setBackground(new Color(48, 48, 48));
            setBorder(BorderFactory.createLineBorder(new Color(70, 70, 70), 1));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            // Doppelklick zum Bearbeiten
            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    if (e.getClickCount() == 2) {
                        e.consume();
                        editMap();
                    }
                }
            });

            // Delete button (top right)
            JLabel delBtn = new JLabel("✕", JLabel.CENTER);
            delBtn.setForeground(new Color(220, 60, 60));
            delBtn.setFont(new Font("SansSerif", Font.BOLD, 10));
            delBtn.setBounds(getPreferredSize().width - 19, 4, 16, 16);
            delBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            delBtn.setOpaque(true);
            delBtn.setBackground(new Color(50, 50, 50));
            delBtn.setBorder(BorderFactory.createLineBorder(new Color(70, 70, 70), 1));
            delBtn.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    e.consume();
                    int result = JOptionPane.showConfirmDialog(MapsPanel.this,
                            "Wirklich löschen?\n" + map.section() + " [" + map.language() + "]",
                            "Bestätigung", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                    if (result == JOptionPane.YES_OPTION) {
                        try {
                            MapManager.deleteMap(map.language(), map.id());
                            cb.onMapDeleted(map.language(), map.id());
                            refreshMapsList();
                        } catch (IOException ex) {
                            JOptionPane.showMessageDialog(MapsPanel.this,
                                    "Fehler beim Löschen: " + ex.getMessage(),
                                    "Fehler", JOptionPane.ERROR_MESSAGE);
                        }
                    }
                }

                @Override
                public void mouseEntered(java.awt.event.MouseEvent e) {
                    delBtn.setBackground(new Color(180, 40, 40));
                    delBtn.setForeground(Color.WHITE);
                    delBtn.setBorder(BorderFactory.createLineBorder(new Color(220, 60, 60), 1));
                }

                @Override
                public void mouseExited(java.awt.event.MouseEvent e) {
                    delBtn.setBackground(new Color(50, 50, 50));
                    delBtn.setForeground(new Color(220, 60, 60));
                    delBtn.setBorder(BorderFactory.createLineBorder(new Color(70, 70, 70), 1));
                }
            });
            add(delBtn);

            // TTS buttons for textI and textII
            addTTSButton(map.textI(), 150, 4);
            addTTSButton(map.textII(), 168, 4);
        }

        private void addTTSButton(String text, int x, int y) {
            if (text == null || text.isEmpty()) return;

            JLabel ttsBtn = new JLabel("🔊", JLabel.CENTER);
            ttsBtn.setForeground(new Color(100, 180, 220));
            ttsBtn.setFont(new Font("SansSerif", Font.BOLD, 11));
            ttsBtn.setBounds(x, y, 16, 16);
            ttsBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            ttsBtn.setOpaque(true);
            ttsBtn.setBackground(new Color(50, 50, 50));
            ttsBtn.setBorder(BorderFactory.createLineBorder(new Color(70, 70, 70), 1));
            ttsBtn.setToolTipText("Text vorlesen");
            ttsBtn.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    e.consume();
                    TextToSpeech.speak(text, map.language());
                }

                @Override
                public void mouseEntered(java.awt.event.MouseEvent e) {
                    ttsBtn.setBackground(new Color(80, 150, 200));
                    ttsBtn.setForeground(Color.WHITE);
                    ttsBtn.setBorder(BorderFactory.createLineBorder(new Color(100, 180, 220), 1));
                }

                @Override
                public void mouseExited(java.awt.event.MouseEvent e) {
                    ttsBtn.setBackground(new Color(50, 50, 50));
                    ttsBtn.setForeground(new Color(100, 180, 220));
                    ttsBtn.setBorder(BorderFactory.createLineBorder(new Color(70, 70, 70), 1));
                }
            });
            add(ttsBtn);
        }

        private void editMap() {
            MapEditDialog dialog = new MapEditDialog(SwingUtilities.getWindowAncestor(MapsPanel.this), map);
            dialog.setVisible(true);

            if (dialog.isAccepted()) {
                try {
                    TranslationMap updated = new TranslationMap(map.id(), dialog.getLanguage(),
                            dialog.getSection(), dialog.getTextI(), dialog.getTextII(), map.createdAt());
                    updated.setModifiedTime(System.currentTimeMillis());
                    MapManager.addOrUpdateMap(updated);
                    cb.onMapEdited(map, updated);
                    refreshMapsList();
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(MapsPanel.this,
                            "Fehler beim Speichern: " + ex.getMessage(),
                            "Fehler", JOptionPane.ERROR_MESSAGE);
                }
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                                java.awt.RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            // Draw background
            g2.setColor(new Color(48, 48, 48));
            g2.fillRect(0, 0, w, h);

            // Section title
            g2.setColor(AppColors.ACCENT);
            g2.setFont(new Font("SansSerif", Font.BOLD, 11));
            g2.drawString(map.section(), 6, 16);

            // Language tag
            g2.setColor(new Color(100, 150, 200));
            g2.setFont(new Font("SansSerif", Font.PLAIN, 9));
            g2.drawString("[" + map.language() + "]", 6, 30);

            // Text I preview
            g2.setColor(AppColors.TEXT_MUTED);
            g2.setFont(new Font("SansSerif", Font.PLAIN, 9));
            String previewI = map.textI();
            if (previewI == null) previewI = "";
            if (previewI.length() > 50) previewI = previewI.substring(0, 50) + "...";
            g2.drawString("I: " + previewI, 6, 46);

            // Text II preview
            String previewII = map.textII();
            if (previewII == null) previewII = "";
            if (previewII.length() > 50) previewII = previewII.substring(0, 50) + "...";
            g2.drawString("II: " + previewII, 6, 62);

            // Draw red border for unlinked maps when showAll is true
            if (isShowAll && linkedMapRef != null && !map.id().equals(linkedMapRef.id())) {
                g2.setColor(AppColors.DANGER);
                g2.setStroke(new java.awt.BasicStroke(1));
                g2.drawRect(0, 0, w - 1, h - 1);
            }

            g2.dispose();
        }
    }
}
