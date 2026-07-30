# Dialog-Vergleich: StartupDialog vs. Schnellauswahl

## Zusammenfassung
Die beiden Dialoge sind **NICHT identisch**. Der StartupDialog hat ein kategorisiertes Tab-Layout mit speziellen Features (Bilder mit Thumbnails), während der Schnellauswahl-Dialog eine flache Liste aller Projekte zeigt.

---

## Detaillierter Vergleich

| Aspekt | **StartupDialog** | **Schnellauswahl** |
|--------|------|------|
| **Datei** | `StartupDialog.java` (separate Klasse) | `SelectiveAlphaEditor.java` (inline in Methode `showQuickOpenDialog()`) |
| **Titel** | "Zuletzt verwendet" | "Schnellauswahl" |
| **Größe** | 600 × 450 px | 400 × 350 px |
| **Fenster-Eigenschaft** | Modal JDialog | Modal JDialog |

### **Layout & Struktur**

| Aspekt | **StartupDialog** | **Schnellauswahl** |
|--------|------|------|
| **Struktur** | Tabs mit Kategorien (4 Tabs) | Flache SingleListModel (keine Kategorien) |
| **Kategorien** | Teaching, Books, Games, Images (hardcoded) | Alle Kategorien gemischt in einer Liste |
| **Kategorie-Quellen** | `LastProjectsManager.CAT_*` Konstanten | `recent.values()` - alle Kategorien kombiniert |
| **Darstellung pro Item** | <pre>• Teaching, Books, Games:<br>  JList mit custom Renderer<br>• Images:<br>  Custom Items mit Thumbnail<br>  (120×120px), Name, Path,<br>  Delete-Button</pre> | Alle Items: `"Dateiname (vollständiger/pfad)"` |

### **Komponenten-Details**

#### **Header/Titel**
| Aspekt | **StartupDialog** | **Schnellauswahl** |
|--------|------|------|
| **Titel-Label** | "Zuletzt geöffnete Projekte" (Größe: 16pt Bold) | "Zuletzt verwendet:" (Größe: 12pt Bold) |
| **Position** | BorderLayout.NORTH | Hinzugefügt zu centeredColumnPanel |

#### **Listen-Rendering**

**StartupDialog (Kategorien ohne Bilder):**
```java
JList<String> list = new JList<>(formatPaths(paths));
// Custom Renderer: ProjectListCellRenderer
// Zeigt: "  Dateiname  —  Pfad"
```

**StartupDialog (Images-Tab):**
```java
// JPanel mit GridBagLayout
// Jedes Item:
//  ├─ Thumbnail (120×120, async geladen)
//  ├─ Info-Panel (Name, Pfad)
//  └─ Delete-Button (✕)
```

**Schnellauswahl:**
```java
DefaultListModel<String> listModel = new DefaultListModel<>();
for (List<String> files : recent.values()) {
    for (String f : files) {
        listModel.addElement(new File(f).getName() + " (" + f + ")");
    }
}
// Standard JList ohne custom Renderer
```

### **Farbgebung & Styling**

| Aspekt | **StartupDialog** | **Schnellauswahl** |
|--------|------|------|
| **Hintergrund** | AppColors.BG_DARK | AppColors.BG_DARK (content Panel) |
| **List/Panel BG** | AppColors.BG_PANEL | AppColors.BTN_BG (JList) |
| **Text-Farbe** | AppColors.TEXT | AppColors.TEXT |
| **Selection-BG** | AppColors.ACCENT | (Standard Swing) |
| **Borders** | BorderFactory.createLineBorder(AppColors.BORDER) | BorderFactory.createLineBorder(AppColors.BORDER) |

### **Buttons**

| Aspekt | **StartupDialog** | **Schnellauswahl** |
|--------|------|------|
| **Button 1** | "Neues Projekt" (SUCCESS / SUCCESS_HOVER) | "Öffnen" (ACCENT / ACCENT_HOVER) |
| **Button 2** | "Überspringen" (BTN_BG / BTN_HOVER) | "Durchsuchen" (BTN_BG / BTN_HOVER) |
| **Button 3** | — | "Abbrechen" (BTN_BG / BTN_HOVER) |
| **Button Layout** | FlowLayout.RIGHT | FlowLayout.CENTER |
| **Text-Farben** | Default | openBtn: weiß, andere: AppColors.TEXT |

### **Funktionalität**

| Aspekt | **StartupDialog** | **Schnellauswahl** |
|--------|------|------|
| **Item-Selektion** | ListSelectionListener → direkt laden (dispose on double-click effect) | Open-Button → path extrahieren → loadFile() |
| **Neue Projekte** | "Neues Projekt"-Button (TODO: Not impl.) | — |
| **Durchsuchen** | — | "Durchsuchen"-Button → JFileChooser |
| **Datei löschen** | Images-Tab: Delete-Button → LastProjectsManager.removeRecent() | — |
| **Fehlerbehandlung** | Keine explizit sichtbar | try-catch mit showErrorDialog() |

### **Code-Organisation**

| Aspekt | **StartupDialog** | **Schnellauswahl** |
|--------|------|------|
| **Klassifizierung** | Separate Klasse (463 Zeilen) | Inline-Methode in SelectiveAlphaEditor (~80 Zeilen) |
| **Wiederverwendbarkeit** | Kann nur über Konstruktor erzeugt werden | Nur als Methode verfügbar |
| **Abhängigkeiten** | UIComponentFactory, AppColors, LastProjectsManager, File I/O | UIComponentFactory, AppColors, LastProjectsManager, JFileChooser |

---

## Hauptunterschiede zusammengefasst

### 🔴 **Kritische Unterschiede:**

1. **Tab-System vs. flache Liste**
   - StartupDialog hat 4 kategorisierte Tabs
   - Schnellauswahl mischt alles in einer Liste

2. **Datei-Darstellung**
   - StartupDialog zeigt Bilder mit Thumbnails (spezial für Images-Tab)
   - Schnellauswahl zeigt nur `"Name (path)"` für alle Dateien

3. **Button-Set**
   - StartupDialog: "Neues Projekt" + "Überspringen"
   - Schnellauswahl: "Öffnen" + "Durchsuchen" + "Abbrechen"

4. **Item-Selection**
   - StartupDialog: ListSelectionListener (direkt auf Auswahl reagiert)
   - Schnellauswahl: Open-Button-Klick erforderlich

5. **Delete-Funktionalität**
   - StartupDialog: Delete-Buttons für Bilder
   - Schnellauswahl: Keine Delete-Funktion

### ⚠️ **Technische Unterschiede:**

| | StartupDialog | Schnellauswahl |
|---|---|---|
| **Code-Ort** | Separate Klasse | Inline in SelectiveAlphaEditor |
| **Größe** | 600×450 | 400×350 |
| **Button-Layout** | Rechts (RIGHT) | Zentriert (CENTER) |
| **Selection-Mode** | Explizit SINGLE_SELECTION | Explizit SINGLE_SELECTION |

---

## Empfehlungen zur Vereinheitlichung

Um 100% Gleichheit zu erreichen, sollte man sich auf **ein** Designs einigen:

### **Option A: Alle Features des StartupDialog nutzen**
- Kategorisierte Tabs verwenden
- Thumbnails für Bilder anzeigen
- Delete-Buttons behalten
- In Schnellauswahl integrieren

### **Option B: Stark vereinfacht halten**
- Nur flache Liste (wie Schnellauswahl)
- Keine Tabs
- Keine Delete-Buttons
- StartupDialog vereinfachen

### **Option C: Neue gemeinsame Dialog-Klasse**
```java
public class RecentProjectsDialog extends JDialog {
    // Unified implementation mit Tabs
    // Unterstützt beide Use-Cases (Startup + QuickOpen)
}
```

