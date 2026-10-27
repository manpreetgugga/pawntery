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
import com.pawtillery.game.GameScreen
import com.pawtillery.game.MatchSetup
import com.pawtillery.game.PetKind
import com.pawtillery.game.Side
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
        at(2.3f) { shot("03-pets") }
        at(2.4f) { tap("pet:PARROT") }
        at(2.6f) { shot("04-unlock-offer") }
        at(2.7f) { tap("offer_close") }
        at(2.8f) { tap("pet:DOG") }
        at(2.9f) { tap("go") }
        at(4.0f) { shot("05-build-start") }
        at(4.1f) { tap("piece:CRATE") }
        at(4.2f) { down(screen.worldOnScreen(11f, 8f)) }
        at(4.3f) { drag(screen.worldOnScreen(10.4f, 8f)) }
        at(4.4f) { shot("06-build-ghost") }
        at(4.5f) { up(screen.worldOnScreen(10.4f, 8f)) }
        at(4.7f) { tap("auto") }
        at(6.8f) { shot("07-build-auto") }
        at(6.9f) { tap("ready") }
        at(8.2f) { shot("08-computer-building") }
        at(11.6f) { tap("angle+"); tap("angle+"); tap("power+") }
        at(12f) { shot("09-your-turn") }
        at(12.1f) { down(screen.uiOnScreen(800f, 450f)) }
        at(12.2f) { drag(screen.uiOnScreen(700f, 380f)) }
        at(12.3f) { drag(screen.uiOnScreen(590f, 300f)) }
        at(12.4f) { shot("10-aiming") }
        at(12.5f) { up(screen.uiOnScreen(590f, 300f)) }
        at(13.1f) { shot("11-flight") }
        at(16.6f) { shot("12-computer-aiming") }
        at(20f) { shot("13-after-computer") }
        at(20.1f) { tap("pause") }
        at(20.3f) { shot("14-paused") }
        // A short computer-vs-computer match with the unlockable pets, played to the end.
        at(20.4f) {
            game.switchTo(GameScreen(game, MatchSetup(PetKind.BUNNY, PetKind.TURTLE, setOf(Side.LEFT, Side.RIGHT), 2)))
        }
        at(27f) { shot("15-bunny-vs-turtle") }
        at(30f) { shot("16-bunny-vs-turtle-later") }
        at(62f) { shot("17-game-over") }
        at(62.1f) { Gdx.app.exit() }
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
