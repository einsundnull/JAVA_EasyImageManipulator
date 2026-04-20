package paint;

import java.awt.Color;

/** Card list (right) — shows/edits textII, uses its own TTS language. */
class RightCardListPanel extends CardListPanel {

    @Override String getTitle() { return "Text II"; }

    @Override String getDisplayText(CardEntry e) { return e.textII; }

    @Override void setDisplayText(CardEntry e, String text) { e.textII = text; }

    @Override String getTtsLang() { return AppSettings.getInstance().getCardTtsLanguageRight(); }

    @Override
    Color loadBgColor() {
        return new Color(AppSettings.getInstance().getCardListBgRight());
    }

    @Override
    void persistBgColor(Color c) {
        AppSettings.getInstance().setCardListBgRight(c.getRGB());
    }
}
