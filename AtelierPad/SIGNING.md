# Development APK signing and safe updates

This is a debug build, not a Play Store release. Its applicationId remains
`hu.atelierpad.app`; versionCode is 2.

## Before installing

Export every important drawing from the old app as an external `.atelier`
file, preferably with a PNG copy too. Verify the exported files exist outside
the app's private storage. Keep the original v1 exports: v2 can read them,
but v1 cannot read a project saved by v2.

Try installing the new APK as an update first. Android requires an update to
have a compatible signing certificate. A new GitHub hosted runner can generate
a different debug signing key. If installation reports a signature conflict,
do not delete the old app until your external backups are complete. Uninstalling
removes the private gallery. After reinstalling, use Gallery > Open .atelier file.
The original private signing key cannot be recovered from an APK.

## Optional: keep the same debug key for subsequent CI builds

The workflow accepts one optional GitHub Actions repository secret:
`ATELIER_DEBUG_KEYSTORE_BASE64`. With no secret it still builds normally.

On a trusted local computer, use an existing development debug keystore, or
create one for future builds with JDK keytool:

```sh
keytool -genkeypair -keystore atelier-debug.keystore -storetype JKS \
  -storepass android -keypass android -alias androiddebugkey \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=Atelier Pad Development,O=Development,C=HU"
```

Base64-encode the file locally and copy the result to the repository secret.
For example, on Linux:

```sh
base64 -w 0 atelier-debug.keystore
```

On Windows PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path .\atelier-debug.keystore)))
```

Treat the key and its base64 representation as private. Do not commit them,
attach them to an issue, print them in CI logs, or put them in a shared Actions
cache. Base64 is encoding, not encryption. Keep a secure backup of the key.

The optional workflow step restores it at `$HOME/.android/debug.keystore`,
checks the alias and lets Android Gradle Plugin use the standard debug signing
configuration. The standard debug passwords above are only suitable for this
private development process. A public release needs a separate release-signing
strategy. Setting a new key now does not make it match a lost key from v1.

Reference: https://developer.android.com/studio/publish/app-signing
