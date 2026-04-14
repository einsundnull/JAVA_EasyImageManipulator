package paint;

import java.io.Serializable;

/**
 * Represents a translation map with language and section information.
 * Maps are stored separately (like Images, Scenes, Games, Books) in their own directory.
 */
public class TranslationMap implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String id;           // unique map identifier
    private final String language;     // language code (e.g., "de", "en", "fr")
    private final String section;      // section for translation (e.g., "intro", "chapter1")
    private final String textI;        // first text content
    private final String textII;       // second text content
    private final long createdAt;      // timestamp when created
    private long modifiedAt;           // timestamp when last modified

    public TranslationMap(String id, String language, String section, String textI, String textII) {
        this(id, language, section, textI, textII, System.currentTimeMillis());
    }

    public TranslationMap(String id, String language, String section, String textI, String textII, long createdAt) {
        this.id = id;
        this.language = language;
        this.section = section;
        this.textI = textI;
        this.textII = textII;
        this.createdAt = createdAt;
        this.modifiedAt = createdAt;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────
    public String id()         { return id; }
    public String language()   { return language; }
    public String section()    { return section; }
    public String textI()      { return textI; }
    public String textII()     { return textII; }
    public long createdAt()    { return createdAt; }
    public long modifiedAt()   { return modifiedAt; }

    public void updateModifiedTime() {
        this.modifiedAt = System.currentTimeMillis();
    }

    public void setModifiedTime(long timestamp) {
        this.modifiedAt = timestamp;
    }

    @Override
    public String toString() {
        int len1 = textI != null ? textI.length() : 0;
        int len2 = textII != null ? textII.length() : 0;
        return String.format("Map{id=%s, lang=%s, section=%s, textI=%d, textII=%d}",
            id, language, section, len1, len2);
    }
}
