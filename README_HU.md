# Atelier Pad 0.3.0-alpha

**Android-forrásprojekt, nem kész APK.** A 0.2-es forrásra épül. A teljes Android-fordítás és a Xiaomi Pad 7-es kipróbálás ebben a környezetben nem történt meg. Az APK-t a mellékelt GitHub Actions workflow fordítja le.

## 1. Ujjal is állítható csúszkák

- A gyorssáv és a panelek csúszkái ugyanazt az új TouchSeekBar vezérlőt használják.
- Az ujj, toll és egér bevitelét nem szűri a csúszka. A "Stylus only on canvas" csak a vászonra vonatkozik.
- A csúszka az érintés kezdetén lefoglalja a húzást, hogy a vízszintes vagy függőleges görgető ne vehesse el.
- A panelcsúszkák 52 dp magasak; a gyorssáv célpontjai legalább 48 dp magasak.
- A számra koppintva pontos érték adható meg. A százalékos mezők százalékot kérnek, nem 0-1 közötti törtet. Tizedesvessző is használható.
- Az alsó gyorscsúszka nem szakítja meg szándékosan a folyamatban lévő tollvonást. A módosított ecsetbeállítás a következő vonástól érvényes.
- Az utoljára használt rajzecset és radír beállításai az alkalmazás háttérbe kerülésekor külön mentést kapnak.

A gyártó rendszer-szintű tenyérérintés-szűrése az alkalmazás előtt is eldobhat eseményeket. A toll közelségében végzett egyidejű ujjas kezelést ezért a tableten is ellenőrizni kell; a forráskód nem ígér ennek megkerülését.

## 2. Kijelölések

A bal oldali lasszóikon vagy az oldalsó panel negyedik füle nyitja meg a Selection eszközöket.

- Freehand lasso: szabadkézi kijelölés.
- Rectangle / Ellipse: téglalap vagy ellipszis a toll lenyomása és felengedése között.
- Select all / Invert selection / Deselect: minden kijelölése, megfordítás, megszüntetés.
- Az ecset, radír és kitöltés a kijelölt területen belül dolgozik. A kitöltés nem juthat át egy nem kijelölt sávon.
- Copy to new layer / Cut to new layer: az aktív réteg kijelölt képpontjainak másolása vagy kivágása új rétegre.
- Clear selected pixels: kijelölt képpontok törlése, visszavonhatóan.
- A kivágott réteget a meglévő Layer settings mezőivel lehet mozgatni, méretezni és forgatni.

A kijelölés az aktív réteg helyi koordinátáihoz tartozik, nem minden rétegre érvényes globális kijelölés. A másolás nem az összeolvasztott képet használja. A másolt réteg megőrzi a forrás réteg transzformációját, fedését és keverési módját. Törlés és kivágás előtt a rétegzárat és az alfa-zárat ki kell kapcsolni.

A kijelölés kerete munkamenet-adat: az alkalmazás bezárása után nem áll vissza. A már elkészült vonások kijelölési korlátjai viszont a projektben vannak, így újranyitáskor és exportkor is érvényesek. Nincs még kijelölés-hozzáadás, kivonás, lágy szél vagy közvetlen transzformációs fogantyú.

## 3. Megmaradt funkciók

A 0.2 ecsetmotorja, nyomássimítása, nyolc kezdő ecsetje, ecsetszerkesztője, piktogramjai, vágómaszkja és 11 keverési módja megmaradt. PNG-, JPEG-, PDF- és .atelier-export továbbra is van.

**PDF:** Export > PDF. Vászonméret, A4 vagy A3; képarányt tartó illesztés. Egyoldalas, összeolvasztott raszteres kép fehér alapon, nem réteges PDF vagy PDF/X.

## 4. Frissítés GitHubon

1. Exportáld a fontos rajzokat a régi alkalmazásból .atelier fájlként külső mappába, PNG-másolattal is.
2. A ZIP AtelierPad/ mappájával frissítsd a repository azonos nevű mappáját. Az új .kt és .xml fájlokat, valamint a teszt-fixture-t is töltsd fel. Ne legyen AtelierPad/AtelierPad/ egymásba ágyazás.
3. A repository gyökerében a .github/workflows/android.yml fájlt cseréld a csomagban lévőre. A régi main.yml-t ne hozd vissza.
4. Actions > Atelier Pad 0.3 APK > Run workflow. Az install_mode maradjon **side-by-side**.
5. Sikeres fordítás után az **AtelierPad-0.3-debug** artifactban találod az app-debug.apk fájlt. A BUILD_INFO.txt jelzi a telepítési módot.

A workflow a repó gyökerében vagy AtelierPad/ alatt is felismeri a Gradle-projektet. Nem elég csak a YAML-t cserélni.

## 5. Telepítés a régi alkalmazás törlése nélkül

Az alapértelmezett side-by-side mód külön alkalmazást készít: **Atelier Pad 0.3**, csomagnév: hu.atelierpad.app.preview03. Ez nem írja felül a hu.atelierpad.app csomagú 0.1/0.2 változatot, és nem fér hozzá annak belső galériájához. A rajzokat Gallery > Open .atelier file menüből importálhatod.

Az **update** mód a régi hu.atelierpad.app csomagnevet tartja meg. Csak akkor használd, ha a telepített alkalmazás aláírókulcsa rendelkezésre áll. A versionCode 3, de ez önmagában nem oldja meg az aláírásütközést.

A workflow továbbra is használja az ATELIER_DEBUG_KEYSTORE_BASE64 repótitkot, ha azt beállítottad. Ha nincs állandó kulcs, a következő 0.3-as build sem feltétlenül frissítheti helyben a mostanit. **A régi appot ellenőrzött külső projektmentés nélkül ne töröld.** Részletek: SIGNING.md. Magánkulcs nincs a csomagban.

## 6. Projektkompatibilitás

A 0.3 olvassa az 1-es és 2-es projektformátumot. A régi vonások eredeti ecsetmotor-verziója megmarad. A kijelölési korlátok miatt a 0.3 **3-as formátumban ment**, amit a 0.1/0.2 nem tud megnyitni. Az eredeti exportokat tartsd meg.

## 7. Ellenőrzés és korlátok

**82 platformfüggetlen Kotlin-ellenőrzés sikeresen lefutott.** Kijelölés-geometria, kitöltés, v1/v2/v3 mentés, törlés és visszavonás, csúszka-skálázás, valamint a 0.2 ecsetmotorjának korábbi tesztjei. Napló: tests/core-results.txt.

20 Android-eszközteszt van a projektben; ebből 11 új. Ezek **nem futottak le itt**, és Android SDK hiányában a teljes APK-fordítás sem történt meg. A workflow a teszteket csak lefordítja; emulátort nem indít. A .yml és XML statikus ellenőrzése nem helyettesíti a készülékes tesztet.

Továbbra sincs rétegmaszk, rétegcsoport, maszatolás, fizikailag szimulált festékkeverés, Liquify, PSD vagy Procreate-ecsetimport. A raszterezés továbbra is CPU-s, nem dedikált Vulkan/OpenGL motor. Nincs új készülékes teljesítményígéret.

Korlátok: oldalanként 4096 px, legfeljebb 12 millió pixel, 32 réteg, 600 000 tollminta, 20 000 művelet, 300 000 tárolt kijelölési csúcspont; egy kijelölés legfeljebb 2048 pont. Kitöltés legfeljebb 4 194 304 pixeles vásznon. Ezek védelmi korlátok, nem garantált sebességek.

Helyi teszt: bash tools/test-core.sh. Helyi APK: tools/build-apk.sh vagy tools/build-apk.ps1. A segédek alapértelmezése is side-by-side; ATELIER_PARALLEL=false esetén a régi csomagnévvel fordítanak. Eszközteszt: gradle -PatelierSideBySide=true :app:connectedDebugAndroidTest.
