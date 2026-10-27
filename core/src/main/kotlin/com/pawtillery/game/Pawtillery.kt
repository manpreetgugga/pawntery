package com.pawtillery.game

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer

class Pawtillery : Game() {
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
        setScreen(MenuScreen(this))
    }

    fun sfx(name: String, volume: Float = 1f, pitch: Float = 1f) {
        if (settings.sound) assets.play(name, volume, pitch)
    }

    fun buzz(milliseconds: Int) {
        if (settings.sound) Gdx.input.vibrate(milliseconds)
    }

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
}
