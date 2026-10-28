package com.pawtillery.game

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.math.MathUtils

class MenuScreen(game: Pawtillery) : BaseScreen(game) {
    private var time = 0f
    private var showHelp = false

    init {
        // Test builds look for a newer build each time the menu opens (at most every half hour).
        game.updater?.checkIfDue(game.clock)
    }

    /** The "new version ready" offer, shown once an update has downloaded, until the player says Later. */
    private val showUpdate: Boolean
        get() = !showHelp && game.updater?.let { it.state == Updater.State.READY && !it.dismissed } == true

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
        val (dialog, main) = buttons.partition { it.layer > 0 }
        val a = game.assets
        beginUiShapes()
        if (!showHelp) {
            s.color = Palette.SHADE
            Ui.roundRect(s, uiW / 2f - 330f, uiH - 268f, 660f, 58f, 29f)
        }
        if (showHelp) drawHelpPanel() else TreatBadge.draw(s, uiW - 250f, uiH - 92f)
        val downloading = !showHelp && game.updater?.state == Updater.State.DOWNLOADING
        if (downloading) drawDownloadShapes()
        Ui.buttons(s, main, pressedId)
        s.end()
        beginUiText()
        if (showHelp) {
            drawHelpText()
        } else {
            Ui.text(batch, a.title, "Pawtillery", uiW / 2f, uiH - 130f, Palette.WHITE)
            Ui.text(batch, a.body, "Build a fort. Splat the neighbor's pet.", uiW / 2f, uiH - 239f, Palette.WHITE)
            TreatBadge.text(batch, a, game.settings.treats, uiW - 250f, uiH - 92f)
        }
        if (downloading) Ui.textAt(batch, a.small, "Downloading update  ${((game.updater?.progress ?: 0f) * 100).toInt()}%", 44f, uiH - 46f, Palette.INK)
        Ui.buttonLabels(batch, a, main, pressedId)
        batch.end()
        if (showUpdate) drawUpdateOffer(dialog)
        drawFade(delta)
    }

    private fun drawDownloadShapes() {
        val s = shapes
        val x = 20f
        val y = uiH - 92f
        Ui.panel(s, x, y, 420f, 70f, r = 30f)
        s.color = Palette.PANEL_EDGE
        Ui.roundRect(s, x + 24f, y + 12f, 372f, 14f, 7f)
        s.color = Palette.TEAL
        Ui.roundRect(s, x + 24f, y + 12f, kotlin.math.max(14f, 372f * (game.updater?.progress ?: 0f)), 14f, 7f)
    }

    private fun drawUpdateOffer(dialog: List<Button>) {
        val u = game.updater ?: return
        val info = u.available ?: return
        val a = game.assets
        val cx = uiW / 2f
        shadeScreen(Palette.SHADE)
        beginUiShapes()
        Ui.panel(shapes, cx - 440f, uiH / 2f - 230f, 880f, 470f)
        Ui.buttons(shapes, dialog, pressedId)
        shapes.end()
        beginUiText()
        Ui.text(batch, a.h1, "New version ready!", cx, uiH / 2f + 170f, Palette.WHITE)
        Ui.text(batch, a.h2, info.versionName, cx, uiH / 2f + 96f, Palette.INK, maxWidth = 800f)
        val notes = info.notes.ifBlank { "Fixes and improvements." }
        Ui.paragraph(batch, a.body, "What's new: $notes", cx, uiH / 2f + 54f, 760f, Palette.INK)
        val note = u.message ?: "Your treats, pets and stars stay as they are."
        Ui.text(batch, a.small, note, cx, uiH / 2f - 92f, Palette.INK, 0.8f, 800f)
        Ui.buttonLabels(batch, a, dialog, pressedId)
        batch.end()
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

    private fun today(): Long = java.time.LocalDate.now().toEpochDay()

    private fun layoutButtons(): List<Button> {
        val cx = uiW / 2f
        if (showHelp) return listOf(Button("help_close", cx - 160f, 70f, 320f, 92f, "Got it!", ButtonStyle.PRIMARY))
        val easy = game.settings.easyMode
        val u = game.updater
        val update = when {
            showUpdate -> listOf(
                Button("update_later", cx - 350f, uiH / 2f - 200f, 330f, 96f, "Later", layer = 1),
                Button("update_install", cx + 20f, uiH / 2f - 200f, 330f, 96f, "Install", ButtonStyle.PRIMARY, layer = 1),
            )
            u != null && u.state == Updater.State.READY ->
                listOf(Button("update_open", 20f, uiH - 112f, 420f, 92f, "Update ready!", ButtonStyle.GOLD, sublabel = "Tap to install ${u.available?.versionName}"))
            else -> emptyList()
        }
        return update + listOf(
            Button("play_ai", cx - 290f, uiH / 2f + 10f, 580f, 100f, "Play vs Computer", ButtonStyle.PRIMARY),
            Button("play_2p", cx - 290f, uiH / 2f - 105f, 580f, 100f, "2 Players · One Phone", ButtonStyle.TEAL),
            Button("tricks", cx - 290f, uiH / 2f - 220f, 282f, 100f, "Trick Shots", ButtonStyle.GOLD,
                sublabel = "${game.settings.totalStars} of ${Challenges.all.size * 3} stars"),
            Button("daily", cx + 8f, uiH / 2f - 220f, 282f, 100f, "Daily", ButtonStyle.GOLD,
                sublabel = game.settings.dailyStars(today()).let { if (it > 0) "Done today: $it stars" else "New every day" }),
            Button("mode", cx - 562f, 56f, 270f, 100f, if (easy) "Easy" else "Pro", ButtonStyle.GOLD,
                sublabel = if (easy) "Aim guide on, no wind" else "Wind + short aim guide"),
            Button("settings", cx - 276f, 56f, 190f, 100f, "Settings"),
            Button("help", cx - 70f, 56f, 220f, 100f, "How to play"),
            Button("trophies", cx + 166f, 56f, 210f, 100f, "Trophies"),
            Button("shop", cx + 392f, 56f, 170f, 100f, "Shop", ButtonStyle.TEAL),
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
            "2. Throw: drag back anywhere and let go, or set angle and power with + and - and tap Throw.",
            "3. Splat the other pet! Every hit fills their mess meter. 100% messy loses.",
            "4. Smash their fort: bowling balls, watermelons, fireworks and the paint bomb crack and break it.",
            "5. Power-ups, once a match: Mega throws big, Triple throws three, Calm stops the wind, Umbrella blocks a hit.",
            "6. Trick shots, Trick Shot levels and the Daily earn treats. Spend them on new pets.",
        )
        var y = uiH - 196f
        for (line in steps) {
            Ui.paragraph(batch, a.body, line, cx, y, 960f, Palette.INK)
            y -= 84f
        }
    }

    override fun onButton(id: String) {
        when (id) {
            "play_ai" -> game.switchTo(PetSelectScreen(game, twoPlayers = false))
            "play_2p" -> game.switchTo(PetSelectScreen(game, twoPlayers = true))
            "tricks" -> game.switchTo(ChallengeListScreen(game))
            "daily" -> game.switchTo(GameScreen(game, MatchSetup.forChallenge(Challenges.daily(today()))))
            "trophies" -> game.switchTo(TrophiesScreen(game))
            "mode" -> game.settings.easyMode = !game.settings.easyMode
            "settings" -> game.switchTo(SettingsScreen(game))
            "help" -> showHelp = true
            "shop" -> game.switchTo(ShopScreen(game))
            "help_close" -> showHelp = false
            "update_open" -> game.updater?.dismissed = false
            "update_later" -> game.updater?.dismissed = true
            "update_install" -> game.updater?.install()
        }
    }

    override fun onKey(keycode: Int): Boolean {
        if (keycode != Input.Keys.BACK && keycode != Input.Keys.ESCAPE) return false
        when {
            showUpdate -> game.updater?.dismissed = true
            showHelp -> showHelp = false
            else -> Gdx.app.exit()
        }
        return true
    }
}
