# Changes

## 0.4.0-alpha

- Replace permanent side inspector with canvas overlays/popovers and monochrome icon chrome.
- Exact upper-right order: Brush / Selection / Eraser / Layers / current color.
- Circular hue/saturation wheel plus brightness on translucent dark backdrop; no HEX UI.
- Up to 12 persisted recent colors; committed selections and painting update history.
- Bottom-left Project and Tools menus. Move Fill behind Tools, import/export behind Project.
- Retain finger-friendly Size/Opacity sliders, exact entry, and all earlier painting tools.
- Remove editor branding, info button and Clear layer action. Build label only on home.
- Layer long-press drag/drop with insertion indicator, edge autoscroll, undo and accessibility moves.
- More padding/gaps and compact action typography in Layer settings.
- Home is the cold-launch screen: cached image previews, names, last-edit dates and dimensions.
- Bounded header-only gallery metadata reads; lazy thumbnail rendering off the UI thread.
- Do not rewrite unchanged projects or advance edit time on active-layer-only changes.
- Preserve failed-save snapshots in the live session; metadata/cache failures never delete artwork.
- Keep portable project format v3 (compatible with 0.3). No brush engine or PDF export format changes.
- Default separate preview04 app. Original-package update remains an explicit option.
- 101 native-free JVM checks pass; 41 XML files and workflow statically checked.
- 32 Android instrumentation tests provided, including 12 new cases; not executed here.

## 0.3.0-alpha

- Finger/stylus native sliders acquire drag ownership on DOWN, before parent scroll interception.
- Larger slider targets; exact numeric entry; percentage units; comma decimal input.
- Quick controls do not deliberately terminate a live pen stroke. Changes apply to the next stroke.
- Explicit split input on layout containers. Pen-only restriction remains local to DrawingView.
- Persist the last paint and eraser brushes separately on pause.
- Layer-local lasso, rectangle and ellipse selection; invert, clear and select-all.
- Selection constraints in new stroke records; constrained fill with blocked outside connectivity.
- Undoable clear operation; copy/cut raw active-layer pixels into a new layer with transform preservation.
- Six extra hand-authored pictograms; reference window placed above the quick controls.
- Archive v3 with v1/v2 imports and a genuine v2 regression fixture.
- Workflow defaults to side-by-side package hu.atelierpad.app.preview03; update mode is explicit.
- 82 offline regression cases; 20 separate Android instrumentation tests (11 new, not run here).

## 0.2.0-alpha

Retained: continuous solid-ink geometry, pressure/position smoothing, separate flow/opacity,
clipping layers, pictogram toolbar, layer thumbnails, 11 blending modes and check views.
