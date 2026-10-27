package com.pawtillery.game

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Preferences

/** Per-device settings and progress, saved with libGDX Preferences. */
class Settings(private val prefs: Preferences = Gdx.app.getPreferences("pawtillery")) {

    /** Easy: full aim guide and no wind. Pro: wind and a short aim guide. */
    var easyMode: Boolean
        get() = prefs.getBoolean("easyMode", true)
        set(value) = save { prefs.putBoolean("easyMode", value) }

    /** Sound effects. */
    var sound: Boolean
        get() = prefs.getBoolean("sound", true)
        set(value) = save { prefs.putBoolean("sound", value) }

    var music: Boolean
        get() = prefs.getBoolean("music", true)
        set(value) = save { prefs.putBoolean("music", value) }

    /** A short buzz on hits, on phones that can vibrate. */
    var vibration: Boolean
        get() = prefs.getBoolean("vibration", true)
        set(value) = save { prefs.putBoolean("vibration", value) }

    /** Clears all progress (treats, pets, stars, stats) but keeps Plus and the sound settings. */
    fun resetProgress() {
        val keep = listOf("plus", "sound", "music", "vibration", "easyMode")
        val saved = keep.associateWith { prefs.getBoolean(it, it != "plus") }
        prefs.clear()
        saved.forEach { (k, v) -> prefs.putBoolean(k, v) }
        prefs.flush()
    }

    /** Quick matches are 5 throws each instead of 10. */
    var quickMatch: Boolean
        get() = prefs.getBoolean("quickMatch", false)
        set(value) = save { prefs.putBoolean("quickMatch", value) }

    /** Set once the player has made their first throw, to stop showing the aiming tip. */
    var aimTipSeen: Boolean
        get() = prefs.getBoolean("aimTipSeen", false)
        set(value) = save { prefs.putBoolean("aimTipSeen", value) }

    var computerLevel: ComputerLevel
        get() = ComputerLevel.entries.firstOrNull { it.name == prefs.getString("computerLevel") } ?: ComputerLevel.NORMAL
        set(value) = save { prefs.putString("computerLevel", value.name) }

    /** The pet chosen last time, so the pet picker starts there. */
    var lastPet: PetKind
        get() = PetKind.entries.firstOrNull { it.name == prefs.getString("lastPet") } ?: PetKind.DOG
        set(value) = save { prefs.putString("lastPet", value.name) }

    /** Treats are earned by playing and spent on unlocking pets. */
    var treats: Int
        get() = prefs.getInteger("treats", 0)
        private set(value) = save { prefs.putInteger("treats", value) }

    var wins: Int
        get() = prefs.getInteger("wins", 0)
        private set(value) = save { prefs.putInteger("wins", value) }

    var gamesPlayed: Int
        get() = prefs.getInteger("gamesPlayed", 0)
        private set(value) = save { prefs.putInteger("gamesPlayed", value) }

    /** Pawtillery Plus: every pet, no ads, double treats. Bought once in the Shop. */
    var plus: Boolean
        get() = prefs.getBoolean("plus", false)
        set(value) = save { prefs.putBoolean("plus", value) }

    /** Treats from games and stars are doubled with Plus. */
    val treatMultiplier: Int get() = if (plus) 2 else 1

    fun isUnlocked(kind: PetKind): Boolean = plus || kind.price == 0 || prefs.getBoolean("unlocked.${kind.name}", false)

    fun addTreats(amount: Int) = save { prefs.putInteger("treats", treats + amount) }

    /** Rewarded ads are optional and capped per day; [today] is an ISO date like 2026-10-27. */
    fun canWatchAd(today: String): Boolean = !plus && adsWatched(today) < MAX_ADS_PER_DAY

    fun recordAdWatched(today: String) = save { prefs.putInteger("ads.$today", adsWatched(today) + 1) }

    private fun adsWatched(today: String) = prefs.getInteger("ads.$today", 0)

    /** Spends treats on [kind]; returns false if it is already unlocked or there aren't enough treats. */
    fun buyPet(kind: PetKind): Boolean {
        if (isUnlocked(kind) || treats < kind.price) return false
        prefs.putInteger("treats", treats - kind.price)
        prefs.putBoolean("unlocked.${kind.name}", true)
        prefs.flush()
        return true
    }

    /**
     * Records a finished game and returns the treats it earned: a base for the result (scaled by
     * match length and by [levelFactor] for the computer's level), 2 per hit and 5 per trick shot.
     */
    fun recordGame(result: GameResult, throwsPerPlayer: Int, hits: Int, tricks: Int, levelFactor: Float = 1f): Int {
        val base = when (result) {
            GameResult.WIN -> 30
            GameResult.DRAW -> 18
            GameResult.LOSS -> 12
        }
        val earned = ((base * throwsPerPlayer / MatchConfig.FULL_MATCH * levelFactor).toInt() + hits * 2 + tricks * 5) * treatMultiplier
        prefs.putInteger("treats", treats + earned)
        prefs.putInteger("gamesPlayed", gamesPlayed + 1)
        if (result == GameResult.WIN) prefs.putInteger("wins", wins + 1)
        prefs.flush()
        return earned
    }

    fun challengeStars(number: Int): Int = prefs.getInteger("challenge.$number.stars", 0)

    val totalStars: Int get() = Challenges.all.sumOf { challengeStars(it.number) }

    /** Levels open one at a time: each needs at least one star on the level before it. */
    fun isChallengeOpen(number: Int): Boolean = number == 1 || challengeStars(number - 1) > 0

    /** Saves a level result and returns the treats for newly earned stars (10 each). */
    fun recordChallenge(number: Int, stars: Int): Int {
        val before = challengeStars(number)
        if (stars <= before) return 0
        val earned = (stars - before) * 10 * treatMultiplier
        prefs.putInteger("challenge.$number.stars", stars)
        prefs.putInteger("treats", treats + earned)
        prefs.flush()
        return earned
    }

    private inline fun save(change: () -> Unit) {
        change()
        prefs.flush()
    }
}

enum class GameResult { WIN, DRAW, LOSS }

const val MAX_ADS_PER_DAY = 5
