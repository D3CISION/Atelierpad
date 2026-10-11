# Xiaomi Pad 7 + Focus Pen acceptance checks (0.2)

Status: these checks have NOT been run on a real device for this release.
Record device OS version, available RAM, canvas dimensions and brush settings
when reporting an issue. Export .atelier backups before installing a new APK.

## First launch and UI
- Install only after a successful Actions build; confirm Help / header says 0.2.
- Test portrait and landscape layouts. Every main pictogram should be legible,
  have a distinct active state and show its name on long press.
- Bottom size and opacity controls must work without accidental canvas marks.
- Open/close the side panel and confirm zoom is not reset.
- Confirm keyboard, dialogs and display cutouts do not cover critical actions.

## Brush feel (start at 1600 x 1200)
- Studio ink and Fine liner: slow curves, fast S-shapes, small loops, long lines.
- Compare Stabilization 0%, 30%, 60%; Pressure smoothing 0%, 35%, 70%.
- Check smooth pressure ramps, tiny taps, corners, start/end points and eraser.
- At 50% Stroke opacity / 100% flow, crossing the same hard-ink stroke should
  not repeatedly darken the interior. A second separate stroke should darken it.
- Test Graphite, Dry paint, Bristles, Soft airbrush and a transparent PNG tip.
- Undo and redo must reproduce the committed stroke. Saving, reopening and
  exporting must reproduce that result without jumps at the final point.
- Begin a pen stroke, rest/lift the palm, and ensure no unexpected zoom/undo.
- Cancel a gesture or interrupt the app; no uncommitted mark should remain.
- Two-finger pan/zoom/rotate; two-finger tap undo; three-finger tap redo.
- Switching brush and eraser should retain separate settings for this session.

## Layers and clipping
- Paint a base shape, add a layer above, enable clipping and paint past its edge.
- Test transparent holes, semi-transparent base pixels, base opacity and hiding.
- Move/scale/rotate the base and confirm the mask follows it.
- Test two clipped layers above the same base and layer reorder/undo.
- With no valid base below, a clipped layer should not appear unrestricted.
- Erasing/alpha lock must not affect other layers or the canvas background.
- Check all 11 blend modes, thumbnails and layer visibility.
- View flip/grayscale should not modify an exported PNG or PDF.

## Existing projects and exports
- Open a backed-up v1 .atelier. Compare with its v1 PNG before adding new marks.
- Add v2 strokes, save, reopen; compare the flattened result.
- Keep original v1 files: the old app cannot open newly saved v2 archives.
- Test PNG transparency, JPEG white background and each PDF paper choice.
- Open PDFs in an independent reader; check full-image fit, orientation, margins.
- Test local autosave, application restart and Gallery > Open .atelier file.

## Performance and resilience
- Start with default canvas; increase size/layers gradually, not all at once.
- Test 20 minutes of drawing, repeated undo/redo and repeated export.
- Watch for pauses, memory errors and disappearing layers; report exact settings.
- Do not assume the 32-layer / 12-million-pixel guardrails are smoothness promises.

Automated real-device coverage: nine test methods in RasterAndPdfTest.kt,
including raster erase, PDF, input cancel, whole-stroke opacity, clipping,
transformed masks, live commit/cancel, alpha lock and vector resource inflation.
