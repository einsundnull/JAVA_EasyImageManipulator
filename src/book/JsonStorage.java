package book;

import java.io.File;
import java.io.IOException;

public class JsonStorage {
    private final ObjectMapper mapper;
    private final File directory;

    public JsonStorage(File directory) {
        this.directory = directory;
        this.mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        if (!directory.exists()) {
            directory.mkdirs();
        }
    }

    // ===== Sheet =====
    public void writeSheet(String fileName, Sheet sheet) throws IOException {
        mapper.writeValue(new File(directory, fileName), sheet);
    }

    public Sheet readSheet(String fileName) throws IOException {
        return mapper.readValue(new File(directory, fileName), Sheet.class);
    }

    // ===== Format =====
    public void writeFormat(String fileName, PaperFormat format) throws IOException {
        mapper.writeValue(new File(directory, fileName), format);
    }

    public PaperFormat readFormat(String fileName) throws IOException {
        return mapper.readValue(new File(directory, fileName), PaperFormat.class);
    }
}





