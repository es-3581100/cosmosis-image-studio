package studio.cosmosis.ui

import org.openrndr.math.Vector2

/**
 * Screen-space hit rectangle used by the OPENRNDR single-window shell.
 *
 * The workstation drawing geometry remains authoritative in OpenrndrWorkspace;
 * this type only binds visible controls to pointer actions.
 */
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
