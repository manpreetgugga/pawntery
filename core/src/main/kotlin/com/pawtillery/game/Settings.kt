package com.pawtillery.game

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Preferences

/** Per-device settings and progress, saved with libGDX Preferences. */
class Settings(private val prefs: Preferences = Gdx.app.getPreferences("pawtillery")) {

    /** Easy: full aim guide and no wind. Pro: wind and a short aim guide. */
    var easyMode: Boolean
        get() = prefs.getBoolean("easyMode", true)
        set(value) = save { prefs.putBoolean("easyMode", value) }

    /** Sound effects and vibration. */
    var sound: Boolean
        get() = prefs.getBoolean("sound", true)
        set(value) = save { prefs.putBoolean("sound", value) }

    /** Quick matches are 5 throws each instead of 10. */
    var quickMatch: Boolean
        get() = prefs.getBoolean("quickMatch", false)
        set(value) = save { prefs.putBoolean("quickMatch", value) }

    /** Set once the player has made their first throw, to stop showing the aiming tip. */
    var aimTipSeen: Boolean
        get() = prefs.getBoolean("aimTipSeen", false)
        set(value) = save { prefs.putBoolean("aimTipSeen", value) }

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

    fun isUnlocked(kind: PetKind): Boolean = kind.price == 0 || prefs.getBoolean("unlocked.${kind.name}", false)

    /** Spends treats on [kind]; returns false if it is already unlocked or there aren't enough treats. */
    fun buyPet(kind: PetKind): Boolean {
        if (isUnlocked(kind) || treats < kind.price) return false
        prefs.putInteger("treats", treats - kind.price)
        prefs.putBoolean("unlocked.${kind.name}", true)
        prefs.flush()
        return true
    }

    /** Records a finished game and returns the treats it earned. */
    fun recordGame(result: GameResult, throwsPerPlayer: Int, directHits: Int): Int {
        val base = when (result) {
            GameResult.WIN -> 30
            GameResult.DRAW -> 18
            GameResult.LOSS -> 12
        }
        val earned = base * throwsPerPlayer / MatchConfig.FULL_MATCH + directHits * 2
        prefs.putInteger("treats", treats + earned)
        prefs.putInteger("gamesPlayed", gamesPlayed + 1)
        if (result == GameResult.WIN) prefs.putInteger("wins", wins + 1)
        prefs.flush()
        return earned
    }

    private inline fun save(change: () -> Unit) {
        change()
        prefs.flush()
    }
}

enum class GameResult { WIN, DRAW, LOSS }
