package book;

import java.io.File;
import java.io.IOException;

/**
 * Placeholder – Jackson (ObjectMapper) is not on the module path.
 * Serialization is currently handled by BookController using plain text manifests.
 */
public class JsonStorage {

    private final File directory;

    public JsonStorage(File directory) {
        this.directory = directory;
        if (!directory.exists()) directory.mkdirs();
    }

    public void writeSheet(String fileName, Sheet sheet) throws IOException {
        throw new UnsupportedOperationException("JsonStorage not implemented");
    }

    public Sheet readSheet(String fileName) throws IOException {
        throw new UnsupportedOperationException("JsonStorage not implemented");
    }

    public void writeFormat(String fileName, PaperFormat format) throws IOException {
        throw new UnsupportedOperationException("JsonStorage not implemented");
    }

    public PaperFormat readFormat(String fileName) throws IOException {
        throw new UnsupportedOperationException("JsonStorage not implemented");
    }
}
