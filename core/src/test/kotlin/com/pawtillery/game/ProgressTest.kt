package com.pawtillery.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ProgressTest {
    private val settings = Settings(MemoryPreferences())

    @Test
    fun petsUnlockWithTreats() {
        assertTrue(settings.isUnlocked(PetKind.DOG))
        assertFalse(settings.isUnlocked(PetKind.BUNNY))
        assertFalse("can't afford it yet", settings.buyPet(PetKind.BUNNY))
        settings.addTreats(PetKind.BUNNY.price + 5)
        assertTrue(settings.buyPet(PetKind.BUNNY))
        assertTrue(settings.isUnlocked(PetKind.BUNNY))
        assertEquals(5, settings.treats)
        assertFalse("already unlocked", settings.buyPet(PetKind.BUNNY))
    }

    @Test
    fun treatsDependOnResultLengthLevelHitsAndTricks() {
        assertEquals(30, settings.recordGame(GameResult.WIN, MatchConfig.FULL_MATCH, hits = 0, tricks = 0))
        assertEquals(6 + 2 * 3 + 5, settings.recordGame(GameResult.LOSS, MatchConfig.QUICK_MATCH, hits = 3, tricks = 1))
        assertEquals(45, settings.recordGame(GameResult.WIN, MatchConfig.FULL_MATCH, 0, 0, ComputerLevel.CHAMPION.treatFactor))
        assertEquals(30 + 17 + 45, settings.treats)
        assertEquals(3, settings.gamesPlayed)
        assertEquals(2, settings.wins)
    }

    @Test
    fun plusUnlocksEverythingDoublesTreatsAndRemovesAds() {
        val today = "2026-10-27"
        assertTrue(settings.canWatchAd(today))
        settings.plus = true
        assertTrue(PetKind.entries.all { settings.isUnlocked(it) })
        assertEquals(60, settings.recordGame(GameResult.WIN, MatchConfig.FULL_MATCH, 0, 0))
        assertEquals(60, settings.recordChallenge(1, 3))
        assertFalse(settings.canWatchAd(today))
    }

    @Test
    fun rewardedAdsAreCappedPerDay() {
        val today = "2026-10-27"
        repeat(MAX_ADS_PER_DAY) {
            assertTrue(settings.canWatchAd(today))
            settings.recordAdWatched(today)
        }
        assertFalse(settings.canWatchAd(today))
        assertTrue("a new day resets the cap", settings.canWatchAd("2026-10-28"))
    }

    @Test
    fun challengeStarsOnlyPayForNewStars() {
        assertTrue(settings.isChallengeOpen(1))
        assertFalse(settings.isChallengeOpen(2))
        assertEquals(10, settings.recordChallenge(1, 1))
        assertTrue(settings.isChallengeOpen(2))
        assertEquals(0, settings.recordChallenge(1, 1))
        assertEquals(20, settings.recordChallenge(1, 3))
        assertEquals(0, settings.recordChallenge(1, 2))
        assertEquals(3, settings.totalStars)
    }

    @Test
    fun parentGateNeedsTheRightAnswer() {
        val gate = ParentGate(Random(4))
        assertTrue(gate.a in 6..9 && gate.b in 6..9)
        gate.type(9)
        gate.type(9)
        gate.type(9)
        gate.type(9)
        assertEquals("only three digits", "999", gate.typed)
        assertFalse(gate.check())
        assertEquals("a wrong answer clears the pad", "", gate.typed)
        (gate.a * gate.b).toString().forEach { gate.type(it.digitToInt()) }
        assertTrue(gate.check())
    }
}
