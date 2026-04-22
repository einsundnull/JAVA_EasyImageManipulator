package paint;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

public class Demo {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("SmartLabel Demo");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(900, 300);
            frame.setLocationRelativeTo(null);

            JPanel root = new JPanel(new BorderLayout());
            root.setBackground(Color.WHITE);

            SmartLabel smartLabel = new SmartLabel(
                    "2. Finde im Bild",
                    SmartLabel.Mode.PILL,
                    SmartLabel.ThemeColor.GREEN
            );

            JPanel center = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 30));
            center.setBackground(Color.WHITE);
            center.add(smartLabel);

            JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
            controls.setBackground(Color.WHITE);

            JButton yellowBtn = new JButton("Gelb");
            JButton greenBtn = new JButton("Grün");
            JButton blueBtn = new JButton("Blau");
            JButton redBtn = new JButton("Rot");

            JButton pillBtn = new JButton("Label");
            JButton bulletBtn = new JButton("Punkte");

            yellowBtn.addActionListener(e -> smartLabel.setThemeColor(SmartLabel.ThemeColor.YELLOW));
            greenBtn.addActionListener(e -> smartLabel.setThemeColor(SmartLabel.ThemeColor.GREEN));
            blueBtn.addActionListener(e -> smartLabel.setThemeColor(SmartLabel.ThemeColor.BLUE));
            redBtn.addActionListener(e -> smartLabel.setThemeColor(SmartLabel.ThemeColor.RED));

            pillBtn.addActionListener(e -> smartLabel.setMode(SmartLabel.Mode.PILL));
            bulletBtn.addActionListener(e -> smartLabel.setMode(SmartLabel.Mode.BULLET_LINE));

            controls.add(new JLabel("Farbe:"));
            controls.add(yellowBtn);
            controls.add(greenBtn);
            controls.add(blueBtn);
            controls.add(redBtn);

            controls.add(Box.createHorizontalStrut(20));

            controls.add(new JLabel("Typ:"));
            controls.add(pillBtn);
            controls.add(bulletBtn);

            root.add(controls, BorderLayout.NORTH);
            root.add(center, BorderLayout.CENTER);

            frame.setContentPane(root);
            frame.setVisible(true);
        });
    }
}