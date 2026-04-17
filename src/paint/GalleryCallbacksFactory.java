package paint;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

/**
 * Factory for TileGalleryPanel.Callbacks (image gallery). Extracted from
 * SelectiveAlphaEditor.buildGalleryCallbacks().
 */
class GalleryCallbacksFactory {

	static TileGalleryPanel.Callbacks build(SelectiveAlphaEditor ed, int idx) {
		return new TileGalleryPanel.Callbacks() {
			@Override
			public void onTileOpened(File f) {
				if (ed.ci(idx).scenesPanel != null)
					ed.ci(idx).scenesPanel.clearActiveAndSelection();
				ed.loadFile(f, idx);
			}

			@Override
			public void onSelectionChanged(List<File> files) {
				ed.selectedImages = files;
				if (!files.isEmpty() && ed.ci(idx).scenesPanel != null)
					ed.ci(idx).scenesPanel.clearActiveAndSelection();
			}

			@Override
			public void onDragStarted(File file) {
				if (idx == 0 && !ed.secondCanvasBtn.isSelected() && ed.ci(0).workingImage != null) {
					ed.repositionRightDropZone();
					ed.rightDropZone.setVisible(true);
					ed.ci(0).layeredPane.repaint();
				}
			}

			@Override
			public void onDragEnded() {
				ed.rightDropZone.setVisible(false);
				if (ed.ci(0).layeredPane != null)
					ed.ci(0).layeredPane.repaint();
			}

			// ── Case 5: LayerTile dropped onto TileGallery → save as PNG ─────
			@Override
			public void onLayerDropped(Layer layer) {
				CanvasInstance c = ed.canvases[idx];
				if (c.sourceFile == null)
					return;
				Layer live = c.activeElements.stream()
						.filter(e -> e.id() == layer.id()).findFirst().orElse(layer);
				java.awt.image.BufferedImage img = ed.renderLayerToImage(live);
				if (img == null)
					return;
				File target = ed.uniqueLayerExportFile(c.sourceFile, live.id());
				ed.saveLayerAsImageFile(img, target, idx);
				ToastNotification.show(ed, "Gespeichert: " + target.getName());
			}

			// ── Right-drag from any panel onto image gallery ─────────────────
			@Override
			public void onFileElementDropped(File src, int insertIndex) {
				CanvasInstance c = ed.canvases[idx];
				boolean isScene = src.getName().endsWith(".txt") || src.getName().endsWith(".json");
				if (isScene) {
					new javax.swing.SwingWorker<File, Void>() {
						@Override
						protected File doInBackground() throws Exception {
							SceneImageAdapter.SceneAsImage s = SceneImageAdapter.loadSceneAsImage(src);
							if (s == null || s.thumbnail == null)
								return null;
							String base = src.getName().replaceAll("\\.(txt|json)$", "");
							File dir = (c.sourceFile != null) ? c.sourceFile.getParentFile()
									: SceneLocator.getToolScenesDir(
											SceneLocator.getToolProjects().isEmpty() ? "Default"
													: SceneLocator.getToolProjects().get(0));
							File out = new File(dir, base + ".png");
							int n = 1;
							while (out.exists())
								out = new File(dir, base + "_" + n++ + ".png");
							javax.imageio.ImageIO.write(s.thumbnail, "PNG", out);
							return out;
						}

						@Override
						protected void done() {
							try {
								File out = get();
								if (out == null)
									return;
								c.tileGallery.addFileAtIndex(out, insertIndex);
								c.tileGallery.setActiveFile(out);
								ToastNotification.show(ed, "Scene → Bild: " + out.getName());
							} catch (Exception ex) {
								ed.showErrorDialog("Fehler", ex.getMessage());
							}
						}
					}.execute();
				} else {
					File dest = BaseSidebarPanel.copyFileWithUniqueName(src, src.getParentFile());
					if (dest != null) {
						c.tileGallery.addFileAtIndex(dest, insertIndex);
						c.tileGallery.setActiveFile(dest);
						ToastNotification.show(ed, "Kopie: " + dest.getName());
					}
				}
			}

			@Override
			public BufferedImage getCompositeForFile(File f) {
				CanvasInstance c = ed.canvases[idx];
				if (f.equals(c.sourceFile) && c.workingImage != null)
					return ed.elementController.renderCompositeForThumbnail(c);
				return null;
			}
		};
	}

	/** Callbacks for the second (optional) gallery panel — loads into the same canvas as the primary. */
	static TileGalleryPanel.Callbacks buildGallery2(SelectiveAlphaEditor ed, int idx) {
		return new TileGalleryPanel.Callbacks() {
			@Override
			public void onTileOpened(File f) {
				if (ed.ci(idx).scenesPanel != null)
					ed.ci(idx).scenesPanel.clearActiveAndSelection();
				ed.loadFileIntoGallery2(f, idx);
			}

			@Override
			public void onSelectionChanged(List<File> files) {
				ed.selectedImages = files;
				if (!files.isEmpty() && ed.ci(idx).scenesPanel != null)
					ed.ci(idx).scenesPanel.clearActiveAndSelection();
			}

			@Override
			public void onDragStarted(File file) {
				if (idx == 0 && !ed.secondCanvasBtn.isSelected() && ed.ci(0).workingImage != null) {
					ed.repositionRightDropZone();
					ed.rightDropZone.setVisible(true);
					ed.ci(0).layeredPane.repaint();
				}
			}

			@Override
			public void onDragEnded() {
				ed.rightDropZone.setVisible(false);
				if (ed.ci(0).layeredPane != null)
					ed.ci(0).layeredPane.repaint();
			}

			@Override
			public void onLayerDropped(Layer layer) {
				CanvasInstance c = ed.canvases[idx];
				if (c.sourceFile == null)
					return;
				Layer live = c.activeElements.stream()
						.filter(e -> e.id() == layer.id()).findFirst().orElse(layer);
				java.awt.image.BufferedImage img = ed.renderLayerToImage(live);
				if (img == null)
					return;
				File target = ed.uniqueLayerExportFile(c.sourceFile, live.id());
				ed.saveLayerAsImageFile(img, target, idx);
				ToastNotification.show(ed, "Gespeichert: " + target.getName());
			}

			@Override
			public void onFileElementDropped(File src, int insertIndex) {
				CanvasInstance c = ed.canvases[idx];
				if (c.tileGallery2 == null) return;
				boolean isScene = src.getName().endsWith(".txt") || src.getName().endsWith(".json");
				if (isScene) {
					new javax.swing.SwingWorker<File, Void>() {
						@Override protected File doInBackground() throws Exception {
							SceneImageAdapter.SceneAsImage s = SceneImageAdapter.loadSceneAsImage(src);
							if (s == null || s.thumbnail == null) return null;
							String base = src.getName().replaceAll("\\.(txt|json)$", "");
							File dir = (c.sourceFile != null) ? c.sourceFile.getParentFile()
									: SceneLocator.getToolScenesDir(
										SceneLocator.getToolProjects().isEmpty() ? "Default"
												: SceneLocator.getToolProjects().get(0));
							File out = new File(dir, base + ".png");
							int n = 1;
							while (out.exists()) out = new File(dir, base + "_" + n++ + ".png");
							javax.imageio.ImageIO.write(s.thumbnail, "PNG", out);
							return out;
						}
						@Override protected void done() {
							try {
								File out = get();
								if (out == null) return;
								c.tileGallery2.addFileAtIndex(out, insertIndex);
								c.tileGallery2.setActiveFile(out);
								ToastNotification.show(ed, "Scene → Bild: " + out.getName());
							} catch (Exception ex) { ed.showErrorDialog("Fehler", ex.getMessage()); }
						}
					}.execute();
				} else {
					File dest = BaseSidebarPanel.copyFileWithUniqueName(src, src.getParentFile());
					if (dest != null) {
						c.tileGallery2.addFileAtIndex(dest, insertIndex);
						c.tileGallery2.setActiveFile(dest);
						ToastNotification.show(ed, "Kopie: " + dest.getName());
					}
				}
			}

			@Override
			public BufferedImage getCompositeForFile(File f) {
				CanvasInstance c = ed.canvases[idx];
				if (f.equals(c.sourceFile) && c.workingImage != null)
					return ed.elementController.renderCompositeForThumbnail(c);
				return null;
			}
		};
	}

	static TileGalleryPanel.FilePreloadCallback buildPreloadCallback(SelectiveAlphaEditor ed, int idx) {
		return (file) -> ed.preloadFileAsync(file, idx);
	}
}
