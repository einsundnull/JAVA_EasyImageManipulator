# PATH Tool Dokumentation

## Überblick

Das PATH Tool ist ein nicht-destruktives Layer-System zur Erstellung und Bearbeitung von Polygonen/Pfaden. Pfade werden als separate Ebenen gespeichert und können jederzeit bearbeitet werden.

---

## Datenstrukturen

### Point3D
```java
public class Point3D {
    public double x, y, z;  // z für zukünftige 3D-Unterstützung (default: 1.0)
}
```
- Speichert einzelne Pfad-Kontrollpunkte
- **Relativkoordinaten**: Positionen sind relativ zum Layer-Ursprung (x, y)
- **Z-Komponente**: Für zukünftige 3D-Features vorgesehen

### PathLayer
```java
public final class PathLayer extends Layer {
    private List<Point3D> points;        // Kontrollpunkte des Pfades
    private BufferedImage image;         // Optional: Bild entlang des Pfades
    private boolean closed;              // true = Polygon (letzter → erster Punkt verbunden)
    private int x, y, width, height;     // Bounding Box (berechnet aus Punkten)
}
```

**Unveränderbar (Value-Object Pattern)**:
- Alle Mutationen geben neue Instanzen zurück
- `withAddedPoint()`, `withRemovedPoint()`, `withMovedPoint()`, etc.
- Die Bounding Box wird automatisch neu berechnet

---

## Benutzerinteraktion

### 1. PATH Tool aktivieren
1. Klick auf PATH-Button in der Toolbar ("≈" Symbol)
2. Der Button zeigt das Tool als aktiv

### 2. Neuen Pfad erstellen
1. Mit aktiviertem PATH Tool auf die Canvas klicken
2. Automatisch wird ein **geschlossenes Polygon mit 3 Startpunkten** erzeugt:
   - Punkt 0 (oben):      Position (50, 10) relativ
   - Punkt 1 (unten-left): Position (10, 90) relativ
   - Punkt 2 (unten-right): Position (90, 90) relativ
3. Das PATH Tool wird automatisch **abgewählt** → SELECT Tool wird aktiviert
4. Der Pfad ist sofort in der Ebenen-Liste sichtbar

### 3. Punkte bearbeiten

#### Hover-Detection
- Wenn die Maus über einen Punkt schwebt: **Rosa Färbung** (Highlight)
- Hit-Radius: 12 Pixel

#### Punkt auswählen
- Klick auf einen Punkt: **Gelbe Färbung** (Selected)
- Nur ein Punkt kann gleichzeitig ausgewählt sein

#### Punkt verschieben
- Selected Punkt + **Drag mit Maus**: Punkt wird an neue Position bewegt
- Die Bounding Box des Layers wird automatisch aktualisiert

#### Punkt entfernen
- Selected Punkt + **ENTF Taste**: Punkt wird gelöscht
- Mindestens 1 Punkt bleibt erhalten
- Die Bounding Box wird neu berechnet

#### Punkt einfügen
- Selected Punkt + **+ Taste**: Neuer Punkt wird NACH dem selected Punkt eingefügt
  - **Position**: Mittelpunkt zwischen selected Punkt und nächstem Punkt
  - **Bei geschlossenen Pfaden**: Wenn letzter Punkt selected ist, wird der erste Punkt als "nächster" verwendet
- Der neue Punkt wird automatisch ausgewählt

---

## Rendering

### Canvas-Ansicht (CanvasPanel)

```
┌─────────────────────────────┐
│                             │
│   ─── Cyan Linien ───      │
│  /    (Punkt-Verbindungen)  │
│ ●─────────────────────● ●  │
│ │        [Punkt]       │ │  │
│ │    (selected=gelb)   │ │  │
│ │    (hovered=rosa)    │ │  │
│ │    (normal=weiß)     │ │  │
│ ●─────────────────────●    │
│                             │
└─────────────────────────────┘
```

**Farben:**
- **Weiße Punkte**: Normal (nicht gehovered, nicht selected)
- **Rosa Punkte**: Gehovered (Maus über dem Punkt)
- **Gelbe Punkte**: Selected (ausgewählt für Bearbeitung)
- **Cyan Linien**: Verbindungen zwischen Punkten
- **Orange Border**: Selected Punkt-Rahmen

**Geschlossene Pfade:**
- Die Linie verbindet auch den letzten mit dem ersten Punkt

### Ebenen-Tile-Preview (ElementLayerPanel)

**Live Bounding Box:**
- Wird **dynamisch** aus aktuellen Punkt-Positionen berechnet
- Nicht basierend auf gespeicherter Layer-Größe

**Rahmen-Berechnung:**
1. Finde Punkt mit **min(x, y)** = obere-linke Ecke
2. Finde Punkt mit **max(x, y)** = untere-rechte Ecke
3. Addiere **5px Padding im 45-Grad-Winkel**:
   - Oben-links: `minX - 5`, `minY - 5`
   - Unten-rechts: `maxX + 5`, `maxY + 5`

**Skalierung:**
```
scale = min(tw / dynamicW, th / dynamicH)
scale = min(scale, 1.0)  // Nicht vergrößern
```

**Beispiel:**
```
Pfad mit Punkten bei:  (20, 30), (80, 40), (50, 90)

Min: (20, 30)  →  Mit Padding: (15, 25)
Max: (80, 90)  →  Mit Padding: (85, 95)

Dynamische Box: 85-15=70 Pixel breit, 95-25=70 Pixel hoch
```

Die Preview wird **live aktualisiert** wenn Punkte bewegt werden!

---

## Tastenkombinationen

| Taste | Aktion |
|-------|--------|
| **ENTF** | Entfernt selected Punkt (mindestens 1 bleibt) |
| **+** | Fügt neuen Punkt nach selected Punkt ein |
| **Maus Drag** | Bewegt selected Punkt |

---

## Integration mit System

### Undo/Redo
- Alle Punkt-Operationen (Add/Remove/Move) sind rückgängig machbar
- Unterstützt durch die bestehende Undo-Stack in SelectiveAlphaEditor

### Serialisierung
- PathLayer wird zusammen mit anderen Ebenen gespeichert
- Points werden als List<Point3D> serialisiert
- Die z-Komponente wird mit gespeichert (für zukünftige 3D-Features)

### Export
- Pfade können mit dem "zu Bild" Button als PNG exportiert werden
- Der Pfad wird als Vektorzeichnung auf die Bildebene gerendert

---

## Beispiel-Workflow

### Szenario: Dreieckiges Objekt tracen

1. **Pfad erstellen**
   ```
   PATH Tool → Click auf Canvas
   → Dreieck mit 3 Punkten wird erzeugt
   → PATH Tool wird abgewählt
   ```

2. **Punkte anpassen**
   ```
   Punkt 0 (oben) anklicken (gelb wird)
   → Drag zu gewünschter Position (z.B. Ecke des Objekts)
   ```

3. **Weitere Punkte einfügen**
   ```
   Punkt 0 selected + PLUS Taste
   → Neuer Punkt 1 wird eingefügt (zwischen Punkt 0 und 1)
   → Dieser wird moved zu nächster Ecke
   ```

4. **Fertigstellung**
   ```
   Alle Punkte positioniert
   → Pfad passt perfekt um das Objekt
   → "zu Bild" exportieren zum Rastern
   ```

---

## Interne Architektur

### Callback-Methoden (CanvasCallbacks)
```java
int getNextElementId()              // Generiert eindeutige Layer-ID
void addElement(Layer el)           // Fügt Layer zur Ebenen-Liste hinzu
void updateSelectedElement(Layer)   // Aktualisiert selected Layer
void setSelectedElement(Layer)      // Wählt Layer aus
```

### PaintToolbar
```java
public void setActiveTool(PaintEngine.Tool tool)  // Tool-Button aktivieren/deaktivieren
```

### CanvasPanel
```java
private int hoveredPathPointIndex     // Welcher Punkt ist gehovered (-1 = keine)
private int selectedPathPointIndex    // Welcher Punkt ist selected (-1 = keine)

// Handler
void mousePressed()      // Pfad erstellen / Punkt auswählen
void mouseDragged()      // Punkt verschieben
void mouseMoved()        // Hover-Detection
void keyPressed()        // DEL / PLUS Befehle
```

---

## Performance-Notizen

- **Bounding Box Berechnung**: O(n) pro Update (n = Anzahl Punkte)
- **Rendering**: O(n) für Linien + O(n) für Punkte
- **Hit-Testing**: O(n) linear Search (12px Radius Check)

Für typische Pfade (3-50 Punkte) ist die Performance auf modernen Systemen nicht kritisch.

---

## Zukünftige Erweiterungen

1. **Z-Komponente nutzen**: 3D-Pfade mit Tiefe
2. **Path Editor Dialog**: Detaillierte Punkt-Liste mit Koordinaten-Eingabe
3. **Kurven**: Bézier-Kurven statt linearer Verbindungen
4. **Path Smoothing**: Automatische Glättung von rauen Pfaden
5. **Pfad-Snapping**: Punkte an Bildelemente snappen
6. **Pfad-Transformation**: Rotation/Skalierung des gesamten Pfades
