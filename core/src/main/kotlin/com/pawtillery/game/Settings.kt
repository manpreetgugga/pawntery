package com.pawtillery.game

import com.badlogic.gdx.Gdx

/** Per-device settings, saved with libGDX Preferences. */
class Settings {
    private val prefs = Gdx.app.getPreferences("pawtillery")

    /** Easy: full aim guide and no wind. Pro: wind and a short aim guide. */
    var easyMode: Boolean
        get() = prefs.getBoolean("easyMode", true)
        set(value) = save { prefs.putBoolean("easyMode", value) }

    /** Sound effects and vibration. */
    var sound: Boolean
        get() = prefs.getBoolean("sound", true)
        set(value) = save { prefs.putBoolean("sound", value) }

    /** Set once the player has made their first throw, to stop showing the aiming tip. */
    var aimTipSeen: Boolean
        get() = prefs.getBoolean("aimTipSeen", false)
        set(value) = save { prefs.putBoolean("aimTipSeen", value) }

    private inline fun save(change: () -> Unit) {
        change()
        prefs.flush()
    }
}
