package paint;

import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.RenderingHints;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.Timer;

/**
 * Lightweight non-blocking toast popup.
 * Appears in the top-right area of the owner frame and auto-dismisses.
 */
public class ToastNotification {

    private static final int DURATION_MS = 2500;

    /** Show a toast on {@code owner}; auto-dismissed after ~2.5 s. */
    public static void show(JFrame owner, String message) {
        JWindow toast = new JWindow(owner);

        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(28, 28, 32));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.setColor(new Color(65, 65, 72));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                g2.dispose();
            }
        };
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 14));

        JLabel iconLbl = new JLabel("\u2713");   // ✓
        iconLbl.setFont(new Font("SansSerif", Font.BOLD, 15));
        iconLbl.setForeground(new Color(60, 200, 80));

        JLabel textLbl = new JLabel(message);
        textLbl.setFont(new Font("SansSerif", Font.PLAIN, 13));
        textLbl.setForeground(new Color(215, 215, 215));

        panel.add(iconLbl);
        panel.add(textLbl);

        toast.getContentPane().add(panel);
        toast.pack();

        // Use window translucency if the platform supports it
        try {
            GraphicsDevice gd = GraphicsEnvironment
                    .getLocalGraphicsEnvironment().getDefaultScreenDevice();
            if (gd.isWindowTranslucencySupported(
                    GraphicsDevice.WindowTranslucency.TRANSLUCENT)) {
                toast.setOpacity(0.93f);
            }
        } catch (Exception ignored) {}

        // Position: top-right corner of the owner frame
        Rectangle ob = owner.getBounds();
        toast.setLocation(ob.x + ob.width - toast.getWidth() - 22, ob.y + 60);

        toast.setAlwaysOnTop(true);
        toast.setVisible(true);

        Timer timer = new Timer(DURATION_MS, e -> toast.dispose());
        timer.setRepeats(false);
        timer.start();
    }
}
