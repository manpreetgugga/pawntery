package com.pawtillery.game

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/** One match: Dog vs Cat, against the computer or two players sharing the phone. */
class GameScreen(game: Pawtillery, private val twoPlayers: Boolean) : BaseScreen(game) {

    private class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val maxLife: Float, val size: Float, val color: Color)

    private class Popup(val text: String, var x: Float, var y: Float, val color: Color, var life: Float, val big: Boolean)

    private var match = newMatch()
    private var computer = ComputerPlayer(Random(System.nanoTime()), game.settings.easyMode)
    private val events = ArrayList<MatchEvent>()
    private val particles = ArrayList<Particle>()
    private val popups = ArrayList<Popup>()

    // Building
    private var selectedPiece = PieceType.CRATE
    private var rotated = false
    private var ghostX: Float? = null
    private val buildQueue = ArrayDeque<Placement>()
    private var buildTimer = 0f
    private var computerBuilding = false

    // Aiming (shared by the player's drag and the computer's animated aim)
    private val selectedItem = arrayOf(ItemType.MUD, ItemType.MUD)
    private var aiming = false
    private val aimStart = Vector2()
    private val aimVel = Vector2()
    private var aimPower = 0f
    private var aimAngle = 0f
    private var computerTimer = 0f
    private var computerPlan: ShotPlan? = null

    private var paused = false
    private var bannerTitle = ""
    private var bannerSub = ""
    private var bannerTime = 0f
    private var hintText = ""
    private var hintTime = 0f
    private var shake = 0f
    private var overTime = 0f

    private val obstacles = ArrayList<Rectangle>()
    private val tmp = Vector2()
    private val tmp2 = Vector2()
    private val tmpColor = Color()

    private fun newMatch() = Match(MatchConfig(easyMode = game.settings.easyMode))

    private fun isHuman(side: Side) = twoPlayers || side == Side.LEFT

    private fun petName(side: Side) = match.pet(side).kind.displayName

    private fun playerLabel(side: Side) = when {
        twoPlayers -> "Player ${side.ordinal + 1}"
        side == Side.LEFT -> "You"
        else -> "Computer"
    }

    private val overlayUp: Boolean get() = paused || (match.phase == Phase.OVER && overTime > 0.9f)

    // ---- Loop ----

    override fun render(delta: Float) {
        val dt = min(delta, 1f / 20f)
        if (!paused) update(dt)
        draw()
    }

    private fun update(dt: Float) {
        match.update(dt)
        match.drainEvents(events)
        for (e in events) onEvent(e)
        events.clear()
        updateComputer(dt)
        updateEffects(dt)
        bannerTime -= dt
        hintTime -= dt
        shake = max(0f, shake - dt * 2.5f)
        if (match.phase == Phase.OVER) overTime += dt
    }

    private fun updateComputer(dt: Float) {
        val side = match.turn
        if (match.phase == Phase.BUILD && buildQueue.isNotEmpty()) {
            buildTimer -= dt
            if (buildTimer <= 0f) {
                val p = buildQueue.removeFirst()
                match.placePiece(p.type, p.rotated, p.x)
                buildTimer = 0.22f
            }
            return
        }
        if (isHuman(side)) return
        when (match.phase) {
            Phase.BUILD -> {
                if (!computerBuilding) {
                    computerBuilding = true
                    buildQueue.addAll(computer.planBuild(side))
                    buildTimer = 0.6f
                    computerTimer = 0f
                } else {
                    computerTimer += dt
                    if (computerTimer > 0.8f) {
                        computerBuilding = false
                        match.finishBuild()
                    }
                }
            }
            Phase.AIM -> {
                computerTimer += dt
                if (computerPlan == null && computerTimer > 0.55f) {
                    val plan = computer.planShot(match, side)
                    computerPlan = plan
                    selectedItem[side.ordinal] = plan.item
                }
                val plan = computerPlan ?: return
                val k = ((computerTimer - 0.55f) / 0.9f).coerceIn(0f, 1f)
                val ease = k * k * (3f - 2f * k)
                setAim(plan.vx * ease, plan.vy * ease)
                aiming = true
                if (computerTimer > 1.75f) {
                    aiming = false
                    match.throwItem(plan.item, plan.vx, plan.vy)
                }
            }
            Phase.FLIGHT -> {
                val hairball = match.activeHairball
                if (computerPlan?.splitAtApex == true && hairball != null && hairball.body.linearVelocity.y < 0f) match.splitHairball()
            }
            else -> Unit
        }
    }

    private fun setAim(vx: Float, vy: Float) {
        aimVel.set(vx, vy)
        aimPower = aimVel.len() / Arena.MAX_SPEED
        aimAngle = atan2(vy, abs(vx)) * MathUtils.radiansToDegrees
    }

    private fun onEvent(e: MatchEvent) {
        when (e) {
            is MatchEvent.BuildStarted -> {
                computerTimer = 0f
                computerBuilding = false
                selectedPiece = PieceType.CRATE
                rotated = false
                if (isHuman(e.side)) {
                    banner(if (twoPlayers) "${playerLabel(e.side)}: build!" else "Build your fort!", "Protect your ${petName(e.side)} with up to ${Arena.PIECES_PER_PLAYER} pieces")
                } else {
                    banner("Computer is building", "Watch where the walls go")
                }
            }
            is MatchEvent.PiecePlaced -> game.sfx("place", 0.8f, 0.9f + MathUtils.random(0.2f))
            is MatchEvent.TurnStarted -> {
                computerTimer = 0f
                computerPlan = null
                aiming = false
                if (match.count(e.side, selectedItem[e.side.ordinal]) == 0) selectedItem[e.side.ordinal] = ItemType.MUD
                val who = when {
                    twoPlayers -> playerLabel(e.side)
                    isHuman(e.side) -> "Your throw"
                    else -> "Computer's throw"
                }
                banner("${petName(e.side)}'s turn", who)
            }
            is MatchEvent.Thrown -> {
                game.sfx("throw", 0.9f, 0.85f + aimPower * 0.3f)
                if (isHuman(e.side)) game.settings.aimTipSeen = true
            }
            is MatchEvent.Split -> {
                game.sfx("pop", 0.6f, 1.6f)
                burst(e.x, e.y, Palette.HAIR, 8, 3f)
            }
            is MatchEvent.Splat -> {
                game.sfx("splat", 0.9f, 0.85f + MathUtils.random(0.3f))
                burst(e.x, e.y, Art.splatColor(e.item), 9, 4f)
            }
            is MatchEvent.Burst -> {
                game.sfx("pop")
                burst(e.x, e.y, Palette.WATER, 18, 6f)
                shake = max(shake, 0.15f)
            }
            is MatchEvent.PetHit -> {
                val amount = e.amount.roundToInt()
                game.sfx("bonk", 0.9f, 0.9f + MathUtils.random(0.25f))
                game.buzz(if (amount >= 12) 60 else 25)
                popups.add(Popup("+$amount", e.x, e.y + 0.7f, Palette.BAD, 1.2f, false))
                if (amount >= 14) popups.add(Popup(HIT_WORDS.random(), e.x, e.y + 1.5f, Palette.GOLD, 1.3f, true))
                if (e.item == null && amount >= 3) popups.add(Popup("Bonk!", e.x, e.y + 1.5f, Palette.WHITE, 1f, true))
                shake = max(shake, 0.25f)
            }
            is MatchEvent.Thud -> {
                val v = (e.strength / 15f).coerceIn(0.15f, 1f)
                game.sfx(if (e.tire) "boing" else "thud", v, 0.9f + MathUtils.random(0.2f))
                if (e.strength > 14f) shake = max(shake, 0.12f)
            }
            is MatchEvent.GameOver -> {
                game.sfx("win")
                overTime = 0f
            }
        }
    }

    private fun banner(title: String, sub: String) {
        bannerTitle = title
        bannerSub = sub
        bannerTime = 1.7f
    }

    private fun hint(text: String) {
        hintText = text
        hintTime = 1.8f
    }

    private fun burst(x: Float, y: Float, color: Color, count: Int, speed: Float) {
        repeat(count) {
            val a = MathUtils.random(20f, 160f)
            val v = MathUtils.random(0.4f, 1f) * speed
            val life = MathUtils.random(0.35f, 0.7f)
            particles.add(Particle(x, y, MathUtils.cosDeg(a) * v, MathUtils.sinDeg(a) * v, life, life, MathUtils.random(0.06f, 0.14f), color))
        }
    }

    private fun updateEffects(dt: Float) {
        val pi = particles.iterator()
        while (pi.hasNext()) {
            val p = pi.next()
            p.vy -= 12f * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.life -= dt
            if (p.life <= 0f) pi.remove()
        }
        val ti = popups.iterator()
        while (ti.hasNext()) {
            val p = ti.next()
            p.y += 1.1f * dt
            p.life -= dt
            if (p.life <= 0f) ti.remove()
        }
    }

    // ---- Input ----

    override fun onPress(ui: Vector2, world: Vector2) {
        if (overlayUp) return
        val side = match.turn
        when (match.phase) {
            Phase.BUILD -> if (isHuman(side) && buildQueue.isEmpty() && match.piecesLeft(side) > 0) ghostX = world.x
            Phase.AIM -> if (isHuman(side)) {
                aiming = true
                aimStart.set(ui)
                setAim(0f, 0f)
            }
            Phase.FLIGHT -> if (isHuman(side) && match.activeHairball != null) match.splitHairball()
            else -> Unit
        }
    }

    override fun onDrag(ui: Vector2, world: Vector2) {
        if (overlayUp) return
        if (ghostX != null) ghostX = world.x
        if (aiming && match.phase == Phase.AIM && isHuman(match.turn)) updateAimFromDrag(ui)
    }

    override fun onRelease(ui: Vector2, world: Vector2) {
        val gx = ghostX
        if (gx != null) {
            ghostX = null
            if (match.phase == Phase.BUILD && !match.placePiece(selectedPiece, rotated, gx)) hint("No room there. Try another spot.")
        }
        if (aiming && match.phase == Phase.AIM && isHuman(match.turn)) {
            aiming = false
            updateAimFromDrag(ui)
            if (aimPower < MIN_POWER) {
                hint("Pull back farther, then let go")
            } else {
                match.throwItem(selectedItem[match.turn.ordinal], aimVel.x, aimVel.y)
            }
        }
    }

    /** Slingshot aiming: pull away from where you touched, and the throw goes the opposite way. */
    private fun updateAimFromDrag(ui: Vector2) {
        val dir = match.turn.dir
        val dx = aimStart.x - ui.x
        val dy = aimStart.y - ui.y
        val power = (Vector2.len(dx, dy) / PULL_FOR_FULL_POWER).coerceAtMost(1f)
        val angle = (atan2(dy, dx * dir) * MathUtils.radiansToDegrees).coerceIn(0f, 85f)
        val speed = power * Arena.MAX_SPEED
        setAim(dir * MathUtils.cosDeg(angle) * speed, MathUtils.sinDeg(angle) * speed)
    }

    override fun onButton(id: String) {
        when {
            id == "pause" -> paused = true
            id == "resume" -> paused = false
            id == "restart" || id == "rematch" -> restart()
            id == "menu" -> game.switchTo(MenuScreen(game))
            id.startsWith("piece:") -> selectedPiece = PieceType.valueOf(id.removePrefix("piece:"))
            id == "rotate" -> rotated = !rotated
            id == "undo" -> match.undoPiece()
            id == "auto" -> autoBuild()
            id == "ready" -> match.finishBuild()
            id.startsWith("item:") -> {
                val item = ItemType.valueOf(id.removePrefix("item:"))
                selectedItem[match.turn.ordinal] = item
                hint(item.hint)
            }
        }
    }

    override fun onKey(keycode: Int): Boolean {
        if (keycode != Input.Keys.BACK && keycode != Input.Keys.ESCAPE) return false
        when {
            match.phase == Phase.OVER -> game.switchTo(MenuScreen(game))
            else -> paused = !paused
        }
        return true
    }

    override fun pause() {
        if (match.phase != Phase.OVER) paused = true
    }

    private fun autoBuild() {
        while (match.undoPiece()) Unit
        buildQueue.clear()
        buildQueue.addAll(computer.planBuild(match.turn))
        buildTimer = 0f
    }

    private fun restart() {
        match.dispose()
        match = newMatch()
        computer = ComputerPlayer(Random(System.nanoTime()), game.settings.easyMode)
        particles.clear()
        popups.clear()
        buildQueue.clear()
        selectedItem.fill(ItemType.MUD)
        aiming = false
        ghostX = null
        paused = false
        computerPlan = null
        computerTimer = 0f
        computerBuilding = false
        overTime = 0f
    }

    override fun dispose() {
        match.dispose()
    }

    // ---- Drawing ----

    private fun draw() {
        clear()
        worldCam.position.set(
            Arena.WIDTH / 2f + if (shake > 0f) MathUtils.random(-1f, 1f) * shake * 0.3f else 0f,
            worldView.worldHeight / 2f + if (shake > 0f) MathUtils.random(-1f, 1f) * shake * 0.3f else 0f,
            0f,
        )
        worldCam.update()
        beginWorld()
        val s = shapes
        Art.background(s, viewLeft, viewRight, viewTop, match.time, match.wind)
        Art.hedges(s, viewLeft, viewRight)
        Art.fence(s)
        Art.ground(s, viewLeft, viewRight)
        Art.groundDecals(s, match.groundDecals)
        if (match.phase == Phase.BUILD && isHuman(match.turn) && buildQueue.isEmpty()) drawBuildZone()
        for (p in match.pieces) Art.piece(s, p)
        for (p in match.pets.values) Art.pet(s, p, match.time)
        for (p in match.projectiles) Art.projectile(s, p)
        drawGhost()
        drawAim()
        drawTurnMarker()
        for (p in particles) {
            s.color = tmpColor.set(p.color).also { it.a *= min(1f, p.life / p.maxLife * 1.5f) }
            s.circle(p.x, p.y, p.size, 8)
        }
        s.end()

        buttons = layoutButtons()
        beginUiShapes()
        drawHudShapes()
        if (!overlayUp) Ui.buttons(s, buttons, pressedId)
        s.end()
        beginUiText()
        drawHudText()
        if (!overlayUp) Ui.buttonLabels(batch, game.assets, buttons, pressedId)
        batch.end()

        if (overlayUp) {
            beginUiShapes()
            drawOverlayShapes()
            Ui.buttons(s, buttons, pressedId)
            s.end()
            beginUiText()
            drawOverlayText()
            Ui.buttonLabels(batch, game.assets, buttons, pressedId)
            batch.end()
        }
    }

    private fun drawBuildZone() {
        val side = match.turn
        val x0 = Arena.buildMinX(side)
        val x1 = Arena.buildMaxX(side)
        val s = shapes
        s.color = Palette.ZONE
        s.rect(x0, Arena.GROUND_Y, x1 - x0, Arena.MAX_BUILD_HEIGHT - Arena.GROUND_Y)
        s.color = tmpColor.set(1f, 1f, 1f, 0.7f)
        var y = Arena.GROUND_Y
        while (y < Arena.MAX_BUILD_HEIGHT) {
            s.rect(x0 - 0.03f, y, 0.06f, 0.3f)
            s.rect(x1 - 0.03f, y, 0.06f, 0.3f)
            y += 0.6f
        }
        var x = x0
        while (x < x1) {
            s.rect(x, Arena.MAX_BUILD_HEIGHT - 0.03f, 0.3f, 0.06f)
            x += 0.6f
        }
    }

    private fun drawGhost() {
        val gx = ghostX ?: return
        val pos = match.dropPosition(match.turn, selectedPiece, rotated, gx, tmp) ?: return
        val s = shapes
        s.color = tmpColor.set(1f, 1f, 1f, 0.5f)
        var y = pos.y
        while (y > Arena.GROUND_Y) {
            s.rect(pos.x - 0.025f, y - 0.2f, 0.05f, 0.2f)
            y -= 0.45f
        }
        s.identity()
        s.translate(pos.x, pos.y, 0f)
        if (rotated) s.rotate(0f, 0f, 1f, 90f)
        Art.pieceShape(s, selectedPiece, 0.6f)
        s.identity()
    }

    private fun drawAim() {
        if (match.phase != Phase.AIM) return
        val side = match.turn
        val origin = Arena.launchPoint(side, tmp2)
        val s = shapes
        val item = selectedItem[side.ordinal]

        // Previous throw, so players can adjust from it.
        val last = match.lastVelocity(side)
        if (last != null && isHuman(side)) {
            val len = 0.8f + 2.2f * last.len() / Arena.MAX_SPEED
            val nx = last.x / last.len()
            val ny = last.y / last.len()
            s.color = tmpColor.set(Palette.INK).also { it.a = 0.3f }
            s.rectLine(origin.x, origin.y, origin.x + nx * len, origin.y + ny * len, 0.07f)
        }

        if (!aiming || aimPower < 0.02f) {
            s.identity()
            s.translate(origin.x, origin.y, 0f)
            Art.itemShape(s, item, item.radius)
            s.identity()
            if (isHuman(side) && !game.settings.aimTipSeen) drawAimTip(origin)
            return
        }

        obstacles.clear()
        for (p in match.pieces) if (p.side == side.other) obstacles.add(match.bodyBounds(p.body, Rectangle()))
        val steps = if (match.config.easyMode) 420 else 26
        Trajectory.simulate(origin.x, origin.y, aimVel.x, aimVel.y, item, side, match.wind, steps) { step, x, y, _, _ ->
            val blocked = y < Arena.GROUND_Y ||
                (abs(x - Arena.FENCE_X) < Arena.FENCE_HALF_W && y < Arena.FENCE_PEAK) ||
                x < 0f || x > Arena.WIDTH ||
                obstacles.any { it.contains(x, y) }
            if (!blocked && step % 4 == 0) {
                val fade = if (match.config.easyMode) 1f else 1f - step / steps.toFloat()
                s.color = tmpColor.set(Palette.INK).also { it.a = 0.45f * fade }
                s.circle(x, y, 0.12f, 10)
                s.color = tmpColor.set(1f, 1f, 1f, 0.95f * fade)
                s.circle(x, y, 0.085f, 10)
            }
            !blocked
        }

        val len = 0.8f + 2.2f * aimPower
        val nx = aimVel.x / aimVel.len()
        val ny = aimVel.y / aimVel.len()
        val c = when {
            aimPower < 0.5f -> tmpColor.set(Palette.GOOD).lerp(Palette.GOLD, aimPower * 2f)
            else -> tmpColor.set(Palette.GOLD).lerp(Palette.BAD, (aimPower - 0.5f) * 2f)
        }
        val tipX = origin.x + nx * len
        val tipY = origin.y + ny * len
        s.color = Palette.INK
        s.rectLine(origin.x, origin.y, tipX, tipY, 0.2f)
        s.triangle(tipX + nx * 0.42f, tipY + ny * 0.42f, tipX - ny * 0.3f, tipY + nx * 0.3f, tipX + ny * 0.3f, tipY - nx * 0.3f)
        s.color = c
        s.rectLine(origin.x, origin.y, tipX, tipY, 0.12f)
        s.triangle(tipX + nx * 0.32f, tipY + ny * 0.32f, tipX - ny * 0.22f, tipY + nx * 0.22f, tipX + ny * 0.22f, tipY - nx * 0.22f)
        s.identity()
        s.translate(origin.x, origin.y, 0f)
        Art.itemShape(s, item, item.radius)
        s.identity()
    }

    /** A ghost finger pulling back, shown until the player's first throw. */
    private fun drawAimTip(origin: Vector2) {
        val t = (match.time % 1.6f) / 1.2f
        val k = t.coerceAtMost(1f)
        val dir = match.turn.dir
        val startX = origin.x + dir * 3.5f
        val startY = origin.y + 3f
        val x = startX - dir * 2.2f * k
        val y = startY - 1.8f * k
        val s = shapes
        s.color = tmpColor.set(1f, 1f, 1f, 0.6f)
        var i = 0f
        while (i < k) {
            s.circle(startX - dir * 2.2f * i, startY - 1.8f * i, 0.06f, 8)
            i += 0.12f
        }
        s.color = tmpColor.set(1f, 1f, 1f, 0.85f)
        s.circle(x, y, 0.32f, 18)
        s.color = tmpColor.set(Palette.INK).also { it.a = 0.6f }
        s.circle(x, y, 0.14f, 12)
    }

    private fun drawTurnMarker() {
        if (match.phase != Phase.AIM) return
        val pos = match.pet(match.turn).body.position
        val y = pos.y + Arena.PET_H / 2f + 1.15f + MathUtils.sin(match.time * 5f) * 0.15f
        val s = shapes
        s.color = Palette.INK
        s.triangle(pos.x - 0.42f, y + 0.08f, pos.x + 0.42f, y + 0.08f, pos.x, y - 0.5f)
        s.color = Palette.GOLD
        s.triangle(pos.x - 0.3f, y, pos.x + 0.3f, y, pos.x, y - 0.38f)
    }

    // ---- HUD ----

    private fun layoutButtons(): List<Button> {
        val list = ArrayList<Button>()
        if (paused) {
            val cx = uiW / 2f
            list += Button("resume", cx - 200f, uiH / 2f + 10f, 400f, 96f, "Resume", ButtonStyle.PRIMARY)
            list += Button("restart", cx - 200f, uiH / 2f - 110f, 400f, 96f, "Restart")
            list += Button("menu", cx - 200f, uiH / 2f - 230f, 400f, 96f, "Quit to menu")
            return list
        }
        if (match.phase == Phase.OVER) {
            if (overTime > 0.9f) {
                val cx = uiW / 2f
                list += Button("rematch", cx - 330f, uiH / 2f - 250f, 320f, 100f, "Rematch", ButtonStyle.PRIMARY)
                list += Button("menu", cx + 10f, uiH / 2f - 250f, 320f, 100f, "Menu")
            }
            return list
        }
        list += Button("pause", 20f, 20f, 92f, 84f, "", icon = { s, cx, cy, _ ->
            s.color = Palette.INK
            s.rect(cx - 16f, cy - 34f, 11f, 34f)
            s.rect(cx + 5f, cy - 34f, 11f, 34f)
        })
        val side = match.turn
        if (match.phase == Phase.BUILD && isHuman(side) && buildQueue.isEmpty()) addBuildTray(list, side)
        if ((match.phase == Phase.AIM) && isHuman(side)) addItemTray(list, side)
        return list
    }

    private fun addBuildTray(list: MutableList<Button>, side: Side) {
        val bw = 124f
        val bh = 118f
        val gap = 10f
        val controls = listOf(124f, 124f, 124f, 172f)
        val total = PieceType.entries.size * (bw + gap) + 24f + controls.sum() + gap * (controls.size - 1)
        var x = (uiW - total) / 2f
        val y = uiH - 22f - bh
        val left = match.piecesLeft(side)
        for (type in PieceType.entries) {
            list += Button("piece:${type.name}", x, y, bw, bh, type.displayName, enabled = left > 0, selected = type == selectedPiece,
                icon = { s, cx, cy, size -> icon(cx, cy - 4f, size / max(type.w, type.h) * 0.95f) { Art.pieceShape(s, type, 1f) } })
            x += bw + gap
        }
        x += 24f
        list += Button("rotate", x, y, controls[0], bh, "Turn", enabled = left > 0, selected = rotated,
            icon = { s, cx, cy, size ->
                val t = selectedPiece
                icon(cx, cy - 4f, size / max(t.w, t.h) * 0.95f) {
                    s.rotate(0f, 0f, 1f, if (rotated) 0f else 90f)
                    Art.pieceShape(s, t, 1f)
                }
            })
        x += controls[0] + gap
        list += Button("undo", x, y, controls[1], bh, "Undo", enabled = left < Arena.PIECES_PER_PLAYER, icon = { s, cx, cy, _ ->
            s.color = Palette.INK
            s.rectLine(cx - 22f, cy, cx + 20f, cy, 9f)
            s.triangle(cx - 34f, cy, cx - 16f, cy + 16f, cx - 16f, cy - 16f)
        })
        x += controls[1] + gap
        list += Button("auto", x, y, controls[2], bh, "Auto", icon = { s, cx, cy, _ ->
            s.color = Palette.GOLD_DARK
            star(s, cx, cy, 26f)
        })
        x += controls[2] + gap
        list += Button("ready", x, y, controls[3], bh, "Ready!", ButtonStyle.PRIMARY)
    }

    private fun star(s: com.badlogic.gdx.graphics.glutils.ShapeRenderer, cx: Float, cy: Float, r: Float) {
        for (i in 0 until 5) {
            val a = 90f + i * 72f
            s.triangle(
                cx, cy,
                cx + MathUtils.cosDeg(a) * r, cy + MathUtils.sinDeg(a) * r,
                cx + MathUtils.cosDeg(a + 36f) * r * 0.45f, cy + MathUtils.sinDeg(a + 36f) * r * 0.45f,
            )
            s.triangle(
                cx, cy,
                cx + MathUtils.cosDeg(a) * r, cy + MathUtils.sinDeg(a) * r,
                cx + MathUtils.cosDeg(a - 36f) * r * 0.45f, cy + MathUtils.sinDeg(a - 36f) * r * 0.45f,
            )
        }
    }

    private fun addItemTray(list: MutableList<Button>, side: Side) {
        val items = match.items(side)
        val bw = 184f
        val bh = 124f
        val gap = 14f
        var x = (uiW - (items.size * bw + (items.size - 1) * gap)) / 2f
        for (item in items) {
            val n = match.count(side, item)
            list += Button("item:${item.name}", x, 24f, bw, bh, item.displayName,
                enabled = n != 0, selected = item == selectedItem[side.ordinal],
                badge = if (n < 0) "∞" else "x$n",
                icon = { s, cx, cy, size -> icon(cx, cy - 2f, size * 0.42f / item.radius) { Art.itemShape(s, item, item.radius) } })
            x += bw + gap
        }
    }

    private fun drawHudShapes() {
        val s = shapes
        if (match.phase != Phase.BUILD) {
            petPanel(Side.LEFT)
            petPanel(Side.RIGHT)
            windPanel()
        } else if (isHuman(match.turn) && buildQueue.isEmpty()) {
            s.color = Palette.SHADE
            Ui.roundRect(s, uiW / 2f - 470f, uiH - 22f - 118f - 72f, 940f, 52f, 26f)
        }
        val side = match.turn
        if (match.phase == Phase.AIM && isHuman(side) && !aiming && hintTime <= 0f) {
            s.color = Palette.SHADE
            Ui.roundRect(s, uiW / 2f - 380f, 24f + 124f + 22f, 760f, 48f, 24f)
        }
        if (hintTime > 0f) {
            s.color = Palette.SHADE
            Ui.roundRect(s, uiW / 2f - 440f, uiH * 0.36f - 30f, 880f, 60f, 30f)
        }
    }

    private fun petPanel(side: Side) {
        val s = shapes
        val w = 470f
        val h = 112f
        val x = if (side == Side.LEFT) 20f else uiW - 20f - w
        val y = uiH - 20f - h
        val active = match.turn == side && match.phase != Phase.OVER
        if (active) {
            s.color = Palette.GOLD_DARK
            Ui.roundRect(s, x - 6f, y - 12f, w + 12f, h + 18f, 26f)
        }
        Ui.panel(s, x, y, w, h)
        val pet = match.pet(side)
        val iconX = if (side == Side.LEFT) x + 62f else x + w - 62f
        s.color = if (side == Side.LEFT) Palette.TEAL else Palette.ACCENT
        s.circle(iconX, y + h / 2f, 44f, 30)
        icon(iconX - side.dir * 4f, y + h / 2f - 26f, 72f) {
            s.scale(side.dir, 1f, 1f)
            Art.petShape(s, pet.kind, match.time, pet.hitTimer > 0f, false, pet.mess)
        }
        // Mess bar
        val barX = if (side == Side.LEFT) x + 124f else x + 22f
        val barW = w - 146f
        s.color = tmpColor.set(Palette.INK).also { it.a = 0.14f }
        Ui.roundRect(s, barX, y + 18f, barW, 30f, 15f)
        val f = pet.mess / Arena.MESS_TO_LOSE
        if (f > 0f) {
            s.color = if (f < 0.5f) tmpColor.set(Palette.GOOD).lerp(Palette.GOLD, f * 2f) else tmpColor.set(Palette.GOLD).lerp(Palette.BAD, (f - 0.5f) * 2f)
            Ui.roundRect(s, barX, y + 18f, max(30f, barW * f), 30f, 15f)
        }
    }

    private fun windPanel() {
        val s = shapes
        val w = 300f
        val x = uiW / 2f - w / 2f
        val y = uiH - 20f - 90f
        Ui.panel(s, x, y, w, 90f)
        val wind = match.wind
        if (wind != 0f) {
            val len = 30f + abs(wind) / 3.5f * 90f
            val cx = uiW / 2f
            val cy = y + 30f
            val d = if (wind > 0f) 1f else -1f
            s.color = Palette.TEAL
            s.rectLine(cx - d * len / 2f, cy, cx + d * len / 2f, cy, 12f)
            s.triangle(cx + d * (len / 2f + 22f), cy, cx + d * len / 2f, cy + 18f, cx + d * len / 2f, cy - 18f)
        }
    }

    private fun drawHudText() {
        val a = game.assets
        if (match.phase != Phase.BUILD) {
            for (side in Side.entries) {
                val w = 470f
                val h = 112f
                val x = if (side == Side.LEFT) 20f else uiW - 20f - w
                val y = uiH - 20f - h
                val textX = if (side == Side.LEFT) x + 124f else x + 22f
                val pet = match.pet(side)
                Ui.textAt(batch, a.h2, pet.kind.displayName, textX, y + 78f, Palette.INK)
                Ui.textAt(batch, a.small, "${playerLabel(side)} · ${match.throwsLeft(side)} throws left", textX + w - 146f, y + 78f, Palette.INK, rightAligned = true, alpha = 0.75f)
                Ui.text(batch, a.small, "Mess ${pet.mess.roundToInt()}%", textX + (w - 146f) / 2f, y + 33f, Palette.INK)
            }
            val wy = uiH - 20f - 90f
            val label = when {
                match.config.easyMode -> "No wind"
                match.wind == 0f -> "Calm"
                else -> "Wind ${"%.1f".format(abs(match.wind))}"
            }
            Ui.text(batch, a.small, label, uiW / 2f, wy + (if (match.wind != 0f) 66f else 45f), Palette.INK)
        } else if (isHuman(match.turn) && buildQueue.isEmpty()) {
            val left = match.piecesLeft(match.turn)
            val msg = if (left > 0) "Tap a piece, then touch your yard to drop it  ·  $left left" else "Fort done! Tap Ready when you like it"
            Ui.text(batch, a.small, msg, uiW / 2f, uiH - 22f - 118f - 46f, Palette.WHITE)
        }

        val side = match.turn
        if (match.phase == Phase.AIM && isHuman(side) && !aiming && hintTime <= 0f) {
            val msg = if (!game.settings.aimTipSeen) "Drag back anywhere and let go to throw" else selectedItem[side.ordinal].hint
            Ui.text(batch, a.small, msg, uiW / 2f, 24f + 124f + 46f, Palette.WHITE, maxWidth = 740f)
        }
        if (match.phase == Phase.FLIGHT && isHuman(side) && match.activeHairball != null) {
            Ui.text(batch, a.h2, "Tap to split!", uiW / 2f, uiH * 0.36f, Palette.WHITE)
        }
        if (aiming && match.phase == Phase.AIM && aimPower >= 0.02f) {
            val p = worldToUi(Arena.launchPoint(side).x, Arena.launchPoint(side).y + 1.6f, tmp)
            Ui.text(batch, a.h2, "${aimAngle.roundToInt()}°  ${(aimPower * 100).roundToInt()}%", p.x, p.y, Palette.WHITE)
        }
        if (hintTime > 0f) Ui.text(batch, a.body, hintText, uiW / 2f, uiH * 0.36f, Palette.WHITE, min(1f, hintTime * 3f), 860f)

        for (p in popups) {
            val pos = worldToUi(p.x, p.y, tmp)
            val alpha = min(1f, p.life * 2.5f)
            Ui.text(batch, if (p.big) a.h1 else a.h2, p.text, pos.x, pos.y, p.color, alpha)
        }
        if (bannerTime > 0f && match.phase != Phase.OVER) {
            val alpha = min(1f, bannerTime * 2.5f) * min(1f, (1.7f - bannerTime) * 6f)
            Ui.text(batch, a.h1, bannerTitle, uiW / 2f, uiH * 0.62f, Palette.WHITE, alpha)
            Ui.text(batch, a.h2, bannerSub, uiW / 2f, uiH * 0.62f - 64f, Palette.INK, alpha)
        }
        if (match.phase == Phase.OVER && overTime <= 0.9f) {
            Ui.text(batch, a.title, "Splat!", uiW / 2f, uiH * 0.6f, Palette.WHITE, min(1f, overTime * 4f))
        }
    }

    // ---- Overlays ----

    private fun drawOverlayShapes() {
        val s = shapes
        s.color = Palette.SHADE
        s.rect(0f, 0f, uiW, uiH)
        if (paused) {
            Ui.panel(s, uiW / 2f - 260f, uiH / 2f - 270f, 520f, 520f)
            return
        }
        Ui.panel(s, uiW / 2f - 420f, uiH / 2f - 290f, 840f, 600f)
        val winner = match.winner ?: return
        val pet = match.pet(winner)
        val bounce = abs(MathUtils.sin(overTime * 4f)) * 14f
        icon(uiW / 2f, uiH / 2f + 70f + bounce, 120f) {
            s.scale(winner.dir, 1f, 1f)
            Art.petShape(s, pet.kind, overTime, false, false, 0f)
        }
    }

    private fun drawOverlayText() {
        val a = game.assets
        val cx = uiW / 2f
        if (paused) {
            Ui.text(batch, a.h1, "Paused", cx, uiH / 2f + 180f, Palette.INK)
            return
        }
        val winner = match.winner
        val title = when {
            winner == null -> "It's a draw!"
            twoPlayers -> "${petName(winner)} wins!"
            winner == Side.LEFT -> "You win!"
            else -> "Computer wins!"
        }
        Ui.text(batch, a.title, title, cx, uiH / 2f + 230f, Palette.WHITE)
        val loser = winner?.other
        val why = when {
            loser != null && match.pet(loser).mess >= Arena.MESS_TO_LOSE -> "${petName(loser)} is 100% messy!"
            winner == null -> "Exactly as messy as each other"
            else -> "Least messy after ${Arena.THROWS_PER_PLAYER} throws each"
        }
        Ui.text(batch, a.h2, why, cx, uiH / 2f - 60f, Palette.INK)
        val l = match.pet(Side.LEFT)
        val r = match.pet(Side.RIGHT)
        Ui.text(batch, a.body, "${l.kind.displayName} mess ${l.mess.roundToInt()}%     ${r.kind.displayName} mess ${r.mess.roundToInt()}%", cx, uiH / 2f - 112f, Palette.INK, 0.8f)
    }

    private companion object {
        const val PULL_FOR_FULL_POWER = 330f
        const val MIN_POWER = 0.12f
        val HIT_WORDS = listOf("Splat!", "Gotcha!", "Bullseye!", "Yuck!", "Messy!")
    }
}
