package com.pawtillery.game

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.math.MathUtils

class MenuScreen(game: Pawtillery) : BaseScreen(game) {
    private var time = 0f
    private var showHelp = false

    override fun render(delta: Float) {
        time += delta
        clear()
        beginWorld()
        val s = shapes
        Art.background(s, viewLeft, viewRight, viewTop, time, 0.8f)
        Art.hedges(s, viewLeft, viewRight)
        Art.fence(s)
        Art.ground(s, viewLeft, viewRight)
        scenePiece(PieceType.CRATE, 9.3f, Arena.GROUND_Y + 0.6f)
        scenePiece(PieceType.BOX, 9.3f, Arena.GROUND_Y + 1.7f)
        scenePiece(PieceType.GNOME, 22.4f, Arena.GROUND_Y + 0.65f)
        scenePiece(PieceType.POT, 23.4f, Arena.GROUND_Y + 0.45f)
        scenePet(PetKind.DOG, 6.5f, Side.LEFT)
        scenePet(PetKind.CAT, Arena.WIDTH - 6.5f, Side.RIGHT)
        // A mud ball forever flying from the dog to the cat.
        val t = (time % 2.6f) / 2.2f
        if (t <= 1f) {
            val x = MathUtils.lerp(7f, Arena.WIDTH - 7f, t)
            val y = Arena.GROUND_Y + 2.6f + 7.5f * t * (1f - t) * 4f * 0.55f
            s.identity()
            s.translate(x, y, 0f)
            s.rotate(0f, 0f, 1f, -time * 300f)
            Art.itemShape(s, ItemType.MUD, ItemType.MUD.radius)
            s.identity()
        }
        s.end()

        buttons = layoutButtons()
        val a = game.assets
        beginUiShapes()
        if (!showHelp) {
            s.color = Palette.SHADE
            Ui.roundRect(s, uiW / 2f - 330f, uiH - 268f, 660f, 58f, 29f)
        }
        if (showHelp) drawHelpPanel() else TreatBadge.draw(s, uiW - 250f, uiH - 92f)
        Ui.buttons(s, buttons, pressedId)
        s.end()
        beginUiText()
        if (showHelp) {
            drawHelpText()
        } else {
            Ui.text(batch, a.title, "Pawtillery", uiW / 2f, uiH - 130f, Palette.WHITE)
            Ui.text(batch, a.body, "Build a fort. Splat the neighbor's pet.", uiW / 2f, uiH - 239f, Palette.WHITE)
            TreatBadge.text(batch, a, game.settings.treats, uiW - 250f, uiH - 92f)
        }
        Ui.buttonLabels(batch, a, buttons, pressedId)
        batch.end()
        drawFade(delta)
    }

    private fun scenePiece(type: PieceType, x: Float, y: Float) {
        shapes.identity()
        shapes.translate(x, y, 0f)
        Art.pieceShape(shapes, type, 1f)
        shapes.identity()
    }

    private fun scenePet(kind: PetKind, x: Float, side: Side) {
        val s = shapes
        s.identity()
        s.translate(x, Arena.GROUND_Y + Arena.PET_H / 2f, 0f)
        s.scale(side.dir * Arena.PET_ART_SCALE, Arena.PET_ART_SCALE, 1f)
        val hit = side == Side.RIGHT && (time % 2.6f) in 2.15f..2.6f
        Art.petOutlined(s, kind, time + side.ordinal, hit, (time + side.ordinal) % 3.3f < 0.12f, 0f)
        s.identity()
    }

    private fun layoutButtons(): List<Button> {
        val cx = uiW / 2f
        if (showHelp) return listOf(Button("help_close", cx - 160f, 70f, 320f, 92f, "Got it!", ButtonStyle.PRIMARY))
        val easy = game.settings.easyMode
        return listOf(
            Button("play_ai", cx - 290f, uiH / 2f - 20f, 580f, 108f, "Play vs Computer", ButtonStyle.PRIMARY),
            Button("play_2p", cx - 290f, uiH / 2f - 150f, 580f, 108f, "2 Players · One Phone", ButtonStyle.TEAL),
            Button("mode", cx - 420f, 56f, 340f, 100f, if (easy) "Easy" else "Pro", ButtonStyle.GOLD,
                sublabel = if (easy) "Aim guide on, no wind" else "Wind + short aim guide"),
            Button("sound", cx - 60f, 56f, 220f, 100f, if (game.settings.sound) "Sound on" else "Sound off"),
            Button("help", cx + 180f, 56f, 240f, 100f, "How to play"),
        )
    }

    private fun drawHelpPanel() {
        Ui.panel(shapes, uiW / 2f - 520f, 200f, 1040f, uiH - 260f)
    }

    private fun drawHelpText() {
        val a = game.assets
        val cx = uiW / 2f
        Ui.text(batch, a.h1, "How to play", cx, uiH - 120f, Palette.WHITE)
        val steps = listOf(
            "1. Build: tap a piece, then touch your yard to drop it. Up to 6 pieces shield your pet.",
            "2. Throw: drag back anywhere and let go. Further back throws harder.",
            "3. Splat the other pet! Every hit fills their mess meter.",
            "4. A pet at 100% mess loses. After 10 throws each, the cleaner pet wins.",
            "Bowling balls smash forts, water balloons soak around walls, and each pet has a special throw.",
        )
        var y = uiH - 200f
        for (line in steps) {
            Ui.paragraph(batch, a.body, line, cx, y, 940f, Palette.INK)
            y -= 92f
        }
    }

    override fun onButton(id: String) {
        when (id) {
            "play_ai" -> game.switchTo(PetSelectScreen(game, twoPlayers = false))
            "play_2p" -> game.switchTo(PetSelectScreen(game, twoPlayers = true))
            "mode" -> game.settings.easyMode = !game.settings.easyMode
            "sound" -> game.settings.sound = !game.settings.sound
            "help" -> showHelp = true
            "help_close" -> showHelp = false
        }
    }

    override fun onKey(keycode: Int): Boolean {
        if (keycode != Input.Keys.BACK && keycode != Input.Keys.ESCAPE) return false
        if (showHelp) showHelp = false else Gdx.app.exit()
        return true
    }
}
