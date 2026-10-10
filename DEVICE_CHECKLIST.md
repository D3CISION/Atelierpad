# Device acceptance checklist - NOT RUN

Target: Xiaomi Pad 7 / Xiaomi Focus Pen. Record HyperOS version, Android version, RAM, APK build hash and date.

## Build gate
- Run the real Gradle Android build and JVM tests.
- Compile and run RasterAndPdfTest on API 35 and the actual tablet.
- Do not label this release device-tested without recording results.

## Input
- Test light / medium / heavy pressure, fast curves and long continuous strokes.
- Rest the palm before, during and after drawing. No palm marks should survive.
- Cancel input by opening system navigation; incomplete strokes must be rolled back.
- Switch to the eraser; ensure only the active layer is erased.
- Stylus-only mode must ignore finger painting.
- Two-finger navigation and two-/three-finger tap undo/redo must not create strokes.
- Test small and large handwriting with stabilization disabled and enabled.

## Layers and appearance
- Compare visible layer blends on screen, PNG and PDF.
- Check alpha lock, visibility, opacity, reorder, duplicates, delete and undo.
- Test rotated/scaled layers and continue painting on them.
- Test drawing with split-screen, portrait, landscape and a hidden inspector.
- Verify native UI text, touch targets and dialogs are not clipped.

## Files
- Save a multi-layer .atelier file, terminate/relaunch, reopen and compare.
- Test transparent PNG, white-background JPEG and all three PDF page sizes.
- Open exported PDFs in two independent PDF viewers; confirm no crop or altered aspect ratio.
- Cancel each system file picker and repeat without losing the project.
- Simulate full storage and inaccessible output; the app must show an error, not success.
- Export an external backup before uninstalling or clearing app storage.

## Performance
- Start at 1600x1200 and four layers. Record stroke latency, frame drops and memory.
- Increase canvas and layer count gradually; do not infer performance from theoretical limits.
- Test at least a long drawing session with autosave enabled and repeated import/export.

Status: all device checks pending.
