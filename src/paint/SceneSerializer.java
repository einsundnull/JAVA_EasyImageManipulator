package paint;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import javax.imageio.ImageIO;

/**
 * Serialisiert und deserialisiert Layer ↔ JSON.
 * Unterstützt TextLayer, ImageLayer, PathLayer ohne externe Bibliotheken.
 */
public class SceneSerializer {

    /**
     * Konvertiert eine Liste von Layern zu JSON-String.
     */
    public static String layersToJson(List<Layer> layers) {
        if (layers == null || layers.isEmpty()) {
            return "[]";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("[\n");

        for (int i = 0; i < layers.size(); i++) {
            Layer layer = layers.get(i);
            sb.append("  ");
            sb.append(serializeLayer(layer));

            if (i < layers.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }

        sb.append("]");
        return sb.toString();
    }

    /**
     * Konvertiert JSON-String zurück zu Layer-Liste.
     */
    public static List<Layer> layersFromJson(String json) {
        List<Layer> layers = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) {
            return layers;
        }

        json = json.trim();
        if (!json.startsWith("[") || !json.endsWith("]")) {
            return layers;
        }

        // Vereinfaches Parsing: split auf "},{" um Layer zu trennen
        String content = json.substring(1, json.length() - 1);
        List<String> layerJsons = splitLayers(content);

        for (String layerJson : layerJsons) {
            Layer layer = deserializeLayer(layerJson.trim());
            if (layer != null) {
                layers.add(layer);
            }
        }

        return layers;
    }

    private static String serializeLayer(Layer layer) {
        if (layer instanceof TextLayer tl) {
            return String.format(
                "{\"type\":\"TextLayer\",\"id\":%d,\"x\":%d,\"y\":%d,\"width\":%d,\"height\":%d," +
                "\"text\":\"%s\",\"fontName\":\"%s\",\"fontSize\":%d,\"bold\":%s,\"italic\":%s,\"color\":%d,\"hidden\":%s,\"wrapping\":%s}",
                tl.id(), tl.x(), tl.y(), tl.width(), tl.height(),
                escapeJson(tl.text()), tl.fontName(), tl.fontSize(),
                tl.fontBold() ? "true" : "false", tl.fontItalic() ? "true" : "false",
                tl.fontColor().getRGB(), tl.isHidden() ? "true" : "false",
                tl.isWrapping() ? "true" : "false"
            );
        } else if (layer instanceof ImageLayer il) {
            String imageData = "";
            if (il.image() != null) {
                try {
                    imageData = imageToBase64(il.image());
                } catch (IOException e) {
                    System.err.println("[WARN] Fehler beim Encodieren des ImageLayer-Bildes: " + e.getMessage());
                }
            }
            return String.format(
                "{\"type\":\"ImageLayer\",\"id\":%d,\"x\":%d,\"y\":%d,\"width\":%d,\"height\":%d," +
                "\"rotationAngle\":%.6f,\"opacity\":%d,\"hidden\":%s,\"imageData\":\"%s\"}",
                il.id(), il.x(), il.y(), il.width(), il.height(), il.rotationAngle(),
                il.opacity(), il.isHidden() ? "true" : "false", imageData
            );
        } else if (layer instanceof PathLayer pl) {
            StringBuilder pointsJson = new StringBuilder("[");
            List<Point3D> points = pl.points();
            for (int i = 0; i < points.size(); i++) {
                Point3D p = points.get(i);
                pointsJson.append(String.format("{\"x\":%.2f,\"y\":%.2f,\"z\":%.2f}", p.x, p.y, p.z));
                if (i < points.size() - 1) pointsJson.append(",");
            }
            pointsJson.append("]");

            return String.format(
                "{\"type\":\"PathLayer\",\"id\":%d,\"x\":%d,\"y\":%d,\"width\":%d,\"height\":%d," +
                "\"closed\":%s,\"hidden\":%s,\"points\":%s}",
                pl.id(), pl.x(), pl.y(), pl.width(), pl.height(),
                pl.isClosed() ? "true" : "false", pl.isHidden() ? "true" : "false", pointsJson.toString()
            );
        }

        return "{}";
    }

    private static Layer deserializeLayer(String json) {
        try {
            // Parse type
            String type = extractField(json, "type");
            if (type == null) return null;

            int id = Integer.parseInt(extractField(json, "id"));
            int x = Integer.parseInt(extractField(json, "x"));
            int y = Integer.parseInt(extractField(json, "y"));
            int w = Integer.parseInt(extractField(json, "width"));
            int h = Integer.parseInt(extractField(json, "height"));

            if ("TextLayer".equals(type)) {
                String text = extractField(json, "text");
                String fontName = extractField(json, "fontName");
                int fontSize = Integer.parseInt(extractField(json, "fontSize"));
                boolean bold = "true".equals(extractField(json, "bold"));
                boolean italic = "true".equals(extractField(json, "italic"));
                int colorInt = Integer.parseInt(extractField(json, "color"));
                Color color = new Color(colorInt, true);
                boolean hidden = "true".equals(extractField(json, "hidden"));
                boolean wrapping = "true".equals(extractField(json, "wrapping"));

                if (wrapping) {
                    return TextLayer.wrappingOf(id, text, fontName, fontSize, bold, italic, color, x, y, w, h, hidden);
                }
                return TextLayer.of(id, text, fontName, fontSize, bold, italic, color, x, y, hidden);
            } else if ("ImageLayer".equals(type)) {
                String imageData = extractField(json, "imageData");
                BufferedImage img = null;
                if (imageData != null && !imageData.isEmpty()) {
                    img = base64ToImage(imageData);
                }
                // Parse rotationAngle (default 0.0 for backward compatibility with old saved files)
                String rotStr = extractField(json, "rotationAngle");
                double rotAngle = (rotStr != null && !rotStr.isEmpty()) ? Double.parseDouble(rotStr) : 0.0;
                // Parse opacity (default 100 for backward compatibility)
                String opacityStr = extractField(json, "opacity");
                int opacity = (opacityStr != null && !opacityStr.isEmpty()) ? Integer.parseInt(opacityStr) : 100;
                // Parse hidden flag (default false for backward compatibility)
                boolean hidden = "true".equals(extractField(json, "hidden"));
                return new ImageLayer(id, img, x, y, w, h, rotAngle, opacity, hidden);
            } else if ("PathLayer".equals(type)) {
                boolean closed = "true".equals(extractField(json, "closed"));
                boolean hidden = "true".equals(extractField(json, "hidden"));
                List<Point3D> points = extractPoints(json);
                return PathLayer.of(id, points, null, closed, x, y, hidden);
            }
        } catch (Exception e) {
            System.err.println("[ERROR] Fehler beim Deserialisieren von Layer: " + e.getMessage());
        }

        return null;
    }

    private static List<String> splitLayers(String content) {
        List<String> result = new ArrayList<>();
        int depth = 0;
        int start = 0;

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '{') {
                if (depth == 0) start = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    result.add(content.substring(start, i + 1));
                }
            }
        }

        return result;
    }

    private static String extractField(String json, String fieldName) {
        String pattern = "\"" + fieldName + "\":";
        int idx = json.indexOf(pattern);
        if (idx < 0) return null;

        int start = idx + pattern.length();
        // Überspringen von Whitespace
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;

        if (start >= json.length()) return null;

        // String-Wert (in Anführungszeichen)
        if (json.charAt(start) == '"') {
            int end = start + 1;
            while (end < json.length()) {
                if (json.charAt(end) == '"' && json.charAt(end - 1) != '\\') break;
                end++;
            }
            if (end < json.length()) {
                return json.substring(start + 1, end);
            }
        } else {
            // Boolean oder Number
            int end = start;
            while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}' && json.charAt(end) != ']') {
                end++;
            }
            return json.substring(start, end).trim();
        }

        return null;
    }

    private static List<Point3D> extractPoints(String json) {
        List<Point3D> points = new ArrayList<>();
        // extractField kann Arrays nicht lesen – Array-Grenzen direkt suchen
        String key = "\"points\":";
        int keyIdx = json.indexOf(key);
        if (keyIdx < 0) return points;

        int start = json.indexOf('[', keyIdx + key.length());
        if (start < 0) return points;

        // Passendes ']' finden
        int depth = 0, end = -1;
        for (int i = start; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (ch == '[') depth++;
            else if (ch == ']') { if (--depth == 0) { end = i; break; } }
        }
        if (end < 0) return points;

        // Einzelne Punkt-Objekte { } aufteilen (reuse splitLayers)
        for (String pStr : splitLayers(json.substring(start + 1, end))) {
            try {
                String xs = extractField(pStr, "x");
                String ys = extractField(pStr, "y");
                String zs = extractField(pStr, "z");
                if (xs == null || ys == null) continue;
                double z = (zs != null && !zs.isEmpty()) ? Double.parseDouble(zs) : 0.0;
                points.add(new Point3D(Double.parseDouble(xs), Double.parseDouble(ys), z));
            } catch (Exception e) {
                System.err.println("[WARN] Fehler beim Parsen eines Punkts: " + e.getMessage());
            }
        }

        return points;
    }

    /**
     * Konvertiert BufferedImage zu Base64-kodiertem PNG-String.
     */
    public static String imageToBase64(BufferedImage img) throws IOException {
        if (img == null) return "";

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "PNG", baos);
        byte[] bytes = baos.toByteArray();
        return Base64.getEncoder().encodeToString(bytes);
    }

    /**
     * Konvertiert Base64-kodierten PNG-String zurück zu BufferedImage.
     */
    public static BufferedImage base64ToImage(String b64) throws IOException {
        if (b64 == null || b64.isEmpty()) return null;

        byte[] bytes = Base64.getDecoder().decode(b64);
        return ImageIO.read(new java.io.ByteArrayInputStream(bytes));
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
