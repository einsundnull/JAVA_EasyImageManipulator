package paint.copy;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Manages file state caching, directory browsing, and dirty tracking.
 * Encapsulates file I/O and image navigation logic.
 */
public class FileStateCache {

    // Directory browsing
    private List<File> directoryImages = new ArrayList<>();
    private int currentImageIndex = -1;
    private File lastIndexedDir = null;
    private List<File> selectedImages = new ArrayList<>();

    // File cache & dirty tracking
    private final Map<File, CanvasState> fileStateCache = new LinkedHashMap<>();
    private final Set<File> dirtyFiles = new HashSet<>();

    // Current file
    private File sourceFile = null;

    // Project manager for scene data
    private ProjectManager projectManager = null;

    // UI/state callbacks
    private Runnable onImageLoaded = null;
    private Runnable onDirectoryIndexed = null;

    public FileStateCache() {
    }

    // ─────────────────────────────────────────────────────────────
    // Public API - File Management
    // ─────────────────────────────────────────────────────────────

    /**
     * Get the current source file.
     */
    public File getSourceFile() {
        return sourceFile;
    }

    /**
     * Set the current source file (triggers index/load).
     */
    public void setSourceFile(File file) {
        this.sourceFile = file;
        if (file != null) {
            indexDirectory(file);
        }
    }

    /**
     * Get the file state cache (LRU map).
     */
    public Map<File, CanvasState> getCache() {
        return fileStateCache;
    }

    /**
     * Save current canvas state to cache.
     * Call this before switching files.
     */
    public void saveCurrentState(File file, BufferedImage workingImage,
                                  Deque<BufferedImage> undoStack,
                                  Deque<BufferedImage> redoStack,
                                  List<Layer> activeElements) {
        if (file == null || workingImage == null) return;
        CanvasState cs = fileStateCache.computeIfAbsent(file, k -> new CanvasState(workingImage));
        cs.image = workingImage;
        cs.undoStack.clear();
        cs.undoStack.addAll(undoStack);
        cs.redoStack.clear();
        cs.redoStack.addAll(redoStack);
        cs.elements.clear();
        cs.elements.addAll(activeElements);
    }

    /**
     * Check if a file is in the cache.
     */
    public boolean isCached(File file) {
        return fileStateCache.containsKey(file);
    }

    /**
     * Get cached state for a file, or null if not cached.
     */
    public CanvasState getCachedState(File file) {
        return fileStateCache.get(file);
    }

    /**
     * Clear the entire cache (e.g., on project close).
     */
    public void clearCache() {
        fileStateCache.clear();
        dirtyFiles.clear();
    }

    // ─────────────────────────────────────────────────────────────
    // Public API - Dirty Tracking
    // ─────────────────────────────────────────────────────────────

    /**
     * Mark a file as having unsaved changes.
     */
    public void markDirty(File file) {
        if (file != null) {
            dirtyFiles.add(file);
        }
    }

    /**
     * Check if a file has unsaved changes.
     */
    public boolean isDirty(File file) {
        return file != null && dirtyFiles.contains(file);
    }

    /**
     * Clear the dirty flag for a file.
     */
    public void clearDirty(File file) {
        if (file != null) {
            dirtyFiles.remove(file);
        }
    }

    /**
     * Get all dirty files.
     */
    public Set<File> getDirtyFiles() {
        return new HashSet<>(dirtyFiles);
    }

    /**
     * Clear all dirty flags.
     */
    public void clearAllDirty() {
        dirtyFiles.clear();
    }

    // ─────────────────────────────────────────────────────────────
    // Public API - Navigation
    // ─────────────────────────────────────────────────────────────

    /**
     * Index the directory containing a file.
     * Populates directoryImages with supported image files.
     */
    public void indexDirectory(File imageFile) {
        if (imageFile == null) return;
        File parentDir = imageFile.getParentFile();
        if (parentDir == null || parentDir.equals(lastIndexedDir)) return;

        lastIndexedDir = parentDir;
        directoryImages.clear();
        currentImageIndex = -1;

        File[] files = parentDir.listFiles();
        if (files != null) {
            Arrays.sort(files);
            for (File f : files) {
                if (isSupportedFile(f)) {
                    directoryImages.add(f);
                    if (f.getAbsolutePath().equals(imageFile.getAbsolutePath())) {
                        currentImageIndex = directoryImages.size() - 1;
                    }
                }
            }
        }

        if (onDirectoryIndexed != null) {
            onDirectoryIndexed.run();
        }
    }

    /**
     * Navigate to previous image in directory.
     */
    public File navigatePrevious() {
        if (directoryImages.isEmpty() || currentImageIndex <= 0) return null;
        currentImageIndex--;
        return directoryImages.get(currentImageIndex);
    }

    /**
     * Navigate to next image in directory.
     */
    public File navigateNext() {
        if (directoryImages.isEmpty() || currentImageIndex >= directoryImages.size() - 1) return null;
        currentImageIndex++;
        return directoryImages.get(currentImageIndex);
    }

    /**
     * Check if can navigate to previous.
     */
    public boolean canNavigatePrevious() {
        return !directoryImages.isEmpty() && currentImageIndex > 0;
    }

    /**
     * Check if can navigate to next.
     */
    public boolean canNavigateNext() {
        return !directoryImages.isEmpty() && currentImageIndex < directoryImages.size() - 1;
    }

    /**
     * Get the current image index.
     */
    public int getCurrentImageIndex() {
        return currentImageIndex;
    }

    /**
     * Get total number of images in directory.
     */
    public int getDirectoryImageCount() {
        return directoryImages.size();
    }

    /**
     * Get all images in current directory.
     */
    public List<File> getDirectoryImages() {
        return new ArrayList<>(directoryImages);
    }

    /**
     * Get selected images in the gallery.
     */
    public List<File> getSelectedImages() {
        return new ArrayList<>(selectedImages);
    }

    /**
     * Set selected images (gallery multi-select).
     */
    public void setSelectedImages(List<File> images) {
        selectedImages.clear();
        if (images != null) {
            selectedImages.addAll(images);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Public API - Scene Data (Project Integration)
    // ─────────────────────────────────────────────────────────────

    /**
     * Load scene data (layers, zoom, mode) from project manager.
     */
    public void loadSceneData(File file, Callbacks callbacks) {
        if (projectManager == null || projectManager.getProjectName() == null) {
            return;
        }

        try {
            List<Layer> savedLayers = projectManager.loadScene(file);
            if (savedLayers != null) {
                callbacks.setActiveElements(savedLayers);
            }

            double savedZoom = projectManager.loadSceneZoom(file);
            if (savedZoom > 0) {
                callbacks.setZoom(savedZoom);
            }

            AppMode savedMode = projectManager.loadSceneMode(file);
            if (savedMode != null) {
                callbacks.setAppMode(savedMode);
            }
        } catch (IOException e) {
            System.err.println("[WARN] Fehler beim Laden der Szenen-Daten: " + e.getMessage());
        }
    }

    /**
     * Save scene data (layers, zoom, mode) to project manager.
     */
    public void saveSceneData(File file, List<Layer> layers, double zoom, AppMode mode)
            throws IOException {
        if (projectManager == null || projectManager.getProjectName() == null) {
            return;
        }
        projectManager.saveScene(file, layers, zoom, mode);
    }

    // ─────────────────────────────────────────────────────────────
    // Private Helpers
    // ─────────────────────────────────────────────────────────────

    private static boolean isSupportedFile(File f) {
        if (f == null || !f.isFile()) return false;
        String n = f.getName().toLowerCase();
        String[] SUPPORTED_EXTENSIONS = {"png", "jpg", "jpeg", "gif", "bmp", "webp"};
        for (String e : SUPPORTED_EXTENSIONS) {
            if (n.endsWith("." + e)) return true;
        }
        return false;
    }

    // ─────────────────────────────────────────────────────────────
    // Setters for Context
    // ─────────────────────────────────────────────────────────────

    public void setProjectManager(ProjectManager pm) {
        this.projectManager = pm;
    }

    public void setOnImageLoaded(Runnable r) {
        this.onImageLoaded = r;
    }

    public void setOnDirectoryIndexed(Runnable r) {
        this.onDirectoryIndexed = r;
    }

    // ─────────────────────────────────────────────────────────────
    // Callback Interface for External Dependencies
    // ─────────────────────────────────────────────────────────────

    /**
     * Callback interface for FileStateCache to notify the owner
     * when it needs to update shared state.
     */
    public interface Callbacks {
        void setActiveElements(List<Layer> elements);
        void setZoom(double zoom);
        void setAppMode(AppMode mode);
    }
}
