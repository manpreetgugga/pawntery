package com.pawtillery.game

import com.badlogic.gdx.Input

/** Who plays which side, how long the match is, and how well the computer aims. */
class MatchSetup(
    val left: PetKind,
    val right: PetKind,
    val computer: Set<Side>,
    val throwsPerPlayer: Int,
    val level: ComputerLevel = ComputerLevel.NORMAL,
    val challenge: Challenge? = null,
) {
    companion object {
        fun forChallenge(c: Challenge) = MatchSetup(c.player, c.target, setOf(Side.RIGHT), c.maxThrows, challenge = c)
    }
}

/**
 * Pick a pet (each player in turn for two players). Locked pets show their price in treats and can
 * be unlocked here; the computer may use any pet, which shows players what they can earn.
 */
class PetSelectScreen(game: Pawtillery, private val twoPlayers: Boolean) : BaseScreen(game) {
    private var time = 0f
    private var step = 0
    private val chosen = arrayOfNulls<PetKind>(2)
    private var selected = game.settings.lastPet.takeIf { game.settings.isUnlocked(it) } ?: PetKind.DOG
    private var offer: PetKind? = null

    private val cardW = 252f
    private val cardH = 330f
    private val gap = 18f

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
        val (offerButtons, mainButtons) = buttons.partition { it.id.startsWith("offer_") }
        beginUiShapes()
        TreatBadge.draw(s, uiW - 250f, uiH - 92f)
        Ui.buttons(s, mainButtons, pressedId)
        s.end()
        beginUiText()
        TreatBadge.text(batch, a, game.settings.treats, uiW - 250f, uiH - 92f)
        val title = when {
            !twoPlayers -> "Choose your pet"
            step == 0 -> "Player 1: choose your pet"
            else -> "Player 2: choose your pet"
        }
        Ui.text(batch, a.h1, title, uiW / 2f, uiH - 70f, Palette.WHITE)
        Ui.buttonLabels(batch, a, mainButtons, pressedId)
        for (b in mainButtons) {
            if (!b.id.startsWith("pet:")) continue
            val kind = PetKind.valueOf(b.id.removePrefix("pet:"))
            Ui.text(batch, a.small, kind.signature.displayName, b.x + b.w / 2f, b.y + 78f, Palette.INK, if (b.enabled) 0.75f else 0.4f, b.w - 20f)
        }
        batch.end()

        val kind = offer
        if (kind != null) {
            shadeScreen(Palette.SHADE)
            beginUiShapes()
            Ui.panel(s, uiW / 2f - 380f, uiH / 2f - 210f, 760f, 400f)
            Ui.buttons(s, offerButtons, pressedId)
            s.end()
            beginUiText()
            drawOfferText(kind)
            Ui.buttonLabels(batch, a, offerButtons, pressedId)
            batch.end()
        }
        drawFade(delta)
    }

    private fun layoutButtons(): List<Button> {
        val list = ArrayList<Button>()
        val pets = PetKind.entries
        var x = (uiW - (pets.size * cardW + (pets.size - 1) * gap)) / 2f
        val y = uiH / 2f - 190f
        val taken = if (step == 1) chosen[0] else null
        for (kind in pets) {
            val unlocked = game.settings.isUnlocked(kind)
            list += Button("pet:${kind.name}", x, y, cardW, cardH, kind.displayName,
                enabled = kind != taken, selected = kind == selected && unlocked,
                badge = if (unlocked) null else "${kind.price}",
                font = game.assets.h2,
                icon = { s, cx, cy, _ -> drawCardPet(s, kind, cx, cy, unlocked) })
            x += cardW + gap
        }
        val quick = game.settings.quickMatch
        val cx = uiW / 2f
        val level = game.settings.computerLevel
        list += Button("back", cx - 680f, 40f, 200f, 100f, "Back")
        list += Button("length", cx - 460f, 40f, 300f, 100f, if (quick) "Quick match" else "Full match", ButtonStyle.GOLD,
            sublabel = if (quick) "${MatchConfig.QUICK_MATCH} throws each" else "${MatchConfig.FULL_MATCH} throws each")
        if (!twoPlayers) {
            list += Button("level", cx - 140f, 40f, 300f, 100f, level.displayName, ButtonStyle.GOLD,
                sublabel = when (level) {
                    ComputerLevel.ROOKIE -> "Gentle computer"
                    ComputerLevel.NORMAL -> "Fair fight"
                    ComputerLevel.CHAMPION -> "Sharp aim, +50% treats"
                })
        }
        list += Button("go", cx + 180f, 40f, 500f, 100f, if (twoPlayers && step == 0) "Next player" else "Play!", ButtonStyle.PRIMARY)
        offer?.let { kind ->
            val affordable = game.settings.treats >= kind.price
            if (affordable) {
                list += Button("offer_buy", cx - 330f, uiH / 2f - 170f, 320f, 96f, "Unlock", ButtonStyle.PRIMARY, layer = 1)
                list += Button("offer_close", cx + 10f, uiH / 2f - 170f, 320f, 96f, "Not now", layer = 1)
            } else {
                list += Button("offer_close", cx - 160f, uiH / 2f - 170f, 320f, 96f, "OK", ButtonStyle.PRIMARY, layer = 1)
            }
        }
        return list
    }

    private fun drawCardPet(s: com.badlogic.gdx.graphics.glutils.ShapeRenderer, kind: PetKind, cx: Float, cy: Float, unlocked: Boolean) {
        s.color = Art.petColor(kind)
        s.circle(cx, cy - 8f, 92f, 40)
        icon(cx - 6f, cy - 26f, 128f) {
            Art.petOutlined(s, kind, time + kind.ordinal, false, (time + kind.ordinal) % 3.4f < 0.12f, 0f)
        }
        if (!unlocked) {
            s.color = Palette.SHADE
            s.circle(cx, cy - 8f, 92f, 40)
            // Padlock
            s.color = Palette.GOLD_DARK
            s.rectLine(cx - 22f, cy + 4f, cx - 22f, cy + 26f, 10f)
            s.rectLine(cx + 22f, cy + 4f, cx + 22f, cy + 26f, 10f)
            s.arc(cx, cy + 26f, 27f, 0f, 180f, 16)
            s.color = Palette.SHADE
            s.arc(cx, cy + 26f, 17f, 0f, 180f, 16)
            s.color = Palette.GOLD
            Ui.roundRect(s, cx - 36f, cy - 46f, 72f, 56f, 10f)
            s.color = Palette.INK
            s.circle(cx, cy - 14f, 7f, 10)
            s.rect(cx - 3f, cy - 34f, 6f, 18f)
        }
    }

    private fun drawOfferText(kind: PetKind) {
        val a = game.assets
        val cx = uiW / 2f
        val treats = game.settings.treats
        if (treats >= kind.price) {
            Ui.text(batch, a.h1, "Unlock ${kind.displayName}?", cx, uiH / 2f + 120f, Palette.WHITE, maxWidth = 700f)
            Ui.paragraph(batch, a.body, "Spend ${kind.price} of your $treats treats. ${kind.displayName}'s special throw is the ${kind.signature.displayName}: ${kind.signature.hint}", cx, uiH / 2f + 60f, 660f, Palette.INK)
        } else {
            Ui.text(batch, a.h1, "${kind.displayName} costs ${kind.price} treats", cx, uiH / 2f + 120f, Palette.WHITE, maxWidth = 700f)
            Ui.paragraph(batch, a.body, "You have $treats. Every game earns treats, and wins earn the most. Direct hits add a bonus!", cx, uiH / 2f + 60f, 660f, Palette.INK)
        }
    }

    override fun onButton(id: String) {
        when {
            id.startsWith("pet:") -> {
                val kind = PetKind.valueOf(id.removePrefix("pet:"))
                if (game.settings.isUnlocked(kind)) selected = kind else offer = kind
            }
            id == "offer_buy" -> {
                val kind = offer ?: return
                if (game.settings.buyPet(kind)) {
                    selected = kind
                    game.sfx("win", 0.8f)
                }
                offer = null
            }
            id == "offer_close" -> offer = null
            id == "length" -> game.settings.quickMatch = !game.settings.quickMatch
            id == "level" -> {
                val levels = ComputerLevel.entries
                game.settings.computerLevel = levels[(game.settings.computerLevel.ordinal + 1) % levels.size]
            }
            id == "back" -> back()
            id == "go" -> go()
        }
    }

    private fun go() {
        chosen[step] = selected
        if (twoPlayers && step == 0) {
            step = 1
            selected = PetKind.entries.first { it != chosen[0] && game.settings.isUnlocked(it) }
            return
        }
        val first = chosen[0]!!
        if (!twoPlayers) game.settings.lastPet = first
        val second = if (twoPlayers) chosen[1]!! else PetKind.entries.filter { it != first }.random()
        val throws = if (game.settings.quickMatch) MatchConfig.QUICK_MATCH else MatchConfig.FULL_MATCH
        val computer = if (twoPlayers) emptySet() else setOf(Side.RIGHT)
        game.switchTo(GameScreen(game, MatchSetup(first, second, computer, throws, game.settings.computerLevel)))
    }

    private fun back() {
        when {
            offer != null -> offer = null
            step == 1 -> {
                step = 0
                selected = chosen[0] ?: PetKind.DOG
            }
            else -> game.switchTo(MenuScreen(game))
        }
    }

    override fun onKey(keycode: Int): Boolean {
        if (keycode != Input.Keys.BACK && keycode != Input.Keys.ESCAPE) return false
        back()
        return true
    }
}

/** The treats counter shown on the menu and the pet picker. */
object TreatBadge {
    fun draw(s: com.badlogic.gdx.graphics.glutils.ShapeRenderer, x: Float, y: Float) {
        Ui.panel(s, x, y, 230f, 70f, r = 35f)
        s.identity()
        s.translate(x + 44f, y + 35f, 0f)
        s.rotate(0f, 0f, 1f, 20f)
        s.scale(80f, 80f, 1f)
        Art.itemShape(s, ItemType.BONE, ItemType.BONE.radius)
        s.identity()
    }

    fun text(batch: com.badlogic.gdx.graphics.g2d.SpriteBatch, a: Assets, treats: Int, x: Float, y: Float) {
        Ui.textAt(batch, a.h2, "$treats", x + 88f, y + 37f, Palette.INK)
    }
}
