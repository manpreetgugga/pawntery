package com.pawtillery.game

import com.badlogic.gdx.Input

/** Music, sound, vibration, aim guide, a confirmed progress reset, and credits. */
class SettingsScreen(game: Pawtillery) : BaseScreen(game) {
    private var time = 0f
    private var confirmReset = false
    private var message = ""
    private var messageTime = 0f

    override fun render(delta: Float) {
        time += delta
        messageTime -= delta
        clear()
        beginWorld()
        val s = shapes
        Art.background(s, viewLeft, viewRight, viewTop, time, 0.5f)
        Art.hedges(s, viewLeft, viewRight)
        Art.fence(s)
        Art.ground(s, viewLeft, viewRight)
        s.end()

        buttons = layoutButtons()
        val (dialog, main) = buttons.partition { it.id.startsWith("reset_") }
        val a = game.assets
        val cx = uiW / 2f
        beginUiShapes()
        Ui.panel(s, cx - 520f, uiH / 2f - 250f, 1040f, 480f)
        Ui.buttons(s, main, pressedId)
        s.end()
        beginUiText()
        Ui.text(batch, a.h1, "Settings", cx, uiH - 70f, Palette.WHITE)
        Ui.paragraph(
            batch, a.small,
            "Pawtillery ${game.version}. Made with libGDX and Box2D. Fonts: Titan One and Varela Round (SIL Open Font License). " +
                "All art, music and sound effects are made in code for this game.",
            cx, uiH / 2f - 160f, 940f, Palette.INK,
        )
        Ui.buttonLabels(batch, a, main, pressedId)
        if (messageTime > 0f) Ui.text(batch, a.h2, message, cx, 180f, Palette.WHITE, kotlin.math.min(1f, messageTime * 3f))
        // What the phone's installer needs, if anything (like allowing Pawtillery to install updates).
        game.updater?.message?.let { if (it.isNotEmpty() && game.updater?.state == Updater.State.READY) Ui.text(batch, a.small, it, cx, 150f, Palette.WHITE, maxWidth = 1100f) }
        batch.end()

        if (confirmReset) {
            shadeScreen(Palette.SHADE)
            beginUiShapes()
            Ui.panel(s, cx - 380f, uiH / 2f - 200f, 760f, 400f)
            Ui.buttons(s, dialog, pressedId)
            s.end()
            beginUiText()
            Ui.text(batch, a.h1, "Reset progress?", cx, uiH / 2f + 130f, Palette.WHITE)
            Ui.paragraph(batch, a.body, "Treats, unlocked pets, Trick Shot stars and stats will be cleared. Plus stays yours.", cx, uiH / 2f + 70f, 640f, Palette.INK)
            Ui.buttonLabels(batch, a, dialog, pressedId)
            batch.end()
        }
        drawFade(delta)
    }

    private fun layoutButtons(): List<Button> {
        val st = game.settings
        val cx = uiW / 2f
        val colW = 470f
        val left = cx - colW - 20f
        val right = cx + 20f
        val row1 = uiH / 2f + 90f
        val row2 = uiH / 2f - 30f
        fun onOff(v: Boolean) = if (v) "On" else "Off"
        val list = mutableListOf(
            Button("music", left, row1, colW, 100f, "Music: ${onOff(st.music)}", if (st.music) ButtonStyle.GOLD else ButtonStyle.LIGHT,
                sublabel = "Background tunes"),
            Button("sound", right, row1, colW, 100f, "Sound effects: ${onOff(st.sound)}", if (st.sound) ButtonStyle.GOLD else ButtonStyle.LIGHT,
                sublabel = "Splats, bonks and pet voices"),
            Button("vibration", left, row2, colW, 100f, "Vibration: ${onOff(st.vibration)}", if (st.vibration) ButtonStyle.GOLD else ButtonStyle.LIGHT,
                sublabel = "A buzz on big hits"),
            Button("mode", right, row2, colW, 100f, if (st.easyMode) "Aim guide: Easy" else "Aim guide: Pro", ButtonStyle.GOLD,
                sublabel = if (st.easyMode) "Full guide, no wind" else "Short guide, wind each turn"),
            Button("back", 40f, 34f, 220f, 96f, "Back"),
            Button("reset", uiW - 340f, 34f, 300f, 96f, "Reset progress", font = game.assets.body),
        )
        game.updater?.let { list += updateButton(it, cx) }
        if (confirmReset) {
            list += Button("reset_yes", cx - 330f, uiH / 2f - 170f, 310f, 96f, "Reset", layer = 1)
            list += Button("reset_no", cx + 20f, uiH / 2f - 170f, 310f, 96f, "Keep it", ButtonStyle.PRIMARY, layer = 1)
        }
        return list
    }

    /** Check for updates, or install one that's ready (test builds only). */
    private fun updateButton(u: Updater, cx: Float): Button {
        val ready = u.state == Updater.State.READY
        val busy = u.state == Updater.State.CHECKING || u.state == Updater.State.DOWNLOADING
        val label = when (u.state) {
            Updater.State.READY -> "Install update"
            Updater.State.CHECKING -> "Checking..."
            Updater.State.DOWNLOADING -> "Downloading ${(u.progress * 100).toInt()}%"
            else -> "Check for updates"
        }
        val sub = when (u.state) {
            Updater.State.READY -> "${u.available?.versionName} is ready"
            Updater.State.UP_TO_DATE -> "You have the newest version"
            Updater.State.FAILED -> u.message ?: "Try again later"
            else -> "This is ${u.currentVersion}"
        }
        return Button("update", cx - 240f, 34f, 480f, 96f, label, if (ready) ButtonStyle.PRIMARY else ButtonStyle.LIGHT,
            enabled = !busy, sublabel = sub, font = game.assets.body)
    }

    override fun onButton(id: String) {
        val st = game.settings
        when (id) {
            "update" -> game.updater?.let { if (it.state == Updater.State.READY) it.install() else it.check(game.clock) }
            "music" -> {
                st.music = !st.music
                game.refreshMusic()
            }
            "sound" -> st.sound = !st.sound
            "vibration" -> {
                st.vibration = !st.vibration
                game.buzz(40)
            }
            "mode" -> st.easyMode = !st.easyMode
            "reset" -> confirmReset = true
            "reset_no" -> confirmReset = false
            "reset_yes" -> {
                st.resetProgress()
                confirmReset = false
                message = "Progress cleared. A fresh start!"
                messageTime = 3f
            }
            "back" -> game.switchTo(MenuScreen(game))
        }
    }

    override fun onKey(keycode: Int): Boolean {
        if (keycode != Input.Keys.BACK && keycode != Input.Keys.ESCAPE) return false
        if (confirmReset) confirmReset = false else game.switchTo(MenuScreen(game))
        return true
    }
}
