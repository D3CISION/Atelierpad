# Xiaomi Pad 7 / Focus Pen checklist - 0.5

Back up external .atelier files first. Install the side-by-side APK and import copies.

1. Cold launch: Projects home, name/thumbnail/last-edited time; build 5 only on home.
2. Portrait and landscape: Gallery, wrench Tools, Effects on top left; Brush, Selection,
   Eraser, Layers, Color on top right. Project remains bottom left. No bottom slider bar.
3. Left rail: upper size, outlined-square Eyedropper, lower opacity, Undo, Redo. Drag with
   finger while stylus-only canvas is on. Increasing goes upwards, bubble follows the thumb.
   Long press opens numeric entry. Test near top/bottom and while the pen is hovering/drawing.
   System palm rejection may block simultaneous delivery; report device/OS behavior separately.
4. Confirm in-flight strokes keep their initial brush; next stroke adopts changed size/opacity.
   Check pen, finger, keyboard and accessible progress changes; cancel restores slider value.
5. Eyedropper enters one-shot picker; Undo/Redo are disabled when no applicable history exists.
6. Brush and eraser libraries: dark tiles, white strokes independent of selected color, no
   Size/Opacity controls. Selecting a preset preserves that tool's current size/opacity.
7. Color: hue ring + inner SV disc; white/black/pure hue reachable. No scrolling, HEX, brightness
   slider or eyedropper. Ring gestures never switch into disc gestures halfway through. Cancel
   restores old color. Recent colors persist and do not move/resize the wheel when added.
8. Selection: no visible text, just icons. Lasso/rectangle/ellipse in one row. Invert, deselect,
   copy/cut below. Disabled controls without a selection; no Select all/Clear actions.
9. Open a reference, then a color/effects panel. The panel/scrim must be above the reference and
   rail, and dismiss by outside tap. Closing does not reset canvas zoom or pan.
10. Effects: preview updates without modifying the canvas; holding preview shows Before.
    Cancel/X/outside tap/Back keep project unchanged. Apply changes only the active layer once;
    Undo restores original strokes, Redo restores result. Check hue/saturation/tones/blur.
11. Test selected and inverted-selected effects, transformed and clipped layers, layer opacity
    and blend modes. Thumbnail is local-layer preview, not the final composite.
12. Alpha-safe blur: white paint fading into transparency must not acquire colored/dark halos.
    Test 64x64, 1600x1200, 2048x2048; larger canvases show the explicit effect limit.
13. Reject effects on hidden/locked/alpha-locked layer. Close/reopen panel rapidly during preview;
    rotate or background app during processing. Never apply stale preview to a different project.
14. Save/reopen/export .atelier, PNG, JPEG and PDF after applying effects. Import v1/v2/v3 files;
    v3 remains readable in 0.3/0.4. Session undo is not expected to survive a restart.
15. Recheck layer long-press drag/reorder/auto-scroll, two-finger viewport gestures and pressure.

Instrumented tests: gradle -PatelierSideBySide=true :app:connectedDebugAndroidTest
CI compiles instrumented tests, but does not execute them. Do not treat this checklist as passed.
