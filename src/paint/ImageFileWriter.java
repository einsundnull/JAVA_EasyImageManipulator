package paint;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import javax.imageio.ImageIO;

/**
 * Rolle: der EINZIGE Weg, ein Bild als Datei zu schreiben (Univ. §6).
 * Gegenstück zu {@link ImageLoader} — dort wird gelesen, hier geschrieben.
 *
 * <p>Schreibt <b>atomar</b>: erst in eine Nachbardatei, dann Umbenennen. Ein
 * Absturz oder ein voller Datenträger mitten im Schreiben lässt das Original
 * damit unangetastet, statt es zu zerstören (Befund F02). Und der
 * Rückgabewert von {@code ImageIO.write} wird geprüft — er ist {@code false},
 * wenn kein Writer gefunden wurde, <b>ohne</b> dass eine Exception fliegt
 * (Befund F03).
 *
 * <p>Was diese Klasse ausdrücklich <b>nicht</b> tut: Sicherungskopien anlegen,
 * Dateinamen erfinden, Formate außer PNG schreiben, oder den Benutzer fragen.
 */
final class ImageFileWriter {

	private ImageFileWriter() {
	}

	/**
	 * Schreibt {@code img} als PNG nach {@code target} — atomar und geprüft.
	 *
	 * <p>Ablauf: Zielverzeichnis anlegen → in eine Temp-Datei <b>im selben
	 * Verzeichnis</b> schreiben → umbenennen. Das Verzeichnis ist Absicht:
	 * {@code ATOMIC_MOVE} funktioniert nur innerhalb eines Datenträgers, eine
	 * Temp-Datei in {@code %TEMP%} läge unter Umständen auf einem anderen.
	 *
	 * <p>Scheitert irgendetwas, wird die Temp-Datei entfernt und eine
	 * {@link IOException} geworfen. Das Ziel bleibt dann in dem Zustand, in dem
	 * es vorher war — es gibt keinen Zwischenzustand, in dem es halb
	 * geschrieben ist.
	 *
	 * @throws IOException wenn kein PNG-Writer existiert, das Schreiben
	 *                     fehlschlägt oder das Umbenennen nicht gelingt
	 */
	static void writePng(BufferedImage img, File target) throws IOException {
		if (img == null)
			throw new IOException("Kein Bild zum Speichern (null).");
		if (target == null)
			throw new IOException("Kein Zielpfad zum Speichern (null).");

		Path targetPath = target.toPath().toAbsolutePath();
		Path dir = targetPath.getParent();
		if (dir != null)
			Files.createDirectories(dir);

		Path tmp = Files.createTempFile(dir, target.getName() + ".", ".part");
		try {
			boolean written;
			try (OutputStream out = Files.newOutputStream(tmp)) {
				written = ImageIO.write(img, "PNG", out);
			}
			if (!written)
				throw new IOException("Kein PNG-Writer verfügbar — nichts geschrieben: "
						+ targetPath);

			try {
				Files.move(tmp, targetPath, StandardCopyOption.ATOMIC_MOVE,
						StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException e) {
				// Kommt auf manchen Netz-/Fremddateisystemen vor. Dann eben
				// nicht atomar — immer noch besser als in das Original hinein
				// zu schreiben, denn die Daten sind zu diesem Zeitpunkt bereits
				// vollständig auf dem Datenträger.
				Files.move(tmp, targetPath, StandardCopyOption.REPLACE_EXISTING);
			}
			tmp = null; // erfolgreich verschoben, nichts mehr aufzuräumen
		} finally {
			if (tmp != null) {
				try {
					Files.deleteIfExists(tmp);
				} catch (IOException suppressed) {
					System.err.println("[ImageFileWriter] Temp-Datei blieb liegen: "
							+ tmp + " (" + suppressed.getMessage() + ")");
				}
			}
		}
	}

	/**
	 * Schreibt {@code img} als PNG in einen Strom. Für Ziele, die keine Datei
	 * sind (z. B. die Base64-Kodierung in {@code SceneSerializer}) — dort gibt
	 * es nichts zu zerstören, geprüft wird trotzdem (F03).
	 *
	 * <p>Der Strom wird <b>nicht</b> geschlossen; das bleibt beim Aufrufer.
	 */
	static void writePng(BufferedImage img, OutputStream out) throws IOException {
		if (img == null)
			throw new IOException("Kein Bild zum Speichern (null).");
		if (!ImageIO.write(img, "PNG", out))
			throw new IOException("Kein PNG-Writer verfügbar — nichts geschrieben.");
	}
}
