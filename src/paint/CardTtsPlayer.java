package paint;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

/**
 * Singleton TTS player for card panels.
 * Kills any running speech before starting a new one.
 * Uses a temporary UTF-8 .ps1 script file on Windows to handle all
 * Unicode characters (including Japanese) and avoid quoting issues.
 */
class CardTtsPlayer {

    private static volatile String   currentId      = null;
    private static volatile Process  currentProcess = null;
    private static volatile Runnable currentOnDone  = null;

    /** Stops any running speech, then speaks {@code text} in {@code lang}. */
    static void play(String cardId, String text, String lang, Runnable onDone) {
        stop();
        if (text == null || text.isBlank()) return;
        currentId     = cardId;
        currentOnDone = onDone;
        new Thread(() -> {
            try {
                Process p = buildProcess(text, lang);
                currentProcess = p;
                p.waitFor();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } catch (Exception ex) {
                System.err.println("[CardTtsPlayer] " + ex.getMessage());
            } finally {
                currentId     = null;
                currentProcess = null;
                Runnable cb = currentOnDone;
                currentOnDone = null;
                if (cb != null) cb.run();
            }
        }, "CardTTS").start();
    }

    static void stop() {
        Process p = currentProcess;
        if (p != null && p.isAlive()) p.destroyForcibly();
        currentId     = null;
        currentProcess = null;
        Runnable cb = currentOnDone;
        currentOnDone = null;
        if (cb != null) cb.run();
    }

    static boolean isPlaying(String cardId) {
        return cardId != null && cardId.equals(currentId);
    }

    // ── Process builder ───────────────────────────────────────────────────────

    private static Process buildProcess(String text, String lang) throws IOException {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) return speakWindows(text, lang);
        if (os.contains("mac")) return speakMac(text, lang);
        return speakLinux(text, lang);
    }

    // ── Windows: write UTF-8 .ps1 temp file, run with powershell ─────────────

    private static Process speakWindows(String text, String lang) throws IOException {
        String locale = windowsLocale(lang);

        // Escape single-quote for PowerShell here-string safety
        String safeText = text.replace("'", "''");

        // Build PowerShell script (UTF-8 content, here-string avoids all escaping)
        String script =
            "Add-Type -AssemblyName System.Speech\r\n" +
            "$sp = New-Object System.Speech.Synthesis.SpeechSynthesizer\r\n" +
            "try {\r\n" +
            "  $ci = [System.Globalization.CultureInfo]::new('" + locale + "')\r\n" +
            "  $sp.SelectVoiceByHints(\r\n" +
            "    [System.Speech.Synthesis.VoiceGender]::NotSet,\r\n" +
            "    [System.Speech.Synthesis.VoiceAge]::NotSet,\r\n" +
            "    0, $ci)\r\n" +
            "} catch {}\r\n" +
            "$sp.Speak(@'\r\n" +
            safeText + "\r\n" +
            "'@)\r\n";

        File tmp = File.createTempFile("cardtts_", ".ps1");
        tmp.deleteOnExit();
        try (Writer w = new OutputStreamWriter(
                new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
            // Write UTF-8 BOM so PowerShell recognises the encoding
            w.write('\uFEFF');
            w.write(script);
        }

        ProcessBuilder pb = new ProcessBuilder(
            "powershell",
            "-ExecutionPolicy", "Bypass",
            "-NonInteractive",
            "-File", tmp.getAbsolutePath());
        pb.redirectErrorStream(true);
        return pb.start();
    }

    private static Process speakMac(String text, String lang) throws IOException {
        ProcessBuilder pb = new ProcessBuilder("say", "-v", macVoice(lang), text);
        pb.redirectErrorStream(true);
        return pb.start();
    }

    private static Process speakLinux(String text, String lang) throws IOException {
        ProcessBuilder pb = new ProcessBuilder("espeak", "-v", lang, text);
        pb.redirectErrorStream(true);
        return pb.start();
    }

    // ── Language maps ─────────────────────────────────────────────────────────

    static String[] availableLanguages() {
        return new String[]{ "de", "en", "fr", "es", "it", "pt", "nl", "ru", "ja", "zh" };
    }

    private static String windowsLocale(String l) {
        return switch (l == null ? "" : l) {
            case "de" -> "de-DE"; case "en" -> "en-US"; case "fr" -> "fr-FR";
            case "es" -> "es-ES"; case "it" -> "it-IT"; case "pt" -> "pt-BR";
            case "nl" -> "nl-NL"; case "ru" -> "ru-RU"; case "ja" -> "ja-JP";
            case "zh" -> "zh-CN"; default -> "en-US";
        };
    }

    private static String macVoice(String l) {
        return switch (l == null ? "" : l) {
            case "de" -> "Anna"; case "fr" -> "Amelie"; case "es" -> "Maria";
            case "it" -> "Alice"; case "nl" -> "Ellen"; case "ru" -> "Yuri";
            case "ja" -> "Kyoko"; case "zh" -> "Sin-ji"; default -> "Alex";
        };
    }
}
