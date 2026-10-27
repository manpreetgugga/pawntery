package com.pawtillery.game

/**
 * A Trick Shot level: the player's pet against a target pet sitting in a ready-made fort. The target
 * never throws back. Mess the target up to [goal]% within [maxThrows]; fewer throws earn more stars.
 */
class Challenge(
    val number: Int,
    val title: String,
    val brief: String,
    val player: PetKind,
    val target: PetKind,
    /** What the player can throw and how many of each (-1 = unlimited), in tray order. */
    val items: List<Pair<ItemType, Int>>,
    /** The target's fort: offsets in meters from the target pet, positive toward the fence. */
    private val fort: List<Triple<PieceType, Boolean, Float>>,
    val wind: Float,
    val goal: Float,
    val maxThrows: Int,
    val threeStars: Int,
    val twoStars: Int,
    /** For the daily challenge: the day it belongs to (days since 1970-01-01); null for Trick Shot levels. */
    val day: Long? = null,
) {
    val isDaily: Boolean get() = day != null

    fun fortPlacements(): List<Placement> {
        val petX = Arena.petX(Side.RIGHT)
        return fort.map { (type, rotated, offset) -> Placement(type, rotated, petX + Side.RIGHT.dir * offset) }
    }

    /** Stars for finishing in [throwsUsed] throws (0 when the goal was not reached). */
    fun stars(won: Boolean, throwsUsed: Int): Int = when {
        !won -> 0
        throwsUsed <= threeStars -> 3
        throwsUsed <= twoStars -> 2
        else -> 1
    }
}

object Challenges {
    private val crate = PieceType.CRATE
    private val box = PieceType.BOX
    private val plank = PieceType.PLANK
    private val pot = PieceType.POT
    private val gnome = PieceType.GNOME
    private val tire = PieceType.TIRE

    private fun p(type: PieceType, offset: Float, rotated: Boolean = false) = Triple(type, rotated, offset)

    val all: List<Challenge> = listOf(
        Challenge(
            1, "First Splat", "No walls, no wind. Find your range!",
            PetKind.DOG, PetKind.CAT,
            items = listOf(ItemType.MUD to -1),
            fort = listOf(p(pot, -2.4f), p(tire, 3.4f)),
            wind = 0f, goal = 40f, maxThrows = 6, threeStars = 3, twoStars = 4,
        ),
        Challenge(
            2, "Over the Wall", "A tall wall. Lob it high and drop it in.",
            PetKind.DOG, PetKind.CAT,
            items = listOf(ItemType.MUD to -1),
            fort = listOf(p(crate, 2.2f), p(crate, 2.2f), p(crate, 2.2f), p(box, 3.4f)),
            wind = 0f, goal = 40f, maxThrows = 7, threeStars = 3, twoStars = 5,
        ),
        Challenge(
            3, "Splash Zone", "A roof keeps mud off. Water soaks everything nearby.",
            PetKind.DOG, PetKind.CAT,
            items = listOf(ItemType.WATER to 3, ItemType.MUD to -1),
            fort = listOf(p(gnome, 1.3f), p(gnome, -1.3f), p(plank, 0f), p(crate, 2.7f), p(box, 2.7f), p(pot, -2.7f)),
            wind = 0f, goal = 40f, maxThrows = 6, threeStars = 2, twoStars = 4,
        ),
        Challenge(
            4, "Bowl Them Over", "Smash the bunker open, then finish the job.",
            PetKind.CAT, PetKind.DOG,
            items = listOf(ItemType.BOWLING to 3, ItemType.MUD to -1),
            fort = listOf(p(box, 2.0f), p(box, 2.0f), p(box, 3.2f), p(box, 3.2f), p(gnome, 1.1f), p(gnome, -1.4f), p(plank, -0.15f)),
            wind = 0f, goal = 40f, maxThrows = 7, threeStars = 4, twoStars = 5,
        ),
        Challenge(
            5, "Boomerang Bay", "The front is sealed. The back is wide open...",
            PetKind.DOG, PetKind.CAT,
            items = listOf(ItemType.BONE to 4, ItemType.MUD to -1),
            fort = listOf(p(crate, 1.6f), p(crate, 1.6f), p(crate, 1.6f), p(box, 1.6f), p(crate, 2.9f), p(crate, 2.9f)),
            wind = 0f, goal = 30f, maxThrows = 6, threeStars = 3, twoStars = 4,
        ),
        Challenge(
            6, "Split Decision", "Tap a hairball mid-air: three chances to land one.",
            PetKind.CAT, PetKind.DOG,
            items = listOf(ItemType.HAIRBALL to 4, ItemType.MUD to -1),
            fort = listOf(p(crate, 2.2f), p(crate, 2.2f), p(box, 3.4f), p(gnome, -2.2f)),
            wind = 0f, goal = 40f, maxThrows = 6, threeStars = 3, twoStars = 4,
        ),
        Challenge(
            7, "Headwind", "A strong wind blows back at you. Aim long.",
            PetKind.DOG, PetKind.CAT,
            items = listOf(ItemType.MUD to -1, ItemType.WATER to 1),
            fort = listOf(p(box, 2.4f), p(pot, 3.4f), p(tire, -2.2f)),
            wind = -3f, goal = 45f, maxThrows = 7, threeStars = 3, twoStars = 5,
        ),
        Challenge(
            8, "Gale Force", "A gale blows back at you. Carrots fly fast, so the wind has less time to push them.",
            PetKind.BUNNY, PetKind.CAT,
            items = listOf(ItemType.CARROT to 4, ItemType.MUD to -1),
            fort = listOf(p(box, 2.2f), p(pot, 3.4f), p(gnome, -2.0f)),
            wind = -3.5f, goal = 34f, maxThrows = 6, threeStars = 2, twoStars = 4,
        ),
        Challenge(
            9, "Back Door", "Shells bounce off the hedge. Roll one in from behind.",
            PetKind.TURTLE, PetKind.DOG,
            items = listOf(ItemType.SHELL to 4, ItemType.MUD to -1),
            fort = listOf(p(gnome, 1.3f), p(gnome, -1.3f), p(plank, 0f), p(crate, 2.4f), p(crate, 2.4f)),
            wind = 0f, goal = 30f, maxThrows = 7, threeStars = 4, twoStars = 5,
        ),
        Challenge(
            10, "Seed Storm", "Walls front and back, open sky above. Scatter the seeds.",
            PetKind.PARROT, PetKind.CAT,
            items = listOf(ItemType.SEEDS to 4, ItemType.WATER to 1, ItemType.MUD to -1),
            fort = listOf(p(crate, 1.8f), p(crate, 1.8f), p(crate, -1.8f), p(crate, -1.8f), p(box, 3.0f)),
            wind = 1.5f, goal = 40f, maxThrows = 6, threeStars = 3, twoStars = 4,
        ),
    )

    fun byNumber(n: Int): Challenge? = all.firstOrNull { it.number == n }

    /**
     * Today's challenge, the same for everyone on the same date: a random pet pairing, fort and wind,
     * built from the same parts as the hand-made levels so it is always fair.
     */
    fun daily(day: Long): Challenge {
        val rng = kotlin.random.Random(day * 7919 + 13)
        val pets = PetKind.entries
        val player = pets[rng.nextInt(pets.size)]
        val target = pets.filter { it != player }[rng.nextInt(pets.size - 1)]
        val fort = ArrayList<Triple<PieceType, Boolean, Float>>()
        val roof = rng.nextFloat() < 0.4f
        // With a roof, the wall stands clear of the roof's front post so nothing lands on it.
        val wallAt = if (roof) 2.5f + rng.nextFloat() * 0.6f else 1.9f + rng.nextFloat() * 1.2f
        val wall = listOf(crate, box)
        repeat(1 + rng.nextInt(3)) { fort += p(wall[rng.nextInt(2)], wallAt) }
        if (roof) fort += listOf(p(gnome, 1.3f), p(gnome, -1.3f), p(plank, 0f))
        val extras = listOf(pot, tire, box, gnome)
        repeat(rng.nextInt(3)) { fort += p(extras[rng.nextInt(extras.size)], if (rng.nextBoolean()) -2.6f else 3.9f) }
        val wind = (rng.nextInt(-6, 7) * 0.5f)
        val items = listOf(player.signature to 3, ItemType.WATER to 1, ItemType.MUD to -1)
        val windText = when {
            wind == 0f -> "no wind"
            wind * Side.LEFT.dir > 0f -> "a tailwind"
            else -> "a headwind"
        }
        return Challenge(
            0, "Daily Challenge", "${player.displayName} against ${target.displayName} with $windText. A new one every day!",
            player, target, items, fort, wind, goal = 40f, maxThrows = 7, threeStars = 3, twoStars = 5, day = day,
        )
    }
}
