package paint;

/** Discriminates the two kinds of non-destructive layers. */
public enum ElementType {
    /** A rasterised pixel region (pasted image, drawn stroke, lifted selection, …). */
    IMAGE_LAYER,
    /** A live-text region whose glyphs are rendered on-the-fly from stored text + font settings. */
    TEXT_LAYER
}
