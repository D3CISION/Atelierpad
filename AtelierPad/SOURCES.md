# Technical references and scope

Current change set checked against primary documentation (2026-10-12):

- Android custom View drawing/input:
  https://developer.android.com/develop/ui/views/layout/custom-views/custom-components
- Accessible ranges and ACTION_SET_PROGRESS:
  https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo
- Color matrix / hue rotation definition (W3C Filter Effects):
  https://www.w3.org/TR/filter-effects-1/#feColorMatrixElement

UI references: the two images supplied by the user in this conversation. They guided the left
rail arrangement and the hue-ring/SV-disc design. No screenshot/Procreate asset is bundled;
controls, vector glyphs and color wheel are implemented in this project.

The color wheel uses an elliptical-grid square/disc map and cached HSV-derived pixels.
The blur is a three-box Gaussian approximation, premultiplied before filtering, with clamped
edges. This is a CPU approximation, not a physical paint engine or a GPU effect backend.
All behavior claims must be interpreted with tests/VERIFICATION.md; Android execution and
Focus Pen latency have not been validated in this environment.
