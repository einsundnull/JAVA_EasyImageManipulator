package paint;

/** Mutable data model for one translation-map card (textI = source, textII = translation). */
class CardEntry {
    String id;
    String textI;
    String textII;

    CardEntry(String id, String textI, String textII) {
        this.id    = id;
        this.textI = textI  != null ? textI  : "";
        this.textII = textII != null ? textII : "";
    }
}
