# Verification scope: 0.5.0-alpha

## Executed

- Production platform-free Kotlin core compiled with kotlinc; 138 regression cases passed.
  101 existing + 37 new cases cover slider geometry, full-range ring/disc mapping, immutable
  color adjustments, premultiplied-alpha Gaussian approximation, pixel bounds, effect bake
  metadata, undo/redo and v3 archive compatibility. See core-results.txt.
- 49 XML files parsed; drawable/ID references, top-left and top-right tool order, icon-only
  selection, white brush previews, removed controls, startup gallery and version/package
  contracts statically checked. See resource-results.txt.
- All 30 Kotlin files parsed with the Kotlin compiler PSI parser (syntax only, without
  Android type/API resolution). See kotlin-syntax-results.txt.
- Workflow YAML and embedded Bash syntax checked. Root/nested/missing-project discovery
  fixtures executed. See workflow-results.txt.
- Regression tests and UI checks repeated on files extracted from the final ZIP.

## Not executed / not established

- No full Android SDK/Gradle build or APK, aapt2 resource linking, Android API type check,
  Android UI screenshot test, emulator, installed-app test or device profiling in this environment.
- 50 Android instrumented test methods are present (32 existing, updated for changed UI,
  plus 18 new in StudioWorkspaceTest.kt). They have not run here. CI compiles them, but does
  not boot an emulator. JVM core tests are run by CI.
- Focus Pen and finger coexistence, real input delivery, actual display density, palm rejection,
  pixel antialiasing, overlay z-order and orientation/multi-window layout require real-device tests.
- No promise of signature compatibility with any previously installed APK.

The environment provides Java/Kotlin, but no Android SDK or Gradle. The SDK download host was
not resolvable from the container. There are no mock Android APIs or private signing keys in
this archive. Static UI validation is not visual QA.

## Effect scope

Effects are active-layer raster edits with temporary, local-layer thumbnail previews.
Applying creates one replacement image operation and one undo entry. They are NOT persistent
adjustment layers or editable filter parameters. Selection is clipped in layer-local coordinates;
preview coordinates and blur sigma are scaled to document pixels. Local history is session-only.
Effects are bounded to 4,194,304 canvas pixels and reject hidden/locked/alpha-locked layers.

Run: bash tools/test-core.sh ; python3 tools/check-ui.py
With SDK/device: gradle -PatelierSideBySide=true :app:connectedDebugAndroidTest
