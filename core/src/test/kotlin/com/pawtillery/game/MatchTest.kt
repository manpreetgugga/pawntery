package com.pawtillery.game

import com.badlogic.gdx.math.MathUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import kotlin.random.Random

class MatchTest {
    companion object {
        /** On a device the libGDX backend loads its native code; in unit tests we do it ourselves. */
        @JvmStatic
        @BeforeClass
        fun loadNatives() = com.badlogic.gdx.utils.GdxNativesLoader.load()
    }

    private val matches = ArrayList<Match>()

    private fun newMatch(easy: Boolean = true, seed: Long = 1L, left: PetKind = PetKind.DOG, right: PetKind = PetKind.CAT, throws: Int = MatchConfig.FULL_MATCH) =
        Match(MatchConfig(easyMode = easy, leftKind = left, rightKind = right, throwsPerPlayer = throws, seed = seed)).also { matches.add(it) }

    @After
    fun tearDown() = matches.forEach { it.dispose() }

    private fun Match.runUntil(maxSeconds: Float = 20f, condition: Match.() -> Boolean) {
        var t = 0f
        while (!condition() && t < maxSeconds) {
            step()
            t += Arena.STEP
        }
    }

    private fun Match.skipBuild() {
        finishBuild()
        finishBuild()
    }

    @Test
    fun aimGuideMatchesTheRealThrow() {
        for (item in listOf(ItemType.MUD, ItemType.BONE)) {
            val m = newMatch(easy = false, seed = 5L)
            m.skipBuild()
            val vx = 9f
            val vy = 14f
            val origin = Arena.launchPoint(Side.LEFT)
            val predicted = ArrayList<Pair<Float, Float>>()
            Trajectory.simulate(origin.x, origin.y, vx, vy, item, Side.LEFT, m.wind, 40) { _, x, y, _, _ ->
                predicted.add(x to y)
                true
            }
            assertTrue(m.throwItem(item, vx, vy))
            val body = m.projectiles.single().body
            for ((x, y) in predicted) {
                m.step()
                assertEquals("x for $item", x, body.position.x, 0.01f)
                assertEquals("y for $item", y, body.position.y, 0.01f)
            }
        }
    }

    @Test
    fun piecesStackInsteadOfOverlapping() {
        val m = newMatch()
        val x = Arena.petX(Side.LEFT) + 2.5f
        assertTrue(m.placePiece(PieceType.CRATE, false, x))
        m.runUntil(3f) { false }
        val drop = m.dropPosition(Side.LEFT, PieceType.CRATE, false, x)
        assertNotNull(drop)
        // The second crate drops onto the first one, not into it.
        assertTrue(drop!!.y > Arena.GROUND_Y + PieceType.CRATE.h + PieceType.CRATE.h / 2f)
    }

    @Test
    fun buildingIsLimitedToSixPiecesInYourOwnYard() {
        val m = newMatch()
        repeat(Arena.PIECES_PER_PLAYER) { assertTrue(m.placePiece(PieceType.BOX, false, 3f + it * 1.5f)) }
        assertFalse(m.placePiece(PieceType.BOX, false, 4f))
        assertTrue(m.pieces.all { it.body.position.x < Arena.FENCE_X })
        assertTrue(m.undoPiece())
        assertEquals(1, m.piecesLeft(Side.LEFT))
        // Dropping far over the fence still lands in your own yard.
        m.placePiece(PieceType.BOX, false, 30f)
        assertTrue(m.pieces.last().body.position.x <= Arena.buildMaxX(Side.LEFT))
    }

    @Test
    fun turnsAlternateAndUseUpLimitedItems() {
        val m = newMatch()
        m.skipBuild()
        assertEquals(Phase.AIM, m.phase)
        assertEquals(Side.LEFT, m.turn)
        assertEquals(2, m.count(Side.LEFT, ItemType.WATER))
        assertTrue(m.throwItem(ItemType.WATER, 3f, 3f))
        assertEquals(1, m.count(Side.LEFT, ItemType.WATER))
        m.runUntil { phase == Phase.AIM }
        assertEquals(Side.RIGHT, m.turn)
        assertEquals(MatchConfig.FULL_MATCH - 1, m.throwsLeft(Side.LEFT))
        // Cat cannot throw the dog's bone.
        assertFalse(m.canThrow(ItemType.BONE))
        assertTrue(m.canThrow(ItemType.HAIRBALL))
        assertEquals(-1, m.count(Side.RIGHT, ItemType.MUD))
    }

    @Test
    fun directHitMakesThePetMessy() {
        val m = newMatch()
        m.skipBuild()
        val plan = findHit(m, Side.LEFT)
        assertTrue(m.throwItem(ItemType.MUD, plan.first, plan.second))
        m.runUntil { phase == Phase.AIM }
        assertTrue("cat mess was ${m.pet(Side.RIGHT).mess}", m.pet(Side.RIGHT).mess >= 9f)
        assertEquals(0f, m.pet(Side.LEFT).mess, 0.001f)
        assertTrue(m.pet(Side.RIGHT).decals.isNotEmpty())
        assertEquals(ThrowStats(throws = 1, hits = 1, tricks = 0), m.stats(Side.LEFT))
    }

    @Test
    fun hairballSplitsIntoThree() {
        val m = newMatch()
        m.finishBuild()
        m.finishBuild()
        m.throwItem(ItemType.MUD, 1f, 1f)
        m.runUntil { phase == Phase.AIM }
        assertTrue(m.throwItem(ItemType.HAIRBALL, -8f, 12f))
        repeat(20) { m.step() }
        assertTrue(m.splitActive())
        assertEquals(3, m.projectiles.size)
        assertTrue(m.projectiles.all { it.fragment })
        assertFalse(m.splitActive())
    }

    @Test
    fun seedShowerScattersFiveSeeds() {
        val m = newMatch(left = PetKind.PARROT)
        m.skipBuild()
        assertTrue(m.throwItem(ItemType.SEEDS, 8f, 12f))
        repeat(20) { m.step() }
        assertTrue(m.splitActive())
        assertEquals(5, m.projectiles.size)
        assertTrue(m.projectiles.all { it.fragment && it.mess == ItemType.SEEDS.fragmentMess })
    }

    @Test
    fun quickMatchesEndAfterFiveThrowsEach() {
        val m = newMatch(throws = MatchConfig.QUICK_MATCH)
        m.skipBuild()
        var throws = 0
        while (m.phase != Phase.OVER) {
            assertTrue(m.throwItem(ItemType.MUD, m.turn.dir * 2f, 2f))
            throws++
            m.runUntil { phase == Phase.AIM || phase == Phase.OVER }
        }
        assertEquals(2 * MatchConfig.QUICK_MATCH, throws)
        assertNull("no one got messy, so it's a draw", m.winner)
    }

    @Test
    fun everyPetCanThrowItsSignature() {
        for (kind in PetKind.entries) {
            val m = newMatch(left = kind)
            m.skipBuild()
            assertTrue("${kind.name} throws ${kind.signature}", m.throwItem(kind.signature, 8f, 10f))
            m.runUntil { phase == Phase.AIM }
            assertEquals(1, m.count(Side.LEFT, kind.signature))
        }
    }

    @Test
    fun easyModeHasNoWindButProModeDoes() {
        val easy = newMatch(easy = true)
        easy.skipBuild()
        assertEquals(0f, easy.wind, 0f)
        val winds = (1L..12L).map { seed -> newMatch(easy = false, seed = seed).also { it.skipBuild() }.wind }
        assertTrue(winds.any { it != 0f })
        assertTrue(winds.all { it in -3.5f..3.5f })
    }

    @Test
    fun computerVsComputerMatchesFinish() {
        for (seed in 1L..5L) {
            val pets = PetKind.entries
            val m = newMatch(easy = seed % 2 == 0L, seed = seed, left = pets[seed.toInt() % pets.size], right = pets[(seed.toInt() + 2) % pets.size])
            val ai = ComputerPlayer(Random(seed), ComputerLevel.entries[seed.toInt() % ComputerLevel.entries.size])
            for (side in Side.entries) {
                ai.planBuild(side).forEach { assertTrue(m.placePiece(it.type, it.rotated, it.x)) }
                m.runUntil(2f) { false }
                m.finishBuild()
            }
            var throws = 0
            while (m.phase != Phase.OVER && throws < 2 * m.config.throwsPerPlayer + 1) {
                assertEquals(Phase.AIM, m.phase)
                val plan = ai.planShot(m, m.turn)
                assertTrue(m.throwItem(plan.item, plan.vx, plan.vy))
                throws++
                m.runUntil {
                    if (plan.splitAtApex && activeSplitter != null && activeSplitter!!.body.linearVelocity.y < 0f) splitActive()
                    phase == Phase.AIM || phase == Phase.OVER
                }
            }
            assertEquals(Phase.OVER, m.phase)
            val left = m.pet(Side.LEFT).mess
            val right = m.pet(Side.RIGHT).mess
            assertTrue("someone should get messy (seed $seed)", left + right > 20f)
            when {
                left < right -> assertEquals(Side.LEFT, m.winner)
                right < left -> assertEquals(Side.RIGHT, m.winner)
                else -> assertNull(m.winner)
            }
        }
    }

    @Test
    fun aBoneThatHitsHeadOnIsNotABoomerang() {
        val m = newMatch()
        m.skipBuild()
        val (vx, vy) = findHit(m, Side.LEFT, ItemType.BONE, forwardOnly = true)
        assertTrue(m.throwItem(ItemType.BONE, vx, vy))
        m.runUntil { phase == Phase.AIM }
        assertTrue("the bone should hit (cat mess ${m.pet(Side.RIGHT).mess})", m.pet(Side.RIGHT).mess > 5f)
        assertFalse("a head-on hit bounced back but is no boomerang: ${m.tricks(Side.LEFT)}", "Boomerang!" in m.tricks(Side.LEFT))
        assertEquals(1, m.stats(Side.LEFT).hits)
    }

    /** Brute-force search for a throw from [side] that lands on the other pet with no fort in the way. */
    private fun findHit(m: Match, side: Side, item: ItemType = ItemType.MUD, forwardOnly: Boolean = false): Pair<Float, Float> {
        val target = m.pet(side.other).body.position
        val origin = Arena.launchPoint(side)
        var best = Float.MAX_VALUE
        var bestV = 0f to 0f
        var angle = 20f
        while (angle < 80f) {
            var speed = 8f
            while (speed < Arena.MAX_SPEED) {
                val vx = side.dir * MathUtils.cosDeg(angle) * speed
                val vy = MathUtils.sinDeg(angle) * speed
                var miss = Float.MAX_VALUE
                Trajectory.simulate(origin.x, origin.y, vx, vy, item, side, m.wind, 400) { _, x, y, svx, _ ->
                    if (!forwardOnly || svx * side.dir > 0f) miss = minOf(miss, com.badlogic.gdx.math.Vector2.dst(x, y, target.x, target.y))
                    val inFence = kotlin.math.abs(x - Arena.FENCE_X) < Arena.FENCE_HALF_W + 0.4f && y < Arena.FENCE_PEAK + 0.4f
                    y > Arena.GROUND_Y && !inFence
                }
                if (miss < best) {
                    best = miss
                    bestV = vx to vy
                }
                speed += 0.25f
            }
            angle += 1f
        }
        return bestV
    }
}
