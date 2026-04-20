package paint;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;

/**
 * Controls the secondary preview window (F1–F7): initialization, show/hide,
 * fullscreen, preview mode cycling, canvas display mode, snapshot, and
 * apply-to-canvas. Card list panels are dynamically added/removed per zone.
 */
class SecondaryWindowController {

	private final SelectiveAlphaEditor ed;

	// Dynamic card-list zones – any number of TranslationMapListPanel per side
	private final List<TranslationMapListPanel> leftPanels  = new ArrayList<>();
	private final List<TranslationMapListPanel> rightPanels = new ArrayList<>();
	private JPanel     leftZone;
	private JPanel     rightZone;
	private JSplitPane leftSplit;    // leftZone | centerWrap
	private JSplitPane outerSplit;   // leftSplit | rightZone
	private int savedLeftDivider  = -1;
	private int savedRightDivider = -1;

	SecondaryWindowController(SelectiveAlphaEditor ed) {
		this.ed = ed;
	}

	void initSecondaryWindow() {
		ed.secPanel = new SecondaryPanel(ed);
		ed.secPanel.setBackground(Color.BLACK);
		ed.secWin = new JFrame("TransparencyTool - Canvas Preview");
		ed.secWin.setUndecorated(false);
		ed.secWin.setResizable(true);
		ed.secWin.setSize(900, 600);
		ed.secWin.setLocationRelativeTo(ed);

		// Zones for dynamic card-list panels
		leftZone = new JPanel();
		leftZone.setLayout(new BoxLayout(leftZone, BoxLayout.X_AXIS));
		leftZone.setMinimumSize(new java.awt.Dimension(0, 0));

		rightZone = new JPanel();
		rightZone.setLayout(new BoxLayout(rightZone, BoxLayout.X_AXIS));
		rightZone.setMinimumSize(new java.awt.Dimension(0, 0));

		// Split panes: leftZone | secPanel | rightZone
		JPanel centerWrap = new JPanel(new BorderLayout());
		centerWrap.add(ed.secPanel, BorderLayout.CENTER);
		centerWrap.setMinimumSize(new java.awt.Dimension(100, 0));

		leftSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftZone, centerWrap);
		leftSplit.setResizeWeight(0.0);       // center gets all extra space
		leftSplit.setContinuousLayout(true);
		leftSplit.setBorder(null);

		outerSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftSplit, rightZone);
		outerSplit.setResizeWeight(1.0);      // center gets all extra space
		outerSplit.setContinuousLayout(true);
		outerSplit.setBorder(null);

		// Control bar
		ed.secControlBar = new SecondaryControlBar(ed, this);
		JPanel barHolder = new JPanel(new BorderLayout());
		barHolder.add(ed.secControlBar, BorderLayout.CENTER);
		ed.secControlBar.setBarHolder(barHolder);

		ed.secWin.getContentPane().setLayout(new BorderLayout());
		ed.secWin.getContentPane().add(outerSplit, BorderLayout.CENTER);
		ed.secWin.getContentPane().add(barHolder, BorderLayout.SOUTH);

		ed.secWin.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
		ed.secTimer = new javax.swing.Timer(16, e -> ed.secPanel.repaint());

		// Restore saved divider widths
		AppSettings s = AppSettings.getInstance();
		savedLeftDivider  = s.getCardPanelLeftWidth();
		savedRightDivider = s.getCardPanelRightWidth();

		// Apply dividers and attach listeners once the window is shown
		ed.secWin.addWindowListener(new java.awt.event.WindowAdapter() {
			@Override public void windowOpened(java.awt.event.WindowEvent e) {
				applyDividers();
				leftSplit.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, ev -> {
					if (!leftPanels.isEmpty()) {
						int loc = leftSplit.getDividerLocation();
						if (loc > 10) {
							AppSettings.getInstance().setCardPanelLeftWidth(loc);
							savedLeftDivider = loc;
						}
					}
				});
				outerSplit.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, ev -> {
					if (!rightPanels.isEmpty()) {
						int w   = outerSplit.getWidth();
						int loc = outerSplit.getDividerLocation();
						int rw  = w - loc - outerSplit.getDividerSize();
						if (rw > 10) {
							AppSettings.getInstance().setCardPanelRightWidth(rw);
							savedRightDivider = rw;
						}
					}
				});
			}
		});
	}

	private void applyDividers() {
		leftSplit.setDividerLocation(!leftPanels.isEmpty() ? Math.max(80, savedLeftDivider) : 0);
		int totalW = outerSplit.getWidth();
		if (!rightPanels.isEmpty()) {
			int rw = Math.max(80, savedRightDivider);
			outerSplit.setDividerLocation(Math.max(0, totalW - rw - outerSplit.getDividerSize()));
		} else {
			outerSplit.setDividerLocation(totalW > 0 ? totalW : 9999);
		}
	}

	// ── Dynamic panel management ──────────────────────────────────────────────

	/** Add a new TranslationMapListPanel to the left or right zone. */
	void addPanel(String language, boolean leftSide) {
		if (ed.secWin == null) initSecondaryWindow();
		List<TranslationMapListPanel> zone      = leftSide ? leftPanels : rightPanels;
		JPanel                        zonePanel = leftSide ? leftZone   : rightZone;

		TranslationMapListPanel p = new TranslationMapListPanel(
				language, AppColors.BG_PANEL, this::removePanelFromZone);
		zone.add(p);
		zonePanel.add(p);
		zonePanel.revalidate();
		zonePanel.repaint();

		if (zone.size() == 1) showZone(leftSide);
		syncControlBar();
	}

	private void removePanelFromZone(TranslationMapListPanel p) {
		if (leftPanels.remove(p)) {
			leftZone.remove(p);
			leftZone.revalidate();
			leftZone.repaint();
			if (leftPanels.isEmpty()) hideZone(true);
		} else if (rightPanels.remove(p)) {
			rightZone.remove(p);
			rightZone.revalidate();
			rightZone.repaint();
			if (rightPanels.isEmpty()) hideZone(false);
		}
		syncControlBar();
	}

	private void showZone(boolean leftSide) {
		if (leftSide) {
			leftSplit.setDividerLocation(Math.max(80, savedLeftDivider));
		} else {
			int totalW = outerSplit.getWidth();
			int rw = Math.max(80, savedRightDivider);
			outerSplit.setDividerLocation(Math.max(0, totalW - rw - outerSplit.getDividerSize()));
		}
	}

	private void hideZone(boolean leftSide) {
		if (leftSide) {
			savedLeftDivider = Math.max(80, leftSplit.getDividerLocation());
			leftSplit.setDividerLocation(0);
		} else {
			int totalW = outerSplit.getWidth();
			int loc = outerSplit.getDividerLocation();
			savedRightDivider = Math.max(80, totalW - loc - outerSplit.getDividerSize());
			outerSplit.setDividerLocation(totalW > 0 ? totalW : 9999);
		}
		try { AppSettings.getInstance().save(); } catch (Exception ex) { /* ignore */ }
	}

	void applyCardDisplaySettings() {
		leftPanels.forEach(TranslationMapListPanel::refresh);
		rightPanels.forEach(TranslationMapListPanel::refresh);
	}

	private void syncControlBar() {
		if (ed.secControlBar != null)
			ed.secControlBar.syncState();
	}

	// ── Secondary window lifecycle ────────────────────────────────────────────

	void toggleSecondaryWindow() {
		if (ed.secWin == null)
			initSecondaryWindow();
		if (ed.secWin.isVisible()) {
			ed.secTimer.stop();
			ed.secWin.setVisible(false);
		} else {
			if (ed.secMode != PreviewMode.SNAPSHOT)
				ed.secTimer.start();
			ed.secWin.setVisible(true);
			ed.secPanel.repaint();
		}
	}

	void cyclePreviewMode() {
		if (ed.secWin == null)
			initSecondaryWindow();
		ed.secMode = switch (ed.secMode) {
		case SNAPSHOT      -> PreviewMode.LIVE_ALL;
		case LIVE_ALL      -> PreviewMode.LIVE_ALL_EDIT;
		case LIVE_ALL_EDIT -> PreviewMode.SNAPSHOT;
		};
		if (ed.secWin.isVisible()) {
			if (ed.secMode == PreviewMode.SNAPSHOT)
				ed.secTimer.stop();
			else
				ed.secTimer.start();
			ed.secPanel.repaint();
		}
		ToastNotification.show(toastOwner(), "Preview: " + ed.secMode.name());
		syncControlBar();
	}

	void refreshSnapshot() {
		if (ed.secWin == null)
			initSecondaryWindow();
		BufferedImage src = ed.ci().workingImage;
		if (src == null)
			return;
		ed.secSnapshot = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2 = ed.secSnapshot.createGraphics();
		g2.drawImage(src, 0, 0, null);
		for (Layer el : ed.ci().activeElements) {
			if (el instanceof ImageLayer il)
				g2.drawImage(il.image(), il.x(), il.y(), il.width(), il.height(), null);
		}
		g2.dispose();
		if (ed.secWin != null && ed.secWin.isVisible())
			ed.secPanel.repaint();
		ToastNotification.show(toastOwner(), "Snapshot updated");
		syncControlBar();
	}

	void toggleSecondaryFullscreen() {
		if (ed.secWin == null)
			initSecondaryWindow();
		if (!ed.secWin.isVisible())
			ed.secWin.setVisible(true);

		if (ed.secFullscreen) {
			ed.secWin.setExtendedState(JFrame.NORMAL);
			ed.secWin.setBounds(ed.secOldX, ed.secOldY, ed.secOldW, ed.secOldH);
			ed.secFullscreen = false;
			ToastNotification.show(toastOwner(), "Fullscreen: OFF");
		} else {
			ed.secOldX = ed.secWin.getX();
			ed.secOldY = ed.secWin.getY();
			ed.secOldW = ed.secWin.getWidth();
			ed.secOldH = ed.secWin.getHeight();
			ed.secWin.setExtendedState(JFrame.MAXIMIZED_BOTH);
			ed.secFullscreen = true;
			ToastNotification.show(toastOwner(), "Fullscreen: ON");
		}
		syncControlBar();
	}

	void cycleAlwaysOnTop() {
		if (ed.secWin == null)
			initSecondaryWindow();
		if (!ed.secWin.isVisible())
			ed.secWin.setVisible(true);

		ed.secAlwaysOnTop = switch (ed.secAlwaysOnTop) {
		case TO_FRONT      -> AlwaysOnTopMode.NORMAL;
		case NORMAL        -> AlwaysOnTopMode.TO_BACKGROUND;
		case TO_BACKGROUND -> AlwaysOnTopMode.TO_FRONT;
		};

		switch (ed.secAlwaysOnTop) {
		case TO_FRONT:
			ed.secWin.setAlwaysOnTop(true);
			ToastNotification.show(toastOwner(), "Window: Always on Top");
			break;
		case NORMAL:
			ed.secWin.setAlwaysOnTop(false);
			ToastNotification.show(toastOwner(), "Window: Normal");
			break;
		case TO_BACKGROUND:
			ed.secWin.setAlwaysOnTop(false);
			ToastNotification.show(toastOwner(), "Window: Behind Main");
			break;
		}
		syncControlBar();
	}

	void cycleCanvasDisplayMode() {
		if (ed.secWin == null)
			initSecondaryWindow();
		if (!ed.secWin.isVisible())
			ed.secWin.setVisible(true);

		ed.secCanvasMode = switch (ed.secCanvasMode) {
		case SHOW_CANVAS_I_ONLY  -> CanvasDisplayMode.SHOW_CANVAS_II_ONLY;
		case SHOW_CANVAS_II_ONLY -> CanvasDisplayMode.SHOW_ACTIVE_CANVAS;
		case SHOW_ACTIVE_CANVAS  -> CanvasDisplayMode.SHOW_CANVAS_I_ONLY;
		};

		String msg = switch (ed.secCanvasMode) {
		case SHOW_CANVAS_I_ONLY  -> "Display: Canvas I Only";
		case SHOW_CANVAS_II_ONLY -> "Display: Canvas II Only";
		case SHOW_ACTIVE_CANVAS  -> "Display: Active Canvas";
		};
		ToastNotification.show(toastOwner(), msg);
		if (ed.secPanel != null)
			ed.secPanel.repaint();
		syncControlBar();
	}

	void showSecondaryTextInput() {
		if (ed.secWin == null)
			initSecondaryWindow();
		if (!ed.secWin.isVisible()) {
			ed.secWin.setVisible(true);
			if (ed.secMode != PreviewMode.SNAPSHOT)
				ed.secTimer.start();
		}
		SwingUtilities.invokeLater(() -> ed.secPanel.showTextInput());
	}

	void addTextLayerFromInput(String text, int panelX, int panelY, int tfW, int tfH, int panelW, int panelH) {
		CanvasInstance c = ed.ci();
		int imgX = 50, imgY = 50, imgW = 300, imgH = 60;
		if (c.workingImage != null && panelW > 0 && panelH > 0) {
			int iw = c.workingImage.getWidth(), ih = c.workingImage.getHeight();
			double scale = Math.min((double) panelW / iw, (double) panelH / ih);
			int ox = (panelW - (int) (iw * scale)) / 2;
			int oy = (panelH - (int) (ih * scale)) / 2;
			imgX = Math.max(0, (int) ((panelX - ox) / scale));
			imgY = Math.max(0, (int) ((panelY - oy) / scale));
			imgW = Math.max(10, (int) (tfW / scale));
			imgH = Math.max(10, (int) (tfH / scale));
		}
		TextLayer tl = TextLayer.wrappingOf(c.nextElementId++, text, "SansSerif", 28, false, false,
				Color.WHITE, imgX, imgY, imgW, imgH);
		c.activeElements.add(tl);
		c.selectedElements.clear();
		c.selectedElements.add(tl);
		ed.markDirty();
		SwingUtilities.invokeLater(() -> {
			ed.refreshElementPanel();
			if (c.canvasPanel != null) c.canvasPanel.repaint();
		});
		if (ed.secPanel != null) ed.secPanel.repaint();
		ToastNotification.show(ed.secWin != null ? ed.secWin : ed, "Text hinzugefügt");
	}

	void applySecondaryWindowToCanvas() {
		if (ed.secWin == null || !ed.secWin.isVisible()) {
			ToastNotification.show(ed, "Secondary window not open");
			return;
		}

		BufferedImage imageToApply;
		if (ed.secMode == PreviewMode.SNAPSHOT) {
			if (ed.secSnapshot == null) {
				ToastNotification.show(ed, "No snapshot available");
				return;
			}
			imageToApply = ed.deepCopy(ed.secSnapshot);
		} else {
			CanvasInstance srcCi = ed.ci();
			if (srcCi.workingImage == null) {
				ToastNotification.show(ed, "Active canvas has no image");
				return;
			}
			imageToApply = ed.deepCopy(srcCi.workingImage);
		}

		CanvasInstance targetCi = ed.ci(1);
		targetCi.workingImage = ed.normalizeImage(imageToApply);
		targetCi.undoStack.clear();
		targetCi.redoStack.clear();
		targetCi.activeElements = new ArrayList<>();
		targetCi.selectedElements.clear();
		targetCi.zoom = 1.0;
		targetCi.userHasManuallyZoomed = false;

		if (targetCi.canvasPanel != null)
			targetCi.canvasPanel.repaint();
		if (ed.elementLayerPanel2 != null)
			ed.refreshElementPanel();

		ed.activeCanvasIndex = 1;
		ed.secondCanvasBtn.setSelected(true);
		ed.secondCanvasBtn.setEnabled(true);
		ed.updateLayoutVisibility();
		ed.centerCanvas(1);

		ToastNotification.show(ed, "Image applied to Canvas II");
	}

	/** Returns secWin as toast owner when it is visible, otherwise the main frame. */
	private JFrame toastOwner() {
		return (ed.secWin != null && ed.secWin.isVisible()) ? ed.secWin : ed;
	}
}
