package book;

public class PaperFormat {

    public enum Format {
        A0(841, 1189),
        A1(594, 841),
        A2(420, 594),
        A3(297, 420),
        A4(210, 297),

        LETTER(216, 279),
        LEGAL(216, 356),
        TABLOID(279, 432),

        A0_DOC(841, 1189, 60, 60, 70, 50),
        A1_DOC(594, 841, 45, 45, 50, 40),
        A2_DOC(420, 594, 35, 35, 40, 30),
        A3_DOC(297, 420, 30, 30, 35, 25),
        A4_DOC(210, 297, 22, 28, 28, 20),

        LETTER_DOC(216, 279, 22, 28, 28, 20),
        LEGAL_DOC(216, 356, 22, 28, 28, 20),
        TABLOID_DOC(279, 432, 30, 30, 35, 25);

        private final int width;
        private final int height;

        private final int marginTop;
        private final int marginBottom;
        private final int marginInner;
        private final int marginOuter;

        // Standard ohne spezielle Dokument-Ränder
        Format(int width, int height) {
            this(width, height, 25, 25, 25, 25);
        }

        // Mit individuellen Dokument-Rändern
        Format(int width, int height,
               int marginTop, int marginBottom,
               int marginInner, int marginOuter) {
            this.width = width;
            this.height = height;
            this.marginTop = marginTop;
            this.marginBottom = marginBottom;
            this.marginInner = marginInner;
            this.marginOuter = marginOuter;
        }

        public int getWidthPortrait() {
            return width;
        }

        public int getHeightPortrait() {
            return height;
        }

        public int getWidthLandscape() {
            return height;
        }

        public int getHeightLandscape() {
            return width;
        }

        public String portrait() {
            return width + " x " + height + " mm";
        }

        public String landscape() {
            return height + " x " + width + " mm";
        }

        public int getMarginTop() {
            return marginTop;
        }

        public int getMarginBottom() {
            return marginBottom;
        }

        public int getMarginInner() {
            return marginInner;
        }

        public int getMarginOuter() {
            return marginOuter;
        }

        public int getTextWidth() {
            return width - marginInner - marginOuter;
        }

        public int getTextHeight() {
            return height - marginTop - marginBottom;
        }
    }
}