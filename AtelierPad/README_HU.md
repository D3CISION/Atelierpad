# Atelier Pad 0.1.0-alpha

Natív Androidos rajzalkalmazás első forráskód-változata, Xiaomi Pad 7 + Focus Pen munkafolyamatra tervezve.

**Ez forrásprojekt, nem telepíthető APK.** A teljes Android-fordítás és a készülékes tesztelés még nem történt meg. A kódgenerálási környezetben nincs Android SDK, és a buildcsomagok letöltése sem volt elérhető.

Az alkalmazás nem Procreate-másolat, és ez az alpha még nem a teljes tervezett funkciókészlet. A felület ebben a változatban angol.

## Mi van meg

- Natív Kotlin/Android felület: rajzvászon, oldalsó eszközsáv, ecset-, szín- és rétegpanel. Nincs WebView.
- Nyomásfüggő méret és fedés, állítható nyomásgörbe, vonalstabilizálás.
- Hat ecsetpreset: tus, grafit, airbrush, száraz festék, sörte és kerek festőecset.
- Ecsetszerkesztő: méret, flow, keménység, bélyegzőtávolság, textúra, szórás, forgatás, iránykövetés, minimum vonalvastagság. Saját presetek mentése.
- Saját képalapú ecsetfej importja. A lenyomatot az alfa-csatorna adja; átlátszó PNG ajánlott. A JPG téglalap alakú, fedett lenyomatot ad.
- Radír, pipetta, tükörszimmetria, rács, nagyítás, vászonforgatás, mozgatás.
- Rétegek: létrehozás, másolás, törlés, sorrend, láthatóság, zárolás, alfa-zár, fedés; Normal, Multiply, Screen, Overlay és Add keverés.
- Utólag módosítható rétegeltolás, méretezés és forgatás a Layer settings alatt.
- Kitöltés állítható színtűréssel; határnak az aktív vagy minden látható réteget használja.
- HSV-színválasztó, HEX-bevitel és alap paletta.
- Kép importálása új rétegként vagy mozgatható referenciaablakként.
- Visszavonás/újraalkalmazás; kétujjas koppintás: undo, háromujjas: redo.
- Automatikus helyi projektmentés, helyi galéria, utolsó projekt visszaolvasása.
- Export: szerkeszthető .atelier, PNG, JPEG, PDF.

## PDF-export

Az Export > PDF menüben három választás van: vászonméret a projekt DPI-ével, A4, illetve A3. A4/A3 esetén az alkalmazás a képarányhoz választ álló vagy fekvő lapot, és a teljes képet vágás nélkül illeszti rá. A margó 18 pont, körülbelül 6,35 mm.

A PDF egyoldalas, összeolvasztott raszteres képet tartalmaz fehér lapon. Nem vektorosítja a rajzot, nem őrzi meg szerkeszthetően a rétegeket, és nem PDF/X nyomdai kimenet. A projektfájlt külön is mentsd el. A DPI a fizikai méretet adja meg; új képrészletet nem hoz létre.

## Fordítás Windows alatt

1. Telepíts Android Studiót. Az SDK Managerben legyen telepítve az Android SDK Platform 35 és a Build Tools 35.0.0. A fordításhoz JDK 17 vagy 21 szükséges.
2. Csomagold ki ezt a projektet. Nyiss terminált az AtelierPad könyvtárban.
3. Futtasd:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\build-apk.ps1
```

A Bypass csak az indított PowerShell-folyamatra vonatkozik, nem módosítja tartósan a gép beállítását. A script letölti a rögzített Gradle 8.11.1-et, ellenőrzi a hivatalos SHA-256 lenyomatot, elkészíti a szabványos Gradle wrappert, majd megpróbálja fordítani és egységtesztelni az alkalmazást. Az első futtatáshoz internetkapcsolat kell. Sikertelen fordításnál leáll; nem hoz létre látszólag sikeres APK-t.

Sikeres fordítás után az APK helye:

```text
app/build/outputs/apk/debug/app-debug.apk
```

A Gradle wrapper létrehozása után az Android Studio az AtelierPad mappát közvetlenül meg tudja nyitni. A wrapper JAR nincs a forráscsomaghoz mellékelve; a bootstrap script generálja.

## Linux / macOS

Android SDK 35 és JDK 17/21 telepítése után:

```bash
bash tools/build-apk.sh
```

Az ANDROID_HOME az SDK helyére mutasson. A script a szokásos felhasználói SDK-könyvtárakat is megpróbálja felismerni. A Gradle wrapper generálása után például a `./gradlew :app:assembleDebug` parancs is használható.

## GitHub Actions alternatíva

A .github/workflows/android.yml egy opcionális buildfolyamat. Nem kapcsolódtam GitHub-fiókhoz és nem töltöttem fel semmit. A teljes projekt saját, lehetőleg privát repositoryba feltöltve az Actions menüben kézzel indítható. Csak sikeres futás után jelenik meg APK-artifact. Ezt a CI-folyamatot itt nem futtattam.

## Első próba a tableten

Csak sikeres Android-fordítás után telepítsd az APK-t. Indulj az alap 1600 x 1200 pixeles vászonnal. A Stylus only alapból aktív: a toll rajzol, két ujjal mozgatható, nagyítható és forgatható a vászon. Toll nélküli próbához a Brush panelben kapcsold ki a Stylus only beállítást.

Próbálj vékonytól vastagodó vonalat, radírozást, új réteget, undo/redo műveletet, majd egy .atelier projektet ments ki, nyiss vissza, és exportáld PDF-be. Részletes tesztlista: tests/DEVICE_CHECKLIST.md.

## Ellenőrzési állapot

- 28 platformfüggetlen Kotlin-teszt sikeresen lefutott. Napló: tests/core-results.txt.
- A teljes alkalmazás Kotlin-szintaktikai és alap típusellenőrzése helyi, csak fordításra szolgáló Android API-helyettesítőkkel megtörtént. Ezek nem a valódi SDK, és nincsenek az alkalmazásba csomagolva. Ez nem igazolja az APK fordíthatóságát vagy futását.
- Az Android-specifikus tesztek forrása elkészült: raszteres pont/radír, PDF megnyitása Android PdfRendererrel, tollvonás megszakítása, ujjérintés figyelmen kívül hagyása. Ezeket itt nem lehetett futtatni.
- A megjelenés, a Focus Pen tényleges nyomásadatai, a késleltetés és a teljesítmény nincsenek készüléken ellenőrizve.

A 28 alapteszt Android SDK nélkül is futtatható Kotlin parancssori fordítóval:

```bash
./tools/test-core.sh
```

Az Android-tesztek valódi SDK-val és csatlakoztatott eszközzel:

```bash
./gradlew :app:connectedDebugAndroidTest
```

## Ismert határok és még hiányzó funkciók

A rajzolás CPU-s bitmapmotorral történik, hardveresen gyorsított Android View megjelenítéssel. Ez még nem dedikált OpenGL/Vulkan festőmotor, nincs mozgáselőrejelzés vagy garantált kis késleltetés. A nyomás 0..1 normalizált Android-adat; a Focus Pen gombjai és dőlése nem használt funkciók.

A flow lenyomatonként halmozódik. Az ecsetkeménység és a textúra hatása az ecsetcsaládtól függ; a custom ecset formáját maga az importált alfa-csatorna határozza meg. Nincs valódi nedvesfesték-szimuláció, maszatolóecset vagy fizikai pigmentkeverés.

Az alpha határai: legfeljebb 4096 pixel oldalhossz és 12 millió pixel/vászon, 32 réteg, 600 000 tárolt vonalpont, 20 000 művelet, 96 MiB kitömörített projektadat. A vászonlétrehozás további memóriaellenőrzést használ. A kitöltés legfeljebb 4 194 304 pixeles vásznon engedélyezett. Ezek védelmi korlátok, nem teljesítményígéretek.

A rétegszám felső korlátja nem jelenti, hogy minden vászonméret mellett 32 réteg gyors vagy biztonságos. Sok nagy réteg esetén a készülékes teljesítményteszt elengedhetetlen.

A következők még nincsenek meg: rétegcsoportok, clipping mask, rétegmaszk, lasszó/kijelölések, perspektívarács, alakzatfelismerés, korrekciós rétegek, Liquify, PSD, Procreate-ecsetformátum, P3/CMYK/PDF-X, felhőszinkron, animáció.

A referenciakép elrendezése és az undo-előzmény nem része a .atelier mentésnek. A projekt rétegei és tényleges rajztartalma megmarad. A saját ecsetpresetek alkalmazásszinten tárolódnak; a projektben használt ecsetbeállítás és importált ecsetfej a projekttel együtt is mentődik.

Az importált JPEG-ek EXIF szerinti forgatása még nem kezelt. Az importált nagy képeket az alkalmazás mintavételezve csökkenti. A helyi galéria első változata névlistás, nincs bélyegkép.

## Adatbiztonság

Az alkalmazás nem kér internet- vagy teljes tárhely-hozzáférést. A kiválasztott fájlokat az Android rendszer fájlválasztóján keresztül olvassa/írja. Az automatikus mentés privát alkalmazástárhelyre történik, atomi fájlfrissítéssel. Az alkalmazás eltávolítása vagy adatainak törlése ezeket a helyi projekteket is törli.

**Fontos rajzról mindig készíts külső .atelier és PNG/PDF másolatot.** Az alpha mentési funkcióját valódi eszközön is ellenőrizni kell. A gyorsítótárban maradó, már nem hivatkozott importált assetek automatikus takarítása még nincs meg.

## Projektfelépítés

- core/Model.kt: immutable projekt- és rétegmodell, korlátok, undo/redo.
- core/BrushEngine.kt: determinisztikus, nyomásérzékeny ecsetlenyomatok.
- core/Geometry.kt: PDF-elrendezés, iteratív kitöltés.
- core/ProjectArchive.kt: verziózott projektarchívum, SHA-256 assetek, méret- és útvonalellenőrzés.
- app/RasterRenderer.kt: raszterezés, rétegcache, kompozitálás.
- app/DrawingView.kt: toll/ujj szétválasztása, gesztusok, vonásvisszavonás megszakításkor.
- app/ProjectFiles.kt: atomi helyi mentés, PNG/JPEG/PDF-export.
- app/MainActivity.kt, Widgets.kt: natív felület.

Technikai hivatkozások: SOURCES.md.
