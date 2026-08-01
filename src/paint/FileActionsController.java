package paint;

import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.function.Supplier;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Die Datei-Aktionen der Rechtsklick-Menüs: öffnen, kopieren, speichern unter,
 * duplizieren, umbenennen, löschen, Eigenschaften.
 *
 * <p><b>Rolle:</b> Fachlogik-Bündel hinter den Kontextmenüs der Listen. Die
 * Panels kennen diese Klasse nicht — sie füllen nur ein {@link ContextMenu},
 * und die {@code *CallbacksFactory} zeigt hierher (§22). Umgekehrt kennt diese
 * Klasse kein Panel-Innenleben, sondern bekommt eine Auffrisch-Funktion
 * gereicht.
 *
 * <p><b>Bilder werden ausschließlich über {@link ImageFileWriter} geschrieben</b>
 * (§34) — auch beim „Speichern unter“. Kopiert wird sonst byteweise mit
 * {@link Files#copy}; ein Kopiervorgang ist kein Bildschreibvorgang und darf
 * das Format nicht anfassen (ein JPG bliebe sonst nicht dasselbe JPG).
 */
final class FileActionsController {

    private FileActionsController() {}

    // =========================================================================
    // Menü-Zusammenstellungen
    // =========================================================================

    /**
     * Das volle Menü einer Bildkachel.
     *
     * @param ed      Hauptfenster
     * @param idx     Canvas, zu dem diese Liste gehört
     * @param file    die angeklickte Datei
     * @param panel   liefert die Liste, die nach Änderungen aufzufrischen ist
     * @param menu    das zu füllende Menü
     */
    static void fillImageMenu(SelectiveAlphaEditor ed, int idx, File file,
                              Supplier<TileGalleryPanel> panel, ContextMenu menu) {
        if (file == null) return;

        boolean exists = file.isFile();
        boolean isOpen = file.equals(ed.ci(idx).sourceFile);
        boolean dirty  = ed.dirtyFiles.contains(file);
        boolean twoCanvases = ed.secondCanvasBtn != null && ed.secondCanvasBtn.isSelected();

        menu.itemBold("Öffnen", exists, () -> ed.loadFile(file, idx));
        menu.item("Im anderen Canvas öffnen", exists && twoCanvases,
                () -> ed.loadFile(file, 1 - idx));

        menu.separator();
        menu.item("Kopieren", exists, () -> copyToClipboard(ed, file));
        menu.item("Als Layer einfügen", exists && ed.ci(idx).workingImage != null,
                () -> ElementLayerCallbacksFactory.insertFileAsLayer(ed, idx, file, 0));

        menu.separator();
        // „Speichern“ ist nur dann etwas wert, wenn es auch etwas zu speichern
        // gibt. Ein immer aktiver Eintrag verspräche eine Wirkung, die
        // ausbleibt — dieselbe Art Lüge wie ein Kürzel ohne Handler.
        menu.item("Speichern", isOpen && dirty, ed::saveImageToOriginal);
        menu.item("Speichern unter …", exists, () -> saveAs(ed, idx, file));
        menu.item("Duplizieren", exists, () -> duplicate(ed, file, panel));

        menu.separator();
        // Bei ungespeicherten Änderungen wären Umbenennen und Löschen ein
        // stiller Datenverlust: das Umbenennen zwänge zum Neuladen von der
        // Platte, das Löschen nähme die Vorlage weg. Beides bleibt grau, bis
        // gespeichert ist.
        menu.item("Umbenennen", exists && !dirty, () -> rename(ed, file, panel));
        menu.item("Löschen", exists && !dirty, () -> delete(ed, file, panel));

        menu.separator();
        menu.itemShowInExplorer(file);
        menu.item("Eigenschaften …", exists, () -> showProperties(ed, file));
    }

    /**
     * Das schlanke Menü für Szenen und Buchseiten.
     *
     * <p>Bewusst ohne „Speichern unter“: eine Szene ist ein <b>Verzeichnis</b>
     * (§23), das Kopieren wäre eine eigene Entscheidung mit eigenem
     * Fehlerbild — und der Format-Vertrag mit GameII hängt daran.
     *
     * @param onRename wird für „Umbenennen“ aufgerufen; die Listen haben dafür
     *                 bereits je eine eigene, erprobte Umsetzung
     */
    static void fillSlimMenu(File file, Runnable onOpen, Runnable onRename, ContextMenu menu) {
        if (file == null) return;
        boolean exists = file.exists();

        menu.itemBold("Öffnen", exists && onOpen != null, onOpen);
        menu.separator();
        menu.item("Umbenennen", exists && onRename != null, onRename);
        menu.separator();
        menu.itemShowInExplorer(file);
    }

    // =========================================================================
    // Die einzelnen Aktionen
    // =========================================================================

    /** Legt die Datei als Datei-Liste in die System-Zwischenablage. */
    static void copyToClipboard(SelectiveAlphaEditor ed, File file) {
        Transferable t = new Transferable() {
            @Override public DataFlavor[] getTransferDataFlavors() {
                return new DataFlavor[]{ DataFlavor.javaFileListFlavor };
            }
            @Override public boolean isDataFlavorSupported(DataFlavor f) {
                return DataFlavor.javaFileListFlavor.equals(f);
            }
            @Override public Object getTransferData(DataFlavor f) {
                return List.of(file);
            }
        };
        java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(t, null);
        ToastNotification.show(ed, "Kopiert: " + file.getName());
    }

    /**
     * „Speichern unter …“.
     *
     * <p><b>Zwei Fälle, ein Eintrag:</b> Ist die Datei gerade im Canvas offen,
     * wird der <i>bearbeitete</i> Stand geschrieben (über
     * {@link ImageFileWriter}, §34) — sonst wäre „Speichern unter“ nach dem
     * Malen eine Überraschung, weil es die alte Fassung von der Platte
     * kopierte. Ist sie es nicht, wird die Datei byteweise kopiert und behält
     * dadurch ihr Format.
     */
    static void saveAs(SelectiveAlphaEditor ed, int idx, File file) {
        CanvasInstance c = ed.ci(idx);
        boolean live = file.equals(c.sourceFile) && c.workingImage != null;

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Speichern unter");
        chooser.setSelectedFile(live ? withExtension(file, "png") : file);
        if (live) {
            chooser.setAcceptAllFileFilterUsed(false);
            chooser.addChoosableFileFilter(new FileNameExtensionFilter("PNG Images", "png"));
        }
        if (chooser.showSaveDialog(ed) != JFileChooser.APPROVE_OPTION) return;

        File target = chooser.getSelectedFile();
        if (target.equals(file)) {
            ed.showErrorDialog("Speichern unter", "Quelle und Ziel sind dieselbe Datei.");
            return;
        }
        if (target.exists() && !ConfirmDialog.ask(ed, "Überschreiben?",
                target.getName() + " gibt es bereits.\nSoll die Datei überschrieben werden?",
                "Überschreiben", true)) {
            return;
        }

        try {
            if (live) {
                if (!target.getName().toLowerCase().endsWith(".png"))
                    target = new File(target.getAbsolutePath() + ".png");
                BufferedImage composite = ed.renderCompositeForThumbnail(c);
                ImageFileWriter.writePng(composite != null ? composite : c.workingImage, target);
            } else {
                Files.copy(file.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            ToastNotification.show(ed, "Gespeichert: " + target.getName());
        } catch (IOException ex) {
            ed.showErrorDialog("Speicherfehler", ex.getMessage());
        }
    }

    /** Kopie im selben Verzeichnis, mit garantiert freiem Namen. */
    static void duplicate(SelectiveAlphaEditor ed, File file, Supplier<TileGalleryPanel> panel) {
        File dir = file.getParentFile();
        if (dir == null) return;
        // Derselbe Helfer, den auch das Ziehen-und-Ablegen benutzt — kein
        // zweiter Namensfindungs-Algorithmus (Univ. §6).
        File copy = BaseSidebarPanel.copyFileWithUniqueName(file, dir);
        if (copy == null) {
            ed.showErrorDialog("Fehler", "Duplizieren fehlgeschlagen.");
            return;
        }
        refresh(panel);
        ToastNotification.show(ed, "Dupliziert: " + copy.getName());
    }

    /** Umbenennen mit Namensprüfung. Die Endung bleibt erhalten. */
    static void rename(SelectiveAlphaEditor ed, File file, Supplier<TileGalleryPanel> panel) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext  = dot > 0 ? name.substring(dot)    : "";

        String input = TextInputDialog.ask(ed, "Umbenennen", "Neuer Name:", base);
        if (input == null || input.equals(base)) return;

        File target = new File(file.getParentFile(), input + ext);
        if (target.exists()) {
            ed.showErrorDialog("Umbenennen", "Es gibt bereits eine Datei mit diesem Namen.");
            return;
        }
        if (!file.renameTo(target)) {
            ed.showErrorDialog("Umbenennen", "Umbenennen fehlgeschlagen.");
            return;
        }
        forgetFile(ed, file);
        refresh(panel);
        ToastNotification.show(ed, "Umbenannt: " + target.getName());
    }

    /**
     * Löschen — mit Rückfrage und, wo möglich, in den Papierkorb.
     *
     * <p><b>Die Rückfrage ist keine Höflichkeit.</b> Es gibt für Dateien kein
     * Undo, und für Layer ebenso wenig (Befund D01: das Undo-Band speichert
     * nur {@code workingImage}). Ein Rechtsklick-Fehlgriff wäre sonst
     * endgültig.
     */
    static void delete(SelectiveAlphaEditor ed, File file, Supplier<TileGalleryPanel> panel) {
        boolean trash = Desktop.isDesktopSupported()
                && Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH);

        String question = file.getName() + "\n\n"
                + (trash ? "wird in den Papierkorb verschoben."
                         : "wird ENDGÜLTIG gelöscht — dieser Rechner bietet keinen Papierkorb an.");
        if (!ConfirmDialog.ask(ed, "Löschen", question, "Löschen", true)) return;

        boolean ok = trash ? Desktop.getDesktop().moveToTrash(file) : file.delete();
        if (!ok) {
            ed.showErrorDialog("Löschen", "Die Datei konnte nicht gelöscht werden.\n"
                    + "Möglicherweise ist sie noch von einem anderen Programm geöffnet.");
            return;
        }
        forgetFile(ed, file);
        refresh(panel);
        ToastNotification.show(ed, "Gelöscht: " + file.getName());
    }

    /** Pfad, Maße, Größe, Datum — die Fragen, die ein Dateimanager beantwortet. */
    static void showProperties(SelectiveAlphaEditor ed, File file) {
        int[] dim = readImageSize(file);
        String size = String.format("%,d Byte", file.length());
        String when = new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm")
                .format(new java.util.Date(file.lastModified()));

        JDialog d = UIComponentFactory.createBaseDialog(ed, "Eigenschaften", 520, 260);
        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBackground(AppColors.BG_PANEL);
        body.setBorder(BorderFactory.createEmptyBorder(AppTheme.PAD_XL, AppTheme.PAD_XL,
                AppTheme.PAD_XL, AppTheme.PAD_XL));

        body.add(row("Name",    file.getName()));
        body.add(row("Ordner",  String.valueOf(file.getParent())));
        body.add(row("Maße",    dim == null ? "—" : dim[0] + " × " + dim[1] + " Pixel"));
        body.add(row("Größe",   size));
        body.add(row("Geändert", when));

        d.add(body, BorderLayout.CENTER);
        d.setVisible(true);
    }

    // =========================================================================
    // Kleinkram
    // =========================================================================

    private static JPanel row(String caption, String value) {
        JPanel p = new JPanel(new BorderLayout(AppTheme.GAP_MD, 0));
        p.setBackground(AppColors.BG_PANEL);
        p.setBorder(BorderFactory.createEmptyBorder(AppTheme.PAD_MD, 0, AppTheme.PAD_MD, 0));

        JLabel c = new JLabel(caption);
        c.setForeground(AppColors.TEXT_MUTED);
        c.setFont(AppTheme.FONT_BASE);
        c.setPreferredSize(new java.awt.Dimension(80, 18));

        JLabel v = new JLabel(value);
        v.setForeground(AppColors.TEXT);
        v.setFont(AppTheme.FONT_BASE);

        p.add(c, BorderLayout.WEST);
        p.add(v, BorderLayout.CENTER);
        return p;
    }

    /**
     * Maße ohne die Pixel zu dekodieren — {@code ImageIO.read} auf ein großes
     * Bild nur für zwei Zahlen wäre Verschwendung im EDT.
     */
    private static int[] readImageSize(File file) {
        try (javax.imageio.stream.ImageInputStream in =
                     javax.imageio.ImageIO.createImageInputStream(file)) {
            if (in == null) return null;
            java.util.Iterator<javax.imageio.ImageReader> it = javax.imageio.ImageIO.getImageReaders(in);
            if (!it.hasNext()) return null;
            javax.imageio.ImageReader r = it.next();
            try {
                r.setInput(in);
                return new int[]{ r.getWidth(0), r.getHeight(0) };
            } finally {
                r.dispose();
            }
        } catch (IOException ex) {
            System.err.println("[FileActions] size read failed: " + ex.getMessage());
            return null;
        }
    }

    private static File withExtension(File f, String ext) {
        String n = f.getName();
        int dot = n.lastIndexOf('.');
        return new File(f.getParentFile(), (dot > 0 ? n.substring(0, dot) : n) + "." + ext);
    }

    /**
     * Nimmt eine verschwundene Datei aus den globalen Merklisten.
     *
     * <p>Ohne diesen Schritt bliebe ein gelöschter oder umbenannter Pfad in
     * {@code dirtyFiles} stehen — und die Rückfrage beim Beenden fragte
     * hinterher nach einer Datei, die es nicht mehr gibt (F01).
     */
    private static void forgetFile(SelectiveAlphaEditor ed, File file) {
        ed.dirtyFiles.remove(file);
        ed.updateDirtyUI();
    }

    private static void refresh(Supplier<TileGalleryPanel> panel) {
        if (panel == null) return;
        TileGalleryPanel p = panel.get();
        if (p != null) p.refreshGallery();
    }
}
