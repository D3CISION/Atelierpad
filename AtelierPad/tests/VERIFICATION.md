# Verification scope: 0.3.0-alpha

## Executed here

- Compile platform-independent core + tests with local Kotlin/JVM compiler.
- Execute 82 regression cases on the JVM, including genuine v1 and v2 archives,
  v3 selection/clear serialization, selection scan conversion, constrained flood
  fill, undo and slider scale mappings. Full output: core-results.txt.
- XML parse and local drawable-reference checks: resource-results.txt.
- YAML parse, shell syntax and root/subfolder discovery fixtures: workflow-results.txt.
- Repeat core test execution after creating and extracting the delivery ZIP.

## NOT executed / NOT established here

- Android Gradle build or Android API type-check of the complete app.
- Android resource compilation/linking or emulator rendering.
- All 20 instrumentation tests. CI compiles them but does not run a device.
- Xiaomi Focus Pen pressure behavior, end-to-end input latency, palm rejection,
  simultaneous pen/finger delivery and GPU/display results.
- Production/release signing, original-key recovery or successful APK install.

There is no Android SDK or Gradle installation in this environment. The SDK
host was not reachable from the build container. A dependency-less Kotlin
parse pass is not counted as an Android build or type-check.

20 instrumentation tests: 9 previous raster/PDF/input cases, 6 new touch/icon
cases, 5 new selection raster/clear/transform/live-replay cases. See DEVICE_CHECKLIST.md.
