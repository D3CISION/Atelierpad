# Atelier Pad 0.5.0-alpha

**Android-forrásprojekt, nem előre lefordított APK.** A 0.4-es verzió folytatása, a megadott két kezelőfelületi referencia alapján. A teljes Android-fordítás és a Xiaomi Pad 7-es teszt itt nem történt meg. A GitHub-tárolót nem módosítottuk közvetlenül.

## Bal oldali gyorssáv

A vászon bal szélén, fentről lefelé:

1. Függőleges ecsetméret-csúszka.
2. Lekerekített négyzet: **Eyedropper / pipetta**.
3. Függőleges fedés- (opacity-) csúszka.
4. Visszavonás, majd újraalkalmazás.

A csúszkáknak nincs ikonjuk vagy állandó feliratuk. Sötét, lekerekített sávot és világos fogantyút kaptak. Húzáskor az érték egy kis lebegő buborékban látszik. Felfelé nő az érték. Hosszú nyomással pontos szám is beírható.

Ujjal és tollal is kezelhetők, bekapcsolt **Stylus only on canvas** mellett is. A tollra korlátozás csak a vászonra vonatkozik. A gyártó rendszerszintű tenyérérintés-szűrését a program nem tudja megkerülni; az egyidejű toll/ujj bevitel készülékes ellenőrzést igényel.

## Felső eszközsor és projektmenü

**Bal felül:** Gallery / kezdőlap, Tools / csavarkulcs, Effects / varázspálca.

**Jobb felül:** ecset, kijelölés, radír, rétegek, aktuális szín.

A Project menü bal alul maradt. Az import és a PNG-, JPEG-, PDF-, illetve .atelier-export innen nyitható. Nincs alsó vízszintes csúszkasáv. A rétegek hosszú nyomásos átrendezése, a kezdőlapi projekt-előnézetek és szerkesztési dátumok megmaradtak.

## Színválasztó

Külső **árnyalatgyűrű**, belső körben **telítettség és világosság**, külön jelölőkkel. A gyűrűn megkezdett húzás csak az árnyalatot változtatja; a belső körön megkezdett húzás a telítettséget/világosságot. A fehér, a fekete és a teljesen telített szín is elérhető.

Az áttetsző sötét panelben nincs görgetés, HEX-mező, pipettagomb vagy külön Brightness csúszka. A kör alatt két sorban legfeljebb 12 legutóbbi szín jelenik meg. A panel a rendelkezésre álló magassághoz igazodik; a színkör csökken, nem a panel kezd görgetni.

## Ecsetek és kijelölések

Az ecsetminták mindig **fehérek, sötét alapon**, és nem veszik át az aktuális rajzszínt. Az ecset és a radír könyvtára azonos megjelenésű. A Size/Opacity ismétlődő vezérlői kikerültek belőlük; ezek a bal oldali sávon vannak. A részletes szerkesztőben a textúra, a flow, a stabilizálás, a nyomásválasz stb. továbbra is állítható. Az ecsetválasztás megtartja az adott eszköz aktuális méretét/fedését.

A kijelölőpanel **csak ikonokat** tartalmaz. Felül lasszó, téglalap, ellipszis és bezárás; alul megfordítás, kijelölés megszüntetése, másolás új rétegre, kivágás új rétegre. A Select all és Clear nincs a panelen. A funkciónevek hosszú nyomásos súgóként/akadálymentes megnevezésként elérhetők.

## Új: Effects

- Hue / saturation: árnyalat és telítettség.
- Brightness / contrast: világosság és kontraszt.
- Gaussian blur: Gauss-jellegű elmosás, gyors háromlépéses dobozszűrős közelítéssel, legfeljebb 24 px szigmával.

A módosítások először kicsinyített előnézetben látszanak. Az előnézetet nyomva tartva az eredeti látszik. **Apply** hajtja végre a módosítást; **Cancel**, a bezárás vagy az ablakon kívüli koppintás elveti. Egy alkalmazás egy visszavonható lépés.

Az effekt az **aktív rétegre**, aktív kijelölésnél annak területére hat. A többi réteg, a sorrend, a transzformáció, a keverési mód, a fedés és a vágómaszk beállítása megmarad. Az előnézet a kiválasztott réteg helyi képe, nem az összes réteg végső kompozitja. Az elmosás az átlátszóságot is kezeli.

**Ezek még nem külön korrekciós rétegek.** Az Apply az aktív réteg tartalmát raszteres képké alakítja, a visszavonási előzményben megőrizve az előző állapotot. Az effekt csúszkái utólag nem nyithatók vissza az eredeti paraméterekkel. A visszavonási előzmény nem marad meg az alkalmazás bezárása után; fontos képnél őrizz meg külső projektmásolatot is.

Memóriavédelem miatt az effektek jelenlegi határa **4 194 304 vászonpixel** (például 2048 x 2048). Látható, nem zárolt réteg kell; az alfa-zárat előbb ki kell kapcsolni. Hiba vagy memóriahiány esetén a projekt nem változik meg.

## Telepítés / GitHub Actions

1. A fontos rajzokat előbb exportáld .atelier fájlként külső mappába.
2. A ZIP **AtelierPad/** mappájával frissítsd a repository azonos nevű mappáját, minden új .kt, .xml és tesztfájllal együtt. Ne legyen AtelierPad/AtelierPad/ egymásba ágyazás.
3. Cseréld le a gyökérben a **.github/workflows/android.yml** fájlt is. A korábbi main.yml-t ne hozd vissza.
4. **Actions > Atelier Pad 0.5 APK > Run workflow**, `install_mode = side-by-side`.
5. Sikeres fordításkor az **AtelierPad-0.5-debug** artifactban találod az app-debug.apk-t.

Ez külön **Atelier Pad 0.5** alkalmazást készít (`hu.atelierpad.app.preview05`), így nem kell törölni a 0.4-et. A régi galéria nem kerül át automatikusan; a .atelier fájlokat az új kezdőlapon importáld. Az update mód a régi `hu.atelierpad.app` csomagot célozza, nem a preview04-et. Azonos csomag helyben frissítéséhez továbbra is azonos aláírókulcs kell; az opcionális `ATELIER_DEBUG_KEYSTORE_BASE64` támogatása megmaradt. Magánkulcs nincs a csomagban.

A projektformátum továbbra is **v3**: a 0.3, 0.4 és 0.5 között kompatibilis. A 0.5 olvassa a régebbi v1/v2 fájlokat is. A már alkalmazott effektek képként a projektbe kerülnek; PNG-, JPEG- és PDF-exportban is megjelennek.

## Ellenőrzés

**138 platformfüggetlen Kotlin/JVM-teszt lefutott**: a korábbi 101 mellett 37 új teszt a függőleges csúszkák geometriájára, az új színkörre, a színkorrekciókra, az elmosásra, az effekt-visszavonásra és a mentési kompatibilitásra. A 49 XML-fájl, az ikonhivatkozások és a felület statikus szerkezeti ellenőrzése is megtörtént. A teszteket a kész ZIP kicsomagolása után is megismételtük.

**50 Android-eszközteszt forrása** van a projektben; ezek itt nem futottak le. Android SDK és Gradle hiányában a teljes APK-fordítást, az Android-felület megjelenítését és a Focus Pen-es próbát itt nem tudtuk elvégezni. A statikus ellenőrzés nem helyettesíti ezeket. A workflow lefordítja, de nem futtatja az eszközteszteket.

A renderelő továbbra is CPU-s bitmapalapú, hardveresen gyorsított View-megjelenítéssel, nem új Vulkan/OpenGL motor. Nincs még rétegmaszk, rétegcsoport, maszatolás, Liquify, PSD vagy Procreate-ecsetimport. Nem ígérünk új, méréssel nem igazolt készülékes teljesítményt.

Helyi magteszt: `bash tools/test-core.sh`

Statikus UI-ellenőrzés: `python3 tools/check-ui.py`

Eszközteszt SDK-val: `gradle -PatelierSideBySide=true :app:connectedDebugAndroidTest`
