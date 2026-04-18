package paint;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

import javax.swing.JToggleButton;

/**
 * A JToggleButton whose selected state automatically mirrors the visibility of
 * a bound Component. Call bind(panel) once after both button and panel exist.
 *
 * The button cannot appear highlighted while its panel is hidden: any external
 * setVisible(false) on the panel immediately deselects the button via a
 * ComponentListener, with no additional synchronisation calls required.
 */
class PanelToggleButton extends JToggleButton {

	enum Style { MODE, BOOK }

	private final Color normalBg;
	private final Color hoverBg;

	PanelToggleButton(String symbol, Style style) {
		super(symbol);
		if (style == Style.BOOK) {
			normalBg = new Color(45, 52, 72);
			hoverBg  = new Color(58, 66, 90);
		} else {
			normalBg = AppColors.BTN_BG;
			hoverBg  = AppColors.BTN_HOVER;
		}
	}

	/**
	 * Permanently links this button to {@code panel}.
	 * Whenever the panel is shown or hidden the button is automatically
	 * selected or deselected.  The button's current state is synchronised
	 * immediately to match the panel's current visibility.
	 */
	void bind(Component panel) {
		setSelected(panel.isVisible());
		panel.addComponentListener(new ComponentAdapter() {
			@Override public void componentShown(ComponentEvent e)  { setSelected(true);  repaint(); }
			@Override public void componentHidden(ComponentEvent e) { setSelected(false); repaint(); }
		});
	}

	@Override
	protected void paintComponent(Graphics g) {
		Graphics2D g2 = (Graphics2D) g;
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		Color bg = isSelected() ? AppColors.ACCENT_ACTIVE
				: (getModel().isRollover() ? hoverBg : normalBg);
		g2.setColor(bg);
		g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
		if (isSelected()) {
			g2.setColor(AppColors.ACCENT);
			g2.setStroke(new BasicStroke(2f));
			g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 8, 8);
		}
		super.paintComponent(g);
	}
}
