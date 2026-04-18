package paint;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

/**
 * Liest TextLayer aus dem kompatiblen .txt Format (wie GameII43 PathReader).
 * Rekonstruiert Text, Font-Eigenschaften, Position und Sichtbarkeit.
 */
public class TextReader {

    public static TextLayer readConfigFile(String filePath) {
        // Falls nur Dateiname, versuche in scenes/texts/ zu suchen
        String resolved = (filePath.contains("/") || filePath.contains("\\"))
                ? filePath : filePath;

        // Defaults
        int id = 0;
        int x = 0, y = 0;
        int width = 100, height = 20;
        boolean hidden = false;
        boolean isWrapping = false;
        String content = "";
        String fontName = "SansSerif";
        int fontSize = 12;
        boolean fontBold = false;
        boolean fontItalic = false;
        Color fontColor = Color.BLACK;

        try (BufferedReader r = new BufferedReader(new FileReader(resolved))) {
            String line;
            String section = null;

            while ((line = r.readLine()) != null) {
                if (line.isEmpty()) continue;

                String trimmed = line.trim();

                // Abschnitt-Header
                if (trimmed.startsWith("#")) {
                    section = trimmed.substring(1).trim();
                    continue;
                }

                if (section == null) continue;

                // Dash am Anfang entfernen
                if (trimmed.startsWith("-")) {
                    trimmed = trimmed.substring(1).trim();
                }

                if (!trimmed.contains(":")) {
                    // Name-Zeile hat kein Colon (z.B. "text_42") — ID hier extrahieren
                    if ("Name:".equals(section) && trimmed.startsWith("text_")) {
                        try {
                            id = Integer.parseInt(trimmed.substring(5));
                        } catch (NumberFormatException e) {
                            id = Math.abs(trimmed.hashCode());
                        }
                    }
                    continue;
                }

                String key = trimmed.substring(0, trimmed.indexOf(':')).trim();
                String val = trimmed.substring(trimmed.indexOf(':') + 1).trim();

                switch (section) {
                    case "Name:":
                        break;

                    case "Position:":
                        try {
                            switch (key) {
                                case "x" -> x = Integer.parseInt(val);
                                case "y" -> y = Integer.parseInt(val);
                                case "width" -> width = Integer.parseInt(val);
                                case "height" -> height = Integer.parseInt(val);
                            }
                        } catch (NumberFormatException e) {
                            // Ignorieren bei Parse-Fehler
                        }
                        break;

                    case "Properties:":
                        switch (key) {
                            case "hidden"     -> hidden     = Boolean.parseBoolean(val);
                            case "isWrapping" -> isWrapping = Boolean.parseBoolean(val);
                        }
                        break;

                    case "Text:":
                        if (key.equals("content")) {
                            content = unescapeText(val);
                        }
                        break;

                    case "Font:":
                        try {
                            switch (key) {
                                case "fontName" -> fontName = val;
                                case "fontSize" -> fontSize = Integer.parseInt(val);
                                case "fontBold" -> fontBold = Boolean.parseBoolean(val);
                                case "fontItalic" -> fontItalic = Boolean.parseBoolean(val);
                                case "fontColor" -> {
                                    int rgb = Integer.parseInt(val);
                                    fontColor = new Color(rgb, true); // true = hasAlpha
                                }
                            }
                        } catch (NumberFormatException e) {
                            // Ignorieren bei Parse-Fehler
                        }
                        break;
                }
            }

        } catch (IOException e) {
            System.err.println("[TextReader] " + e.getMessage());
            return null;
        }

        // TextLayer erstellen
        if (isWrapping)
            return TextLayer.wrappingOf(id, content, fontName, fontSize, fontBold, fontItalic, fontColor, x, y, width, height, hidden);
        return TextLayer.of(id, content, fontName, fontSize, fontBold, fontItalic, fontColor, x, y, hidden);
    }

    /**
     * Unescapet Zeilenumbrüche und spezielle Zeichen.
     */
    private static String unescapeText(String text) {
        if (text == null) return "";
        return text.replace("\\n", "\n")
                   .replace("\\r", "\r");
    }
}
