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
        TABLOID(279, 432);

        private final int width;
        private final int height;

        Format(int width, int height) {
            this.width = width;
            this.height = height;
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
    }

    public static void main(String[] args) {
        for (Format format : Format.values()) {
            System.out.println(format.name());
            System.out.println("  Hochformat: " + format.portrait());
            System.out.println("  Querformat: " + format.landscape());
            System.out.println();
        }
    }
}