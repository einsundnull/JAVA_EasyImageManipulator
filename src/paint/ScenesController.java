package paint;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.SwingUtilities;

/**
 * Handles scene panel callbacks, scene loading (including GameII scenes),
 * and scene creation from dropped files. Extracted from SelectiveAlphaEditor.
 */
class ScenesController {

	private final SelectiveAlphaEditor ed;

	ScenesController(SelectiveAlphaEditor ed) {
		this.ed = ed;
	}

	TileGalleryPanel.Callbacks buildScenesCallbacks(int idx) {
		return new TileGalleryPanel.Callbacks() {
			@Override
			public void onTileOpened(File sceneFile) {
				if (sceneFile.isDirectory()) {
					if (new File(sceneFile, "sprites").isDirectory()) {
						ed.ci(idx).tileGallery.clearActiveAndSelection();
						loadGameIISceneDir(sceneFile, idx);
					}
					return;
				}
				if (!sceneFile.getName().endsWith(".txt") && !sceneFile.getName().endsWith(".json"))
					return;
				ed.ci(idx).tileGallery.clearActiveAndSelection();
				CanvasInstance c = ed.ci(idx);
				try {
					File sceneDir = sceneFile.getParentFile();
					String sceneName = sceneFile.getName().replaceAll("\\.(txt|json)$", "");

					File spritesSubDir = new File(sceneDir, "sprites");
					if (spritesSubDir.exists() && spritesSubDir.isDirectory()) {
						// ── GameII-Scene ──────────────────────────────────
						GameSceneReader.GameSceneData gameData =
								GameSceneReader.readScene(sceneDir, sceneName);

						if (gameData.backgroundImage != null) {
							c.workingImage  = ed.normalizeImage(gameData.backgroundImage);
							c.originalImage = c.workingImage;
						} else if (c.workingImage == null
								|| c.workingImage.getWidth()  != gameData.canvasW
								|| c.workingImage.getHeight() != gameData.canvasH) {
							java.awt.image.BufferedImage blank = new java.awt.image.BufferedImage(
									gameData.canvasW, gameData.canvasH,
									java.awt.image.BufferedImage.TYPE_INT_ARGB);
							java.awt.Graphics2D g2 = blank.createGraphics();
							g2.setColor(new java.awt.Color(30, 30, 40));
							g2.fillRect(0, 0, gameData.canvasW, gameData.canvasH);
							g2.dispose();
							c.workingImage  = blank;
							c.originalImage = blank;
						}

						c.gameSceneRoot    = sceneDir;
						c.gameCanvasW      = gameData.canvasW;
						c.gameCanvasH      = gameData.canvasH;
						c.activeElements   = gameData.layers;
						c.selectedElements.clear();
						c.activeSceneFile  = sceneFile;
						c.sourceFile       = sceneFile;
						ed.refreshElementPanel();
						c.canvasPanel.repaint();

					} else {
						// ── Standard TT-Scene ─────────────────────────────
						SceneFileReader.SceneData sceneData = SceneFileReader.readScene(sceneDir, sceneName);

						if (sceneData.backgroundImage != null) {
							ed.loadSceneBackground(sceneData.backgroundImage, idx);
						}

						List<Layer> allLayers = new ArrayList<>();
						int nextId = System.identityHashCode(new Object());
						for (SceneFileReader.ImageLayerRef ref : sceneData.imageLayers) {
							java.awt.image.BufferedImage img = ImageLoader.loadImage(ref.file);
							if (img != null) {
								int w = ref.w > 0 ? ref.w : img.getWidth();
								int h = ref.h > 0 ? ref.h : img.getHeight();
								ImageLayer il = new ImageLayer(nextId++, img, ref.x, ref.y, w, h, ref.rotation, ref.opacity);
								allLayers.add(il);
							}
						}
						allLayers.addAll(sceneData.textLayers);
						allLayers.addAll(sceneData.pathLayers);

						c.gameSceneRoot    = null;
						c.activeElements   = allLayers;
						c.selectedElements.clear();
						c.activeSceneFile  = sceneFile;
						ed.refreshElementPanel();
						c.canvasPanel.repaint();
					}

				} catch (Exception e) {
					System.err.println("[ERROR] loadScene failed: " + e.getMessage());
					ed.showErrorDialog("Fehler", "Scene konnte nicht geladen werden:\n" + e.getMessage());
				}
			}

			@Override
			public void onSelectionChanged(List<File> selectedFiles) {
				if (!selectedFiles.isEmpty())
					ed.ci(idx).tileGallery.clearActiveAndSelection();
			}

			@Override
			public void onDragStarted(File file) {
				// Scene tiles dragged out — no drop zone needed
			}

			@Override
			public void onDragEnded() {
				ed.rightDropZone.setVisible(false);
				if (ed.ci(0).layeredPane != null)
					ed.ci(0).layeredPane.repaint();
			}

			// ── Right-drag from any panel onto scenes gallery ─────────────────
			@Override
			public void onFileElementDropped(File src, int insertIndex) {
				boolean isScene = src.getName().endsWith(".txt") || src.getName().endsWith(".json");
				if (isScene) {
					ed.copySceneDirectory(src, idx);
				} else {
					createSceneFromDrop(Arrays.asList(src), idx);
				}
			}

			// ── Layer dragged from ElementLayerPanel onto scenes gallery ──────
			@Override
			public void onLayerDropped(Layer layer) {
				CanvasInstance c = ed.ci(idx);
				java.awt.image.BufferedImage img = ed.renderLayerToImage(layer);
				if (img == null)
					return;
				try {
					File tmp = File.createTempFile("layer_scene_", ".png");
					tmp.deleteOnExit();
					javax.imageio.ImageIO.write(img, "PNG", tmp);
					createSceneFromDrop(Arrays.asList(tmp), idx);
				} catch (Exception ex) {
					ed.showErrorDialog("Fehler", "Layer → Scene fehlgeschlagen:\n" + ex.getMessage());
				}
			}

			@Override
			public void onRenameRequested(File sceneFile) {
				renameSceneFile(sceneFile, idx);
			}
		};
	}

	private void renameSceneFile(File sceneFile, int idx) {
		if (sceneFile == null || !sceneFile.exists()) return;
		String oldName = sceneFile.getName().replaceAll("\\.(txt|json)$", "");
		String newName = (String) javax.swing.JOptionPane.showInputDialog(
				ed, "Neuer Szenenname:", "Szene umbenennen",
				javax.swing.JOptionPane.PLAIN_MESSAGE, null, null, oldName);
		if (newName == null || newName.isBlank() || newName.equals(oldName)) return;
		newName = newName.trim();
		String ext = sceneFile.getName().endsWith(".json") ? ".json" : ".txt";
		File newFile = new File(sceneFile.getParentFile(), newName + ext);
		if (newFile.exists()) {
			javax.swing.JOptionPane.showMessageDialog(ed, "Name bereits vorhanden.", "Fehler",
					javax.swing.JOptionPane.ERROR_MESSAGE);
			return;
		}
		if (!sceneFile.renameTo(newFile)) {
			javax.swing.JOptionPane.showMessageDialog(ed, "Umbenennen fehlgeschlagen.", "Fehler",
					javax.swing.JOptionPane.ERROR_MESSAGE);
			return;
		}
		refreshSceneFiles(idx);
	}

	void createSceneFromDrop(List<File> files, int idx) {
		if (files == null || files.isEmpty())
			return;
		File imageFile = files.get(0);
		String n = imageFile.getName().toLowerCase();
		boolean isImage = n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg")
				|| n.endsWith(".gif") || n.endsWith(".bmp") || n.endsWith(".webp");
		if (!isImage)
			return;

		CanvasInstance c = ed.ci(idx);

		// Auto-generate scene name from image file + timestamp
		String sceneName = imageFile.getName().replaceAll("\\.[^.]+$", "") + "_"
				+ new java.text.SimpleDateFormat("HHmmss").format(new java.util.Date());

		List<String> projects = SceneLocator.getToolProjects();
		String projectName = projects.isEmpty() ? "Default" : projects.get(0);
		File scenesRoot = SceneLocator.getToolScenesDir(projectName);
		File sceneDir   = new File(scenesRoot, sceneName);

		// Save exactly what's on the canvas + current layers (no cached manifest lookup,
		// no copy-of-disk-file background). If the canvas has a workingImage we snapshot
		// it to a temp PNG; otherwise fall back to the dropped file on disk.
		final List<Layer> layers = new ArrayList<>(c.activeElements);
		final java.awt.image.BufferedImage canvasSnapshot =
				c.workingImage != null ? ed.deepCopy(c.workingImage) : null;
		final File fallbackFile = imageFile;
		final String finalName = sceneName;
		new javax.swing.SwingWorker<Void, Void>() {
			@Override
			protected Void doInBackground() throws Exception {
				File bgFile;
				if (canvasSnapshot != null) {
					bgFile = File.createTempFile("scene_bg_", ".png");
					bgFile.deleteOnExit();
					javax.imageio.ImageIO.write(canvasSnapshot, "PNG", bgFile);
				} else {
					bgFile = fallbackFile;
				}
				SceneFileWriter.writeScene(sceneDir, finalName, bgFile, layers);
				return null;
			}

			@Override
			protected void done() {
				try {
					get();
				} catch (Exception ex) {
					System.err.println("[ERROR] Scene save failed: " + ex.getMessage());
					ed.showErrorDialog("Fehler", "Scene konnte nicht gespeichert werden:\n" + ex.getMessage());
					return;
				}
				refreshSceneFiles(idx);
				ToastNotification.show(ed, "Scene gespeichert: " + finalName);
			}
		}.execute();
	}

	/** Collects all scene .txt files from SceneLocator and populates the scenes panel. */
	void refreshSceneFiles(int idx) {
		CanvasInstance c = ed.ci(idx);
		List<File> allScenes = new ArrayList<>();
		for (String project : SceneLocator.getToolProjects()) {
			allScenes.addAll(SceneLocator.getToolScenes(project));
		}
		for (String game : SceneLocator.getAvailableGames()) {
			allScenes.addAll(SceneLocator.getGameScenes(game));
		}
		for (String game : SceneLocator.getAppDataGames()) {
			allScenes.addAll(SceneLocator.getAppDataGameScenes(game));
		}
		c.scenesPanel.setFiles(allScenes, null);
	}

	void loadGameIISceneDir(File sceneDir, int idx) {
		CanvasInstance c = ed.ci(idx);
		String sceneName = sceneDir.getName();
		try {
			GameSceneReader.GameSceneData gameData = GameSceneReader.readScene(sceneDir, sceneName);

			if (gameData.backgroundImage != null) {
				c.workingImage  = ed.normalizeImage(gameData.backgroundImage);
				c.originalImage = c.workingImage;
			} else if (c.workingImage == null
					|| c.workingImage.getWidth()  != gameData.canvasW
					|| c.workingImage.getHeight() != gameData.canvasH) {
				java.awt.image.BufferedImage blank = new java.awt.image.BufferedImage(
						gameData.canvasW, gameData.canvasH,
						java.awt.image.BufferedImage.TYPE_INT_ARGB);
				java.awt.Graphics2D g2 = blank.createGraphics();
				g2.setColor(new java.awt.Color(30, 30, 40));
				g2.fillRect(0, 0, gameData.canvasW, gameData.canvasH);
				g2.dispose();
				c.workingImage  = blank;
				c.originalImage = blank;
			}

			File syntheticManifest = new File(sceneDir, sceneName + ".txt");
			if (!syntheticManifest.exists()) {
				try {
					GameSceneWriter.writeManifest(sceneDir, sceneName, gameData.layers, gameData.canvasW, gameData.canvasH);
				} catch (IOException e) {
					System.err.println("[ScenesController] Manifest konnte nicht erstellt werden: " + e.getMessage());
				}
			}
			c.gameSceneRoot   = sceneDir;
			c.gameCanvasW     = gameData.canvasW;
			c.gameCanvasH     = gameData.canvasH;
			c.activeElements  = gameData.layers;
			c.selectedElements.clear();
			c.activeSceneFile = syntheticManifest;
			c.sourceFile      = syntheticManifest;
			ed.ci(idx).tileGallery.clearActiveAndSelection();
			ed.activeCanvasIndex = idx;
			ed.updateCanvasFocusBorder();
			ed.swapToImageView(idx);
			SwingUtilities.invokeLater(() -> ed.fitToViewport(idx));
			ed.refreshElementPanel();
			ed.updateTitle();
			ed.updateStatus();
			ed.setBottomButtonsEnabled(true);

			File gameScenesDir = sceneDir.getParentFile();
			List<File> gameScenes = new ArrayList<>();
			if (gameScenesDir != null && gameScenesDir.isDirectory()) {
				File[] siblings = gameScenesDir.listFiles(File::isDirectory);
				if (siblings != null) {
					Arrays.sort(siblings, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
					for (File s : siblings) {
						if (new File(s, "sprites").isDirectory())
							gameScenes.add(s);
					}
				}
			}
			if (idx == 0)
				ed.scenesBtn.setSelected(true);
			else if (idx == 1)
				ed.secondScenesBtn.setSelected(true);
			c.scenesPanel.setVisible(true);
			c.scenesPanel.setFiles(gameScenes, sceneDir);
			ed.updateLayoutVisibility();
			SwingUtilities.invokeLater(() -> SwingUtilities.invokeLater(() -> ed.reloadCurrentImage(idx)));

		} catch (Exception e) {
			System.err.println("[ERROR] loadGameIISceneDir failed: " + e.getMessage());
			ed.showErrorDialog("Fehler", "GameII-Scene konnte nicht geladen werden:\n" + e.getMessage());
		}
	}

	static String extractJsonField(String json, String fieldName) {
		String pattern = "\"" + fieldName + "\":";
		int idx = json.indexOf(pattern);
		if (idx < 0)
			return null;

		int start = idx + pattern.length();
		while (start < json.length() && Character.isWhitespace(json.charAt(start)))
			start++;

		if (start >= json.length() || json.charAt(start) != '[')
			return null;

		int depth = 0;
		int end = start;
		while (end < json.length()) {
			if (json.charAt(end) == '[')
				depth++;
			if (json.charAt(end) == ']') {
				depth--;
				if (depth == 0) {
					end++;
					break;
				}
			}
			end++;
		}
		return json.substring(start, end).trim();
	}

	// ── Directory copy (for scene duplication) ────────────────────────────────

	/**
	 * Copies an entire scene directory to a new name in the same scenes root,
	 * then refreshes the scene panel.
	 */
	void copySceneDirectory(File sceneFile, int idx) {
		File srcDir = sceneFile.getParentFile();
		File scenesRoot = srcDir.getParentFile();
		String baseName = srcDir.getName();
		File destDir = new File(scenesRoot, baseName + "_copy");
		int n = 2;
		while (destDir.exists()) destDir = new File(scenesRoot, baseName + "_copy" + n++);
		File finalDest = destDir;
		new javax.swing.SwingWorker<Void, Void>() {
			@Override protected Void doInBackground() throws Exception {
				copyDirRecursive(srcDir, finalDest);
				File oldTxt = new File(finalDest, baseName + ".txt");
				File newTxt = new File(finalDest, finalDest.getName() + ".txt");
				if (oldTxt.exists()) oldTxt.renameTo(newTxt);
				return null;
			}
			@Override protected void done() {
				try { get(); } catch (Exception ex) { ed.showErrorDialog("Fehler", ex.getMessage()); return; }
				ed.refreshSceneFiles(idx);
				ToastNotification.show(ed, "Scene kopiert: " + finalDest.getName());
			}
		}.execute();
	}

	private static void copyDirRecursive(File src, File dest) throws java.io.IOException {
		dest.mkdirs();
		for (File f : src.listFiles()) {
			File d = new File(dest, f.getName());
			if (f.isDirectory()) copyDirRecursive(f, d);
			else java.nio.file.Files.copy(f.toPath(), d.toPath(),
					java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
