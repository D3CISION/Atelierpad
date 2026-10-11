# Verification scope: 0.4.0-alpha

## Executed in this environment

- Native-free Kotlin/JVM production core compiled and 101 tests passed (82 retained + 19 new).
  New cases cover recent-color ordering/serialization, wheel geometry, all insertion gaps for
  layer ordering, active-layer identity, undo, clipping boundaries, header parsing and validation.
  Full output: core-results.txt. Genuine v1/v2 fixtures are retained.
- 41 XML files parsed; Kotlin drawable/action-ID references resolved locally; startup home,
  removed labels, toolbar order and version/package contracts checked. See resource-results.txt.
- Both workflow copies parsed, all inline Bash scripts syntax-checked, and root/nested project
  discovery fixtures checked. See workflow-results.txt.
- All production Kotlin and the new instrumentation source received an additional compile sanity
  check against LOCAL HANDWRITTEN API PLACEHOLDERS. This caught Kotlin syntax/type-shape mistakes,
  but does NOT establish compatibility with the real Android SDK, resource linking or execution.
  The placeholders and resulting throwaway jars are excluded from the deliverable.
- Tests and static UI checks repeated after unpacking the final ZIP; archive manifest verified.

## Not executed / not established

- The real Android Gradle/APK build, Android resource linking (aapt2) or actual SDK type checking.
- UI rendering, interactive drag/drop, pointer handling, lifecycle and device instrumentation.
- All 32 Android instrumentation tests (20 retained + 12 new). GitHub CI compiles device tests but
  does not boot an emulator or execute those tests. Unit tests are run by the workflow.
- Xiaomi Focus Pen end-to-end latency/pressure behavior or simultaneous pen/finger delivery.
- Successful installation, production signing or recovery of a previous lost signing key.

The container has Java and Kotlin but no Android SDK/Gradle installation; the SDK download host
was unreachable. There is no compiled APK in this archive. Static checks are NOT visual QA.

## New device test coverage (source, pending execution)

MinimalWorkspaceTest.kt: finger wheel completion/cancellation/brightness, color swatch target,
project names/dates and bounded thumbnails, unchanged and active-only save timestamps,
evicted snapshot timestamps, launcher home/build label, editor tool order/color popover,
and accessible layer movement. Long-press drag/autoscroll remains a required manual device test.

Run locally: bash tools/test-core.sh ; python3 tools/check-ui.py
Run with Android SDK/device: gradle -PatelierSideBySide=true :app:connectedDebugAndroidTest
See DEVICE_CHECKLIST.md before treating the alpha as ready for artwork without external backups.
