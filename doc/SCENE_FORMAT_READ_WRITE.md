# Scene Format – TransparencyTool

Vollständige Spezifikation des Scene-Formats für externe Programme (GameII, Tools, Skripte).

---

## 1. Verzeichnisstruktur

Eine Scene besteht immer aus einem **Verzeichnis**, nicht einer einzelnen Datei.

```
scenes/
└── <SceneName>/                    ← Scene-Verzeichnis (= Name der Scene)
    ├── <SceneName>.txt             ← Haupt-Datei (Referenz-Liste)
    ├── images/
    │   ├── background.png          ← Hintergrundbild (immer erstes #Images-Eintrag)
    │   ├── image_1.png             ← ImageLayer 1
    │   ├── image_2.png             ← ImageLayer 2
    │   └── ...
    ├── texts/
    │   ├── text_<id>.txt           ← TextLayer-Konfiguration
    │   └── ...
    └── paths/
        ├── path_<id>.txt           ← PathLayer-Konfiguration (TODO: nicht implementiert)
        └── ...
```

### Wo liegen die Scenes?

#### TransparencyTool-Projekte (primärer Speicherort)
```
%APPDATA%\TransparencyTool\projects\<ProjectName>\scenes\<SceneName>\
```
Beispiel (Windows):
```
C:\Users\<User>\AppData\Roaming\TransparencyTool\projects\Default\scenes\MyScene\
```

#### GameII-Spiele
```
<User>\Games\<GameName>\scenes\<SceneName>\
```
Beispiel:
```
C:\Users\<User>\Games\MyGame\scenes\Level1\
```

> **Hinweis:** Bei GameII-Scenes liegt die `.txt`-Datei direkt im `scenes/`-Ordner  
> (keine Unterordner je Scene). Das ist das **Legacy-Format** – nur lesen, nicht schreiben.

---

## 2. Haupt-Datei `<SceneName>.txt`

UTF-8 kodiert. Abschnitte beginnen mit `#`, Einträge beginnen mit `-`.
Leerzeilen werden ignoriert.

### Vollständiges Beispiel

```
#Name:
-MyScene

#Images:
-background.png
-image_1.png 120 80 400 300 0.0 100
-image_2.png 0 0 200 150 45.0 80

#Texts:
-text_42.txt
-text_99.txt

#Paths:
-path_7.txt
```

### Abschnitte

| Abschnitt | Bedeutung |
|---|---|
| `#Name:` | Interner Name der Scene (Pflicht) |
| `#Images:` | Bild-Referenzen. **Erster Eintrag = Hintergrundbild**, alle weiteren = ImageLayer |
| `#Texts:` | Referenzen auf TextLayer-Dateien in `texts/` |
| `#Paths:` | Referenzen auf PathLayer-Dateien in `paths/` (aktuell nicht gelesen) |

---

## 3. `#Images:`-Einträge

### Format

```
-<filename> [x y w h rotation opacity]
```

| Feld | Typ | Pflicht | Bedeutung |
|---|---|---|---|
| `filename` | String | ✓ | Dateiname relativ zu `images/` |
| `x` | int | ✗ | X-Position auf dem Canvas (Pixel) |
| `y` | int | ✗ | Y-Position auf dem Canvas (Pixel) |
| `w` | int | ✗ | Renderbreite (kann von Bildbreite abweichen) |
| `h` | int | ✗ | Renderhöhe |
| `rotation` | double | ✗ | Rotation in Grad (0.0 = keine) |
| `opacity` | int | ✗ | Deckkraft 0–100 (100 = vollständig sichtbar) |

**Erster Eintrag** hat keine Metadaten – er ist immer das Hintergrundbild:
```
-background.png
```

**Weitere Einträge** (ImageLayer) haben optionale Metadaten:
```
-image_1.png 120 80 400 300 0.0 100
```
Wenn Metadaten fehlen, werden Defaults verwendet: `x=0, y=0, w=Bildbreite, h=Bildhöhe, rotation=0.0, opacity=100`.

---

## 4. `texts/<id>.txt` – TextLayer-Format

Geschrieben von `TextWriter.writeConfigFile()`, gelesen von `TextReader.readConfigFile()`.

```
text=Hallo Welt
font=Arial
size=24
bold=true
italic=false
color=#FF0000
x=100
y=200
width=300
height=50
```

| Feld | Typ | Bedeutung |
|---|---|---|
| `text` | String | Anzuzeigender Text |
| `font` | String | Fontname (System-Font) |
| `size` | int | Schriftgröße in Punkten |
| `bold` | boolean | Fett |
| `italic` | boolean | Kursiv |
| `color` | `#RRGGBB` | Textfarbe (Hex) |
| `x`, `y` | int | Position auf dem Canvas |
| `width`, `height` | int | Bounding Box |

---

## 5. `paths/<id>.txt` – PathLayer-Format

> **Status: nicht implementiert.** Die Datei wird referenziert aber aktuell nicht gelesen/geschrieben.  
> Zukünftiges Format: TBD.

---

## 6. Algorithmus: Scene finden

```
1. Iteriere alle Projekte in:
       %APPDATA%\TransparencyTool\projects\

2. Für jedes Projekt <P>:
       Verzeichnis: %APPDATA%\TransparencyTool\projects\<P>\scenes\

3. Für jedes Unterverzeichnis <S> in scenes\:
       Prüfe ob existiert: scenes\<S>\<S>.txt
       → Wenn ja: gültige Scene gefunden
       → scene.file = scenes\<S>\<S>.txt
       → scene.name = <S>
```

---

## 7. Algorithmus: Scene laden

```
1. sceneFile = <SceneName>.txt
2. sceneDir  = sceneFile.getParentFile()
3. imagesDir = sceneDir/images/
4. textsDir  = sceneDir/texts/

5. Parse sceneFile:
   - #Name:       → sceneName
   - #Images:     → erster Eintrag = backgroundImage (sceneDir/images/<filename>)
                    → alle weiteren = ImageLayer mit x,y,w,h,rotation,opacity
   - #Texts:      → TextLayer laden aus textsDir/<filename>
   - #Paths:      → ignoriert (noch nicht implementiert)

6. backgroundImage auf Canvas zeichnen
7. Alle ImageLayer mit gespeicherten Bounds rendern
8. Alle TextLayer rendern
```

---

## 8. Algorithmus: Scene speichern

```
1. sceneDir  = <scenesRoot>/<sceneName>/     (anlegen wenn nicht vorhanden)
2. imagesDir = sceneDir/images/              (anlegen)
3. textsDir  = sceneDir/texts/              (anlegen)
4. pathsDir  = sceneDir/paths/              (anlegen)

5. Hintergrundbild:
       Kopiere backgroundImageFile → imagesDir/background<ext>
       Schreibe in #Images: "-background<ext>\n"

6. Für jeden ImageLayer:
       Schreibe Pixel als PNG: imagesDir/image_<N>.png
       Schreibe in #Images: "-image_<N>.png x y w h rotation opacity\n"

7. Für jeden TextLayer:
       Schreibe Config: textsDir/text_<id>.txt
       Schreibe in #Texts: "-text_<id>.txt\n"

8. Für jeden PathLayer:
       Schreibe Referenz: pathsDir/path_<id>.txt  (Inhalt: TODO)
       Schreibe in #Paths: "-path_<id>.txt\n"

9. Schreibe Haupt-Datei sceneDir/<sceneName>.txt:
       #Name:
       -<sceneName>

       #Images:
       <imageRefs>

       #Paths:
       <pathRefs>

       #Texts:
       <textRefs>
```

---

## 9. Minimales Beispiel – Scene von Grund auf erstellen

```
scenes/
└── TestScene/
    ├── TestScene.txt
    └── images/
        └── background.png
```

**TestScene.txt:**
```
#Name:
-TestScene

#Images:
-background.png
```

Das ist die kleinstmögliche gültige Scene (nur Hintergrund, keine Layer).

---

## 10. Vollständiges Beispiel mit Layern

```
scenes/
└── Level1/
    ├── Level1.txt
    ├── images/
    │   ├── background.png
    │   ├── image_1.png
    │   └── image_2.png
    └── texts/
        └── text_42.txt
```

**Level1.txt:**
```
#Name:
-Level1

#Images:
-background.png
-image_1.png 0 0 800 600 0.0 100
-image_2.png 200 150 128 128 90.0 75

#Texts:
-text_42.txt
```

**texts/text_42.txt:**
```
text=Score: 0
font=Arial
size=18
bold=true
italic=false
color=#FFFFFF
x=10
y=10
width=200
height=30
```

---

## 11. Wichtige Hinweise für externe Programme

- **Dateinamen:** Scene-Verzeichnisname und `.txt`-Dateiname müssen **identisch** sein.
- **Encoding:** Alle `.txt`-Dateien sind **UTF-8** ohne BOM.
- **Bilder:** Immer **PNG** (`images/`). Andere Formate werden beim Laden via `ImageIO.read()` versucht, aber nie so gespeichert.
- **Leerzeilen:** Werden beim Parsen übersprungen. Dürfen überall stehen.
- **`-` Präfix:** Jeder Wert-Eintrag beginnt mit `-`. Beim Parsen wird er entfernt.
- **Metadaten sind optional:** Ein Image-Eintrag ohne Metadaten (`-filename.png`) ist gültig; Defaults werden angewendet.
- **Reihenfolge der Abschnitte:** Beliebig – der Parser ist abschnittbasiert, nicht positionsbasiert.
- **Paths nicht implementiert:** `#Paths:`-Einträge werden referenziert aber beim Laden ignoriert.
