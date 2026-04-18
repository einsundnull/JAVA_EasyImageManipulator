package paint;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Schreibt TextLayer in das kompatible .txt Format (wie GameII43 PathWriter).
 * Speichert Text, Font-Eigenschaften, Position und Sichtbarkeit.
 */
public class TextWriter {

    /**
     * Schreibt eine TextLayer-Datei in src/bin parallel.
     */
    public static void writeConfigFile(String filePath, TextLayer text) {
        write(filePath, text);
        // Auch in bin/ speichern wenn in src/ ist
        String bin = filePath.replace("src/texts/", "bin/texts/")
                             .replace("src\\texts\\", "bin\\texts\\");
        if (!bin.equals(filePath)) write(bin, text);
    }

    private static void write(String filePath, TextLayer text) {
        try {
            Files.createDirectories(Paths.get(filePath).getParent());
        } catch (IOException e) {
            System.err.println("[TextWriter] Verzeichnis konnte nicht erstellt werden: " + e.getMessage());
        }
        try (FileWriter w = new FileWriter(filePath)) {

            // Basis-Informationen
            w.write("#Name:\n");
            w.write("-text_" + text.id() + "\n\n");

            // Position und Größe
            w.write("#Position:\n");
            w.write("-x: " + text.x() + "\n");
            w.write("-y: " + text.y() + "\n");
            w.write("-width: " + text.width + "\n");
            w.write("-height: " + text.height + "\n\n");

            // Sichtbarkeit
            w.write("#Properties:\n");
            w.write("-hidden: " + text.isHidden() + "\n");
            w.write("-isWrapping: " + text.isWrapping() + "\n\n");

            // Text-Inhalt
            w.write("#Text:\n");
            w.write("-content: " + escapeText(text.text()) + "\n\n");

            // Font-Eigenschaften
            w.write("#Font:\n");
            w.write("-fontName: " + text.fontName() + "\n");
            w.write("-fontSize: " + text.fontSize() + "\n");
            w.write("-fontBold: " + text.fontBold() + "\n");
            w.write("-fontItalic: " + text.fontItalic() + "\n");

            // Farbe als RGB
            w.write("-fontColor: " + text.fontColor().getRGB() + "\n");
            w.write("\n");

        } catch (IOException e) {
            System.err.println("[TextWriter] " + e.getMessage());
        }
    }

    /**
     * Escapet Zeilenumbrüche und spezielle Zeichen für das Text-Format.
     */
    private static String escapeText(String text) {
        if (text == null) return "";
        return text.replace("\n", "\\n")
                   .replace("\r", "\\r");
    }
}
