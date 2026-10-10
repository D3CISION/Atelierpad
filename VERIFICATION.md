# Verification record

## Executed in the code-generation container
- Kotlin/JVM core tests: 28/28 passed (see core-results.txt).
- Full main-source Kotlin type/syntax compilation using 71 local compile-only Android API shims: passed. This is NOT compilation against the real Android SDK. The shims and generated check JAR are intentionally not included in the app.
- XML parsing and shell syntax checks: performed at packaging.

## Not executed
- Android Gradle build, D8, resource linking, APK signing.
- Android JUnit / instrumentation tests.
- Real PdfDocument export followed by PDF rendering. Only PDF geometry was executed on JVM.
- Native UI screenshot/layout verification.
- Xiaomi Pad 7 / Focus Pen pressure, latency or palm rejection testing.
- Bootstrap downloads and GitHub Actions workflow.

No APK is included. The source archive must not be represented as an installed, fully tested or Procreate-equivalent application.
