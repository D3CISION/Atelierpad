package hu.atelierpad.app

import android.app.*
import android.content.*
import android.content.res.Configuration
import android.graphics.*
import android.net.Uri
import android.os.*
import android.text.InputType
import android.text.TextUtils
import android.view.*
import android.widget.*
import hu.atelierpad.core.*
import java.io.*
import java.text.DateFormat
import java.util.Date
import java.util.UUID
import kotlin.math.*

/** Canvas-first editor with transient popovers and a real home gallery. No fixed side inspector. */
class MainActivity : Activity() {
    private lateinit var assets: AssetStore
    private lateinit var files: ProjectFiles
    private var history = ProjectHistory(Project())
    private val p get() = history.current
    private lateinit var canvas: DrawingView
    private lateinit var workspace: FrameLayout
    private lateinit var overlayHost: FrameLayout
    private lateinit var saveState: TextView
    private lateinit var busyOverlay: TextView
    private var busyText: String? = null
    private var preview: BrushPreview? = null
    private var reference: ImageView? = null
    private var referenceBitmap: Bitmap? = null
    private var panelKind: String? = null
    private var colorSwatch: ColorSwatchView? = null
    private var colorWheel: ColorWheelView? = null
    private var recentContainer: LinearLayout? = null
    private var currentColor = Color.rgb(33, 39, 47)
    private var recentColors = emptyList<Int>()
    private var customBrushes = mutableListOf<Brush>()
    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("atelier", MODE_PRIVATE) }
    private var started = false
    private var loading = false
    @Volatile private var homeMode = true
    @Volatile private var galleryGeneration = 0
    private var hasOpenProject = false
    private var homeEntries = emptyList<ProjectGallery.Entry>()
    private lateinit var homeGrid: LinearLayout
    private var pendingExport: Project? = null
    private var exportKind = "png"
    private var pdfPaper = PdfPaper.CANVAS
    private var importKind = "layer"
    private var saveRunning = false
    private val pendingSaves = LinkedHashMap<String, Project>()
    private val unsavedRecoveries = LinkedHashMap<String, Project>() // UI-thread only; failed saves stay recoverable in this session.
    private val debounce = Runnable { if (hasOpenProject) queueSave(p) }
    private val toolButtons = HashMap<Tool, ImageButton>()
    private var sizeControl: VerticalSliderView? = null
    private var opacityControl: VerticalSliderView? = null
    private var controlRail: LinearLayout? = null
    private var railValue: TextView? = null
    private var activeSlider: VerticalSliderView? = null
    private var pickerButton: ImageButton? = null
    private var undoButton: ImageButton? = null
    private var redoButton: ImageButton? = null
    private var effectEditor: EffectsEditorView? = null
    private var paintBrush = Brush()
    private var eraserBrush = Brush("Studio eraser", size = 35f, hardness = 1f, smoothing = .25f)

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false)
        assets = AssetStore(applicationContext); files = ProjectFiles(applicationContext, assets)
        loadBrushes(); loadWorkingBrushes()
        currentColor = prefs.getInt("currentColor", currentColor)
        recentColors = RecentColors.decode(prefs.getString("recentColors", null))
        started = true
        showHome(false) // Cold launch never restores the last canvas or creates an unwanted blank project.
    }
    private fun installContent(content: View) {
        val root = FrameLayout(this).apply { setBackgroundColor(Theme.bg) }
        root.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            } else view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            insets
        }
        root.addView(content, FrameLayout.LayoutParams(-1, -1))
        busyOverlay = label(busyText.orEmpty(), 15f).apply {
            gravity = Gravity.CENTER; setBackgroundColor(0xcc151517.toInt())
            isClickable = true; visibility = if (busyText == null) View.GONE else View.VISIBLE
        }
        root.addView(busyOverlay, FrameLayout.LayoutParams(-1, -1))
        setContentView(root); root.requestApplyInsets()
    }
    private fun buildEditor(restoreViewport: Boolean = true) {
        val old = if (::canvas.isInitialized) canvas else null
        val viewport = if (restoreViewport) old?.captureViewport() else null
        val oldBrush = old?.brush ?: paintBrush
        val oldTool = if (restoreViewport) old?.tool ?: Tool.BRUSH else Tool.BRUSH
        val selection = if (restoreViewport) old?.activeSelection() else null
        val selectionMode = old?.selectionMode ?: SelectionMode.LASSO
        closePanel(); old?.release()
        homeMode = false; galleryGeneration++
        val root = column().apply { setBackgroundColor(Theme.bg) }
        val top = row().apply { setPadding(dp(8), dp(4), dp(8), dp(4)) }
        top.addView(iconButton(R.drawable.ic_gallery, "Home / projects") { showHome() }, LinearLayout.LayoutParams(dp(48), dp(48)))
        top.addView(iconButton(R.drawable.ic_tools, "Tools") { togglePanel("tools") }, LinearLayout.LayoutParams(dp(48), dp(48)))
        top.addView(iconButton(R.drawable.ic_effects, "Effects") { togglePanel("effects") }, LinearLayout.LayoutParams(dp(48), dp(48)))
        top.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        toolButtons.clear()
        fun tool(icon: Int, name: String, kind: Tool, click: () -> Unit) {
            val button = iconButton(icon, name, action = click)
            top.addView(button, LinearLayout.LayoutParams(dp(48), dp(48))); toolButtons[kind] = button
        }
        // Deliberate left-to-right order, including portrait: brush, selection, eraser, layers, color.
        tool(R.drawable.ic_brush, "Brush", Tool.BRUSH) {
            selectTool(Tool.BRUSH, false); togglePanel("brush")
        }
        tool(R.drawable.ic_select, "Selection", Tool.SELECT) {
            selectTool(Tool.SELECT, false); togglePanel("select")
        }
        tool(R.drawable.ic_eraser, "Eraser", Tool.ERASER) {
            val already = canvas.tool == Tool.ERASER
            selectTool(Tool.ERASER, false)
            if (already) togglePanel("brush") else closePanel()
        }
        top.addView(iconButton(R.drawable.ic_layers, "Layers") { togglePanel("layers") }, LinearLayout.LayoutParams(dp(48), dp(48)))
        colorSwatch = ColorSwatchView(this, currentColor) { togglePanel("color") }
        top.addView(colorSwatch, LinearLayout.LayoutParams(dp(48), dp(48)))
        root.addView(top, LinearLayout.LayoutParams(-1, dp(56)))
        workspace = FrameLayout(this).apply { isMotionEventSplittingEnabled = true }
        canvas = DrawingView(this, assets, { p }, { doc, label -> applyProject(doc, label) }, { message(it) },
            { value -> setColor(value); rememberColor(value); selectTool(Tool.BRUSH, false) },
            { x, y -> fillAt(x, y) }, { undo() }, { redo() })
        canvas.brush = if (oldTool == Tool.ERASER) oldBrush else paintBrush
        canvas.color = currentColor; canvas.tool = oldTool
        canvas.selectionMode = selectionMode; canvas.setSelection(selection)
        canvas.penOnly = prefs.getBoolean("penOnly", true); canvas.symmetry = prefs.getBoolean("symmetry", false)
        canvas.showGrid = prefs.getBoolean("grid", false); canvas.mirrorView = prefs.getBoolean("mirrorView", false)
        canvas.grayscaleView = prefs.getBoolean("grayscaleView", false)
        workspace.addView(canvas, FrameLayout.LayoutParams(-1, -1))
        addControlRail()
        overlayHost = FrameLayout(this).apply { visibility = View.GONE; elevation = dp(16).toFloat() }
        workspace.addView(overlayHost, FrameLayout.LayoutParams(-1, -1))
        canvas.onBrushChanged = { syncQuickControls() }
        canvas.onSelectionChanged = { if (panelKind == "select") refreshPanel() }
        root.addView(workspace, LinearLayout.LayoutParams(-1, 0, 1f))
        saveState = label("Saved locally", 11f, Theme.muted) // Status is exposed inside Project, not a permanent footer banner.
        installContent(root); updateTools(); syncQuickControls()
        if (viewport != null) { val drawing = canvas; drawing.post { drawing.restoreViewport(viewport) } }
        referenceBitmap?.let { showReference(it) }
        overlayHost.bringToFront()
    }
    private fun addControlRail() {
        val rail = column().apply {
            setPadding(dp(2), dp(8), dp(2), dp(8)); clipChildren = false
            background = rounded(0xe51d1d22.toInt(), dp(16).toFloat(), 0x223f3f49)
            elevation = dp(4).toFloat()
        }
        controlRail = rail
        fun slider(title: String, format: (Int) -> String, update: (Int) -> Unit, exact: () -> Unit): VerticalSliderView {
            return VerticalSliderView(this, title).apply {
                formatValue = format
                changed = { value -> update(value); updateRailValue(this) }
                tracking = { active ->
                    activeSlider = if (active) this else null
                    if (active) updateRailValue(this) else railValue?.visibility = View.GONE
                }
                exactValue = exact
            }
        }
        sizeControl = slider("Brush size", { "${SliderScale.sizeAt(it).roundToInt()} px" },
            { canvas.brush = canvas.brush.copy(size = SliderScale.sizeAt(it)) },
            { numberDialog("Brush size (px)", 1f, 240f, canvas.brush.size) { canvas.brush = canvas.brush.copy(size = it) } })
        rail.addView(sizeControl, LinearLayout.LayoutParams(-1, 0, 1f))
        pickerButton = iconButton(R.drawable.ic_picker_switch, "Eyedropper") {
            closePanel(); selectTool(if (canvas.tool == Tool.PICKER) Tool.BRUSH else Tool.PICKER, false)
        }
        rail.addView(pickerButton, LinearLayout.LayoutParams(-1, dp(48)))
        opacityControl = slider("Stroke opacity", { "${(SliderScale.opacityAt(it)*100).roundToInt()}%" },
            { canvas.brush = canvas.brush.copy(opacity = SliderScale.opacityAt(it)) },
            { numberDialog("Stroke opacity (%)", 1f, 100f, canvas.brush.opacity*100) { canvas.brush = canvas.brush.copy(opacity = it/100) } })
        rail.addView(opacityControl, LinearLayout.LayoutParams(-1, 0, 1f))
        rail.gap(4)
        undoButton = iconButton(R.drawable.ic_undo, "Undo") { undo() }
        redoButton = iconButton(R.drawable.ic_redo, "Redo") { redo() }
        rail.addView(undoButton, LinearLayout.LayoutParams(-1, dp(48)))
        rail.addView(redoButton, LinearLayout.LayoutParams(-1, dp(48)))
        val params = FrameLayout.LayoutParams(dp(56), dp((resources.configuration.screenHeightDp-180).coerceIn(220,520)), Gravity.LEFT or Gravity.CENTER_VERTICAL)
        params.setMargins(dp(10), dp(8), 0, dp(68)); workspace.addView(rail, params)
        workspace.addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
            val targetHeight = (bottom-top-dp(92)).coerceIn(dp(180),dp(520))
            if (rail.layoutParams.height != targetHeight) { rail.layoutParams = (rail.layoutParams as FrameLayout.LayoutParams).apply { height = targetHeight } }
        }
        val project = iconButton(R.drawable.ic_project, "Project") { togglePanel("project") }.apply {
            background = rounded(0xe51d1d22.toInt(),dp(14).toFloat())
        }
        workspace.addView(project, FrameLayout.LayoutParams(dp(52),dp(48),Gravity.LEFT or Gravity.BOTTOM).apply { setMargins(dp(12),0,0,dp(10)) })
        railValue = label("", 13f).apply {
            gravity = Gravity.CENTER; background = rounded(0xee303037.toInt(), dp(10).toFloat())
            visibility = View.GONE; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        workspace.addView(railValue,FrameLayout.LayoutParams(dp(80),dp(40),Gravity.TOP or Gravity.LEFT).apply { leftMargin=dp(74) })
    }
    private fun updateRailValue(slider: VerticalSliderView) {
        if (activeSlider != slider) return
        railValue?.apply {
            text = slider.formatValue(slider.progress)
            val y = ((controlRail?.top ?: 0) + slider.top + slider.thumbY - dp(20)).roundToInt()
            layoutParams = (layoutParams as FrameLayout.LayoutParams).apply { topMargin = y.coerceIn(0,(workspace.height-dp(40)).coerceAtLeast(0)) }
            visibility = View.VISIBLE
        }
    }
    private fun syncQuickControls() {
        if (!::canvas.isInitialized) return
        val brush = canvas.brush
        if (canvas.tool == Tool.ERASER) eraserBrush = brush else paintBrush = brush
        sizeControl?.progress = SliderScale.sizeProgress(brush.size); opacityControl?.progress = SliderScale.opacityProgress(brush.opacity)
        activeSlider?.let { updateRailValue(it) }
    }
    private fun updateTools() {
        if (!::canvas.isInitialized) return
        toolButtons.forEach { (tool, button) -> styleIcon(button, canvas.tool == tool) }
        pickerButton?.let { styleIcon(it, canvas.tool == Tool.PICKER) }
        undoButton?.apply { isEnabled = history.canUndo; alpha = if (isEnabled) 1f else .35f }
        redoButton?.apply { isEnabled = history.canRedo; alpha = if (isEnabled) 1f else .35f }
        canvas.invalidate()
    }
    private fun selectTool(tool: Tool, refresh: Boolean = true) {
        canvas.finishStroke()
        if (canvas.tool == Tool.ERASER) eraserBrush = canvas.brush else paintBrush = canvas.brush
        canvas.tool = tool; canvas.brush = if (tool == Tool.ERASER) eraserBrush else paintBrush
        updateTools(); if (refresh && panelKind == "brush") refreshPanel()
    }
    private fun updateStatus() { updateTools() }
    private fun togglePanel(kind: String) { if (panelKind == kind) closePanel() else showPanel(kind) }
    private fun showPanel(kind: String) {
        if (homeMode || !::canvas.isInitialized) return
        canvas.finishStroke(); closePanel(); panelKind = kind; refreshPanel()
    }
    private fun closePanel() {
        effectEditor?.release(); effectEditor = null
        panelKind = null; colorWheel = null; recentContainer = null; preview = null
        if (::overlayHost.isInitialized) { overlayHost.removeAllViews(); overlayHost.visibility = View.GONE }
    }
    private fun refreshPanel() {
        val kind = panelKind ?: return
        if (homeMode || !::overlayHost.isInitialized) return
        effectEditor?.release(); effectEditor = null
        preview = null; colorWheel = null; recentContainer = null
        overlayHost.removeAllViews(); overlayHost.visibility = View.VISIBLE
        val scrim = View(this).apply { setBackgroundColor(if(kind=="color")0x33000000 else 0x55000000); isClickable = true; setOnClickListener { closePanel() } }
        overlayHost.addView(scrim, FrameLayout.LayoutParams(-1, -1))
        val card = column(16).apply {
            isClickable = true; background = rounded(if(kind=="color")0xbe212125.toInt() else 0xee222225.toInt(), dp(20).toFloat(), Theme.line)
            elevation = dp(12).toFloat()
        }
        val title = when (kind) {
            "brush" -> if (canvas.tool == Tool.ERASER) "Eraser" else "Brushes"
            "color" -> "Color"; "select" -> "Selection"; "layers" -> "Layers"; "project" -> "Project"
            "effects" -> "Effects"; "effect-hsv" -> "Hue / saturation"; "effect-tone" -> "Brightness / contrast"
            "effect-blur" -> "Gaussian blur"; else -> "Tools"
        }
        if (kind != "select") {
            val header = row()
            header.addView(label(title, 15f).apply { typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER_VERTICAL }, LinearLayout.LayoutParams(0, dp(48), 1f))
            header.addView(iconButton(R.drawable.ic_close, "Close $title") { closePanel() }, LinearLayout.LayoutParams(dp(48), dp(48)))
            card.addView(header); card.gap(8)
        }
        val availableWidth = if (workspace.width > 0) workspace.width else resources.displayMetrics.widthPixels
        val availableHeight = if (workspace.height > 0) workspace.height else dp((resources.configuration.screenHeightDp - 90).coerceAtLeast(240))
        val preferredHeight = when (kind) { "color" -> 548; "select" -> 144; "effects" -> 326; "project" -> 510; "layers" -> 600; else -> 600 }
        val preferredWidth = when(kind) { "color" -> 352; "select" -> 280; else -> 380 }
        val width = min(dp(preferredWidth), availableWidth - dp(24)).coerceAtLeast(dp(220))
        val height = min(dp(preferredHeight), availableHeight - dp(24)).coerceAtLeast(dp(140))
        val content = column()
        when {
            kind == "layers" -> { card.addView(content, LinearLayout.LayoutParams(-1, 0, 1f)); layerPanel(content) }
            kind == "color" -> { card.addView(content, LinearLayout.LayoutParams(-1, 0, 1f)); colorPanel(content) }
            kind == "select" -> { card.addView(content, LinearLayout.LayoutParams(-1, 0, 1f)); selectionPanel(content) }
            kind.startsWith("effect-") -> {
                val snapshot = p; val selection = canvas.activeSelection()
                effectEditor = EffectsEditorView(this, assets, snapshot, selection, kind, { closePanel() }, { settings -> applyEffect(snapshot, selection, settings) })
                card.addView(effectEditor, LinearLayout.LayoutParams(-1, 0, 1f))
            }
            else -> {
                val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(content) }
                card.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
                when (kind) { "brush" -> brushPanel(content); "project" -> projectPanel(content); "effects" -> effectsPanel(content); else -> toolsPanel(content) }
            }
        }
        val gravity = when { kind == "project" -> Gravity.BOTTOM or Gravity.LEFT; kind == "tools" || kind == "effects" || kind.startsWith("effect-") -> Gravity.TOP or Gravity.LEFT; else -> Gravity.TOP or Gravity.RIGHT }
        overlayHost.addView(card, FrameLayout.LayoutParams(width, height, gravity).apply { setMargins(dp(12), dp(12), dp(12), dp(12)) })
    }
    private fun menuAction(parent: LinearLayout, icon: Int, text: String, action: () -> Unit) {
        val item = row().apply {
            minimumHeight = dp(52); setPadding(dp(4), dp(2), dp(8), dp(2))
            background = rounded(Theme.card, dp(10).toFloat()); isClickable = true; isFocusable = true
            contentDescription = text; setOnClickListener { action() }
        }
        item.addView(ImageView(this).apply { setImageResource(icon); setPadding(dp(12), dp(12), dp(12), dp(12)); imageTintList = android.content.res.ColorStateList.valueOf(Theme.ink) }, LinearLayout.LayoutParams(dp(44), dp(44)))
        item.addView(label(text, 13f), LinearLayout.LayoutParams(0, -2, 1f))
        parent.addView(item, LinearLayout.LayoutParams(-1, -2)); parent.gap(8)
    }
    private fun projectPanel(c: LinearLayout) {
        c.addView(label(p.title, 17f).apply { maxLines = 2 }); c.gap(8)
        c.addView(label("${p.width} x ${p.height} px / ${p.dpi} DPI", 12f, Theme.muted)); c.gap(6)
        c.addView(label(saveState.text.toString(), 11f, Theme.muted)); c.gap(16)
        menuAction(c, R.drawable.ic_gallery, "Home / projects") { closePanel(); showHome() }
        menuAction(c, R.drawable.ic_add, "New canvas") { closePanel(); newProjectDialog() }
        menuAction(c, R.drawable.ic_project, "Rename project") { closePanel(); renameProject() }
        menuAction(c, R.drawable.ic_import, "Open .atelier project") { closePanel(); openProjectFile() }
        menuAction(c, R.drawable.ic_import, "Import image / reference / brush tip") { closePanel(); importMenu() }
        menuAction(c, R.drawable.ic_export, "Export / PNG / JPEG / PDF / project") { closePanel(); exportMenu() }
    }
    private fun toolsPanel(c: LinearLayout) {
        menuAction(c, R.drawable.ic_fill, "Fill") { selectTool(Tool.FILL, false); closePanel(); message("Tap a region on the canvas to fill it.") }
        menuAction(c, R.drawable.ic_picker, "Eyedropper") { selectTool(Tool.PICKER, false); closePanel() }
        menuAction(c, R.drawable.ic_move, "Move canvas") { selectTool(Tool.PAN, false); closePanel() }
        menuAction(c, R.drawable.ic_fit, "Fit canvas") { canvas.fit(); closePanel() }
        c.section("Canvas")
        toggle(c, "Stylus only on canvas", canvas.penOnly) { canvas.penOnly = it; prefs.edit().putBoolean("penOnly", it).apply() }
        toggle(c, "Mirror symmetry", canvas.symmetry) { canvas.symmetry = it; prefs.edit().putBoolean("symmetry", it).apply(); canvas.invalidate() }
        toggle(c, "Grid", canvas.showGrid) { canvas.showGrid = it; prefs.edit().putBoolean("grid", it).apply(); canvas.invalidate() }
        toggle(c, "Flip view", canvas.mirrorView) { canvas.mirrorView = it; prefs.edit().putBoolean("mirrorView", it).apply(); canvas.invalidate() }
        toggle(c, "Grayscale view", canvas.grayscaleView) { canvas.grayscaleView = it; prefs.edit().putBoolean("grayscaleView", it).apply(); canvas.invalidate() }
        toggle(c, "Transparent background", p.transparent) { applyProject(p.copy(transparent = it), "Canvas background") }
    }
    private fun brushPanel(c: LinearLayout) {
        preview = BrushPreview(this, assets, { canvas.brush })
        c.addView(preview, LinearLayout.LayoutParams(-1, dp(64))); c.gap(12)
        menuAction(c, R.drawable.ic_settings, if(canvas.tool == Tool.ERASER) "Eraser settings" else "Brush settings") { closePanel(); brushStudio() }
        c.gap(4)
        (Brush.presets() + customBrushes).forEach { brush ->
            val tile = column(10).apply {
                background = rounded(if (canvas.brush.name == brush.name) Theme.selected else Theme.card, dp(10).toFloat())
                isClickable = true; isFocusable = true; contentDescription = "Choose ${brush.name}"
                setOnClickListener {
                    canvas.finishStroke()
                    canvas.brush = brush.copy(size=canvas.brush.size, opacity=canvas.brush.opacity)
                    if (canvas.tool == Tool.ERASER) eraserBrush = canvas.brush else paintBrush = canvas.brush
                    closePanel(); updateTools()
                }
            }
            tile.addView(label(brush.name, 12f)); tile.gap(7)
            tile.addView(BrushPreview(this, assets, { brush }), LinearLayout.LayoutParams(-1, dp(40)))
            c.addView(tile); c.gap(8)
        }
    }
    private fun colorPanel(c: LinearLayout) {
        val wheel = ColorWheelView(this, currentColor, { setColor(it, false) }, { rememberColor(it) })
        colorWheel = wheel
        c.addView(wheel, LinearLayout.LayoutParams(-1, 0, 1f))
        c.gap(12)
        recentContainer = column()
        // Reserved height prevents the wheel from jumping when the first/7th recent color is added.
        c.addView(recentContainer, LinearLayout.LayoutParams(-1, dp(96)))
        renderRecentColors()
    }
    private fun setColor(value: Int, updateWheel: Boolean = true) {
        currentColor = value or 0xff000000.toInt()
        if (::canvas.isInitialized) { canvas.color = currentColor; canvas.invalidate() }
        colorSwatch?.color = currentColor; preview?.invalidate()
        if (updateWheel) colorWheel?.setColor(currentColor)
    }
    private fun rememberColor(value: Int) {
        recentColors = RecentColors.add(recentColors, value)
        prefs.edit().putInt("currentColor", currentColor).putString("recentColors", RecentColors.encode(recentColors)).apply()
        renderRecentColors()
    }
    private fun renderRecentColors() {
        val host = recentContainer ?: return
        host.removeAllViews()
        if (recentColors.isEmpty()) return
        recentColors.chunked(6).forEach { colors ->
            val line = row()
            colors.forEach { color ->
                val swatch = ColorSwatchView(this, color) { setColor(color); rememberColor(color); refreshPanel() }
                swatch.contentDescription = "Use recent color"; swatch.tooltipText = "Recent color"
                line.addView(swatch, LinearLayout.LayoutParams(0, dp(48), 1f))
            }
            repeat(6 - colors.size) { line.addView(View(this), LinearLayout.LayoutParams(0, dp(48), 1f)) }
            host.addView(line)
        }
    }
    private fun layerPanel(c: LinearLayout) {
        menuAction(c, R.drawable.ic_add, "New layer") {
            canvas.finishStroke()
            if (p.layers.size >= 32) message("Layer limit reached.") else {
                val list = p.layers.toMutableList(); list.add(p.active + 1, Layer(name = "Layer ${p.layers.size + 1}"))
                applyProject(p.copy(layers = list, active = p.active + 1), "New layer")
            }
        }
        c.addView(label("Hold a layer, then drag it up or down", 11f, Theme.muted)); c.gap(12)
        val stack = LayerStackView(this, { p }, canvas.renderer,
            { index -> canvas.finishStroke(); history.select(index); canvas.invalidate(); refreshPanel() },
            { index -> changeLayer(index, p.layers[index].copy(visible = !p.layers[index].visible), "Visibility") },
            { index -> closePanel(); layerSettings(index) },
            { doc -> if (!homeMode && doc.id == p.id && doc != p) applyProject(doc, "Reorder layers") })
        c.addView(stack, LinearLayout.LayoutParams(-1, 0, 1f))
    }
    private fun selectionPanel(c: LinearLayout) {
        val modes = row()
        listOf(Triple(SelectionMode.LASSO, R.drawable.ic_select, "Freehand lasso"),
            Triple(SelectionMode.RECTANGLE, R.drawable.ic_rectangle, "Rectangle selection"),
            Triple(SelectionMode.ELLIPSE, R.drawable.ic_ellipse, "Ellipse selection")).forEach { (mode, icon, title) ->
            modes.addView(iconButton(icon, title, canvas.selectionMode == mode && canvas.tool == Tool.SELECT) {
                canvas.selectionMode = mode; selectTool(Tool.SELECT, false); closePanel()
            }, LinearLayout.LayoutParams(0, dp(48), 1f))
        }
        modes.addView(iconButton(R.drawable.ic_close, "Close selection") { closePanel() }, LinearLayout.LayoutParams(dp(48), dp(48)))
        c.addView(modes); c.gap(12)
        val actions = row(); val selected = canvas.activeSelection()
        fun action(icon: Int, title: String, selectedIcon: Boolean = false, block: () -> Unit) {
            val button = iconButton(icon, title, selectedIcon, block).apply { isEnabled = selected != null; alpha = if(isEnabled)1f else .35f }
            actions.addView(button, LinearLayout.LayoutParams(0, dp(48), 1f))
        }
        action(R.drawable.ic_invert, "Invert selection", selected?.inverted == true) {
            canvas.finishStroke(); canvas.activeSelection()?.let { canvas.setSelection(it.copy(inverted = !it.inverted)) }
        }
        action(R.drawable.ic_deselect, "Deselect") { canvas.finishStroke(); canvas.clearSelection(); closePanel() }
        action(R.drawable.ic_copy, "Copy to new layer") { extractSelection(false) }
        action(R.drawable.ic_cut, "Cut to new layer") { extractSelection(true) }
        c.addView(actions)
    }
    private fun effectsPanel(c: LinearLayout) {
        fun open(kind: String) {
            val layer = p.layers[p.active]
            if (!layer.visible || layer.locked || layer.alphaLock) { message("Use a visible, unlocked layer. Turn off alpha lock for effects."); return }
            if (p.width.toLong()*p.height > PixelEffects.MAX_PIXELS) { message("Effects currently support canvases up to 4,194,304 pixels."); return }
            showPanel(kind)
        }
        menuAction(c, R.drawable.ic_color, "Hue / saturation") { open("effect-hsv") }
        menuAction(c, R.drawable.ic_tone, "Brightness / contrast") { open("effect-tone") }
        menuAction(c, R.drawable.ic_blur, "Gaussian blur") { open("effect-blur") }
        c.gap(4); c.addView(label("Preview first. Apply makes one undoable edit to the active layer.", 11f, Theme.muted))
    }
    private fun applyEffect(snapshot: Project, selection: Selection?, settings: EffectSettings) {
        if (snapshot != p || homeMode) { closePanel(); message("The project changed. Open the effect again."); return }
        if (settings.isIdentity) return
        closePanel(); busy("Applying effect...")
        work({
            RasterRenderer(assets).use { renderer ->
                val image = renderer.layer(snapshot, snapshot.layers[snapshot.active])
                val processed = EffectRaster.apply(image, settings, selection, snapshot.width, snapshot.height)
                try { assets.store(processed) } finally { processed.recycle() }
            }
        }, { asset ->
            if (!homeMode && p == snapshot) {
                val next = EffectEdit.bake(snapshot, snapshot.layers[snapshot.active].id, asset)
                applyProject(next, "Layer effect")
            } else message("The project changed. Effect was not applied.")
            busy(null)
        })
    }
    private fun extractSelection(cut:Boolean) {
        canvas.finishStroke()
        val selected=canvas.activeSelection() ?: return
        val snapshot=p;val layer=snapshot.layers[snapshot.active]
        if(snapshot.layers.size>=32){message("Layer limit reached.");return}
        if(!layer.visible || (cut && (layer.locked || layer.alphaLock))) {
            message("Use a visible layer. Cut also requires layer lock and alpha lock to be off.");return
        }
        busy(if(cut)"Cutting selection..." else "Copying selection...")
        work({
            RasterRenderer(assets).use {renderer->
                val bitmap=Bitmap.createBitmap(snapshot.width,snapshot.height,Bitmap.Config.ARGB_8888)
                try {
                    val target=Canvas(bitmap);target.clipPath(selected.clipPath(snapshot.width,snapshot.height))
                    target.drawBitmap(renderer.layer(snapshot,layer),0f,0f,Paint())
                    assets.store(bitmap)
                } finally {bitmap.recycle()}
            }
        },{id->
            val image=Operation.Image(asset=id,x=0f,y=0f,width=snapshot.width.toFloat(),height=snapshot.height.toFloat())
            val extracted=layer.copy(id=UUID.randomUUID().toString(),name=(layer.name+" selection").take(120),locked=false,alphaLock=false,operations=listOf(image))
            val layers=snapshot.layers.toMutableList()
            if(cut)layers[snapshot.active]=layer.copy(operations=layer.operations+Operation.Clear(selection=selected))
            var insertAt=snapshot.active+1
            if(!extracted.clipped)while(insertAt<layers.size && layers[insertAt].clipped)insertAt++
            layers.add(insertAt,extracted)
            applyProject(snapshot.copy(layers=layers,active=insertAt),if(cut)"Cut selection to layer" else "Copy selection to layer")
            canvas.clearSelection();selectTool(Tool.BRUSH,false);busy(null);showPanel("layers")
        })
    }
    private fun changeLayer(index: Int, layer: Layer, label: String) {
        val list = p.layers.toMutableList(); list[index] = layer; applyProject(p.copy(layers = list), label)
    }
    private fun applyProject(doc: Project, label: String) {
        try { doc.validate() } catch (e: IllegalArgumentException) { message(e.message ?: "Project limit reached"); return }
        if (doc == p) return
        history.commit(doc, label); canvas.invalidate(); updateStatus()
        if (label == "Brush stroke") (doc.layers[doc.active].operations.lastOrNull() as? Operation.Stroke)?.let { rememberColor(it.color) }
        if (label == "Fill region") rememberColor(canvas.color)
        if (panelKind == "layers") refreshPanel()
        handler.removeCallbacks(debounce); handler.postDelayed(debounce, 1400); saveState.text = "Unsaved changes"
    }
    private fun undo() { closePanel(); canvas.finishStroke(true); if (history.undo()) afterHistory() else message("Nothing to undo") }
    private fun redo() { closePanel(); canvas.finishStroke(true); if (history.redo()) afterHistory() else message("Nothing to redo") }
    private fun afterHistory() {
        canvas.invalidate(); updateStatus(); handler.removeCallbacks(debounce); handler.postDelayed(debounce, 1400); saveState.text = "Unsaved changes"
    }
    private fun renameProject() {
        val input = edit(p.title); val box = column(24); box.addView(input)
        AlertDialog.Builder(this).setTitle("Project name").setView(box).setPositiveButton("Save") { _, _ ->
            val text = input.text.toString().trim().take(200); if (text.isNotEmpty()) applyProject(p.copy(title = text), "Rename project")
        }.setNegativeButton("Cancel", null).show()
    }
    private fun queueSave(doc: Project) {
        if (!started || loading || !hasOpenProject) return
        unsavedRecoveries[doc.id] = doc
        synchronized(pendingSaves) { pendingSaves[doc.id] = doc; if (saveRunning) return; saveRunning = true }
        files.executor.execute {
            while (true) {
                val next = synchronized(pendingSaves) {
                    val entry = pendingSaves.entries.firstOrNull()
                    if (entry == null) { saveRunning = false; null } else { pendingSaves.remove(entry.key); entry.value }
                } ?: break
                try {
                    files.saveLocal(next)
                    runOnUiThread {
                        if(unsavedRecoveries[next.id]==next)unsavedRecoveries.remove(next.id)
                        if (!isDestroyed && p == next) saveState.text = "Saved locally"
                    }
                } catch (e: Exception) {
                    runOnUiThread { if (!isDestroyed) { saveState.text = "SAVE FAILED"; message("Could not save: ${e.message}") } }
                } catch (_: OutOfMemoryError) {
                    runOnUiThread { if (!isDestroyed) { saveState.text = "SAVE FAILED"; message("Not enough memory to save. Keep the app open and export a backup.") } }
                }
            }
        }
    }
    private fun openProject(doc: Project) {
        if (::canvas.isInitialized) { canvas.finishStroke(); canvas.clearSelection() }
        history.reset(doc); hasOpenProject = true; buildEditor(false)
        saveState.text = "Saved locally"
    }
    private fun busy(text: String?) {
        busyText = text
        if (::busyOverlay.isInitialized) { busyOverlay.text = text.orEmpty(); busyOverlay.visibility = if (text == null) View.GONE else View.VISIBLE }
    }
    private fun message(text: String) { Toast.makeText(this, text, Toast.LENGTH_LONG).show() }
    private fun <T> work(task: () -> T, success: (T) -> Unit, failure: (Throwable) -> Unit = { busy(null); message(it.message ?: "Operation failed") }) {
        files.executor.execute {
            try { val value = task(); runOnUiThread { if (!isDestroyed) success(value) } }
            catch (e: Exception) { runOnUiThread { if (!isDestroyed) failure(e) } }
            catch (e: OutOfMemoryError) { runOnUiThread { if (!isDestroyed) { busy(null); failure(e); message("Not enough memory. Try a smaller canvas.") } } }
        }
    }
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (homeMode) showHome(false) else { canvas.finishStroke(); saveWorkingBrushes(); buildEditor() }
    }
    @Deprecated("Legacy back handling for this dependency-light Activity")
    override fun onBackPressed() {
        when { busyText != null -> Unit; panelKind != null -> closePanel(); !homeMode -> showHome(); else -> super.onBackPressed() }
    }
    override fun onPause() {
        super.onPause()
        if (::canvas.isInitialized) { canvas.finishStroke(); saveWorkingBrushes() }
        prefs.edit().putInt("currentColor", currentColor).apply()
        handler.removeCallbacks(debounce); if (started && !loading && hasOpenProject) queueSave(p)
    }
    override fun onDestroy() {
        closePanel(); handler.removeCallbacksAndMessages(null); galleryGeneration++
        if (::canvas.isInitialized) canvas.release()
        referenceBitmap = null; files.executor.shutdown(); super.onDestroy()
    }
    private fun brushStudio() {
        canvas.finishStroke();var draft=canvas.brush
        val c=column(18);val name=edit(draft.name,"Brush name");c.addView(name)
        val test=BrushPreview(this,assets,{draft});c.addView(test,LinearLayout.LayoutParams(-1,dp(100)))
        fun refresh(){test.invalidate()}
        slider(c,"Hardness",0f,1f,draft.hardness){draft=draft.copy(hardness=it);refresh()}
        slider(c,"Stamp spacing",.025f,1.5f,draft.spacing,{String.format("%.2f",it)}){draft=draft.copy(spacing=it);refresh()}
        slider(c,"Texture grain",0f,1f,draft.texture){draft=draft.copy(texture=it);refresh()}
        slider(c,"Scatter",0f,1f,draft.scatter,{String.format("%.2f",it)}){draft=draft.copy(scatter=it);refresh()}
        slider(c,"Tip rotation",-180f,180f,draft.rotation){draft=draft.copy(rotation=it);refresh()}
        slider(c,"Paint flow",.01f,1f,draft.flow,{"${(it*100).roundToInt()}%"}){draft=draft.copy(flow=it);refresh()}
        slider(c,"Stabilization",0f,1f,draft.smoothing,{"${(it*100).roundToInt()}%"}){draft=draft.copy(smoothing=it);refresh()}
        c.section("Pressure response")
        slider(c,"Pressure smoothing",0f,1f,draft.pressureSmoothing,{"${(it*100).roundToInt()}%"}){draft=draft.copy(pressureSmoothing=it);refresh()}
        toggle(c,"Pressure controls size",draft.pressureSize){draft=draft.copy(pressureSize=it);refresh()}
        toggle(c,"Pressure controls flow",draft.pressureOpacity){draft=draft.copy(pressureOpacity=it);refresh()}
        slider(c,"Minimum size",.01f,1f,draft.minSize,{"${(it*100).roundToInt()}%"}){draft=draft.copy(minSize=it);refresh()}
        slider(c,"Pressure curve",.2f,4f,draft.pressureGamma,{String.format("%.2f",it)}){draft=draft.copy(pressureGamma=it);refresh()}
        toggle(c,"Follow stroke direction",draft.followDirection){draft=draft.copy(followDirection=it);refresh()}
        c.gap(12);c.addView(label("Flow controls paint deposited along the stroke. Opacity caps the whole stroke, including overlapping parts. Hard Studio ink is drawn as continuous antialiased geometry. Custom tips use PNG transparency.",12f,Theme.muted))
        fun applyBrush(save:Boolean) {
            draft=draft.copy(name=name.text.toString().trim().ifEmpty{"Custom brush"}.take(120));draft.validate()
            canvas.brush=draft
            if(save) {customBrushes.add(draft);if(customBrushes.size>40)customBrushes.removeAt(0);saveBrushes()}
            refreshPanel()
        }
        AlertDialog.Builder(this).setTitle(if(canvas.tool==Tool.ERASER)"Eraser settings" else "Brush settings").setView(ScrollView(this).apply{addView(c)})
            .setPositiveButton("Use brush"){_,_->applyBrush(false)}.setNeutralButton("Save preset"){_,_->applyBrush(true)}.setNegativeButton("Cancel",null).show()
    }
    private fun saveWorkingBrushes() {
        if(!::canvas.isInitialized)return
        if(canvas.tool==Tool.ERASER)eraserBrush=canvas.brush else paintBrush=canvas.brush
        fun encode(brush:Brush):String {
            val buffer=ByteArrayOutputStream();DataOutputStream(buffer).use {ProjectArchive.writeBrush(it,brush)}
            return android.util.Base64.encodeToString(buffer.toByteArray(),android.util.Base64.NO_WRAP)
        }
        prefs.edit().putString("workingPaintV2",encode(paintBrush)).putString("workingEraserV2",encode(eraserBrush)).apply()
    }
    private fun loadWorkingBrushes() {
        fun decode(key:String,fallback:Brush):Brush=try {
            val text=prefs.getString(key,null)
            if(text==null)fallback else DataInputStream(ByteArrayInputStream(android.util.Base64.decode(text,android.util.Base64.NO_WRAP))).use {
                ProjectArchive.readBrush(it,2).takeIf {b->b.asset==null || assets.file(b.asset).exists()} ?: fallback
            }
        } catch(e:Exception){fallback}
        paintBrush=decode("workingPaintV2",paintBrush);eraserBrush=decode("workingEraserV2",eraserBrush)
    }
    private fun saveBrushes() {
        val b=ByteArrayOutputStream();DataOutputStream(b).use{out->out.writeInt(customBrushes.size);customBrushes.forEach{ProjectArchive.writeBrush(out,it)}}
        prefs.edit().putString("customBrushesV2",android.util.Base64.encodeToString(b.toByteArray(),android.util.Base64.NO_WRAP)).apply()
    }
    private fun loadBrushes() {
        try {
            val v2=prefs.getString("customBrushesV2",null)
            val raw=v2 ?: prefs.getString("customBrushes",null) ?: return
            DataInputStream(ByteArrayInputStream(android.util.Base64.decode(raw,android.util.Base64.NO_WRAP))).use{input->
                val count=input.readInt();require(count in 0..40);customBrushes=MutableList(count){ProjectArchive.readBrush(input,if(v2==null)1 else 2)}
            }
            customBrushes.removeAll{it.asset!=null && !assets.file(it.asset).exists()}
        } catch(e:Exception){customBrushes.clear()}
    }
    private fun layerSettings(index: Int) {
        canvas.finishStroke(); history.select(index)
        var draft = p.layers[index]
        val c = column(24)
        c.addView(label("Name", 12f, Theme.muted)); c.gap(6)
        val name = edit(draft.name); c.addView(name); c.gap(16)
        slider(c, "Opacity", 0f, 1f, draft.opacity, { "${(it * 100).roundToInt()}%" }) { draft = draft.copy(opacity = it) }
        c.gap(12); c.addView(label("Blend mode", 12f, Theme.muted)); c.gap(8)
        val blend = Spinner(this).apply {
            adapter = ArrayAdapter<String>(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                Mixing.values().map { it.name.lowercase().replace('_', ' ') })
            setSelection(draft.mixing.ordinal)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { draft = draft.copy(mixing = Mixing.values()[position]) }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
        c.addView(blend, LinearLayout.LayoutParams(-1, dp(48))); c.gap(16)
        toggle(c, "Lock layer", draft.locked) { draft = draft.copy(locked = it) }; c.gap(8)
        toggle(c, "Alpha lock", draft.alphaLock) { draft = draft.copy(alphaLock = it) }; c.gap(8)
        toggle(c, "Clipping mask", draft.clipped) { draft = draft.copy(clipped = it) }.isEnabled = index > 0
        c.section("Transform")
        slider(c, "Horizontal offset", -p.width.toFloat(), p.width.toFloat(), draft.offsetX.coerceIn(-p.width.toFloat(), p.width.toFloat())) { draft = draft.copy(offsetX = it) }
        slider(c, "Vertical offset", -p.height.toFloat(), p.height.toFloat(), draft.offsetY.coerceIn(-p.height.toFloat(), p.height.toFloat())) { draft = draft.copy(offsetY = it) }
        slider(c, "Scale", .1f, 5f, draft.scale, { String.format("%.2fx", it) }) { draft = draft.copy(scale = it) }
        slider(c, "Rotation", -180f, 180f, draft.rotation.coerceIn(-180f, 180f)) { draft = draft.copy(rotation = it) }
        lateinit var dialog: AlertDialog
        fun action(text: String, destructive: Boolean = false, run: () -> Unit) {
            val b = button(text, action = run).apply {
                textSize = 12f; setPadding(dp(20), dp(12), dp(20), dp(12)); minHeight = dp(48)
                if (destructive) setTextColor(0xffecaaaa.toInt())
            }
            c.addView(b, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(12); bottomMargin = dp(4) })
        }
        action("Reset transform") {
            draft = draft.copy(offsetX = 0f, offsetY = 0f, scale = 1f, rotation = 0f,
                name = name.text.toString().trim().ifEmpty { "Layer" }.take(120))
            changeLayer(index, draft, "Reset transform"); dialog.dismiss(); showPanel("layers")
        }
        c.section("Actions")
        action("Duplicate layer") { dialog.dismiss(); duplicateLayer(index); showPanel("layers") }
        action("Delete layer", true) { dialog.dismiss(); deleteLayer(index) }
        c.gap(20)
        dialog = AlertDialog.Builder(this).setTitle("Layer settings").setView(ScrollView(this).apply { addView(c) })
            .setPositiveButton("Apply") { _, _ ->
                draft = draft.copy(name = name.text.toString().trim().ifEmpty { "Layer" }.take(120))
                changeLayer(index, draft, "Layer settings"); showPanel("layers")
            }.setNegativeButton("Cancel") { _, _ -> showPanel("layers") }.create()
        dialog.show()
    }
    private fun duplicateLayer(index:Int) {
        if(p.layers.size>=32){message("Layer limit reached");return}
        val layer=p.layers[index]
        val copy=layer.copy(id=UUID.randomUUID().toString(),name=(layer.name+" copy").take(120),operations=layer.operations.map{
            when(it){is Operation.Stroke->it.copy(id=UUID.randomUUID().toString());is Operation.Image->it.copy(id=UUID.randomUUID().toString());is Operation.Clear->it.copy(id=UUID.randomUUID().toString())}
        })
        val list=p.layers.toMutableList();list.add(index+1,copy);applyProject(p.copy(layers=list,active=index+1),"Duplicate layer");refreshPanel()
    }
    private fun moveLayer(index:Int,direction:Int) {
        applyProject(LayerOrder.moveBy(p,p.layers[index].id,direction>0),"Reorder layers");refreshPanel()
    }
    private fun deleteLayer(index:Int) {
        if(p.layers.size<=1){message("Keep at least one layer.");return}
        confirm("Delete this layer? This can be undone.") {
            val list=p.layers.toMutableList();list.removeAt(index)
            if(list.first().clipped)list[0]=list[0].copy(clipped=false)
            applyProject(p.copy(layers=list,active=(index-1).coerceAtLeast(0)),"Delete layer");showPanel("layers")
        }
    }
    private fun confirm(text:String,action:()->Unit){AlertDialog.Builder(this).setMessage(text).setPositiveButton("Continue"){_,_->action()}.setNegativeButton("Cancel",null).show()}
    private fun showHome(saveCurrent: Boolean = true) {
        if (::canvas.isInitialized) {
            canvas.finishStroke(); saveWorkingBrushes(); currentColor = canvas.color
            if (saveCurrent && hasOpenProject) queueSave(p)
            canvas.renderer.close()
        }
        handler.removeCallbacks(debounce); closePanel(); homeMode = true
        sizeControl = null; opacityControl = null; controlRail = null; railValue = null; activeSlider = null; colorSwatch = null
        pickerButton = null; undoButton = null; redoButton = null
        toolButtons.clear()
        val generation = ++galleryGeneration
        val root = column().apply { setPadding(dp(20), dp(12), dp(20), dp(10)) }
        val head = row()
        head.addView(label("Projects", 25f).apply { typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER_VERTICAL }, LinearLayout.LayoutParams(0, dp(64), 1f))
        head.addView(iconButton(R.drawable.ic_import, "Open .atelier project") { openProjectFile() }, LinearLayout.LayoutParams(dp(48), dp(48)))
        head.addView(iconButton(R.drawable.ic_add, "New canvas") { newProjectDialog() }, LinearLayout.LayoutParams(dp(48), dp(48)))
        root.addView(head); root.gap(4)
        root.addView(label("Your local illustrations", 12f, Theme.muted)); root.gap(20)
        homeGrid = column()
        homeGrid.addView(label("Loading projects...", 14f, Theme.muted))
        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(homeGrid) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.gap(12)
        root.addView(label(getString(R.string.build_label), 11f, Theme.muted).apply { gravity = Gravity.CENTER })
        saveState = label("Saved locally", 11f, Theme.muted)
        installContent(root)
        work({ files.gallery.entries() }, { entries ->
            if (homeMode && generation == galleryGeneration) {
                val pending = unsavedRecoveries.values.map { doc ->
                    ProjectGallery.Entry(files.path(doc),doc.id,doc.title,doc.width,doc.height,System.currentTimeMillis(),0L,0L,"Unsaved changes")
                }
                homeEntries = pending + entries.filter { it.id !in unsavedRecoveries }
                if(hasOpenProject && homeEntries.none { it.id == p.id })
                    homeEntries = listOf(ProjectGallery.Entry(files.path(p),p.id,p.title,p.width,p.height,System.currentTimeMillis(),0L,0L,"Not saved yet"))+homeEntries
                populateHome(generation)
            }
        }, { error ->
            if (homeMode && generation == galleryGeneration) {
                homeGrid.removeAllViews(); homeGrid.addView(label("Could not read the local gallery. Your files have not been deleted.", 14f))
                homeGrid.gap(12); homeGrid.addView(button("Retry") { showHome(false) })
                message(error.message ?: "Gallery unavailable")
            }
        })
    }
    private fun populateHome(generation: Int) {
        homeGrid.removeAllViews()
        if (homeEntries.isEmpty()) {
            val empty = column(24)
            empty.gap(40); empty.addView(label("A space for your next illustration", 20f)); empty.gap(12)
            empty.addView(label("Create a canvas, or open an .atelier project from an earlier version.", 14f, Theme.muted)); empty.gap(24)
            empty.addView(button("New canvas", true) { newProjectDialog() }); empty.gap(12)
            empty.addView(button("Open project") { openProjectFile() }); homeGrid.addView(empty)
            return
        }
        val width = resources.configuration.screenWidthDp
        val columns = when { width >= 1100 -> 4; width >= 720 -> 3; width >= 430 -> 2; else -> 1 }
        homeEntries.chunked(columns).forEach { entries ->
            val line = row().apply { gravity = Gravity.TOP }
            entries.forEach { entry ->
                val tile = column(10).apply {
                    background = rounded(Theme.card, dp(16).toFloat()); isClickable = true; isFocusable = true
                    contentDescription = "${entry.title}, last edited ${editedLabel(entry.editedAt)}"
                    setOnClickListener { openGalleryEntry(entry) }
                }
                val imageFrame = FrameLayout(this).apply { background = rounded(Theme.bg, dp(8).toFloat()); clipToOutline = true }
                val image = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER; contentDescription = "Preview of ${entry.title}" }
                imageFrame.addView(image, FrameLayout.LayoutParams(-1, -1))
                val placeholder = label(if (entry.problem == null) "Loading preview..." else if(entry.id in unsavedRecoveries)"Unsaved changes - tap to recover" else "Preview unavailable", 11f, Theme.muted).apply { gravity = Gravity.CENTER }
                imageFrame.addView(placeholder, FrameLayout.LayoutParams(-1, -1))
                val previewHeight = (((width - 40) / columns - 36) * .7f).toInt().coerceIn(120, 230)
                tile.addView(imageFrame, LinearLayout.LayoutParams(-1, dp(previewHeight))); tile.gap(12)
                tile.addView(label(entry.title, 14f).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }); tile.gap(6)
                tile.addView(label("Edited ${editedLabel(entry.editedAt)}", 11f, Theme.muted))
                if (entry.width > 0) { tile.gap(4); tile.addView(label("${entry.width} x ${entry.height} px", 10f, Theme.muted)) }
                line.addView(tile, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(4), 0, dp(4), dp(14)) })
                files.executor.execute {
                    if (!homeMode || generation != galleryGeneration || isDestroyed) return@execute
                    val bitmap = try { files.gallery.thumbnail(entry) } catch (_: Exception) { null } catch (_: OutOfMemoryError) { null }
                    runOnUiThread {
                        if (homeMode && generation == galleryGeneration && !isDestroyed) {
                            image.setImageBitmap(bitmap); placeholder.visibility = if (bitmap != null) View.GONE else View.VISIBLE
                            if (bitmap == null && entry.id !in unsavedRecoveries) placeholder.text = "Preview unavailable"
                        } else bitmap?.recycle()
                    }
                }
            }
            repeat(columns - entries.size) { line.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f).apply { setMargins(dp(4), 0, dp(4), 0) }) }
            homeGrid.addView(line)
        }
    }
    private fun editedLabel(time: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(time))
    private fun openGalleryEntry(entry: ProjectGallery.Entry) {
        if (hasOpenProject && p.id == entry.id) { buildEditor(); return }
        unsavedRecoveries[entry.id]?.let { openProject(it); queueSave(it); return }
        loading = true; galleryGeneration++; busy("Opening project...")
        work({ files.readLocal(entry.file) }, { doc ->
            loading = false; busy(null); openProject(doc)
        }, { error -> loading = false; busy(null); message("Could not open this project: ${error.message}. The file was kept."); showHome(false) })
    }
    private fun openProjectFile() {
        if (::canvas.isInitialized && !homeMode) { canvas.finishStroke(); queueSave(p) }
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "*/*"; addCategory(Intent.CATEGORY_OPENABLE) }, 102)
    }
    private fun newProjectDialog() {
        val c=column(18);val title=edit("Untitled illustration","Project name");c.addView(title)
        val width=edit("1600","Width (px)").apply{inputType=InputType.TYPE_CLASS_NUMBER}
        val height=edit("1200","Height (px)").apply{inputType=InputType.TYPE_CLASS_NUMBER}
        val dpi=edit("300","DPI").apply{inputType=InputType.TYPE_CLASS_NUMBER}
        c.addView(label("Width / height / DPI",12f,Theme.muted));c.addView(width);c.addView(height);c.addView(dpi)
        val presets=Spinner(this).apply{adapter=ArrayAdapter<String>(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,listOf("1600 x 1200 - Sketch","2048 x 2048 - Square","2480 x 3508 - A4 at 300 DPI"));onItemSelectedListener=object:AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent:AdapterView<*>?,view:View?,position:Int,id:Long){val wh=listOf(1600 to 1200,2048 to 2048,2480 to 3508)[position];width.setText(wh.first.toString());height.setText(wh.second.toString())}
            override fun onNothingSelected(parent:AdapterView<*>?){}
        }};c.addView(presets)
        var transparent=false;toggle(c,"Transparent background",false){transparent=it}
        c.gap(10);c.addView(label("Start with 1600 x 1200 for this alpha. Larger canvases need more memory and device testing.",12f,Theme.muted))
        val dialog=AlertDialog.Builder(this).setTitle("New canvas").setView(c).setPositiveButton("Create",null).setNegativeButton("Cancel",null).create()
        dialog.setOnShowListener{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
            try {
                val w=width.text.toString().toInt();val h=height.text.toString().toInt();val d=dpi.text.toString().toInt()
                val doc=Project(title=title.text.toString().trim().ifEmpty{"Untitled"}.take(200),width=w,height=h,dpi=d,transparent=transparent)
                doc.validate();require(w.toLong()*h*36<Runtime.getRuntime().maxMemory()){"This canvas is too large for the current memory budget."}
                if(hasOpenProject)queueSave(p);openProject(doc);queueSave(doc);dialog.dismiss()
            } catch(e:Exception){message(e.message ?: "Enter valid canvas dimensions.")}
        }};dialog.show()
    }
    private fun importMenu(){AlertDialog.Builder(this).setTitle("Import image").setItems(arrayOf("As a new layer","As a floating reference","As a custom brush tip")){_,which->importKind=listOf("layer","reference","tip")[which];chooseImage()}.show()}
    private fun chooseImage(){if(::canvas.isInitialized)canvas.finishStroke();startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="image/*";addCategory(Intent.CATEGORY_OPENABLE)},103)}
    private fun exportMenu() {
        canvas.finishStroke();queueSave(p)
        AlertDialog.Builder(this).setTitle("Export illustration").setItems(arrayOf("Editable project (.atelier)","PNG - transparency supported","JPEG - white background","PDF - print / share")){_,which->
            exportKind=listOf("atelier","png","jpg","pdf")[which]
            if(exportKind=="pdf") {
                AlertDialog.Builder(this).setTitle("PDF page size").setSingleChoiceItems(arrayOf("Canvas size at ${p.dpi} DPI","A4 - fit, no crop","A3 - fit, no crop"),pdfPaper.ordinal){d,pos->pdfPaper=PdfPaper.values()[pos];d.dismiss();requestExport()}.setNegativeButton("Cancel",null).show()
            } else requestExport()
        }.show()
    }
    private fun requestExport() {
        pendingExport=p
        val mime=when(exportKind){"pdf"->"application/pdf";"png"->"image/png";"jpg"->"image/jpeg";else->"application/octet-stream"}
        val name=p.title.replace(Regex("[\\\\/:*?\"<>|]"),"_").take(100)+"."+exportKind
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply{type=mime;addCategory(Intent.CATEGORY_OPENABLE);putExtra(Intent.EXTRA_TITLE,name)},101)
    }
    @Deprecated("Legacy Activity result API is sufficient for this dependency-light alpha")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(resultCode!=RESULT_OK){if(requestCode==101)pendingExport=null;return}
        val uri=data?.data ?: return
        when(requestCode) {
            101->{val snapshot=pendingExport;pendingExport=null
                if(snapshot==null){message("The app restarted. Please export again; no file was written.");return}
                busy("Exporting...")
                val kind=exportKind;val paper=pdfPaper
                work({when(kind){"atelier"->files.exportProject(uri,snapshot);"png"->files.exportImage(uri,snapshot,false);"jpg"->files.exportImage(uri,snapshot,true);"pdf"->files.exportPdf(uri,snapshot,paper);else->error("Unknown export format")}},{busy(null);message("Export complete")})
            }
            102->{busy("Reading project...");work({contentResolver.openInputStream(uri).use{files.load(requireNotNull(it))}.copy(id=UUID.randomUUID().toString())},{doc->if(hasOpenProject)queueSave(p);openProject(doc);queueSave(doc);busy(null)})}
            103->if(hasOpenProject && ::canvas.isInitialized && !homeMode)importImage(uri,importKind)
                else message("Open a project and import the image again. The app was restarted.")
        }
    }
    private fun importImage(uri:Uri,kind:String) {
        busy("Importing image...")
        work({assets.importImage(uri,if(kind=="tip")512 else 4096)},{bitmap->
            busy(null)
            if(kind=="reference") {showReference(bitmap);return@work}
            if(kind=="layer" && p.layers.size>=32){bitmap.recycle();message("Layer limit reached");return@work}
            // Encoding uses the file worker rather than blocking the UI.
            busy("Saving imported asset...")
            val w=bitmap.width;val h=bitmap.height
            work({try{assets.store(bitmap)}finally{bitmap.recycle()}},{id->
                busy(null)
                if(kind=="tip") {
                    canvas.brush=canvas.brush.copy(name="Custom tip",tip=Tip.CUSTOM,asset=id)
                    closePanel();brushStudio()
                } else {
                    val scale=min(p.width.toFloat()/w,p.height.toFloat()/h)
                    val op=Operation.Image(asset=id,x=(p.width-w*scale)/2,y=(p.height-h*scale)/2,width=w*scale,height=h*scale)
                    val layers=p.layers.toMutableList();layers.add(p.active+1,Layer(name="Imported image",operations=listOf(op)))
                    applyProject(p.copy(layers=layers,active=p.active+1),"Import image");showPanel("layers")
                }
            })
        })
    }
    private fun showReference(bitmap:Bitmap) {
        referenceBitmap?.takeIf{it!==bitmap}?.recycle();referenceBitmap=bitmap
        reference?.let{(it.parent as? ViewGroup)?.let{parent->(parent.parent as? ViewGroup)?.removeView(parent)}}
        val frame=FrameLayout(this).apply{background=rounded(Theme.card,dp(12).toFloat(),Theme.line);elevation=dp(8).toFloat()}
        val image=ImageView(this).apply{setImageBitmap(bitmap);scaleType=ImageView.ScaleType.FIT_CENTER}
        frame.addView(image,FrameLayout.LayoutParams(-1,-1).apply{setMargins(dp(8),dp(36),dp(8),dp(8))})
        frame.addView(label("REFERENCE",10f,Theme.muted).apply{setPadding(dp(10),dp(10),0,0)},FrameLayout.LayoutParams(-1,dp(36)))
        frame.addView(iconButton(R.drawable.ic_close,"Close reference"){workspace.removeView(frame);reference=null;referenceBitmap=null},FrameLayout.LayoutParams(dp(48),dp(48),Gravity.TOP or Gravity.RIGHT))
        workspace.addView(frame,FrameLayout.LayoutParams(dp(220),dp(190),Gravity.TOP or Gravity.RIGHT).apply{setMargins(dp(10),dp(10),dp(16),dp(16))})
        var x=0f;var y=0f
        frame.setOnTouchListener{v,e->when(e.actionMasked){MotionEvent.ACTION_DOWN->{x=e.rawX;y=e.rawY};MotionEvent.ACTION_MOVE->{v.translationX+=e.rawX-x;v.translationY+=e.rawY-y;x=e.rawX;y=e.rawY}};true}
        reference=image;overlayHost.bringToFront()
    }
    private fun fillAt(x:Int,y:Int) {
        val layer=p.layers[p.active]
        if(layer.locked || !layer.visible){message("Select a visible, unlocked layer.");return}
        if(p.width.toLong()*p.height>4_194_304){message("Fill is limited to 4 megapixels in this alpha to keep memory use bounded.");return}
        var tolerance=24f;var allLayers=true;val c=column(18)
        slider(c,"Color tolerance",0f,100f,tolerance){tolerance=it}
        toggle(c,"Use visible layers as boundaries",true){allLayers=it}
        c.addView(label("Fills the active layer. Small contour-gap closing and edge expansion are not included yet.",12f,Theme.muted))
        AlertDialog.Builder(this).setTitle("Fill region").setView(c).setPositiveButton("Fill"){_,_->performFill(x,y,tolerance.roundToInt(),allLayers)}.setNegativeButton("Cancel",null).show()
    }
    private fun performFill(x:Int,y:Int,tolerance:Int,allLayers:Boolean) {
        val snapshot=p;val layer=snapshot.layers[snapshot.active];val color=canvas.color
        val selection=canvas.activeSelection()
        if(selection!=null && !selection.contains(x+.5f,y+.5f)){message("Tap inside the selection.");return}
        busy("Filling region...")
        work({
            RasterRenderer(assets).use{renderer->
                val w=snapshot.width;val h=snapshot.height
                val source=IntArray(w*h)
                if(allLayers) {
                    val bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
                    try {val target=Canvas(bitmap);val inverse=Matrix();renderer.matrix(snapshot,layer).invert(inverse);target.concat(inverse);renderer.compose(target,snapshot);bitmap.getPixels(source,0,w,0,0,w,h)}finally{bitmap.recycle()}
                } else renderer.layer(snapshot,layer).getPixels(source,0,w,0,0,w,h)
                val selected=selection?.rasterMask(w,h)
                val mask=FloodFill.mask(source,w,h,x,y,tolerance,selected)
                val pixels=IntArray(w*h)
                if(layer.alphaLock)renderer.layer(snapshot,layer).getPixels(pixels,0,w,0,0,w,h)
                for(i in pixels.indices)if(mask[i] && (selected==null || selected[i])) {
                    pixels[i]=if(layer.alphaLock)(pixels[i] and 0xff000000.toInt()) or (color and 0xffffff) else color
                }
                val bitmap=Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888)
                val id=try{assets.store(bitmap)}finally{bitmap.recycle()}
                Operation.Image(asset=id,x=0f,y=0f,width=w.toFloat(),height=h.toFloat())
            }
        },{op->
            val operations=if(layer.alphaLock)listOf(op) else layer.operations+op
            val layers=snapshot.layers.toMutableList();layers[snapshot.active]=layer.copy(operations=operations)
            applyProject(snapshot.copy(layers=layers),"Fill region");busy(null)
        })
    }
}
