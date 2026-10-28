package com.pawtillery.game

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

class Placement(val type: PieceType, val rotated: Boolean, val x: Float)

/** How well the computer aims (aiming error in degrees and m/s), and whether it eases off when far ahead. */
enum class ComputerLevel(val displayName: String, val angleError: Float, val speedError: Float, val easesOff: Boolean, val treatFactor: Float) {
    ROOKIE("Rookie", 3.2f, 0.55f, true, 0.75f),
    NORMAL("Normal", 1.5f, 0.27f, true, 1f),
    CHAMPION("Champion", 0.55f, 0.1f, false, 1.5f),
}

/** A planned throw; [likelyHit] means the aim (before error) lands on the other pet. */
class ShotPlan(val item: ItemType, val vx: Float, val vy: Float, val splitAtApex: Boolean, val likelyHit: Boolean = false)

/**
 * The computer opponent. It searches throws with [Trajectory] (so it plays by the same physics as
 * the player), then adds aiming error for its [level]; below Champion it also eases off when far ahead.
 */
class ComputerPlayer(
    private val rng: Random,
    private val baseAngleError: Float,
    private val baseSpeedError: Float,
    private val easesOff: Boolean,
) {
    constructor(rng: Random, level: ComputerLevel) : this(rng, level.angleError, level.speedError, level.easesOff)


    private enum class Stop { PET, PIECE, FENCE, GROUND, OUT }

    private class Result(val stop: Stop, val x: Float, val y: Float)

    private var angleError = 0f
    private var speedError = 0f

    private class Best(var score: Float = Float.MAX_VALUE, var angle: Float = 45f, var speed: Float = 12f, var stop: Stop = Stop.OUT)

    /** Fort plans; offsets are meters from the pet toward the fence (negative = behind the pet). */
    private val templates: List<List<Triple<PieceType, Boolean, Float>>> = listOf(
        listOf(
            Triple(PieceType.CRATE, false, 2.2f), Triple(PieceType.CRATE, false, 2.2f), Triple(PieceType.BOX, false, 2.2f),
            Triple(PieceType.POT, false, 3.6f), Triple(PieceType.GNOME, false, 3.6f), Triple(PieceType.TIRE, false, -2.0f),
        ),
        listOf(
            Triple(PieceType.GNOME, false, 1.3f), Triple(PieceType.GNOME, false, -1.3f), Triple(PieceType.PLANK, false, 0f),
            Triple(PieceType.CRATE, false, 2.9f), Triple(PieceType.BOX, false, 2.9f), Triple(PieceType.POT, false, -2.7f),
        ),
        listOf(
            Triple(PieceType.CRATE, false, 2.0f), Triple(PieceType.BOX, false, 2.0f), Triple(PieceType.CRATE, false, 3.4f),
            Triple(PieceType.BOX, false, 3.4f), Triple(PieceType.TIRE, false, 4.7f), Triple(PieceType.GNOME, false, -1.6f),
        ),
        listOf(
            Triple(PieceType.CRATE, false, 1.9f), Triple(PieceType.CRATE, false, 1.9f), Triple(PieceType.CRATE, false, 1.9f),
            Triple(PieceType.BOX, false, 3.3f), Triple(PieceType.BOX, false, 3.3f), Triple(PieceType.POT, false, -1.8f),
        ),
    )

    fun planBuild(side: Side): List<Placement> {
        val px = Arena.petX(side)
        return templates[rng.nextInt(templates.size)].map { (type, rotated, offset) ->
            Placement(type, rotated, px + side.dir * (offset + (rng.nextFloat() - 0.5f) * 0.12f))
        }
    }

    /** Plans this turn like [planShot], and uses power-ups along the way now and then. */
    fun takeTurn(match: Match, side: Side): ShotPlan {
        val left = match.throwsLeft(side)
        // Calm first, so the throw is planned without wind.
        if (match.canUsePower(PowerUp.CALM) && abs(match.wind) >= 2f && rng.nextFloat() < 0.6f) match.usePower(PowerUp.CALM)
        if (match.canUsePower(PowerUp.UMBRELLA) && (match.pet(side).mess >= 30f || left <= 3) && rng.nextFloat() < 0.5f) match.usePower(PowerUp.UMBRELLA)
        val plan = planShot(match, side)
        // Mega and Triple are saved for good chances, and spent before the match runs out.
        val now = left <= 1 || (plan.likelyHit && (left <= 3 || rng.nextFloat() < 0.25f))
        if (now && match.canUsePower(PowerUp.MEGA)) {
            match.usePower(PowerUp.MEGA)
        } else if (now && match.canUsePower(PowerUp.TRIPLE)) {
            match.usePower(PowerUp.TRIPLE)
        }
        return plan
    }

    fun planShot(match: Match, side: Side): ShotPlan {
        val targetPos = match.pet(side.other).body.position
        val target = Vector2(targetPos.x, targetPos.y)
        val blockers = match.pieces.filter { it.side == side.other }.map { match.bodyBounds(it.body, Rectangle()) }
        val available = match.items(side).filter { match.count(side, it) != 0 }
        val signature = match.pet(side).kind.signature
        angleError = baseAngleError
        speedError = baseSpeedError
        // Ease off when far ahead so a losing player can catch up.
        if (easesOff && match.pet(side).mess + 30f < match.pet(side.other).mess) {
            angleError *= 1.6f
            speedError *= 1.6f
        }

        val mud = search(match, side, ItemType.MUD, target, blockers)
        var item = ItemType.MUD
        var best = mud
        fun use(choice: ItemType) {
            item = choice
            best = search(match, side, choice, target, blockers)
        }
        if (mud.stop == Stop.PET) {
            val roll = rng.nextFloat()
            if (signature in available && roll < 0.45f) {
                val sig = search(match, side, signature, target, blockers)
                if (sig.stop == Stop.PET) { item = signature; best = sig }
            } else if (ItemType.WATER in available && roll < 0.65f) {
                use(ItemType.WATER)
            } else if (ItemType.MELON in available && roll < 0.75f) {
                use(ItemType.MELON)
            } else if (ItemType.FIREWORK in available && roll < 0.82f) {
                use(ItemType.FIREWORK)
            }
        } else {
            val bowling = if (ItemType.BOWLING in available) search(match, side, ItemType.BOWLING, target, blockers) else null
            // Some signatures find a way around walls (the bone from behind, the carrot through gaps).
            val sig = if (signature in available) search(match, side, signature, target, blockers) else null
            when {
                sig != null && sig.stop == Stop.PET -> { item = signature; best = sig }
                ItemType.PAINT_BOMB in available && rng.nextFloat() < 0.5f -> use(ItemType.PAINT_BOMB)
                ItemType.MELON in available && rng.nextFloat() < 0.45f -> use(ItemType.MELON)
                ItemType.FIREWORK in available && rng.nextFloat() < 0.4f -> use(ItemType.FIREWORK)
                bowling != null && rng.nextFloat() < 0.75f -> { item = ItemType.BOWLING; best = bowling }
                ItemType.WATER in available -> use(ItemType.WATER)
            }
        }
        // Big throws left near the end are used rather than wasted.
        if (match.throwsLeft(side) <= 2 && item == ItemType.MUD) {
            val spare = listOf(ItemType.PAINT_BOMB, ItemType.MELON, ItemType.FIREWORK).firstOrNull { it in available }
            if (spare != null) use(spare)
        }

        val angle = (best.angle + gauss() * angleError).coerceIn(5f, 88f)
        val speed = (best.speed + gauss() * speedError).coerceIn(5f, Arena.MAX_SPEED)
        return ShotPlan(
            item,
            side.dir * MathUtils.cosDeg(angle) * speed,
            MathUtils.sinDeg(angle) * speed,
            splitAtApex = item.splitCount > 0 && rng.nextFloat() < 0.75f,
            likelyHit = best.stop == Stop.PET,
        )
    }

    /** The best throw with [item], before aiming error: for demos and screenshots. */
    fun aimWith(match: Match, side: Side, item: ItemType): ShotPlan {
        angleError = baseAngleError
        speedError = baseSpeedError
        val targetPos = match.pet(side.other).body.position
        val blockers = match.pieces.filter { it.side == side.other }.map { match.bodyBounds(it.body, Rectangle()) }
        val best = search(match, side, item, Vector2(targetPos.x, targetPos.y), blockers)
        return ShotPlan(item, side.dir * MathUtils.cosDeg(best.angle) * best.speed, MathUtils.sinDeg(best.angle) * best.speed, false, best.stop == Stop.PET)
    }

    /** Coarse grid over angle and speed, then a finer pass around the best throw. */
    private fun search(match: Match, side: Side, item: ItemType, target: Vector2, blockers: List<Rectangle>): Best {
        val best = Best()
        var angle = 10f
        while (angle <= 84f) {
            var speed = 6f
            while (speed <= Arena.MAX_SPEED) {
                consider(match, side, item, target, blockers, angle, speed, best)
                speed += 0.5f
            }
            angle += 3f
        }
        val coarseAngle = best.angle
        val coarseSpeed = best.speed
        for (da in -6..6) {
            for (ds in -5..5) {
                val a = coarseAngle + da * 0.5f
                val s = coarseSpeed + ds * 0.1f
                if (a in 5f..88f && s in 5f..Arena.MAX_SPEED) consider(match, side, item, target, blockers, a, s, best)
            }
        }
        return best
    }

    private fun consider(match: Match, side: Side, item: ItemType, target: Vector2, blockers: List<Rectangle>, angle: Float, speed: Float, best: Best) {
        val vx = side.dir * MathUtils.cosDeg(angle) * speed
        val vy = MathUtils.sinDeg(angle) * speed
        val r = fly(match, side, item, vx, vy, target, blockers)
        val dist = Vector2.dst(r.x, r.y, target.x, target.y)
        var score = when (r.stop) {
            // Among hits, prefer throws that still hit with this player's usual aiming error.
            Stop.PET -> -robustness(match, side, item, target, blockers, angle, speed)
            // Heavy rollers (bowling ball, shell) are worth throwing at the fort itself.
            Stop.PIECE -> if (!item.splatsOnContact) 0.5f * dist else 3f + dist
            else -> 3f + dist
        }
        // Bursts don't need a direct hit: landing close is as good.
        val burst = item.burst
        if (burst != null && r.stop != Stop.PET) score = maxOf(0f, dist - if (item == ItemType.WATER) 1.2f else burst.radius * 0.4f)
        // Prefer flatter, gentler throws among equally good ones: they are less sensitive to error.
        score += speed * 0.002f
        if (score < best.score) {
            best.score = score
            best.angle = angle
            best.speed = speed
            best.stop = r.stop
        }
    }

    private fun robustness(match: Match, side: Side, item: ItemType, target: Vector2, blockers: List<Rectangle>, angle: Float, speed: Float): Float {
        var hits = 0
        for ((da, ds) in PERTURBATIONS) {
            val a = angle + da * angleError
            val s = speed + ds * speedError
            val r = fly(match, side, item, side.dir * MathUtils.cosDeg(a) * s, MathUtils.sinDeg(a) * s, target, blockers)
            if (r.stop == Stop.PET) hits++
        }
        return hits.toFloat()
    }

    private fun fly(match: Match, side: Side, item: ItemType, vx: Float, vy: Float, target: Vector2, blockers: List<Rectangle>): Result {
        val origin = Arena.launchPoint(side)
        val r = item.radius
        val petReach = Arena.PET_W * 0.55f + r
        var result = Result(Stop.OUT, origin.x, origin.y)
        Trajectory.simulate(origin.x, origin.y, vx, vy, item, side, match.wind, 480) { _, x, y, _, _ ->
            val stop = when {
                Vector2.dst2(x, y, target.x, target.y) < petReach * petReach -> Stop.PET
                y - r < Arena.GROUND_Y -> Stop.GROUND
                (x < Arena.HEDGE_W + r || x > Arena.WIDTH - Arena.HEDGE_W - r) && y < Arena.HEDGE_TOP -> Stop.GROUND
                x > Arena.FENCE_X - Arena.FENCE_HALF_W - r && x < Arena.FENCE_X + Arena.FENCE_HALF_W + r && y - r < Arena.FENCE_PEAK -> Stop.FENCE
                blockers.any { x > it.x - r && x < it.x + it.width + r && y > it.y - r && y < it.y + it.height + r } -> Stop.PIECE
                x < -3f || x > Arena.WIDTH + 3f -> Stop.OUT
                else -> null
            }
            if (stop != null) {
                result = Result(stop, x, y)
                false
            } else {
                true
            }
        }
        return result
    }

    private fun gauss(): Float {
        val u1 = 1.0 - rng.nextDouble()
        val u2 = rng.nextDouble()
        return (sqrt(-2.0 * ln(u1)) * cos(2.0 * Math.PI * u2)).toFloat()
    }

    private companion object {
        val PERTURBATIONS = listOf(-1f to 0f, 1f to 0f, 0f to -1f, 0f to 1f, -0.7f to 0.7f, 0.7f to -0.7f)
    }
}
