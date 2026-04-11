package com.spriteanimator;

import javax.swing.SwingUtilities;

import com.spriteanimator.ui.MainWindow;

/**
 * Sprite Animator – Entry point.
 * Startet die Anwendung auf dem Event Dispatch Thread.
 */
public class SpriteAnimator {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(MainWindow::new);
    }
}
