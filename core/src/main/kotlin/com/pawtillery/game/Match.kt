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
    val seed: Long = System.nanoTime(),
)

sealed interface MatchEvent {
    data class BuildStarted(val side: Side) : MatchEvent
    data class PiecePlaced(val side: Side, val type: PieceType) : MatchEvent
    data class TurnStarted(val side: Side) : MatchEvent
    data class Thrown(val side: Side, val item: ItemType) : MatchEvent
    data class Split(val x: Float, val y: Float) : MatchEvent
    data class Splat(val x: Float, val y: Float, val item: ItemType) : MatchEvent
    data class Burst(val x: Float, val y: Float) : MatchEvent
    /** [item] is null when a falling fort piece did the damage. */
    data class PetHit(val side: Side, val amount: Float, val x: Float, val y: Float, val item: ItemType?) : MatchEvent
    data class Thud(val strength: Float, val tire: Boolean) : MatchEvent
    data class GameOver(val winner: Side?) : MatchEvent
}

/**
 * One game: the Box2D world plus the rules (build phase, turns, wind, inventories, mess, winning).
 * Runs on a fixed time step so throws, the aim guide and the computer player all agree.
 */
class Match(val config: MatchConfig) : Disposable {
    private class Hit(val projectile: Projectile, val other: Any?, val x: Float, val y: Float, val speed: Float)
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

    /** The un-split hairball in the air, if any. */
    var activeHairball: Projectile? = null
        private set

    private val throwsDone = IntArray(2)
    private val inventory = Array(2) { IntArray(ItemType.entries.size) }
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
        for (side in Side.entries) {
            for (item in items(side)) inventory[side.ordinal][item.ordinal] = item.startCount
        }
        events.add(MatchEvent.BuildStarted(Side.LEFT))
    }

    fun pet(side: Side): Pet = pets.getValue(side)

    /** What [side] can throw, in tray order. */
    fun items(side: Side): List<ItemType> =
        listOf(ItemType.MUD, ItemType.WATER, ItemType.BOWLING, pet(side).kind.signature)

    /** Items left; -1 means unlimited. */
    fun count(side: Side, item: ItemType): Int = inventory[side.ordinal][item.ordinal]

    fun throwsLeft(side: Side): Int = Arena.THROWS_PER_PLAYER - throwsDone[side.ordinal]

    fun piecesLeft(side: Side): Int = Arena.PIECES_PER_PLAYER - pieces.count { it.side == side }

    fun lastVelocity(side: Side): Vector2? = lastVelocity[side.ordinal]

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
        val piece = Piece(type, turn, rotated, createPieceBody(type, turn, rotated, pos.x, pos.y))
        piece.body.userData = piece
        pieces.add(piece)
        events.add(MatchEvent.PiecePlaced(turn, type))
        return true
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
        val left = count(side, item)
        if (left > 0) inventory[side.ordinal][item.ordinal] = left - 1
        val v = tmp.set(vx, vy).limit(Arena.MAX_SPEED)
        lastVelocity[side.ordinal] = Vector2(v)
        val origin = Arena.launchPoint(side)
        val p = spawnProjectile(item, side, origin.x, origin.y, v.x, v.y, fragment = false)
        if (item == ItemType.HAIRBALL) activeHairball = p
        phase = Phase.FLIGHT
        events.add(MatchEvent.Thrown(side, item))
        return true
    }

    /** Splits the hairball in the air into three smaller ones. */
    fun splitHairball(): Boolean {
        val p = activeHairball ?: return false
        activeHairball = null
        if (p.dead || phase != Phase.FLIGHT) return false
        val x = p.body.position.x
        val y = p.body.position.y
        val vx = p.body.linearVelocity.x
        val vy = p.body.linearVelocity.y
        world.destroyBody(p.body)
        projectiles.remove(p)
        for (deg in floatArrayOf(-11f, 0f, 11f)) {
            val c = MathUtils.cosDeg(deg)
            val s = MathUtils.sinDeg(deg)
            spawnProjectile(ItemType.HAIRBALL, p.side, x, y, vx * c - vy * s, vx * s + vy * c, fragment = true)
        }
        events.add(MatchEvent.Split(x, y))
        return true
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
        resolveHits()
        resolveCrushes()
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
        wind = if (config.easyMode) 0f else rng.nextInt(-7, 8) * 0.5f
        crushThisTurn.fill(0f)
        events.add(MatchEvent.TurnStarted(side))
    }

    private fun endTurn() {
        throwsDone[turn.ordinal]++
        val l = pet(Side.LEFT).mess
        val r = pet(Side.RIGHT).mess
        val someoneLost = l >= Arena.MESS_TO_LOSE || r >= Arena.MESS_TO_LOSE
        val outOfThrows = throwsDone.all { it >= Arena.THROWS_PER_PLAYER }
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
            if (p.item == ItemType.WATER) {
                burst(p, hit.x, hit.y)
                continue
            }
            when (val other = hit.other) {
                is Pet -> {
                    if (!p.scoredPet) {
                        p.scoredPet = true
                        addMess(other, p.mess * speedFactor(hit.speed), hit.x, hit.y, p.item)
                    }
                    if (p.item.splatsOnContact) splat(p, other.body, other.decals, PET_DECALS, hit.x, hit.y)
                }
                is Piece -> if (p.item.splatsOnContact) splat(p, other.body, other.decals, PIECE_DECALS, hit.x, hit.y)
                else -> if (p.item.splatsOnContact) splat(p, null, groundDecals, GROUND_DECALS, hit.x, hit.y)
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

    private fun speedFactor(speed: Float) = (speed / 12f).coerceIn(0.6f, 1.25f)

    private fun addMess(pet: Pet, amount: Float, x: Float, y: Float, item: ItemType?) {
        if (amount < 0.5f) return
        val before = pet.mess
        pet.mess = min(Arena.MESS_TO_LOSE, pet.mess + amount)
        pet.hitTimer = 0.9f
        events.add(MatchEvent.PetHit(pet.side, pet.mess - before, x, y, item))
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

    private fun burst(p: Projectile, x: Float, y: Float) {
        p.dead = true
        events.add(MatchEvent.Burst(x, y))
        for (pet in pets.values) {
            val pos = pet.body.position
            val d = Vector2.dst(pos.x, pos.y, x, y)
            val f = 1f - (d - 0.8f) / (SPLASH_RADIUS - 0.8f)
            if (f > 0f) {
                addMess(pet, ItemType.WATER.mess * min(f, 1f), pos.x, pos.y + Arena.PET_H / 2f, ItemType.WATER)
                pet.decals.add(Decal((x - pos.x) * 0.4f, 0.1f + rng.nextFloat() * 0.3f, 0.2f, ItemType.WATER, rng.nextInt()))
                if (pet.decals.size > PET_DECALS) pet.decals.removeAt(0)
            }
        }
        for (body in solidBodies()) {
            val pos = body.position
            val dx = pos.x - x
            val dy = pos.y - y
            val d = Vector2.len(dx, dy)
            if (d < SPLASH_RADIUS && d > 0.01f) {
                val dv = 1.4f * (1f - d / SPLASH_RADIUS) * body.mass / d
                body.applyLinearImpulse(dx * dv, dy * dv + 0.2f * body.mass, pos.x, pos.y, true)
            }
        }
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
            if (p === activeHairball) activeHairball = null
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

    private fun spawnProjectile(item: ItemType, side: Side, x: Float, y: Float, vx: Float, vy: Float, fragment: Boolean): Projectile {
        val body = world.createBody(BodyDef().apply {
            type = BodyDef.BodyType.DynamicBody
            position.set(x, y)
            linearVelocity.set(vx, vy)
            angularVelocity = -side.dir * (if (item == ItemType.BONE) 12f else 4f)
            gravityScale = item.gravityScale
            bullet = true
        })
        val p = Projectile(item, side, body, fragment)
        body.userData = p
        val shape = CircleShape().apply { radius = p.radius }
        addFixture(body, shape, item.density, 0.5f, item.restitution, Bits.shot(side), Bits.shotMask(side))
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
            hits.add(Hit(proj, other.userData, x, y, Vector2.len(pv.x - ov.x, pv.y - ov.y)))
        }

        override fun postSolve(contact: Contact, impulse: ContactImpulse) {
            val ua = contact.fixtureA.body.userData
            val ub = contact.fixtureB.body.userData
            if (ua !is Piece && ub !is Piece) return
            var strength = 0f
            for (i in 0 until impulse.count) strength = max(strength, impulse.normalImpulses[i])
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
        const val SPLASH_RADIUS = 3.2f
        const val NEAR_MISS_RADIUS = 1.5f
        const val CRUSH_IMPULSE = 3f
        const val MAX_CRUSH_PER_TURN = 20f
        const val THUD_IMPULSE = 2.5f
        private const val PET_DECALS = 40
        private const val PIECE_DECALS = 12
        private const val GROUND_DECALS = 50
    }
}
