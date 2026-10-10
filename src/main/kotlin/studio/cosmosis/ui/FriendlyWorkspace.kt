package studio.cosmosis.ui

import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.color.Linearity
import org.openrndr.draw.ColorBuffer
import org.openrndr.draw.loadFont
import org.openrndr.draw.loadImage
import org.openrndr.math.IntVector2
import org.openrndr.math.Vector2
import studio.cosmosis.WorkflowMode
import studio.cosmosis.theme.OffworldTheme
import java.awt.Rectangle
import java.awt.Robot
import java.awt.Toolkit
import java.awt.Window
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import javax.imageio.ImageIO
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.SwingUtilities
import kotlin.math.min

private fun java.awt.Color.toFriendlyColor() =
    ColorRGBa(red / 255.0, green / 255.0, blue / 255.0, alpha / 255.0, Linearity.SRGB)

private val F_BG = OffworldTheme.background.toFriendlyColor()
private val F_FG = OffworldTheme.foreground.toFriendlyColor()
private val F_MUTED = OffworldTheme.muted.toFriendlyColor()
private val F_SECOND = OffworldTheme.secondary.toFriendlyColor()
private val F_GREEN = OffworldTheme.positive.toFriendlyColor()
private val F_RED = OffworldTheme.destructive.toFriendlyColor()
private val F_SURFACE = ColorRGBa.fromHex("#151511")
private val F_SURFACE_2 = ColorRGBa.fromHex("#1B1B17")
private val F_FIELD = ColorRGBa.fromHex("#0D0D0B")

private data class FriendlyHit(val rect: UiRect, val action: () -> Unit)

private fun drawFriendlyRect(drawer: org.openrndr.draw.Drawer, rect: UiRect) {
    drawer.rectangle(rect.x, rect.y, rect.width, rect.height)
}

private fun wrapFriendly(text: String, width: Int, maxLines: Int): List<String> {
    if (width < 4 || maxLines <= 0) return emptyList()
    val out = mutableListOf<String>()
    for (paragraph in text.replace("\r", "").split('\n')) {
        if (out.size >= maxLines) break
        if (paragraph.isBlank()) {
            out += ""
            continue
        }
        var line = ""
        for (word in paragraph.split(Regex("\\s+")).filter { it.isNotBlank() }) {
            if (out.size >= maxLines) break
            if (line.isBlank()) line = word
            else if (line.length + 1 + word.length <= width) line += " " + word
            else {
                out += line
                line = word
            }
        }
        if (line.isNotBlank() && out.size < maxLines) out += line
    }
    return out.take(maxLines)
}

private fun friendlySha256(path: Path): String =
    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))
        .joinToString("") { "%02x".format(it) }

private fun friendlyNear(rgb: Int, target: java.awt.Color, tolerance: Int = 14): Boolean {
    val r = (rgb shr 16) and 255
    val g = (rgb shr 8) and 255
    val b = rgb and 255
    return kotlin.math.abs(r - target.red) <= tolerance &&
        kotlin.math.abs(g - target.green) <= tolerance &&
        kotlin.math.abs(b - target.blue) <= tolerance
}

private fun writeFriendlySmoke(
    config: WorkspaceSmokeConfig,
    frame: Long,
    workspaceWidth: Int,
    workspaceHeight: Int,
    composerWidth: Double,
    historyWidth: Double
): Boolean {
    config.reportPath.parent?.let(Files::createDirectories)
    config.screenshotPath.parent?.let(Files::createDirectories)
    val errors = mutableListOf<String>()
    val desktop = Toolkit.getDefaultToolkit().screenSize
    var dark = 0L
    var ivory = 0L
    var sampled = 0L
    var screenshotHash = "unavailable"

    runCatching {
        val capture = Robot().createScreenCapture(Rectangle(desktop))
        check(ImageIO.write(capture, "png", config.screenshotPath.toFile()))
        for (y in 0 until capture.height step 3) {
            for (x in 0 until capture.width step 3) {
                val rgb = capture.getRGB(x, y)
                sampled++
                if (friendlyNear(rgb, OffworldTheme.background)) dark++
                if (friendlyNear(rgb, OffworldTheme.foreground)) ivory++
            }
        }
        screenshotHash = friendlySha256(config.screenshotPath)
    }.onFailure { errors += "screenshot: " + (it.message ?: it.javaClass.simpleName) }

    val visibleSwing = Window.getWindows().count { it.isShowing }
    if (visibleSwing != 0) errors += "persistent Swing window visible: " + visibleSwing
    if (OffworldTheme.radius != 0) errors += "Offworld hard-corner authority regressed"
    if (dark <= 1000) errors += "Offworld warm-dark surface not visible enough"
    if (ivory <= 10) errors += "Offworld ivory foreground not visible enough"
    if (composerWidth < 240.0) errors += "composer is too narrow"
    if (historyWidth < 200.0) errors += "history is too narrow"

    val passed = errors.isEmpty()
    Files.writeString(config.reportPath, buildString {
        appendLine(if (passed) "UI_SMOKE_PASS" else "UI_SMOKE_FAIL")
        appendLine("layout.mode=single-window")
        appendLine("layout.family=friendly-editor")
        appendLine("layout.primaryFlow=generate-edit-mask")
        appendLine("composer.directPrompt=true")
        appendLine("history.visual=true")
        appendLine("dock.showing=false")
        appendLine("frame=" + frame)
        appendLine("workspace=" + workspaceWidth + "x" + workspaceHeight)
        appendLine("desktop=" + desktop.width + "x" + desktop.height)
        appendLine("composer.width=" + composerWidth.toInt())
        appendLine("history.width=" + historyWidth.toInt())
        appendLine("theme.background=#10100E")
        appendLine("theme.foreground=#FFFFE3")
        appendLine("theme.radius=" + OffworldTheme.radius)
        appendLine("sampled=" + sampled)
        appendLine("warmDarkPixels=" + dark)
        appendLine("ivoryPixels=" + ivory)
        appendLine("screenshot=" + config.screenshotPath)
        appendLine("screenshot.sha256=" + screenshotHash)
        errors.forEach { appendLine("error=" + it) }
    })
    return passed
}

fun launchFriendlyWorkspace(
    state: StudioState,
    controller: StudioController,
    smoke: WorkspaceSmokeConfig? = null
) = application {
    val desktop = Toolkit.getDefaultToolkit().screenSize
    val workspaceWidth = (desktop.width - 28).coerceIn(960, 1600)
    val workspaceHeight = (desktop.height - 72).coerceIn(640, 980)

    configure {
        width = workspaceWidth
        height = workspaceHeight
        title = "COSMOSIS / IMAGE STUDIO"
        position = IntVector2(14, 14)
    }

    program {
        val sansPath = listOf(
            "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
            "/usr/share/fonts/truetype/liberation2/LiberationSans-Regular.ttf"
        ).firstOrNull { Files.exists(Path.of(it)) }
        val boldPath = listOf(
            "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
            "/usr/share/fonts/truetype/liberation2/LiberationSans-Bold.ttf"
        ).firstOrNull { Files.exists(Path.of(it)) }
        val monoPath = listOf(
            "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf",
            "/usr/share/fonts/truetype/liberation2/LiberationMono-Regular.ttf"
        ).firstOrNull { Files.exists(Path.of(it)) }

        val ui = sansPath?.let { loadFont(it, 11.0) }
        val uiTiny = sansPath?.let { loadFont(it, 9.5) }
        val uiBold = boldPath?.let { loadFont(it, 12.5) } ?: ui
        val uiTitle = boldPath?.let { loadFont(it, 17.0) } ?: ui
        val mono = monoPath?.let { loadFont(it, 9.5) }

        var currentPath: String? = null
        var currentImage: ColorBuffer? = null
        var comparePath: String? = null
        var compareImage: ColorBuffer? = null
        var overlayPath: String? = null
        var overlayImage: ColorBuffer? = null
        val thumbs = linkedMapOf<String, ColorBuffer>()
        val thumbFailures = mutableSetOf<String>()

        var zoom = 1.0
        var pan = Vector2.ZERO
        var lastDrag: Vector2? = null
        var promptFocused = false
        var composerCollapsed = false
        var historyCollapsed = false
        var historyScroll = 0.0
        var hitTargets: List<FriendlyHit> = emptyList()
        var imageRect: UiRect? = null
        var smokeDone = false
        var advancedDock: ControlDock? = null

        fun label(text: String, x: Double, y: Double, color: ColorRGBa = F_FG, bold: Boolean = false, tiny: Boolean = false) {
            val font = if (tiny) uiTiny else if (bold) uiBold else ui
            if (font == null) return
            drawer.fontMap = font
            drawer.fill = color
            drawer.stroke = null
            drawer.text(text, x, y)
        }

        fun fail(t: Throwable) {
            val message = t.message ?: t.toString()
            state.update { it.copy(message = "ERROR / " + message) }
            SwingUtilities.invokeLater {
                JOptionPane.showMessageDialog(null, message, "COSMOSIS / ERROR", JOptionPane.ERROR_MESSAGE)
            }
        }

        fun chooseProject(create: Boolean) {
            SwingUtilities.invokeLater {
                val chooser = JFileChooser().apply {
                    dialogTitle = if (create) "Create COSMOSIS project" else "Open COSMOSIS project"
                    fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                }
                if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return@invokeLater
                runCatching {
                    if (create) controller.createProject(chooser.selectedFile.toPath(), chooser.selectedFile.name)
                    else controller.openProject(chooser.selectedFile.toPath())
                }.onFailure(::fail)
            }
        }

        fun chooseImage(reference: Boolean = false) {
            SwingUtilities.invokeLater {
                val chooser = JFileChooser().apply {
                    dialogTitle = if (reference) "Add reference images" else "Choose image"
                    isMultiSelectionEnabled = reference
                }
                if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return@invokeLater
                runCatching {
                    if (reference) {
                        val files = chooser.selectedFiles.takeIf { it.isNotEmpty() }?.toList()
                            ?: listOfNotNull(chooser.selectedFile)
                        files.forEach { controller.addReferenceImage(it.toPath()) }
                    } else controller.importImage(chooser.selectedFile.toPath())
                }.onFailure(::fail)
            }
        }

        fun exportCurrent() {
            SwingUtilities.invokeLater {
                if (state.get().imagePath == null) {
                    fail(IllegalStateException("There is no image to export yet"))
                    return@invokeLater
                }
                val chooser = JFileChooser().apply { selectedFile = java.io.File("cosmosis-image.png") }
                if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) return@invokeLater
                runCatching { controller.exportCurrent(chooser.selectedFile.toPath()) }.onFailure(::fail)
            }
        }

        fun openAdvanced() {
            SwingUtilities.invokeLater {
                val existing = advancedDock
                if (existing == null || !existing.isDisplayable) advancedDock = ControlDock(controller, state)
                else {
                    existing.isVisible = true
                    existing.toFront()
                    existing.requestFocus()
                }
            }
        }

        fun cycleProvider() {
            val ids = listOf("local", "openai", "gemini", "litellm")
            val available = ids.filter { id ->
                runCatching { controller.modelsFor(id).isNotEmpty() }.getOrDefault(false)
            }
            if (available.isEmpty()) return
            val current = state.get().provider.lowercase()
            val index = available.indexOf(current)
            val next = available[(if (index < 0) 0 else index + 1) % available.size]
            val model = controller.modelsFor(next).first().id
            state.update { it.copy(provider = next.uppercase(), model = model, message = "Provider / " + next.uppercase()) }
        }

        fun cycleModel() {
            val snapshot = state.get()
            val id = snapshot.provider.lowercase()
            val models = runCatching { controller.modelsFor(id) }.getOrDefault(emptyList())
            if (models.isEmpty()) return
            val index = models.indexOfFirst { it.id == snapshot.model }
            val next = models[(if (index < 0) 0 else index + 1) % models.size]
            state.update { it.copy(model = next.id, message = "Model / " + next.id) }
        }

        fun modeSupported(snapshot:UiSnapshot,mode:WorkflowMode):Boolean {
            val caps=runCatching { controller.capabilitiesFor(snapshot.provider.lowercase(),snapshot.model) }.getOrNull()
                ?: return false
            return when(mode){
                WorkflowMode.QUICK_GENERATE -> caps.textToImage
                WorkflowMode.EDIT_EXISTING -> caps.imageToImage
                WorkflowMode.MASK_EDIT -> caps.maskEditing
                else -> true
            }
        }

        fun unsupportedReason(snapshot:UiSnapshot,mode:WorkflowMode):String = when {
            snapshot.provider.equals("CUSTOM",true) &&
                snapshot.model=="inclusionai/ming-image-0.1-design" &&
                mode==WorkflowMode.EDIT_EXISTING ->
                "This Ming model is Generate-only. For free Ming editing use inclusionai/ming-image-0.1-design-layer."
            mode==WorkflowMode.EDIT_EXISTING -> "Selected model does not support image editing."
            mode==WorkflowMode.MASK_EDIT -> "Selected model does not support mask editing."
            mode==WorkflowMode.QUICK_GENERATE -> "Selected model does not support prompt-only generation."
            else -> "Selected model does not support this workflow."
        }

        fun runCurrent() {
            val snapshot = state.get()
            if(snapshot.workflowMode in setOf(WorkflowMode.QUICK_GENERATE,WorkflowMode.EDIT_EXISTING,WorkflowMode.MASK_EDIT) &&
                !modeSupported(snapshot,snapshot.workflowMode)){
                state.update{it.copy(message="UNAVAILABLE / "+unsupportedReason(snapshot,snapshot.workflowMode))}
                return
            }
            runCatching {
                when (snapshot.workflowMode) {
                    WorkflowMode.IMAGE_TO_PROMPT -> controller.analyzeCurrent()
                    WorkflowMode.UPSCALE -> controller.upscaleCurrent(2)
                    WorkflowMode.AGENT_BUILD -> {
                        require(snapshot.promptBody.isNotBlank()) { "Describe what you want the agent to build" }
                        controller.runAgentBuild(snapshot.promptBody, snapshot.provider.lowercase(), snapshot.model)
                    }
                    else -> {
                        require(snapshot.promptBody.isNotBlank()) { "Write a prompt first" }
                        val editing = snapshot.workflowMode in setOf(
                            WorkflowMode.EDIT_EXISTING,
                            WorkflowMode.MASK_EDIT,
                            WorkflowMode.REFERENCE_REMIX,
                            WorkflowMode.STYLE_TRANSFER,
                            WorkflowMode.BACKGROUND_REPLACE,
                            WorkflowMode.SUBJECT_PRESERVE
                        )
                        if (editing) require(snapshot.imagePath != null) { "Choose an image to edit first" }
                        if (snapshot.workflowMode == WorkflowMode.MASK_EDIT) {
                            require(snapshot.maskPath != null) { "Create a mask first" }
                        }
                        controller.generate(
                            prompt = snapshot.promptBody,
                            providerId = snapshot.provider.lowercase(),
                            model = snapshot.model,
                            edit = editing,
                            workflowMode = snapshot.workflowMode
                        )
                    }
                }
            }.onFailure(::fail)
        }

        fun previousVersionId(): String? {
            val versions = controller.versionPreviews()
            val current = versions.indexOfFirst { it.current }
            return when {
                current > 0 -> versions[current - 1].id
                versions.size > 1 -> versions[versions.lastIndex - 1].id
                else -> null
            }
        }

        fun setMode(mode: WorkflowMode) {
            promptFocused = false
            val snapshot=state.get()
            if(!modeSupported(snapshot,mode)){
                state.update{it.copy(message="UNAVAILABLE / "+unsupportedReason(snapshot,mode))}
                return
            }
            controller.setWorkflowMode(mode)
        }

        window.drop.listen { dropped ->
            dropped.files.firstOrNull {
                java.io.File(it).extension.lowercase() in setOf("png", "jpg", "jpeg", "webp")
            }?.let { path -> runCatching { controller.importImage(Path.of(path)) }.onFailure(::fail) }
        }

        mouse.scrolled.listen { event ->
            val composerWidth = if (composerCollapsed) 48.0 else if (width < 1200) 286.0 else 326.0
            val historyWidth = if (historyCollapsed) 48.0 else if (width < 1200) 238.0 else 286.0
            if (event.position.x >= width - historyWidth && !historyCollapsed) {
                historyScroll = (historyScroll + event.rotation.y * 72.0).coerceAtLeast(0.0)
            } else if (event.position.x > composerWidth && event.position.x < width - historyWidth) {
                zoom = (zoom * if (event.rotation.y < 0) 1.1 else 0.9).coerceIn(0.1, 8.0)
                state.update { it.copy(zoom = zoom) }
            }
        }

        mouse.buttonDown.listen { event ->
            val target = hitTargets.lastOrNull { it.rect.contains(event.position) }
            if (target != null) {
                target.action()
                lastDrag = null
                return@listen
            }
            promptFocused = false
            if (imageRect?.contains(event.position) == true && state.get().workflowMode != WorkflowMode.MASK_EDIT) {
                lastDrag = event.position
            }
        }

        mouse.buttonUp.listen { lastDrag = null }

        mouse.dragged.listen { event ->
            val previous = lastDrag ?: return@listen
            pan += event.position - previous
            lastDrag = event.position
            state.update { it.copy(panX = pan.x, panY = pan.y) }
        }

        keyboard.character.listen { event ->
            if (!promptFocused) return@listen
            val ch = event.character
            if (ch.code >= 32 && ch != '\u007f') {
                state.update { it.copy(promptBody = it.promptBody + ch, message = "Prompt editing") }
            }
        }

        keyboard.keyDown.listen { event ->
            val ctrl = event.modifiers.any { it.name == "CTRL" || it.name == "META" || it.name == "SUPER" }
            val name = event.name.lowercase()
            when {
                ctrl && name == "enter" -> runCurrent()
                promptFocused && name == "backspace" -> state.update {
                    it.copy(promptBody = it.promptBody.dropLast(1), message = "Prompt editing")
                }
                promptFocused && name == "enter" -> state.update {
                    it.copy(promptBody = it.promptBody + "\n", message = "Prompt editing")
                }
                name == "escape" -> promptFocused = false
                !promptFocused && name == "g" -> setMode(WorkflowMode.QUICK_GENERATE)
                !promptFocused && name == "e" -> setMode(WorkflowMode.EDIT_EXISTING)
                !promptFocused && name == "m" -> setMode(WorkflowMode.MASK_EDIT)
                !promptFocused && name == "h" -> historyCollapsed = !historyCollapsed
                !promptFocused && name == "p" -> composerCollapsed = !composerCollapsed
                !promptFocused && name == "c" -> {
                    if (state.get().compareMode == "OFF") controller.setCompareVersion(previousVersionId())
                    else controller.setCompareVersion(null)
                }
                !promptFocused && name == "d" -> exportCurrent()
                !promptFocused && name == "0" -> {
                    zoom = 1.0
                    pan = Vector2.ZERO
                    controller.resetView()
                }
            }
        }

        extend {
            val snapshot = state.get()
            val targets = mutableListOf<FriendlyHit>()
            val composerWidth = if (composerCollapsed) 48.0 else if (width < 1200) 286.0 else 326.0
            val historyWidth = if (historyCollapsed) 48.0 else if (width < 1200) 238.0 else 286.0
            val headerHeight = 56.0
            val statusHeight = 28.0
            val canvasLeft = composerWidth
            val canvasRight = width - historyWidth
            val canvasArea = UiRect(canvasLeft, headerHeight, canvasRight - canvasLeft, height - headerHeight - statusHeight)

            fun button(
                rect: UiRect,
                text: String,
                active: Boolean = false,
                primary: Boolean = false,
                enabled: Boolean = true,
                action: () -> Unit
            ) {
                if(enabled) targets += FriendlyHit(rect, action)
                drawer.stroke = when {
                    !enabled -> F_FG.opacify(.08)
                    active || primary -> F_FG.opacify(.9)
                    else -> F_FG.opacify(.18)
                }
                drawer.strokeWeight = if (enabled && (active || primary)) 1.2 else 1.0
                drawer.fill = when {
                    !enabled -> F_BG.opacify(.65)
                    primary -> F_FG
                    active -> F_FG.opacify(.09)
                    else -> F_SURFACE_2
                }
                drawFriendlyRect(drawer, rect)
                label(
                    text,
                    rect.x + 9.0,
                    rect.y + rect.height / 2.0 + 4.0,
                    when {
                        !enabled -> F_SECOND.opacify(.55)
                        primary -> F_BG
                        active -> F_FG
                        else -> F_MUTED
                    },
                    bold = enabled && (primary || active)
                )
            }

            drawer.clear(F_BG)

            drawer.fill = F_SURFACE
            drawer.stroke = F_FG.opacify(.12)
            drawer.rectangle(0.0, 0.0, width.toDouble(), headerHeight)

            if (uiTitle != null) {
                drawer.fontMap = uiTitle
                drawer.fill = F_FG
                drawer.text("COSMOSIS", 18.0, 25.0)
            }
            label(if (snapshot.projectName == "NO PROJECT") "Image Studio" else snapshot.projectName, 18.0, 44.0, F_MUTED, tiny = true)

            var hx = 150.0
            fun headerButton(text: String, w: Double, action: () -> Unit) {
                if (hx + w < width - 132.0) {
                    button(UiRect(hx, 12.0, w, 32.0), text, action = action)
                    hx += w + 6.0
                }
            }
            headerButton("New project", 86.0) { chooseProject(true) }
            headerButton("Open", 58.0) { chooseProject(false) }
            headerButton("Import image", 92.0) { chooseImage(false) }
            button(UiRect(width - 106.0, 12.0, 88.0, 32.0), "Advanced") { openAdvanced() }

            drawer.fill = F_SURFACE
            drawer.stroke = F_FG.opacify(.12)
            drawer.rectangle(0.0, headerHeight, composerWidth, height - headerHeight - statusHeight)

            if (composerCollapsed) {
                label("P", 19.0, headerHeight + 31.0, F_MUTED, bold = true)
                button(UiRect(8.0, headerHeight + 46.0, 32.0, 32.0), ">") { composerCollapsed = false }
            } else {
                val pad = 16.0
                val innerW = composerWidth - pad * 2.0
                var cy = headerHeight + 18.0

                label("Create", pad, cy, F_FG, bold = true)
                cy += 17.0
                label("Choose a mode, describe it, then run.", pad, cy, F_MUTED, tiny = true)
                cy += 15.0

                val gap = 6.0
                val tabW = (innerW - gap * 2.0) / 3.0
                val modes = listOf(
                    "Generate" to WorkflowMode.QUICK_GENERATE,
                    "Edit" to WorkflowMode.EDIT_EXISTING,
                    "Mask" to WorkflowMode.MASK_EDIT
                )
                modes.forEachIndexed { index, pair ->
                    val supported=modeSupported(snapshot,pair.second)
                    button(
                        UiRect(pad + index * (tabW + gap), cy, tabW, 36.0),
                        pair.first,
                        active = snapshot.workflowMode == pair.second,
                        enabled = supported
                    ) { setMode(pair.second) }
                }
                cy += 43.0
                val selectedModeSupported=modeSupported(snapshot,snapshot.workflowMode)
                if(!selectedModeSupported){
                    label(unsupportedReason(snapshot,snapshot.workflowMode).take(52),pad,cy,F_RED,tiny=true)
                    cy += 17.0
                }else{
                    val capabilityHint=when{
                        snapshot.provider.equals("CUSTOM",true) && snapshot.model=="inclusionai/ming-image-0.1-design" ->
                            "Ming Design / Generate only"
                        snapshot.provider.equals("CUSTOM",true) && snapshot.model=="inclusionai/ming-image-0.1-design-layer" ->
                            "Ming Design Layer / Edit only / 1 image"
                        else -> null
                    }
                    capabilityHint?.let{label(it,pad,cy,F_SECOND,tiny=true);cy+=17.0}
                }
                cy += 7.0

                if (snapshot.workflowMode in setOf(WorkflowMode.EDIT_EXISTING, WorkflowMode.MASK_EDIT)) {
                    label("Editing", pad, cy, F_MUTED, tiny = true)
                    cy += 10.0
                    if (snapshot.imagePath == null) {
                        button(UiRect(pad, cy, innerW, 50.0), "Choose an image to edit") { chooseImage(false) }
                    } else {
                        drawer.fill = F_FIELD
                        drawer.stroke = F_FG.opacify(.14)
                        drawFriendlyRect(drawer, UiRect(pad, cy, innerW, 50.0))
                        label("Current canvas", pad + 10.0, cy + 18.0, F_FG, bold = true)
                        label(snapshot.imageWidth.toString() + " × " + snapshot.imageHeight, pad + 10.0, cy + 36.0, F_MUTED, tiny = true)
                        button(UiRect(pad + innerW - 72.0, cy + 10.0, 62.0, 30.0), "Replace") { chooseImage(false) }
                    }
                    cy += 64.0
                }

                val promptLabel = when (snapshot.workflowMode) {
                    WorkflowMode.EDIT_EXISTING -> "What should change?"
                    WorkflowMode.MASK_EDIT -> "What should happen in the mask?"
                    else -> "Prompt"
                }
                label(promptLabel, pad, cy, F_FG, bold = true)
                cy += 9.0

                val promptHeight = if (height < 760) 100.0 else 126.0
                val promptRect = UiRect(pad, cy, innerW, promptHeight)
                targets += FriendlyHit(promptRect) { promptFocused = true }
                drawer.fill = F_FIELD
                drawer.stroke = if (promptFocused) F_FG.opacify(.78) else F_FG.opacify(.16)
                drawFriendlyRect(drawer, promptRect)

                val placeholder = when (snapshot.workflowMode) {
                    WorkflowMode.EDIT_EXISTING -> "Replace the sky with a dramatic sunset and keep everything else the same…"
                    WorkflowMode.MASK_EDIT -> "Turn the selected area into fresh flowers…"
                    else -> "Describe the image you want to create…"
                }
                val shown = snapshot.promptBody.ifBlank { placeholder }
                val lines = wrapFriendly(shown, ((innerW - 20.0) / 6.4).toInt(), if (height < 760) 5 else 7)
                lines.forEachIndexed { index, line ->
                    label(line, promptRect.x + 10.0, promptRect.y + 20.0 + index * 16.0, if (snapshot.promptBody.isBlank()) F_SECOND else F_FG)
                }
                if (promptFocused) label("typing…", promptRect.right - 48.0, promptRect.bottom - 8.0, F_SECOND, tiny = true)
                cy = promptRect.bottom + 18.0

                label("Reference images", pad, cy, F_FG, bold = true)
                label(snapshot.referencePaths.size.toString() + " attached", composerWidth - 82.0, cy, F_MUTED, tiny = true)
                cy += 10.0

                val thumbSize = 48.0
                val refGap = 7.0
                snapshot.referencePaths.take(4).forEachIndexed { index, path ->
                    val x = pad + index * (thumbSize + refGap)
                    val rect = UiRect(x, cy, thumbSize, thumbSize)
                    drawer.fill = F_FIELD
                    drawer.stroke = F_FG.opacify(.16)
                    drawFriendlyRect(drawer, rect)
                    val img = thumbs[path] ?: if (path !in thumbFailures) {
                        runCatching { loadImage(path) }.onFailure { thumbFailures += path }.getOrNull()?.also { thumbs[path] = it }
                    } else null
                    img?.let { drawer.image(it, rect.x, rect.y, rect.width, rect.height) }
                }
                val addIndex = snapshot.referencePaths.size.coerceAtMost(4)
                if (addIndex < 4) {
                    button(UiRect(pad + addIndex * (thumbSize + refGap), cy, thumbSize, thumbSize), "+ Ref") { chooseImage(true) }
                }
                cy += thumbSize + 18.0

                label("Model", pad, cy, F_FG, bold = true)
                cy += 8.0
                val providerW = 82.0
                button(UiRect(pad, cy, providerW, 34.0), snapshot.provider) { cycleProvider() }
                button(UiRect(pad + providerW + 6.0, cy, innerW - providerW - 6.0, 34.0), snapshot.model.take(26)) { cycleModel() }
                cy += 49.0

                label("Quick tools", pad, cy, F_MUTED, tiny = true)
                cy += 8.0
                val toolGap = 6.0
                val toolW = (innerW - toolGap * 2.0) / 3.0
                button(UiRect(pad, cy, toolW, 31.0), "Analyze") {
                    runCatching { controller.analyzeCurrent() }.onFailure(::fail)
                }
                button(UiRect(pad + toolW + toolGap, cy, toolW, 31.0), "Remix") {
                    setMode(WorkflowMode.REFERENCE_REMIX)
                }
                button(UiRect(pad + (toolW + toolGap) * 2.0, cy, toolW, 31.0), "Upscale") {
                    runCatching { controller.upscaleCurrent(2) }.onFailure(::fail)
                }

                val actionH = 45.0
                val actionY = height - statusHeight - actionH - 16.0
                if (snapshot.workflowMode == WorkflowMode.MASK_EDIT && snapshot.maskPath == null) {
                    button(UiRect(pad, actionY - 39.0, innerW, 31.0), "Auto mask subject") {
                        runCatching { controller.smartSaliencyMask() }.onFailure(::fail)
                    }
                }
                val busy=snapshot.jobState in setOf("QUEUED","RUNNING","WAITING")
                val actionText = if(busy) "Cancel job" else when (snapshot.workflowMode) {
                    WorkflowMode.EDIT_EXISTING -> "Apply edit"
                    WorkflowMode.MASK_EDIT -> "Apply masked edit"
                    WorkflowMode.REFERENCE_REMIX -> "Create remix"
                    else -> "Generate image"
                }
                val actionSupported=busy || modeSupported(snapshot,snapshot.workflowMode)
                button(UiRect(pad, actionY, innerW, actionH), actionText, primary = true, enabled = actionSupported) {
                    if(busy) controller.cancelActiveJobs() else runCurrent()
                }
                label(if(busy)"Provider request in progress" else "Ctrl + Enter", composerWidth - if(busy)151.0 else 87.0, actionY - 8.0, F_SECOND, tiny = true)
            }

            drawer.fill = F_BG
            drawer.stroke = F_FG.opacify(.10)
            drawFriendlyRect(drawer, canvasArea)

            val canvasInset = 18.0
            val toolbarY = headerHeight + 12.0
            var tx = canvasLeft + canvasInset
            fun canvasTool(text: String, w: Double, active: Boolean = false, action: () -> Unit) {
                button(UiRect(tx, toolbarY, w, 30.0), text, active = active, action = action)
                tx += w + 6.0
            }
            canvasTool("Fit", 48.0) {
                zoom = 1.0
                pan = Vector2.ZERO
                controller.resetView()
            }
            canvasTool("Compare", 72.0, snapshot.compareMode != "OFF") {
                if (snapshot.compareMode == "OFF") controller.setCompareVersion(previousVersionId())
                else controller.setCompareVersion(null)
            }
            if (snapshot.maskPath != null) {
                canvasTool(if (snapshot.maskVisible) "Mask on" else "Mask off", 72.0, snapshot.maskVisible) {
                    controller.setMaskVisible(!snapshot.maskVisible)
                }
            }
            canvasTool("Export", 62.0) { exportCurrent() }

            val field = UiRect(
                canvasLeft + canvasInset,
                headerHeight + 54.0,
                canvasArea.width - canvasInset * 2.0,
                canvasArea.height - 70.0
            )
            drawer.fill = F_FIELD
            drawer.stroke = F_FG.opacify(.08)
            drawFriendlyRect(drawer, field)

            if (snapshot.projectName == "NO PROJECT") {
                imageRect = null
                val cardW = min(470.0, field.width - 60.0)
                val card = UiRect(field.x + (field.width - cardW) / 2.0, field.y + (field.height - 190.0) / 2.0, cardW, 190.0)
                drawer.fill = F_SURFACE
                drawer.stroke = F_FG.opacify(.16)
                drawFriendlyRect(drawer, card)
                if (uiTitle != null) {
                    drawer.fontMap = uiTitle
                    drawer.fill = F_FG
                    drawer.text("Start a COSMOSIS project", card.x + 24.0, card.y + 38.0)
                }
                label("Projects keep originals, edits, masks and history together.", card.x + 24.0, card.y + 64.0, F_MUTED)
                button(UiRect(card.x + 24.0, card.y + 92.0, 144.0, 40.0), "Create project", primary = true) { chooseProject(true) }
                button(UiRect(card.x + 178.0, card.y + 92.0, 116.0, 40.0), "Open project") { chooseProject(false) }
                label("After that, drop an image anywhere or start with Generate.", card.x + 24.0, card.y + 158.0, F_SECOND, tiny = true)
            } else if (snapshot.imagePath == null) {
                imageRect = null
                if (uiTitle != null) {
                    drawer.fontMap = uiTitle
                    drawer.fill = F_FG
                    drawer.text("Drop an image here", field.x + field.width / 2.0 - 74.0, field.y + field.height / 2.0 - 8.0)
                }
                label("or use Import image above", field.x + field.width / 2.0 - 72.0, field.y + field.height / 2.0 + 20.0, F_MUTED)
            } else {
                if (snapshot.imagePath != currentPath) {
                    runCatching {
                        currentImage?.destroy()
                        currentImage = loadImage(snapshot.imagePath)
                        currentPath = snapshot.imagePath
                    }.onFailure(::fail)
                }
                currentImage?.let { image ->
                    val compare = snapshot.comparePath.takeIf { snapshot.compareMode == "SPLIT" }
                    if (compare != null) {
                        if (compare != comparePath) {
                            runCatching {
                                compareImage?.destroy()
                                compareImage = loadImage(compare)
                                comparePath = compare
                            }.onFailure(::fail)
                        }
                        val gap = 12.0
                        val half = (field.width - gap) / 2.0
                        val fitA = min(half / image.width, field.height / image.height)
                        val aw = image.width * fitA * zoom
                        val ah = image.height * fitA * zoom
                        val ax = field.x + (half - aw) / 2.0 + pan.x
                        val ay = field.y + (field.height - ah) / 2.0 + pan.y
                        drawer.image(image, ax, ay, aw, ah)
                        compareImage?.let { cmp ->
                            val fitB = min(half / cmp.width, field.height / cmp.height)
                            val bw = cmp.width * fitB * zoom
                            val bh = cmp.height * fitB * zoom
                            val bx = field.x + half + gap + (half - bw) / 2.0 + pan.x
                            val by = field.y + (field.height - bh) / 2.0 + pan.y
                            drawer.image(cmp, bx, by, bw, bh)
                        }
                        imageRect = UiRect(ax, ay, aw, ah)
                        label("Current", field.x + 8.0, field.y + 17.0, F_MUTED, tiny = true)
                        label("Compare", field.x + half + gap + 8.0, field.y + 17.0, F_MUTED, tiny = true)
                    } else {
                        val fit = min(field.width / image.width, field.height / image.height)
                        val dw = image.width * fit * zoom
                        val dh = image.height * fit * zoom
                        val x = field.x + (field.width - dw) / 2.0 + pan.x
                        val y = field.y + (field.height - dh) / 2.0 + pan.y
                        imageRect = UiRect(x, y, dw, dh)
                        drawer.image(image, x, y, dw, dh)

                        if (snapshot.maskOverlayPath != null && snapshot.maskVisible) {
                            if (snapshot.maskOverlayPath != overlayPath) {
                                runCatching {
                                    overlayImage?.destroy()
                                    overlayImage = loadImage(snapshot.maskOverlayPath)
                                    overlayPath = snapshot.maskOverlayPath
                                }.onFailure(::fail)
                            }
                            overlayImage?.let { drawer.image(it, x, y, dw, dh) }
                        }

                        if (snapshot.analysisVisible && snapshot.analysisRegions.isNotEmpty()) {
                            drawer.fill = null
                            drawer.stroke = F_FG.opacify(.52)
                            snapshot.analysisRegions.take(40).forEach { region ->
                                val rx = x + region.x.toDouble() / image.width * dw
                                val ry = y + region.y.toDouble() / image.height * dh
                                val rw = region.width.toDouble() / image.width * dw
                                val rh = region.height.toDouble() / image.height * dh
                                drawer.rectangle(rx, ry, rw, rh)
                            }
                        }
                    }
                }
            }

            val historyX = width - historyWidth
            drawer.fill = F_SURFACE
            drawer.stroke = F_FG.opacify(.12)
            drawer.rectangle(historyX, headerHeight, historyWidth, height - headerHeight - statusHeight)

            if (historyCollapsed) {
                label("H", historyX + 18.0, headerHeight + 31.0, F_MUTED, bold = true)
                button(UiRect(historyX + 8.0, headerHeight + 46.0, 32.0, 32.0), "<") { historyCollapsed = false }
            } else {
                val versions = controller.versionPreviews()
                label("History", historyX + 14.0, headerHeight + 26.0, F_FG, bold = true)
                label(versions.size.toString(), width - 34.0, headerHeight + 26.0, F_MUTED, tiny = true)
                if (versions.isEmpty()) {
                    label("Nothing here yet", historyX + 14.0, headerHeight + 72.0, F_FG, bold = true)
                    label("Your imports, generations and edits", historyX + 14.0, headerHeight + 92.0, F_MUTED, tiny = true)
                    label("will appear here as visual history.", historyX + 14.0, headerHeight + 108.0, F_MUTED, tiny = true)
                }

                val gap = 8.0
                val pad = 12.0
                val cardW = (historyWidth - pad * 2.0 - gap) / 2.0
                val cardH = cardW + 26.0
                val startY = headerHeight + 44.0 - historyScroll

                versions.asReversed().forEachIndexed { index, version ->
                    val col = index % 2
                    val row = index / 2
                    val x = historyX + pad + col * (cardW + gap)
                    val y = startY + row * (cardH + gap)
                    if (y + cardH < headerHeight + 38.0 || y > height - statusHeight - 84.0) return@forEachIndexed
                    val card = UiRect(x, y, cardW, cardH)
                    targets += FriendlyHit(card) { runCatching { controller.setCurrentVersion(version.id) }.onFailure(::fail) }
                    drawer.fill = if (version.current) F_FG.opacify(.06) else F_FIELD
                    drawer.stroke = if (version.current) F_FG.opacify(.74) else F_FG.opacify(.14)
                    drawFriendlyRect(drawer, card)

                    val imageBox = UiRect(card.x + 4.0, card.y + 4.0, card.width - 8.0, card.width - 8.0)
                    version.path?.let { path ->
                        val img = thumbs[path] ?: if (path !in thumbFailures) {
                            runCatching { loadImage(path) }.onFailure { thumbFailures += path }.getOrNull()?.also { thumbs[path] = it }
                        } else null
                        img?.let { drawer.image(it, imageBox.x, imageBox.y, imageBox.width, imageBox.height) }
                    }
                    label(version.operation.lowercase().replace('_', ' ').take(16), card.x + 6.0, card.bottom - 12.0, if (version.current) F_FG else F_MUTED, tiny = true)
                }

                val selected = versions.firstOrNull { it.current }
                if (selected != null) {
                    val footerY = height - statusHeight - 76.0
                    drawer.fill = F_SURFACE_2
                    drawer.stroke = F_FG.opacify(.12)
                    drawer.rectangle(historyX, footerY, historyWidth, 76.0)
                    label(selected.name.take(28), historyX + 12.0, footerY + 20.0, F_FG, bold = true)
                    val bw = (historyWidth - 36.0) / 3.0
                    button(
                        UiRect(historyX + 12.0, footerY + 32.0, bw, 30.0),
                        "Edit",
                        enabled = modeSupported(snapshot,WorkflowMode.EDIT_EXISTING)
                    ) { setMode(WorkflowMode.EDIT_EXISTING) }
                    button(UiRect(historyX + 18.0 + bw, footerY + 32.0, bw, 30.0), "Compare") { controller.setCompareVersion(previousVersionId()) }
                    button(UiRect(historyX + 24.0 + bw * 2.0, footerY + 32.0, bw, 30.0), "Export") { exportCurrent() }
                }

                val keep = buildSet {
                    addAll(snapshot.referencePaths)
                    addAll(versions.takeLast(18).mapNotNull { it.path })
                }
                thumbs.keys.filter { it !in keep }.toList().forEach { path ->
                    thumbs.remove(path)?.destroy()
                    thumbFailures.remove(path)
                }
            }

            drawer.fill = F_SURFACE_2
            drawer.stroke = F_FG.opacify(.10)
            drawer.rectangle(0.0, (height - statusHeight).toDouble(), width.toDouble(), statusHeight)
            val busyStatus=snapshot.jobState in setOf("QUEUED","RUNNING","WAITING")
            if(busyStatus){
                drawer.stroke=null
                drawer.fill=F_FG.opacify(.82)
                if(snapshot.reducedMotion){
                    drawer.rectangle(0.0,(height-statusHeight).toDouble(),width*0.28,3.0)
                }else{
                    val span=(width*0.18).coerceIn(120.0,260.0)
                    val travel=width+span
                    val x=((frameCount*7.0)%travel)-span
                    drawer.rectangle(x,(height-statusHeight).toDouble(),span,3.0)
                }
            }
            val statusColor = when (snapshot.jobState) {
                "FAILED" -> F_RED
                "COMPLETE" -> F_GREEN
                "QUEUED","RUNNING","WAITING" -> F_FG
                else -> F_MUTED
            }
            label(snapshot.message.take(88), 14.0, height - 9.0, statusColor, tiny = true)
            if (mono != null) {
                drawer.fontMap = mono
                drawer.fill = F_SECOND
                val detail = snapshot.imageWidth.toString() + "×" + snapshot.imageHeight + "  ·  " +
                    String.format("%.0f", zoom * 100) + "%  ·  " + snapshot.provider + "/" + snapshot.model.take(20)
                drawer.text(detail, (width - historyWidth - 260.0).coerceAtLeast(composerWidth + 12.0), height - 9.0)
            }

            hitTargets = targets

            if (smoke != null && !smokeDone && frameCount.toLong() >= smoke.frames.toLong()) {
                smokeDone = true
                runCatching {
                    writeFriendlySmoke(smoke, frameCount.toLong(), width, height, composerWidth, historyWidth)
                }.onFailure { error ->
                    smoke.reportPath.parent?.let(Files::createDirectories)
                    Files.writeString(smoke.reportPath, "UI_SMOKE_FAIL\nerror=" + (error.message ?: error.javaClass.simpleName) + "\n")
                }
                application.exit()
            }
        }
    }
}
