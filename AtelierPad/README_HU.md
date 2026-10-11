# Atelier Pad 0.2.0-alpha

**Android-forrásprojekt, nem kész APK.** A 0.1-es alkalmazás már futott nálad, de a mostani verzió teljes Android-fordítása és tabletes tesztje itt nem történt meg. Az APK-t a mellékelt GitHub Actions workflow készíti el.

## Mi változott?

### Ecsetmotor

Időbélyeges tollminták, sebességhez igazodó pozíciósimítás, külön nyomássimítás, másodfokú görbék és egyenletesebb mintaközök. A vonás a toll felengedésekor a tényleges végpontra zárul.

A kemény **Studio ink / Fine liner** ecset élsimított, összefüggő geometriát rajzol felnagyított kör-bitmapok helyett. A dokumentum továbbra is raszteres, nem vektorgrafika.

Külön **Stroke opacity** (a teljes vonás fedése) és **Paint flow** (festékáramlás). A fedés csak egyszer kerül a teljes vonásra: ugyanazon félig átlátszó tusvonás önmetszése nem sötétedik fel 100%-ra. Külön vonások egymásra festhetők.

Külön élő vonásfelület, kevesebb lenyomatonkénti objektumlétrehozás, nyolc ecsetpreset képes előnézettel. A rajzecset és a radír külön beállítást tart meg az aktuális munkamenetben.

### Piktogramok és illusztrációs eszközök

- Vektoros ikonok a fő eszközökhöz; hosszú nyomásra felirat, akadálymentes megnevezés, 48 dp érintési terület. A felület szövege egyelőre angol.
- Alsó gyors ecsetméret-, fedés- és színvezérlő.
- **Clipping mask / vágómaszk:** az alatta lévő legközelebbi nem vágómaszkos réteg formáján belül jelenik meg. A bázis láthatósága, alfája, fedése és transzformációja is számít. Az elrejtett bázis a hozzá vágott rétegeket is elrejti.
- Réteg-bélyegképek, 11 keverési mód. Új: Soft light, Darken, Lighten, Difference, Color, Luminosity.
- Ideiglenes vízszintes nézettükrözés és szürkeárnyalatos ellenőrzés; az exportot nem változtatják meg.
- A panel elrejtése nem állítja vissza a nagyítást.

## Mentés és PDF

Megmaradt a PNG-, JPEG-, PDF- és szerkeszthető .atelier export, az automatikus helyi mentés, a galéria és a korábbi eszközök.

**PDF:** Export > PDF, vászonméret, A4 vagy A3, képaránytartó illesztés. Egyoldalas, összeolvasztott raszteres kép fehér alapon; nem PDF/X és nem réteges munkafájl.

A 0.2 beolvassa a v1 .atelier fájlokat. A régi vonások továbbra is a befagyasztott LegacyBrushEngine motort használják, ezért nem kerül rájuk utólag az új simítás. Az új vonások v2-esek. **A v2-ben mentett projektet a 0.1 nem tudja visszaolvasni.** Az eredeti exportokat tartsd meg.

## Frissítés GitHubon

1. A régi alkalmazásban exportáld a fontos rajzaidat **.atelier** fájlként, lehetőleg PNG-másolattal is.
2. A ZIP **AtelierPad/** mappájával frissítsd a repó azonos nevű projektmappáját. Minden új .kt, ikon .xml és tests/fixtures fájl is szükséges. Ne legyen AtelierPad/AtelierPad/ egymásba ágyazás.
3. A repó gyökerében cseréld a **.github/workflows/android.yml** fájlt a ZIP azonos elérésű fájljára. A korábbi hibás main.yml workflow-t ne hozd vissza.
4. Commit changes, majd Actions > **Atelier Pad 0.2 APK** > Run workflow. Sikeres fordítás után az **AtelierPad-0.2-debug** artifactban jelenik meg az APK.

**Nem elég csak az android.yml-t frissíteni.** Ha a Gradle-projekted közvetlenül a repó gyökerében van, az AtelierPad mappa tartalmát oda másold: a workflow mindkét elrendezést felismeri.

### Telepítés és adatbiztonság

Az applicationId változatlan: hu.atelierpad.app, versionCode=2. A helyben frissítéshez azonos aláírás kell. A friss GitHub runner új debug kulcsot generálhat, ezért a telepítés aláírás-ütközéssel elutasítható.

**Az alkalmazást csak ellenőrzött külső projektmentés után távolítsd el.** Eltávolításkor a helyi galéria törlődik. Az exportokat a Gallery > Open .atelier file menüben lehet visszaimportálni. A változatlan jövőbeli debug kulcshoz opcionális repótitok használható; lásd SIGNING.md.

## Ellenőrzés

**59 platformfüggetlen Kotlin-ellenőrzés lefutott.** Mentés, v1/v2 kompatibilitás, régi vonások lenyomatlistája, simítás, nyomás, fedés/flow, végpontok, clipping metaadat, kitöltés és PDF-lapméretezés. Napló: tests/core-results.txt. A Gradle JVM-tesztjei is futtatják ezt az ellenőrzéssort.

A 32 XML-fájlt, az ikonhivatkozásokat és az ikonok külön kirajzolását ellenőriztem. A mag és RasterRenderer minimális, csak fordításra szolgáló API-helyettesítőkkel Kotlin-típusellenőrzésen is átesett. Ezek nem valódi SDK-osztályok és nincsenek a csomagban.

**A teljes APK-fordítás, a felület, a 9 Android-eszközteszt, a kompozitálás és a Focus Pen rajzérzete itt még nincs ellenőrizve.** A workflow az eszközteszteket csak lefordítja, nem futtat emulátort. Részletek: tests/VERIFICATION.md és tests/DEVICE_CHECKLIST.md.

Helyi alaptesztek: `bash tools/test-core.sh`. Készülékes tesztek: `gradle :app:connectedDebugAndroidTest`.

Első próbához Studio ink vagy Fine liner, 100% Stroke opacity, 100% Paint flow, 25-40% Stabilization, 30-50% Pressure smoothing javasolt. Ez kiinduló beállítás, nem méréssel meghatározott kalibráció.

## Korlátok

Továbbra is alpha: CPU-s bitmaprétegek hardveresen gyorsított Android View-val, nem dedikált Vulkan/OpenGL festőmotor. Nincs még rétegmaszk, lasszó, rétegcsoport, maszatoló, fizikai pigmentkeverés, Liquify, PSD vagy Procreate-ecsetimport. A toll dőlése és gyártóspecifikus gombjai nem használt funkciók.

A védelmi korlátok maradnak: 4096 px oldalhossz, legfeljebb 12 millió pixel, 32 réteg, 600 000 pont, 20 000 művelet. A kitöltés legfeljebb 4 194 304 pixeles vásznon használható. Ezek nem teljesítményígéretek. A referenciakép elrendezése és az undo-előzmény nem része a projektmentésnek. Az alkalmazás nem kér internetengedélyt vagy fiókot.
