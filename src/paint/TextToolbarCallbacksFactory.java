package paint;

/**
 * Factory for TextToolbar.Callbacks. Wires toolbar actions to CanvasPanel.
 */
class TextToolbarCallbacksFactory {

	static TextToolbar.Callbacks build(SelectiveAlphaEditor ed) {
		return new TextToolbar.Callbacks() {

			@Override
			public void onTextPropsChanged(String font, int size, boolean bold, boolean italic, java.awt.Color color) {
				CanvasPanel cp = ed.ci().canvasPanel;
				if (cp != null) cp.applyTextPropsFromToolbar(font, size, bold, italic, color);
			}

			@Override
			public void onCommit() {
				CanvasPanel cp = ed.ci().canvasPanel;
				if (cp != null) cp.commitTextFromToolbar();
			}

			@Override
			public void onCancel() {
				CanvasPanel cp = ed.ci().canvasPanel;
				if (cp != null) cp.cancelTextFromToolbar();
			}
		};
	}
}
