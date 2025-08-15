package com.dilongdann.quickrun.toolbar

import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.Icon
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Envuelve un Icon para limitarlo a un máximo de 16x16 (escalado por JBUI) manteniendo proporciones.
 * - No amplifica iconos pequeños (si el icono es <= 16x16, se pinta tal cual).
 * - Si es más grande, se reduce proporcionalmente.
 */
class MaxSizeIcon(
    private val delegate: Icon,
    maxLogicalSize: Int = 16
) : Icon {
    private val max = JBUI.scale(maxLogicalSize)
    private val outW: Int
    private val outH: Int
    private val scale: Double

    init {
        val iw = delegate.iconWidth.coerceAtLeast(1)
        val ih = delegate.iconHeight.coerceAtLeast(1)
        scale = if (iw > max || ih > max) {
            min(max.toDouble() / iw, max.toDouble() / ih)
        } else {
            1.0
        }
        outW = (iw * scale).roundToInt().coerceAtLeast(1)
        outH = (ih * scale).roundToInt().coerceAtLeast(1)
    }

    override fun getIconWidth(): Int = outW
    override fun getIconHeight(): Int = outH

    override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
        if (scale == 1.0) {
            delegate.paintIcon(c, g, x, y)
            return
        }
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.translate(x, y)
            g2.scale(scale, scale)
            delegate.paintIcon(c, g2, 0, 0)
        } finally {
            g2.dispose()
        }
    }
}
