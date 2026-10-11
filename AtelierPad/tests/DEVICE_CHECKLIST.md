# Required Xiaomi Pad 7 / Focus Pen checks for 0.4 (not performed here)

## New UI and home

1. Build the side-by-side APK. Old 0.3 and its gallery must remain. New label is Atelier Pad 0.4.
2. Cold launch and force-stop/relaunch: Projects home first, even after working on a canvas.
   Build label appears only on home. Empty gallery must offer New canvas and Open project.
3. Import v1/v2/v3 .atelier files. Home must show actual thumbnails, names, local edit date/time
   and dimensions; most recently edited first. Merely opening/closing must not change the date.
4. Rename/draw, then return home: title/date/preview must update. Select a layer without drawing:
   its active identity should save but edit date should not advance. Repeat with >3 projects.
5. Rotate home and editor; resize/multi-window. No hidden actions, lost zoom, clipped menus,
   fake side-panel space or returning editor branding. Test large accessibility font sizes.
6. Editor top-right, left-to-right: Brush, Selection, Eraser, Layers, Color. Home icon at left.
7. Open color: circular hue/saturation, Brightness, recent colors, eyedropper. No HEX or Studio
   palette. Dark backdrop translucent. Finger and pen work. Drag outside and cancel safely.
   Up to 12 recent unique swatches persist; simply opening the picker must not add a swatch.
8. Brightness zero and one, gray/white/black selection, hue wraparound, recent-swatch selection.
   Close outside/Back/X, then reopen: color and brightness agree. Picking a recent color refreshes
   the slider, not just the disk. No accidental painting through the scrim.
9. Bottom-left Project includes rename/import/export; Tools includes Fill and canvas options.
   No permanent Fill/import/export/info buttons. Closing a popover preserves canvas position.
10. Layers: long-press thumbnail/name, drag upward/downward and release on insertion marker.
    Autoscroll at both ends; cancel outside without reordering. Repeat active/inactive layers,
    one-layer document, 32 layers, hidden/locked/transformed/clipped layers. Undo/redo reorder.
    Single-layer dragging can change clipping bases; bottom layer cannot remain clipped.
11. Layer settings: separated action buttons, readable labels, 24dp content padding. No Clear
    layer. Duplicate/delete confirmation/reset/apply/cancel still work. Test keyboard visible.
12. Simulate save/cache failure in a disposable project: do not delete the archive. Errors must
    be visible; an unsaved snapshot should remain available in the live session. This is NOT a
    durable recovery guarantee after process death. Keep external backups for real drawings.

## Retained behavior regression

13. With Stylus only on canvas ON, drag Size/Opacity and all Brush/Layer/Fill sliders with a finger.
    Parent scrolling must not steal the drag. Exact numeric entry accepts comma decimal and
    percentage units. Test cancelled touch, then immediately retry with pen and finger.
14. Draw with pen while another finger moves a slider. Existing stroke keeps starting brush
    parameters; next stroke uses the change. System-level pen proximity filtering is device-
    dependent and must be distinguished from application scroll interception.
15. Compare Studio ink/Fine liner, opacity/flow, smoothing and preserved pen/eraser presets.
16. Lasso/rectangle/ellipse, invert/deselect, constrained paint/erase/fill, copy/cut/clear selection,
    transforms/clipping and undo/redo must match 0.3. Selection clear remains; Clear layer does not.
17. Export/import portable v3 and read it with 0.3. Compare original images; home sidecar metadata
    and recent colors are intentionally not inside the portable file. Also read v1/v2 fixtures.
18. Export PNG/JPEG and canvas/A4/A3 PDF through Project. Verify actual artwork, not UI overlays,
    appears. Transparency/white PDF background and aspect ratio should match previous versions.
19. Background/foreground, Back, new/import cancellation, numeric dialogs and rotate during
    gallery loading. No project loss, stuck blocking overlay or stale thumbnail updates.
20. Test 1600x1200 first, then 2048 square and several projects. Check memory and responsiveness;
    safety limits are not promises that every device will handle maximum canvases fluidly.

Run all 32 native tests on a connected emulator/device:
    gradle -PatelierSideBySide=true :app:connectedDebugAndroidTest

The source-generation environment did not execute these device checks or build the APK.
