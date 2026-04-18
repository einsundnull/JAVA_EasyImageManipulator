package paint;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Handles the quick-open recent-projects dialog and file/directory loading logic.
 * Extracted from SelectiveAlphaEditor.
 */
class QuickOpenController {

	private final SelectiveAlphaEditor ed;

	QuickOpenController(SelectiveAlphaEditor ed) {
		this.ed = ed;
	}

	/** Show quick open dialog for the active canvas. */
	void show() {
		show(ed.activeCanvasIndex);
	}

	/** Show quick open dialog for the given canvas (default category: images). */
	void show(int canvasIdx) {
		show(canvasIdx, LastProjectsManager.CAT_IMAGES);
	}

	/** Show quick open dialog and load the chosen path into the secondary gallery (gallery2). */
	void showForGallery2(int canvasIdx, String initialCategory) {
		try {
			Map<String, List<String>> recent = LastProjectsManager.loadAll();
			StartupDialog dlg = new StartupDialog(ed, recent, StartupDialog.Mode.QUICK_OPEN, canvasIdx, initialCategory);
			dlg.setVisible(true);
			File chosen = dlg.getSelectedPath();
			if (chosen == null) return;
			ed.fileLoader.indexDirectory2(chosen, canvasIdx);
		} catch (IOException ex) {
			ed.showErrorDialog("Fehler", "Schnellauswahl konnte nicht geöffnet werden:\n" + ex.getMessage());
		}
	}

	/** Show quick open dialog with a specific initial category. */
	void show(int canvasIdx, String initialCategory) {
		try {
			Map<String, List<String>> recent = LastProjectsManager.loadAll();
			StartupDialog dlg = new StartupDialog(ed, recent, StartupDialog.Mode.QUICK_OPEN, canvasIdx,
					initialCategory);
			dlg.setVisible(true);
			File chosen = dlg.getSelectedPath();
			if (chosen == null)
				return;
			String category = dlg.getSelectedCategory();
			int gallerySlot = dlg.getSelectedGallerySlot();

			// Extra gallery slot: load into secondary gallery panel
			if (gallerySlot >= 1) {
				ed.fileLoader.indexDirectory2(chosen, canvasIdx);
				return;
			}

			if (chosen.isDirectory()) {
				// GameII game directory: has scenes/ subdir → open first scene
				File scenesSubDir = new File(chosen, "scenes");
				if (scenesSubDir.exists() && scenesSubDir.isDirectory()) {
					File[] sceneDirs = scenesSubDir.listFiles(
							f -> f.isDirectory() && new File(f, "sprites").isDirectory());
					if (sceneDirs != null && sceneDirs.length > 0) {
						Arrays.sort(sceneDirs, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
						ed.loadGameIISceneDir(sceneDirs[0], canvasIdx);
						return;
					}
				}
				// GameII scene directory: directly has sprites/ subdir
				if (new File(chosen, "sprites").isDirectory()) {
					ed.loadGameIISceneDir(chosen, canvasIdx);
					return;
				}
				// Standard: image directory
				File[] images = chosen.listFiles(f -> f.isFile() && SelectiveAlphaEditor.isSupportedFile(f));
				if (images != null && images.length > 0) {
					Arrays.sort(images, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
					ed.indexDirectory(images[0], canvasIdx, category);
				}
			} else {
				ed.loadFile(chosen, canvasIdx);
				try {
					LastProjectsManager.addRecent(category, chosen.getParent());
				} catch (IOException ignored) {
				}
			}
		} catch (IOException ex) {
			ed.showErrorDialog("Fehler", "Schnellauswahl konnte nicht geöffnet werden:\n" + ex.getMessage());
		}
	}
}
