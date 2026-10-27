package com.pawtillery.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import kotlin.random.Random

class ChallengeTest {
    companion object {
        @JvmStatic
        @BeforeClass
        fun loadNatives() = com.badlogic.gdx.utils.GdxNativesLoader.load()
    }

    /** Plays [challenge] with a sharp computer player; returns throws used, or -1 if it failed. */
    private fun solve(challenge: Challenge, seed: Long): Int {
        val m = Match(MatchConfig(easyMode = true, leftKind = challenge.player, rightKind = challenge.target, seed = seed, challenge = challenge))
        try {
            val ai = ComputerPlayer(Random(seed), 0.6f, 0.1f, easesOff = false)
            while (m.phase != Phase.OVER) {
                assertEquals(Phase.AIM, m.phase)
                val plan = ai.planShot(m, Side.LEFT)
                assertTrue("${challenge.title}: can throw ${plan.item}", m.throwItem(plan.item, plan.vx, plan.vy))
                var t = 0
                while (m.phase != Phase.AIM && m.phase != Phase.OVER && t < 2000) {
                    val s = m.activeSplitter
                    if (plan.splitAtApex && s != null && s.body.linearVelocity.y < 0f) m.splitActive()
                    m.step()
                    t++
                }
            }
            return if (m.winner == Side.LEFT) m.stats(Side.LEFT).throws else -1
        } finally {
            m.dispose()
        }
    }

    @Test
    fun everyChallengeStartsReadyToThrowWithItsFortBuilt() {
        for (c in Challenges.all) {
            val m = Match(MatchConfig(easyMode = true, leftKind = c.player, rightKind = c.target, seed = 1, challenge = c))
            assertEquals(Phase.AIM, m.phase)
            assertEquals(Side.LEFT, m.turn)
            assertEquals(c.wind, m.wind, 0f)
            assertEquals(c.items.map { it.first }, m.items(Side.LEFT))
            assertEquals(c.maxThrows, m.throwsLeft(Side.LEFT))
            assertEquals("${c.title}: whole fort placed", c.fortPlacements().size, m.pieces.size)
            assertTrue("${c.title}: fort is on the target's side", m.pieces.all { it.side == Side.RIGHT && it.body.position.x > Arena.FENCE_X })
            assertEquals("${c.title}: the fort settles without touching the target", 0f, m.pet(Side.RIGHT).mess, 0f)
            m.dispose()
        }
    }

    @Test
    fun everyChallengeCanBeBeaten() {
        val report = StringBuilder()
        var failures = 0
        for (c in Challenges.all) {
            val results = (1L..3L).map { solve(c, it) }
            report.append("${c.number}. ${c.title}: ${results.joinToString { if (it < 0) "failed" else "$it throws" }} (3 stars at ${c.threeStars})\n")
            if (results.none { it > 0 }) failures++
        }
        println(report)
        assertEquals("challenges no player could beat:\n$report", 0, failures)
    }

    @Test
    fun starsDependOnThrowsUsed() {
        val c = Challenges.all.first()
        assertEquals(3, c.stars(true, c.threeStars))
        assertEquals(2, c.stars(true, c.threeStars + 1))
        assertEquals(1, c.stars(true, c.twoStars + 1))
        assertEquals(0, c.stars(false, 1))
    }
}
