package paint;

import java.io.IOException;

/**
 * Text-to-Speech utility for speaking map content.
 * Uses system speech capabilities:
 * - Windows: PowerShell with SAPI
 * - macOS: native 'say' command
 * - Linux: espeak (if available)
 */
public class TextToSpeech {

    /**
     * Speaks the given text in the specified language.
     * Runs asynchronously in a background thread.
     */
    public static void speak(String text, String language) {
        if (text == null || text.isEmpty()) {
            return;
        }

        // Run speech in background thread to avoid blocking UI
        new Thread(() -> speakBlocking(text, language)).start();
    }

    /**
     * Blocking version of speak (runs in background thread).
     */
    private static void speakBlocking(String text, String language) {
        String os = System.getProperty("os.name").toLowerCase();

        try {
            if (os.contains("win")) {
                // Windows: use PowerShell with SAPI
                speakWindows(text, language);
            } else if (os.contains("mac")) {
                // macOS: use 'say' command
                speakMacOS(text, language);
            } else if (os.contains("linux")) {
                // Linux: use espeak
                speakLinux(text, language);
            }
        } catch (Exception ex) {
            System.err.println("[ERROR] Text-to-Speech failed: " + ex.getMessage());
        }
    }

    /**
     * Windows speech using PowerShell + SAPI.
     */
    private static void speakWindows(String text, String language) throws IOException {
        // Escape quotes in text
        String escapedText = text.replace("\"", "\\\"").replace("\n", " ");

        // Map language codes to SAPI voices (German, English, French, etc.)
        String voiceLanguage = getWindowsVoiceLanguage(language);

        // PowerShell command to speak text
        String psCommand = String.format(
                "Add-Type -AssemblyName System.Speech; " +
                        "$speak = New-Object System.Speech.Synthesis.SpeechSynthesizer; " +
                        "$speak.SelectVoiceByHints([System.Speech.Synthesis.VoiceGender]::NotSet, [System.Speech.Synthesis.VoiceAge]::NotSet, 100, [System.Globalization.CultureInfo]::GetCultureInfo('%s')); " +
                        "$speak.Speak(\\\"%s\\\")",
                voiceLanguage, escapedText);

        ProcessBuilder pb = new ProcessBuilder("powershell", "-Command", psCommand);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        // Wait for completion with timeout
        try {
            process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * macOS speech using 'say' command.
     */
    private static void speakMacOS(String text, String language) throws IOException {
        String voiceLanguage = getMacOSVoiceLanguage(language);

        ProcessBuilder pb = new ProcessBuilder("say", "-v", voiceLanguage, text);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        try {
            process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Linux speech using espeak.
     */
    private static void speakLinux(String text, String language) throws IOException {
        String voiceLanguage = getLinuxVoiceLanguage(language);

        ProcessBuilder pb = new ProcessBuilder("espeak", "-v", voiceLanguage, text);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        try {
            process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Map language codes to Windows SAPI voice locale codes.
     */
    private static String getWindowsVoiceLanguage(String lang) {
        return switch (lang.toLowerCase()) {
            case "de" -> "de-DE";  // German
            case "en" -> "en-US";  // English
            case "fr" -> "fr-FR";  // French
            case "es" -> "es-ES";  // Spanish
            case "it" -> "it-IT";  // Italian
            case "pt" -> "pt-BR";  // Portuguese
            case "nl" -> "nl-NL";  // Dutch
            case "ru" -> "ru-RU";  // Russian
            case "ja" -> "ja-JP";  // Japanese
            case "zh" -> "zh-CN";  // Chinese
            default -> "en-US";    // Default to English
        };
    }

    /**
     * Map language codes to macOS voice names.
     */
    private static String getMacOSVoiceLanguage(String lang) {
        return switch (lang.toLowerCase()) {
            case "de" -> "Anna";        // German
            case "en" -> "Alex";        // English
            case "fr" -> "Amelie";      // French
            case "es" -> "Maria";       // Spanish
            case "it" -> "Alice";       // Italian
            case "pt" -> "Luciana";     // Portuguese
            case "nl" -> "Ellen";       // Dutch
            case "ru" -> "Yuri";        // Russian
            case "ja" -> "Kyoko";       // Japanese
            case "zh" -> "Sin-ji";      // Chinese
            default -> "Alex";          // Default to English
        };
    }

    /**
     * Map language codes to espeak voice codes.
     */
    private static String getLinuxVoiceLanguage(String lang) {
        return switch (lang.toLowerCase()) {
            case "de" -> "de";  // German
            case "en" -> "en";  // English
            case "fr" -> "fr";  // French
            case "es" -> "es";  // Spanish
            case "it" -> "it";  // Italian
            case "pt" -> "pt";  // Portuguese
            case "nl" -> "nl";  // Dutch
            case "ru" -> "ru";  // Russian
            case "ja" -> "ja";  // Japanese
            case "zh" -> "zh";  // Chinese
            default -> "en";    // Default to English
        };
    }
}
