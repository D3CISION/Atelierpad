# Changes

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
