package com.pawtillery.game

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.Body

/** The two yards. LEFT throws to the right (dir = +1), RIGHT throws to the left. */
enum class Side(val dir: Float) {
    LEFT(1f), RIGHT(-1f);

    val other: Side get() = if (this == LEFT) RIGHT else LEFT
}

/** [price] is in treats; 0 means free from the start. */
enum class PetKind(val displayName: String, val signature: ItemType, val price: Int) {
    DOG("Dog", ItemType.BONE, 0),
    CAT("Cat", ItemType.HAIRBALL, 0),
    BUNNY("Bunny", ItemType.CARROT, 60),
    TURTLE("Turtle", ItemType.SHELL, 120),
    PARROT("Parrot", ItemType.SEEDS, 180),
}

/**
 * How an item bursts on its first touch: pets within [radius] get messy (the closer, the messier),
 * and things nearby are pushed away and, with [damage], cracked.
 */
class Burst(val radius: Float, val push: Float, val damage: Float)

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
    /** Tapping during flight splits it into this many pieces (0 = no split). */
    val splitCount: Int = 0,
    /** Angle between split pieces, in degrees. */
    val splitSpread: Float = 0f,
    val fragmentRadius: Float = 0f,
    val fragmentMess: Float = 0f,
    /** Bursts on its first touch instead of splatting. */
    val burst: Burst? = null,
    /** Hard enough to crack and break fort pieces it hits. */
    val smashes: Boolean = false,
    /** Tapping during flight sets it off early (fireworks). */
    val popsOnTap: Boolean = false,
    val hint: String,
) {
    MUD("Mud Ball", 0.32f, 2f, 15f, -1, true, hint = "Always ready. Splats on contact."),
    WATER("Water Balloon", 0.4f, 1f, 24f, 2, true, burst = Burst(3.2f, 1.4f, 0f), hint = "Bursts and soaks everything nearby, even behind walls."),
    BOWLING("Bowling Ball", 0.42f, 8f, 8f, 2, false, restitution = 0.05f, smashes = true, hint = "Heavy. Smashes forts apart."),
    MELON("Watermelon", 0.46f, 3f, 22f, 2, true, burst = Burst(2.3f, 2.2f, 8f), smashes = true, hint = "Heavy. Crashes through and bursts in a big splash."),
    FIREWORK("Firework", 0.24f, 1.2f, 16f, 2, true, gravityScale = 0.5f, burst = Burst(3f, 4.5f, 26f), popsOnTap = true, hint = "Floats far and fast. Tap to pop it in the air!"),
    PAINT_BOMB("Paint Bomb", 0.38f, 2.5f, 24f, 1, true, burst = Burst(3.4f, 7f, 50f), hint = "Kaboom! A huge paint blast that throws forts around."),
    BONE("Boomerang Bone", 0.3f, 1.5f, 18f, 2, true, gravityScale = 0.45f, returnAccel = 3.5f, hint = "Glides out and curves back. Hit them from behind!"),
    HAIRBALL("Hairball", 0.35f, 1.5f, 14f, 2, true, splitCount = 3, splitSpread = 11f, fragmentRadius = 0.25f, fragmentMess = 10f, hint = "Tap while it flies to split it into three."),
    CARROT("Carrot Rocket", 0.28f, 1.6f, 17f, 2, true, gravityScale = 0.3f, hint = "Flies fast and almost straight. Great for sneaky gaps."),
    SHELL("Shell Slide", 0.45f, 5f, 12f, 2, false, restitution = 0.45f, hint = "Bounces and rolls right through forts."),
    SEEDS("Seed Shower", 0.34f, 1.5f, 12f, 2, true, splitCount = 5, splitSpread = 13f, fragmentRadius = 0.19f, fragmentMess = 7f, hint = "Tap while it flies to scatter five seeds."),
    ;

    /** Reacts to a tap in flight: splits or pops. */
    val tappable: Boolean get() = splitCount > 0 || popsOnTap
}

/** One-use boosts: each player gets one of each per match (none in Trick Shots). */
enum class PowerUp(val displayName: String, val hint: String) {
    MEGA("Mega", "Mega: your next throw is twice as big"),
    TRIPLE("Triple", "Triple: throw three at once"),
    CALM("Calm", "Calm: no wind for this throw"),
    UMBRELLA("Umbrella", "Umbrella: blocks the next hit on your pet"),
}

enum class PieceShape { BOX, TRAPEZOID, CIRCLE }

/**
 * Fort pieces: [w] and [h] are the unrotated size in meters. A piece breaks once its damage from
 * hard knocks reaches [toughness].
 */
enum class PieceType(
    val displayName: String,
    val shape: PieceShape,
    val w: Float,
    val h: Float,
    val density: Float,
    val friction: Float,
    val restitution: Float,
    val toughness: Float,
) {
    BOX("Box", PieceShape.BOX, 1.0f, 1.0f, 0.5f, 0.6f, 0.05f, 32f),
    CRATE("Crate", PieceShape.BOX, 1.2f, 1.2f, 1.2f, 0.7f, 0.05f, 62f),
    PLANK("Plank", PieceShape.BOX, 3.0f, 0.3f, 1.0f, 0.7f, 0.05f, 42f),
    POT("Flower Pot", PieceShape.TRAPEZOID, 0.9f, 0.9f, 2.0f, 0.6f, 0.05f, 28f),
    GNOME("Gnome", PieceShape.BOX, 0.7f, 1.3f, 2.5f, 0.6f, 0.05f, 50f),
    TIRE("Tire", PieceShape.CIRCLE, 1.2f, 1.2f, 0.9f, 0.9f, 0.45f, 150f),
}

/** Field layout in meters. The camera always shows at least WIDTH x HEIGHT. */
object Arena {
    const val WIDTH = 32f
    const val HEIGHT = 18f
    /** The camera shows from this height up, so the grass strip has room for the item tray. */
    const val VIEW_BOTTOM = -1.6f
    const val VIEW_HEIGHT = HEIGHT - VIEW_BOTTOM
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
    const val MESS_TO_LOSE = 100f
    const val MAX_BUILD_HEIGHT = 12f
    const val STEP = 1f / 60f
    const val GRAVITY = -9.8f
    const val PET_W = 1.35f
    const val PET_H = 1.45f
    /** Pet art is drawn for a 1.1 x 1.2 m body; this scales it to the real body. */
    const val PET_ART_SCALE = 1.2f

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

class Piece(val type: PieceType, val side: Side, val rotated: Boolean, val body: Body, val seed: Int) {
    val decals = ArrayList<Decal>()
    /** Knocks taken so far; the piece breaks at its type's toughness. */
    var damage = 0f
    /** Knocks felt during the current physics step, added to [damage] once the step is over. */
    var pendingKnock = 0f
    /**
     * Steps left during which knocks don't count, because something soft (a mud ball, a balloon)
     * just pressed the piece into whatever it stands on.
     */
    var softTimer = 0

    /** How close to breaking, from 0 (new) to 1. */
    val wear: Float get() = (damage / type.toughness).coerceIn(0f, 1f)

    val halfW: Float get() = (if (rotated) type.h else type.w) / 2f
    val halfH: Float get() = (if (rotated) type.w else type.h) / 2f
}

/**
 * Something in flight. [scale] is its size (bigger with Mega); [multi] marks one of a Triple throw.
 */
class Projectile(val item: ItemType, val side: Side, val body: Body, val fragment: Boolean, val scale: Float = 1f, val multi: Boolean = false) {
    var age = 0f
    var dead = false
    var scoredPet = false
    /** Times it hit something other than a pet; a heavy roller that bounces before hitting is a bank shot. */
    var bounces = 0

    val radius: Float get() = (if (fragment) item.fragmentRadius else item.radius) * scale
    val mess: Float get() = (if (fragment) item.fragmentMess else item.mess) * (if (scale > 1f) MEGA_MESS else 1f) * (if (multi) TRIPLE_MESS else 1f)

    companion object {
        const val MEGA_SCALE = 1.6f
        const val MEGA_MESS = 1.5f
        /** Each of a Triple throw's three items carries a bit less, so the three together aren't overwhelming. */
        const val TRIPLE_MESS = 0.75f
    }
}
