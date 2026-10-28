package com.pawtillery.game.android

import android.os.Bundle
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.pawtillery.game.Pawtillery

class AndroidLauncher : AndroidApplication() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = AndroidApplicationConfiguration().apply {
            useImmersiveMode = true
            // Draw the backyard edge to edge on every Android version; the game keeps its
            // buttons inside the safe area reported by the phone.
            renderUnderCutout = true
            // Keep the screen awake while a player lines up a shot.
            useWakelock = true
            useAccelerometer = false
            useCompass = false
            numSamples = 2
        }
        // Test builds get a platform that updates itself from GitHub; store builds don't (src/debug, src/release).
        initialize(Pawtillery(launcherPlatform(this)), config)
    }
}
