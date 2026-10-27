package com.pawtillery.game.lwjgl3

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.BufferUtils
import com.badlogic.gdx.utils.ScreenUtils
import com.pawtillery.game.BaseScreen
import com.pawtillery.game.Pawtillery
import java.io.File
import kotlin.math.min

/**
 * Plays a scripted session through real touch input and saves screenshots, so the look of every
 * screen can be checked without a phone: ./gradlew :lwjgl3:tour --args="out-dir 1280 720"
 */
class ScreenshotTour(private val outDir: File) : ApplicationAdapter() {
    private val game = Pawtillery()
    private var time = 0f
    private var next = 0
    private val steps = ArrayList<Pair<Float, () -> Unit>>()

    private val screen: BaseScreen get() = game.screen as BaseScreen

    private fun at(t: Float, action: () -> Unit) {
        steps += t to action
    }

    private fun tap(id: String) {
        val p = screen.buttonOnScreen(id) ?: error("No button '$id' on ${screen::class.simpleName}")
        Gdx.input.inputProcessor.touchDown(p.x.toInt(), p.y.toInt(), 0, Input.Buttons.LEFT)
        Gdx.input.inputProcessor.touchUp(p.x.toInt(), p.y.toInt(), 0, Input.Buttons.LEFT)
    }

    private fun down(p: Vector2) = Gdx.input.inputProcessor.touchDown(p.x.toInt(), p.y.toInt(), 0, Input.Buttons.LEFT)

    private fun drag(p: Vector2) = Gdx.input.inputProcessor.touchDragged(p.x.toInt(), p.y.toInt(), 0)

    private fun up(p: Vector2) = Gdx.input.inputProcessor.touchUp(p.x.toInt(), p.y.toInt(), 0, Input.Buttons.LEFT)

    override fun create() {
        outDir.mkdirs()
        game.create()
        at(1.2f) { shot("01-menu") }
        at(1.3f) { tap("help") }
        at(1.6f) { shot("02-help") }
        at(1.7f) { tap("help_close") }
        at(1.8f) { tap("play_ai") }
        at(3.0f) { shot("03-build-start") }
        at(3.1f) { tap("piece:CRATE") }
        at(3.2f) { down(screen.worldOnScreen(11f, 8f)) }
        at(3.3f) { drag(screen.worldOnScreen(10.4f, 8f)) }
        at(3.4f) { shot("04-build-ghost") }
        at(3.5f) { up(screen.worldOnScreen(10.4f, 8f)) }
        at(3.7f) { tap("auto") }
        at(5.8f) { shot("05-build-auto") }
        at(5.9f) { tap("ready") }
        at(7.2f) { shot("06-computer-building") }
        at(11f) { shot("07-your-turn") }
        at(11.1f) { down(screen.uiOnScreen(800f, 450f)) }
        at(11.2f) { drag(screen.uiOnScreen(700f, 380f)) }
        at(11.3f) { drag(screen.uiOnScreen(590f, 300f)) }
        at(11.4f) { shot("08-aiming") }
        at(11.5f) { up(screen.uiOnScreen(590f, 300f)) }
        at(12.1f) { shot("09-flight") }
        at(15.6f) { shot("10-computer-aiming") }
        at(19f) { shot("11-after-computer") }
        at(19.1f) { tap("pause") }
        at(19.3f) { shot("12-paused") }
        at(19.4f) { Gdx.app.exit() }
    }

    override fun render() {
        game.render()
        time += min(Gdx.graphics.deltaTime, 1f / 20f)
        while (next < steps.size && steps[next].first <= time) {
            steps[next].second()
            next++
        }
    }

    override fun resize(width: Int, height: Int) = game.resize(width, height)

    override fun dispose() = game.dispose()

    private fun shot(name: String) {
        val w = Gdx.graphics.backBufferWidth
        val h = Gdx.graphics.backBufferHeight
        val pixels = ScreenUtils.getFrameBufferPixels(0, 0, w, h, true)
        for (i in 3 until pixels.size step 4) pixels[i] = 255.toByte()
        val pixmap = Pixmap(w, h, Pixmap.Format.RGBA8888)
        BufferUtils.copy(pixels, 0, pixmap.pixels, pixels.size)
        PixmapIO.writePNG(Gdx.files.absolute(File(outDir, "$name.png").absolutePath), pixmap)
        pixmap.dispose()
        println("screenshot $name")
    }
}

fun main(args: Array<String>) {
    val out = File(args.getOrElse(0) { "tour" })
    val config = Lwjgl3ApplicationConfiguration().apply {
        setTitle("Pawtillery tour")
        setWindowedMode(args.getOrNull(1)?.toInt() ?: 1280, args.getOrNull(2)?.toInt() ?: 720)
        setForegroundFPS(60)
    }
    Lwjgl3Application(ScreenshotTour(out), config)
}
