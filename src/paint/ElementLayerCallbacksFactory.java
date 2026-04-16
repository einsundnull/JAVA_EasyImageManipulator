package paint;

import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 * Factory for ElementLayerPanel.Callbacks instances, one per canvas index.
 * Extracted from SelectiveAlphaEditor.buildElementLayerCallbacks().
 */
class ElementLayerCallbacksFactory {

	static ElementLayerPanel.Callbacks build(SelectiveAlphaEditor ed, int idx) {
		return new ElementLayerPanel.Callbacks() {
			private CanvasInstance c() {
				return ed.canvases[idx];
			}

			@Override
			public List<Layer> getActiveElements() {
				return c().activeElements;
			}

			@Override
			public List<Layer> getSelectedElements() {
				return c().selectedElements;
			}

			@Override
			public void setSelectedElement(Layer el) {
				c().selectedElements.clear();
				if (el != null)
					c().selectedElements.add(el);
				if (c().canvasPanel != null)
					c().canvasPanel.repaint();
			}

			@Override
			public void toggleElementSelection(Layer el) {
				ed.doToggleElementSelection(el);
				if (c().canvasPanel != null)
					c().canvasPanel.repaint();
			}

			@Override
			public void deleteElement(Layer el) {
				c().activeElements.removeIf(e -> e.id() == el.id());
				c().selectedElements.removeIf(e -> e.id() == el.id());
				ed.markDirty();
				ed.refreshElementPanel();
				if (c().canvasPanel != null)
					c().canvasPanel.repaint();
				ed.persistSceneIfActive(idx);
			}

			@Override
			public void burnElement(Layer el) {
				if (c().workingImage == null)
					return;
				// Tile holds a snapshot — look up live layer so position/scale are current
				Layer live = c().activeElements.stream().filter(e -> e.id() == el.id()).findFirst().orElse(el);
				ed.pushUndo();
				BufferedImage imgToBurn;
				if (live instanceof TextLayer tl) {
					imgToBurn = ed.renderTextLayerToImage(tl);
				} else {
					ImageLayer il = (ImageLayer) live;
					imgToBurn = PaintEngine.scale(il.image(), Math.max(1, live.width()), Math.max(1, live.height()));
				}
				PaintEngine.pasteRegion(c().workingImage, imgToBurn, new Point(live.x(), live.y()));
				c().activeElements.removeIf(e -> e.id() == el.id());
				c().selectedElements.removeIf(e -> e.id() == el.id());
				ed.markDirty();
				ed.refreshElementPanel();
				if (c().canvasPanel != null)
					c().canvasPanel.repaint();
			}

			@Override
			public void exportElementAsImage(Layer el) {
				if (c().workingImage == null || c().sourceFile == null)
					return;
				Layer live = c().activeElements.stream()
						.filter(e -> e.id() == el.id()).findFirst().orElse(el);
				BufferedImage imgToExport = ed.renderLayerToImage(live);
				if (imgToExport == null)
					return;

				File exportDir = c().sourceFile.getParentFile();
				String defaultName = ed.uniqueLayerExportFile(c().sourceFile, live.id()).getName();

				final File exportDirFinal = exportDir;
				JTextField fileNameField = new JTextField(defaultName);
				fileNameField.selectAll();

				String[] options = { "Speichern", "Abbrechen" };
				JPanel panel = new JPanel(new java.awt.BorderLayout(5, 5));
				panel.add(new JLabel("Dateiname:"), java.awt.BorderLayout.WEST);
				panel.add(fileNameField, java.awt.BorderLayout.CENTER);

				fileNameField.addActionListener(ev -> {
					String fileName = fileNameField.getText().trim();
					if (!fileName.isEmpty()) {
						ed.saveLayerAsImageFile(imgToExport, new File(exportDirFinal, fileName), idx);
					}
				});

				int result = JOptionPane.showOptionDialog(ed, panel,
						"Exportieren als Bild", JOptionPane.OK_CANCEL_OPTION,
						JOptionPane.QUESTION_MESSAGE, null, options, options[0]);

				if (result == JOptionPane.OK_OPTION) {
					String fileName = fileNameField.getText().trim();
					if (!fileName.isEmpty()) {
						ed.saveLayerAsImageFile(imgToExport, new File(exportDirFinal, fileName), idx);
					}
				}
			}

			@Override
			public void repaintCanvas() {
				if (c().canvasPanel != null)
					c().canvasPanel.repaint();
			}

			@Override
			public void onCloseRequested() {
				ed.canvasModeBtn.setSelected(false);
				ed.toggleCanvasMode();
			}

			@Override
			public void onLayerPanelElementHover(int elementId) {
				if (c().canvasPanel != null)
					c().canvasPanel.setHoveredElementId(elementId);
			}

			@Override
			public void openElementInOtherCanvas(Layer el) {
				ed.doOpenImageLayerInOtherCanvas(idx, el);
			}

			@Override
			public void openTextLayerForEditing(Layer el) {
				if (!(el instanceof TextLayer))
					return;
				ed.activeCanvasIndex = idx;
				ed.updateCanvasFocusBorder();
				CanvasInstance ci = c();
				if (ci.canvasPanel != null)
					ci.canvasPanel.enterTextEditMode(el);
			}

			@Override
			public void resetElementRotation(Layer el) {
				CanvasInstance c = c();
				if (!(el instanceof ImageLayer il))
					return;
				ed.pushUndo();
				ImageLayer updated = il.withRotation(0.0);
				ed.replaceInLists(c, updated);
				ed.markDirty(idx);
				ed.refreshElementPanel();
				if (c.canvasPanel != null)
					c.canvasPanel.repaint();
			}

			@Override
			public void exportElementAsMap(Layer el) {
				if (!(el instanceof TextLayer tl))
					return;
				Layer live = c().activeElements.stream().filter(e -> e.id() == el.id()).findFirst().orElse(el);
				if (!(live instanceof TextLayer textLive))
					return;

				String textContent = textLive.text();
				if (textContent == null || textContent.isEmpty()) {
					JOptionPane.showMessageDialog(ed, "TextLayer hat keinen Inhalt.",
							"Fehler", JOptionPane.ERROR_MESSAGE);
					return;
				}

				MapCreateDialog dialog = new MapCreateDialog(ed, textContent);
				dialog.setVisible(true);

				if (!dialog.isAccepted())
					return;

				try {
					String mapId = MapManager.generateMapId();
					TranslationMap newMap = new TranslationMap(mapId, dialog.getLanguage(), dialog.getSection(),
							dialog.getTextI(), dialog.getTextII());
					MapManager.addOrUpdateMap(newMap);

					if (ed.mapsPanel != null) {
						ed.mapsPanel.refreshMapsList();
					}

					JOptionPane.showMessageDialog(
							ed, "Translation Map gespeichert:\nSprache: " + dialog.getLanguage()
									+ "\nBereich: " + dialog.getSection(),
							"Erfolg", JOptionPane.INFORMATION_MESSAGE);
				} catch (Exception ex) {
					System.err.println("[ERROR] Failed to export map: " + ex.getMessage());
					ex.printStackTrace();
					JOptionPane.showMessageDialog(ed,
							"Fehler beim Speichern:\n" + ex.getMessage(), "Fehler",
							JOptionPane.ERROR_MESSAGE);
				}
			}

			@Override
			public void toggleElementVisibility(Layer el) {
				CanvasInstance c = c();
				ed.pushUndo();
				Layer updated = null;
				if (el instanceof ImageLayer il) {
					updated = il.withHidden(!il.isHidden());
				} else if (el instanceof TextLayer tl) {
					updated = tl.withHidden(!tl.isHidden());
				} else if (el instanceof PathLayer pl) {
					updated = pl.withHidden(!pl.isHidden());
				}
				if (updated != null) {
					ed.replaceInLists(c, updated);
					ed.markDirty(idx);
					ed.refreshElementPanel();
					if (c.canvasPanel != null)
						c.canvasPanel.repaint();
				}
			}

			// ── Case 2: LayerTile dropped onto another ElementLayerPanel ─────
			@Override
			public void insertLayerCopyAt(Layer layer, int visualIdx) {
				CanvasInstance c = c();
				Layer copy = ed.copyLayerWithNewId(layer, c.nextElementId++);
				if (copy == null)
					return;
				int insertIdx = ed.visualToInsertIndex(visualIdx, c.activeElements.size());
				c.activeElements.add(insertIdx, copy);
				c.selectedElements.clear();
				c.selectedElements.add(copy);
				ed.markDirty(idx);
				if (c.canvasPanel != null)
					c.canvasPanel.repaint();
				SwingUtilities.invokeLater(() -> ed.refreshElementPanel());
			}

			// ── Case 4: TileGallery right-drag dropped onto ElementLayerPanel ─
			@Override
			public void insertFileAsLayerAt(File file, int visualIdx) {
				CanvasInstance c = c();
				if (c.workingImage == null)
					return;
				try {
					BufferedImage img = ImageIO.read(file);
					if (img == null)
						return;
					img = ed.normalizeImage(img);
					int[] size = ed.fitElementSize(img.getWidth(), img.getHeight(), c.workingImage.getWidth(),
							c.workingImage.getHeight());
					int cx = Math.max(0, (c.workingImage.getWidth() - size[0]) / 2);
					int cy = Math.max(0, (c.workingImage.getHeight() - size[1]) / 2);
					ImageLayer layer = new ImageLayer(c.nextElementId++, img, cx, cy, size[0], size[1]);
					int insertIdx = ed.visualToInsertIndex(visualIdx, c.activeElements.size());
					c.activeElements.add(insertIdx, layer);
					c.selectedElements.clear();
					c.selectedElements.add(layer);
					ed.markDirty(idx);
					if (c.canvasPanel != null)
						c.canvasPanel.repaint();
					SwingUtilities.invokeLater(() -> ed.refreshElementPanel());
				} catch (Exception ex) {
					ed.showErrorDialog("Fehler", "Bild konnte nicht geladen werden: " + ex.getMessage());
				}
			}
		};
	}
}
