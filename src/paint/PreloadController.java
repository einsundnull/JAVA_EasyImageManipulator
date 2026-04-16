package paint;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.SwingWorker;

/**
 * Manages the hover-preload cache for smooth tile gallery browsing.
 * Extracted from SelectiveAlphaEditor.
 */
class PreloadController {

	private final SelectiveAlphaEditor ed;

	PreloadController(SelectiveAlphaEditor ed) {
		this.ed = ed;
	}

	/**
	 * Asynchronously preloads a file into the preload cache.
	 * Runs in a background thread and does NOT update UI.
	 */
	void preloadFileAsync(File file, int idx) {
		CanvasInstance c = ed.ci(idx);

		if (c.preloadCache.containsKey(file))
			return;

		SwingWorker<CanvasInstance.PreloadedFileState, Void> worker =
				new SwingWorker<CanvasInstance.PreloadedFileState, Void>() {
					@Override
					protected CanvasInstance.PreloadedFileState doInBackground() throws Exception {
						BufferedImage img = null;
						CanvasInstance.CanvasFileState cached = c.fileCache.get(file);
						if (cached != null) {
							img = cached.image;
						} else {
							img = javax.imageio.ImageIO.read(file);
							if (img != null) {
								img = ed.normalizeImage(img);
								c.fileCache.put(file, new CanvasInstance.CanvasFileState(img));
							}
						}
						if (img == null)
							return null;
						return new CanvasInstance.PreloadedFileState(img);
					}

					@Override
					protected void done() {
						try {
							CanvasInstance.PreloadedFileState state = get();
							if (state != null && !isCancelled()) {
								c.preloadCache.put(file, state);
							}
						} catch (Exception e) {
							// Silently ignore preload failures
						}
					}
				};

		worker.execute();
	}

	/**
	 * Preloads next and previous images in browsing order for smooth navigation.
	 */
	void preloadNextImages(int idx) {
		CanvasInstance c = ed.ci(idx);
		if (c.directoryImages.isEmpty() || c.currentImageIndex < 0)
			return;

		int nextIdx = c.currentImageIndex + 1;
		if (nextIdx < c.directoryImages.size())
			preloadFileAsync(c.directoryImages.get(nextIdx), idx);

		int prevIdx = c.currentImageIndex - 1;
		if (prevIdx >= 0)
			preloadFileAsync(c.directoryImages.get(prevIdx), idx);

		cleanupPreloadCache(idx);
	}

	private void cleanupPreloadCache(int idx) {
		CanvasInstance c = ed.ci(idx);
		if (c.preloadCache.isEmpty())
			return;

		long CACHE_EXPIRY_MS = 60_000;
		int MAX_CACHE_SIZE = 5;

		java.util.Set<File> keepFiles = new java.util.HashSet<>();
		if (c.currentImageIndex >= 0 && c.currentImageIndex < c.directoryImages.size()) {
			keepFiles.add(c.directoryImages.get(c.currentImageIndex));
			if (c.currentImageIndex + 1 < c.directoryImages.size())
				keepFiles.add(c.directoryImages.get(c.currentImageIndex + 1));
			if (c.currentImageIndex - 1 >= 0)
				keepFiles.add(c.directoryImages.get(c.currentImageIndex - 1));
		}

		List<File> toRemove = new ArrayList<>();
		for (java.util.Map.Entry<File, CanvasInstance.PreloadedFileState> entry : c.preloadCache.entrySet()) {
			File file = entry.getKey();
			CanvasInstance.PreloadedFileState state = entry.getValue();
			if (keepFiles.contains(file))
				continue;
			if (state.isStale(CACHE_EXPIRY_MS))
				toRemove.add(file);
		}

		if (c.preloadCache.size() > MAX_CACHE_SIZE) {
			List<File> nonKeepFiles = new ArrayList<>();
			for (java.util.Map.Entry<File, CanvasInstance.PreloadedFileState> entry : c.preloadCache.entrySet()) {
				if (!keepFiles.contains(entry.getKey()))
					nonKeepFiles.add(entry.getKey());
			}
			nonKeepFiles.sort((f1, f2) -> Long.compare(
					c.preloadCache.get(f1).timestamp,
					c.preloadCache.get(f2).timestamp));
			int toRemoveCount = c.preloadCache.size() - MAX_CACHE_SIZE;
			for (int i = 0; i < toRemoveCount && i < nonKeepFiles.size(); i++)
				toRemove.add(nonKeepFiles.get(i));
		}

		for (File f : toRemove)
			c.preloadCache.remove(f);
	}
}
