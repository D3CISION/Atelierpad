# Atelier Pad 0.2 - frissítés

A ZIP forráskód, nem APK. Először exportáld a fontos rajzokat .atelier fájlként.

Frissítsd a repó **AtelierPad/** mappáját a ZIP azonos mappájával, minden új Kotlin-, XML- és tesztfájllal. A gyökérben cseréld a **.github/workflows/android.yml** fájlt. Ne hozz vissza egy második main.yml workflow-t. Nem elég csak az YML cseréje.

Commit, majd Actions > Atelier Pad 0.2 APK > Run workflow. Sikeres fordításkor az APK az AtelierPad-0.2-debug artifactban lesz.

Az új debug aláírás miatt előfordulhat, hogy újratelepítés szükséges. Eltávolítás előtt legyen ellenőrzött külső mentésed: a helyi galéria törlődik.

Részletek: AtelierPad/README_HU.md. 59 Kotlin-alapellenőrzés lefutott, de az APK-fordítás és a tabletes teszt itt nem történt meg.
