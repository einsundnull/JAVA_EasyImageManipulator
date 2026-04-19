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
			public boolean hasSelectedLayers() { return !ed.ci(idx).selectedElements.isEmpty(); }

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
					File destDir = c.tileGallery.getTileGalleryDirectory();
					if (destDir == null) return;
					if (src.getParentFile().equals(destDir)) {
						// Same gallery — duplicate with unique name
						File dest = BaseSidebarPanel.copyFileWithUniqueName(src, destDir);
						if (dest != null) {
							c.tileGallery.addFileAtIndex(dest, insertIndex);
							c.tileGallery.setActiveFile(dest);
							ToastNotification.show(ed, "Kopiert: " + dest.getName());
						}
						return;
					}
					File destFile = new File(destDir, src.getName());
					if (destFile.exists()) {
						c.tileGallery.addFileAtIndex(destFile, insertIndex);
						c.tileGallery.setActiveFile(destFile);
					} else {
						try {
							java.nio.file.Files.copy(src.toPath(), destFile.toPath());
							c.tileGallery.addFileAtIndex(destFile, insertIndex);
							c.tileGallery.setActiveFile(destFile);
							ToastNotification.show(ed, "Kopiert: " + destFile.getName());
						} catch (java.io.IOException ex) {
							System.err.println("[GalleryCallbacks] copy failed: " + ex.getMessage());
						}
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
			public boolean hasSelectedLayers() { return !ed.ci(idx).selectedElements.isEmpty(); }

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

	// ── Book panel pair ───────────────────────────────────────────────────────

	/**
	 * Creates a linked (listPanel, pagesPanel) pair for book navigation.
	 * Clicking a book in the list fills the pages panel; clicking a page loads it.
	 * Returns { listPanel, pagesPanel }.
	 */
	static TileGalleryPanel[] buildBookPanelPair(SelectiveAlphaEditor ed, int idx) {
		File[]             currentBook  = { null };
		TileGalleryPanel[] pagesPanelRef = { null };
		TileGalleryPanel[] listPanelRef  = { null };

		// ── Pages panel ───────────────────────────────────────────────────────
		pagesPanelRef[0] = new TileGalleryPanel(
			new TileGalleryPanel.Callbacks() {
				@Override public void onTileOpened(File pageFile) {
					ed.loadFile(pageFile, idx);
				}
				@Override public void onSelectionChanged(List<File> sel) {}
				@Override public void onRenameRequested(File pageFile) {
					if (currentBook[0] == null) return;
					String cur = pageFile.getName().replaceAll("\\.png$", "");
					String newName = (String) javax.swing.JOptionPane.showInputDialog(
							ed, "Neuer Seitenname:", "Seite umbenennen",
							javax.swing.JOptionPane.PLAIN_MESSAGE, null, null, cur);
					if (newName == null || newName.isBlank() || newName.equals(cur)) return;
					try {
						BookController.renamePage(pageFile, newName.trim(), currentBook[0]);
						pagesPanelRef[0].setFiles(BookController.listPages(currentBook[0]), null);
					} catch (java.io.IOException ex) {
						ed.showErrorDialog("Fehler", "Umbenennen fehlgeschlagen:\n" + ex.getMessage());
					}
				}
				@Override public void onFileElementDropped(File src, int insertIndex) {
					if (currentBook[0] == null) return;
					boolean isPage = src.getParent() != null
							&& new java.io.File(src.getParent()).getName().equals("pages");
					if (isPage) {
						try {
							BookController.copyPage(src, currentBook[0]);
							pagesPanelRef[0].setFiles(BookController.listPages(currentBook[0]), null);
						} catch (java.io.IOException ex) {
							ed.showErrorDialog("Fehler", ex.getMessage());
						}
					} else {
						// Immediately create a page — load scene layers from the image if available
						java.util.List<Layer> layers = BookController.loadLayersForFile(src);
						ed.bookController.createPageFromFile(src, layers, currentBook[0],
								() -> javax.swing.SwingUtilities.invokeLater(
										() -> pagesPanelRef[0].setFiles(BookController.listPages(currentBook[0]), null)));
					}
				}
				@Override public void onLayerDropped(Layer layer) {
					if (currentBook[0] == null) return;
					java.awt.image.BufferedImage img = ed.renderLayerToImage(layer);
					if (img == null) return;
					try {
						java.io.File tmp = java.io.File.createTempFile("layer_page_", ".png");
						tmp.deleteOnExit();
						javax.imageio.ImageIO.write(img, "PNG", tmp);
						java.util.List<Layer> layers = new java.util.ArrayList<>();
						layers.add(layer);
						ed.bookController.createPageFromFile(tmp, layers, currentBook[0],
								() -> javax.swing.SwingUtilities.invokeLater(
										() -> pagesPanelRef[0].setFiles(BookController.listPages(currentBook[0]), null)));
					} catch (java.io.IOException ex) {
						ed.showErrorDialog("Fehler", ex.getMessage());
					}
				}
			},
			null, "Seiten", null,
			() -> { if (currentBook[0] != null) pagesPanelRef[0].setFiles(BookController.listPages(currentBook[0]), null); }
		);
		TileGalleryPanel pagesPanel = pagesPanelRef[0];
		pagesPanel.setFileDropOverride(files -> {
			if (currentBook[0] == null || files.isEmpty()) return;
			File src = files.get(0);
			BufferedImage img = ImageLoader.loadImage(src);
			if (img == null) return;
			ed.bookController.showNewPageDialog(img, currentBook[0],
					() -> javax.swing.SwingUtilities.invokeLater(
							() -> pagesPanel.setFiles(BookController.listPages(currentBook[0]), null)));
		});
		pagesPanel.setOnAdd(() -> ed.bookController.showNewPageDialog(null, currentBook[0],
				() -> javax.swing.SwingUtilities.invokeLater(
						() -> pagesPanel.setFiles(BookController.listPages(currentBook[0]), null))));
		pagesPanel.setCurrentBookDirSupplier(() -> currentBook[0]);
		pagesPanel.setVisible(false);

		// ── List panel ────────────────────────────────────────────────────────
		listPanelRef[0] = new TileGalleryPanel(
			new TileGalleryPanel.Callbacks() {
				@Override public void onTileOpened(File bookDir) {
					currentBook[0] = bookDir;
					pagesPanel.setFiles(BookController.listPages(bookDir), null);
					if (!pagesPanel.isVisible()) {
						pagesPanel.setVisible(true);
						ed.galleryWrapper.revalidate();
						ed.galleryWrapper.repaint();
					}
				}
				@Override public void onSelectionChanged(List<File> sel) {}
				@Override public void onRenameRequested(File bookDir) {
					String cur = bookDir.getName();
					String newName = (String) javax.swing.JOptionPane.showInputDialog(
							ed, "Neuer Buchname:", "Buch umbenennen",
							javax.swing.JOptionPane.PLAIN_MESSAGE, null, null, cur);
					if (newName == null || newName.isBlank() || newName.equals(cur)) return;
					java.io.File parent = bookDir.getParentFile();
					java.io.File newDir = new java.io.File(parent, newName.trim());
					if (newDir.exists()) { ed.showErrorDialog("Fehler", "Name bereits vorhanden."); return; }
					if (!bookDir.renameTo(newDir)) { ed.showErrorDialog("Fehler", "Umbenennen fehlgeschlagen."); return; }
					if (currentBook[0] != null && currentBook[0].equals(bookDir)) currentBook[0] = newDir;
					listPanelRef[0].setFiles(BookController.listBooks(), currentBook[0]);
				}
			},
			null, "Bücher", null,
			() -> listPanelRef[0].setFiles(BookController.listBooks(), currentBook[0])
		);
		TileGalleryPanel listPanel = listPanelRef[0];
		listPanel.setOnAdd(() -> {
			String name = (String) javax.swing.JOptionPane.showInputDialog(
					ed, "Buchname:", "Neues Buch", javax.swing.JOptionPane.PLAIN_MESSAGE);
			if (name == null || name.isBlank()) return;
			try {
				java.io.File book = BookController.createBook(name.trim());
				currentBook[0] = book;
				listPanel.setFiles(BookController.listBooks(), book);
				pagesPanel.setFiles(BookController.listPages(book), null);
			} catch (java.io.IOException ex) {
				ed.showErrorDialog("Fehler", ex.getMessage());
			}
		});
		listPanel.setFiles(BookController.listBooks(), null);
		listPanel.setVisible(false);

		return new TileGalleryPanel[]{ listPanel, pagesPanel };
	}
}
