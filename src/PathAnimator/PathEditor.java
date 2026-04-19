package PathAnimator;

import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.dnd.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;

public class PathEditor extends JFrame {

    // ── Model ──────────────────────────────────────────────────────────────

    static class PathPoint {
        double x, y, z;
        PathPoint(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
        PathPoint copy() { return new PathPoint(x, y, z); }
        @Override public String toString() {
            return String.format("(%.1f, %.1f, %.1f)", x, y, z);
        }
    }

    static class PathModel {
        final String name;
        final List<PathPoint> points = new ArrayList<>();
        PathModel parentPath     = null;
        int       parentPointIdx = -1;
        PathModel(String name) { this.name = name; }
        boolean isBranch() { return parentPath != null; }
        @Override public String toString() { return name; }
    }

    // ── Animation model ─────────────────────────────────────────────────────

    static class Keyframe {
        int      frame;
        double[] xs, ys, zs;
        Keyframe(int frame, List<PathPoint> pts) {
            this.frame = frame;
            xs = new double[pts.size()];
            ys = new double[pts.size()];
            zs = new double[pts.size()];
            for (int i = 0; i < pts.size(); i++) {
                xs[i] = pts.get(i).x;
                ys[i] = pts.get(i).y;
                zs[i] = pts.get(i).z;
            }
        }
        Keyframe(Keyframe src) {
            this.frame = src.frame;
            this.xs = src.xs.clone();
            this.ys = src.ys.clone();
            this.zs = src.zs.clone();
        }
    }

    static class AnimTrack {
        PathModel       path;
        List<Keyframe>  keyframes = new ArrayList<>();

        void addKeyframe(int frame) {
            keyframes.removeIf(k -> k.frame == frame);
            keyframes.add(new Keyframe(frame, path.points));
            keyframes.sort(Comparator.comparingInt(k -> k.frame));
        }

        void removeKeyframe(int frame) {
            keyframes.removeIf(k -> k.frame == frame);
        }

        void applyAtFrame(double t) {
            if (keyframes.isEmpty()) return;
            Keyframe first = keyframes.get(0), last = keyframes.get(keyframes.size() - 1);
            if (t <= first.frame) { applyKf(first); return; }
            if (t >= last.frame)  { applyKf(last);  return; }
            for (int i = 0; i < keyframes.size() - 1; i++) {
                Keyframe a = keyframes.get(i), b = keyframes.get(i + 1);
                if (t >= a.frame && t <= b.frame) {
                    double alpha = (t - a.frame) / (double)(b.frame - a.frame);
                    interpolate(a, b, alpha);
                    return;
                }
            }
        }

        private void applyKf(Keyframe kf) {
            int n = Math.min(path.points.size(), kf.xs.length);
            for (int i = 0; i < n; i++) {
                path.points.get(i).x = kf.xs[i];
                path.points.get(i).y = kf.ys[i];
                path.points.get(i).z = kf.zs[i];
            }
        }

        private void interpolate(Keyframe a, Keyframe b, double t) {
            int n = Math.min(path.points.size(), Math.min(a.xs.length, b.xs.length));
            for (int i = 0; i < n; i++) {
                path.points.get(i).x = lerp(a.xs[i], b.xs[i], t);
                path.points.get(i).y = lerp(a.ys[i], b.ys[i], t);
                path.points.get(i).z = lerp(a.zs[i], b.zs[i], t);
            }
        }

        static double lerp(double a, double b, double t) { return a + (b - a) * t; }

        /** Keyframe exists at this exact frame? */
        boolean hasKeyframeAt(int frame) {
            for (Keyframe k : keyframes) if (k.frame == frame) return true;
            return false;
        }
    }

    static class Animation {
        String         name        = "Animation";
        int            totalFrames = 120;
        int            fps         = 24;
        List<AnimTrack> tracks     = new ArrayList<>();

        AnimTrack getOrCreate(PathModel path) {
            for (AnimTrack t : tracks) if (t.path == path) return t;
            AnimTrack t = new AnimTrack();
            t.path = path;
            tracks.add(t);
            return t;
        }

        AnimTrack trackFor(PathModel path) {
            for (AnimTrack t : tracks) if (t.path == path) return t;
            return null;
        }

        void applyAtFrame(double frame) {
            for (AnimTrack t : tracks) t.applyAtFrame(frame);
        }
    }

    // ── App state ───────────────────────────────────────────────────────────

    final List<PathModel> paths = new ArrayList<>();
    PathModel selectedPath  = null;
    int       selectedPoint = -1;
    int       pathCounter   = 1;
    boolean   updating      = false;

    // ── Sprite / skeleton binding ───────────────────────────────────────────

    BufferedImage spriteImage = null;
    double        spriteX = 100, spriteY = 100;
    Map<PathModel, List<PathPoint>> restPoses  = new LinkedHashMap<>();
    Map<PathModel, float[]>         weightMaps = new LinkedHashMap<>();
    JLabel bindLabel;

    // ── Paint / heatmap mode ────────────────────────────────────────────────

    boolean paintMode    = false;
    int     brushRadius  = 20;
    float   brushOpacity = 0.08f;
    JButton paintModeBtn;
    int     paintCursorX = -1, paintCursorY = -1;

    static final int MESH_COLS = 14, MESH_ROWS = 14;

    // ── Keyframe clipboard ──────────────────────────────────────────────────

    Keyframe  copiedKeyframe = null;

    // ── Animation state ─────────────────────────────────────────────────────

    Animation      animation    = new Animation();
    double         currentFrame = 0;
    boolean        playing      = false;
    javax.swing.Timer playTimer;
    JButton        playBtn;
    JLabel         frameLabel;
    JSpinner       frameSpinner;
    TimelinePanel  timelinePanel;

    // ── UI references ───────────────────────────────────────────────────────

    JPanel      pathListPanel;
    JTextField  xField, yField, zField;
    CanvasPanel canvas;

    // ── Constructor ─────────────────────────────────────────────────────────

    PathEditor() {
        super("Path Editor");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1200, 820);
        setLayout(new BorderLayout());

        // ── Left panel ──────────────────────────────────────────────────────
        JPanel left = new JPanel(new BorderLayout(4, 4));
        left.setPreferredSize(new Dimension(270, 0));
        left.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        JPanel pathBtns = new JPanel(new GridLayout(1, 2, 4, 0));
        JButton newBtn = new JButton("Neuer Pfad  (ALT+N)");
        JButton delPathBtn = new JButton("Pfad löschen  (ALT+DEL)");
        newBtn    .addActionListener(e -> createNewPath());
        delPathBtn.addActionListener(e -> deleteSelectedPath());
        pathBtns.add(newBtn);
        pathBtns.add(delPathBtn);
        left.add(pathBtns, BorderLayout.NORTH);

        pathListPanel = new JPanel();
        pathListPanel.setLayout(new BoxLayout(pathListPanel, BoxLayout.Y_AXIS));
        JScrollPane scroll = new JScrollPane(pathListPanel);
        scroll.setBorder(BorderFactory.createTitledBorder("Pfade / Punkte"));
        left.add(scroll, BorderLayout.CENTER);

        // ── Coordinate fields ────────────────────────────────────────────────
        JPanel coords = new JPanel(new GridLayout(3, 2, 4, 4));
        coords.setBorder(BorderFactory.createTitledBorder("Koordinaten"));
        xField = new JTextField("0.00");
        yField = new JTextField("0.00");
        zField = new JTextField("0.00");
        coords.add(new JLabel("X:")); coords.add(xField);
        coords.add(new JLabel("Y:")); coords.add(yField);
        coords.add(new JLabel("Z:")); coords.add(zField);

        JPanel spritePanel = new JPanel(new GridLayout(0, 1, 2, 2));
        spritePanel.setBorder(BorderFactory.createTitledBorder("Sprite"));
        JButton loadSpriteBtn = new JButton("Sprite laden…");
        JButton bindBtn       = new JButton("Skelett binden");
        JButton unbindBtn     = new JButton("Bindung lösen");
        paintModeBtn          = new JButton("🎨 Heatmap malen");
        bindLabel = new JLabel("<html><small><i>kein Sprite</i></small></html>");
        loadSpriteBtn.addActionListener(e -> loadSprite());
        bindBtn      .addActionListener(e -> bindSkeleton());
        unbindBtn    .addActionListener(e -> unbind());
        paintModeBtn .addActionListener(e -> togglePaintMode());
        spritePanel.add(loadSpriteBtn);
        spritePanel.add(bindBtn);
        spritePanel.add(unbindBtn);
        spritePanel.add(paintModeBtn);
        spritePanel.add(bindLabel);

        JButton removeBtn = new JButton("Punkt entfernen  (DEL)");
        removeBtn.addActionListener(e -> removeSelectedPoint());
        JPanel hints = new JPanel(new GridLayout(4, 1));
        hints.add(removeBtn);
        hints.add(new JLabel("<html><small>+/NUM+ → Punkt nach N</small></html>"));
        hints.add(new JLabel("<html><small>CTRL++ → Punkt vor N</small></html>"));
        hints.add(new JLabel("<html><small>SHIFT+K → Keyframe löschen</small></html>"));

        JPanel bottom = new JPanel(new BorderLayout(4, 4));
        bottom.add(coords,      BorderLayout.NORTH);
        bottom.add(spritePanel, BorderLayout.CENTER);
        bottom.add(hints,       BorderLayout.SOUTH);
        left.add(bottom, BorderLayout.SOUTH);

        DocumentListener dl = new DocumentListener() {
            public void insertUpdate(DocumentEvent e)  { onCoordTyped(); }
            public void removeUpdate(DocumentEvent e)  { onCoordTyped(); }
            public void changedUpdate(DocumentEvent e) { onCoordTyped(); }
        };
        xField.getDocument().addDocumentListener(dl);
        yField.getDocument().addDocumentListener(dl);
        zField.getDocument().addDocumentListener(dl);

        canvas = new CanvasPanel();

        // ── Timeline / animation controls ────────────────────────────────────
        JPanel animControls = buildAnimControls();
        timelinePanel = new TimelinePanel();
        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.add(animControls,  BorderLayout.NORTH);
        southPanel.add(timelinePanel, BorderLayout.CENTER);

        add(left,       BorderLayout.WEST);
        add(canvas,     BorderLayout.CENTER);
        add(southPanel, BorderLayout.SOUTH);

        buildPlayTimer();
        bindKeys();
        setLocationRelativeTo(null);
        setVisible(true);
        installDropTarget();
    }

    // ── Animation controls panel ─────────────────────────────────────────────

    JPanel buildAnimControls() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
        p.setBackground(new Color(35, 35, 45));

        JButton stopBtn = makeAnimBtn("■");
        playBtn         = makeAnimBtn("▶");
        JButton prevKfBtn = makeAnimBtn("|◀");
        JButton nextKfBtn = makeAnimBtn("▶|");
        JButton kfBtn    = new JButton("◆ Keyframe  (K)");
        JButton kfDelBtn = new JButton("✕ KF löschen  (SHIFT+K)");
        kfBtn   .addActionListener(e -> addKeyframeForSelected());
        kfDelBtn.addActionListener(e -> removeKeyframeForSelected());

        stopBtn  .addActionListener(e -> stop());
        playBtn  .addActionListener(e -> togglePlay());
        prevKfBtn.addActionListener(e -> seekPrevKeyframe());
        nextKfBtn.addActionListener(e -> seekNextKeyframe());

        frameSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 9999, 1));
        frameSpinner.setPreferredSize(new Dimension(60, 24));
        frameSpinner.addChangeListener(e -> {
            if (!playing) seekToFrame((int) frameSpinner.getValue());
        });

        JSpinner totalSpinner = new JSpinner(new SpinnerNumberModel(120, 10, 9999, 10));
        totalSpinner.setPreferredSize(new Dimension(65, 24));
        totalSpinner.addChangeListener(e -> {
            animation.totalFrames = (int) totalSpinner.getValue();
            timelinePanel.repaint();
        });

        JSpinner fpsSpinner = new JSpinner(new SpinnerNumberModel(24, 1, 120, 1));
        fpsSpinner.setPreferredSize(new Dimension(50, 24));
        fpsSpinner.addChangeListener(e -> {
            animation.fps = (int) fpsSpinner.getValue();
            if (playing) { playTimer.stop(); buildPlayTimer(); playTimer.start(); }
        });

        frameLabel = new JLabel("/ 120");
        frameLabel.setForeground(Color.LIGHT_GRAY);

        p.add(stopBtn); p.add(prevKfBtn); p.add(playBtn); p.add(nextKfBtn);
        p.add(Box.createHorizontalStrut(8));
        p.add(new JLabel("Frame:") {{ setForeground(Color.LIGHT_GRAY); }});
        p.add(frameSpinner);
        p.add(frameLabel);
        p.add(Box.createHorizontalStrut(8));
        p.add(new JLabel("Gesamt:") {{ setForeground(Color.LIGHT_GRAY); }});
        p.add(totalSpinner);
        p.add(new JLabel("FPS:") {{ setForeground(Color.LIGHT_GRAY); }});
        p.add(fpsSpinner);
        p.add(Box.createHorizontalStrut(12));
        p.add(kfBtn);
        p.add(kfDelBtn);
        return p;
    }

    JButton makeAnimBtn(String label) {
        JButton b = new JButton(label);
        b.setPreferredSize(new Dimension(36, 24));
        return b;
    }

    // ── Playback ─────────────────────────────────────────────────────────────

    void buildPlayTimer() {
        if (playTimer != null) playTimer.stop();
        playTimer = new javax.swing.Timer(1000 / Math.max(1, animation.fps), e -> {
            currentFrame += 1.0;
            if (currentFrame > animation.totalFrames) currentFrame = 0;
            onFrameChanged();
        });
    }

    void togglePlay() {
        if (playing) pause(); else play();
    }

    void play() {
        playing = true;
        playBtn.setText("⏸");
        buildPlayTimer();
        playTimer.start();
    }

    void pause() {
        playing = false;
        playBtn.setText("▶");
        playTimer.stop();
    }

    void stop() {
        pause();
        seekToFrame(0);
    }

    void seekToFrame(int frame) {
        currentFrame = Math.max(0, Math.min(animation.totalFrames, frame));
        onFrameChanged();
    }

    void onFrameChanged() {
        animation.applyAtFrame(currentFrame);
        updating = true;
        frameSpinner.setValue((int) currentFrame);
        updating = false;
        syncCoordsFromModel();
        for (PathModel pm : paths) refreshCombo(pm);
        timelinePanel.repaint();
        canvas.repaint();
    }

    void addKeyframeForSelected() {
        if (selectedPath == null) return;
        animation.getOrCreate(selectedPath).addKeyframe((int) currentFrame);
        timelinePanel.repaint();
    }

    void removeKeyframeForSelected() {
        if (selectedPath == null) return;
        AnimTrack t = animation.trackFor(selectedPath);
        if (t == null) return;
        t.removeKeyframe((int) currentFrame);
        timelinePanel.repaint();
        canvas.repaint();
    }

    void copyKeyframe() {
        if (selectedPath == null) return;
        AnimTrack t = animation.trackFor(selectedPath);
        Keyframe kf = null;
        if (t != null)
            for (Keyframe k : t.keyframes) if (k.frame == (int) currentFrame) { kf = k; break; }
        if (kf == null) {
            // No keyframe here — snapshot current point positions
            kf = new Keyframe((int) currentFrame, selectedPath.points);
        }
        copiedKeyframe = new Keyframe(kf);
    }

    void pasteKeyframe() {
        if (copiedKeyframe == null || selectedPath == null) return;
        if (selectedPath.points.size() != copiedKeyframe.xs.length) {
            JOptionPane.showMessageDialog(this,
                "Punktanzahl stimmt nicht überein (" + copiedKeyframe.xs.length
                + " → " + selectedPath.points.size() + ").");
            return;
        }
        for (int i = 0; i < selectedPath.points.size(); i++) {
            selectedPath.points.get(i).x = copiedKeyframe.xs[i];
            selectedPath.points.get(i).y = copiedKeyframe.ys[i];
            selectedPath.points.get(i).z = copiedKeyframe.zs[i];
        }
        animation.getOrCreate(selectedPath).addKeyframe((int) currentFrame);
        syncCoordsFromModel();
        for (PathModel pm : paths) refreshCombo(pm);
        timelinePanel.repaint();
        canvas.repaint();
    }

    void seekPrevKeyframe() {
        if (selectedPath == null) return;
        AnimTrack t = animation.trackFor(selectedPath);
        if (t == null) return;
        int target = -1;
        for (Keyframe kf : t.keyframes)
            if (kf.frame < (int) currentFrame) target = kf.frame;
        if (target >= 0) seekToFrame(target);
    }

    void seekNextKeyframe() {
        if (selectedPath == null) return;
        AnimTrack t = animation.trackFor(selectedPath);
        if (t == null) return;
        for (Keyframe kf : t.keyframes)
            if (kf.frame > (int) currentFrame) { seekToFrame(kf.frame); return; }
    }

    // ── Key bindings ────────────────────────────────────────────────────────

    void bindKeys() {
        JRootPane rp = getRootPane();
        InputMap  im = rp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = rp.getActionMap();

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_N,      InputEvent.ALT_DOWN_MASK),                             "newPath");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, InputEvent.ALT_DOWN_MASK),                             "deletePath");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_PLUS,   0),                           "insertAfter");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ADD,    0),                           "insertAfter");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_PLUS,   InputEvent.CTRL_DOWN_MASK),   "insertBefore");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ADD,    InputEvent.CTRL_DOWN_MASK),   "insertBefore");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_C,      InputEvent.CTRL_DOWN_MASK),   "copyKf");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_V,      InputEvent.CTRL_DOWN_MASK),   "pasteKfOrSprite");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE,  0),                           "togglePlay");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_K,      0),                           "addKf");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_K,      InputEvent.SHIFT_DOWN_MASK),  "removeKf");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT,   0),                           "prevFrame");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT,  0),                           "nextFrame");

        am.put("newPath",      new AbstractAction() { public void actionPerformed(ActionEvent e) { createNewPath();             } });
        am.put("deletePath",   new AbstractAction() { public void actionPerformed(ActionEvent e) { deleteSelectedPath();        } });
        am.put("insertAfter",  new AbstractAction() { public void actionPerformed(ActionEvent e) { insertPoint(false);          } });
        am.put("insertBefore", new AbstractAction() { public void actionPerformed(ActionEvent e) { insertPoint(true);           } });
        am.put("copyKf",         new AbstractAction() { public void actionPerformed(ActionEvent e) { copyKeyframe();             } });
        am.put("pasteKfOrSprite",new AbstractAction() { public void actionPerformed(ActionEvent e) {
            if (copiedKeyframe != null) pasteKeyframe(); else pasteFromClipboard();
        } });
        am.put("togglePlay",   new AbstractAction() { public void actionPerformed(ActionEvent e) { togglePlay();                } });
        am.put("addKf",        new AbstractAction() { public void actionPerformed(ActionEvent e) { addKeyframeForSelected();    } });
        am.put("removeKf",     new AbstractAction() { public void actionPerformed(ActionEvent e) { removeKeyframeForSelected(); } });
        am.put("prevFrame",    new AbstractAction() { public void actionPerformed(ActionEvent e) { if (!playing) seekToFrame((int) currentFrame - 1); } });
        am.put("nextFrame",    new AbstractAction() { public void actionPerformed(ActionEvent e) { if (!playing) seekToFrame((int) currentFrame + 1); } });

        // DEL via KeyEventDispatcher so it works even when a text field has focus
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
            if (e.getID() == KeyEvent.KEY_PRESSED && e.getKeyCode() == KeyEvent.VK_DELETE) {
                Component focused = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
                if (focused instanceof JTextField) return false; // let text field handle it
                removeSelectedPoint();
                return true;
            }
            return false;
        });
    }

    // ── Path / point management ─────────────────────────────────────────────

    void createNewPath() {
        PathModel path = new PathModel("Pfad " + pathCounter++);
        double cx = canvas.getWidth()  / 2.0;
        double cy = canvas.getHeight() / 2.0;
        path.points.add(new PathPoint(cx - 60, cy, 0));
        path.points.add(new PathPoint(cx + 60, cy, 0));
        paths.add(path);
        addPathRow(path);
        selectPointOf(path, 0);
    }

    void insertPoint(boolean before) {
        if (selectedPath == null || selectedPoint < 0) return;
        List<PathPoint> pts = selectedPath.points;
        int n = selectedPoint;
        PathPoint np;
        int insertAt;
        if (before) {
            if (n > 0) {
                PathPoint a = pts.get(n-1), b = pts.get(n);
                np = new PathPoint((a.x+b.x)/2, (a.y+b.y)/2, (a.z+b.z)/2);
            } else {
                PathPoint a = pts.get(n);
                np = new PathPoint(a.x - 30, a.y, a.z);
            }
            insertAt = n;
        } else {
            if (n < pts.size()-1) {
                PathPoint a = pts.get(n), b = pts.get(n+1);
                np = new PathPoint((a.x+b.x)/2, (a.y+b.y)/2, (a.z+b.z)/2);
            } else {
                PathPoint a = pts.get(n);
                np = new PathPoint(a.x + 30, a.y, a.z);
            }
            insertAt = n + 1;
        }
        pts.add(insertAt, np);
        refreshCombo(selectedPath);
        selectPointOf(selectedPath, insertAt);
    }

    void removeSelectedPoint() {
        if (selectedPath == null || selectedPoint < 0) return;
        if (selectedPath.isBranch() && selectedPoint == 0) return;
        List<PathPoint> pts = selectedPath.points;
        if (pts.size() <= 2) return;
        pts.remove(selectedPoint);
        refreshCombo(selectedPath);
        selectPointOf(selectedPath, Math.min(selectedPoint, pts.size()-1));
    }

    void deleteSelectedPath() {
        if (selectedPath == null) return;
        PathModel toDelete = selectedPath;
        // Remove child branches that depend on this path
        paths.removeIf(p -> p.parentPath == toDelete);
        paths.remove(toDelete);
        restPoses.remove(toDelete);
        weightMaps.remove(toDelete);
        animation.tracks.removeIf(t -> t.path == toDelete);
        selectedPath  = null;
        selectedPoint = -1;
        rebuildPathList();
        syncCoordsFromModel();
        timelinePanel.repaint();
        canvas.repaint();
    }

    void rebuildPathList() {
        pathListPanel.removeAll();
        for (PathModel pm : paths) addPathRow(pm);
        pathListPanel.revalidate();
        pathListPanel.repaint();
    }

    // ── Sprite / clipboard / drop ────────────────────────────────────────────

    void pasteFromClipboard() {
        Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
        try {
            if (cb.isDataFlavorAvailable(DataFlavor.imageFlavor)) {
                applySpriteImage(toBufferedImage((Image) cb.getData(DataFlavor.imageFlavor)));
                return;
            }
            if (cb.isDataFlavorAvailable(DataFlavor.javaFileListFlavor)) {
                @SuppressWarnings("unchecked")
                List<File> files = (List<File>) cb.getData(DataFlavor.javaFileListFlavor);
                if (!files.isEmpty()) {
                    BufferedImage img = ImageIO.read(files.get(0));
                    if (img != null) applySpriteImage(img);
                }
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Einfügen fehlgeschlagen: " + ex.getMessage());
        }
    }

    void installDropTarget() {
        new DropTarget(canvas, DnDConstants.ACTION_COPY, new DropTargetAdapter() {
            @Override public void dragOver(DropTargetDragEvent e) {
                if (acceptable(e.getCurrentDataFlavors())) e.acceptDrag(DnDConstants.ACTION_COPY);
                else e.rejectDrag();
            }
            @Override public void drop(DropTargetDropEvent e) {
                e.acceptDrop(DnDConstants.ACTION_COPY);
                try {
                    Transferable t = e.getTransferable();
                    if (t.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                        applySpriteImage(toBufferedImage((Image) t.getTransferData(DataFlavor.imageFlavor)));
                    } else if (t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        @SuppressWarnings("unchecked")
                        List<File> files = (List<File>) t.getTransferData(DataFlavor.javaFileListFlavor);
                        for (File f : files) {
                            BufferedImage img = ImageIO.read(f);
                            if (img != null) { applySpriteImage(img); break; }
                        }
                    }
                    e.dropComplete(true);
                } catch (Exception ex) { e.dropComplete(false); }
            }
            private boolean acceptable(DataFlavor[] fs) {
                for (DataFlavor f : fs)
                    if (f.equals(DataFlavor.imageFlavor) || f.equals(DataFlavor.javaFileListFlavor)) return true;
                return false;
            }
        }, true);
    }

    void applySpriteImage(BufferedImage img) {
        spriteImage = img;
        spriteX = (canvas.getWidth()  - img.getWidth())  / 2.0;
        spriteY = (canvas.getHeight() - img.getHeight()) / 2.0;
        restPoses.clear();
        weightMaps.clear();
        updateBindLabel();
        canvas.repaint();
    }

    static BufferedImage toBufferedImage(Image img) {
        if (img instanceof BufferedImage) return (BufferedImage) img;
        BufferedImage bi = new BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = bi.createGraphics();
        g.drawImage(img, 0, 0, null);
        g.dispose();
        return bi;
    }

    void loadSprite() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Sprite-Bild laden");
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "Bilder (PNG, JPG, GIF, BMP)", "png", "jpg", "jpeg", "gif", "bmp"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            BufferedImage img = ImageIO.read(fc.getSelectedFile());
            if (img != null) applySpriteImage(img);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Fehler beim Laden: " + ex.getMessage());
        }
    }

    void bindSkeleton() {
        if (spriteImage == null)  { JOptionPane.showMessageDialog(this, "Zuerst ein Sprite laden."); return; }
        if (selectedPath == null) { JOptionPane.showMessageDialog(this, "Zuerst einen Pfad auswählen."); return; }
        if (!restPoses.containsKey(selectedPath)) {
            List<PathPoint> rest = new ArrayList<>();
            for (PathPoint p : selectedPath.points) rest.add(p.copy());
            restPoses.put(selectedPath, rest);
            getOrCreateWeightMap(selectedPath);
        }
        updateBindLabel();
        canvas.repaint();
    }

    void unbind() {
        if (selectedPath != null) {
            restPoses.remove(selectedPath);
            weightMaps.remove(selectedPath);
        } else {
            restPoses.clear();
            weightMaps.clear();
        }
        updateBindLabel();
        canvas.repaint();
    }

    void updateBindLabel() {
        if (spriteImage == null) {
            bindLabel.setText("<html><small><i>kein Sprite</i></small></html>");
        } else if (restPoses.isEmpty()) {
            bindLabel.setText("<html><small>Sprite geladen – kein Skelett gebunden</small></html>");
        } else {
            StringBuilder sb = new StringBuilder("<html><small>");
            for (PathModel pm : restPoses.keySet())
                sb.append("● ").append(pm.name).append("<br>");
            sb.append("</small></html>");
            bindLabel.setText(sb.toString());
        }
    }

    void togglePaintMode() {
        paintMode = !paintMode;
        paintModeBtn.setBackground(paintMode ? new Color(180, 80, 60) : null);
        paintModeBtn.setForeground(paintMode ? Color.WHITE : null);
        canvas.setCursor(paintMode
            ? Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)
            : Cursor.getDefaultCursor());
        canvas.repaint();
    }

    float[] getOrCreateWeightMap(PathModel path) {
        return weightMaps.computeIfAbsent(path, p -> {
            int size = spriteImage.getWidth() * spriteImage.getHeight();
            float[] wm = new float[size];
            java.util.Arrays.fill(wm, 1.0f);
            return wm;
        });
    }

    void paintWeight(int canvasX, int canvasY, boolean erase) {
        if (spriteImage == null || selectedPath == null) return;
        if (!restPoses.containsKey(selectedPath)) return;
        float[] wm = getOrCreateWeightMap(selectedPath);
        int imgW = spriteImage.getWidth(), imgH = spriteImage.getHeight();
        int cx = (int)(canvasX - spriteX), cy = (int)(canvasY - spriteY);
        for (int dy = -brushRadius; dy <= brushRadius; dy++) {
            for (int dx = -brushRadius; dx <= brushRadius; dx++) {
                int px = cx + dx, py = cy + dy;
                if (px < 0 || py < 0 || px >= imgW || py >= imgH) continue;
                double dist = Math.sqrt(dx*dx + dy*dy);
                if (dist > brushRadius) continue;
                float falloff = (float)(1.0 - dist / brushRadius);
                int idx = py * imgW + px;
                if (erase) wm[idx] = Math.max(0f, wm[idx] - brushOpacity * falloff);
                else        wm[idx] = Math.min(1f, wm[idx] + brushOpacity * falloff);
            }
        }
        canvas.repaint();
    }

    void drawHeatmapOverlay(Graphics2D g2) {
        if (!paintMode || spriteImage == null || selectedPath == null) return;
        float[] wm = weightMaps.get(selectedPath);
        if (wm == null) return;
        int imgW = spriteImage.getWidth(), imgH = spriteImage.getHeight();
        int[] pixels = new int[imgW * imgH];
        for (int i = 0; i < pixels.length; i++) {
            float w = wm[i];
            int r = (int)(Math.min(1f, w * 2f)         * 220);
            int gv= (int)(Math.max(0f, 1f - Math.abs(w - 0.5f) * 2f) * 220);
            int b = (int)(Math.max(0f, (1f - w) * 2f)  * 220);
            pixels[i] = (170 << 24) | (r << 16) | (gv << 8) | b;
        }
        BufferedImage overlay = new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_ARGB);
        overlay.setRGB(0, 0, imgW, imgH, pixels, 0, imgW);
        g2.drawImage(overlay, (int)spriteX, (int)spriteY, null);
    }

    // ── Selection ────────────────────────────────────────────────────────────

    void selectPointOf(PathModel path, int idx) {
        selectedPath = path; selectedPoint = idx;
        for (Component c : pathListPanel.getComponents()) {
            if (!(c instanceof JComboBox)) continue;
            @SuppressWarnings("unchecked") JComboBox<String> cb = (JComboBox<String>) c;
            if (cb.getClientProperty("path") != path) continue;
            updating = true;
            cb.setSelectedIndex(idx + 1);
            updating = false;
        }
        syncCoordsFromModel();
        canvas.repaint();
    }

    // ── Coord field ↔ model ──────────────────────────────────────────────────

    void syncCoordsFromModel() {
        if (selectedPath == null || selectedPoint < 0 || selectedPoint >= selectedPath.points.size()) {
            updating = true;
            xField.setText(""); yField.setText(""); zField.setText("");
            updating = false;
            return;
        }
        PathPoint p = selectedPath.points.get(selectedPoint);
        updating = true;
        xField.setText(String.format("%.2f", p.x));
        yField.setText(String.format("%.2f", p.y));
        zField.setText(String.format("%.2f", p.z));
        updating = false;
    }

    void onCoordTyped() {
        if (updating) return;
        if (selectedPath == null || selectedPoint < 0 || selectedPoint >= selectedPath.points.size()) return;
        try {
            double x = Double.parseDouble(xField.getText().trim());
            double y = Double.parseDouble(yField.getText().trim());
            double z = Double.parseDouble(zField.getText().trim());
            PathPoint p = selectedPath.points.get(selectedPoint);
            p.x = x; p.y = y; p.z = z;
            refreshCombo(selectedPath);
            canvas.repaint();
        } catch (NumberFormatException ignored) {}
    }

    // ── Path list (ComboBox rows) ────────────────────────────────────────────

    void addPathRow(PathModel path) {
        JComboBox<String> cb = new JComboBox<>();
        cb.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        cb.putClientProperty("path", path);
        fillCombo(cb, path);
        cb.addActionListener(e -> {
            if (updating) return;
            int sel = cb.getSelectedIndex();
            if (sel <= 0) return;
            int ptIdx = sel - 1;
            if (ptIdx < path.points.size()) selectPointOf(path, ptIdx);
        });
        pathListPanel.add(cb);
        pathListPanel.add(Box.createVerticalStrut(4));
        pathListPanel.revalidate();
        pathListPanel.repaint();
    }

    void fillCombo(JComboBox<String> cb, PathModel path) {
        updating = true;
        cb.removeAllItems();
        String branchInfo = path.isBranch()
                ? "  \u21A9 " + path.parentPath.name + "[" + path.parentPointIdx + "]" : "";
        AnimTrack track = animation.trackFor(path);
        String kfInfo = (track != null && track.hasKeyframeAt((int) currentFrame)) ? " ◆" : "";
        cb.addItem("\u25B6 " + path.name + "  (" + path.points.size() + " Punkte)"
                + (restPoses.containsKey(path) ? " [gebunden]" : "") + branchInfo + kfInfo);
        for (int i = 0; i < path.points.size(); i++)
            cb.addItem("   " + i + ": " + path.points.get(i));
        updating = false;
    }

    void refreshCombo(PathModel path) {
        for (Component c : pathListPanel.getComponents()) {
            if (!(c instanceof JComboBox)) continue;
            @SuppressWarnings("unchecked") JComboBox<String> cb = (JComboBox<String>) c;
            if (cb.getClientProperty("path") != path) continue;
            int sel = cb.getSelectedIndex();
            fillCombo(cb, path);
            updating = true;
            cb.setSelectedIndex(Math.min(sel, cb.getItemCount()-1));
            updating = false;
        }
    }

    // ── Sprite deformation rendering ─────────────────────────────────────────

    void drawDeformedSprite(Graphics2D g2) {
        if (spriteImage == null) return;
        int imgW = spriteImage.getWidth(), imgH = spriteImage.getHeight();

        if (restPoses.isEmpty()) {
            g2.drawImage(spriteImage, (int) spriteX, (int) spriteY, null);
            return;
        }

        int C = MESH_COLS, R = MESH_ROWS;
        double[][] gx = new double[R+1][C+1], gy = new double[R+1][C+1];
        for (int r = 0; r <= R; r++) {
            for (int c = 0; c <= C; c++) {
                double rx = spriteX + (double)c * imgW / C;
                double ry = spriteY + (double)r * imgH / R;
                double totalW = 0, dx = 0, dy = 0;
                for (Map.Entry<PathModel, List<PathPoint>> entry : restPoses.entrySet()) {
                    PathModel bp   = entry.getKey();
                    List<PathPoint> rest = entry.getValue();
                    float[] wm = weightMaps.get(bp);
                    float pathWeight = 1f;
                    if (wm != null) {
                        int ix = (int)(rx - spriteX), iy = (int)(ry - spriteY);
                        if (ix >= 0 && iy >= 0 && ix < imgW && iy < imgH)
                            pathWeight = wm[iy * imgW + ix];
                    }
                    int n = Math.min(rest.size(), bp.points.size());
                    for (int i = 0; i < n; i++) {
                        PathPoint restPt = rest.get(i), currPt = bp.points.get(i);
                        double ex = rx - restPt.x, ey = ry - restPt.y;
                        double w = pathWeight / (ex*ex + ey*ey + 200.0);
                        totalW += w;
                        dx += w * (currPt.x - restPt.x);
                        dy += w * (currPt.y - restPt.y);
                    }
                }
                gx[r][c] = totalW > 0 ? rx + dx / totalW : rx;
                gy[r][c] = totalW > 0 ? ry + dy / totalW : ry;
            }
        }

        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        for (int r = 0; r < R; r++) {
            for (int c = 0; c < C; c++) {
                double u0 = (double)c    *imgW/C, v0 = (double)r    *imgH/R;
                double u1 = (double)(c+1)*imgW/C, v1 = (double)(r+1)*imgH/R;
                drawTexturedTri(g2, gx[r][c],   gy[r][c],   gx[r][c+1],   gy[r][c+1],   gx[r+1][c],   gy[r+1][c],   u0,v0, u1,v0, u0,v1);
                drawTexturedTri(g2, gx[r][c+1], gy[r][c+1], gx[r+1][c+1], gy[r+1][c+1], gx[r+1][c],   gy[r+1][c],   u1,v0, u1,v1, u0,v1);
            }
        }
    }

    void drawTexturedTri(Graphics2D g2,
        double x1, double y1, double x2, double y2, double x3, double y3,
        double u1, double v1, double u2, double v2, double u3, double v3) {

        double det = u1*(v2-v3) - v1*(u2-u3) + (u2*v3 - u3*v2);
        if (Math.abs(det) < 1e-10) return;

        double m00 = (x1*(v2-v3) - v1*(x2-x3) + (x2*v3-x3*v2)) / det;
        double m01 = (u1*(x2-x3) - x1*(u2-u3) + (u2*x3-u3*x2)) / det;
        double m02 = (u1*(v2*x3-v3*x2) - v1*(u2*x3-u3*x2) + x1*(u2*v3-u3*v2)) / det;
        double m10 = (y1*(v2-v3) - v1*(y2-y3) + (y2*v3-y3*v2)) / det;
        double m11 = (u1*(y2-y3) - y1*(u2-u3) + (u2*y3-u3*y2)) / det;
        double m12 = (u1*(v2*y3-v3*y2) - v1*(u2*y3-u3*y2) + y1*(u2*v3-u3*v2)) / det;

        AffineTransform at = new AffineTransform(m00, m10, m01, m11, m02, m12);
        Path2D.Double tri = new Path2D.Double();
        tri.moveTo(x1,y1); tri.lineTo(x2,y2); tri.lineTo(x3,y3); tri.closePath();
        Graphics2D g = (Graphics2D) g2.create();
        g.clip(tri);
        g.drawImage(spriteImage, at, null);
        g.dispose();
    }

    // ── Timeline panel ────────────────────────────────────────────────────────

    class TimelinePanel extends JPanel {
        static final int LEFT   = 110;   // label column width
        static final int RULER_H = 22;   // ruler height
        static final int TRACK_H = 18;   // per-track height
        static final int KF      = 5;    // keyframe diamond half-size

        double pxPerFrame = 5.0;         // zoom
        boolean dragging  = false;

        TimelinePanel() {
            setPreferredSize(new Dimension(0, 130));
            setBackground(new Color(28, 28, 36));

            MouseAdapter ma = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    if (e.getX() >= LEFT) { dragging = true; scrub(e.getX()); }
                }
                @Override public void mouseDragged(MouseEvent e) {
                    if (dragging) scrub(e.getX());
                }
                @Override public void mouseReleased(MouseEvent e) { dragging = false; }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);

            addMouseWheelListener(e -> {
                pxPerFrame = Math.max(1.5, Math.min(30, pxPerFrame - e.getPreciseWheelRotation()));
                repaint();
            });
        }

        void scrub(int mouseX) {
            int f = (int) Math.round((mouseX - LEFT) / pxPerFrame);
            seekToFrame(f);
        }

        int px(double frame) { return (int)(LEFT + frame * pxPerFrame); }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();

            // ── Ruler ────────────────────────────────────────────────────────
            g2.setColor(new Color(42, 42, 54));
            g2.fillRect(LEFT, 0, w, RULER_H);

            g2.setFont(g2.getFont().deriveFont(9f));
            int step = pxPerFrame < 3 ? 10 : pxPerFrame < 8 ? 5 : 1;
            for (int f = 0; f <= animation.totalFrames; f += step) {
                int px = px(f);
                if (px > w) break;
                g2.setColor(new Color(100, 100, 120));
                g2.drawLine(px, RULER_H - 5, px, RULER_H);
                if (f % (step * 5) == 0 || step == 1) {
                    g2.setColor(new Color(160, 160, 180));
                    g2.drawString(String.valueOf(f), px + 2, RULER_H - 6);
                }
            }

            // ── Tracks ───────────────────────────────────────────────────────
            int ty = RULER_H;
            for (int ti = 0; ti < animation.tracks.size(); ti++) {
                AnimTrack track = animation.tracks.get(ti);

                // label
                g2.setColor(new Color(50, 50, 62));
                g2.fillRect(0, ty, LEFT, TRACK_H);
                g2.setColor(track.path == selectedPath ? new Color(255, 220, 80) : new Color(180, 180, 200));
                g2.setFont(g2.getFont().deriveFont(10f));
                String lbl = track.path.name;
                if (lbl.length() > 13) lbl = lbl.substring(0, 11) + "..";
                g2.drawString(lbl, 4, ty + TRACK_H - 4);

                // track bg
                g2.setColor(ti % 2 == 0 ? new Color(36, 36, 46) : new Color(32, 32, 42));
                g2.fillRect(LEFT, ty, w, TRACK_H);

                // bar connecting first to last keyframe
                if (track.keyframes.size() >= 2) {
                    int x0 = px(track.keyframes.get(0).frame);
                    int x1 = px(track.keyframes.get(track.keyframes.size()-1).frame);
                    int cy = ty + TRACK_H/2;
                    g2.setColor(new Color(80, 80, 120));
                    g2.fillRect(x0, cy - 2, x1 - x0, 4);
                }

                // keyframe diamonds
                for (Keyframe kf : track.keyframes) {
                    int px = px(kf.frame), cy = ty + TRACK_H/2;
                    boolean atCurrent = kf.frame == (int) currentFrame;
                    int[] xs = {px, px+KF, px, px-KF};
                    int[] ys = {cy-KF, cy, cy+KF, cy};
                    g2.setColor(atCurrent ? new Color(255, 120, 60) : new Color(255, 200, 50));
                    g2.fillPolygon(xs, ys, 4);
                    g2.setColor(Color.WHITE);
                    g2.setStroke(new BasicStroke(1f));
                    g2.drawPolygon(xs, ys, 4);
                }

                ty += TRACK_H;
            }

            // ── End-of-animation marker ───────────────────────────────────────
            int endPx = px(animation.totalFrames);
            if (endPx < w) {
                g2.setColor(new Color(200, 80, 80, 180));
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawLine(endPx, 0, endPx, h);
            }

            // ── Playhead ─────────────────────────────────────────────────────
            int phx = px(currentFrame);
            g2.setColor(new Color(255, 70, 70));
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawLine(phx, 0, phx, h);
            g2.fillPolygon(new int[]{phx-5, phx+5, phx}, new int[]{0, 0, 9}, 3);
        }
    }

    // ── Canvas ───────────────────────────────────────────────────────────────

    class CanvasPanel extends JPanel {
        static final int R = 7;
        PathModel dragPath     = null;
        int       dragIdx      = -1;
        boolean   rightDrag    = false;
        PathPoint ghost        = null;
        boolean   spriteDrag   = false;
        double    prevDragX, prevDragY;
        double    zoom = 1.0, viewOffX = 0, viewOffY = 0;

        double toWorldX(double sx) { return (sx - viewOffX) / zoom; }
        double toWorldY(double sy) { return (sy - viewOffY) / zoom; }

        CanvasPanel() {
            setBackground(new Color(40, 40, 48));
            MouseAdapter ma = new MouseAdapter() {

                @Override public void mousePressed(MouseEvent e) {
                    if (SwingUtilities.isMiddleMouseButton(e)) {
                        spriteDrag = true;
                        prevDragX = e.getX(); prevDragY = e.getY(); return;
                    }
                    if (paintMode) {
                        paintWeight((int)toWorldX(e.getX()), (int)toWorldY(e.getY()),
                                    SwingUtilities.isRightMouseButton(e));
                        return;
                    }
                    if (playing) pause();

                    PathPoint hit = null;
                    outer:
                    for (PathModel pm : paths) {
                        for (int i = 0; i < pm.points.size(); i++) {
                            if (dist(e.getPoint(), pm.points.get(i)) <= R + 4) {
                                dragPath = pm; dragIdx = i; hit = pm.points.get(i);
                                break outer;
                            }
                        }
                    }
                    if (hit == null) { dragPath = null; dragIdx = -1; return; }
                    if (SwingUtilities.isRightMouseButton(e)) {
                        rightDrag = true;
                        ghost = new PathPoint(hit.x, hit.y, hit.z);
                    } else {
                        rightDrag = false;
                        selectPointOf(dragPath, dragIdx);
                    }
                }

                @Override public void mouseMoved(MouseEvent e) {
                    if (paintMode) {
                        paintCursorX = e.getX(); paintCursorY = e.getY();
                        repaint();
                    }
                }

                @Override public void mouseDragged(MouseEvent e) {
                    if (paintMode) {
                        paintCursorX = e.getX(); paintCursorY = e.getY();
                        paintWeight((int)toWorldX(e.getX()), (int)toWorldY(e.getY()),
                                    SwingUtilities.isRightMouseButton(e));
                        return;
                    }
                    if (spriteDrag) {
                        double dx = (e.getX() - prevDragX) / zoom;
                        double dy = (e.getY() - prevDragY) / zoom;
                        spriteX += dx; spriteY += dy;
                        for (List<PathPoint> rest : restPoses.values())
                            for (PathPoint p : rest) { p.x += dx; p.y += dy; }
                        prevDragX = e.getX(); prevDragY = e.getY();
                        repaint(); return;
                    }
                    if (dragPath == null) return;
                    if (rightDrag) {
                        ghost.x = toWorldX(e.getX()); ghost.y = toWorldY(e.getY()); repaint();
                    } else {
                        PathPoint p = dragPath.points.get(dragIdx);
                        p.x = toWorldX(e.getX()); p.y = toWorldY(e.getY());
                        if (dragPath == selectedPath && dragIdx == selectedPoint) syncCoordsFromModel();
                        refreshCombo(dragPath);
                        repaint();
                    }
                }

                @Override public void mouseReleased(MouseEvent e) {
                    spriteDrag = false;
                    if (paintMode) return;
                    if (rightDrag && dragPath != null && ghost != null) {
                        PathPoint sharedRoot = dragPath.points.get(dragIdx);
                        PathModel branch = new PathModel("Pfad " + pathCounter++);
                        branch.parentPath = dragPath; branch.parentPointIdx = dragIdx;
                        branch.points.add(sharedRoot);
                        branch.points.add(ghost);
                        paths.add(branch);
                        addPathRow(branch);
                        selectPointOf(branch, 1);
                        ghost = null;
                    }
                    rightDrag = false;
                }

                @Override public void mouseExited(MouseEvent e) {
                    paintCursorX = -1; paintCursorY = -1;
                    if (paintMode) repaint();
                }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);

            addMouseWheelListener(e -> {
                if (e.isControlDown()) {
                    double factor = e.getPreciseWheelRotation() > 0 ? 1.0 / 1.12 : 1.12;
                    double mx = e.getX(), my = e.getY();
                    viewOffX = mx + (viewOffX - mx) * factor;
                    viewOffY = my + (viewOffY - my) * factor;
                    zoom = Math.max(0.05, Math.min(20.0, zoom * factor));
                    repaint();
                } else if (paintMode) {
                    brushRadius = Math.max(2, Math.min(200, brushRadius - (int) e.getPreciseWheelRotation() * 2));
                    repaint();
                }
            });
        }

        double dist(Point e, PathPoint p) {
            double dx = toWorldX(e.x) - p.x, dy = toWorldY(e.y) - p.y;
            return Math.sqrt(dx*dx + dy*dy);
        }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // apply view transform
            java.awt.geom.AffineTransform savedTx = g2.getTransform();
            g2.translate(viewOffX, viewOffY);
            g2.scale(zoom, zoom);

            drawDeformedSprite(g2);
            drawHeatmapOverlay(g2);

            // rest-pose overlay
            for (List<PathPoint> restPose : restPoses.values()) {
                if (restPose.size() < 2) continue;
                g2.setColor(new Color(180, 180, 80, 100));
                g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{4f,4f}, 0f));
                for (int i = 0; i < restPose.size()-1; i++) {
                    PathPoint a = restPose.get(i), b = restPose.get(i+1);
                    g2.drawLine((int)a.x,(int)a.y,(int)b.x,(int)b.y);
                }
                g2.setStroke(new BasicStroke(1f));
                for (PathPoint p : restPose) {
                    g2.setColor(new Color(180,180,80,120));
                    g2.fillOval((int)p.x-3,(int)p.y-3,6,6);
                }
            }

            // skeleton paths
            for (PathModel pm : paths) {
                List<PathPoint> pts = pm.points;
                Color edgeColor = pm.isBranch() ? new Color(230,150,60) : new Color(160,160,200);
                g2.setColor(edgeColor);
                g2.setStroke(new BasicStroke(1.5f));
                for (int i = 0; i < pts.size()-1; i++) {
                    PathPoint a = pts.get(i), b = pts.get(i+1);
                    g2.drawLine((int)a.x,(int)a.y,(int)b.x,(int)b.y);
                }
                for (int i = 0; i < pts.size(); i++) {
                    PathPoint p = pts.get(i);
                    boolean sel    = (pm == selectedPath && i == selectedPoint);
                    boolean isRoot = pm.isBranch() && i == 0;

                    // keyframe indicator ring
                    AnimTrack trk = animation.trackFor(pm);
                    if (trk != null && trk.hasKeyframeAt((int) currentFrame)) {
                        g2.setColor(new Color(255, 160, 40, 180));
                        g2.setStroke(new BasicStroke(2f));
                        g2.drawOval((int)p.x-R-3,(int)p.y-R-3,(R+3)*2,(R+3)*2);
                    }

                    if (isRoot) {
                        int[] xs = {(int)p.x, (int)p.x+R, (int)p.x, (int)p.x-R};
                        int[] ys = {(int)p.y-R, (int)p.y, (int)p.y+R, (int)p.y};
                        g2.setColor(sel ? new Color(255,220,50) : new Color(230,150,60));
                        g2.fillPolygon(xs,ys,4);
                        g2.setColor(Color.WHITE);
                        g2.setStroke(new BasicStroke(sel?2f:1f));
                        g2.drawPolygon(xs,ys,4);
                    } else {
                        g2.setColor(sel ? new Color(255,220,50) : new Color(80,180,255));
                        g2.fillOval((int)p.x-R,(int)p.y-R,R*2,R*2);
                        g2.setColor(Color.WHITE);
                        g2.setStroke(new BasicStroke(sel?2f:1f));
                        g2.drawOval((int)p.x-R,(int)p.y-R,R*2,R*2);
                    }
                    g2.setColor(Color.WHITE);
                    g2.setFont(g2.getFont().deriveFont(9f));
                    g2.drawString(String.valueOf(i),(int)p.x-3,(int)p.y+3);
                }
            }

            // right-drag ghost
            if (rightDrag && ghost != null && dragPath != null) {
                PathPoint origin = dragPath.points.get(dragIdx);
                g2.setColor(new Color(80,230,120));
                g2.setStroke(new BasicStroke(1.2f,BasicStroke.CAP_BUTT,BasicStroke.JOIN_MITER,10f,new float[]{6f,4f},0f));
                g2.drawLine((int)origin.x,(int)origin.y,(int)ghost.x,(int)ghost.y);
                g2.setStroke(new BasicStroke(1f));
                g2.fillOval((int)ghost.x-R,(int)ghost.y-R,R*2,R*2);
            }

            // restore screen-space transform
            g2.setTransform(savedTx);

            if (paths.isEmpty() && spriteImage == null) {
                g2.setColor(new Color(120,120,130));
                g2.setFont(g2.getFont().deriveFont(14f));
                String msg = "ALT+N → Pfad anlegen   |   'Sprite laden' → Bild wählen";
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(msg,(getWidth()-fm.stringWidth(msg))/2,getHeight()/2);
            }

            // brush cursor in screen space
            if (paintMode && paintCursorX >= 0) {
                g2.setColor(new Color(255, 255, 255, 180));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawOval(paintCursorX - brushRadius, paintCursorY - brushRadius,
                            brushRadius * 2, brushRadius * 2);
            }

            // playing indicator + zoom level
            if (playing) {
                g2.setColor(new Color(255, 80, 80, 200));
                g2.setFont(g2.getFont().deriveFont(Font.BOLD, 12f));
                g2.drawString("▶ " + (int)currentFrame, 8, 18);
            }
            if (zoom != 1.0) {
                g2.setColor(new Color(200, 200, 200, 180));
                g2.setFont(g2.getFont().deriveFont(10f));
                g2.drawString(String.format("%.0f%%", zoom * 100), getWidth() - 42, 14);
            }
        }
    }

    // ── Entry point ──────────────────────────────────────────────────────────

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
        catch (Exception ignored) {}
        SwingUtilities.invokeLater(PathEditor::new);
    }
}
