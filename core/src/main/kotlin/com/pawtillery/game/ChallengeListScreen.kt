package com.pawtillery.game

import com.badlogic.gdx.Input

/** Trick Shot level picker: ten levels in two rows, each showing its best stars. */
class ChallengeListScreen(game: Pawtillery) : BaseScreen(game) {
    private var time = 0f

    private val cardW = 250f
    private val cardH = 230f
    private val gap = 22f

    override fun render(delta: Float) {
        time += delta
        clear()
        beginWorld()
        val s = shapes
        Art.background(s, viewLeft, viewRight, viewTop, time, 0.5f)
        Art.hedges(s, viewLeft, viewRight)
        Art.fence(s)
        Art.ground(s, viewLeft, viewRight)
        s.end()

        buttons = layoutButtons()
        val a = game.assets
        val settings = game.settings
        beginUiShapes()
        Ui.panel(s, uiW - 290f, uiH - 92f, 270f, 70f, r = 35f)
        s.color = Palette.INK
        Ui.star(s, uiW - 248f, uiH - 57f, 28f)
        s.color = Palette.GOLD
        Ui.star(s, uiW - 248f, uiH - 57f, 22f)
        s.color = Palette.SHADE
        Ui.roundRect(s, uiW / 2f - 640f, 226f, 1280f, 50f, 25f)
        Ui.buttons(s, buttons, pressedId)
        for (b in buttons) {
            val n = b.id.removePrefix("level:").toIntOrNull() ?: continue
            if (b.enabled) Ui.starRow(s, b.x + b.w / 2f, b.y + 48f, 20f, settings.challengeStars(n)) else drawLock(b.x + b.w / 2f, b.y + 32f)
        }
        s.end()
        beginUiText()
        Ui.text(batch, a.h1, "Trick Shots", uiW / 2f, uiH - 70f, Palette.WHITE)
        Ui.textAt(batch, a.h2, "${settings.totalStars} / ${Challenges.all.size * 3}", uiW - 210f, uiH - 55f, Palette.INK)
        Ui.buttonLabels(batch, a, buttons, pressedId)
        for (b in buttons) {
            val n = b.id.removePrefix("level:").toIntOrNull() ?: continue
            val c = Challenges.byNumber(n) ?: continue
            val alpha = if (b.enabled) 1f else 0.45f
            Ui.text(batch, a.h1, "${c.number}", b.x + b.w / 2f, b.y + 172f, Palette.WHITE, alpha)
            Ui.text(batch, a.small, c.title, b.x + b.w / 2f, b.y + 114f, Palette.INK, alpha, b.w - 20f)
            Ui.text(batch, a.small, "with ${c.player.displayName}", b.x + b.w / 2f, b.y + 90f, Palette.INK, alpha * 0.6f, b.w - 20f)
        }
        Ui.text(batch, a.small, "Beat a level to open the next. Fewer throws earn more stars, and every new star is worth 10 treats.", uiW / 2f, 251f, Palette.WHITE, maxWidth = 1240f)
        batch.end()
        drawFade(delta)
    }

    private fun drawLock(cx: Float, cy: Float) {
        val s = shapes
        s.color = Palette.PANEL_EDGE
        s.rectLine(cx - 16f, cy, cx - 16f, cy + 18f, 8f)
        s.rectLine(cx + 16f, cy, cx + 16f, cy + 18f, 8f)
        s.arc(cx, cy + 18f, 20f, 0f, 180f, 14)
        s.color = Palette.PANEL
        s.arc(cx, cy + 18f, 12f, 0f, 180f, 14)
        s.color = Palette.PANEL_EDGE
        Ui.roundRect(s, cx - 26f, cy - 22f, 52f, 40f, 8f)
    }

    private fun layoutButtons(): List<Button> {
        val list = ArrayList<Button>()
        val perRow = 5
        val rowW = perRow * cardW + (perRow - 1) * gap
        val x0 = (uiW - rowW) / 2f
        val top = uiH - 130f
        for ((i, c) in Challenges.all.withIndex()) {
            val col = i % perRow
            val row = i / perRow
            val x = x0 + col * (cardW + gap)
            val y = top - cardH - row * (cardH + gap)
            list += Button("level:${c.number}", x, y, cardW, cardH, "", enabled = game.settings.isChallengeOpen(c.number))
        }
        list += Button("back", 40f, 34f, 220f, 96f, "Back")
        return list
    }

    override fun onButton(id: String) {
        when {
            id == "back" -> game.switchTo(MenuScreen(game))
            id.startsWith("level:") -> {
                val c = Challenges.byNumber(id.removePrefix("level:").toInt()) ?: return
                game.switchTo(GameScreen(game, MatchSetup.forChallenge(c)))
            }
        }
    }

    override fun onKey(keycode: Int): Boolean {
        if (keycode != Input.Keys.BACK && keycode != Input.Keys.ESCAPE) return false
        game.switchTo(MenuScreen(game))
        return true
    }
}
