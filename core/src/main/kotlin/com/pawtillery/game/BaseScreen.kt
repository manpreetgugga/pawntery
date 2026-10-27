package com.pawtillery.game

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.viewport.ExtendViewport

/**
 * Two cameras over the same screen: the backyard in meters (at least 32 x 18) and the interface in
 * UI units (at least 1600 x 900). Both extend on wider or taller phones instead of letterboxing.
 */
abstract class BaseScreen(protected val game: Pawtillery) : ScreenAdapter() {
    protected val worldCam = OrthographicCamera()
    protected val worldView = ExtendViewport(Arena.WIDTH, Arena.HEIGHT, worldCam)
    protected val uiCam = OrthographicCamera()
    protected val uiView = ExtendViewport(Assets.UI_WIDTH, Assets.UI_HEIGHT, uiCam)
    protected val shapes: ShapeRenderer get() = game.shapes
    protected val batch: SpriteBatch get() = game.batch
    protected var buttons: List<Button> = emptyList()
    protected var pressedId: String? = null
    private val touchUi = Vector2()
    private val touchWorld = Vector2()

    protected val uiW: Float get() = uiView.worldWidth
    protected val uiH: Float get() = uiView.worldHeight
    protected val viewLeft: Float get() = worldCam.position.x - worldView.worldWidth / 2f
    protected val viewRight: Float get() = worldCam.position.x + worldView.worldWidth / 2f
    protected val viewTop: Float get() = worldView.worldHeight

    private val input = object : InputAdapter() {
        override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            if (pointer > 0) return false
            val ui = uiView.unproject(touchUi.set(screenX.toFloat(), screenY.toFloat()))
            val hit = buttons.firstOrNull { it.enabled && it.contains(ui.x, ui.y) }
            if (hit != null) {
                pressedId = hit.id
                return true
            }
            onPress(ui, worldView.unproject(touchWorld.set(screenX.toFloat(), screenY.toFloat())))
            return true
        }

        override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
            if (pointer > 0 || pressedId != null) return pointer == 0
            val ui = uiView.unproject(touchUi.set(screenX.toFloat(), screenY.toFloat()))
            onDrag(ui, worldView.unproject(touchWorld.set(screenX.toFloat(), screenY.toFloat())))
            return true
        }

        override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            if (pointer > 0) return false
            val ui = uiView.unproject(touchUi.set(screenX.toFloat(), screenY.toFloat()))
            val pressed = pressedId
            if (pressed != null) {
                pressedId = null
                val b = buttons.firstOrNull { it.id == pressed }
                if (b != null && b.enabled && b.contains(ui.x, ui.y)) {
                    game.sfx("click", 0.7f)
                    onButton(b.id)
                }
                return true
            }
            onRelease(ui, worldView.unproject(touchWorld.set(screenX.toFloat(), screenY.toFloat())))
            return true
        }

        override fun keyDown(keycode: Int): Boolean = onKey(keycode)
    }

    override fun show() {
        Gdx.input.inputProcessor = input
    }

    override fun resize(width: Int, height: Int) {
        worldView.update(width, height, false)
        worldCam.position.set(Arena.WIDTH / 2f, worldView.worldHeight / 2f, 0f)
        worldCam.update()
        uiView.update(width, height, true)
    }

    protected fun clear() {
        Gdx.gl.glClearColor(Palette.SKY_BOTTOM.r, Palette.SKY_BOTTOM.g, Palette.SKY_BOTTOM.b, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
    }

    protected fun beginWorld() {
        worldView.apply()
        shapes.projectionMatrix = worldCam.combined
        shapes.identity()
        shapes.begin(ShapeRenderer.ShapeType.Filled)
    }

    protected fun beginUiShapes() {
        uiView.apply()
        shapes.projectionMatrix = uiCam.combined
        shapes.identity()
        shapes.begin(ShapeRenderer.ShapeType.Filled)
    }

    protected fun beginUiText() {
        uiView.apply()
        batch.projectionMatrix = uiCam.combined
        batch.begin()
    }

    /** Maps a point in the backyard to UI coordinates (both views always cover the whole screen). */
    protected fun worldToUi(x: Float, y: Float, out: Vector2): Vector2 {
        val ux = (x - viewLeft) / worldView.worldWidth * uiW
        val uy = (y - (worldCam.position.y - worldView.worldHeight / 2f)) / worldView.worldHeight * uiH
        return out.set(ux, uy)
    }

    /** Draws an icon in UI space by scaling world-meter art: [draw] works in meters around (0, 0). */
    protected fun icon(cx: Float, cy: Float, metersToUi: Float, draw: () -> Unit) {
        shapes.identity()
        shapes.translate(cx, cy, 0f)
        shapes.scale(metersToUi, metersToUi, 1f)
        draw()
        shapes.identity()
    }

    open fun onPress(ui: Vector2, world: Vector2) = Unit

    open fun onDrag(ui: Vector2, world: Vector2) = Unit

    open fun onRelease(ui: Vector2, world: Vector2) = Unit

    open fun onButton(id: String) = Unit

    open fun onKey(keycode: Int): Boolean = false
}
