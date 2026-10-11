# Implementation references

Primary platform documentation consulted; these are not bundled libraries.
The toolbar vector paths are hand-authored; no Procreate artwork, brush assets
or proprietary application code is included.

- Android MotionEvent (historical input, pressure, event timestamps):
  https://developer.android.com/reference/android/view/MotionEvent
- Android stylus input:
  https://developer.android.com/develop/ui/views/touch-and-input/stylus-input/advanced-stylus-features
- Android View (unbuffered dispatch, frame invalidation):
  https://developer.android.com/reference/android/view/View
- Android Canvas / Path / BlendMode:
  https://developer.android.com/reference/android/graphics/Canvas
  https://developer.android.com/reference/android/graphics/Path
  https://developer.android.com/reference/android/graphics/BlendMode
- Android PDF output:
  https://developer.android.com/reference/android/graphics/pdf/PdfDocument
- Android application signing / debug keys:
  https://developer.android.com/studio/publish/app-signing
- GitHub workflow syntax and secrets:
  https://docs.github.com/en/actions/writing-workflows/workflow-syntax-for-github-actions
  https://docs.github.com/en/actions/security-for-github-actions/security-guides/using-secrets-in-github-actions

Additional references for 0.3:
- Android ViewGroup touch ownership and requestDisallowInterceptTouchEvent:
  https://developer.android.com/develop/ui/views/touch-and-input/gestures/viewgroup
- Native SeekBar input and accessibility:
  https://developer.android.com/reference/android/widget/SeekBar
