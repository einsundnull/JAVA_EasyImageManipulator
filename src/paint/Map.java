package paint;

import java.io.Serializable;

/**
 * Represents a translation map with language and section information.
 * Maps are stored separately (like Images, Scenes, Games, Books) in their own directory.
 */
public class Map implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String id;           // unique map identifier
    private final String language;     // language code (e.g., "de", "en", "fr")
    private final String section;      // section for translation (e.g., "intro", "chapter1")
    private final String content;      // the actual text content from TextLayer
    private final long createdAt;      // timestamp when created
    private long modifiedAt;           // timestamp when last modified

    public Map(String id, String language, String section, String content) {
        this.id = id;
        this.language = language;
        this.section = section;
        this.content = content;
        this.createdAt = System.currentTimeMillis();
        this.modifiedAt = createdAt;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────
    public String id()         { return id; }
    public String language()   { return language; }
    public String section()    { return section; }
    public String content()    { return content; }
    public long createdAt()    { return createdAt; }
    public long modifiedAt()   { return modifiedAt; }

    public void updateModifiedTime() {
        this.modifiedAt = System.currentTimeMillis();
    }

    @Override
    public String toString() {
        return String.format("Map{id=%s, lang=%s, section=%s, len=%d}",
            id, language, section, content != null ? content.length() : 0);
    }
}
