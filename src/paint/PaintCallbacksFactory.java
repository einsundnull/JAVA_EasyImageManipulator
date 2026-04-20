package paint;

import java.awt.Color;
import java.awt.image.BufferedImage;

/**
 * Factory for PaintToolbar.Callbacks. Extracted from
 * SelectiveAlphaEditor.buildPaintCallbacks().
 */
class PaintCallbacksFactory {

	static PaintToolbar.Callbacks build(SelectiveAlphaEditor ed) {
		return new PaintToolbar.Callbacks() {
			@Override
			public void onToolChanged(PaintEngine.Tool tool) {
				// If we're leaving a brush tool (PENCIL/ERASER/SMEAR), the canvas cursor
				// may still be BLANK_CURSOR from the brush-preview overlay. Reset it so the
				// user doesn't see "no cursor" when the mouse is already over the canvas.
				boolean isBrushTool = tool == PaintEngine.Tool.PENCIL
						|| tool == PaintEngine.Tool.ERASER
						|| tool == PaintEngine.Tool.ERASER_BG
						|| tool == PaintEngine.Tool.ERASER_COLOR
						|| tool == PaintEngine.Tool.SMEAR;
				if (!isBrushTool) {
					ed.ci().canvasPanel.setCursor(java.awt.Cursor.getDefaultCursor());
				}
				ed.ci().canvasPanel.repaint();
			}

			@Override
			public void onColorChanged(Color p, Color s) {
			}

			@Override
			public void onStrokeChanged(int w) {
			}

			@Override
			public void onFillModeChanged(PaintEngine.FillMode m) {
			}

			@Override
			public void onBrushShapeChanged(PaintEngine.BrushShape s) {
			}

			@Override
			public void onAntialiasingChanged(boolean aa) {
				ed.ci().canvasPanel.repaint();
			}

			@Override
			public void onCut() {
				ed.doCut();
			}

			@Override
			public void onCopy() {
				ed.doCopy();
			}

			@Override
			public void onPaste() {
				ed.doPaste();
			}

			@Override
			public void onToggleGrid(boolean show) {
				ed.ci().showGrid = show;
				ed.ci().canvasPanel.repaint();
			}

			@Override
			public void onToggleRuler(boolean show) {
				ed.showRuler = show;
				ed.buildRulerLayout();
			}

			@Override
			public void onRulerUnitChanged(int idx) {
				ed.rulerUnit = RulerUnit.values()[idx];
				if (ed.showRuler) {
					ed.hRuler.repaint();
					ed.vRuler.repaint();
				}
			}

			@Override
			public void onFlipHorizontal() {
				ed.doFlipH();
			}

			@Override
			public void onFlipVertical() {
				ed.doFlipV();
			}

			@Override
			public void onRotate() {
				ed.doRotate();
			}

			@Override
			public void onRotateDeg(double deg) {
				ed.doRotate(deg);
			}

			@Override
			public void onScale() {
				ed.doScale();
			}

			@Override
			public void onUndo() {
				ed.doUndo();
			}

			@Override
			public void onRedo() {
				ed.doRedo();
			}

			@Override
			public BufferedImage getWorkingImage() {
				return ed.ci().workingImage;
			}
		};
	}
}
