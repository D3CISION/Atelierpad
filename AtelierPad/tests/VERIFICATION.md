# Verification record: 0.2.0-alpha

## Executed in this environment

1. `bash tools/test-core.sh`: 59 platform-independent Kotlin regression checks
   passed, using Kotlin 1.9.0 / OpenJDK 21. Output: `core-results.txt`.
   The app's Gradle compiler remains Kotlin 2.1.20 / JVM target 17.
2. A synthetic archive and golden stamp list were generated using untouched
   v1 source. The new reader and frozen legacy engine reproduce that list.
   These fixtures are under `fixtures/`; they contain no user artwork.
3. Android main XML well-formedness and drawable-reference checks passed.
   All 29 toolbar path sets were rasterized separately for visual inspection.
   This does not substitute for Android resource inflation. See `resource-results.txt`.
4. Core and RasterRenderer were compiled against minimal, local API doubles
   to catch Kotlin-level errors. The doubles are NOT the Android SDK, were not
   executed, and are not included in the distributable source. This was only
   a partial source check, not a full app build.
5. Release YAML was parsed, embedded bash scripts syntax-checked, and Gradle
   root detection tested with repository-root and AtelierPad/ layouts.
   This is not an actual GitHub Actions run. See `workflow-results.txt`.

## Not executed here

- Android Gradle Plugin build, packaging, real Android API type checking.
- Android unit tests under the real Gradle test runner.
- The nine Android instrumentation test methods. The supplied Actions workflow
  COMPILES the device-test APK; it does not run an emulator.
- Xiaomi Pad 7 / Focus Pen rendering, pressure feel, palm rejection, latency,
  frame rate, battery use or memory stress.
- Actual Android PDF creation/opening or Android pixel-compositing tests.

No Android SDK or Gradle distribution was available locally; SDK/download
endpoints could not be reached from the execution container. No APK is included.

## Reproduce

Standalone core suite, from the Gradle project directory:

```sh
bash tools/test-core.sh
```

With Android SDK 35, Build Tools 35.0.0 and Gradle 8.11.1 / JDK 17:

```sh
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
```

With a connected test device or configured emulator:

```sh
gradle --no-daemon :app:connectedDebugAndroidTest
```

The normal unit-test source set also includes `tests/CoreTests.kt` and the two
v1 fixtures, so the full 59-check regression suite runs in CI as a JUnit test.
See DEVICE_CHECKLIST.md for manual checks before relying on the new build.
