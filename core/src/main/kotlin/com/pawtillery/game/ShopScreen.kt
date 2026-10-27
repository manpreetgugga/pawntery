package com.pawtillery.game

import com.badlogic.gdx.Input

/**
 * The Shop: one optional purchase, Pawtillery Plus. Buying, restoring and opening the privacy
 * policy all go through a grown-up check first, since children play this game too.
 */
class ShopScreen(game: Pawtillery) : BaseScreen(game) {
    private var time = 0f
    private val gate = ParentGate()
    /** What to do once a grown-up passes the check; null when the check isn't showing. */
    private var afterGate: (() -> Unit)? = null
    private var message = ""
    private var messageTime = 0f
    private var busy = false

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
        val (gateButtons, mainButtons) = buttons.partition { it.id.startsWith("gate") }
        val a = game.assets
        val cx = uiW / 2f
        val px = cx - 560f
        val py = uiH / 2f - 230f
        beginUiShapes()
        TreatBadge.draw(s, uiW - 250f, uiH - 92f)
        Ui.panel(s, px, py, 1120f, 480f)
        // The three pets Plus unlocks, peeking from the left of the card
        val pets = listOf(PetKind.BUNNY, PetKind.TURTLE, PetKind.PARROT)
        for ((i, kind) in pets.withIndex()) {
            val x = px + 110f + i * 120f
            val y = py + 280f + (if (i == 1) 40f else 0f)
            s.color = Art.petColor(kind)
            s.circle(x, y, 62f, 30)
            icon(x - 4f, y - 18f, 92f) { Art.petOutlined(s, kind, time + i, false, false, 0f) }
        }
        s.color = Palette.GOLD
        for (i in 0 until 3) s.circle(px + 482f, py + 350f - i * 46f, 8f, 12)
        Ui.buttons(s, mainButtons, pressedId)
        s.end()
        beginUiText()
        TreatBadge.text(batch, a, game.settings.treats, uiW - 250f, uiH - 92f)
        Ui.text(batch, a.h1, "Shop", cx, uiH - 70f, Palette.WHITE)
        val tx = px + 470f
        Ui.textAt(batch, a.h1, "Pawtillery Plus", tx, py + 420f, Palette.WHITE)
        val perks = listOf("All five pets, right now", "No ads, ever", "Double treats from every game and star")
        for ((i, perk) in perks.withIndex()) Ui.textAt(batch, a.body, perk, tx + 28f, py + 350f - i * 46f, Palette.INK)
        Ui.text(batch, a.small, "One purchase. No subscriptions, loot boxes or timers. Everything in Plus can also be earned by playing.", px + 560f, py + 34f, Palette.INK, 0.7f, 1060f)
        Ui.buttonLabels(batch, a, mainButtons, pressedId)
        if (messageTime > 0f && afterGate == null) Ui.text(batch, a.h2, message, cx, py - 40f, Palette.WHITE, kotlin.math.min(1f, messageTime * 3f))
        batch.end()

        if (afterGate != null) {
            shadeScreen(Palette.SHADE)
            beginUiShapes()
            Ui.panel(s, cx - 330f, uiH / 2f - 400f, 660f, 720f)
            Ui.buttons(s, gateButtons, pressedId)
            s.end()
            beginUiText()
            Ui.text(batch, a.h1, "Ask a grown-up", cx, uiH / 2f + 270f, Palette.WHITE)
            Ui.text(batch, a.h2, gate.question, cx, uiH / 2f + 200f, Palette.INK)
            Ui.text(batch, a.h1, gate.typed.ifEmpty { "?" }, cx, uiH / 2f + 136f, Palette.ACCENT_DARK)
            Ui.buttonLabels(batch, a, gateButtons, pressedId)
            if (messageTime > 0f) Ui.text(batch, a.body, message, cx, uiH / 2f - 290f, Palette.BAD, kotlin.math.min(1f, messageTime * 3f))
            batch.end()
        }
        drawFade(delta)
    }

    private fun layoutButtons(): List<Button> {
        val list = ArrayList<Button>()
        val cx = uiW / 2f
        val px = cx - 560f
        val py = uiH / 2f - 230f
        val settings = game.settings
        val price = game.platform.plusPrice
        when {
            settings.plus -> list += Button("owned", px + 470f, py + 150f, 560f, 90f, "You have Plus. Thank you!", ButtonStyle.GOLD, enabled = false)
            price == null -> list += Button("unavailable", px + 470f, py + 150f, 560f, 90f, "Coming soon", enabled = false,
                sublabel = "Purchases aren't set up in this version")
            else -> list += Button("buy", px + 470f, py + 150f, 560f, 90f, "Get Plus  ·  $price", ButtonStyle.PRIMARY, enabled = !busy)
        }
        list += Button("restore", px + 470f, py + 70f, 270f, 64f, "Restore purchase", enabled = !settings.plus && price != null && !busy, font = game.assets.body)
        list += Button("privacy", px + 760f, py + 70f, 270f, 64f, "Privacy policy", font = game.assets.body)
        list += Button("back", 40f, 34f, 220f, 96f, "Back")
        if (afterGate != null) {
            val keyW = 150f
            val keyH = 80f
            val x0 = cx - (3 * keyW + 2 * 16f) / 2f
            val top = uiH / 2f + 100f
            val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "C", "0", "OK")
            for ((i, k) in keys.withIndex()) {
                val col = i % 3
                val row = i / 3
                val style = if (k == "OK") ButtonStyle.PRIMARY else ButtonStyle.LIGHT
                list += Button("gate:$k", x0 + col * (keyW + 16f), top - keyH - row * (keyH + 14f), keyW, keyH, k, style, layer = 1)
            }
            list += Button("gate:cancel", cx - 150f, uiH / 2f - 384f, 300f, 64f, "Cancel", font = game.assets.body, layer = 1)
        }
        return list
    }

    /** Runs [action] once a grown-up answers the question. */
    private fun withGate(action: () -> Unit) {
        gate.newQuestion()
        afterGate = action
    }

    private fun say(text: String) {
        message = text
        messageTime = 3f
    }

    override fun onButton(id: String) {
        val platform = game.platform
        when {
            id.startsWith("gate:") -> onGateKey(id.removePrefix("gate:"))
            id == "buy" -> withGate {
                busy = true
                platform.buyPlus { result ->
                    busy = false
                    when (result) {
                        PurchaseResult.BOUGHT -> {
                            game.settings.plus = true
                            game.sfx("win")
                            say("Plus is yours. Have fun!")
                        }
                        PurchaseResult.CANCELLED -> say("No problem. Nothing was charged.")
                        PurchaseResult.FAILED -> say("The store didn't respond. Please try again later.")
                    }
                }
            }
            id == "restore" -> withGate {
                busy = true
                platform.restorePurchases { owned ->
                    busy = false
                    if (owned) game.settings.plus = true
                    say(if (owned) "Plus restored. Welcome back!" else "No earlier purchase found for this account.")
                }
            }
            id == "privacy" -> withGate { platform.openUrl(ParentGate.PRIVACY_URL) }
            id == "back" -> game.switchTo(MenuScreen(game))
        }
    }

    private fun onGateKey(key: String) {
        when (key) {
            "cancel" -> afterGate = null
            "C" -> gate.clear()
            "OK" -> if (gate.check()) {
                val action = afterGate
                afterGate = null
                action?.invoke()
            } else {
                say("Not quite. Here's another one.")
            }
            else -> key.toIntOrNull()?.let { gate.type(it) }
        }
    }

    override fun onKey(keycode: Int): Boolean {
        if (keycode != Input.Keys.BACK && keycode != Input.Keys.ESCAPE) return false
        if (afterGate != null) afterGate = null else game.switchTo(MenuScreen(game))
        return true
    }
}
