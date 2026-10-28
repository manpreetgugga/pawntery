package com.pawtillery.game

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.Body
import com.badlogic.gdx.physics.box2d.BodyDef
import com.badlogic.gdx.physics.box2d.Box2D
import com.badlogic.gdx.physics.box2d.CircleShape
import com.badlogic.gdx.physics.box2d.Contact
import com.badlogic.gdx.physics.box2d.ContactImpulse
import com.badlogic.gdx.physics.box2d.ContactListener
import com.badlogic.gdx.physics.box2d.FixtureDef
import com.badlogic.gdx.physics.box2d.Manifold
import com.badlogic.gdx.physics.box2d.PolygonShape
import com.badlogic.gdx.physics.box2d.Shape
import com.badlogic.gdx.physics.box2d.World
import com.badlogic.gdx.utils.Disposable
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

enum class Phase { BUILD, AIM, FLIGHT, SETTLE, OVER }

class MatchConfig(
    /** Easy: no wind. Pro: wind changes every turn. */
    val easyMode: Boolean,
    val leftKind: PetKind = PetKind.DOG,
    val rightKind: PetKind = PetKind.CAT,
    val throwsPerPlayer: Int = FULL_MATCH,
    val seed: Long = System.nanoTime(),
    /** When set, this is a Trick Shot level: no building, and only the left player throws. */
    val challenge: Challenge? = null,
) {
    companion object {
        const val FULL_MATCH = 10
        const val QUICK_MATCH = 5
    }
}

sealed interface MatchEvent {
    data class BuildStarted(val side: Side) : MatchEvent
    data class PiecePlaced(val side: Side, val type: PieceType) : MatchEvent
    data class TurnStarted(val side: Side) : MatchEvent
    data class Thrown(val side: Side, val item: ItemType) : MatchEvent
    data class Split(val x: Float, val y: Float) : MatchEvent
    data class Splat(val x: Float, val y: Float, val item: ItemType) : MatchEvent
    /** A water balloon, watermelon, firework or paint bomb going off, reaching [radius] meters. */
    data class Burst(val x: Float, val y: Float, val item: ItemType, val radius: Float) : MatchEvent
    /** [item] is null when a falling fort piece did the damage; [trick] names a skill shot. */
    data class PetHit(val side: Side, val amount: Float, val x: Float, val y: Float, val item: ItemType?, val trick: String? = null) : MatchEvent
    data class Thud(val strength: Float, val tire: Boolean) : MatchEvent
    /** A fort piece took one knock too many and fell apart; [vx], [vy] is how it was moving. */
    data class PieceBroken(val side: Side, val type: PieceType, val x: Float, val y: Float, val angle: Float, val vx: Float, val vy: Float) : MatchEvent
    data class PowerUsed(val side: Side, val power: PowerUp) : MatchEvent
    /** [side]'s umbrella caught a hit meant for its pet. */
    data class Blocked(val side: Side, val x: Float, val y: Float) : MatchEvent
    data class GameOver(val winner: Side?) : MatchEvent
}

/** How one player's throws went; [smashed] counts the other side's fort pieces broken on this player's turns. */
data class ThrowStats(val throws: Int, val hits: Int, val tricks: Int, val smashed: Int = 0)

/**
 * One game: the Box2D world plus the rules (build phase, turns, wind, inventories, mess, winning).
 * Runs on a fixed time step so throws, the aim guide and the computer player all agree.
 */
class Match(val config: MatchConfig) : Disposable {
    /** A projectile touching something; [vx] is its horizontal velocity at that moment, before the bounce. */
    private class Hit(val projectile: Projectile, val other: Any?, val x: Float, val y: Float, val speed: Float, val vx: Float)
    private class Crush(val pet: Pet, val impulse: Float)

    val world: World
    val rng = Random(config.seed)
    val pets: Map<Side, Pet>
    val pieces = ArrayList<Piece>()
    val projectiles = ArrayList<Projectile>()
    val groundDecals = ArrayList<Decal>()

    var phase = Phase.BUILD
        private set
    var turn = Side.LEFT
        private set
    /** Horizontal wind acceleration in m/s²; positive blows to the right. */
    var wind = 0f
        private set
    var winner: Side? = null
        private set
    /** Seconds simulated so far, for animation. */
    var time = 0f
        private set

    /** Thrown items that still react to a tap: hairballs and seed showers split, fireworks pop. */
    private val tappables = ArrayList<Projectile>()

    /** The first item in the air that a tap would split or pop, if any. */
    val tapTarget: Projectile? get() = tappables.firstOrNull { !it.dead }

    private val throwsDone = IntArray(2)
    private val throwsThatHit = IntArray(2)
    private val trickShots = IntArray(2)
    private val trickLog = Array(2) { ArrayList<String>() }
    private val smashed = IntArray(2)
    private var throwHit = false
    /** Split pieces or Triple items from this throw that hit the pet so far, for Double!/Triple! */
    private var comboHits = 0
    /** A throw earns one Sky pop! at most, even with three fireworks. */
    private var skyPopped = false
    private val inventory = Array(2) { IntArray(ItemType.entries.size) }
    private val powers = Array(2) { IntArray(PowerUp.entries.size) }
    private val armed = Array(2) { BooleanArray(PowerUp.entries.size) }
    private val umbrella = BooleanArray(2)
    /** Set once an umbrella has caught something this turn; it folds away when the turn ends. */
    private val umbrellaHit = BooleanArray(2)
    private val lastVelocity = arrayOfNulls<Vector2>(2)
    private val events = ArrayList<MatchEvent>()
    private val hits = ArrayList<Hit>()
    private val crushes = ArrayList<Crush>()
    private val crushCooldown = FloatArray(2)
    private val crushThisTurn = FloatArray(2)
    private var thudStrength = 0f
    private var thudTire = false
    private var thudCooldown = 0f
    private var accumulator = 0f
    private var settleTime = 0f
    private var calmTime = 0f
    private val tmp = Vector2()
    private val bounds = Rectangle()

    init {
        Box2D.init()
        world = World(Vector2(0f, Arena.GRAVITY), true)
        world.setContactListener(ContactRecorder())
        createScenery()
        pets = Side.entries.associateWith { side ->
            Pet(side, if (side == Side.LEFT) config.leftKind else config.rightKind, createPetBody(side)).also { it.body.userData = it }
        }
        val challenge = config.challenge
        if (challenge == null) {
            for (side in Side.entries) {
                for (item in items(side)) inventory[side.ordinal][item.ordinal] = item.startCount
                powers[side.ordinal].fill(1)
            }
            events.add(MatchEvent.BuildStarted(Side.LEFT))
        } else {
            for ((item, n) in challenge.items) inventory[Side.LEFT.ordinal][item.ordinal] = n
            for (p in challenge.fortPlacements()) {
                val pos = dropPosition(Side.RIGHT, p.type, p.rotated, p.x) ?: continue
                addPiece(p.type, Side.RIGHT, p.rotated, pos.x, pos.y)
                // Let each piece land before the next is dropped on it.
                repeat(40) { world.step(Arena.STEP, 8, 3) }
            }
            repeat(120) { world.step(Arena.STEP, 8, 3) }
            hits.clear()
            crushes.clear()
            thudStrength = 0f
            startTurn(Side.LEFT)
        }
    }

    fun pet(side: Side): Pet = pets.getValue(side)

    /** What [side] can throw, in tray order. */
    fun items(side: Side): List<ItemType> {
        val challenge = config.challenge
        if (challenge != null) return if (side == Side.LEFT) challenge.items.map { it.first } else emptyList()
        return listOf(ItemType.MUD, pet(side).kind.signature, ItemType.WATER, ItemType.BOWLING, ItemType.MELON, ItemType.FIREWORK, ItemType.PAINT_BOMB)
    }

    fun throwsAllowed(): Int = config.challenge?.maxThrows ?: config.throwsPerPlayer

    /** Items left; -1 means unlimited. */
    fun count(side: Side, item: ItemType): Int = inventory[side.ordinal][item.ordinal]

    fun throwsLeft(side: Side): Int = throwsAllowed() - throwsDone[side.ordinal]

    fun piecesLeft(side: Side): Int = Arena.PIECES_PER_PLAYER - pieces.count { it.side == side }

    fun lastVelocity(side: Side): Vector2? = lastVelocity[side.ordinal]

    /** Stars earned in a finished Trick Shot level. */
    fun challengeStars(): Int {
        val c = config.challenge ?: return 0
        return if (phase == Phase.OVER) c.stars(winner == Side.LEFT, throwsDone[Side.LEFT.ordinal]) else 0
    }

    /** The names of the trick shots [side] landed, in order. */
    fun tricks(side: Side): List<String> = trickLog[side.ordinal]

    fun stats(side: Side) = ThrowStats(throwsDone[side.ordinal], throwsThatHit[side.ordinal], trickShots[side.ordinal], smashed[side.ordinal])

    // ---- Power-ups ----

    fun powersLeft(side: Side, power: PowerUp): Int = powers[side.ordinal][power.ordinal]

    /** Mega and Triple wait, armed, for the throw. */
    fun isArmed(side: Side, power: PowerUp): Boolean = armed[side.ordinal][power.ordinal]

    fun hasUmbrella(side: Side): Boolean = umbrella[side.ordinal]

    /** Whether the player whose turn it is can use [power] right now. */
    fun canUsePower(power: PowerUp): Boolean {
        if (phase != Phase.AIM || powersLeft(turn, power) <= 0) return false
        return when (power) {
            PowerUp.CALM -> wind != 0f
            PowerUp.UMBRELLA -> !umbrella[turn.ordinal]
            else -> true
        }
    }

    /**
     * Uses [power] for the player whose turn it is. Calm and Umbrella act at once; Mega and Triple
     * are armed for this turn's throw (using them again disarms them) and used up when it's thrown.
     */
    fun usePower(power: PowerUp): Boolean {
        if (phase != Phase.AIM) return false
        val i = turn.ordinal
        if (armed[i][power.ordinal]) {
            armed[i][power.ordinal] = false
            return true
        }
        if (!canUsePower(power)) return false
        when (power) {
            PowerUp.MEGA, PowerUp.TRIPLE -> armed[i][power.ordinal] = true
            PowerUp.CALM -> {
                powers[i][power.ordinal]--
                wind = 0f
                events.add(MatchEvent.PowerUsed(turn, power))
            }
            PowerUp.UMBRELLA -> {
                powers[i][power.ordinal]--
                umbrella[i] = true
                events.add(MatchEvent.PowerUsed(turn, power))
            }
        }
        return true
    }

    fun drainEvents(out: MutableList<MatchEvent>) {
        out.addAll(events)
        events.clear()
    }

    // ---- Build phase ----

    /** Where a piece dropped at [x] would appear: just above whatever is already in that column. */
    fun dropPosition(side: Side, type: PieceType, rotated: Boolean, x: Float, out: Vector2 = Vector2()): Vector2? {
        val hw = (if (rotated) type.h else type.w) / 2f
        val hh = (if (rotated) type.w else type.h) / 2f
        val cx = x.coerceIn(Arena.buildMinX(side) + hw, Arena.buildMaxX(side) - hw)
        var top = Arena.GROUND_Y
        for (body in solidBodies()) {
            bodyBounds(body, bounds)
            if (bounds.x < cx + hw && bounds.x + bounds.width > cx - hw) top = max(top, bounds.y + bounds.height)
        }
        val cy = top + hh + 0.12f
        if (cy + hh > Arena.MAX_BUILD_HEIGHT) return null
        return out.set(cx, cy)
    }

    fun placePiece(type: PieceType, rotated: Boolean, x: Float): Boolean {
        if (phase != Phase.BUILD || piecesLeft(turn) <= 0) return false
        val pos = dropPosition(turn, type, rotated, x) ?: return false
        addPiece(type, turn, rotated, pos.x, pos.y)
        events.add(MatchEvent.PiecePlaced(turn, type))
        return true
    }

    private fun addPiece(type: PieceType, side: Side, rotated: Boolean, x: Float, y: Float) {
        val piece = Piece(type, side, rotated, createPieceBody(type, side, rotated, x, y), rng.nextInt())
        piece.body.userData = piece
        pieces.add(piece)
    }

    fun undoPiece(): Boolean {
        if (phase != Phase.BUILD) return false
        val last = pieces.lastOrNull { it.side == turn } ?: return false
        world.destroyBody(last.body)
        pieces.remove(last)
        return true
    }

    fun finishBuild() {
        if (phase != Phase.BUILD) return
        if (turn == Side.LEFT) {
            turn = Side.RIGHT
            events.add(MatchEvent.BuildStarted(Side.RIGHT))
        } else {
            startTurn(Side.LEFT)
        }
    }

    // ---- Throwing ----

    fun canThrow(item: ItemType): Boolean = phase == Phase.AIM && item in items(turn) && count(turn, item) != 0

    fun throwItem(item: ItemType, vx: Float, vy: Float): Boolean {
        if (!canThrow(item)) return false
        val side = turn
        val i = side.ordinal
        val left = count(side, item)
        if (left > 0) inventory[i][item.ordinal] = left - 1
        val v = tmp.set(vx, vy).limit(Arena.MAX_SPEED)
        lastVelocity[i] = Vector2(v)
        val mega = armed[i][PowerUp.MEGA.ordinal]
        val triple = armed[i][PowerUp.TRIPLE.ordinal]
        for (power in listOf(PowerUp.MEGA, PowerUp.TRIPLE)) {
            if (!armed[i][power.ordinal]) continue
            armed[i][power.ordinal] = false
            powers[i][power.ordinal]--
            events.add(MatchEvent.PowerUsed(side, power))
        }
        val origin = Arena.launchPoint(side)
        tappables.clear()
        for (deg in if (triple) TRIPLE_SPREAD else NO_SPREAD) {
            val c = MathUtils.cosDeg(deg)
            val s = MathUtils.sinDeg(deg)
            val p = spawnProjectile(item, side, origin.x, origin.y, v.x * c - v.y * s, v.x * s + v.y * c, fragment = false,
                scale = if (mega) Projectile.MEGA_SCALE else 1f, multi = triple)
            if (item.tappable) tappables.add(p)
        }
        throwHit = false
        comboHits = 0
        skyPopped = false
        phase = Phase.FLIGHT
        events.add(MatchEvent.Thrown(side, item))
        return true
    }

    /** A tap during flight: hairballs and seed showers split into smaller pieces, fireworks pop. */
    fun tap(): Boolean {
        if (phase != Phase.FLIGHT) return false
        val live = tappables.filter { !it.dead }
        tappables.clear()
        for (p in live) {
            if (p.item.popsOnTap) burst(p, p.body.position.x, p.body.position.y, popped = true) else split(p)
        }
        return live.isNotEmpty()
    }

    /** Splits [p] in the air into smaller pieces that fan out. */
    private fun split(p: Projectile) {
        val x = p.body.position.x
        val y = p.body.position.y
        val vx = p.body.linearVelocity.x
        val vy = p.body.linearVelocity.y
        world.destroyBody(p.body)
        projectiles.remove(p)
        val item = p.item
        for (i in 0 until item.splitCount) {
            val deg = (i - (item.splitCount - 1) / 2f) * item.splitSpread
            val c = MathUtils.cosDeg(deg)
            val s = MathUtils.sinDeg(deg)
            spawnProjectile(item, p.side, x, y, vx * c - vy * s, vx * s + vy * c, fragment = true, scale = p.scale, multi = p.multi)
        }
        events.add(MatchEvent.Split(x, y))
    }

    // ---- Simulation ----

    fun update(delta: Float) {
        accumulator += min(delta, 0.1f)
        while (accumulator >= Arena.STEP) {
            accumulator -= Arena.STEP
            step()
        }
    }

    fun step() {
        time += Arena.STEP
        for (p in projectiles) {
            val m = p.body.mass
            p.body.applyForceToCenter((wind - p.side.dir * p.item.returnAccel) * m, 0f, true)
        }
        world.step(Arena.STEP, 8, 3)
        for (piece in pieces) {
            if (piece.softTimer > 0) piece.softTimer-- else piece.damage += piece.pendingKnock
            piece.pendingKnock = 0f
        }
        resolveHits()
        resolveCrushes()
        breakPieces()
        if (thudCooldown > 0f) thudCooldown -= Arena.STEP
        if (thudStrength > THUD_IMPULSE && thudCooldown <= 0f) {
            events.add(MatchEvent.Thud(thudStrength, thudTire))
            thudCooldown = 0.08f
        }
        thudStrength = 0f
        ageProjectiles()
        for (pet in pets.values) if (pet.hitTimer > 0f) pet.hitTimer -= Arena.STEP
        for (i in crushCooldown.indices) if (crushCooldown[i] > 0f) crushCooldown[i] -= Arena.STEP
        advancePhase()
    }

    private fun advancePhase() {
        when (phase) {
            Phase.FLIGHT -> if (projectiles.isEmpty()) {
                phase = Phase.SETTLE
                settleTime = 0f
                calmTime = 0f
            }
            Phase.SETTLE -> {
                settleTime += Arena.STEP
                calmTime = if (isCalm()) calmTime + Arena.STEP else 0f
                if (calmTime > 0.35f || settleTime > 3.5f) endTurn()
            }
            else -> Unit
        }
    }

    private fun startTurn(side: Side) {
        turn = side
        phase = Phase.AIM
        wind = config.challenge?.wind ?: if (config.easyMode) 0f else rng.nextInt(-7, 8) * 0.5f
        crushThisTurn.fill(0f)
        events.add(MatchEvent.TurnStarted(side))
    }

    private fun endTurn() {
        throwsDone[turn.ordinal]++
        if (throwHit) throwsThatHit[turn.ordinal]++
        for (i in umbrella.indices) {
            if (umbrellaHit[i]) umbrella[i] = false
            umbrellaHit[i] = false
        }
        val challenge = config.challenge
        if (challenge != null) {
            val reached = pet(Side.RIGHT).mess >= challenge.goal
            if (reached || throwsLeft(Side.LEFT) <= 0) {
                phase = Phase.OVER
                winner = if (reached) Side.LEFT else Side.RIGHT
                events.add(MatchEvent.GameOver(winner))
            } else {
                startTurn(Side.LEFT)
            }
            return
        }
        val l = pet(Side.LEFT).mess
        val r = pet(Side.RIGHT).mess
        val someoneLost = l >= Arena.MESS_TO_LOSE || r >= Arena.MESS_TO_LOSE
        val outOfThrows = throwsDone.all { it >= config.throwsPerPlayer }
        if (someoneLost || outOfThrows) {
            phase = Phase.OVER
            winner = when {
                l < r -> Side.LEFT
                r < l -> Side.RIGHT
                else -> null
            }
            events.add(MatchEvent.GameOver(winner))
        } else {
            startTurn(turn.other)
        }
    }

    private fun isCalm(): Boolean {
        for (body in solidBodies()) {
            if (body.linearVelocity.len2() > 0.06f || kotlin.math.abs(body.angularVelocity) > 0.5f) return false
        }
        return true
    }

    private fun resolveHits() {
        for (hit in hits) {
            val p = hit.projectile
            if (p.dead) continue
            if (p.item.burst != null) {
                burst(p, hit.x, hit.y)
                continue
            }
            when (val other = hit.other) {
                is Pet -> {
                    if (!p.scoredPet) {
                        p.scoredPet = true
                        addMess(other, p.mess * speedFactor(hit.speed), hit.x, hit.y, p.item, directHit = true, trick = trickFor(p, hit.vx))
                    }
                    if (p.item.splatsOnContact) {
                        if (shielded(other)) {
                            // It splats on the umbrella instead.
                            p.dead = true
                            events.add(MatchEvent.Splat(hit.x, hit.y, p.item))
                        } else {
                            splat(p, other.body, other.decals, PET_DECALS, hit.x, hit.y)
                        }
                    }
                }
                is Piece -> if (p.item.splatsOnContact) splat(p, other.body, other.decals, PIECE_DECALS, hit.x, hit.y) else p.bounces++
                else -> if (p.item.splatsOnContact) splat(p, null, groundDecals, GROUND_DECALS, hit.x, hit.y) else p.bounces++
            }
        }
        hits.clear()
    }

    private fun resolveCrushes() {
        if (phase == Phase.FLIGHT || phase == Phase.SETTLE) {
            for (c in crushes) {
                val i = c.pet.side.ordinal
                val room = MAX_CRUSH_PER_TURN - crushThisTurn[i]
                if (crushCooldown[i] > 0f || room <= 0f) continue
                val amount = min(min(c.impulse * 1.2f, 8f), room)
                crushThisTurn[i] += amount
                crushCooldown[i] = 0.4f
                val pos = c.pet.body.position
                addMess(c.pet, amount, pos.x, pos.y + Arena.PET_H / 2f, null)
            }
        }
        crushes.clear()
    }

    /** Names a skill shot: a boomerang hitting on its way back, a roller that banked in, or several split pieces landing. */
    private fun trickFor(p: Projectile, vxAtContact: Float): String? {
        if (p.fragment || p.multi) {
            comboHits++
            return when (comboHits) {
                1 -> null
                2 -> "Double!"
                3 -> "Triple!"
                else -> "Combo x$comboHits!"
            }
        }
        return when {
            p.item.returnAccel > 0f && vxAtContact * p.side.dir < 0f -> "Boomerang!"
            !p.item.splatsOnContact && p.bounces > 0 -> "Bank shot!"
            else -> null
        }
    }

    private fun speedFactor(speed: Float) = (speed / 12f).coerceIn(0.6f, 1.25f)

    /** Whether [pet] sits under its umbrella while the other player throws. */
    private fun shielded(pet: Pet): Boolean = umbrella[pet.side.ordinal] && pet.side != turn

    /**
     * Adds mess to [pet] unless its umbrella catches it; returns whether any was added. [directHit]
     * marks mess that counts as a hit in the stats: a direct hit or a burst, not near-miss spatter or crushes.
     */
    private fun addMess(pet: Pet, amount: Float, x: Float, y: Float, item: ItemType?, directHit: Boolean = false, trick: String? = null): Boolean {
        if (amount < 0.5f) return false
        if (shielded(pet)) {
            val i = pet.side.ordinal
            if (!umbrellaHit[i]) events.add(MatchEvent.Blocked(pet.side, x, y))
            umbrellaHit[i] = true
            return false
        }
        val before = pet.mess
        pet.mess = min(Arena.MESS_TO_LOSE, pet.mess + amount)
        pet.hitTimer = 0.9f
        val gained = pet.mess - before
        if (directHit && pet.side != turn && gained >= HIT_THRESHOLD) throwHit = true
        if (trick != null && pet.side != turn) {
            trickShots[turn.ordinal]++
            trickLog[turn.ordinal].add(trick)
        }
        events.add(MatchEvent.PetHit(pet.side, gained, x, y, item, trick))
        return true
    }

    private fun splat(p: Projectile, body: Body?, decals: MutableList<Decal>, cap: Int, x: Float, y: Float) {
        p.dead = true
        var lx = x
        var ly = y
        if (body != null) {
            val local = body.getLocalPoint(tmp.set(x, y))
            // Pull pet splats a little inward so they sit on the fur rather than the outline.
            val pull = if (body.userData is Pet) 0.8f else 1f
            lx = local.x * pull
            ly = local.y * pull
        }
        decals.add(Decal(lx, ly, p.radius * (0.75f + rng.nextFloat() * 0.35f), p.item, rng.nextInt()))
        if (decals.size > cap) decals.removeAt(0)
        events.add(MatchEvent.Splat(x, y, p.item))
        if (body?.userData !is Pet) splashNearby(p, x, y)
    }

    /** A splat that just misses still spatters a pet standing close by. */
    private fun splashNearby(p: Projectile, x: Float, y: Float) {
        for (pet in pets.values) {
            val pos = pet.body.position
            val d = Vector2.dst(pos.x, pos.y, x, y)
            if (d < NEAR_MISS_RADIUS) {
                addMess(pet, p.mess * 0.45f * (1f - d / NEAR_MISS_RADIUS), pos.x, pos.y + Arena.PET_H / 2f, p.item)
            }
        }
    }

    /**
     * A water balloon, watermelon, firework or paint bomb going off at (x, y): pets in reach get messy
     * (fully within 0.8 m, less farther out), and fort pieces are pushed away and, by the harder ones, cracked.
     * A firework [popped] in the air by a tap that still messes up the other pet is a trick shot.
     */
    private fun burst(p: Projectile, x: Float, y: Float, popped: Boolean = false) {
        p.dead = true
        val b = p.item.burst ?: return
        val radius = b.radius * if (p.scale > 1f) MEGA_BURST else 1f
        events.add(MatchEvent.Burst(x, y, p.item, radius))
        var trick = if (popped && !skyPopped) "Sky pop!" else null
        for (pet in pets.values) {
            val pos = pet.body.position
            val d = Vector2.dst(pos.x, pos.y, x, y)
            val f = 1f - (d - 0.8f) / (radius - 0.8f)
            if (f <= 0f) continue
            val forOther = pet.side != p.side
            if (addMess(pet, p.mess * min(f, 1f), pos.x, pos.y + Arena.PET_H / 2f, p.item, directHit = true, trick = if (forOther) trick else null)) {
                if (forOther && trick != null) {
                    trick = null
                    skyPopped = true
                }
                pet.decals.add(Decal((x - pos.x).coerceIn(-1.5f, 1.5f) * 0.3f, -0.1f + rng.nextFloat() * 0.5f, 0.2f * min(f, 1f) + 0.06f, p.item, rng.nextInt()))
                if (pet.decals.size > PET_DECALS) pet.decals.removeAt(0)
            }
            // Pets only wobble; a blast never knocks them flying or over.
            push(pet.body, x, y, radius, min(b.push, 1.4f), 0.2f, tumble = false)
        }
        for (piece in pieces) {
            val pos = piece.body.position
            val reach = 1f - (Vector2.dst(pos.x, pos.y, x, y) - min(piece.halfW, piece.halfH)).coerceAtLeast(0f) / radius
            if (reach <= 0f) continue
            // Blasts that crack things also throw them up in the air; a water balloon only nudges.
            push(piece.body, x, y, radius, b.push, if (b.damage > 0f) 0.2f + b.push * 0.3f * reach else 0.2f, tumble = b.damage > 0f)
            piece.damage += b.damage * reach
            if (p.item != ItemType.WATER && piece.decals.size < PIECE_DECALS && reach > 0.4f) {
                val local = piece.body.getLocalPoint(tmp.set(x, y)).limit(min(piece.halfW, piece.halfH))
                piece.decals.add(Decal(local.x, local.y, 0.18f + 0.12f * reach, p.item, rng.nextInt()))
            }
        }
        if (p.item != ItemType.WATER && y < Arena.GROUND_Y + radius * 0.5f) {
            groundDecals.add(Decal(x, Arena.GROUND_Y - 0.05f, radius * 0.22f, p.item, rng.nextInt()))
            if (groundDecals.size > GROUND_DECALS) groundDecals.removeAt(0)
        }
    }

    /**
     * Pushes [body] away from (x, y): up to [speed] m/s at the center of the burst, plus [lift] m/s
     * upward. With [tumble], the push lands a little off center so the body spins as it flies.
     */
    private fun push(body: Body, x: Float, y: Float, radius: Float, speed: Float, lift: Float, tumble: Boolean) {
        val pos = body.position
        val dx = pos.x - x
        val dy = pos.y - y
        val d = Vector2.len(dx, dy)
        if (d >= radius || d < 0.01f) return
        val dv = speed * (1f - d / radius) * body.mass / d
        val offset = if (tumble) 0.15f * (if (dx > 0f) 1f else -1f) else 0f
        body.applyLinearImpulse(dx * dv, dy * dv + lift * body.mass, pos.x, pos.y + offset, true)
    }

    /** Pieces that took one knock too many fall apart. */
    private fun breakPieces() {
        val it = pieces.iterator()
        while (it.hasNext()) {
            val piece = it.next()
            if (piece.damage < piece.type.toughness) continue
            it.remove()
            val b = piece.body
            events.add(MatchEvent.PieceBroken(piece.side, piece.type, b.position.x, b.position.y, b.angle, b.linearVelocity.x, b.linearVelocity.y))
            if (piece.side != turn) smashed[turn.ordinal]++
            world.destroyBody(b)
        }
    }

    /**
     * A knock on [piece] from [other]: hard throws, falls and crashes crack things; soft throws never
     * do, not even by pressing the piece into the ground.
     */
    private fun knock(piece: Piece, other: Any?, impulse: Float) {
        if (other is Projectile && !other.item.smashes) {
            piece.softTimer = SOFT_STEPS
            return
        }
        val jolt = impulse / piece.body.mass
        if (jolt > KNOCK_FLOOR) piece.pendingKnock += jolt - KNOCK_FLOOR
    }

    private fun ageProjectiles() {
        for (p in projectiles) {
            p.age += Arena.STEP
            val pos = p.body.position
            if (pos.x < -3f || pos.x > Arena.WIDTH + 3f || pos.y < -2f || p.age > 8f) p.dead = true
            if (!p.item.splatsOnContact && p.age > 0.6f && p.body.linearVelocity.len2() < 0.2f) p.dead = true
            if (!p.item.splatsOnContact && p.age > 6f) p.dead = true
        }
        val it = projectiles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            if (!p.dead) continue
            world.destroyBody(p.body)
            tappables.remove(p)
            it.remove()
        }
    }

    // ---- Bodies ----

    fun solidBodies(): Sequence<Body> = pieces.asSequence().map { it.body } + pets.values.asSequence().map { it.body }

    fun bodyBounds(body: Body, out: Rectangle): Rectangle {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (f in body.fixtureList) {
            when (val s = f.shape) {
                is PolygonShape -> for (i in 0 until s.vertexCount) {
                    s.getVertex(i, tmp)
                    val w = body.getWorldPoint(tmp)
                    minX = min(minX, w.x); maxX = max(maxX, w.x)
                    minY = min(minY, w.y); maxY = max(maxY, w.y)
                }
                is CircleShape -> {
                    val c = body.getWorldPoint(s.position)
                    minX = min(minX, c.x - s.radius); maxX = max(maxX, c.x + s.radius)
                    minY = min(minY, c.y - s.radius); maxY = max(maxY, c.y + s.radius)
                }
                else -> Unit
            }
        }
        return out.set(minX, minY, maxX - minX, maxY - minY)
    }

    private fun createScenery() {
        val groundDepth = 5f
        staticBox(Arena.WIDTH / 2f, Arena.GROUND_Y - groundDepth / 2f, 40f, groundDepth / 2f, SceneryKind.GROUND)
        val hedgeHalfH = (Arena.HEDGE_TOP - Arena.GROUND_Y) / 2f
        staticBox(Arena.HEDGE_W / 2f, Arena.GROUND_Y + hedgeHalfH, Arena.HEDGE_W / 2f, hedgeHalfH, SceneryKind.HEDGE)
        staticBox(Arena.WIDTH - Arena.HEDGE_W / 2f, Arena.GROUND_Y + hedgeHalfH, Arena.HEDGE_W / 2f, hedgeHalfH, SceneryKind.HEDGE)
        val fenceHalfH = (Arena.FENCE_TOP - Arena.GROUND_Y) / 2f
        val fence = staticBox(Arena.FENCE_X, Arena.GROUND_Y + fenceHalfH, Arena.FENCE_HALF_W, fenceHalfH, SceneryKind.FENCE)
        val cap = PolygonShape()
        val hw = Arena.FENCE_HALF_W
        cap.set(floatArrayOf(-hw, fenceHalfH, hw, fenceHalfH, 0f, fenceHalfH + Arena.FENCE_PEAK - Arena.FENCE_TOP))
        addFixture(fence, cap, 0f, 0.8f, 0.1f, Bits.SCENERY, 0xFFFF)
    }

    private fun staticBox(cx: Float, cy: Float, hx: Float, hy: Float, kind: SceneryKind): Body {
        val body = world.createBody(BodyDef().apply { position.set(cx, cy) })
        body.userData = kind
        val shape = PolygonShape()
        shape.setAsBox(hx, hy)
        addFixture(body, shape, 0f, 0.8f, 0.1f, Bits.SCENERY, 0xFFFF)
        return body
    }

    private fun createPetBody(side: Side): Body {
        val body = world.createBody(BodyDef().apply {
            type = BodyDef.BodyType.DynamicBody
            position.set(Arena.petX(side), Arena.GROUND_Y + Arena.PET_H / 2f + 0.01f)
            angularDamping = 2f
            linearDamping = 0.1f
        })
        val shape = PolygonShape()
        shape.setAsBox(Arena.PET_W / 2f, Arena.PET_H / 2f)
        addFixture(body, shape, 3f, 0.9f, 0f, Bits.solid(side), Bits.solidMask(side))
        return body
    }

    private fun createPieceBody(type: PieceType, side: Side, rotated: Boolean, x: Float, y: Float): Body {
        val body = world.createBody(BodyDef().apply {
            this.type = BodyDef.BodyType.DynamicBody
            position.set(x, y)
            angle = if (rotated) MathUtils.HALF_PI else 0f
            angularDamping = 0.3f
        })
        val shape: Shape = when (type.shape) {
            PieceShape.BOX -> PolygonShape().apply { setAsBox(type.w / 2f, type.h / 2f) }
            PieceShape.TRAPEZOID -> PolygonShape().apply {
                val bw = type.w * 0.36f
                val tw = type.w / 2f
                val hh = type.h / 2f
                set(floatArrayOf(-bw, -hh, bw, -hh, tw, hh, -tw, hh))
            }
            PieceShape.CIRCLE -> CircleShape().apply { radius = type.w / 2f }
        }
        addFixture(body, shape, type.density, type.friction, type.restitution, Bits.solid(side), Bits.solidMask(side))
        return body
    }

    private fun spawnProjectile(item: ItemType, side: Side, x: Float, y: Float, vx: Float, vy: Float, fragment: Boolean, scale: Float = 1f, multi: Boolean = false): Projectile {
        val body = world.createBody(BodyDef().apply {
            type = BodyDef.BodyType.DynamicBody
            position.set(x, y)
            linearVelocity.set(vx, vy)
            angularVelocity = when (item) {
                ItemType.BONE -> -side.dir * 12f
                ItemType.CARROT, ItemType.FIREWORK -> 0f
                else -> -side.dir * 4f
            }
            fixedRotation = item == ItemType.CARROT || item == ItemType.FIREWORK
            gravityScale = item.gravityScale
            bullet = true
        })
        val p = Projectile(item, side, body, fragment, scale, multi)
        body.userData = p
        val shape = CircleShape().apply { radius = p.radius }
        addFixture(body, shape, item.density, if (item == ItemType.SHELL) 0.15f else 0.5f, item.restitution, Bits.shot(side), Bits.shotMask(side))
        projectiles.add(p)
        return p
    }

    private fun addFixture(body: Body, shape: Shape, density: Float, friction: Float, restitution: Float, category: Int, mask: Int) {
        val def = FixtureDef().apply {
            this.shape = shape
            this.density = density
            this.friction = friction
            this.restitution = restitution
            filter.categoryBits = category.toShort()
            filter.maskBits = mask.toShort()
        }
        body.createFixture(def)
        shape.dispose()
    }

    private inner class ContactRecorder : ContactListener {
        override fun beginContact(contact: Contact) {
            val a = contact.fixtureA.body
            val b = contact.fixtureB.body
            val pa = a.userData as? Projectile
            val pb = b.userData as? Projectile
            val proj: Projectile
            val other: Body
            when {
                pa != null -> { proj = pa; other = b }
                pb != null -> { proj = pb; other = a }
                else -> return
            }
            val wm = contact.worldManifold
            val x: Float
            val y: Float
            if (wm.numberOfContactPoints > 0) {
                x = wm.points[0].x
                y = wm.points[0].y
            } else {
                x = proj.body.position.x
                y = proj.body.position.y
            }
            val pv = proj.body.linearVelocity
            val ov = other.linearVelocity
            hits.add(Hit(proj, other.userData, x, y, Vector2.len(pv.x - ov.x, pv.y - ov.y), pv.x))
        }

        override fun postSolve(contact: Contact, impulse: ContactImpulse) {
            val ua = contact.fixtureA.body.userData
            val ub = contact.fixtureB.body.userData
            if (ua !is Piece && ub !is Piece) return
            var strength = 0f
            var total = 0f
            for (i in 0 until impulse.count) {
                strength = max(strength, impulse.normalImpulses[i])
                total += impulse.normalImpulses[i]
            }
            // Only knocks while things fly and settle count: building and setting up a level never break anything.
            if (phase == Phase.FLIGHT || phase == Phase.SETTLE) {
                if (ua is Piece) knock(ua, ub, total)
                if (ub is Piece) knock(ub, ua, total)
            }
            val pet = ua as? Pet ?: ub as? Pet
            if (pet != null && strength > CRUSH_IMPULSE) crushes.add(Crush(pet, strength))
            if (strength > thudStrength) {
                thudStrength = strength
                thudTire = (ua as? Piece)?.type == PieceType.TIRE || (ub as? Piece)?.type == PieceType.TIRE
            }
        }

        override fun endContact(contact: Contact) = Unit

        override fun preSolve(contact: Contact, oldManifold: Manifold) = Unit
    }

    override fun dispose() {
        world.dispose()
    }

    companion object {
        const val NEAR_MISS_RADIUS = 1.5f
        /** Knocks gentler than this (a change in speed, in m/s) do no damage, so stacking and toppling are safe. */
        const val KNOCK_FLOOR = 3f
        private const val SOFT_STEPS = 3
        /** A Mega burst reaches this much farther. */
        const val MEGA_BURST = 1.3f
        private val NO_SPREAD = floatArrayOf(0f)
        private val TRIPLE_SPREAD = floatArrayOf(-5f, 0f, 5f)
        const val CRUSH_IMPULSE = 3f
        const val MAX_CRUSH_PER_TURN = 20f
        const val THUD_IMPULSE = 2.5f
        /** Mess a throw must add to count as a hit in the stats (near-miss spatter doesn't). */
        const val HIT_THRESHOLD = 5f
        private const val PET_DECALS = 40
        private const val PIECE_DECALS = 12
        private const val GROUND_DECALS = 50
    }
}
