package com.pawtillery.game

import com.badlogic.gdx.Input
import kotlin.math.roundToInt

/** Lifetime stats on the left, every trophy (earned or not) on the right. */
class TrophiesScreen(game: Pawtillery) : BaseScreen(game) {
    private var time = 0f

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

        val st = game.settings
        val a = game.assets
        buttons = listOf(Button("back", 40f, 34f, 220f, 96f, "Back"))
        val top = uiH - 150f
        val statsX = uiW / 2f - 760f
        val listX = statsX + 500f
        val colW = 500f
        val rowH = 78f
        val trophies = Trophy.entries
        val rows = (trophies.size + 1) / 2
        val listH = rows * rowH + 30f

        beginUiShapes()
        Ui.panel(s, statsX, top - listH, 470f, listH)
        Ui.panel(s, listX, top - listH, colW * 2 + 20f, listH)
        for ((i, t) in trophies.withIndex()) {
            val x = listX + 16f + (i % 2) * colW
            val y = top - 15f - (i / 2 + 1) * rowH
            Ui.trophy(s, x + 36f, y + rowH / 2f, 52f, st.hasTrophy(t))
        }
        Ui.buttons(s, buttons, pressedId)
        s.end()

        beginUiText()
        val earned = trophies.count { st.hasTrophy(it) }
        Ui.text(batch, a.h1, "Trophies", uiW / 2f, uiH - 70f, Palette.WHITE)
        Ui.textAt(batch, a.label, "$earned of ${trophies.size} earned", listX + colW * 2f + 10f, top + 30f, Palette.WHITE, rightAligned = true)
        val hitRate = if (st.totalThrows > 0) (st.totalHits * 100f / st.totalThrows).roundToInt() else 0
        val stats = listOf(
            "Games played" to "${st.gamesPlayed}",
            "Wins" to "${st.wins}",
            "Champion wins" to "${st.championWins}",
            "Best win streak" to "${st.bestStreak}",
            "Throws" to "${st.totalThrows}",
            "Hit rate" to "$hitRate%",
            "Trick shots" to "${st.totalTricks}",
            "Trick Shot stars" to "${st.totalStars} / ${Challenges.all.size * 3}",
            "Daily challenges" to "${st.dailiesDone}",
            "Two-player games" to "${st.twoPlayerGames}",
        )
        Ui.textAt(batch, a.h2, "Your stats", statsX + 28f, top - 44f, Palette.INK)
        for ((i, pair) in stats.withIndex()) {
            val y = top - 96f - i * 46f
            Ui.textAt(batch, a.body, pair.first, statsX + 28f, y, Palette.INK, alpha = 0.75f)
            Ui.textAt(batch, a.h2, pair.second, statsX + 442f, y, Palette.INK, rightAligned = true)
        }
        for ((i, t) in trophies.withIndex()) {
            val x = listX + 16f + (i % 2) * colW
            val y = top - 15f - (i / 2 + 1) * rowH
            val has = st.hasTrophy(t)
            val alpha = if (has) 1f else 0.6f
            Ui.textAt(batch, a.body, t.title, x + 80f, y + rowH * 0.66f, Palette.INK, alpha = alpha)
            Ui.textAt(batch, a.small, if (has) "Earned" else "+${t.reward}", x + colW - 30f, y + rowH * 0.66f, if (has) Palette.GOOD else Palette.ACCENT_DARK, rightAligned = true)
            Ui.textAt(batch, a.small, t.description, x + 80f, y + rowH * 0.3f, Palette.INK, alpha = alpha * 0.75f)
        }
        Ui.buttonLabels(batch, a, buttons, pressedId)
        batch.end()
        drawFade(delta)
    }

    override fun onButton(id: String) {
        if (id == "back") game.switchTo(MenuScreen(game))
    }

    override fun onKey(keycode: Int): Boolean {
        if (keycode != Input.Keys.BACK && keycode != Input.Keys.ESCAPE) return false
        game.switchTo(MenuScreen(game))
        return true
    }
}
