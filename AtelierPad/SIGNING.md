# Development APK signing and safe installation

## Default in 0.5: side-by-side

The workflow and build helper scripts default to a separate app:
`hu.atelierpad.app.preview05`, displayed as **Atelier Pad 0.5**, versionCode=4.
This avoids overwriting an installed 0.1/0.2 (`hu.atelierpad.app`). It does not
share that app's private gallery. It also leaves the 0.3 preview03 app untouched. Transfer art via externally exported .atelier files.
No private signing key is included in this source archive.

## In-place update

The workflow's `install_mode: update`, or a direct Gradle build without
`-PatelierSideBySide=true`, uses `hu.atelierpad.app`, NOT the 0.3 preview03 package. This ONLY updates an old
installation when the signing certificate is compatible. Raising versionCode
does not fix a signing mismatch. A new key cannot recover a lost original key.

All subsequent builds of the separate 0.5 app ALSO need the same key to update
that 0.5 installation. Side-by-side is not a permanent signing-key solution.
Never uninstall a drawing app without checked external .atelier backups.
The file format stays at v3, compatible with 0.3. Keep original v1/v2 exports for 0.1/0.2.

## Optional stable CI debug key

Set the repository Actions secret `ATELIER_DEBUG_KEYSTORE_BASE64` to a base64
encoding of your own development debug keystore. The workflow restores it at
`$HOME/.android/debug.keystore`. Without the secret it builds with a freshly
generated runner debug key and prints a warning. An existing installation's
private key cannot be extracted from its APK.

Create a key once on a trusted local computer, or use the existing key if you
have it. Keep an offline private backup. For a new development key with JDK:

```sh
keytool -genkeypair -keystore atelier-debug.keystore -storetype JKS \
  -storepass android -keypass android -alias androiddebugkey \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=Atelier Pad Development,O=Development,C=HU"
```

Linux encoding:
```sh
base64 -w 0 atelier-debug.keystore
```

Windows PowerShell encoding:
```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path .\atelier-debug.keystore)))
```

Do not commit, send in chat, attach to an issue, log or publicly cache the key
or its base64 representation. Base64 is not encryption. The conventional debug
passwords above are not a production release-signing strategy.

Official reference: https://developer.android.com/studio/publish/app-signing
