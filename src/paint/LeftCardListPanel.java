package paint;

import java.awt.Color;

/** Card list (left) — shows/edits textI, uses its own TTS language. */
class LeftCardListPanel extends CardListPanel {

    @Override String getTitle() { return "Text I"; }

    @Override String getDisplayText(CardEntry e) { return e.textI; }

    @Override void setDisplayText(CardEntry e, String text) { e.textI = text; }

    @Override String getTtsLang() { return AppSettings.getInstance().getCardTtsLanguageLeft(); }

    @Override
    Color loadBgColor() {
        return new Color(AppSettings.getInstance().getCardListBgLeft());
    }

    @Override
    void persistBgColor(Color c) {
        AppSettings.getInstance().setCardListBgLeft(c.getRGB());
    }
}
