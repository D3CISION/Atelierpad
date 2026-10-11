# Atelier Pad 0.4.0-alpha - minimalista munkafelület

**Teljes Android-forrásprojekt, nem előre lefordított APK.** A 0.3-as alkalmazás folytatása. A módosított forrás ebben a csomagban van; GitHub-tárolót nem módosítottunk közvetlenül. A teljes Android-fordítást és a Xiaomi Pad 7-es próbát itt nem lehetett elvégezni.

## 1. Új szerkesztőfelület

Nincs állandó jobb oldali panel. A rajzfelület mellett csak a felső ikonsor és az alsó gyorsvezérlők maradnak; az eszközök beállításai ideiglenes, bezárható ablakokban nyílnak meg. A panelek nem veszik el a vászon szélességét és nem állítják vissza a nagyítást.

A felső sor jobb oldalán balról jobbra: **ecset, kijelölés, radír, rétegek, aktuális szín**. A szín álló és fekvő helyzetben is a jobb felső sarokban van. Bal felül csak a kezdőlapra visszavezető ikon maradt.

- Az ecsetikon megnyitja az ecsetválasztót; ott a **Brush settings** nyitja a részletes szerkesztőt.
- A radír első megnyomása eszközt vált; az aktív radír újabb megnyomása a beállításait nyitja.
- Az ablakon kívüli érintés, a bezárás ikon és a Vissza bezárja az ideiglenes ablakot.
- A szerkesztőből eltűnt az Atelier Pad / Illustration Studio felirat, az infógomb és a buildszám.
- A felület szövege továbbra is angol. A színek semleges, sötét szürke tónusúak.

## 2. Kör alakú színválasztó

A jobb felső színkörre koppintva áttetsző sötét hátterű ablak nyílik. A korongon az irány adja a színárnyalatot, a középponttól mért távolság a telítettséget. Alatta **Brightness** csúszka szabályozza a világosságot. Ujjal és tollal is kezelhető.

Nincs HEX-mező és nincs Studio palette felirat. Helyette legfeljebb **12 legutóbbi szín** jelenik meg. A választás és a tényleges festés frissíti a listát; a puszta ablaknyitás nem. A színelőzmény helyileg, alkalmazásszinten megmarad. Első indításkor a lista üres. Pipetta is elérhető ebből az ablakból.

## 3. Bal alsó menük és ujjas csúszkák

**Project:** kezdőlap, új vászon, átnevezés, .atelier megnyitása, kép/referencia/ecsetfej import, export.

**Tools / három pont:** Fill, pipetta, vászonmozgatás, képernyőhöz igazítás és rajzolási segédek. A kitöltés innen választható, nem foglal külön helyet a fő eszközsoron.

A **Size / Opacity** gyorscsúszkák alul maradnak, ujjal is állíthatók a Stylus only on canvas mellett. A számra koppintva pontos érték írható be. Szűk kijelzőn az alsó sáv két sorra rendeződik. A tollra korlátozás csak a rajzvászonra vonatkozik; a rendszer tollközelségi érintésszűrését nem kerüli meg.

## 4. Rétegek

Hosszan tartsd lenyomva a réteg bélyegképét vagy nevét, majd húzd fel vagy le. Beszúrási jel mutatja az új helyet, a lista szélén automatikus görgetés segít. A művelet visszavonható, az aktív réteg azonossága megmarad. Az akadálymentes műveletekben külön fel/le mozgatás is elérhető.

A **Layer settings** elemei több térközt és 24 dp belső margót kaptak. Az akciógombok kisebb betűsek, különállóak, 48 dp magas érintési felülettel. A **Clear layer** gomb nincs a felületen. A kijelölt képpontok törlése a kijelölő eszköz része maradt.

Egyszerre egy réteg mozgatható; rétegcsoportok még nincsenek. Vágómaszkos réteg mozgatása megváltoztathatja az alatta lévő bázist. Legalulra húzva a vágómaszk kikapcsol, mert alatta nincs bázis.

## 5. Kezdőlap és projektáttekintés

Hidegindításkor mindig a **Projects** kezdőlap jelenik meg, nem a legutóbbi vászon. Minden projekt kártyán szerepel a valódi képelőnézet, név, utolsó mentett szerkesztés dátuma/időpontja és pixelmérete. A legutóbb szerkesztett van elöl. A buildszám kizárólag itt látható: **0.4.0-alpha / build 4**.

Az előnézetek a kezdőlap megnyitásakor, háttérszálon készülnek és gyorsítótárat használnak; nem minden ecsetvonásnál. A puszta megnyitás és aktív réteg váltása nem számít új szerkesztésnek. A dátum az eszköz helyi formátumában jelenik meg.

A dátum helyi projektadat. Importnál az új helyi másolat mentési ideje jelenik meg; a korábbi alkalmazás eredeti szerkesztési dátumát a .atelier v3 nem tartalmazza. Hibás előnézet esetén a projektfájlt nem töröljük. Sikertelen mentéskor a még nyitott munkamenetben helyreállítható másolat marad; ez nem helyettesíti a tartós, külső mentést.

## 6. Megmaradt rajzolási funkciók és formátumok

Az ecsetmotor, a tollnyomás-simítás, az ecsetszerkesztő, a rétegek/keverési módok, a vágómaszk és a 0.3-as kijelölések megmaradtak. Ez a verzió elsősorban kezelőfelületi frissítés, nem új GPU-motor.

**Export:** Project > Export > PNG / JPEG / PDF / .atelier. PDF-nél továbbra is vászonméret, A4 vagy A3 választható. A PDF egylapos, összeolvasztott raszterkép fehér alapon, nem réteges szerkesztőfájl.

A projektformátum **változatlanul v3**: a 0.4 olvassa a v1/v2/v3 fájlokat, a 0.3 pedig meg tudja nyitni a 0.4-ben mentett v3 projektet. A 0.1/0.2 nem tud v3-at olvasni. Az eredeti exportokat őrizd meg. Az előnézetek és színelőzmények nem kerülnek a hordozható projektformátumba.

## 7. Fordítás és telepítés

1. Mentsd ki a fontos rajzokat a régi alkalmazásból külső .atelier fájlként, PNG-másolattal is.
2. A ZIP **AtelierPad/** mappájával frissítsd a tároló azonos nevű mappáját, minden új .kt, .xml, eszköz és tesztfájllal együtt. Ne legyen AtelierPad/AtelierPad/ egymásba ágyazás.
3. A tároló gyökerében cseréld a **.github/workflows/android.yml** fájlt is. A régi main.yml workflow-t ne hozd vissza. Nem elég csak a YAML-t frissíteni.
4. **Actions > Atelier Pad 0.4 APK > Run workflow**, `install_mode = side-by-side`.
5. Sikeres fordításnál az **AtelierPad-0.4-debug** artifactban van az app-debug.apk. A BUILD_INFO.txt tartalmazza a forráscommitot és a telepítési módot.

Az alapértelmezés külön **Atelier Pad 0.4** alkalmazás, csomagnév: `hu.atelierpad.app.preview04`. A régi 0.3/0.2 mellé települ, azok belső galériájához nem fér hozzá. A korábbi rajzokat a kezdőlap **Open project** lehetőségével, vagy a felső importikonnal töltheted vissza.

Az `update` mód a régi `hu.atelierpad.app` csomagot célozza, **nem a preview03-at**. A 0.4 azonos csomagú későbbi frissítéseihez is állandó aláírókulcs kell. A workflow továbbra is használja az `ATELIER_DEBUG_KEYSTORE_BASE64` titkot, amennyiben beállítottad. Magánkulcs nincs a csomagban. Ellenőrzött külső mentés nélkül ne töröld a régi alkalmazást. Részletek: SIGNING.md.

## 8. Ellenőrzés és korlátok

**101 platformfüggetlen Kotlin/JVM-teszt lefutott**, továbbá 41 XML-fájl, a helyi ikonhivatkozások, a felső eszközsorrend és a workflow statikus ellenőrzése is. A teljes szállított csomag tesztjeit kicsomagolás után megismételtük. A naplók a tests/ mappában vannak.

**Nincs itt elvégzett teljes Android/Gradle-fordítás vagy APK.** Nincs telepített Android SDK, a letöltési végpont a konténerből nem volt elérhető. A külön végzett Kotlin típusellenőrzés helyi API-helyettesítőket használt, nem az Android SDK-t; nem igazol erőforrás-linkelést, valódi API-kompatibilitást vagy futási viselkedést. Ezek a helyettesítők nincsenek a csomagban.

**32 Android-eszközteszt** forrása szerepel a projektben, köztük 12 új színválasztó/galéria/felület-ellenőrzés. Ezek itt nem futottak le. A workflow lefordítja, de nem futtatja az eszközteszteket. A hosszú nyomásos réteghúzást, álló/fekvő elrendezést, Focus Pen-es és ujjas kezelést valódi eszközön kell ellenőrizni. Lásd tests/DEVICE_CHECKLIST.md.

Változatlan védelmi korlátok: 4096 px oldalhossz, 12 millió pixel, 32 réteg, 600 000 tollminta, 20 000 művelet; kitöltés legfeljebb 4 194 304 pixeles vásznon. Ezek nem teljesítményígéretek. Továbbra sincs rétegmaszk, rétegcsoport, maszatolás, Liquify, PSD vagy Procreate-ecsetimport.

Helyi alapteszt: `bash tools/test-core.sh`

Felület statikus ellenőrzése: `python3 tools/check-ui.py`

APK fordítás: `tools/build-apk.sh` vagy `tools/build-apk.ps1` (Android SDK/Gradle szükséges).

Eszközteszt: `gradle -PatelierSideBySide=true :app:connectedDebugAndroidTest`.
