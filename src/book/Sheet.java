package book;

public class Sheet {
    private String title;
    private int titleHeight;
    private String titleFontFamily;
    private int titleFontSize;
    private boolean titleBold;
    private boolean titleItalic;

    private int marginLeft;
    private int marginRight;
    private int marginTop;
    private int marginBottom;

    private int headerHeight;
    private int footerHeight;
    private boolean showPageNumber;

    private PaperFormat format;

    public Sheet() {}

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public int getTitleHeight() { return titleHeight; }
    public void setTitleHeight(int titleHeight) { this.titleHeight = titleHeight; }

    public String getTitleFontFamily() { return titleFontFamily; }
    public void setTitleFontFamily(String titleFontFamily) { this.titleFontFamily = titleFontFamily; }

    public int getTitleFontSize() { return titleFontSize; }
    public void setTitleFontSize(int titleFontSize) { this.titleFontSize = titleFontSize; }

    public boolean isTitleBold() { return titleBold; }
    public void setTitleBold(boolean titleBold) { this.titleBold = titleBold; }

    public boolean isTitleItalic() { return titleItalic; }
    public void setTitleItalic(boolean titleItalic) { this.titleItalic = titleItalic; }

    public int getMarginLeft() { return marginLeft; }
    public void setMarginLeft(int marginLeft) { this.marginLeft = marginLeft; }

    public int getMarginRight() { return marginRight; }
    public void setMarginRight(int marginRight) { this.marginRight = marginRight; }

    public int getMarginTop() { return marginTop; }
    public void setMarginTop(int marginTop) { this.marginTop = marginTop; }

    public int getMarginBottom() { return marginBottom; }
    public void setMarginBottom(int marginBottom) { this.marginBottom = marginBottom; }

    public int getHeaderHeight() { return headerHeight; }
    public void setHeaderHeight(int headerHeight) { this.headerHeight = headerHeight; }

    public int getFooterHeight() { return footerHeight; }
    public void setFooterHeight(int footerHeight) { this.footerHeight = footerHeight; }

    public boolean isShowPageNumber() { return showPageNumber; }
    public void setShowPageNumber(boolean showPageNumber) { this.showPageNumber = showPageNumber; }

    public PaperFormat getFormat() { return format; }
    public void setFormat(PaperFormat format) { this.format = format; }
}