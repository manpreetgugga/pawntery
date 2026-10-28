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
import com.pawtillery.game.ComputerLevel
import com.pawtillery.game.GameScreen
import com.pawtillery.game.ItemType
import com.pawtillery.game.MatchConfig
import com.pawtillery.game.MatchSetup
import com.pawtillery.game.MenuScreen
import com.pawtillery.game.Updater
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
    private val game = Pawtillery(DemoPlatform())
    private var time = 0f
    private var next = 0
    private val steps = ArrayList<Pair<Float, () -> Unit>>()
    /** Actions that each wait for the person's next turn to aim. */
    private val myTurns = ArrayDeque<() -> Unit>()

    private val screen: BaseScreen get() = game.screen as BaseScreen

    private fun at(t: Float, action: () -> Unit) {
        steps += t to action
    }

    private fun tap(id: String) {
        val p = screen.buttonOnScreen(id) ?: error("No button '$id' on ${screen::class.simpleName}")
        Gdx.input.inputProcessor.touchDown(p.x.toInt(), p.y.toInt(), 0, Input.Buttons.LEFT)
        Gdx.input.inputProcessor.touchUp(p.x.toInt(), p.y.toInt(), 0, Input.Buttons.LEFT)
    }

    /** Aims and throws like a sharp player would, by setting the aim through the angle/power buttons' state. */
    private fun throwComputerShot() {
        val gs = game.screen as? GameScreen ?: return
        gs.devAutoThrow()
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
        // Trick Shots: the level list, a level, and its result.
        at(62.2f) { game.switchTo(MenuScreen(game)) }
        at(62.8f) { tap("tricks") }
        at(63.4f) { shot("18-trick-shot-levels") }
        at(63.5f) { tap("level:1") }
        at(64.6f) { shot("19-level-1") }
        at(64.7f) { throwComputerShot() }
        at(68.5f) { throwComputerShot() }
        at(72.5f) { throwComputerShot() }
        at(76.5f) { throwComputerShot() }
        at(80.5f) { throwComputerShot() }
        at(84.5f) { shot("20-level-result") }
        at(84.6f) { tap("double_ad") }
        at(85f) { shot("21-ad-loading") }
        at(87f) { shot("22-ad-doubled") }
        at(87.1f) { game.switchTo(MenuScreen(game)) }
        at(87.7f) { tap("shop") }
        at(88.3f) { shot("23-shop") }
        at(88.4f) { tap("buy") }
        at(88.6f) { tap("gate:4"); tap("gate:2") }
        at(88.8f) { shot("24-parent-gate") }
        at(88.9f) { tap("gate:cancel") }
        at(89.0f) { game.switchTo(MenuScreen(game)) }
        at(89.6f) { tap("settings") }
        at(90.2f) { shot("25-settings") }
        at(90.3f) { tap("reset") }
        at(90.6f) { shot("26-reset-confirm") }
        at(90.7f) { tap("reset_no") }
        at(90.8f) { game.switchTo(MenuScreen(game)) }
        at(91.4f) { shot("27-menu-after") }
        at(91.5f) { tap("daily") }
        at(92.6f) { shot("28-daily") }
        at(92.7f) { throwComputerShot() }
        at(96.7f) { throwComputerShot() }
        at(100.7f) { throwComputerShot() }
        at(104.7f) { throwComputerShot() }
        at(108.7f) { throwComputerShot() }
        at(112.7f) { throwComputerShot() }
        at(116.7f) { throwComputerShot() }
        at(121f) { shot("29-daily-result") }
        at(121.2f) { game.switchTo(MenuScreen(game)) }
        at(121.8f) { tap("trophies") }
        at(122.4f) { shot("30-trophies") }
        // Destruction and power-ups: a Pro match against the Champion, one turn at a time.
        at(122.6f) {
            game.settings.easyMode = false
            game.switchTo(GameScreen(game, MatchSetup(PetKind.DOG, PetKind.CAT, setOf(Side.RIGHT), MatchConfig.FULL_MATCH, ComputerLevel.CHAMPION)))
        }
        at(123.6f) { tap("auto") }
        at(125.8f) { tap("ready") }
        myTurns += {
            shot("31-power-ups")
            tap("power:MEGA")
            tap("item:BOWLING")
            at(time + 0.2f) { shot("32-mega-bowling") }
            at(time + 0.3f) { autoThrow(ItemType.BOWLING) }
            for (i in 0 until 10) at(time + 0.6f + i * 0.3f) { shot("33-smash-$i") }
        }
        myTurns += {
            tap("item:PAINT_BOMB")
            at(time + 0.2f) { autoThrow(ItemType.PAINT_BOMB) }
            for (i in 0 until 14) at(time + 0.5f + i * 0.2f) { shot("34-bomb-$i") }
        }
        myTurns += {
            tap("power:TRIPLE")
            tap("item:FIREWORK")
            at(time + 0.2f) { shot("35-triple-firework") }
            at(time + 0.3f) { autoThrow(ItemType.FIREWORK) }
            for (i in 0 until 10) at(time + 0.6f + i * 0.25f) { shot("36-fireworks-$i") }
        }
        myTurns += {
            tap("power:UMBRELLA")
            tap("item:MELON")
            at(time + 0.3f) { shot("37-umbrella") }
            at(time + 0.4f) { autoThrow(ItemType.MELON) }
            for (i in 0 until 8) at(time + 0.7f + i * 0.25f) { shot("38-melon-$i") }
        }
        myTurns += {
            shot("39-later")
            at(time + 0.2f) { updateTour() }
        }
    }

    /** Test builds updating themselves, with a pretend GitHub. */
    private fun updateTour() {
        val dir = File(outDir, "update-demo").apply { deleteRecursively() }
        game.updater = Updater(DemoSelfUpdate(dir), DemoFetch())
        game.switchTo(MenuScreen(game))
        at(time + 1.2f) { shot("40-update-downloading") }
        at(time + 3.0f) { shot("41-update-ready") }
        at(time + 3.1f) { tap("update_install") }
        at(time + 3.3f) { shot("42-update-install-tapped") }
        at(time + 3.4f) { tap("update_later") }
        at(time + 3.7f) { shot("43-update-later") }
        at(time + 3.8f) { tap("settings") }
        at(time + 4.4f) { shot("44-settings-update") }
        at(time + 4.5f) { dir.deleteRecursively(); Gdx.app.exit() }
    }

    private fun autoThrow(item: ItemType) {
        (game.screen as? GameScreen)?.devAutoThrow(item)
    }

    private var lastTurnAction = 0f

    override fun render() {
        game.render()
        time += min(Gdx.graphics.deltaTime, 1f / 20f)
        while (next < steps.size && steps[next].first <= time) {
            steps[next].second()
            next++
        }
        val gs = game.screen as? GameScreen
        if (time > 123f && gs != null && gs.isHumanAiming && myTurns.isNotEmpty() && time - lastTurnAction > 1.5f) {
            lastTurnAction = time
            myTurns.removeFirst()()
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
