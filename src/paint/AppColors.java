package paint;

import java.awt.Color;

/**
 * Shared color palette used by all UI classes.
 * Single source of truth – change here, affects everywhere.
 */
public final class AppColors {
    // console.log("### AppColors.java ###");

    private AppColors() {}

    public static final Color BG_DARK        = new Color(30,  30,  30);
    public static final Color BG_PANEL       = new Color(45,  45,  45);
    public static final Color BG_TOOLBAR     = new Color(38,  38,  38);
    public static final Color ACCENT         = new Color(0,  150, 255);
    public static final Color ACCENT_HOVER   = new Color(0,  180, 255);
    public static final Color ACCENT_ACTIVE  = new Color(0,  120, 220);
    public static final Color BTN_BG         = new Color(60,  60,  60);
    public static final Color BTN_HOVER      = new Color(80,  80,  80);
    public static final Color BTN_ACTIVE     = new Color(0,  130, 230);
    public static final Color TEXT           = new Color(220, 220, 220);
    public static final Color TEXT_MUTED     = new Color(140, 140, 140);
    public static final Color BORDER         = new Color(70,  70,  70);
    public static final Color SUCCESS        = new Color(60,  180,  80);
    public static final Color SUCCESS_HOVER  = new Color(80,  200, 100);
    public static final Color DANGER         = new Color(200,  60,  60);
    public static final Color DANGER_HOVER   = new Color(220,  80,  80);
    public static final Color WARNING        = new Color(220, 160,   0);
}
