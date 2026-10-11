# Atelier Pad 0.3 frissítés

A teljes AtelierPad/ projektmappát frissítsd, az új fájlokkal együtt.
A .github/workflows/android.yml helyére a mellékelt android.yml kerüljön.
Csak egy aktív workflow maradjon; a régi main.yml ne kerüljön vissza.

Actions > Atelier Pad 0.3 APK > Run workflow > install_mode: side-by-side.
Az app a régi mellé települ "Atelier Pad 0.3" néven. Nem kell a régit törölni.
A korábbi galériát nem veszi át automatikusan: export/import .atelier fájllal.

APK artifact: AtelierPad-0.3-debug. A csomag jelenleg forráskód, nem APK.
Részletes útmutató: AtelierPad/README_HU.md.
