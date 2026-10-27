package com.pawtillery.game

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.Body

/** The two yards. LEFT throws to the right (dir = +1), RIGHT throws to the left. */
enum class Side(val dir: Float) {
    LEFT(1f), RIGHT(-1f);

    val other: Side get() = if (this == LEFT) RIGHT else LEFT
}

enum class PetKind(val displayName: String, val signature: ItemType) {
    DOG("Dog", ItemType.BONE),
    CAT("Cat", ItemType.HAIRBALL),
}

/**
 * Things a pet can throw. [mess] is what a direct hit adds to the other pet's mess meter at a
 * normal throwing speed; [startCount] of -1 means unlimited.
 */
enum class ItemType(
    val displayName: String,
    val radius: Float,
    val density: Float,
    val mess: Float,
    val startCount: Int,
    val splatsOnContact: Boolean,
    val gravityScale: Float = 1f,
    /** Boomerang pull back toward the thrower, in m/s². */
    val returnAccel: Float = 0f,
    val restitution: Float = 0.1f,
    val hint: String,
) {
    MUD("Mud Ball", 0.32f, 2f, 15f, -1, true, hint = "Always ready. Splats on contact."),
    WATER("Water Balloon", 0.4f, 1f, 24f, 2, true, hint = "Bursts and soaks everything nearby, even behind walls."),
    BOWLING("Bowling Ball", 0.42f, 8f, 8f, 2, false, restitution = 0.05f, hint = "Heavy. Smashes forts apart."),
    BONE("Boomerang Bone", 0.3f, 1.5f, 18f, 2, true, gravityScale = 0.45f, returnAccel = 3.5f, hint = "Glides out and curves back. Hit them from behind!"),
    HAIRBALL("Hairball", 0.35f, 1.5f, 14f, 2, true, hint = "Tap while it flies to split it into three."),
}

enum class PieceShape { BOX, TRAPEZOID, CIRCLE }

/** Fort pieces: [w] and [h] are the unrotated size in meters. */
enum class PieceType(
    val displayName: String,
    val shape: PieceShape,
    val w: Float,
    val h: Float,
    val density: Float,
    val friction: Float,
    val restitution: Float,
) {
    BOX("Box", PieceShape.BOX, 1.0f, 1.0f, 0.5f, 0.6f, 0.05f),
    CRATE("Crate", PieceShape.BOX, 1.2f, 1.2f, 1.2f, 0.7f, 0.05f),
    PLANK("Plank", PieceShape.BOX, 3.0f, 0.3f, 1.0f, 0.7f, 0.05f),
    POT("Flower Pot", PieceShape.TRAPEZOID, 0.9f, 0.9f, 2.0f, 0.6f, 0.05f),
    GNOME("Gnome", PieceShape.BOX, 0.7f, 1.3f, 2.5f, 0.6f, 0.05f),
    TIRE("Tire", PieceShape.CIRCLE, 1.2f, 1.2f, 0.9f, 0.9f, 0.45f),
}

/** Field layout in meters. The camera always shows at least WIDTH x HEIGHT. */
object Arena {
    const val WIDTH = 32f
    const val HEIGHT = 18f
    const val GROUND_Y = 2f
    const val FENCE_X = 16f
    const val FENCE_HALF_W = 0.3f
    const val FENCE_TOP = 7.2f
    const val FENCE_PEAK = 7.6f
    const val HEDGE_W = 0.7f
    const val HEDGE_TOP = 7f
    const val LAUNCH_HEIGHT = 2.7f
    const val MAX_SPEED = 19f
    const val PIECES_PER_PLAYER = 6
    const val THROWS_PER_PLAYER = 10
    const val MESS_TO_LOSE = 100f
    const val MAX_BUILD_HEIGHT = 12f
    const val STEP = 1f / 60f
    const val GRAVITY = -9.8f
    const val PET_W = 1.1f
    const val PET_H = 1.2f

    fun petX(side: Side) = if (side == Side.LEFT) 6f else WIDTH - 6f

    fun buildMinX(side: Side) = if (side == Side.LEFT) HEDGE_W + 0.1f else FENCE_X + FENCE_HALF_W + 0.8f

    fun buildMaxX(side: Side) = if (side == Side.LEFT) FENCE_X - FENCE_HALF_W - 0.8f else WIDTH - HEDGE_W - 0.1f

    fun launchPoint(side: Side, out: Vector2 = Vector2()): Vector2 =
        out.set(petX(side) + side.dir * 0.2f, GROUND_Y + LAUNCH_HEIGHT)
}

/** Box2D collision categories: your own throws pass through your own fort and pet. */
object Bits {
    const val SCENERY = 0x0001
    const val LEFT_SOLID = 0x0002
    const val RIGHT_SOLID = 0x0004
    const val LEFT_SHOT = 0x0008
    const val RIGHT_SHOT = 0x0010

    fun solid(side: Side) = if (side == Side.LEFT) LEFT_SOLID else RIGHT_SOLID

    fun shot(side: Side) = if (side == Side.LEFT) LEFT_SHOT else RIGHT_SHOT

    fun solidMask(side: Side) = SCENERY or LEFT_SOLID or RIGHT_SOLID or shot(side.other)

    fun shotMask(side: Side) = SCENERY or solid(side.other)
}

/** A splat stuck to something, in that body's local coordinates (or world coordinates on the ground). */
class Decal(val x: Float, val y: Float, val r: Float, val item: ItemType, val seed: Int)

enum class SceneryKind { GROUND, FENCE, HEDGE }

class Pet(val side: Side, val kind: PetKind, val body: Body) {
    var mess = 0f
    /** Seconds left of the dizzy face after a hit. */
    var hitTimer = 0f
    val decals = ArrayList<Decal>()
}

class Piece(val type: PieceType, val side: Side, val rotated: Boolean, val body: Body) {
    val decals = ArrayList<Decal>()

    val halfW: Float get() = (if (rotated) type.h else type.w) / 2f
    val halfH: Float get() = (if (rotated) type.w else type.h) / 2f
}

class Projectile(val item: ItemType, val side: Side, val body: Body, val fragment: Boolean) {
    var age = 0f
    var dead = false
    var scoredPet = false

    val radius: Float get() = if (fragment) FRAGMENT_RADIUS else item.radius
    val mess: Float get() = if (fragment) FRAGMENT_MESS else item.mess

    companion object {
        const val FRAGMENT_RADIUS = 0.25f
        const val FRAGMENT_MESS = 10f
    }
}
