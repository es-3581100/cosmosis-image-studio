package studio.cosmosis.ui

import org.openrndr.math.Vector2

data class UiRect(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double
) {
    val right: Double get() = x + width
    val bottom: Double get() = y + height
    fun contains(point: Vector2): Boolean =
        point.x >= x && point.x <= right && point.y >= y && point.y <= bottom
}

data class SingleWindowLayout(
    val width: Int,
    val height: Int,
    val top: UiRect,
    val rail: UiRect,
    val stage: UiRect,
    val inspector: UiRect?,
    val timeline: UiRect,
    val prompt: UiRect,
    val compact: Boolean
)

object SingleWindowLayoutEngine {
    fun compute(width: Int, height: Int): SingleWindowLayout {
        require(width >= 640 && height >= 560) { "Single-window workspace requires at least 640x560" }

        val compact = width < 1180 || height < 760
        val topHeight = if (compact) 60.0 else 68.0
        val railWidth = if (compact) 118.0 else 139.0
        val inspectorWidth = when {
            width < 1040 -> 0.0
            width < 1380 -> 252.0
            else -> 286.0
        }
        val promptHeight = if (compact) 116.0 else 132.0
        val timelineHeight = if (compact) 68.0 else 82.0
        val gap = 13.0

        val centerWidth = (width - railWidth - inspectorWidth).coerceAtLeast(420.0)
        val stageHeight = (height - topHeight - promptHeight - timelineHeight - gap * 2.0).coerceAtLeast(260.0)

        val top = UiRect(0.0, 0.0, width.toDouble(), topHeight)
        val rail = UiRect(0.0, topHeight, railWidth, height - topHeight)
        val inspector = inspectorWidth.takeIf { it > 0.0 }?.let {
            UiRect(width - it, topHeight, it, height - topHeight)
        }
        val stage = UiRect(
            railWidth + gap,
            topHeight + gap,
            centerWidth - gap * 2.0,
            stageHeight
        )
        val timeline = UiRect(
            railWidth,
            stage.bottom + gap,
            centerWidth,
            timelineHeight
        )
        val prompt = UiRect(
            railWidth,
            height - promptHeight,
            centerWidth,
            promptHeight
        )

        return SingleWindowLayout(width, height, top, rail, stage, inspector, timeline, prompt, compact)
    }
}
