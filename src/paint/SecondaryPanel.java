package paint;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

import javax.swing.JPanel;

/**
 * Secondary preview window panel. Handles window drag/resize via mouse, and
 * renders the canvas image with all layers. Extracted from SelectiveAlphaEditor.
 */
class SecondaryPanel extends JPanel {
	private static final int HANDLE_SIZE = 8;
	private int dragStartX, dragStartY;
	private int dragStartWinX, dragStartWinY, dragStartWinW, dragStartWinH;
	private String resizeEdge = null; // "tl", "t", "tr", "l", "r", "bl", "b", "br", or null for drag

	private final SelectiveAlphaEditor editor;

	SecondaryPanel(SelectiveAlphaEditor editor) {
		this.editor = editor;

		addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				dragStartX = e.getXOnScreen();
				dragStartY = e.getYOnScreen();
				dragStartWinX = editor.secWin.getX();
				dragStartWinY = editor.secWin.getY();
				dragStartWinW = editor.secWin.getWidth();
				dragStartWinH = editor.secWin.getHeight();
				resizeEdge = getResizeEdgeAt(e.getX(), e.getY());
			}
		});
		addMouseMotionListener(new MouseAdapter() {
			@Override
			public void mouseMoved(MouseEvent e) {
				String edge = getResizeEdgeAt(e.getX(), e.getY());
				updateCursor(edge);
			}

			@Override
			public void mouseDragged(MouseEvent e) {
				if (editor.secWin == null || editor.secFullscreen)
					return;
				int dx = e.getXOnScreen() - dragStartX;
				int dy = e.getYOnScreen() - dragStartY;

				if (resizeEdge == null) {
					// Window drag
					editor.secWin.setLocation(dragStartWinX + dx, dragStartWinY + dy);
				} else {
					// Window resize
					int newX = dragStartWinX, newY = dragStartWinY;
					int newW = dragStartWinW, newH = dragStartWinH;

					if (resizeEdge.contains("l"))
						newX += dx;
					if (resizeEdge.contains("r"))
						newW += dx;
					if (resizeEdge.contains("t"))
						newY += dy;
					if (resizeEdge.contains("b"))
						newH += dy;

					newW = Math.max(200, newW);
					newH = Math.max(150, newH);

					editor.secWin.setBounds(newX, newY, newW, newH);
				}
			}
		});
	}

	private String getResizeEdgeAt(int x, int y) {
		int w = getWidth(), h = getHeight();
		boolean nearLeft = x < HANDLE_SIZE;
		boolean nearRight = x >= w - HANDLE_SIZE;
		boolean nearTop = y < HANDLE_SIZE;
		boolean nearBottom = y >= h - HANDLE_SIZE;

		if (nearTop && nearLeft)
			return "tl";
		if (nearTop && nearRight)
			return "tr";
		if (nearBottom && nearLeft)
			return "bl";
		if (nearBottom && nearRight)
			return "br";
		if (nearTop)
			return "t";
		if (nearBottom)
			return "b";
		if (nearLeft)
			return "l";
		if (nearRight)
			return "r";
		return null;
	}

	private void updateCursor(String edge) {
		if (edge == null) {
			setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
		} else {
			int cursorType = switch (edge) {
			case "tl", "br" -> Cursor.NW_RESIZE_CURSOR;
			case "tr", "bl" -> Cursor.NE_RESIZE_CURSOR;
			case "t", "b" -> Cursor.N_RESIZE_CURSOR;
			case "l", "r" -> Cursor.W_RESIZE_CURSOR;
			default -> Cursor.DEFAULT_CURSOR;
			};
			setCursor(Cursor.getPredefinedCursor(cursorType));
		}
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D) g;
		g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

		// Determine source canvas based on display mode
		int srcIdx = switch (editor.secCanvasMode) {
		case SHOW_CANVAS_I_ONLY -> 0;
		case SHOW_CANVAS_II_ONLY -> 1;
		case SHOW_ACTIVE_CANVAS -> editor.activeCanvasIndex;
		};
		CanvasInstance ci = editor.canvases[srcIdx];
		if (ci.workingImage == null)
			return;

		java.awt.image.BufferedImage src = ci.workingImage;
		List<Layer> elements = ci.activeElements;

		// Scale-to-fit (maintain aspect ratio)
		int pw = getWidth(), ph = getHeight();
		int iw = src.getWidth(), ih = src.getHeight();
		double scale = Math.min((double) pw / iw, (double) ph / ih);
		int dw = (int) (iw * scale), dh = (int) (ih * scale);
		int ox = (pw - dw) / 2, oy = (ph - dh) / 2;

		// Checkerboard background
		int cell = 16;
		for (int cy = 0; cy < ph; cy += cell) {
			for (int cx = 0; cx < pw; cx += cell) {
				g2.setColor(((cx / cell + cy / cell) % 2 == 0) ? editor.canvasBg1 : editor.canvasBg2);
				g2.fillRect(cx, cy, cell, cell);
			}
		}

		// Draw main image
		g2.drawImage(src, ox, oy, dw, dh, null);

		// Draw elements
		for (Layer el : elements) {
			if (el instanceof ImageLayer il) {
				int ex = ox + (int) Math.round(il.x() * scale);
				int ey = oy + (int) Math.round(il.y() * scale);
				int ew = (int) Math.round(il.width() * scale);
				int eh = (int) Math.round(il.height() * scale);
				if (Math.abs(il.rotationAngle()) > 0.001) {
					java.awt.geom.AffineTransform orig = g2.getTransform();
					double cx = ex + ew / 2.0;
					double cy = ey + eh / 2.0;
					g2.rotate(Math.toRadians(il.rotationAngle()), cx, cy);
					g2.drawImage(il.image(), ex, ey, ew, eh, null);
					g2.setTransform(orig);
				} else {
					g2.drawImage(il.image(), ex, ey, ew, eh, null);
				}
			} else if (el instanceof TextLayer tl) {
				int tstyle = (tl.fontBold() ? java.awt.Font.BOLD : 0)
						| (tl.fontItalic() ? java.awt.Font.ITALIC : 0);
				int scaledFontSize = Math.max(1, (int) Math.round(tl.fontSize() * scale));
				java.awt.Font tfont = new java.awt.Font(tl.fontName(), tstyle, scaledFontSize);
				g2.setFont(tfont);
				g2.setColor(tl.fontColor());
				java.awt.FontMetrics tfm = g2.getFontMetrics();
				String[] tLines = tl.text().split("\n", -1);
				int tpx = ox + (int) Math.round((tl.x() + TextLayer.TEXT_PADDING) * scale);
				int tpy = oy + (int) Math.round((tl.y() + TextLayer.TEXT_PADDING) * scale);
				for (int li = 0; li < tLines.length; li++) {
					g2.drawString(tLines[li], tpx, tpy + tfm.getHeight() * li + tfm.getAscent());
				}
			} else if (el instanceof PathLayer pl && editor.secMode == PreviewMode.LIVE_ALL_EDIT) {
				renderPathLayerPreview(g2, pl, ox, oy, scale);
			}
		}

		// Element borders only in LIVE_ALL_EDIT
		if (editor.secMode == PreviewMode.LIVE_ALL_EDIT) {
			g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10,
					new float[] { 4, 3 }, 0));
			g2.setColor(new Color(0, 180, 255));
			for (Layer el : elements) {
				int ex = ox + (int) Math.round(el.x() * scale);
				int ey = oy + (int) Math.round(el.y() * scale);
				int ew = (int) Math.round(el.width() * scale);
				int eh = (int) Math.round(el.height() * scale);
				g2.drawRect(ex, ey, ew, eh);
			}
		}
	}

	private void renderPathLayerPreview(Graphics2D g2, PathLayer pl, int ox, int oy, double scale) {
		List<Point3D> pts = pl.points();
		if (pts.isEmpty())
			return;
		g2.setColor(new Color(0, 200, 255));
		g2.setStroke(new BasicStroke(1.5f));
		for (int i = 1; i < pts.size(); i++) {
			int x1 = ox + (int) Math.round(pts.get(i - 1).x * scale);
			int y1 = oy + (int) Math.round(pts.get(i - 1).y * scale);
			int x2 = ox + (int) Math.round(pts.get(i).x * scale);
			int y2 = oy + (int) Math.round(pts.get(i).y * scale);
			g2.drawLine(x1, y1, x2, y2);
		}
		g2.setColor(Color.WHITE);
		for (Point3D p : pts) {
			int px = ox + (int) Math.round(p.x * scale) - 3;
			int py = oy + (int) Math.round(p.y * scale) - 3;
			g2.fillOval(px, py, 6, 6);
		}
	}
}
