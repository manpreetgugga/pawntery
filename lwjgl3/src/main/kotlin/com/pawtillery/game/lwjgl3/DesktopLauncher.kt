package com.pawtillery.game.lwjgl3

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.pawtillery.game.Pawtillery

/** Desktop window for testing on a computer; the mouse stands in for a finger. */
fun main() {
    val config = Lwjgl3ApplicationConfiguration().apply {
        setTitle("Pawtillery (desktop test build)")
        setWindowedMode(1280, 720)
        useVsync(true)
        setForegroundFPS(60)
    }
    Lwjgl3Application(Pawtillery(DemoPlatform()), config)
}
