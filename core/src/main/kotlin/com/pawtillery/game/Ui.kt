package com.pawtillery.game

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.utils.Align

enum class ButtonStyle(val fill: Color, val edge: Color, val text: Color) {
    PRIMARY(Palette.ACCENT, Palette.ACCENT_DARK, Palette.WHITE),
    TEAL(Palette.TEAL, Palette.TEAL_DARK, Palette.WHITE),
    LIGHT(Palette.PANEL, Palette.PANEL_EDGE, Palette.INK),
    GOLD(Palette.GOLD, Palette.GOLD_DARK, Palette.INK),
}

/**
 * An immediate-mode button: screens rebuild their button list each frame from game state, and the
 * same list is used for drawing and for hit testing.
 */
class Button(
    val id: String,
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val label: String,
    val style: ButtonStyle = ButtonStyle.LIGHT,
    val enabled: Boolean = true,
    val selected: Boolean = false,
    val sublabel: String? = null,
    val badge: String? = null,
    /** Draws an icon centered at (cx, cy) fitting in [size] UI units. Labels move below it. */
    val icon: ((ShapeRenderer, Float, Float, Float) -> Unit)? = null,
    val font: BitmapFont? = null,
) {
    fun contains(px: Float, py: Float) = px >= x && px <= x + w && py >= y && py <= y + h
}

object Ui {
    private val layout = GlyphLayout()
    private val tmp = Color()
    private const val DEPTH = 7f

    fun roundRect(s: ShapeRenderer, x: Float, y: Float, w: Float, h: Float, r: Float) {
        val rr = minOf(r, w / 2f, h / 2f)
        s.rect(x + rr, y, w - 2f * rr, h)
        s.rect(x, y + rr, rr, h - 2f * rr)
        s.rect(x + w - rr, y + rr, rr, h - 2f * rr)
        s.circle(x + rr, y + rr, rr, 16)
        s.circle(x + w - rr, y + rr, rr, 16)
        s.circle(x + rr, y + h - rr, rr, 16)
        s.circle(x + w - rr, y + h - rr, rr, 16)
    }

    fun panel(s: ShapeRenderer, x: Float, y: Float, w: Float, h: Float, fill: Color = Palette.PANEL, edge: Color = Palette.PANEL_EDGE, r: Float = 22f) {
        s.color = edge
        roundRect(s, x, y - 6f, w, h + 6f, r)
        s.color = fill
        roundRect(s, x, y, w, h, r)
    }

    fun buttons(s: ShapeRenderer, buttons: List<Button>, pressedId: String?) {
        for (b in buttons) button(s, b, b.id == pressedId)
    }

    private fun button(s: ShapeRenderer, b: Button, pressed: Boolean) {
        val down = if (pressed && b.enabled) DEPTH - 2f else 0f
        val fill = if (b.selected) Palette.GOLD else b.style.fill
        val edge = if (b.selected) Palette.GOLD_DARK else b.style.edge
        val alpha = if (b.enabled) 1f else 0.45f
        if (b.selected) {
            s.color = Palette.INK
            roundRect(s, b.x - 5f, b.y - DEPTH - 5f, b.w + 10f, b.h + DEPTH + 10f, 26f)
        }
        s.color = tmp.set(edge).also { it.a *= alpha }
        roundRect(s, b.x, b.y - DEPTH + down, b.w, b.h, 20f)
        s.color = tmp.set(fill).also { it.a *= alpha }
        roundRect(s, b.x, b.y - down, b.w, b.h, 20f)
        b.icon?.let { icon ->
            val size = minOf(b.w, b.h) * 0.52f
            icon(s, b.x + b.w / 2f, b.y - down + b.h - size / 2f - 12f, size)
        }
        if (b.badge != null) {
            s.color = Palette.INK
            roundRect(s, b.x + b.w - 54f, b.y - down + b.h - 30f, 62f, 40f, 18f)
        }
    }

    fun buttonLabels(batch: SpriteBatch, assets: Assets, buttons: List<Button>, pressedId: String?) {
        for (b in buttons) {
            val down = if (b.id == pressedId && b.enabled) DEPTH - 2f else 0f
            val color = if (b.selected) Palette.INK else b.style.text
            val alpha = if (b.enabled) 1f else 0.6f
            val font = b.font ?: if (b.icon != null) assets.small else assets.h2
            val top = b.y - down
            if (b.icon != null) {
                text(batch, font, b.label, b.x + b.w / 2f, top + 34f, color, alpha, b.w - 10f)
            } else if (b.sublabel != null) {
                text(batch, font, b.label, b.x + b.w / 2f, top + b.h * 0.66f, color, alpha, b.w - 20f)
                text(batch, assets.small, b.sublabel, b.x + b.w / 2f, top + b.h * 0.3f, color, alpha * 0.85f, b.w - 20f)
            } else {
                text(batch, font, b.label, b.x + b.w / 2f, top + b.h / 2f, color, alpha, b.w - 16f)
            }
            if (b.badge != null) {
                text(batch, assets.small, b.badge, b.x + b.w - 23f, top + b.h - 10f, Palette.WHITE, 1f, 60f)
            }
        }
    }

    /** Draws [text] centered horizontally on [cx] and vertically on [cy], shrinking it to fit [maxWidth]. */
    fun text(batch: SpriteBatch, font: BitmapFont, text: String, cx: Float, cy: Float, color: Color, alpha: Float = 1f, maxWidth: Float = Float.MAX_VALUE) {
        val baseX = font.data.scaleX
        val baseY = font.data.scaleY
        // GlyphLayout captures the font color, so set it first.
        font.color = tmp.set(color).also { it.a *= alpha }
        layout.setText(font, text)
        if (layout.width > maxWidth) {
            val k = maxWidth / layout.width
            font.data.setScale(baseX * k, baseY * k)
            layout.setText(font, text)
        }
        font.draw(batch, layout, cx - layout.width / 2f, cy + layout.height / 2f)
        font.data.setScale(baseX, baseY)
        font.color = Color.WHITE
    }

    /** Draws [text] starting at [x] (or ending at [x] when [rightAligned]), vertically centered on [cy]. */
    fun textAt(batch: SpriteBatch, font: BitmapFont, text: String, x: Float, cy: Float, color: Color, rightAligned: Boolean = false, alpha: Float = 1f) {
        font.color = tmp.set(color).also { it.a *= alpha }
        layout.setText(font, text)
        font.draw(batch, layout, if (rightAligned) x - layout.width else x, cy + layout.height / 2f)
        font.color = Color.WHITE
    }

    /** Draws wrapped, centered text whose first line's top sits at [top]. */
    fun paragraph(batch: SpriteBatch, font: BitmapFont, text: String, cx: Float, top: Float, width: Float, color: Color) {
        font.color = color
        layout.setText(font, text, color, width, Align.center, true)
        font.draw(batch, layout, cx - width / 2f, top)
        font.color = Color.WHITE
    }
}
