package com.pawtillery.game

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Music
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.badlogic.gdx.utils.Disposable
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Fonts and sounds. Fonts are rasterized at the device's real pixel size and scaled back to the
 * 1600x900 UI coordinate space, so text stays sharp on every phone.
 */
class Assets : Disposable {
    lateinit var title: BitmapFont
    lateinit var h1: BitmapFont
    lateinit var h2: BitmapFont
    /** Like h2 but outlined, for text drawn over the backyard. */
    lateinit var label: BitmapFont
    lateinit var body: BitmapFont
    lateinit var small: BitmapFont
    private val sounds = HashMap<String, Sound>()
    lateinit var menuMusic: Music
    lateinit var matchMusic: Music

    fun load() {
        val scale = max(0.5f, minOf(Gdx.graphics.width / UI_WIDTH, Gdx.graphics.height / UI_HEIGHT))
        val display = "fonts/TitanOne-Regular.ttf"
        val text = "fonts/VarelaRound-Regular.ttf"
        title = font(display, 108, scale, border = 5f)
        h1 = font(display, 56, scale, border = 3.5f)
        h2 = font(display, 38, scale)
        label = font(display, 40, scale, border = 3f)
        body = font(text, 30, scale)
        small = font(text, 23, scale)
        for (name in SOUND_NAMES) sounds[name] = Gdx.audio.newSound(Gdx.files.internal("sounds/$name.wav"))
        menuMusic = loop("music/menu.ogg")
        matchMusic = loop("music/match.ogg")
    }

    fun play(name: String, volume: Float = 1f, pitch: Float = 1f) {
        sounds[name]?.play(volume.coerceIn(0f, 1f), pitch.coerceIn(0.5f, 2f), 0f)
    }

    private fun loop(path: String): Music = Gdx.audio.newMusic(Gdx.files.internal(path)).apply {
        isLooping = true
        volume = MUSIC_VOLUME
    }

    private fun font(file: String, size: Int, scale: Float, border: Float = 0f): BitmapFont {
        val generator = FreeTypeFontGenerator(Gdx.files.internal(file))
        val params = FreeTypeFontGenerator.FreeTypeFontParameter().apply {
            this.size = (size * scale).roundToInt().coerceAtLeast(8)
            color = Color.WHITE
            if (border > 0f) {
                borderWidth = border * scale
                borderColor = Palette.INK
                shadowOffsetY = (3f * scale).roundToInt().coerceAtLeast(1)
                shadowColor = Color(0f, 0f, 0f, 0.25f)
            }
            minFilter = Texture.TextureFilter.Linear
            magFilter = Texture.TextureFilter.Linear
            characters = FreeTypeFontGenerator.DEFAULT_CHARS + "∞←→"
        }
        val font = generator.generateFont(params)
        generator.dispose()
        font.data.setScale(1f / scale)
        font.setUseIntegerPositions(false)
        return font
    }

    override fun dispose() {
        listOf(title, h1, h2, label, body, small).forEach { it.dispose() }
        sounds.values.forEach { it.dispose() }
        menuMusic.dispose()
        matchMusic.dispose()
    }

    companion object {
        const val UI_WIDTH = 1600f
        const val UI_HEIGHT = 900f
        const val MUSIC_VOLUME = 0.45f
        private val SOUND_NAMES = listOf(
            "throw", "splat", "thud", "bonk", "pop", "place", "click", "win", "boing", "woof", "meow", "squeak", "chirp",
        )
    }
}
