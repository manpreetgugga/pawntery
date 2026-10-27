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
    fun resetClearsProgressButKeepsPlusAndSettings() {
        settings.addTreats(100)
        settings.recordChallenge(1, 2)
        settings.plus = true
        settings.music = false
        settings.resetProgress()
        assertEquals(0, settings.treats)
        assertEquals(0, settings.totalStars)
        assertTrue(settings.plus)
        assertFalse(settings.music)
        assertTrue(settings.sound)
    }

    @Test
    fun resetKeepsTheDailyAdCountAndOtherSettings() {
        val today = "2026-10-27"
        repeat(MAX_ADS_PER_DAY) { settings.recordAdWatched(today) }
        settings.computerLevel = ComputerLevel.CHAMPION
        settings.quickMatch = true
        settings.grantTrophy(Trophy.FIRST_WIN)
        settings.resetProgress()
        assertFalse("reset must not refill today's ads", settings.canWatchAd(today))
        assertEquals(ComputerLevel.CHAMPION, settings.computerLevel)
        assertTrue(settings.quickMatch)
        assertFalse(settings.hasTrophy(Trophy.FIRST_WIN))
        assertEquals(0, settings.treats)
    }

    private fun win(level: ComputerLevel = ComputerLevel.NORMAL, hits: Int = 3, tricks: List<String> = emptyList(), ownMess: Float = 20f) =
        GameSummary(GameKind.VS_COMPUTER, true, level, MatchConfig.FULL_MATCH, 10, hits, tricks, ownMess)

    private fun award(summary: GameSummary): List<Trophy> {
        settings.recordStats(summary)
        return Trophies.earned(settings, summary).onEach { settings.grantTrophy(it) }
    }

    @Test
    fun trophiesAreEarnedOnceAndPayTheirReward() {
        val first = award(win(ComputerLevel.CHAMPION, hits = 8, tricks = listOf("Boomerang!", "Triple!"), ownMess = 0f))
        assertEquals(
            setOf(Trophy.FIRST_WIN, Trophy.CHAMPION, Trophy.SHARPSHOOTER, Trophy.SPOTLESS, Trophy.BOOMERANG, Trophy.TRIPLE),
            first.toSet(),
        )
        assertEquals(first.sumOf { it.reward }, settings.treats)
        assertTrue("already earned trophies don't come back", award(win()).isEmpty())
        assertEquals(listOf(Trophy.HOT_STREAK), award(win()))
        // A loss resets the streak
        award(GameSummary(GameKind.VS_COMPUTER, false, ComputerLevel.NORMAL, 10, 10, 1, emptyList(), 60f))
        assertEquals(0, settings.winStreak)
        assertEquals(3, settings.bestStreak)
        assertEquals(1, settings.championWins)
    }

    @Test
    fun dailyChallengePaysOncePerDayAndCountsTowardDailyRegular() {
        assertEquals(DAILY_REWARD, settings.recordDaily(100L, 1))
        assertEquals("a better score the same day pays nothing more", 0, settings.recordDaily(100L, 3))
        assertEquals(3, settings.dailyStars(100L))
        for (day in 101L..104L) settings.recordDaily(day, 2)
        assertEquals(5, settings.dailiesDone)
        val summary = GameSummary(GameKind.DAILY, true, null, 7, 3, 2, emptyList(), 0f)
        assertEquals(listOf(Trophy.DAILY_REGULAR), award(summary))
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
