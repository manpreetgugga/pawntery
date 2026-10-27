package com.pawtillery.game.lwjgl3

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.utils.BufferUtils
import com.badlogic.gdx.utils.ScreenUtils
import com.pawtillery.game.Arena
import com.pawtillery.game.Art
import com.pawtillery.game.Assets
import com.pawtillery.game.Decal
import com.pawtillery.game.ItemType
import com.pawtillery.game.Palette
import com.pawtillery.game.PetKind
import com.pawtillery.game.PieceType
import com.pawtillery.game.Ui
import java.io.File

/**
 * Renders the Play Store icon, the Android launcher icon layers and the feature graphic from the
 * game's own drawing code: ./gradlew :lwjgl3:storeAssets --args="<repo root>"
 */
class StoreAssets(private val root: File) : ApplicationAdapter() {
    private lateinit var shapes: ShapeRenderer
    private lateinit var batch: SpriteBatch
    private lateinit var assets: Assets

    override fun create() {
        shapes = ShapeRenderer(8000)
        batch = SpriteBatch()
        assets = Assets().apply { load() }

        render(File(root, "store/icon-512.png"), 512, 512, opaque = true) { icon(it, background = true) }
        val launcher = mapOf("mdpi" to 108, "hdpi" to 162, "xhdpi" to 216, "xxhdpi" to 324, "xxxhdpi" to 432)
        for ((density, size) in launcher) {
            render(File(root, "android/res/mipmap-$density/ic_launcher_foreground.png"), size, size, opaque = false) {
                icon(it, background = false)
            }
        }
        render(File(root, "store/feature-graphic-1024x500.png"), 1024, 500, opaque = true) { featureGraphic() }
        Gdx.app.exit()
    }

    /** Draws into an offscreen buffer of [w] x [h] pixels and saves it as a PNG. */
    private fun render(file: File, w: Int, h: Int, opaque: Boolean, draw: (OrthographicCamera) -> Unit) {
        val fbo = FrameBuffer(Pixmap.Format.RGBA8888, w, h, false)
        fbo.begin()
        Gdx.gl.glViewport(0, 0, w, h)
        Gdx.gl.glClearColor(0f, 0f, 0f, 0f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        Gdx.gl.glEnable(GL20.GL_BLEND)
        // Separate alpha blending keeps transparent pixels transparent in the saved image.
        Gdx.gl.glBlendFuncSeparate(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA)
        val cam = OrthographicCamera(w.toFloat(), h.toFloat())
        draw(cam)
        val pixels = ScreenUtils.getFrameBufferPixels(0, 0, w, h, true)
        fbo.end()
        fbo.dispose()
        if (opaque) for (i in 3 until pixels.size step 4) pixels[i] = 255.toByte()
        val pixmap = Pixmap(w, h, Pixmap.Format.RGBA8888)
        BufferUtils.copy(pixels, 0, pixmap.pixels, pixels.size)
        file.parentFile.mkdirs()
        PixmapIO.writePNG(Gdx.files.absolute(file.absolutePath), pixmap)
        pixmap.dispose()
        println("wrote ${file.relativeTo(root)}")
    }

    /** The Dog, mid-splat, on a sunny gold background. Without [background] it fits the adaptive icon safe zone. */
    private fun icon(cam: OrthographicCamera, background: Boolean) {
        // A 3 x 3 meter square; the adaptive-icon foreground keeps to the central 61% (66 of 108 dp).
        val size = 3f
        cam.setToOrtho(false, size, size)
        cam.position.set(0f, 0f, 0f)
        cam.update()
        val s = shapes
        s.projectionMatrix = cam.combined
        s.identity()
        s.begin(ShapeRenderer.ShapeType.Filled)
        if (background) {
            s.color = Palette.GOLD
            s.rect(-1.5f, -1.5f, 3f, 3f)
            s.color = Color(1f, 0.92f, 0.55f, 0.55f)
            for (i in 0 until 12 step 2) {
                val a0 = i * 30f
                s.triangle(0f, -0.2f, MathUtils.cosDeg(a0) * 3f, MathUtils.sinDeg(a0) * 3f - 0.2f, MathUtils.cosDeg(a0 + 30f) * 3f, MathUtils.sinDeg(a0 + 30f) * 3f - 0.2f)
            }
        }
        val k = if (background) 1.75f else 1.12f
        s.translate(-0.05f, -0.1f, 0f)
        s.scale(k, k, 1f)
        Art.petOutlined(s, PetKind.DOG, 0.3f, false, false, 0f)
        Art.splat(s, Decal(0.22f, 0.44f, 0.15f, ItemType.MUD, 0x2A5F3))
        s.identity()
        // A mud ball flying in from the top right, with speed lines.
        val bx = if (background) 0.95f else 0.6f
        val by = if (background) 0.95f else 0.6f
        val r = if (background) 0.22f else 0.15f
        if (background) {
            s.color = Palette.INK
            s.rectLine(bx + r * 1.2f, by + r * 0.6f, bx + r * 3.2f, by + r * 1.6f, r * 0.25f)
            s.rectLine(bx + r * 1.4f, by - r * 0.2f, bx + r * 3.0f, by + r * 0.5f, r * 0.2f)
        }
        s.translate(bx, by, 0f)
        Art.itemShape(s, ItemType.MUD, r)
        s.identity()
        s.end()
    }

    /** The backyard with both pets, a fort, a mud ball in flight and the title. */
    private fun featureGraphic() {
        val cam = OrthographicCamera()
        // A closer view than in the game, so the pets read at store-thumbnail size.
        val left = 4f
        val worldW = 24f
        val worldH = worldW * 500f / 1024f
        val bottom = 0.3f
        cam.setToOrtho(false, worldW, worldH)
        cam.position.set(left + worldW / 2f, bottom + worldH / 2f, 0f)
        cam.update()
        val s = shapes
        s.projectionMatrix = cam.combined
        s.identity()
        s.begin(ShapeRenderer.ShapeType.Filled)
        Art.background(s, left, left + worldW, bottom + worldH, 2f, 0.8f)
        Art.hedges(s, left, left + worldW)
        Art.fence(s)
        Art.ground(s, left, left + worldW)
        fun piece(type: PieceType, x: Float, y: Float) {
            s.identity(); s.translate(x, y, 0f); Art.pieceShape(s, type, 1f); s.identity()
        }
        piece(PieceType.CRATE, 9.4f, Arena.GROUND_Y + 0.6f)
        piece(PieceType.CRATE, 9.4f, Arena.GROUND_Y + 1.8f)
        piece(PieceType.BOX, 10.7f, Arena.GROUND_Y + 0.5f)
        piece(PieceType.GNOME, 23.0f, Arena.GROUND_Y + 0.65f)
        piece(PieceType.POT, 24.0f, Arena.GROUND_Y + 0.45f)
        piece(PieceType.TIRE, 27.4f, Arena.GROUND_Y + 0.6f)
        fun pet(kind: PetKind, x: Float, dir: Float, hit: Boolean) {
            s.identity()
            s.translate(x, Arena.GROUND_Y + Arena.PET_H / 2f, 0f)
            s.scale(dir * Arena.PET_ART_SCALE * 1.15f, Arena.PET_ART_SCALE * 1.15f, 1f)
            Art.petOutlined(s, kind, 0.4f, hit, false, 0f)
            if (hit) Art.splat(s, Decal(0.15f, 0.25f, 0.16f, ItemType.MUD, 0x51C2))
            s.identity()
        }
        pet(PetKind.DOG, 6.6f, 1f, false)
        pet(PetKind.CAT, 25.4f, -1f, true)
        // The throw's arc, dotted, ending in the mud ball about to land on the cat
        fun arcY(t: Float) = Arena.GROUND_Y + 2.6f + 5.6f * 4f * t * (1f - t) - 1.2f * t
        s.color = Color(1f, 1f, 1f, 0.9f)
        for (i in 1 until 30) {
            val t = i / 40f
            s.circle(MathUtils.lerp(7f, 25.2f, t), arcY(t), 0.08f, 10)
        }
        s.translate(MathUtils.lerp(7f, 25.2f, 0.82f), arcY(0.82f), 0f)
        Art.itemShape(s, ItemType.MUD, 0.38f)
        s.identity()
        s.end()

        // Title over the sky
        val ui = OrthographicCamera()
        ui.setToOrtho(false, 1024f, 500f)
        s.projectionMatrix = ui.combined
        s.begin(ShapeRenderer.ShapeType.Filled)
        s.color = Palette.SHADE
        Ui.roundRect(s, 512f - 280f, 296f, 560f, 48f, 24f)
        s.end()
        batch.projectionMatrix = ui.combined
        batch.begin()
        Ui.text(batch, assets.title, "Pawtillery", 512f, 410f, Palette.WHITE)
        Ui.text(batch, assets.body, "Build a fort. Splat the neighbor's pet.", 512f, 320f, Palette.WHITE)
        batch.end()
    }

    override fun dispose() {
        shapes.dispose()
        batch.dispose()
        assets.dispose()
    }
}

fun main(args: Array<String>) {
    val root = File(args.getOrElse(0) { ".." }).absoluteFile
    val config = Lwjgl3ApplicationConfiguration().apply {
        setTitle("Pawtillery store assets")
        setWindowedMode(1600, 900)
    }
    Lwjgl3Application(StoreAssets(root), config)
}
