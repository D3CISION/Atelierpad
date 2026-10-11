# Required Xiaomi Pad 7 / Focus Pen checks for 0.3

1. Build and install the side-by-side APK. Verify old app and old gallery still exist.
2. Leave Stylus only on canvas ON. Drag Size and Opacity with a finger, both slowly
   and rapidly, in the lower quick bar and the Brush panel. The bar must not scroll
   instead of changing a slider. Scroll by dragging gaps or non-slider text.
3. Test every Brush Studio slider, Hue, Layer opacity/transform and Fill tolerance.
   Drag with the pen too. Cancel an input, then immediately drag again.
4. Tap a displayed number. Enter an in-range value, a comma decimal, and an invalid
   value. Percentage fields must accept percentages. Keyboard/Back must dismiss safely.
5. Paint with the pen while touching a slider with the other hand. Existing stroke
   should keep its starting brush parameters. The NEXT stroke uses the new setting.
   If HyperOS suppresses finger events near the pen, repeat after lifting the pen;
   record this separately from scroll interception. Do not claim software can bypass it.
6. Close/reopen app and compare paint/eraser settings. Switch between them repeatedly.
7. Draw lasso, rectangle and ellipse. Switch to Brush. Paint across their boundary;
   outside pixels must not change. Repeat with eraser, inverted selection and fill.
8. Select all; deselect. Cancel a new selection with two-finger navigation; old selection
   should remain. Verify layer-local behavior when switching active layers.
9. Copy selection, then cut selection. Check original/derived layer pixels, ordering,
   opacity, blend modes, transform and interaction with clipping layers. Undo/redo both.
10. Clear selection, undo, redo. Locked and alpha-locked layers must reject destructive
    cut/clear. Empty and degenerate selections must not crash.
11. Save/import a v3 project containing selected strokes and clear operations. Compare
    its appearance to the open canvas. Import genuine v1/v2 fixtures too.
12. Export PNG, JPEG, canvas-size PDF, A4 PDF and A3 PDF. Check selection is NOT baked
    as a dashed overlay and exported pixels match the painted result.
13. Rotate the tablet with a selection or numeric dialog open. Minimize and restore.
14. Stress 1600x1200 first, then 2048 square. Watch memory and latency without claiming
    the 4096/12M-pixel safety limits are performance guarantees.

Run all native tests on an attached emulator/device with:
`gradle -PatelierSideBySide=true :app:connectedDebugAndroidTest`

The source-generation environment has not executed these checks.
