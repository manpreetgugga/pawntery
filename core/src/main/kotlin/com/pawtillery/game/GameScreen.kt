package com.pawtillery.game

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/** One match: against the computer, two players sharing the phone, or (for demos) computer vs computer. */
class GameScreen(game: Pawtillery, private val setup: MatchSetup) : BaseScreen(game) {

    private class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val maxLife: Float, val size: Float, val color: Color)

    private class Popup(val text: String, var x: Float, var y: Float, val color: Color, var life: Float, val big: Boolean)

    private var match = newMatch()
    private var computer = newComputer()
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

    // Aiming. Each player's aim is kept between turns, so a throw can be repeated or adjusted one
    // degree or one percent at a time; dragging back sets it directly.
    private val selectedItem = arrayOf(ItemType.MUD, ItemType.MUD)
    private val aimAngle = floatArrayOf(45f, 45f)
    private val aimPower = floatArrayOf(0.6f, 0.6f)
    private var dragging = false
    private var dragMoved = false
    private val dragStart = Vector2()
    private val computerAim = Vector2()
    private var computerAiming = false
    private var computerTimer = 0f
    private var computerPlan: ShotPlan? = null
    private var holdTime = 0f
    private var repeatTimer = 0f

    private var paused = false
    private var bannerTitle = ""
    private var bannerSub = ""
    private var bannerTime = 0f
    private var hintText = ""
    private var hintTime = 0f
    private var shake = 0f
    private var slowMotion = 0f
    private val hop = FloatArray(2)
    private var overTime = 0f
    private var treatsEarned = 0
    /** The optional rewarded ad that doubles a game's treats: not offered, showing, or used. */
    private var adPlaying = false
    private var adUsed = false
    private var unlockable: PetKind? = null
    private val humans = Side.entries.filter { it !in setup.computer }

    private val obstacles = ArrayList<Rectangle>()
    private val bounds = Rectangle()
    private val tmp = Vector2()
    private val tmp2 = Vector2()
    private val vel = Vector2()
    private val tmpColor = Color()

    private fun newMatch() = Match(
        MatchConfig(
            easyMode = game.settings.easyMode,
            leftKind = setup.left,
            rightKind = setup.right,
            throwsPerPlayer = setup.throwsPerPlayer,
            challenge = setup.challenge,
        ),
    )

    private val challenge: Challenge? get() = setup.challenge
    private var starsEarned = 0

    private fun newComputer() = ComputerPlayer(Random(System.nanoTime()), setup.level)

    private fun isHuman(side: Side) = side !in setup.computer

    private fun petName(side: Side) = match.pet(side).kind.displayName

    private fun playerLabel(side: Side) = when {
        challenge != null -> if (side == Side.LEFT) "You" else "Target"
        humans.size == 2 -> "Player ${side.ordinal + 1}"
        isHuman(side) -> "You"
        else -> setup.level.displayName
    }

    private val overlayUp: Boolean get() = paused || (match.phase == Phase.OVER && overTime > 0.9f)

    private val humanAiming: Boolean get() = match.phase == Phase.AIM && isHuman(match.turn) && !overlayUp

    // ---- Loop ----

    override fun render(delta: Float) {
        val dt = min(delta, 1f / 20f)
        if (!paused) update(dt)
        draw()
        drawFade(delta)
    }

    private fun update(realDt: Float) {
        // The hit that decides the match plays in slow motion.
        val dt = if (slowMotion > 0f) realDt * 0.3f else realDt
        slowMotion -= realDt
        match.update(dt)
        match.drainEvents(events)
        for (e in events) onEvent(e)
        events.clear()
        updateComputer(dt)
        updateEffects(dt)
        updateHeldButton(realDt)
        for (i in hop.indices) if (hop[i] > 0f) hop[i] -= dt
        bannerTime -= realDt
        hintTime -= realDt
        shake = max(0f, shake - realDt * 2.5f)
        if (match.phase == Phase.OVER) overTime += realDt
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
                computerAim.set(plan.vx * ease, plan.vy * ease)
                computerAiming = true
                if (computerTimer > 1.75f) {
                    computerAiming = false
                    match.throwItem(plan.item, plan.vx, plan.vy)
                }
            }
            Phase.FLIGHT -> {
                val splitter = match.activeSplitter
                if (computerPlan?.splitAtApex == true && splitter != null && splitter.body.linearVelocity.y < 0f) match.splitActive()
            }
            else -> Unit
        }
    }

    /** The current aim of [side] as a launch velocity. */
    private fun aimVelocity(side: Side, out: Vector2): Vector2 {
        if (!isHuman(side)) return out.set(computerAim)
        val speed = aimPower[side.ordinal] * Arena.MAX_SPEED
        val a = aimAngle[side.ordinal]
        return out.set(side.dir * MathUtils.cosDeg(a) * speed, MathUtils.sinDeg(a) * speed)
    }

    private fun onEvent(e: MatchEvent) {
        when (e) {
            is MatchEvent.BuildStarted -> {
                computerTimer = 0f
                computerBuilding = false
                selectedPiece = PieceType.CRATE
                rotated = false
                if (isHuman(e.side)) {
                    banner(if (humans.size == 2) "${playerLabel(e.side)}: build!" else "Build your fort!", "Protect your ${petName(e.side)} with up to ${Arena.PIECES_PER_PLAYER} pieces")
                } else {
                    banner("Computer is building", "Watch where the walls go")
                }
            }
            is MatchEvent.PiecePlaced -> game.sfx("place", 0.8f, 0.9f + MathUtils.random(0.2f))
            is MatchEvent.TurnStarted -> {
                computerTimer = 0f
                computerPlan = null
                computerAiming = false
                dragging = false
                if (match.count(e.side, selectedItem[e.side.ordinal]) == 0) selectedItem[e.side.ordinal] = ItemType.MUD
                val c = challenge
                if (c != null) {
                    if (match.stats(Side.LEFT).throws == 0) banner("Level ${c.number}: ${c.title}", c.brief)
                } else {
                    val who = when {
                        humans.size == 2 -> playerLabel(e.side)
                        isHuman(e.side) -> "Your throw"
                        else -> "Computer's throw"
                    }
                    banner("${petName(e.side)}'s turn", who)
                }
            }
            is MatchEvent.Thrown -> {
                game.sfx("throw", 0.9f, 0.85f + aimVelocity(e.side, vel).len() / Arena.MAX_SPEED * 0.3f)
                hop[e.side.ordinal] = HOP_TIME
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
                if (amount >= 2) popups.add(Popup("+$amount", e.x, e.y + 0.7f, Palette.BAD, 1.2f, false))
                when {
                    e.trick != null -> popups.add(Popup(e.trick, e.x, e.y + 2.1f, Palette.GOLD, 1.6f, true))
                    e.item == null && amount >= 3 -> popups.add(Popup("Bonk!", e.x, e.y + 2.1f, Palette.WHITE, 1f, true))
                    amount >= 14 -> popups.add(Popup(HIT_WORDS.random(), e.x, e.y + 2.1f, Palette.GOLD, 1.3f, true))
                }
                shake = max(shake, 0.25f)
                if (match.pet(e.side).mess >= Arena.MESS_TO_LOSE && slowMotion <= 0f) slowMotion = 1.3f
            }
            is MatchEvent.Thud -> {
                val v = (e.strength / 15f).coerceIn(0.15f, 1f)
                game.sfx(if (e.tire) "boing" else "thud", v, 0.9f + MathUtils.random(0.2f))
                if (e.strength > 14f) shake = max(shake, 0.12f)
            }
            is MatchEvent.GameOver -> {
                game.sfx("win")
                overTime = 0f
                giveTreats(e.winner)
            }
        }
    }

    private fun giveTreats(winner: Side?) {
        val c = challenge
        if (c != null) {
            starsEarned = match.challengeStars()
            treatsEarned = game.settings.recordChallenge(c.number, starsEarned)
            return
        }
        if (humans.isEmpty()) return
        val result = when {
            humans.size == 2 || winner == null -> GameResult.DRAW
            winner in humans -> GameResult.WIN
            else -> GameResult.LOSS
        }
        val hits = humans.sumOf { match.stats(it).hits }
        val tricks = humans.sumOf { match.stats(it).tricks }
        val factor = if (humans.size == 1) setup.level.treatFactor else 1f
        treatsEarned = game.settings.recordGame(result, setup.throwsPerPlayer, hits, tricks, factor)
        unlockable = PetKind.entries.firstOrNull { !game.settings.isUnlocked(it) && game.settings.treats >= it.price }
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
                dragging = true
                dragMoved = false
                dragStart.set(ui)
            }
            Phase.FLIGHT -> if (isHuman(side) && match.activeSplitter != null) match.splitActive()
            else -> Unit
        }
    }

    override fun onDrag(ui: Vector2, world: Vector2) {
        if (overlayUp) return
        if (ghostX != null) ghostX = world.x
        if (dragging && humanAiming) {
            if (!dragMoved && dragStart.dst(ui) > DRAG_SLOP) dragMoved = true
            if (dragMoved) aimFromDrag(ui)
        }
    }

    override fun onRelease(ui: Vector2, world: Vector2) {
        val gx = ghostX
        if (gx != null) {
            ghostX = null
            if (match.phase == Phase.BUILD && !match.placePiece(selectedPiece, rotated, gx)) hint("No room there. Try another spot.")
        }
        if (dragging && humanAiming) {
            dragging = false
            if (!dragMoved) return
            aimFromDrag(ui)
            if (aimPower[match.turn.ordinal] < MIN_POWER) hint("Pull back farther, then let go") else throwNow()
        }
        dragging = false
    }

    /** Slingshot aiming: pull away from where you touched, and the throw goes the opposite way. */
    private fun aimFromDrag(ui: Vector2) {
        val side = match.turn
        val dx = dragStart.x - ui.x
        val dy = dragStart.y - ui.y
        aimPower[side.ordinal] = (Vector2.len(dx, dy) / PULL_FOR_FULL_POWER).coerceAtMost(1f)
        aimAngle[side.ordinal] = (atan2(dy, dx * side.dir) * MathUtils.radiansToDegrees).coerceIn(0f, 85f).roundToInt().toFloat()
    }

    /** For automated UI tours: aims the current player's throw as a sharp computer player would, and throws it. */
    fun devAutoThrow() {
        if (!humanAiming) return
        val side = match.turn
        val plan = ComputerPlayer(Random(1), ComputerLevel.CHAMPION).planShot(match, side)
        selectedItem[side.ordinal] = plan.item
        aimAngle[side.ordinal] = atan2(plan.vy, abs(plan.vx)) * MathUtils.radiansToDegrees
        aimPower[side.ordinal] = Vector2.len(plan.vx, plan.vy) / Arena.MAX_SPEED
        throwNow()
    }

    private fun throwNow() {
        val side = match.turn
        aimPower[side.ordinal] = aimPower[side.ordinal].coerceAtLeast(MIN_POWER)
        aimVelocity(side, vel)
        match.throwItem(selectedItem[side.ordinal], vel.x, vel.y)
    }

    /** One step of fine aiming: a degree of angle or a percent of power. */
    private fun nudge(id: String) {
        val i = match.turn.ordinal
        when (id) {
            "angle-" -> aimAngle[i] = (aimAngle[i] - 1f).coerceIn(0f, 85f)
            "angle+" -> aimAngle[i] = (aimAngle[i] + 1f).coerceIn(0f, 85f)
            "power-" -> aimPower[i] = ((aimPower[i] * 100f).roundToInt() - 1).coerceIn(5, 100) / 100f
            "power+" -> aimPower[i] = ((aimPower[i] * 100f).roundToInt() + 1).coerceIn(5, 100) / 100f
        }
    }

    override fun onButtonDown(id: String) {
        if (id in NUDGE_IDS && humanAiming) {
            nudge(id)
            holdTime = 0f
            repeatTimer = 0f
        }
    }

    /** Holding an angle or power button keeps stepping it. */
    private fun updateHeldButton(dt: Float) {
        val id = pressedId ?: return
        if (id !in NUDGE_IDS || !humanAiming) return
        holdTime += dt
        if (holdTime < 0.4f) return
        repeatTimer -= dt
        if (repeatTimer <= 0f) {
            nudge(id)
            repeatTimer = 0.05f
        }
    }

    override fun onButton(id: String) {
        when {
            id in NUDGE_IDS -> Unit
            id == "throw" -> if (humanAiming) throwNow()
            id == "pause" -> paused = true
            id == "resume" -> paused = false
            id == "restart" || id == "rematch" -> restart()
            id == "levels" -> game.switchTo(ChallengeListScreen(game))
            id == "double_ad" -> if (canOfferAd()) watchAdForDoubleTreats()
            id == "next" -> {
                val next = challenge?.let { Challenges.byNumber(it.number + 1) }
                if (next != null) game.switchTo(GameScreen(game, MatchSetup.forChallenge(next)))
            }
            id == "menu" -> game.switchTo(MenuScreen(game))
            id == "pets" -> game.switchTo(PetSelectScreen(game, twoPlayers = humans.size == 2))
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
            match.phase == Phase.OVER -> game.switchTo(if (challenge != null) ChallengeListScreen(game) else MenuScreen(game))
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
        computer = newComputer()
        treatsEarned = 0
        adPlaying = false
        adUsed = false
        starsEarned = 0
        unlockable = null
        particles.clear()
        popups.clear()
        buildQueue.clear()
        selectedItem.fill(ItemType.MUD)
        dragging = false
        computerAiming = false
        ghostX = null
        paused = false
        computerPlan = null
        computerTimer = 0f
        computerBuilding = false
        overTime = 0f
        slowMotion = 0f
        hop.fill(0f)
    }

    override fun dispose() {
        match.dispose()
    }

    // ---- Drawing ----

    private fun draw() {
        clear()
        worldCam.position.set(
            Arena.WIDTH / 2f + if (shake > 0f) MathUtils.random(-1f, 1f) * shake * 0.3f else 0f,
            Arena.VIEW_BOTTOM + worldView.worldHeight / 2f + if (shake > 0f) MathUtils.random(-1f, 1f) * shake * 0.3f else 0f,
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
        drawShadows()
        if (match.phase == Phase.BUILD && isHuman(match.turn) && buildQueue.isEmpty()) drawBuildZone()
        for (p in match.pieces) Art.piece(s, p)
        for (p in match.pets.values) Art.pet(s, p, match.time, hopLift(p.side))
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

    private fun hopLift(side: Side): Float {
        val t = hop[side.ordinal]
        return if (t > 0f) MathUtils.sin((1f - t / HOP_TIME) * MathUtils.PI) * 0.3f else 0f
    }

    private fun drawShadows() {
        val s = shapes
        for (p in match.pieces) {
            match.bodyBounds(p.body, bounds)
            Art.shadow(s, bounds.x + bounds.width / 2f, bounds.width / 2f, bounds.y)
        }
        for (p in match.pets.values) {
            match.bodyBounds(p.body, bounds)
            Art.shadow(s, bounds.x + bounds.width / 2f, bounds.width / 2f, bounds.y + hopLift(p.side))
        }
        for (p in match.projectiles) {
            val pos = p.body.position
            Art.shadow(s, pos.x, p.radius, pos.y - p.radius)
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
        if (!isHuman(side) && !computerAiming) return
        val origin = Arena.launchPoint(side, tmp2)
        val s = shapes
        val item = selectedItem[side.ordinal]
        val v = aimVelocity(side, vel)
        val power = v.len() / Arena.MAX_SPEED

        // The previous throw, faintly, so players can adjust from it.
        val last = match.lastVelocity(side)
        if (last != null && isHuman(side)) {
            val len = 0.8f + 2.2f * last.len() / Arena.MAX_SPEED
            s.color = tmpColor.set(Palette.INK).also { it.a = 0.3f }
            s.rectLine(origin.x, origin.y, origin.x + last.x / last.len() * len, origin.y + last.y / last.len() * len, 0.07f)
        }

        if (power > 0.02f) {
            obstacles.clear()
            for (p in match.pieces) if (p.side == side.other) obstacles.add(match.bodyBounds(p.body, Rectangle()))
            val steps = if (match.config.easyMode) 420 else 26
            Trajectory.simulate(origin.x, origin.y, v.x, v.y, item, side, match.wind, steps) { step, x, y, _, _ ->
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
            val len = 0.8f + 2.2f * power
            val nx = v.x / v.len()
            val ny = v.y / v.len()
            val c = when {
                power < 0.5f -> tmpColor.set(Palette.GOOD).lerp(Palette.GOLD, power * 2f)
                else -> tmpColor.set(Palette.GOLD).lerp(Palette.BAD, (power - 0.5f) * 2f)
            }
            val tipX = origin.x + nx * len
            val tipY = origin.y + ny * len
            s.color = Palette.INK
            s.rectLine(origin.x, origin.y, tipX, tipY, 0.2f)
            s.triangle(tipX + nx * 0.42f, tipY + ny * 0.42f, tipX - ny * 0.3f, tipY + nx * 0.3f, tipX + ny * 0.3f, tipY - nx * 0.3f)
            s.color = c
            s.rectLine(origin.x, origin.y, tipX, tipY, 0.12f)
            s.triangle(tipX + nx * 0.32f, tipY + ny * 0.32f, tipX - ny * 0.22f, tipY + nx * 0.22f, tipX + ny * 0.22f, tipY - nx * 0.22f)
        }
        s.identity()
        s.translate(origin.x, origin.y, 0f)
        Art.itemShape(s, item, item.radius)
        s.identity()
        if (isHuman(side) && !dragging && !game.settings.aimTipSeen) drawAimTip(origin)
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
        val y = Arena.GROUND_Y + Arena.LAUNCH_HEIGHT + 1.2f + MathUtils.sin(match.time * 5f) * 0.15f
        val s = shapes
        s.color = Palette.INK
        s.triangle(pos.x - 0.42f, y + 0.08f, pos.x + 0.42f, y + 0.08f, pos.x, y - 0.5f)
        s.color = Palette.GOLD
        s.triangle(pos.x - 0.3f, y, pos.x + 0.3f, y, pos.x, y - 0.38f)
    }

    // ---- HUD ----

    private val aimPanelX: Float get() = uiW - 20f - AIM_PANEL_W

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
            val c = challenge
            if (overTime > 0.9f && canOfferAd()) {
                list += Button("double_ad", uiW / 2f + 10f, treatsLineY() - 32f, 360f, 64f, "Watch an ad: double it", ButtonStyle.TEAL, font = game.assets.body)
            }
            if (overTime > 0.9f && c != null) {
                val cx = uiW / 2f
                val next = Challenges.byNumber(c.number + 1)
                val canGoOn = next != null && game.settings.isChallengeOpen(next.number)
                list += Button("rematch", cx - 390f, uiH / 2f - 290f, 250f, 100f, "Retry", if (canGoOn) ButtonStyle.LIGHT else ButtonStyle.PRIMARY)
                list += Button("levels", cx - 120f, uiH / 2f - 290f, 240f, 100f, "Levels", ButtonStyle.TEAL)
                if (canGoOn) list += Button("next", cx + 140f, uiH / 2f - 290f, 250f, 100f, "Next", ButtonStyle.PRIMARY)
            } else if (overTime > 0.9f) {
                val cx = uiW / 2f
                list += Button("rematch", cx - 390f, uiH / 2f - 290f, 290f, 100f, "Rematch", ButtonStyle.PRIMARY)
                list += Button("pets", cx - 80f, uiH / 2f - 290f, 230f, 100f, "Pets", ButtonStyle.TEAL)
                list += Button("menu", cx + 170f, uiH / 2f - 290f, 220f, 100f, "Menu")
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
        if (match.phase == Phase.AIM && isHuman(side)) {
            addItemTray(list, side)
            addAimControls(list)
        }
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
            Ui.star(s, cx, cy, 26f)
        })
        x += controls[2] + gap
        list += Button("ready", x, y, controls[3], bh, "Ready!", ButtonStyle.PRIMARY)
    }

    private fun addItemTray(list: MutableList<Button>, side: Side) {
        val items = match.items(side)
        val bw = 170f
        val bh = 124f
        val gap = 12f
        var x = 130f
        for (item in items) {
            val n = match.count(side, item)
            list += Button("item:${item.name}", x, 24f, bw, bh, item.displayName,
                enabled = n != 0, selected = item == selectedItem[side.ordinal],
                badge = if (n < 0) "∞" else "x$n",
                icon = { s, cx, cy, size -> icon(cx, cy - 2f, size * 0.42f / item.radius) { Art.itemShape(s, item, item.radius) } })
            x += bw + gap
        }
    }

    /** Angle and power steppers plus a Throw button, for precise, repeatable throws. */
    private fun addAimControls(list: MutableList<Button>) {
        val x = aimPanelX
        val row1 = AIM_PANEL_Y + 82f
        val row2 = AIM_PANEL_Y + 14f
        for ((row, name) in listOf(row1 to "angle", row2 to "power")) {
            list += Button("$name-", x + 12f, row, 62f, 56f, "-")
            list += Button("$name+", x + 210f, row, 62f, 56f, "+")
        }
        list += Button("throw", x + 288f, AIM_PANEL_Y + 14f, AIM_PANEL_W - 300f, 124f, "Throw!", ButtonStyle.PRIMARY)
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
        if (match.phase != Phase.BUILD && tip() != null) {
            s.color = Palette.SHADE
            Ui.roundRect(s, uiW / 2f - 400f, TIP_Y - 25f, 800f, 50f, 25f)
        }
        if (humanAiming) Ui.panel(s, aimPanelX, AIM_PANEL_Y, AIM_PANEL_W, AIM_PANEL_H)
    }

    /** The one-line tip under the wind panel: a fresh hint, the first-throw tip, or what the item does. */
    private fun tip(): String? {
        if (hintTime > 0f) return hintText
        if (!humanAiming || dragging) return null
        return if (!game.settings.aimTipSeen) "Drag back anywhere and let go, or set angle and power and tap Throw" else selectedItem[match.turn.ordinal].hint
    }

    private val TIP_Y: Float get() = uiH - 20f - 90f - 64f

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
        s.color = Art.petColor(pet.kind)
        s.circle(iconX, y + h / 2f, 44f, 30)
        icon(iconX - side.dir * 4f, y + h / 2f - 26f, 72f) {
            s.scale(side.dir, 1f, 1f)
            Art.petOutlined(s, pet.kind, match.time, pet.hitTimer > 0f, false, pet.mess)
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
        val c = challenge
        if (c != null && side == Side.RIGHT) {
            // Goal marker on the target's mess bar
            val gx = barX + barW * c.goal / Arena.MESS_TO_LOSE
            s.color = Palette.INK
            s.rect(gx - 3f, y + 10f, 6f, 46f)
            s.triangle(gx - 10f, y + 60f, gx + 10f, y + 60f, gx, y + 48f)
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
                val c = challenge
                val role = if (c != null && side == Side.RIGHT) "Target · goal ${c.goal.roundToInt()}%" else "${playerLabel(side)} · ${match.throwsLeft(side)} left"
                Ui.textAt(batch, a.small, role, textX + w - 146f, y + 78f, Palette.INK, rightAligned = true, alpha = 0.75f)
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
            val msg = when {
                hintTime > 0f -> hintText
                left > 0 -> "Tap a piece, then touch your yard to drop it  ·  $left left"
                else -> "Fort done! Tap Ready when you like it"
            }
            Ui.text(batch, a.small, msg, uiW / 2f, uiH - 22f - 118f - 46f, Palette.WHITE)
        }

        val side = match.turn
        val tip = tip()
        if (match.phase != Phase.BUILD && tip != null) {
            Ui.text(batch, a.small, tip, uiW / 2f, TIP_Y, Palette.WHITE, if (hintTime > 0f) min(1f, hintTime * 3f) else 1f, 770f)
        }
        if (humanAiming) {
            val x = aimPanelX + 142f
            Ui.text(batch, a.small, "ANGLE", x, AIM_PANEL_Y + 128f, Palette.INK, 0.6f)
            Ui.text(batch, a.h2, "${aimAngle[side.ordinal].roundToInt()}°", x, AIM_PANEL_Y + 102f, Palette.INK)
            Ui.text(batch, a.small, "POWER", x, AIM_PANEL_Y + 60f, Palette.INK, 0.6f)
            Ui.text(batch, a.h2, "${(aimPower[side.ordinal] * 100).roundToInt()}%", x, AIM_PANEL_Y + 34f, Palette.INK)
        }
        if (match.phase == Phase.FLIGHT && isHuman(side) && match.activeSplitter != null) {
            Ui.text(batch, a.label, "Tap to split!", uiW / 2f, uiH * 0.5f, Palette.WHITE)
        }
        if (match.phase == Phase.AIM && ((dragging && dragMoved) || computerAiming)) {
            val launch = Arena.launchPoint(side, tmp2)
            val p = worldToUi(launch.x, launch.y + 1.9f, tmp)
            val v = aimVelocity(side, vel)
            val angle = (atan2(v.y, abs(v.x)) * MathUtils.radiansToDegrees).roundToInt()
            Ui.text(batch, a.label, "$angle°  ${(v.len() / Arena.MAX_SPEED * 100).roundToInt()}%", p.x, p.y, Palette.WHITE)
        }

        for (p in popups) {
            val pos = worldToUi(p.x, p.y, tmp)
            val alpha = min(1f, p.life * 2.5f)
            Ui.text(batch, if (p.big) a.h1 else a.label, p.text, pos.x, pos.y, p.color, alpha)
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
        Ui.panel(s, uiW / 2f - 430f, uiH / 2f - 310f, 860f, 640f)
        if (challenge != null) {
            Ui.starRow(s, uiW / 2f, uiH / 2f + 18f, 40f, starsEarned)
            if (match.winner == Side.LEFT) {
                icon(uiW / 2f, uiH / 2f + 128f + abs(MathUtils.sin(overTime * 4f)) * 10f, 90f) {
                    Art.petOutlined(s, match.pet(Side.LEFT).kind, overTime, false, false, 0f)
                }
            }
            return
        }
        val winner = match.winner ?: return
        val pet = match.pet(winner)
        val bounce = abs(MathUtils.sin(overTime * 4f)) * 14f
        icon(uiW / 2f, uiH / 2f + 90f + bounce, 110f) {
            s.scale(winner.dir, 1f, 1f)
            Art.petOutlined(s, pet.kind, overTime, false, false, 0f)
        }
    }

    private fun drawOverlayText() {
        val a = game.assets
        val cx = uiW / 2f
        if (paused) {
            Ui.text(batch, a.h1, "Paused", cx, uiH / 2f + 180f, Palette.WHITE)
            return
        }
        val c = challenge
        if (c != null) {
            val won = match.winner == Side.LEFT
            Ui.text(batch, a.title, if (won) "Level complete!" else "Out of throws", cx, uiH / 2f + 250f, Palette.WHITE, maxWidth = 800f)
            val st = match.stats(Side.LEFT)
            val line = if (won) {
                "${petName(Side.RIGHT)} reached ${match.pet(Side.RIGHT).mess.roundToInt()}% in ${st.throws} throws"
            } else {
                "${petName(Side.RIGHT)} reached ${match.pet(Side.RIGHT).mess.roundToInt()}% of the ${c.goal.roundToInt()}% goal"
            }
            Ui.text(batch, a.h2, line, cx, uiH / 2f - 56f, Palette.INK, maxWidth = 780f)
            val next = when {
                !won -> "Tip: ${c.brief}"
                starsEarned < 3 -> "3 stars: finish in ${c.threeStars} throws or fewer"
                else -> "Perfect!"
            }
            Ui.text(batch, a.body, next, cx, uiH / 2f - 100f, Palette.INK, 0.8f, 780f)
            drawTreatsLine("+$treatsEarned treats")
            return
        }
        val winner = match.winner
        val title = when {
            winner == null -> "It's a draw!"
            humans.size != 1 -> "${petName(winner)} wins!"
            isHuman(winner) -> "You win!"
            else -> "${setup.level.displayName} wins!"
        }
        Ui.text(batch, a.title, title, cx, uiH / 2f + 250f, Palette.WHITE)
        val loser = winner?.other
        val why = when {
            loser != null && match.pet(loser).mess >= Arena.MESS_TO_LOSE -> "${petName(loser)} is 100% messy!"
            winner == null -> "Exactly as messy as each other"
            else -> "Least messy after ${match.config.throwsPerPlayer} throws each"
        }
        Ui.text(batch, a.h2, why, cx, uiH / 2f - 20f, Palette.INK)
        val l = match.pet(Side.LEFT)
        val r = match.pet(Side.RIGHT)
        Ui.text(batch, a.body, "${l.kind.displayName} mess ${l.mess.roundToInt()}%     ${r.kind.displayName} mess ${r.mess.roundToInt()}%", cx, uiH / 2f - 64f, Palette.INK, 0.8f)
        var y = uiH / 2f - 104f
        for (side in humans) {
            val st = match.stats(side)
            val who = if (humans.size == 2) "${playerLabel(side)}:" else "You:"
            val trickText = when (st.tricks) {
                0 -> ""
                1 -> "  ·  1 trick shot"
                else -> "  ·  ${st.tricks} trick shots"
            }
            Ui.text(batch, a.body, "$who ${st.hits} of ${st.throws} throws hit$trickText", cx, y, Palette.INK, 0.8f)
            y -= 38f
        }
        val next = unlockable
        drawTreatsLine(if (next != null && !canOfferAd()) "+$treatsEarned treats! You can unlock ${next.displayName} now" else "+$treatsEarned treats")
    }

    private fun treatsLineY(): Float = uiH / 2f - if (challenge != null) 144f else 176f

    /** The treats a game earned, with room beside it for the optional ad button. */
    private fun drawTreatsLine(line: String) {
        if (treatsEarned <= 0) return
        val a = game.assets
        val offered = canOfferAd()
        val x = if (offered) uiW / 2f - 170f else uiW / 2f
        Ui.text(batch, a.h2, line, x, treatsLineY(), Palette.ACCENT_DARK, maxWidth = if (offered) 340f else 780f)
        if (adPlaying) Ui.text(batch, a.body, "Loading the ad...", uiW / 2f + 190f, treatsLineY(), Palette.INK, 0.7f)
    }

    /** Rewarded ads are optional, never shown with Plus, and capped per day. */
    private fun canOfferAd(): Boolean =
        treatsEarned > 0 && !adUsed && !adPlaying && game.platform.rewardedAdReady && game.settings.canWatchAd(today())

    private fun today(): String = java.time.LocalDate.now().toString()

    private fun watchAdForDoubleTreats() {
        adPlaying = true
        val day = today()
        game.platform.showRewardedAd(
            onReward = {
                game.settings.addTreats(treatsEarned)
                game.settings.recordAdWatched(day)
                treatsEarned *= 2
                unlockable = PetKind.entries.firstOrNull { !game.settings.isUnlocked(it) && game.settings.treats >= it.price }
                game.sfx("win", 0.7f)
            },
            onDone = {
                adPlaying = false
                adUsed = true
            },
        )
    }

    private companion object {
        const val PULL_FOR_FULL_POWER = 330f
        const val MIN_POWER = 0.12f
        const val DRAG_SLOP = 14f
        const val HOP_TIME = 0.35f
        const val AIM_PANEL_W = 430f
        const val AIM_PANEL_H = 150f
        const val AIM_PANEL_Y = 18f
        val NUDGE_IDS = setOf("angle-", "angle+", "power-", "power+")
        val HIT_WORDS = listOf("Splat!", "Gotcha!", "Bullseye!", "Yuck!", "Messy!")
    }
}
