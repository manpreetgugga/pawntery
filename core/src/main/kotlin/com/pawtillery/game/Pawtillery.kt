package com.pawtillery.game

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.Screen
import com.badlogic.gdx.audio.Music
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer

/** The game. [platform] connects it to the app store and ads; without one, the paid parts stay hidden. */
class Pawtillery(val platform: Platform = OfflinePlatform()) : Game() {
    lateinit var settings: Settings
        private set
    lateinit var assets: Assets
        private set
    lateinit var shapes: ShapeRenderer
        private set
    lateinit var batch: SpriteBatch
        private set

    override fun create() {
        Gdx.input.setCatchKey(Input.Keys.BACK, true)
        settings = Settings()
        assets = Assets().apply { load() }
        shapes = ShapeRenderer(8000)
        batch = SpriteBatch()
        // A phone that was reset or a new install gets Plus back without visiting the Shop.
        if (!settings.plus) platform.restorePurchases { owned -> if (owned) settings.plus = true }
        setScreen(MenuScreen(this))
    }

    fun sfx(name: String, volume: Float = 1f, pitch: Float = 1f) {
        if (settings.sound) assets.play(name, volume, pitch)
    }

    fun buzz(milliseconds: Int) {
        if (settings.vibration) Gdx.input.vibrate(milliseconds)
    }

    private var wantedMusic: Music? = null

    /** Plays [track] (or nothing) as background music, respecting the Music setting. */
    fun playMusic(track: Music?) {
        wantedMusic = track
        for (m in listOf(assets.menuMusic, assets.matchMusic)) if (m !== track || !settings.music) m.stop()
        if (track != null && settings.music && !track.isPlaying) track.play()
    }

    /** Applies a change to the Music setting right away. */
    fun refreshMusic() = playMusic(wantedMusic)

    /** Shows [next] and frees the screen it replaces. */
    fun switchTo(next: Screen) {
        val old = screen
        setScreen(next)
        old?.dispose()
    }

    override fun dispose() {
        screen?.dispose()
        assets.dispose()
        shapes.dispose()
        batch.dispose()
    }

    companion object {
        /** Keep in step with versionName in android/build.gradle.kts. */
        const val VERSION = "1.0.0"
    }
}
