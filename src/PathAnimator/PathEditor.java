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

    static class ComboItem {
        enum Kind { HEADER, POINT, EDGE }
        final Kind   kind;
        final int    idx;
        final String label;
        ComboItem(Kind k, int i, String l) { kind = k; idx = i; label = l; }
        @Override public String toString() { return label; }
    }

    // ── Sprite layer ────────────────────────────────────────────────────────

    static class SpriteLayer {
        BufferedImage image;
        double        x, y;
        boolean       visible = true;
        String        name;
        Map<PathModel, List<PathPoint>> restPoses  = new LinkedHashMap<>();
        Map<PathModel, float[]>         weightMaps = new LinkedHashMap<>();
        Map<PathModel, byte[]>          rigidMaps  = new LinkedHashMap<>();

        SpriteLayer(String name, BufferedImage img, double x, double y) {
            this.name = name; this.image = img; this.x = x; this.y = y;
        }
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
    PathModel selectedEdgePath = null;
    int       selectedEdgeIdx  = -1;
    int       pathCounter   = 1;
    boolean   updating      = false;

    // ── Edge / keep-length ──────────────────────────────────────────────────

    boolean        showEdgesInList  = false;
    boolean        keepLength       = false;
    double         dragOrigLength   = 0;
    JCheckBox      keepLengthBox;

    // ── Heatmap paint modes ─────────────────────────────────────────────────

    boolean        pixelBrush  = false;
    boolean        binaryMode  = false;
    boolean        paintRigid  = false;

    // ── Layer system ────────────────────────────────────────────────────────

    List<SpriteLayer> layers      = new ArrayList<>();
    SpriteLayer       activeLayer = null;
    int               layerCounter = 1;
    JPanel            layerListPanel;
    JLabel            bindLabel;

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
        setSize(1300, 820);
        setLayout(new BorderLayout());

        // ── Left panel ──────────────────────────────────────────────────────
        JPanel left = new JPanel(new BorderLayout(4, 4));
        left.setPreferredSize(new Dimension(270, 0));
        left.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        JPanel pathBtns = new JPanel(new GridLayout(2, 2, 4, 2));
        JButton newBtn = new JButton("Neuer Pfad  (ALT+N)");
        JButton delPathBtn = new JButton("Pfad löschen  (ALT+DEL)");
        JToggleButton showEdgesBtn = new JToggleButton("Kanten in Liste");
        newBtn      .addActionListener(e -> createNewPath());
        delPathBtn  .addActionListener(e -> deleteSelectedPath());
        showEdgesBtn.addActionListener(e -> {
            showEdgesInList = showEdgesBtn.isSelected();
            for (PathModel pm : paths) refreshCombo(pm);
        });
        pathBtns.add(newBtn);
        pathBtns.add(delPathBtn);
        pathBtns.add(showEdgesBtn);
        pathBtns.add(new JLabel()); // placeholder
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
        JButton loadSpriteBtn = new JButton("Layer hinzufügen…");
        JButton bindBtn       = new JButton("Skelett binden");
        JButton unbindBtn     = new JButton("Bindung lösen");
        paintModeBtn          = new JButton("🎨 Heatmap malen");
        bindLabel = new JLabel("<html><small><i>kein Layer aktiv</i></small></html>");
        loadSpriteBtn.addActionListener(e -> loadSprite());
        bindBtn      .addActionListener(e -> bindSkeleton());
        unbindBtn    .addActionListener(e -> unbind());
        paintModeBtn .addActionListener(e -> togglePaintMode());
        JCheckBox     pixelBrushBox  = new JCheckBox("Pixel-Pinsel");
        JCheckBox     binaryModeBox  = new JCheckBox("Ja/Nein-Modus");
        JToggleButton paintRigidBtn  = new JToggleButton("Starr malen");
        pixelBrushBox.addActionListener(e -> pixelBrush = pixelBrushBox.isSelected());
        binaryModeBox.addActionListener(e -> { binaryMode = binaryModeBox.isSelected(); canvas.repaint(); });
        paintRigidBtn.addActionListener(e -> {
            paintRigid = paintRigidBtn.isSelected();
            paintRigidBtn.setBackground(paintRigid ? new Color(255, 160, 40) : null);
            paintRigidBtn.setForeground(paintRigid ? Color.BLACK : null);
        });
        spritePanel.add(loadSpriteBtn);
        spritePanel.add(bindBtn);
        spritePanel.add(unbindBtn);
        spritePanel.add(paintModeBtn);
        spritePanel.add(pixelBrushBox);
        spritePanel.add(binaryModeBox);
        spritePanel.add(paintRigidBtn);
        spritePanel.add(bindLabel);

        JButton removeBtn = new JButton("Punkt entfernen  (DEL)");
        removeBtn.addActionListener(e -> removeSelectedPoint());
        keepLengthBox = new JCheckBox("Keep Length");
        keepLengthBox.addActionListener(e -> keepLength = keepLengthBox.isSelected());
        JPanel hints = new JPanel(new GridLayout(5, 1));
        hints.add(removeBtn);
        hints.add(keepLengthBox);
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

        // ── Layer panel (EAST) ───────────────────────────────────────────────
        JPanel eastPanel = buildLayerPanel();

        // ── Timeline / animation controls ────────────────────────────────────
        JPanel animControls = buildAnimControls();
        timelinePanel = new TimelinePanel();
        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.add(animControls,  BorderLayout.NORTH);
        southPanel.add(timelinePanel, BorderLayout.CENTER);

        add(left,       BorderLayout.WEST);
        add(canvas,     BorderLayout.CENTER);
        add(eastPanel,  BorderLayout.EAST);
        add(southPanel, BorderLayout.SOUTH);

        buildPlayTimer();
        bindKeys();
        setLocationRelativeTo(null);
        setVisible(true);
        installDropTarget();
    }

    // ── Layer panel ──────────────────────────────────────────────────────────

    JPanel buildLayerPanel() {
        JPanel outer = new JPanel(new BorderLayout(4, 4));
        outer.setPreferredSize(new Dimension(170, 0));
        outer.setBorder(BorderFactory.createEmptyBorder(6, 4, 6, 6));

        JLabel title = new JLabel("Layer");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 12f));

        JButton addBtn = new JButton("+  Layer laden");
        addBtn.addActionListener(e -> loadSprite());

        layerListPanel = new JPanel();
        layerListPanel.setLayout(new BoxLayout(layerListPanel, BoxLayout.Y_AXIS));
        JScrollPane sp = new JScrollPane(layerListPanel);
        sp.setBorder(null);

        outer.add(title,  BorderLayout.NORTH);
        outer.add(sp,     BorderLayout.CENTER);
        outer.add(addBtn, BorderLayout.SOUTH);
        return outer;
    }

    void rebuildLayerList() {
        layerListPanel.removeAll();
        for (SpriteLayer layer : layers) {
            JPanel row = new JPanel(new BorderLayout(2, 0));
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
            row.setBorder(BorderFactory.createEmptyBorder(1, 2, 1, 2));

            JCheckBox vis = new JCheckBox();
            vis.setSelected(layer.visible);
            vis.setPreferredSize(new Dimension(20, 20));
            vis.addActionListener(e -> { layer.visible = vis.isSelected(); canvas.repaint(); });

            JButton nameBtn = new JButton(layer.name);
            nameBtn.setHorizontalAlignment(SwingConstants.LEFT);
            nameBtn.setFont(nameBtn.getFont().deriveFont(10f));
            nameBtn.setBorderPainted(layer == activeLayer);
            nameBtn.setBackground(layer == activeLayer ? new Color(60, 100, 160) : null);
            nameBtn.setForeground(layer == activeLayer ? Color.WHITE : null);
            nameBtn.addActionListener(e -> setActiveLayer(layer));

            JButton delBtn = new JButton("✕");
            delBtn.setPreferredSize(new Dimension(22, 20));
            delBtn.setFont(delBtn.getFont().deriveFont(9f));
            delBtn.addActionListener(e -> removeLayer(layer));

            row.add(vis,     BorderLayout.WEST);
            row.add(nameBtn, BorderLayout.CENTER);
            row.add(delBtn,  BorderLayout.EAST);
            layerListPanel.add(row);
            layerListPanel.add(Box.createVerticalStrut(2));
        }
        layerListPanel.revalidate();
        layerListPanel.repaint();
    }

    void setActiveLayer(SpriteLayer layer) {
        activeLayer = layer;
        rebuildLayerList();
        updateBindLabel();
        canvas.repaint();
    }

    void removeLayer(SpriteLayer layer) {
        layers.remove(layer);
        if (activeLayer == layer) activeLayer = layers.isEmpty() ? null : layers.get(layers.size() - 1);
        rebuildLayerList();
        updateBindLabel();
        canvas.repaint();
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

        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
            if (e.getID() == KeyEvent.KEY_PRESSED && e.getKeyCode() == KeyEvent.VK_DELETE) {
                Component focused = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
                if (focused instanceof JTextField) return false;
                if (selectedPoint >= 0) {
                    removeSelectedPoint();
                } else if (selectedPath != null) {
                    AnimTrack t = animation.trackFor(selectedPath);
                    if (t != null && t.hasKeyframeAt((int) currentFrame)) removeKeyframeForSelected();
                    else deleteSelectedPath();
                }
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
        paths.removeIf(p -> p.parentPath == toDelete);
        paths.remove(toDelete);
        // remove from all layers
        for (SpriteLayer layer : layers) {
            layer.restPoses.remove(toDelete);
            layer.weightMaps.remove(toDelete);
            layer.rigidMaps.remove(toDelete);
        }
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
                addSpriteLayer(toBufferedImage((Image) cb.getData(DataFlavor.imageFlavor)));
                return;
            }
            if (cb.isDataFlavorAvailable(DataFlavor.javaFileListFlavor)) {
                @SuppressWarnings("unchecked")
                List<File> files = (List<File>) cb.getData(DataFlavor.javaFileListFlavor);
                if (!files.isEmpty()) {
                    BufferedImage img = ImageIO.read(files.get(0));
                    if (img != null) addSpriteLayer(img);
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
                        addSpriteLayer(toBufferedImage((Image) t.getTransferData(DataFlavor.imageFlavor)));
                    } else if (t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        @SuppressWarnings("unchecked")
                        List<File> files = (List<File>) t.getTransferData(DataFlavor.javaFileListFlavor);
                        for (File f : files) {
                            BufferedImage img = ImageIO.read(f);
                            if (img != null) addSpriteLayer(img);
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

    void addSpriteLayer(BufferedImage img) {
        String name = "Layer " + layerCounter++;
        double lx = (canvas.getWidth()  - img.getWidth())  / 2.0;
        double ly = (canvas.getHeight() - img.getHeight()) / 2.0;
        SpriteLayer layer = new SpriteLayer(name, img, lx, ly);
        layers.add(layer);
        setActiveLayer(layer);   // also calls rebuildLayerList + updateBindLabel + repaint
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
        fc.setDialogTitle("Bild als Layer laden");
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "Bilder (PNG, JPG, GIF, BMP)", "png", "jpg", "jpeg", "gif", "bmp"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            BufferedImage img = ImageIO.read(fc.getSelectedFile());
            if (img != null) {
                String baseName = fc.getSelectedFile().getName();
                int dot = baseName.lastIndexOf('.');
                if (dot > 0) baseName = baseName.substring(0, dot);
                double lx = (canvas.getWidth()  - img.getWidth())  / 2.0;
                double ly = (canvas.getHeight() - img.getHeight()) / 2.0;
                SpriteLayer layer = new SpriteLayer(baseName, img, lx, ly);
                layers.add(layer);
                setActiveLayer(layer);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Fehler beim Laden: " + ex.getMessage());
        }
    }

    void bindSkeleton() {
        if (activeLayer == null)  { JOptionPane.showMessageDialog(this, "Zuerst einen Layer laden."); return; }
        if (selectedPath == null) { JOptionPane.showMessageDialog(this, "Zuerst einen Pfad auswählen."); return; }
        if (!activeLayer.restPoses.containsKey(selectedPath)) {
            List<PathPoint> rest = new ArrayList<>();
            for (PathPoint p : selectedPath.points) rest.add(p.copy());
            activeLayer.restPoses.put(selectedPath, rest);
            getOrCreateWeightMap(selectedPath);
        }
        updateBindLabel();
        canvas.repaint();
    }

    void unbind() {
        if (activeLayer == null) return;
        if (selectedPath != null) {
            activeLayer.restPoses.remove(selectedPath);
            activeLayer.weightMaps.remove(selectedPath);
            activeLayer.rigidMaps.remove(selectedPath);
        } else {
            activeLayer.restPoses.clear();
            activeLayer.weightMaps.clear();
            activeLayer.rigidMaps.clear();
        }
        updateBindLabel();
        canvas.repaint();
    }

    void updateBindLabel() {
        if (activeLayer == null) {
            bindLabel.setText("<html><small><i>kein Layer aktiv</i></small></html>");
        } else if (activeLayer.restPoses.isEmpty()) {
            bindLabel.setText("<html><small>Layer: <b>" + activeLayer.name + "</b> – kein Skelett</small></html>");
        } else {
            StringBuilder sb = new StringBuilder("<html><small><b>" + activeLayer.name + "</b><br>");
            for (PathModel pm : activeLayer.restPoses.keySet())
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
        if (activeLayer == null) return new float[0];
        return activeLayer.weightMaps.computeIfAbsent(path, p -> {
            int size = activeLayer.image.getWidth() * activeLayer.image.getHeight();
            float[] wm = new float[size];
            java.util.Arrays.fill(wm, 1.0f);
            return wm;
        });
    }

    byte[] getOrCreateRigidMap(PathModel path) {
        if (activeLayer == null) return new byte[0];
        return activeLayer.rigidMaps.computeIfAbsent(path, p ->
            new byte[activeLayer.image.getWidth() * activeLayer.image.getHeight()]);
    }

    void paintWeight(int canvasX, int canvasY, boolean erase) {
        if (activeLayer == null || selectedPath == null) return;
        if (!activeLayer.restPoses.containsKey(selectedPath)) return;
        int imgW = activeLayer.image.getWidth(), imgH = activeLayer.image.getHeight();
        int cx = (int)(canvasX - activeLayer.x), cy = (int)(canvasY - activeLayer.y);

        if (paintRigid) {
            byte[] rm = getOrCreateRigidMap(selectedPath);
            if (pixelBrush) {
                if (cx >= 0 && cy >= 0 && cx < imgW && cy < imgH)
                    rm[cy * imgW + cx] = erase ? (byte)0 : (byte)1;
            } else {
                for (int dy = -brushRadius; dy <= brushRadius; dy++) {
                    for (int dx = -brushRadius; dx <= brushRadius; dx++) {
                        int px = cx+dx, py = cy+dy;
                        if (px<0||py<0||px>=imgW||py>=imgH) continue;
                        if (dx*dx+dy*dy > brushRadius*brushRadius) continue;
                        rm[py * imgW + px] = erase ? (byte)0 : (byte)1;
                    }
                }
            }
        } else {
            float[] wm = getOrCreateWeightMap(selectedPath);
            if (pixelBrush) {
                if (cx >= 0 && cy >= 0 && cx < imgW && cy < imgH)
                    wm[cy * imgW + cx] = erase ? 0f : 1f;
            } else {
                for (int dy = -brushRadius; dy <= brushRadius; dy++) {
                    for (int dx = -brushRadius; dx <= brushRadius; dx++) {
                        int px = cx+dx, py = cy+dy;
                        if (px<0||py<0||px>=imgW||py>=imgH) continue;
                        double dist = Math.sqrt(dx*dx+dy*dy);
                        if (dist > brushRadius) continue;
                        int idx = py * imgW + px;
                        if (binaryMode) {
                            wm[idx] = erase ? 0f : 1f;
                        } else {
                            float falloff = (float)(1.0 - dist / brushRadius);
                            if (erase) wm[idx] = Math.max(0f, wm[idx] - brushOpacity * falloff);
                            else       wm[idx] = Math.min(1f, wm[idx] + brushOpacity * falloff);
                        }
                    }
                }
            }
        }
        canvas.repaint();
    }

    void drawHeatmapOverlay(Graphics2D g2) {
        if (!paintMode || activeLayer == null || selectedPath == null) return;
        float[] wm = activeLayer.weightMaps.get(selectedPath);
        if (wm == null) return;
        int imgW = activeLayer.image.getWidth(), imgH = activeLayer.image.getHeight();
        byte[] rm = activeLayer.rigidMaps.get(selectedPath);
        int[] pixels = new int[imgW * imgH];
        for (int i = 0; i < pixels.length; i++) {
            if (rm != null && rm[i] == 1) {
                pixels[i] = (200 << 24) | (255 << 16) | (140 << 8) | 0; // orange = starr
            } else {
                float w = wm[i];
                if (binaryMode) {
                    pixels[i] = w >= 0.5f
                        ? (180 << 24) | (220 << 16) | (60 << 8) | 0
                        : (120 << 24) | (40  << 16) | (40  << 8) | 180;
                } else {
                    int r  = (int)(Math.min(1f, w * 2f)                   * 220);
                    int gv = (int)(Math.max(0f, 1f - Math.abs(w-0.5f)*2f) * 220);
                    int b  = (int)(Math.max(0f, (1f - w) * 2f)            * 220);
                    pixels[i] = (170 << 24) | (r << 16) | (gv << 8) | b;
                }
            }
        }
        BufferedImage overlay = new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_ARGB);
        overlay.setRGB(0, 0, imgW, imgH, pixels, 0, imgW);
        g2.drawImage(overlay, (int)activeLayer.x, (int)activeLayer.y, null);
    }

    // ── Selection ────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    void selectPointOf(PathModel path, int idx) {
        selectedPath = path; selectedPoint = idx;
        selectedEdgePath = null; selectedEdgeIdx = -1;
        for (Component c : pathListPanel.getComponents()) {
            if (!(c instanceof JComboBox)) continue;
            JComboBox<ComboItem> cb = (JComboBox<ComboItem>) c;
            if (cb.getClientProperty("path") != path) continue;
            updating = true;
            for (int i = 0; i < cb.getItemCount(); i++) {
                ComboItem it = cb.getItemAt(i);
                if (it.kind == ComboItem.Kind.POINT && it.idx == idx) { cb.setSelectedIndex(i); break; }
            }
            updating = false;
        }
        syncCoordsFromModel();
        canvas.repaint();
    }

    @SuppressWarnings("unchecked")
    void selectEdge(PathModel path, int edgeIdx) {
        selectedEdgePath = path; selectedEdgeIdx = edgeIdx;
        selectedPath = path; selectedPoint = -1;
        for (Component c : pathListPanel.getComponents()) {
            if (!(c instanceof JComboBox)) continue;
            JComboBox<ComboItem> cb = (JComboBox<ComboItem>) c;
            if (cb.getClientProperty("path") != path) continue;
            updating = true;
            for (int i = 0; i < cb.getItemCount(); i++) {
                ComboItem it = cb.getItemAt(i);
                if (it.kind == ComboItem.Kind.EDGE && it.idx == edgeIdx) { cb.setSelectedIndex(i); break; }
            }
            updating = false;
        }
        syncCoordsFromModel();
        refreshCombo(path);
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

    @SuppressWarnings("unchecked")
    void addPathRow(PathModel path) {
        JComboBox<ComboItem> cb = new JComboBox<>();
        cb.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        cb.putClientProperty("path", path);
        fillCombo(cb, path);
        cb.addActionListener(e -> {
            if (updating) return;
            ComboItem item = (ComboItem) cb.getSelectedItem();
            if (item == null || item.kind == ComboItem.Kind.HEADER) return;
            if      (item.kind == ComboItem.Kind.POINT) selectPointOf(path, item.idx);
            else if (item.kind == ComboItem.Kind.EDGE)  selectEdge(path, item.idx);
        });
        pathListPanel.add(cb);
        pathListPanel.add(Box.createVerticalStrut(4));
        pathListPanel.revalidate();
        pathListPanel.repaint();
    }

    @SuppressWarnings("unchecked")
    void fillCombo(JComboBox<ComboItem> cb, PathModel path) {
        updating = true;
        cb.removeAllItems();
        String branchInfo = path.isBranch()
                ? "  \u21A9 " + path.parentPath.name + "[" + path.parentPointIdx + "]" : "";
        AnimTrack track = animation.trackFor(path);
        String kfInfo = (track != null && track.hasKeyframeAt((int) currentFrame)) ? " ◆" : "";
        boolean isBound = activeLayer != null && activeLayer.restPoses.containsKey(path);
        cb.addItem(new ComboItem(ComboItem.Kind.HEADER, -1,
                "\u25B6 " + path.name + "  (" + path.points.size() + " Punkte)"
                + (isBound ? " [gebunden]" : "") + branchInfo + kfInfo));
        for (int i = 0; i < path.points.size(); i++) {
            cb.addItem(new ComboItem(ComboItem.Kind.POINT, i, "   " + i + ": " + path.points.get(i)));
            if (showEdgesInList && i < path.points.size() - 1) {
                PathPoint a = path.points.get(i), b = path.points.get(i + 1);
                double len = Math.hypot(b.x - a.x, b.y - a.y);
                boolean edgeSel = (path == selectedEdgePath && i == selectedEdgeIdx);
                cb.addItem(new ComboItem(ComboItem.Kind.EDGE, i,
                        (edgeSel ? " ▶ " : "  ↔ ") + i + "-" + (i+1)
                        + "  [" + String.format("%.1f", len) + " px]"));
            }
        }
        updating = false;
    }

    @SuppressWarnings("unchecked")
    void refreshCombo(PathModel path) {
        for (Component c : pathListPanel.getComponents()) {
            if (!(c instanceof JComboBox)) continue;
            JComboBox<ComboItem> cb = (JComboBox<ComboItem>) c;
            if (cb.getClientProperty("path") != path) continue;
            ComboItem sel = (ComboItem) cb.getSelectedItem();
            fillCombo(cb, path);
            if (sel != null) {
                updating = true;
                for (int i = 0; i < cb.getItemCount(); i++) {
                    ComboItem it = cb.getItemAt(i);
                    if (it.kind == sel.kind && it.idx == sel.idx) { cb.setSelectedIndex(i); break; }
                }
                updating = false;
            }
        }
    }

    // ── Sprite deformation rendering ─────────────────────────────────────────

    void drawDeformedSprite(Graphics2D g2) {
        for (SpriteLayer layer : layers) {
            if (!layer.visible) continue;
            drawLayerDeformed(g2, layer);
        }
    }

    void drawLayerDeformed(Graphics2D g2, SpriteLayer layer) {
        int imgW = layer.image.getWidth(), imgH = layer.image.getHeight();

        if (layer.restPoses.isEmpty()) {
            g2.drawImage(layer.image, (int) layer.x, (int) layer.y, null);
            return;
        }

        int C = MESH_COLS, R = MESH_ROWS;
        double[][] gx = new double[R+1][C+1], gy = new double[R+1][C+1];
        for (int r = 0; r <= R; r++) {
            for (int c = 0; c <= C; c++) {
                double rx = layer.x + (double)c * imgW / C;
                double ry = layer.y + (double)r * imgH / R;
                int imgX = (int)(rx - layer.x), imgY = (int)(ry - layer.y);
                boolean isRigid = false;

                if (imgX >= 0 && imgY >= 0 && imgX < imgW && imgY < imgH) {
                    for (Map.Entry<PathModel, List<PathPoint>> entry : layer.restPoses.entrySet()) {
                        byte[] rm = layer.rigidMaps.get(entry.getKey());
                        if (rm != null && rm[imgY * imgW + imgX] == 1) {
                            List<PathPoint> rest = entry.getValue();
                            PathModel bp = entry.getKey();
                            double minD = Double.MAX_VALUE; double rdx = 0, rdy = 0;
                            for (int i = 0; i < Math.min(rest.size(), bp.points.size()); i++) {
                                PathPoint rp = rest.get(i), cp = bp.points.get(i);
                                double d = Math.hypot(rx - rp.x, ry - rp.y);
                                if (d < minD) { minD = d; rdx = cp.x - rp.x; rdy = cp.y - rp.y; }
                            }
                            gx[r][c] = rx + rdx; gy[r][c] = ry + rdy;
                            isRigid = true; break;
                        }
                    }
                }

                if (!isRigid) {
                    double totalW = 0, dx = 0, dy = 0;
                    for (Map.Entry<PathModel, List<PathPoint>> entry : layer.restPoses.entrySet()) {
                        PathModel bp   = entry.getKey();
                        List<PathPoint> rest = entry.getValue();
                        float[] wm = layer.weightMaps.get(bp);
                        float pathWeight = 1f;
                        if (wm != null && imgX >= 0 && imgY >= 0 && imgX < imgW && imgY < imgH)
                            pathWeight = wm[imgY * imgW + imgX];
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
        }

        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        for (int r = 0; r < R; r++) {
            for (int c = 0; c < C; c++) {
                double u0 = (double)c    *imgW/C, v0 = (double)r    *imgH/R;
                double u1 = (double)(c+1)*imgW/C, v1 = (double)(r+1)*imgH/R;
                drawTexturedTri(g2, layer.image, gx[r][c],   gy[r][c],   gx[r][c+1],   gy[r][c+1],   gx[r+1][c],   gy[r+1][c],   u0,v0, u1,v0, u0,v1);
                drawTexturedTri(g2, layer.image, gx[r][c+1], gy[r][c+1], gx[r+1][c+1], gy[r+1][c+1], gx[r+1][c],   gy[r+1][c],   u1,v0, u1,v1, u0,v1);
            }
        }
    }

    void drawTexturedTri(Graphics2D g2, BufferedImage img,
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
        g.drawImage(img, at, null);
        g.dispose();
    }

    static double distToSegment(double px, double py,
                                double ax, double ay, double bx, double by) {
        double dx = bx - ax, dy = by - ay;
        double lenSq = dx*dx + dy*dy;
        if (lenSq == 0) return Math.hypot(px - ax, py - ay);
        double t = Math.max(0, Math.min(1, ((px-ax)*dx + (py-ay)*dy) / lenSq));
        return Math.hypot(px - (ax + t*dx), py - (ay + t*dy));
    }

    // ── Timeline panel ────────────────────────────────────────────────────────

    class TimelinePanel extends JPanel {
        static final int LEFT   = 110;
        static final int RULER_H = 22;
        static final int TRACK_H = 18;
        static final int KF      = 5;

        double    pxPerFrame  = 5.0;
        boolean   dragging    = false;
        boolean   kfDragging  = false;
        AnimTrack kfDragTrack = null;
        Keyframe  kfDragKf    = null;

        TimelinePanel() {
            setPreferredSize(new Dimension(0, 130));
            setBackground(new Color(28, 28, 36));

            MouseAdapter ma = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    int trackIdx = (e.getY() - RULER_H) / TRACK_H;
                    if (e.getX() < LEFT) {
                        if (trackIdx >= 0 && trackIdx < paths.size()) {
                            PathModel pm = paths.get(trackIdx);
                            selectedPath  = pm; selectedPoint = -1;
                            selectedEdgePath = null; selectedEdgeIdx = -1;
                            syncCoordsFromModel();
                            for (PathModel p : paths) refreshCombo(p);
                            canvas.repaint(); repaint();
                        }
                        return;
                    }
                    if (trackIdx >= 0 && trackIdx < paths.size()) {
                        PathModel pm = paths.get(trackIdx);
                        AnimTrack t = animation.trackFor(pm);
                        if (t != null) {
                            for (Keyframe kf : t.keyframes) {
                                if (Math.abs(px(kf.frame) - e.getX()) <= KF + 3) {
                                    kfDragging = true;
                                    kfDragTrack = t; kfDragKf = kf;
                                    selectedPath = pm; selectedPoint = -1;
                                    seekToFrame(kf.frame);
                                    setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                                    return;
                                }
                            }
                        }
                    }
                    dragging = true; scrub(e.getX());
                }

                @Override public void mouseDragged(MouseEvent e) {
                    if (kfDragging && kfDragKf != null) {
                        int newFrame = Math.max(0, Math.min(animation.totalFrames,
                                (int) Math.round((e.getX() - LEFT) / pxPerFrame)));
                        kfDragTrack.keyframes.remove(kfDragKf);
                        kfDragKf.frame = newFrame;
                        kfDragTrack.keyframes.add(kfDragKf);
                        kfDragTrack.keyframes.sort(Comparator.comparingInt(k -> k.frame));
                        seekToFrame(newFrame);
                        return;
                    }
                    if (dragging) scrub(e.getX());
                }

                @Override public void mouseReleased(MouseEvent e) {
                    dragging = false;
                    if (kfDragging) {
                        kfDragging = false; kfDragKf = null; kfDragTrack = null;
                        setCursor(Cursor.getDefaultCursor());
                        for (PathModel p : paths) refreshCombo(p);
                    }
                }

                @Override public void mouseMoved(MouseEvent e) {
                    if (e.getX() < LEFT) { setCursor(Cursor.getDefaultCursor()); return; }
                    int trackIdx = (e.getY() - RULER_H) / TRACK_H;
                    if (trackIdx >= 0 && trackIdx < paths.size()) {
                        AnimTrack t = animation.trackFor(paths.get(trackIdx));
                        if (t != null) {
                            for (Keyframe kf : t.keyframes) {
                                if (Math.abs(px(kf.frame) - e.getX()) <= KF + 3) {
                                    setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                                    return;
                                }
                            }
                        }
                    }
                    setCursor(Cursor.getDefaultCursor());
                }
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

            int ty = RULER_H;
            for (int ti = 0; ti < paths.size(); ti++) {
                PathModel pm = paths.get(ti);
                AnimTrack track = animation.trackFor(pm);

                boolean isSel = (pm == selectedPath);
                g2.setColor(isSel ? new Color(70, 60, 30) : new Color(50, 50, 62));
                g2.fillRect(0, ty, LEFT, TRACK_H);
                g2.setColor(isSel ? new Color(255, 220, 80) : new Color(180, 180, 200));
                g2.setFont(g2.getFont().deriveFont(10f));
                String lbl = pm.name;
                if (lbl.length() > 13) lbl = lbl.substring(0, 11) + "..";
                g2.drawString(lbl, 4, ty + TRACK_H - 4);

                g2.setColor(ti % 2 == 0 ? new Color(36, 36, 46) : new Color(32, 32, 42));
                g2.fillRect(LEFT, ty, w, TRACK_H);

                if (track != null) {
                    if (track.keyframes.size() >= 2) {
                        int x0 = px(track.keyframes.get(0).frame);
                        int x1 = px(track.keyframes.get(track.keyframes.size()-1).frame);
                        int cy2 = ty + TRACK_H/2;
                        g2.setColor(new Color(80, 80, 120));
                        g2.fillRect(x0, cy2 - 2, x1 - x0, 4);
                    }

                    for (Keyframe kf : track.keyframes) {
                        int px = px(kf.frame), cy2 = ty + TRACK_H/2;
                        boolean atCurrent = kf.frame == (int) currentFrame;
                        int[] xs = {px, px+KF, px, px-KF};
                        int[] ys = {cy2-KF, cy2, cy2+KF, cy2};
                        g2.setColor(atCurrent ? new Color(255, 120, 60) : new Color(255, 200, 50));
                        g2.fillPolygon(xs, ys, 4);
                        g2.setColor(Color.WHITE);
                        g2.setStroke(new BasicStroke(1f));
                        g2.drawPolygon(xs, ys, 4);
                    }
                }

                ty += TRACK_H;
            }

            int endPx = px(animation.totalFrames);
            if (endPx < w) {
                g2.setColor(new Color(200, 80, 80, 180));
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawLine(endPx, 0, endPx, h);
            }

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
        boolean   viewPanning  = false;
        double    prevDragX, prevDragY;
        double    zoom = 1.0, viewOffX = 0, viewOffY = 0;
        PathModel hoveredEdgePath  = null;
        int       hoveredEdgeIdx   = -1;
        PathModel hoveredPointPath = null;
        int       hoveredPointIdx  = -1;

        double toWorldX(double sx) { return (sx - viewOffX) / zoom; }
        double toWorldY(double sy) { return (sy - viewOffY) / zoom; }

        CanvasPanel() {
            setBackground(new Color(40, 40, 48));
            MouseAdapter ma = new MouseAdapter() {

                @Override public void mousePressed(MouseEvent e) {
                    if (SwingUtilities.isMiddleMouseButton(e)) {
                        viewPanning = true;
                        prevDragX = e.getX(); prevDragY = e.getY();
                        setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                        return;
                    }
                    if (paintMode) {
                        paintWeight((int)toWorldX(e.getX()), (int)toWorldY(e.getY()),
                                    SwingUtilities.isRightMouseButton(e));
                        return;
                    }
                    if (e.isAltDown() && SwingUtilities.isLeftMouseButton(e)) {
                        viewPanning = false;
                        prevDragX = e.getX(); prevDragY = e.getY();
                        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                        dragPath = null; dragIdx = -2;
                        return;
                    }
                    if (playing) pause();

                    double wx = toWorldX(e.getX()), wy = toWorldY(e.getY());

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
                    if (hit != null) {
                        if (keepLength && dragIdx > 0) {
                            PathPoint prev = dragPath.points.get(dragIdx - 1);
                            dragOrigLength = Math.hypot(hit.x - prev.x, hit.y - prev.y);
                        }
                        if (SwingUtilities.isRightMouseButton(e)) {
                            rightDrag = true;
                            ghost = new PathPoint(hit.x, hit.y, hit.z);
                        } else {
                            rightDrag = false;
                            selectPointOf(dragPath, dragIdx);
                        }
                        return;
                    }

                    double edgeThresh = 6.0 / zoom;
                    for (PathModel pm : paths) {
                        List<PathPoint> pts = pm.points;
                        for (int i = 0; i < pts.size() - 1; i++) {
                            PathPoint a = pts.get(i), b = pts.get(i + 1);
                            if (distToSegment(wx, wy, a.x, a.y, b.x, b.y) <= edgeThresh) {
                                selectEdge(pm, i);
                                dragPath = null; dragIdx = -1;
                                return;
                            }
                        }
                    }
                    dragPath = null; dragIdx = -1;
                }

                @Override public void mouseMoved(MouseEvent e) {
                    if (paintMode) {
                        paintCursorX = e.getX(); paintCursorY = e.getY();
                        repaint(); return;
                    }
                    double wx = toWorldX(e.getX()), wy = toWorldY(e.getY());
                    boolean needRepaint = false;

                    PathModel newHovPtPath = null; int newHovPtIdx = -1;
                    outer1:
                    for (PathModel pm : paths) {
                        for (int i = 0; i < pm.points.size(); i++) {
                            if (dist(e.getPoint(), pm.points.get(i)) <= R + 4) {
                                newHovPtPath = pm; newHovPtIdx = i; break outer1;
                            }
                        }
                    }
                    if (newHovPtPath != hoveredPointPath || newHovPtIdx != hoveredPointIdx) {
                        hoveredPointPath = newHovPtPath; hoveredPointIdx = newHovPtIdx;
                        needRepaint = true;
                    }

                    PathModel newHovPath = null; int newHovIdx = -1;
                    if (newHovPtPath == null) {
                        double edgeThresh = 6.0 / zoom;
                        outer2:
                        for (PathModel pm : paths) {
                            List<PathPoint> pts = pm.points;
                            for (int i = 0; i < pts.size() - 1; i++) {
                                PathPoint a = pts.get(i), b = pts.get(i + 1);
                                if (distToSegment(wx, wy, a.x, a.y, b.x, b.y) <= edgeThresh) {
                                    newHovPath = pm; newHovIdx = i; break outer2;
                                }
                            }
                        }
                    }
                    if (newHovPath != hoveredEdgePath || newHovIdx != hoveredEdgeIdx) {
                        hoveredEdgePath = newHovPath; hoveredEdgeIdx = newHovIdx;
                        needRepaint = true;
                    }
                    if (needRepaint) repaint();
                }

                @Override public void mouseDragged(MouseEvent e) {
                    if (paintMode) {
                        paintCursorX = e.getX(); paintCursorY = e.getY();
                        paintWeight((int)toWorldX(e.getX()), (int)toWorldY(e.getY()),
                                    SwingUtilities.isRightMouseButton(e));
                        return;
                    }
                    if (viewPanning) {
                        viewOffX += e.getX() - prevDragX;
                        viewOffY += e.getY() - prevDragY;
                        prevDragX = e.getX(); prevDragY = e.getY();
                        repaint(); return;
                    }
                    if (dragIdx == -2) {
                        // ALT+drag: move active layer
                        if (activeLayer != null) {
                            double dx = (e.getX() - prevDragX) / zoom;
                            double dy = (e.getY() - prevDragY) / zoom;
                            activeLayer.x += dx; activeLayer.y += dy;
                            for (List<PathPoint> rest : activeLayer.restPoses.values())
                                for (PathPoint p : rest) { p.x += dx; p.y += dy; }
                        }
                        prevDragX = e.getX(); prevDragY = e.getY();
                        repaint(); return;
                    }
                    if (dragPath == null) return;
                    if (rightDrag) {
                        ghost.x = toWorldX(e.getX()); ghost.y = toWorldY(e.getY()); repaint();
                    } else {
                        PathPoint p = dragPath.points.get(dragIdx);
                        double wx = toWorldX(e.getX()), wy = toWorldY(e.getY());
                        if (keepLength && dragIdx > 0 && dragOrigLength > 0) {
                            PathPoint parent = dragPath.points.get(dragIdx - 1);
                            double dx = wx - parent.x, dy = wy - parent.y;
                            double d = Math.hypot(dx, dy);
                            if (d > 0) { p.x = parent.x + dx/d * dragOrigLength; p.y = parent.y + dy/d * dragOrigLength; }
                        } else {
                            p.x = wx; p.y = wy;
                        }
                        if (dragPath == selectedPath && dragIdx == selectedPoint) syncCoordsFromModel();
                        refreshCombo(dragPath);
                        repaint();
                    }
                }

                @Override public void mouseReleased(MouseEvent e) {
                    if (viewPanning) {
                        viewPanning = false;
                        setCursor(Cursor.getDefaultCursor());
                        return;
                    }
                    if (dragIdx == -2) {
                        dragIdx = -1;
                        setCursor(Cursor.getDefaultCursor());
                        return;
                    }
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
                    hoveredEdgePath  = null; hoveredEdgeIdx  = -1;
                    hoveredPointPath = null; hoveredPointIdx = -1;
                    repaint();
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
                } else if (e.isShiftDown()) {
                    brushRadius = Math.max(1, Math.min(200, brushRadius - (int) e.getPreciseWheelRotation() * 2));
                    repaint();
                } else if (paintMode) {
                    brushRadius = Math.max(1, Math.min(200, brushRadius - (int) e.getPreciseWheelRotation() * 2));
                    repaint();
                } else if (zoom != 1.0) {
                    viewOffY -= e.getPreciseWheelRotation() * 40;
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

            java.awt.geom.AffineTransform savedTx = g2.getTransform();
            g2.translate(viewOffX, viewOffY);
            g2.scale(zoom, zoom);

            drawDeformedSprite(g2);
            drawHeatmapOverlay(g2);

            // rest-pose overlay for all layers
            for (SpriteLayer layer : layers) {
                if (!layer.visible) continue;
                for (List<PathPoint> restPose : layer.restPoses.values()) {
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
            }

            // highlight active layer border
            if (activeLayer != null && activeLayer.visible) {
                g2.setColor(new Color(100, 160, 255, 80));
                g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                        10f, new float[]{5f, 4f}, 0f));
                g2.drawRect((int)activeLayer.x, (int)activeLayer.y,
                            activeLayer.image.getWidth(), activeLayer.image.getHeight());
                g2.setStroke(new BasicStroke(1f));
            }

            // skeleton paths
            for (PathModel pm : paths) {
                List<PathPoint> pts = pm.points;
                Color baseEdgeColor = pm.isBranch() ? new Color(230,150,60) : new Color(160,160,200);
                for (int i = 0; i < pts.size()-1; i++) {
                    PathPoint a = pts.get(i), b = pts.get(i+1);
                    boolean hov = (pm == hoveredEdgePath  && i == hoveredEdgeIdx);
                    boolean sel = (pm == selectedEdgePath && i == selectedEdgeIdx);
                    if (sel) {
                        g2.setColor(new Color(255, 220, 50));
                        g2.setStroke(new BasicStroke(3f));
                    } else if (hov) {
                        g2.setColor(new Color(120, 220, 255));
                        g2.setStroke(new BasicStroke(2.5f));
                    } else {
                        g2.setColor(baseEdgeColor);
                        g2.setStroke(new BasicStroke(1.5f));
                    }
                    g2.drawLine((int)a.x,(int)a.y,(int)b.x,(int)b.y);
                }
                g2.setColor(baseEdgeColor);
                g2.setStroke(new BasicStroke(1.5f));
                float[] dash = {3f, 3f};
                Stroke dashedStroke = new BasicStroke(1f, BasicStroke.CAP_BUTT,
                        BasicStroke.JOIN_MITER, 10f, dash, 0f);
                for (int i = 0; i < pts.size(); i++) {
                    PathPoint p = pts.get(i);
                    boolean sel    = (pm == selectedPath && i == selectedPoint);
                    boolean isRoot = pm.isBranch() && i == 0;
                    boolean hov    = (pm == hoveredPointPath && i == hoveredPointIdx);

                    AnimTrack trk = animation.trackFor(pm);
                    if (trk != null && trk.hasKeyframeAt((int) currentFrame)) {
                        g2.setColor(new Color(255, 160, 40, 180));
                        g2.setStroke(new BasicStroke(2f));
                        g2.drawOval((int)p.x-R-3,(int)p.y-R-3,(R+3)*2,(R+3)*2);
                    }

                    if (hov) {
                        g2.setColor(isRoot ? new Color(230,150,60,50) : (sel ? new Color(255,220,50,50) : new Color(80,180,255,50)));
                        if (isRoot) {
                            int[] xs = {(int)p.x,(int)p.x+R,(int)p.x,(int)p.x-R};
                            int[] ys = {(int)p.y-R,(int)p.y,(int)p.y+R,(int)p.y};
                            g2.fillPolygon(xs,ys,4);
                            g2.setColor(new Color(255,255,255,160));
                            g2.setStroke(dashedStroke);
                            g2.drawPolygon(xs,ys,4);
                        } else {
                            g2.fillOval((int)p.x-R,(int)p.y-R,R*2,R*2);
                            g2.setColor(new Color(255,255,255,160));
                            g2.setStroke(dashedStroke);
                            g2.drawOval((int)p.x-R,(int)p.y-R,R*2,R*2);
                        }
                    } else if (isRoot) {
                        int[] xs = {(int)p.x,(int)p.x+R,(int)p.x,(int)p.x-R};
                        int[] ys = {(int)p.y-R,(int)p.y,(int)p.y+R,(int)p.y};
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
                    g2.setColor(hov ? new Color(255,255,255,160) : Color.WHITE);
                    g2.setFont(g2.getFont().deriveFont(9f));
                    g2.drawString(String.valueOf(i),(int)p.x-3,(int)p.y+3);
                    g2.setStroke(new BasicStroke(1.5f));
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

            // brush cursor in world space
            if (paintMode && paintCursorX >= 0) {
                double wx = toWorldX(paintCursorX), wy = toWorldY(paintCursorY);
                g2.setColor(new Color(255, 255, 255, 180));
                g2.setStroke(new BasicStroke((float)(1.0 / zoom)));
                if (pixelBrush) {
                    g2.drawRect((int)wx, (int)wy, 1, 1);
                } else {
                    g2.drawOval((int)(wx - brushRadius), (int)(wy - brushRadius),
                                brushRadius * 2, brushRadius * 2);
                }
            }

            g2.setTransform(savedTx);

            if (paths.isEmpty() && layers.isEmpty()) {
                g2.setColor(new Color(120,120,130));
                g2.setFont(g2.getFont().deriveFont(14f));
                String msg = "ALT+N → Pfad anlegen   |   'Layer hinzufügen' → Bild laden";
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(msg,(getWidth()-fm.stringWidth(msg))/2,getHeight()/2);
            }

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
