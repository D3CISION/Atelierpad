# Changelog

## 0.2.0-alpha (source release)

- New versioned brush engine: timestamp-aware adaptive position smoothing,
  separate pressure filtering, midpoint quadratic curves, distance-resampled
  dabs and exact endpoint flushing.
- Hard ink strokes use continuous antialiased variable-width geometry.
- Separate per-dab flow from whole-stroke opacity; live stroke isolation and
  layer-safe erase / alpha-lock composition.
- Cache per-stroke tools and preview images. Request unbuffered stylus dispatch.
- Native vector pictogram toolbar, tooltips and accessibility labels; bottom
  quick size / opacity controls; visual brush library with eight presets.
- Separate drawing and eraser brush state in the current session.
- Clipping layers based on the nearest lower unclipped layer, including base
  visibility, opacity and transforms. Layer thumbnails.
- Six additional blend modes: Soft Light, Darken, Lighten, Difference, Color,
  Luminosity; eleven modes in total.
- View-only horizontal flip and grayscale checking. Hiding the inspector keeps
  the current canvas zoom.
- v2 archive reader retains v1 compatibility; v1 strokes keep the frozen legacy
  renderer and original dab positions. New saves are archive version 2.
- Existing PNG/JPEG/PDF/project exports, local autosave and gallery retained.
- 59 standalone core regression checks; v1 golden fixtures; 9 Android tests
  provided but not run here. CI runs core regressions as JUnit tests too.
- Working root/nested Gradle project discovery retained in GitHub Actions.
  Optional stable debug signing secret and always-uploaded test reports.

This is not a full GPU paint engine. No device latency or frame-rate guarantee
is made. The full Android build and on-device appearance still require testing.
