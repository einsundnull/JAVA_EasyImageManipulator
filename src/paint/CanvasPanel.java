package paint;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;

import javax.swing.AbstractAction;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

/**
 * Canvas panel for drawing and image manipulation. Handles all mouse input,
 * painting, and selection/floating selection logic. Extracted from
 * SelectiveAlphaEditor as a standalone component.
 */
public class CanvasPanel extends JPanel {
	private final CanvasCallbacks callbacks;

	// Pan tracking
	private Point panStart = null;
	private Point panViewPos = null;
	public int mouseWheelSensitivityInPx = 16;

	// Element multi-select: rubber-band state (screen coords)
	private Point elemBandStart = null;
	private Point elemBandEnd   = null;
	private boolean isElemBanding = false;
	// Delta-based multi-drag: last image-space drag point
	private Point elemLastImgPt = null;

	// ── Text tool state ───────────────────────────────────────────────────────
	/** Image-space origin of the active text input box, or null when not editing. */
	private Point   textOrigin  = null;
	private StringBuilder textBuffer = new StringBuilder();
	private static final int TEXT_PADDING = 4;

	public CanvasPanel(CanvasCallbacks callbacks) {
		this.callbacks = callbacks;
		setOpaque(false);
		setFocusable(true);
		System.err.println("[DEBUG] CanvasPanel created, focusable=" + isFocusable());
		setupMouseHandling();
		setupKeyBindings();
	}

	private void setupMouseHandling() {
		MouseAdapter handler = new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				System.err.println("[DEBUG] mousePressed called! Button=" + e.getButton() + ", Point=" + e.getPoint());
				requestFocusInWindow();

				boolean isMiddle = (e.getButton() == MouseEvent.BUTTON2);
				boolean isCtrlDrg = SwingUtilities.isLeftMouseButton(e)
						&& (e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) != 0;
				if (isMiddle || isCtrlDrg) {
					setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
					panStart = SwingUtilities.convertPoint(CanvasPanel.this, e.getPoint(),
							callbacks.getScrollPane().getViewport());
					panViewPos = callbacks.getScrollPane().getViewport().getViewPosition();
					return;
				}

				if (!SwingUtilities.isLeftMouseButton(e) || callbacks.getWorkingImage() == null)
					return;

				// Commit any open text box when clicking elsewhere (even with TEXT tool)
				if (textOrigin != null) {
					PaintEngine.Tool curTool = callbacks.getAppMode() == AppMode.PAINT
					        ? callbacks.getPaintToolbar().getActiveTool() : null;
					if (curTool != PaintEngine.Tool.TEXT) {
						commitText();
					}
					// TEXT tool click-away → commitText() then open new one (handled below in switch)
				}

				Point imgPt = callbacks.screenToImage(e.getPoint());

				if (!callbacks.getActiveElements().isEmpty() && callbacks.getFloatingImage() == null) {
					boolean shiftDown = (e.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) != 0;

					if (shiftDown) {
						// Shift+click → toggle element in/out of selection
						// Shift+drag (not on element) → start rubber-band multi-select
						Element hit = hitElement(e.getPoint());
						if (hit != null) {
							callbacks.toggleElementSelection(hit);
						} else {
							isElemBanding = true;
							elemBandStart = e.getPoint();
							elemBandEnd   = e.getPoint();
						}
						repaint();
						return;
					}

					// Check resize handles on the primary selected element
					Element primary = callbacks.getSelectedElement();
					if (primary != null) {
						Rectangle[] handles = callbacks.handleRects(callbacks.elemRectScreen(primary));
						for (int hi = 0; hi < handles.length; hi++) {
							if (handles[hi].contains(e.getPoint())) {
								callbacks.setElemActiveHandle(hi);
								callbacks.setElemScaleBase(new Rectangle(
										primary.x(), primary.y(), primary.width(), primary.height()));
								callbacks.setElemScaleStart(e.getPoint());
								return;
							}
						}
					}

					// Click on any currently selected element → drag ALL selected
					for (Element sel : callbacks.getSelectedElements()) {
						if (callbacks.elemRectScreen(sel).contains(e.getPoint())) {
							callbacks.setDraggingElement(true);
							elemLastImgPt = imgPt;
							return;
						}
					}

					// Click on an unselected element → single-select + drag
					Element hit = hitElement(e.getPoint());
					if (hit != null) {
						callbacks.setSelectedElement(hit);
						callbacks.setDraggingElement(true);
						elemLastImgPt = imgPt;
						repaint();
						return;
					} else {
						// Click on empty area → deselect all
						if (!callbacks.getSelectedElements().isEmpty()) {
							callbacks.setSelectedElement(null);
							repaint();
						}
					}
				}

				if (callbacks.getFloatingImage() != null) {
					int h = callbacks.hitHandle(e.getPoint());
					if (h >= 0) {
						callbacks.setActiveHandle(h);
						callbacks.setScaleBaseRect(new Rectangle(callbacks.getFloatRect()));
						callbacks.setScaleDragStart(e.getPoint());
						return;
					} else if (callbacks.floatRectScreen().contains(e.getPoint())) {
						callbacks.setDraggingFloat(true);
						callbacks.setFloatDragAnchor(
								new Point(imgPt.x - callbacks.getFloatRect().x, imgPt.y - callbacks.getFloatRect().y));
						return;
					} else {
						callbacks.commitFloat();
					}
				}

				if (callbacks.getAppMode() == AppMode.PAINT) {
					PaintEngine.Tool tool = callbacks.getPaintToolbar().getActiveTool();
					switch (tool) {
					case PENCIL, ERASER -> {
						callbacks.pushUndo();
						callbacks.setLastPaintPoint(imgPt);
						callbacks.paintDot(imgPt);
					}
					case FLOODFILL -> {
						callbacks.pushUndo();
						PaintEngine.floodFill(callbacks.getWorkingImage(), imgPt.x, imgPt.y,
								callbacks.getPaintToolbar().getPrimaryColor(), 30);
						callbacks.markDirty();
					}
					case EYEDROPPER -> {
						Color picked = PaintEngine.pickColor(callbacks.getWorkingImage(), imgPt.x, imgPt.y);
						callbacks.getPaintToolbar().setSelectedColor(picked);
					}
					case LINE, CIRCLE, RECT -> {
						callbacks.pushUndo();
						callbacks.setShapeStartPoint(imgPt);
						callbacks.setPaintSnapshot(callbacks.deepCopy(callbacks.getWorkingImage()));
					}
					case SELECT -> {
						Rectangle existingSel = callbacks.getActiveSelection();
						if (existingSel != null) {
							// Selection exists – check handles, inside, outside
							int sx = (int) Math.round(existingSel.x * callbacks.getZoom());
							int sy = (int) Math.round(existingSel.y * callbacks.getZoom());
							int sw = (int) Math.round(existingSel.width  * callbacks.getZoom());
							int sh = (int) Math.round(existingSel.height * callbacks.getZoom());
							Rectangle selScr = new Rectangle(sx, sy, sw, sh);
							Rectangle[] handles = callbacks.handleRects(selScr);
							boolean hitHandle = false;
							for (int hi = 0; hi < handles.length; hi++) {
								if (handles[hi].contains(e.getPoint())) {
									// Handle hit → lift pixels, then start float scale-resize
									callbacks.liftSelectionToFloat();
									Rectangle fr = callbacks.getFloatRect();
									if (fr != null) {
										callbacks.setActiveHandle(hi);
										callbacks.setScaleBaseRect(new Rectangle(fr));
										callbacks.setScaleDragStart(e.getPoint());
									}
									hitHandle = true;
									break;
								}
							}
							if (!hitHandle) {
								if (selScr.contains(e.getPoint())) {
									// Click inside → lift pixels, then start float drag
									callbacks.liftSelectionToFloat();
									Rectangle fr = callbacks.getFloatRect();
									if (fr != null) {
										callbacks.setDraggingFloat(true);
										callbacks.setFloatDragAnchor(
												new Point(imgPt.x - fr.x, imgPt.y - fr.y));
									}
								} else {
									// Click outside → clear selection, start new
									callbacks.clearSelection();
									callbacks.setSelecting(true);
									callbacks.setSelectionStart(imgPt);
									callbacks.setSelectionEnd(imgPt);
								}
							}
						} else {
							callbacks.setSelecting(true);
							callbacks.setSelectionStart(imgPt);
							callbacks.setSelectionEnd(imgPt);
						}
					}
					case TEXT -> {
						// Commit any existing text first, then start a new text box
						commitText();
						textOrigin = imgPt;
						textBuffer.setLength(0);
						requestFocusInWindow();
						repaint();
					}
					}
				} else {
					if (callbacks.isFloodfillMode()) {
						callbacks.pushUndo();
						callbacks.performFloodfill(e.getPoint());
					} else {
						// Start a new selection (Shift adds to existing)
						System.err.println("[DEBUG] Started selection at " + imgPt + ", appMode="
								+ callbacks.getAppMode() + ", floodfill=" + callbacks.isFloodfillMode());
						callbacks.setSelecting(true);
						callbacks.setSelectionStart(imgPt);
						callbacks.setSelectionEnd(imgPt);
					}
				}
			}

			@Override
			public void mouseDragged(MouseEvent e) {
				System.err.println("[DEBUG] mouseDragged called! Point=" + e.getPoint());
				if (panStart != null) {
					Point curVp = SwingUtilities.convertPoint(CanvasPanel.this, e.getPoint(),
							callbacks.getScrollPane().getViewport());
					int dx = panStart.x - curVp.x;
					int dy = panStart.y - curVp.y;
					int newX = Math.max(0, panViewPos.x + dx);
					int newY = Math.max(0, panViewPos.y + dy);
					callbacks.getScrollPane().getViewport().setViewPosition(new Point(newX, newY));
					return;
				}

				if (callbacks.getWorkingImage() == null)
					return;
				Point imgPt = callbacks.screenToImage(e.getPoint());

				if (callbacks.isDraggingElement()) {
					if (elemLastImgPt != null) {
						int dx = imgPt.x - elemLastImgPt.x;
						int dy = imgPt.y - elemLastImgPt.y;
						if (dx != 0 || dy != 0) {
							callbacks.moveSelectedElements(dx, dy);
							elemLastImgPt = imgPt;
						}
					}
					repaint();
					return;
				}

				if (isElemBanding) {
					elemBandEnd = e.getPoint();
					repaint();
					return;
				}

				if (callbacks.getElemActiveHandle() >= 0) {
					Rectangle r = callbacks.getElemScaleBase();
					double origW = r.width, origH = r.height;
					double origX = r.x, origY = r.y;
					double dx = (e.getPoint().x - callbacks.getElemScaleStart().x) / callbacks.getZoom();
					double dy = (e.getPoint().y - callbacks.getElemScaleStart().y) / callbacks.getZoom();

					Element el = callbacks.getSelectedElement();
					int handle = callbacks.getElemActiveHandle();

					double nw, nh, nx, ny;
					if (handle == 0) {
						nw = Math.max(1, origW - dx);
						nh = Math.max(1, origH * nw / origW);
						nx = origX + origW - nw;
						ny = origY + origH - nh;
					} else if (handle == 2) {
						nw = Math.max(1, origW + dx);
						nh = Math.max(1, origH * nw / origW);
						nx = origX;
						ny = origY + origH - nh;
					} else if (handle == 5) {
						nw = Math.max(1, origW - dx);
						nh = Math.max(1, origH * nw / origW);
						nx = origX + origW - nw;
						ny = origY;
					} else if (handle == 7) {
						nw = Math.max(1, origW + dx);
						nh = Math.max(1, origH * nw / origW);
						nx = origX;
						ny = origY;
					} else if (handle == 1 || handle == 6) {
						nh = handle == 1 ? Math.max(1, origH - dy) : Math.max(1, origH + dy);
						nw = origW;
						nx = origX;
						ny = handle == 1 ? origY + origH - nh : origY;
					} else if (handle == 3 || handle == 4) {
						nw = handle == 3 ? Math.max(1, origW - dx) : Math.max(1, origW + dx);
						nh = origH;
						nx = handle == 3 ? origX + origW - nw : origX;
						ny = origY;
					} else {
						return;
					}

					Element updated = el.withBounds((int) nx, (int) ny, (int) nw, (int) nh);
					callbacks.updateSelectedElement(updated);
					repaint();
					return;
				}

				if (callbacks.isDraggingFloat()) {
					Rectangle fr = callbacks.getFloatRect();
					fr.x = (int) (imgPt.x - callbacks.getFloatDragAnchor().x);
					fr.y = (int) (imgPt.y - callbacks.getFloatDragAnchor().y);
					callbacks.repaintCanvas();
					return;
				}

				if (callbacks.getActiveHandle() >= 0) {
					Rectangle newR = computeNewFloatRect(callbacks.getActiveHandle(), callbacks.getScaleBaseRect(),
							callbacks.getScaleDragStart(), e.getPoint());
					if (newR != null) {
						Rectangle fr = callbacks.getFloatRect();
						fr.x = newR.x;
						fr.y = newR.y;
						fr.width = newR.width;
						fr.height = newR.height;
						callbacks.repaintCanvas();
					}
					return;
				}

				if (callbacks.getAppMode() == AppMode.PAINT) {
					PaintEngine.Tool tool = callbacks.getPaintToolbar().getActiveTool();
					if (tool == PaintEngine.Tool.PENCIL || tool == PaintEngine.Tool.ERASER) {
						boolean aa = callbacks.getPaintToolbar().isAntialiasing();
						if (tool == PaintEngine.Tool.PENCIL) {
							PaintEngine.drawPencil(callbacks.getWorkingImage(), callbacks.getLastPaintPoint(), imgPt,
									callbacks.getPaintToolbar().getPrimaryColor(),
									callbacks.getPaintToolbar().getStrokeWidth(),
									callbacks.getPaintToolbar().getBrushShape(), aa);
						} else {
							PaintEngine.drawEraser(callbacks.getWorkingImage(), callbacks.getLastPaintPoint(), imgPt,
									callbacks.getPaintToolbar().getStrokeWidth(), aa);
						}
						callbacks.setLastPaintPoint(imgPt);
						callbacks.markDirty();
					} else if (tool == PaintEngine.Tool.LINE || tool == PaintEngine.Tool.CIRCLE
							|| tool == PaintEngine.Tool.RECT) {
						if (callbacks.getPaintSnapshot() != null && callbacks.getWorkingImage() != null) {
							// Restore original image, then draw preview
							copyInto(callbacks.getPaintSnapshot(), callbacks.getWorkingImage());
							drawShape(tool, callbacks.getShapeStartPoint(), imgPt);
							callbacks.markDirty();
						}
						callbacks.repaintCanvas();
					} else if (tool == PaintEngine.Tool.SELECT) {
						// SELECT tool in PAINT mode: draw selection frame
						System.err.println("[DEBUG] Selection drag (PAINT mode): imgPt=" + imgPt);
						callbacks.setSelectionEnd(imgPt);
						callbacks.repaintCanvas();
					}
				} else {
					System.err.println("[DEBUG] Selection drag: imgPt=" + imgPt + ", start="
							+ callbacks.getSelectionStart() + ", end=" + callbacks.getSelectionEnd() + " -> " + imgPt);
					callbacks.setSelectionEnd(imgPt);
					callbacks.repaintCanvas();
				}
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				panStart = null;
				panViewPos = null;
				elemLastImgPt = null;

				if (callbacks.getWorkingImage() == null)
					return;

				// Finalize rubber-band element multi-select
				if (isElemBanding) {
					isElemBanding = false;
					if (elemBandStart != null && elemBandEnd != null) {
						int bx = Math.min(elemBandStart.x, elemBandEnd.x);
						int by = Math.min(elemBandStart.y, elemBandEnd.y);
						int bw = Math.abs(elemBandEnd.x - elemBandStart.x);
						int bh = Math.abs(elemBandEnd.y - elemBandStart.y);
						if (bw > 2 || bh > 2) {
							Rectangle band = new Rectangle(bx, by, bw, bh);
							java.util.List<Element> newSel = new java.util.ArrayList<>();
							for (Element el : callbacks.getActiveElements()) {
								if (callbacks.elemRectScreen(el).intersects(band)) newSel.add(el);
							}
							callbacks.setSelectedElements(newSel);
						}
					}
					elemBandStart = null;
					elemBandEnd   = null;
					repaint();
					return;
				}

				callbacks.setDraggingElement(false);
				callbacks.setElemActiveHandle(-1);

				if (callbacks.isDraggingFloat()) {
					callbacks.setDraggingFloat(false);
					callbacks.repaintCanvas();
					return;
				}

				if (callbacks.getActiveHandle() >= 0) {
					callbacks.setActiveHandle(-1);
					callbacks.setScaleBaseRect(null);
					callbacks.setScaleDragStart(null);
					callbacks.repaintCanvas();
					return;
				}

				if (callbacks.getAppMode() == AppMode.PAINT) {
					PaintEngine.Tool tool = callbacks.getPaintToolbar().getActiveTool();
					if (tool == PaintEngine.Tool.LINE || tool == PaintEngine.Tool.CIRCLE
							|| tool == PaintEngine.Tool.RECT) {
						Point imgPt = callbacks.screenToImage(e.getPoint());
						drawShape(tool, callbacks.getShapeStartPoint(), imgPt);
						callbacks.setShapeStartPoint(null);
						callbacks.setPaintSnapshot(null);
						callbacks.markDirty();
					} else if (tool == PaintEngine.Tool.SELECT) {
						// SELECT tool: finalize selection
						if (callbacks.isSelecting()) {
							callbacks.setSelecting(false);
							Point start = callbacks.getSelectionStart();
							Point end   = callbacks.getSelectionEnd();
							if (start != null && end != null) {
								int x = Math.min(start.x, end.x);
								int y = Math.min(start.y, end.y);
								int w = Math.abs(end.x - start.x);
								int h = Math.abs(end.y - start.y);
								if (w > 0 && h > 0) {
									Rectangle sel = new Rectangle(x, y, w, h);
									if (callbacks.isCanvasSubMode()) {
										// Canvas sub-mode: immediately lift pixels into an Element layer
										callbacks.liftSelectionToElement(sel);
									} else {
										// Normal Paint mode: keep selection rect for further action
										callbacks.getSelectedAreas().clear();
										callbacks.getSelectedAreas().add(sel);
										callbacks.repaintCanvas();
									}
								}
							}
						}
					}
				} else {
					if (callbacks.isSelecting()) {
						callbacks.setSelecting(false);
						Point start = callbacks.getSelectionStart();
						Point end = callbacks.getSelectionEnd();
						if (start != null && end != null) {
							int x = Math.min(start.x, end.x);
							int y = Math.min(start.y, end.y);
							int w = Math.abs(end.x - start.x);
							int h = Math.abs(end.y - start.y);
							if (w > 0 && h > 0) {
								Rectangle sel = new Rectangle(x, y, w, h);
								callbacks.getSelectedAreas().add(sel);
								callbacks.repaintCanvas();
							}
						}
					}
				}
			}

			@Override
			public void mouseWheelMoved(MouseWheelEvent e) {
				System.err.println("[DEBUG] mouseWheelMoved called! Rotation=" + e.getWheelRotation() + ", Ctrl="
						+ ((e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) != 0));
				if ((e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) != 0) {
					double zoomStep = 0.06;
					double newZoom = callbacks.getZoom() - (e.getWheelRotation() * zoomStep);
					callbacks.setZoom(newZoom, e.getPoint());
				} else if ((e.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) != 0) {
					callbacks.getScrollPane().getHorizontalScrollBar()
							.setValue(callbacks.getScrollPane().getHorizontalScrollBar().getValue()
									+ e.getWheelRotation() * mouseWheelSensitivityInPx);
				} else {
					callbacks.getScrollPane().getVerticalScrollBar()
							.setValue(callbacks.getScrollPane().getVerticalScrollBar().getValue()
									+ e.getWheelRotation() * mouseWheelSensitivityInPx);
				}
			}
		};

		addMouseListener(handler);
		addMouseMotionListener(handler);
		addMouseWheelListener(handler);
		System.err.println("[DEBUG] Mouse listeners registered! Focusable=" + isFocusable() + ", Enabled=" + isEnabled()
				+ ", Visible=" + isVisible());
	}

	private void setupKeyBindings() {
		// Text tool: all printable keys are captured via a KeyAdapter registered on the
		// panel so we don't need to enumerate individual KeyStrokes in the InputMap.
		addKeyListener(new java.awt.event.KeyAdapter() {
			@Override
			public void keyPressed(java.awt.event.KeyEvent ke) {
				if (textOrigin == null) return;
				int code = ke.getKeyCode();
				if (code == KeyEvent.VK_ENTER) {
					// Shift+Enter = newline inside text; plain Enter = commit
					if (ke.isShiftDown()) {
						textBuffer.append('\n');
					} else {
						commitText();
					}
					ke.consume(); repaint(); return;
				}
				if (code == KeyEvent.VK_ESCAPE) {
					textOrigin = null; textBuffer.setLength(0);
					ke.consume(); repaint(); return;
				}
				if (code == KeyEvent.VK_BACK_SPACE) {
					if (textBuffer.length() > 0)
						textBuffer.deleteCharAt(textBuffer.length() - 1);
					ke.consume(); repaint(); return;
				}
			}
			@Override
			public void keyTyped(java.awt.event.KeyEvent ke) {
				if (textOrigin == null) return;
				char c = ke.getKeyChar();
				if (c == KeyEvent.CHAR_UNDEFINED) return;
				if (c == '\r' || c == '\n' || c == '\u001b' || c == '\b') return; // handled in keyPressed
				textBuffer.append(c);
				ke.consume(); repaint();
			}
		});

		getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
		getActionMap().put("cancel", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (textOrigin != null) {
					textOrigin = null; textBuffer.setLength(0); repaint(); return;
				}
				if (callbacks.getFloatingImage() != null) {
					callbacks.commitFloat();
				} else if (!callbacks.getSelectedElements().isEmpty()) {
					callbacks.setSelectedElement(null); // clears all
					callbacks.repaintCanvas();
				} else if (callbacks.isSelecting()) {
					callbacks.setSelecting(false);
					callbacks.repaintCanvas();
				} else {
					callbacks.clearSelection();
				}
			}
		});

		getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "commit");
		getActionMap().put("commit", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (textOrigin != null) { commitText(); return; }
				if (callbacks.getFloatingImage() != null) {
					callbacks.commitFloat();
				}
			}
		});

		// DEL and Backspace are handled by the parent window's WHEN_IN_FOCUSED_WINDOW
		// binding in SelectiveAlphaEditor.setupKeyBindings() — do not shadow them here.
	}

	private void copyInto(BufferedImage src, BufferedImage dst) {
		Graphics2D g2 = dst.createGraphics();
		g2.setComposite(java.awt.AlphaComposite.Src);
		g2.drawImage(src, 0, 0, null);
		g2.dispose();
	}

	private void drawShapePreview(BufferedImage img, PaintEngine.Tool tool, Point from, Point to) {
		Graphics2D g2 = img.createGraphics();
		g2.setColor(callbacks.getPaintToolbar().getPrimaryColor());
		g2.setStroke(new BasicStroke(callbacks.getPaintToolbar().getStrokeWidth()));

		int x = Math.min(from.x, to.x);
		int y = Math.min(from.y, to.y);
		int w = Math.abs(to.x - from.x);
		int h = Math.abs(to.y - from.y);

		if (tool == PaintEngine.Tool.LINE) {
			g2.drawLine(from.x, from.y, to.x, to.y);
		} else if (tool == PaintEngine.Tool.CIRCLE) {
			g2.drawOval(x, y, w, h);
		} else if (tool == PaintEngine.Tool.RECT) {
			g2.drawRect(x, y, w, h);
		}
		g2.dispose();
	}

	private void drawShape(PaintEngine.Tool tool, Point from, Point to) {
		if (callbacks.getWorkingImage() == null)
			return;

		int x = Math.min(from.x, to.x);
		int y = Math.min(from.y, to.y);
		int w = Math.abs(to.x - from.x);
		int h = Math.abs(to.y - from.y);

		boolean aa = callbacks.getPaintToolbar().isAntialiasing();
		if (tool == PaintEngine.Tool.LINE) {
			PaintEngine.drawLine(callbacks.getWorkingImage(), from, to, callbacks.getPaintToolbar().getPrimaryColor(),
					callbacks.getPaintToolbar().getStrokeWidth(), aa);
		} else if (tool == PaintEngine.Tool.CIRCLE) {
			PaintEngine.drawCircle(callbacks.getWorkingImage(), from, to, callbacks.getPaintToolbar().getPrimaryColor(),
					callbacks.getPaintToolbar().getStrokeWidth(), callbacks.getPaintToolbar().getFillMode(),
					callbacks.getPaintToolbar().getSecondaryColor(), aa);
		} else if (tool == PaintEngine.Tool.RECT) {
			PaintEngine.drawRect(callbacks.getWorkingImage(), from, to, callbacks.getPaintToolbar().getPrimaryColor(),
					callbacks.getPaintToolbar().getStrokeWidth(), callbacks.getPaintToolbar().getFillMode(),
					callbacks.getPaintToolbar().getSecondaryColor(), aa);
		}
	}

	/**
	 * Render the current text buffer into a BufferedImage and hand it to the
	 * callbacks as a new Element layer. Clears the active text state afterwards.
	 */
	private void commitText() {
		if (textOrigin == null || textBuffer.length() == 0) {
			textOrigin = null;
			textBuffer.setLength(0);
			repaint();
			return;
		}
		Color  color    = callbacks.getPaintToolbar().getPrimaryColor();
		Font   font     = new Font("SansSerif", Font.PLAIN,
		                           Math.max(8, callbacks.getPaintToolbar().getStrokeWidth() * 4));
		String text     = textBuffer.toString();
		String[] lines  = text.split("\n", -1);

		// Measure total size
		java.awt.image.BufferedImage dummy = new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB);
		java.awt.FontMetrics fm = dummy.createGraphics().getFontMetrics(font);
		int lineH  = fm.getHeight();
		int descent = fm.getDescent();
		int maxW   = 1;
		for (String l : lines) maxW = Math.max(maxW, fm.stringWidth(l));
		int totalH = Math.max(1, lineH * lines.length);

		java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
		        maxW + TEXT_PADDING * 2, totalH + TEXT_PADDING * 2,
		        java.awt.image.BufferedImage.TYPE_INT_ARGB);
		java.awt.Graphics2D g2 = img.createGraphics();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g2.setFont(font);
		g2.setColor(color);
		for (int i = 0; i < lines.length; i++) {
			g2.drawString(lines[i], TEXT_PADDING, TEXT_PADDING + lineH * i + lineH - descent);
		}
		g2.dispose();

		callbacks.commitTextAsElement(img, textOrigin.x, textOrigin.y);
		textOrigin = null;
		textBuffer.setLength(0);
		repaint();
	}

	/** Returns the topmost element at screen point, or null. */
	private Element hitElement(Point screenPt) {
		java.util.List<Element> els = callbacks.getActiveElements();
		for (int i = els.size() - 1; i >= 0; i--) {
			if (callbacks.elemRectScreen(els.get(i)).contains(screenPt)) return els.get(i);
		}
		return null;
	}

	private Rectangle computeNewFloatRect(int handle, Rectangle base, Point origin, Point current) {
		double dx = (current.x - origin.x) / callbacks.getZoom();
		double dy = (current.y - origin.y) / callbacks.getZoom();
		double bx = base.x, by = base.y, bw = base.width, bh = base.height;
		final double MIN = 1.0;

		double rx, ry, rw, rh;
		switch (handle) {
		case 0 -> {
			rw = Math.max(MIN, bw - dx);
			rh = Math.max(MIN, bh * rw / bw);
			rx = bx + bw - rw;
			ry = by + bh - rh;
		}
		case 1 -> {
			rh = Math.max(MIN, bh - dy);
			rx = bx;
			rw = bw;
			ry = by + bh - rh;
		}
		case 2 -> {
			rw = Math.max(MIN, bw + dx);
			rh = Math.max(MIN, bh * rw / bw);
			rx = bx;
			ry = by + bh - rh;
		}
		case 3 -> {
			rw = Math.max(MIN, bw - dx);
			rx = bx + bw - rw;
			ry = by;
			rh = bh;
		}
		case 4 -> {
			rw = Math.max(MIN, bw + dx);
			rx = bx;
			ry = by;
			rh = bh;
		}
		case 5 -> {
			rw = Math.max(MIN, bw - dx);
			rh = Math.max(MIN, bh * rw / bw);
			rx = bx + bw - rw;
			ry = by;
		}
		case 6 -> {
			rh = Math.max(MIN, bh + dy);
			rx = bx;
			rw = bw;
			ry = by;
		}
		default -> {
			rw = Math.max(MIN, bw + dx);
			rh = Math.max(MIN, bh * rw / bw);
			rx = bx;
			ry = by;
		}
		}

		return new Rectangle((int) Math.round(rx), (int) Math.round(ry), (int) Math.round(rw), (int) Math.round(rh));
	}

	@Override
	public Dimension getPreferredSize() {
		if (callbacks.getWorkingImage() == null)
			return new Dimension(1, 1);
		return new Dimension((int) Math.ceil(callbacks.getWorkingImage().getWidth() * callbacks.getZoom()),
				(int) Math.ceil(callbacks.getWorkingImage().getHeight() * callbacks.getZoom()));
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		if (callbacks.getWorkingImage() == null)
			return;

		Graphics2D g2 = (Graphics2D) g;
		g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		int cw = (int) Math.round(callbacks.getWorkingImage().getWidth() * callbacks.getZoom());
		int ch = (int) Math.round(callbacks.getWorkingImage().getHeight() * callbacks.getZoom());
		g2.drawImage(callbacks.getWorkingImage(), 0, 0, cw, ch, null);

		// ── Element layers (non-destructive, rendered above base canvas) ──────
		java.util.List<Element> activeEls   = callbacks.getActiveElements();
		java.util.List<Element> selectedEls = callbacks.getSelectedElements();
		Element primaryEl   = callbacks.getSelectedElement();
		boolean showOutlines = callbacks.isShowAllLayerOutlines();
		float[] selDash = { 5f, 3f };
		float[] dimDash = { 3f, 3f };

		// Draw all non-primary elements first; primary draws last (on top)
		for (int pass = 0; pass < 2; pass++) {
			for (Element el : activeEls) {
				boolean isPrimary = primaryEl != null && primaryEl.id() == el.id();
				if (pass == 0 && isPrimary) continue;  // skip primary on first pass
				if (pass == 1 && !isPrimary) continue; // skip others on second pass

				Rectangle sr = callbacks.elemRectScreen(el);
				g2.drawImage(el.image(), sr.x, sr.y, sr.width, sr.height, null);

				boolean isSel = selectedEls.stream().anyMatch(s -> s.id() == el.id());
				if (isSel) {
					g2.setColor(AppColors.ACCENT);
					g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, selDash, 0f));
					g2.drawRect(sr.x, sr.y, sr.width, sr.height);
					if (isPrimary) {
						g2.setStroke(new BasicStroke(1f));
						for (Rectangle hr : callbacks.handleRects(sr)) {
							g2.setColor(Color.WHITE);
							g2.fillRect(hr.x, hr.y, hr.width, hr.height);
							g2.setColor(AppColors.ACCENT);
							g2.drawRect(hr.x, hr.y, hr.width, hr.height);
						}
					}
				} else if (showOutlines) {
					// Dim outline for unselected layers when toggle is on
					g2.setColor(new Color(160, 160, 160, 140));
					g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, dimDash, 0f));
					g2.drawRect(sr.x, sr.y, sr.width, sr.height);
				}
			}
		}

		// ── Active text input preview ─────────────────────────────────────────
		if (textOrigin != null) {
			Color  color  = callbacks.getPaintToolbar().getPrimaryColor();
			Font   font   = new Font("SansSerif", Font.PLAIN,
			                         Math.max(8, callbacks.getPaintToolbar().getStrokeWidth() * 4));
			String text   = textBuffer.toString() + "|"; // cursor
			String[] tLines = text.split("\n", -1);
			g2.setFont(font.deriveFont((float)(font.getSize() * callbacks.getZoom())));
			java.awt.FontMetrics fm = g2.getFontMetrics();
			int lineH  = fm.getHeight();
			int maxW   = 1;
			for (String l : tLines) maxW = Math.max(maxW, fm.stringWidth(l));
			int sx = (int) Math.round(textOrigin.x * callbacks.getZoom());
			int sy = (int) Math.round(textOrigin.y * callbacks.getZoom());
			int boxW = maxW + TEXT_PADDING * 2;
			int boxH = lineH * tLines.length + TEXT_PADDING * 2;
			// Background
			g2.setColor(new Color(255, 255, 255, 30));
			g2.fillRect(sx, sy, boxW, boxH);
			// Border
			g2.setColor(AppColors.ACCENT);
			g2.setStroke(new BasicStroke(1f));
			g2.drawRect(sx, sy, boxW, boxH);
			// Text
			g2.setColor(color);
			for (int i = 0; i < tLines.length; i++) {
				g2.drawString(tLines[i], sx + TEXT_PADDING, sy + TEXT_PADDING + lineH * (i + 1) - fm.getDescent());
			}
		}

		// Rubber-band multi-select rect (screen coords, drawn while shift-dragging)
		if (isElemBanding && elemBandStart != null && elemBandEnd != null) {
			int bx = Math.min(elemBandStart.x, elemBandEnd.x);
			int by = Math.min(elemBandStart.y, elemBandEnd.y);
			int bw = Math.abs(elemBandEnd.x - elemBandStart.x);
			int bh = Math.abs(elemBandEnd.y - elemBandStart.y);
			g2.setColor(new Color(100, 150, 255, 40));
			g2.fillRect(bx, by, bw, bh);
			float[] dash = { 5f, 3f };
			g2.setColor(new Color(100, 150, 255));
			g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, dash, 0f));
			g2.drawRect(bx, by, bw, bh);
		}

		// Selections overlay
		g2.setColor(new Color(255, 0, 0, 70));
		for (Rectangle r : callbacks.getSelectedAreas()) {
			int sx = (int) Math.round(r.x * callbacks.getZoom());
			int sy = (int) Math.round(r.y * callbacks.getZoom());
			int sw = (int) Math.round(r.width * callbacks.getZoom());
			int sh = (int) Math.round(r.height * callbacks.getZoom());
			g2.fillRect(sx, sy, sw, sh);
		}

		g2.setColor(Color.RED);
		g2.setStroke(new BasicStroke(1.2f));
		for (Rectangle r : callbacks.getSelectedAreas()) {
			int sx = (int) Math.round(r.x * callbacks.getZoom());
			int sy = (int) Math.round(r.y * callbacks.getZoom());
			int sw = (int) Math.round(r.width * callbacks.getZoom());
			int sh = (int) Math.round(r.height * callbacks.getZoom());
			g2.drawRect(sx, sy, sw, sh);
		}

		// Drag handles on the active selection (PAINT mode SELECT tool only)
		if (callbacks.getAppMode() == AppMode.PAINT && !callbacks.isSelecting()) {
			Rectangle aSel = callbacks.getActiveSelection();
			if (aSel != null) {
				int sx = (int) Math.round(aSel.x * callbacks.getZoom());
				int sy = (int) Math.round(aSel.y * callbacks.getZoom());
				int sw = (int) Math.round(aSel.width  * callbacks.getZoom());
				int sh = (int) Math.round(aSel.height * callbacks.getZoom());
				g2.setStroke(new BasicStroke(1f));
				for (Rectangle hr : callbacks.handleRects(new Rectangle(sx, sy, sw, sh))) {
					g2.setColor(Color.WHITE);
					g2.fillRect(hr.x, hr.y, hr.width, hr.height);
					g2.setColor(AppColors.ACCENT);
					g2.drawRect(hr.x, hr.y, hr.width, hr.height);
				}
			}
		}

		// Active selection being drawn (during drag)
		if (callbacks.isSelecting() && callbacks.getSelectionStart() != null && callbacks.getSelectionEnd() != null) {
			int x = Math.min(callbacks.getSelectionStart().x, callbacks.getSelectionEnd().x);
			int y = Math.min(callbacks.getSelectionStart().y, callbacks.getSelectionEnd().y);
			int w = Math.abs(callbacks.getSelectionEnd().x - callbacks.getSelectionStart().x);
			int h = Math.abs(callbacks.getSelectionEnd().y - callbacks.getSelectionStart().y);
			System.err.println("[DEBUG PAINT] Drawing selection: imgCoords=(" + x + "," + y + "," + w + "," + h
					+ "), zoom=" + callbacks.getZoom());

			g2.setColor(new Color(0, 200, 255, 60));
			g2.fillRect((int) Math.round(x * callbacks.getZoom()), (int) Math.round(y * callbacks.getZoom()),
					(int) Math.round(w * callbacks.getZoom()), (int) Math.round(h * callbacks.getZoom()));
			g2.setColor(new Color(0, 200, 255));
			g2.setStroke(new BasicStroke(1.5f));
			g2.drawRect((int) Math.round(x * callbacks.getZoom()), (int) Math.round(y * callbacks.getZoom()),
					(int) Math.round(w * callbacks.getZoom()), (int) Math.round(h * callbacks.getZoom()));
		}

		// Floating selection with handles
		if (callbacks.getFloatingImage() != null && callbacks.getFloatRect() != null) {
			Rectangle fr = callbacks.getFloatRect();
			int fx = (int) Math.round(fr.x * callbacks.getZoom());
			int fy = (int) Math.round(fr.y * callbacks.getZoom());
			int fw = (int) Math.round(fr.width * callbacks.getZoom());
			int fh = (int) Math.round(fr.height * callbacks.getZoom());
			g2.drawImage(callbacks.getFloatingImage(), fx, fy, fw, fh, null);

			float[] dash = { 6f, 4f };
			g2.setColor(Color.WHITE);
			g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, dash, 0f));
			g2.drawRect(fx, fy, fw, fh);
			g2.setColor(Color.BLACK);
			g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, dash, 6f));
			g2.drawRect(fx, fy, fw, fh);

			g2.setStroke(new BasicStroke(1f));
			for (Rectangle hr : callbacks.handleRects(new Rectangle(fx, fy, fw, fh))) {
				g2.setColor(Color.WHITE);
				g2.fillRect(hr.x, hr.y, hr.width, hr.height);
				g2.setColor(AppColors.ACCENT);
				g2.drawRect(hr.x, hr.y, hr.width, hr.height);
			}
		}
	}

}
