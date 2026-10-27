package com.pawtillery.game

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

class Placement(val type: PieceType, val rotated: Boolean, val x: Float)

class ShotPlan(val item: ItemType, val vx: Float, val vy: Float, val splitAtApex: Boolean)

/**
 * The computer opponent. It searches throws with [Trajectory] (so it plays by the same physics as
 * the player), then adds aiming error: more in Easy mode, and more when it is far ahead, so games stay close.
 */
class ComputerPlayer(private val rng: Random, private val easy: Boolean) {

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

    fun planShot(match: Match, side: Side): ShotPlan {
        val targetPos = match.pet(side.other).body.position
        val target = Vector2(targetPos.x, targetPos.y)
        val blockers = match.pieces.filter { it.side == side.other }.map { match.bodyBounds(it.body, Rectangle()) }
        val available = match.items(side).filter { match.count(side, it) != 0 }
        val signature = match.pet(side).kind.signature
        angleError = if (easy) 3f else 1.2f
        speedError = if (easy) 0.5f else 0.22f
        // Ease off when far ahead so a losing player can catch up.
        if (match.pet(side).mess + 30f < match.pet(side.other).mess) {
            angleError *= 1.6f
            speedError *= 1.6f
        }

        val mud = search(match, side, ItemType.MUD, target, blockers)
        var item = ItemType.MUD
        var best = mud
        if (mud.stop == Stop.PET) {
            val roll = rng.nextFloat()
            if (signature in available && roll < 0.45f) {
                val sig = search(match, side, signature, target, blockers)
                if (sig.stop == Stop.PET) { item = signature; best = sig }
            } else if (ItemType.WATER in available && roll < 0.65f) {
                item = ItemType.WATER
                best = search(match, side, ItemType.WATER, target, blockers)
            }
        } else {
            val bowling = if (ItemType.BOWLING in available) search(match, side, ItemType.BOWLING, target, blockers) else null
            val bone = if (signature == ItemType.BONE && signature in available) search(match, side, signature, target, blockers) else null
            when {
                bone != null && bone.stop == Stop.PET -> { item = signature; best = bone }
                bowling != null && rng.nextFloat() < 0.75f -> { item = ItemType.BOWLING; best = bowling }
                ItemType.WATER in available -> { item = ItemType.WATER; best = search(match, side, ItemType.WATER, target, blockers) }
            }
        }

        val angle = (best.angle + gauss() * angleError).coerceIn(5f, 88f)
        val speed = (best.speed + gauss() * speedError).coerceIn(5f, Arena.MAX_SPEED)
        return ShotPlan(
            item,
            side.dir * MathUtils.cosDeg(angle) * speed,
            MathUtils.sinDeg(angle) * speed,
            splitAtApex = item == ItemType.HAIRBALL && rng.nextFloat() < 0.75f,
        )
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
            Stop.PIECE -> if (item == ItemType.BOWLING) 0.5f * dist else 3f + dist
            else -> 3f + dist
        }
        if (item == ItemType.WATER && r.stop != Stop.PET) score = maxOf(0f, dist - 1.2f)
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
