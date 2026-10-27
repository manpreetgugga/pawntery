package com.pawtillery.game

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import kotlin.math.abs

object Palette {
    private fun c(hex: String): Color = Color.valueOf(hex)

    val SKY_TOP = c("4FB0EE")
    val SKY_BOTTOM = c("CDEFFC")
    val SUN = c("FFE27A")
    val SUN_HALO = c("FFF1B340")
    val CLOUD = c("F7FCFF")
    val HILL_FAR = c("A9DC93")
    val HILL_NEAR = c("8DCB74")
    val TREE = c("5DA94A")
    val TRUNK = c("8A5A35")
    val GRASS = c("6CBF45")
    val GRASS_DARK = c("4E9A32")
    val GRASS_TUFT = c("82D158")
    val LAWN_STRIPE = c("56A33A")
    val CLOUD_SHADE = c("D9E8F2")
    val HEDGE = c("3E8E41")
    val HEDGE_LIGHT = c("4FA650")
    val FENCE = c("C08250")
    val FENCE_DARK = c("8C5A30")
    val PICKET = c("FFFFFFD9")
    val INK = c("3B2A1A")
    val WHITE = c("FFFFFF")
    val MOUTH = c("7A2E2E")
    val PINK = c("F58FA8")

    val DOG_FUR = c("C98A4B")
    val DOG_DARK = c("7A4E2A")
    val DOG_LIGHT = c("EFCB9C")
    val COLLAR = c("E53935")
    val CAT_FUR = c("F0A04B")
    val CAT_STRIPE = c("C8742A")
    val CAT_LIGHT = c("FBE3C4")
    val CAT_EYE = c("8BCF4F")

    val BUNNY_FUR = c("F4F1EC")
    val BUNNY_SHADE = c("CFC8BE")
    val TURTLE_SKIN = c("9CCC65")
    val TURTLE_SHELL = c("5E9A3A")
    val TURTLE_SHELL_DARK = c("3F6E25")
    val TURTLE_SPOT = c("8BC34A")
    val PARROT_RED = c("E53935")
    val PARROT_GREEN = c("43A047")
    val PARROT_BLUE = c("1E88E5")
    val BEAK = c("F9C23C")
    val CARROT = c("F57C00")
    val SEED = c("E2C27D")
    val SEED_DARK = c("A9864A")

    val MUD = c("6B4423")
    val MUD_DARK = c("4A2E16")
    val WATER = c("4FC3F7")
    val WATER_DROP = c("8FDBFFCC")
    val BOWLING = c("4B2E83")
    val BONE = c("F6EEDC")
    val GRAVY = c("A8582A")
    val HAIR = c("9E9E9E")
    val HAIR_DARK = c("757575")

    val BOX = c("D9A86C")
    val BOX_DARK = c("9C7442")
    val TAPE = c("EBD3A0")
    val CRATE = c("B0723D")
    val CRATE_DARK = c("6E4422")
    val PLANK = c("D39A5E")
    val PLANK_DARK = c("8F6235")
    val POT = c("D4683A")
    val POT_DARK = c("9A4523")
    val LEAF = c("4CAF50")
    val FLOWER = c("FF6FA8")
    val GNOME_BODY = c("3F7FD0")
    val GNOME_HAT = c("E53935")
    val GNOME_FACE = c("F7CBA4")
    val TIRE = c("2E2E2E")
    val TIRE_INNER = c("4A4A4A")
    val HUB = c("BDBDBD")

    val PANEL = c("FFF8EC")
    val PANEL_EDGE = c("E3CBA5")
    val ACCENT = c("FF7A3D")
    val ACCENT_DARK = c("C9541F")
    val TEAL = c("2BB0A0")
    val TEAL_DARK = c("1C7F73")
    val GOLD = c("FFC93C")
    val GOLD_DARK = c("D69A12")
    val GOOD = c("5DBB63")
    val BAD = c("E5533D")
    val SHADE = c("1E140C99")
    val ZONE = c("FFFFFF24")
}

/** Everything in the backyard is drawn from simple shapes, in meters, so it scales to any screen. */
object Art {
    private val tmpColor = Color()

    /** While true, every shape is drawn in the outline color: used to draw a pet's silhouette behind it. */
    private var silhouette = false

    private fun paint(s: ShapeRenderer, c: Color) {
        s.color = if (silhouette) Palette.INK else c
    }

    // ---- Scenery ----

    fun background(s: ShapeRenderer, left: Float, right: Float, top: Float, time: Float, wind: Float) {
        s.rect(left, Arena.GROUND_Y, right - left, top - Arena.GROUND_Y, Palette.SKY_BOTTOM, Palette.SKY_BOTTOM, Palette.SKY_TOP, Palette.SKY_TOP)
        val sunX = left + 4f
        val sunY = top - 3.2f
        paint(s, Palette.SUN_HALO)
        s.circle(sunX, sunY, 2.1f + MathUtils.sin(time * 1.3f) * 0.08f, 40)
        s.circle(sunX, sunY, 1.65f, 40)
        paint(s, Palette.SUN)
        s.circle(sunX, sunY, 1.25f, 40)

        val drift = 0.25f + abs(wind) * 0.35f
        val span = right - left + 10f
        for (i in 0 until 5) {
            val base = i * 9.3f + time * drift * (if (wind < 0f) -1f else 1f) * (0.6f + i * 0.15f)
            val x = left - 5f + ((base % span) + span) % span
            cloud(s, x, top - 2.2f - (i % 3) * 1.7f, 0.8f + (i % 2) * 0.35f)
        }

        paint(s, Palette.HILL_FAR)
        s.ellipse(left - 6f, Arena.GROUND_Y - 4f, 22f, 9f, 48)
        s.ellipse(right - 18f, Arena.GROUND_Y - 4.5f, 26f, 10f, 48)
        paint(s, Palette.HILL_NEAR)
        s.ellipse(8f, Arena.GROUND_Y - 3.5f, 18f, 6.5f, 48)
        tree(s, 10.5f, 1.0f)
        tree(s, 21.5f, 0.85f)
        tree(s, 3f, 0.75f)
        tree(s, 29.2f, 0.95f)

        // Low white picket fence along the back of both yards.
        paint(s, Palette.PICKET)
        val railY = Arena.GROUND_Y + 0.35f
        s.rect(left, railY, right - left, 0.12f)
        s.rect(left, railY + 0.5f, right - left, 0.12f)
        var x = left - (left % 0.55f) - 0.55f
        while (x < right) {
            s.rect(x, Arena.GROUND_Y, 0.26f, 1.05f)
            s.triangle(x, Arena.GROUND_Y + 1.05f, x + 0.26f, Arena.GROUND_Y + 1.05f, x + 0.13f, Arena.GROUND_Y + 1.25f)
            x += 0.55f
        }
    }

    private fun cloud(s: ShapeRenderer, x: Float, y: Float, k: Float) {
        paint(s, Palette.CLOUD_SHADE)
        s.circle(x, y - 0.14f * k, 0.9f * k, 24)
        s.circle(x + 0.9f * k, y + 0.2f * k, 1.1f * k, 24)
        s.circle(x + 1.9f * k, y - 0.14f * k, 0.85f * k, 24)
        s.rect(x, y - 0.99f * k, 1.9f * k, 0.85f * k)
        paint(s, Palette.CLOUD)
        s.circle(x, y, 0.9f * k, 24)
        s.circle(x + 0.9f * k, y + 0.35f * k, 1.1f * k, 24)
        s.circle(x + 1.9f * k, y, 0.85f * k, 24)
        s.rect(x, y - 0.85f * k, 1.9f * k, 0.85f * k)
    }

    private fun tree(s: ShapeRenderer, x: Float, k: Float) {
        paint(s, Palette.TRUNK)
        s.rect(x - 0.18f * k, Arena.GROUND_Y, 0.36f * k, 2.4f * k)
        paint(s, Palette.TREE)
        s.circle(x, Arena.GROUND_Y + 3f * k, 1.3f * k, 28)
        s.circle(x - 0.9f * k, Arena.GROUND_Y + 2.4f * k, 0.9f * k, 24)
        s.circle(x + 0.9f * k, Arena.GROUND_Y + 2.5f * k, 0.95f * k, 24)
    }

    fun ground(s: ShapeRenderer, left: Float, right: Float) {
        paint(s, Palette.GRASS_DARK)
        s.rect(left, Arena.VIEW_BOTTOM - 2f, right - left, Arena.GROUND_Y - Arena.VIEW_BOTTOM + 2f)
        paint(s, Palette.LAWN_STRIPE)
        var stripe = left - (left % 3.2f) - 3.2f
        while (stripe < right) {
            s.rect(stripe, Arena.VIEW_BOTTOM - 2f, 1.6f, Arena.GROUND_Y - Arena.VIEW_BOTTOM + 2f)
            stripe += 3.2f
        }
        paint(s, Palette.GRASS)
        s.rect(left, Arena.GROUND_Y - 0.4f, right - left, 0.4f)
        paint(s, Palette.GRASS_TUFT)
        var x = left - (left % 0.7f) - 0.7f
        var i = 0
        while (x < right) {
            val h = 0.14f + ((i * 37) % 5) * 0.03f
            s.triangle(x, Arena.GROUND_Y - 0.02f, x + 0.12f, Arena.GROUND_Y - 0.02f, x + 0.04f, Arena.GROUND_Y + h)
            s.triangle(x + 0.1f, Arena.GROUND_Y - 0.02f, x + 0.22f, Arena.GROUND_Y - 0.02f, x + 0.2f, Arena.GROUND_Y + h * 0.8f)
            x += 0.7f
            i++
        }
    }

    fun hedges(s: ShapeRenderer, left: Float, right: Float) {
        hedge(s, left - 0.5f, Arena.HEDGE_W)
        hedge(s, Arena.WIDTH - Arena.HEDGE_W, right + 0.5f)
    }

    private fun hedge(s: ShapeRenderer, x0: Float, x1: Float) {
        val top = Arena.HEDGE_TOP
        paint(s, Palette.HEDGE)
        s.rect(x0, Arena.GROUND_Y, x1 - x0, top - Arena.GROUND_Y - 0.4f)
        var x = x0 + 0.4f
        while (x < x1) {
            s.circle(x, top - 0.45f, 0.5f, 18)
            x += 0.7f
        }
        s.circle(x1, top - 0.45f, 0.5f, 18)
        paint(s, Palette.HEDGE_LIGHT)
        var y = Arena.GROUND_Y + 0.6f
        var row = 0
        while (y < top - 0.8f) {
            var bx = x0 + 0.3f + (row % 2) * 0.35f
            while (bx < x1 - 0.1f) {
                s.circle(bx, y, 0.16f, 10)
                bx += 0.7f
            }
            y += 0.6f
            row++
        }
    }

    fun fence(s: ShapeRenderer) {
        val hw = Arena.FENCE_HALF_W
        val x = Arena.FENCE_X - hw
        val h = Arena.FENCE_TOP - Arena.GROUND_Y
        paint(s, Palette.FENCE_DARK)
        s.rect(x - 0.05f, Arena.GROUND_Y, hw * 2f + 0.1f, h + 0.02f)
        s.triangle(x - 0.08f, Arena.FENCE_TOP, x + hw * 2f + 0.08f, Arena.FENCE_TOP, Arena.FENCE_X, Arena.FENCE_PEAK + 0.06f)
        paint(s, Palette.FENCE)
        s.rect(x, Arena.GROUND_Y, hw * 2f, h)
        s.triangle(x, Arena.FENCE_TOP, x + hw * 2f, Arena.FENCE_TOP, Arena.FENCE_X, Arena.FENCE_PEAK)
        paint(s, Palette.FENCE_DARK)
        s.rect(Arena.FENCE_X - 0.015f, Arena.GROUND_Y, 0.03f, h)
        var y = Arena.GROUND_Y + 0.9f
        while (y < Arena.FENCE_TOP - 0.3f) {
            s.rect(x, y, hw * 2f, 0.07f)
            s.circle(x + 0.12f, y + 0.035f, 0.035f, 8)
            s.circle(x + hw * 2f - 0.12f, y + 0.035f, 0.035f, 8)
            y += 1.4f
        }
    }

    fun groundDecals(s: ShapeRenderer, decals: List<Decal>) {
        for (d in decals) splat(s, d)
    }

    // ---- Pets ----

    /** Draws a pet in the yard; [lift] raises it for the little hop it does when throwing. */
    fun pet(s: ShapeRenderer, pet: Pet, time: Float, lift: Float = 0f) {
        val b = pet.body
        s.identity()
        s.translate(b.position.x, b.position.y + lift, 0f)
        s.rotate(0f, 0f, 1f, b.angle * MathUtils.radiansToDegrees)
        // Squash for a moment when hit.
        val squash = ((pet.hitTimer - 0.6f) / 0.3f).coerceIn(0f, 1f)
        val sx = Arena.PET_ART_SCALE * (1f + 0.12f * squash) * pet.side.dir
        val sy = Arena.PET_ART_SCALE * (1f - 0.14f * squash)
        s.translate(0f, -Arena.PET_H / 2f, 0f)
        s.scale(sx, sy, 1f)
        s.translate(0f, Arena.PET_H / 2f / Arena.PET_ART_SCALE, 0f)
        val blink = ((time + pet.side.ordinal * 1.7f) % 3.6f) < 0.12f
        petOutlined(s, pet.kind, time + pet.side.ordinal * 0.9f, pet.hitTimer > 0f, blink, pet.mess)
        s.identity()
        s.translate(b.position.x, b.position.y + lift, 0f)
        s.rotate(0f, 0f, 1f, b.angle * MathUtils.radiansToDegrees)
        for (d in pet.decals) splat(s, d)
        s.identity()
        stink(s, b.position.x, b.position.y + Arena.PET_H / 2f, pet.mess, time)
    }

    /** [petShape] with a dark outline: the same drawing, slightly larger and solid, goes behind it. */
    fun petOutlined(s: ShapeRenderer, kind: PetKind, t: Float, hit: Boolean, blink: Boolean, mess: Float) {
        silhouette = true
        s.scale(OUTLINE_GROW, OUTLINE_GROW, 1f)
        petShape(s, kind, t, hit, blink, mess)
        s.scale(1f / OUTLINE_GROW, 1f / OUTLINE_GROW, 1f)
        silhouette = false
        petShape(s, kind, t, hit, blink, mess)
    }

    private const val OUTLINE_GROW = 1.07f

    /** A soft shadow on the grass under something [halfWidth] wide whose bottom is at [bottom]. */
    fun shadow(s: ShapeRenderer, x: Float, halfWidth: Float, bottom: Float) {
        val h = bottom - Arena.GROUND_Y
        if (h > 8f || h < -0.5f) return
        val k = 1f - h.coerceAtLeast(0f) / 8f
        val w = halfWidth * 2f * (1.1f + (1f - k) * 0.6f)
        s.color = tmpColor.set(0f, 0f, 0f, 0.2f * k)
        s.ellipse(x - w / 2f, Arena.GROUND_Y - 0.13f, w, 0.26f, 20)
    }

    /** A pet drawn in its own coordinates, facing +x, centered in its 1.1 x 1.2 m body. */
    fun petShape(s: ShapeRenderer, kind: PetKind, t: Float, hit: Boolean, blink: Boolean, mess: Float) {
        val breathe = MathUtils.sin(t * 3f) * 0.012f
        when (kind) {
            PetKind.DOG -> dog(s, t, hit, blink, breathe)
            PetKind.CAT -> cat(s, t, hit, blink, breathe)
            PetKind.BUNNY -> bunny(s, t, hit, blink, breathe)
            PetKind.TURTLE -> turtle(s, t, hit, blink, breathe)
            PetKind.PARROT -> parrot(s, t, hit, blink, breathe)
        }
        if (mess > 60f && !hit) {
            // Grumpy brows once things get messy.
            paint(s, Palette.INK)
            s.rectLine(0.2f, 0.4f, 0.4f, 0.34f, 0.035f)
        }
    }

    private fun dog(s: ShapeRenderer, t: Float, hit: Boolean, blink: Boolean, breathe: Float) {
        val wag = MathUtils.sin(t * 9f) * 0.09f
        paint(s, Palette.DOG_DARK)
        s.rectLine(-0.4f, -0.22f, -0.64f, 0.06f + wag, 0.13f)
        s.circle(-0.64f, 0.06f + wag, 0.065f, 10)
        s.ellipse(-0.52f, -0.62f, 0.34f, 0.2f, 14)
        paint(s, Palette.DOG_FUR)
        s.ellipse(-0.56f, -0.58f, 0.9f, 0.62f + breathe, 24)
        paint(s, Palette.DOG_LIGHT)
        s.ellipse(-0.04f, -0.5f, 0.34f, 0.4f, 18)
        paint(s, Palette.DOG_FUR)
        s.rect(0.06f, -0.58f, 0.15f, 0.3f)
        s.rect(0.24f, -0.58f, 0.15f, 0.3f)
        paint(s, Palette.DOG_LIGHT)
        s.ellipse(0.03f, -0.62f, 0.2f, 0.11f, 10)
        s.ellipse(0.22f, -0.62f, 0.2f, 0.11f, 10)
        paint(s, Palette.DOG_FUR)
        s.circle(0.2f, 0.19f + breathe, 0.34f, 28)
        paint(s, Palette.DOG_LIGHT)
        s.ellipse(0.28f, -0.02f + breathe, 0.34f, 0.24f, 20)
        paint(s, Palette.INK)
        s.circle(0.58f, 0.13f + breathe, 0.065f, 12)
        if (hit) {
            paint(s, Palette.MOUTH)
            s.ellipse(0.38f, -0.07f, 0.15f, 0.11f, 12)
            paint(s, Palette.PINK)
            s.ellipse(0.41f, -0.08f, 0.08f, 0.06f, 10)
        }
        paint(s, Palette.DOG_DARK)
        s.ellipse(-0.08f, -0.02f + breathe, 0.22f, 0.44f, -14f, 16)
        eye(s, 0.31f, 0.29f + breathe, 0.075f, hit, blink, Palette.INK)
        paint(s, Palette.COLLAR)
        s.rectLine(-0.02f, -0.1f, 0.32f, -0.16f, 0.07f)
        paint(s, Palette.GOLD)
        s.circle(0.2f, -0.21f, 0.05f, 10)
    }

    private fun cat(s: ShapeRenderer, t: Float, hit: Boolean, blink: Boolean, breathe: Float) {
        val sway = MathUtils.sin(t * 2.2f) * 0.1f
        paint(s, Palette.CAT_FUR)
        s.rectLine(-0.4f, -0.45f, -0.63f, -0.14f, 0.12f)
        s.rectLine(-0.63f, -0.14f, -0.58f + sway, 0.26f, 0.11f)
        s.circle(-0.63f, -0.14f, 0.06f, 10)
        paint(s, Palette.CAT_STRIPE)
        s.circle(-0.58f + sway, 0.26f, 0.07f, 10)
        paint(s, Palette.CAT_FUR)
        s.ellipse(-0.55f, -0.6f, 0.86f, 0.6f + breathe, 24)
        paint(s, Palette.CAT_STRIPE)
        s.rectLine(-0.36f, -0.06f, -0.31f, -0.3f, 0.06f)
        s.rectLine(-0.2f, -0.02f, -0.16f, -0.27f, 0.06f)
        paint(s, Palette.CAT_LIGHT)
        s.ellipse(-0.04f, -0.52f, 0.32f, 0.38f, 16)
        paint(s, Palette.CAT_FUR)
        s.rect(0.05f, -0.58f, 0.14f, 0.28f)
        s.rect(0.22f, -0.58f, 0.14f, 0.28f)
        paint(s, Palette.CAT_LIGHT)
        s.ellipse(0.03f, -0.62f, 0.18f, 0.1f, 10)
        s.ellipse(0.2f, -0.62f, 0.18f, 0.1f, 10)
        val hy = 0.19f + breathe
        paint(s, Palette.CAT_FUR)
        s.triangle(-0.04f, hy + 0.1f, 0.04f, hy + 0.45f, 0.2f, hy + 0.24f)
        s.triangle(0.2f, hy + 0.26f, 0.38f, hy + 0.45f, 0.45f, hy + 0.1f)
        paint(s, Palette.PINK)
        s.triangle(0.02f, hy + 0.16f, 0.05f, hy + 0.36f, 0.14f, hy + 0.24f)
        s.triangle(0.26f, hy + 0.26f, 0.36f, hy + 0.37f, 0.39f, hy + 0.16f)
        paint(s, Palette.CAT_FUR)
        s.circle(0.2f, hy, 0.31f, 28)
        paint(s, Palette.CAT_LIGHT)
        s.ellipse(0.25f, hy - 0.22f, 0.3f, 0.2f, 16)
        catEye(s, 0.15f, hy + 0.06f, hit, blink)
        catEye(s, 0.34f, hy + 0.06f, hit, blink)
        paint(s, Palette.PINK)
        s.triangle(0.43f, hy - 0.08f, 0.53f, hy - 0.08f, 0.48f, hy - 0.14f)
        if (hit) {
            paint(s, Palette.MOUTH)
            s.ellipse(0.4f, hy - 0.26f, 0.12f, 0.09f, 12)
        }
        paint(s, Palette.INK)
        s.rectLine(0.44f, hy - 0.14f, 0.68f, hy - 0.08f, 0.015f)
        s.rectLine(0.44f, hy - 0.16f, 0.68f, hy - 0.2f, 0.015f)
    }

    /** The color that stands for each pet on panels and cards. */
    fun petColor(kind: PetKind): Color = when (kind) {
        PetKind.DOG -> Palette.TEAL
        PetKind.CAT -> Palette.ACCENT
        PetKind.BUNNY -> Palette.PINK
        PetKind.TURTLE -> Palette.GOLD
        PetKind.PARROT -> Palette.PARROT_BLUE
    }

    private fun bunny(s: ShapeRenderer, t: Float, hit: Boolean, blink: Boolean, breathe: Float) {
        val hy = 0.12f + breathe
        val wiggle = MathUtils.sin(t * 2.5f) * 5f
        paint(s, Palette.WHITE)
        s.circle(-0.5f, -0.3f, 0.13f, 14)
        paint(s, Palette.BUNNY_FUR)
        s.ellipse(-0.52f, -0.6f, 0.86f, 0.62f + breathe, 24)
        paint(s, Palette.BUNNY_SHADE)
        s.ellipse(-0.47f, -0.62f, 0.44f, 0.17f, 14)
        paint(s, Palette.BUNNY_FUR)
        s.ellipse(0.08f, -0.62f, 0.18f, 0.14f, 10)
        s.ellipse(0.24f, -0.62f, 0.18f, 0.14f, 10)
        // Ears
        s.ellipse(-0.08f, hy + 0.1f, 0.17f, 0.6f, 14f + wiggle, 16)
        s.ellipse(0.12f, hy + 0.14f, 0.17f, 0.62f, -4f + wiggle, 16)
        paint(s, Palette.PINK)
        s.ellipse(-0.05f, hy + 0.2f, 0.09f, 0.42f, 14f + wiggle, 12)
        s.ellipse(0.15f, hy + 0.24f, 0.09f, 0.44f, -4f + wiggle, 12)
        paint(s, Palette.BUNNY_FUR)
        s.circle(0.2f, hy, 0.3f, 28)
        paint(s, Palette.WHITE)
        s.ellipse(0.28f, hy - 0.22f, 0.26f, 0.18f, 16)
        paint(s, Palette.PINK)
        s.circle(0.5f, hy - 0.08f, 0.05f, 10)
        if (hit) {
            paint(s, Palette.WHITE)
            s.rect(0.4f, hy - 0.26f, 0.08f, 0.08f)
            paint(s, Palette.INK)
            s.rect(0.437f, hy - 0.26f, 0.006f, 0.08f)
        }
        eye(s, 0.3f, hy + 0.06f, 0.07f, hit, blink, Palette.INK)
        paint(s, Palette.INK)
        s.rectLine(0.46f, hy - 0.12f, 0.68f, hy - 0.06f, 0.012f)
        s.rectLine(0.46f, hy - 0.14f, 0.68f, hy - 0.18f, 0.012f)
    }

    private fun turtle(s: ShapeRenderer, t: Float, hit: Boolean, blink: Boolean, breathe: Float) {
        val bob = MathUtils.sin(t * 2f) * 0.02f
        paint(s, Palette.TURTLE_SKIN)
        s.ellipse(-0.44f, -0.62f, 0.28f, 0.2f, 12)
        s.ellipse(0.16f, -0.62f, 0.28f, 0.2f, 12)
        s.triangle(-0.5f, -0.36f, -0.5f, -0.46f, -0.68f, -0.44f)
        // Neck and head
        s.rectLine(0.22f, -0.3f, 0.4f, -0.02f + bob, 0.2f)
        s.circle(0.44f, 0.02f + bob, 0.21f, 22)
        if (hit) {
            paint(s, Palette.MOUTH)
            s.ellipse(0.5f, -0.1f + bob, 0.12f, 0.07f, 10)
        } else {
            paint(s, Palette.INK)
            s.rectLine(0.5f, -0.07f + bob, 0.62f, -0.05f + bob, 0.02f)
        }
        eye(s, 0.5f, 0.07f + bob, 0.06f, hit, blink, Palette.INK)
        // Shell: a half-dome stretched upward, with spots and a rim.
        paint(s, Palette.TURTLE_SHELL_DARK)
        domeShape(s, -0.06f, -0.42f, 0.54f, 1.85f + breathe)
        paint(s, Palette.TURTLE_SHELL)
        domeShape(s, -0.06f, -0.42f, 0.49f, 1.85f + breathe)
        paint(s, Palette.TURTLE_SPOT)
        s.circle(-0.06f, 0.12f, 0.15f, 16)
        s.circle(-0.3f, -0.12f, 0.12f, 14)
        s.circle(0.18f, -0.12f, 0.12f, 14)
        s.circle(-0.06f, -0.28f, 0.1f, 12)
        paint(s, Palette.TURTLE_SHELL_DARK)
        s.rect(-0.62f, -0.48f, 1.12f, 0.1f)
    }

    private fun domeShape(s: ShapeRenderer, cx: Float, cy: Float, r: Float, stretch: Float) {
        s.translate(cx, cy, 0f)
        s.scale(1f, stretch, 1f)
        s.arc(0f, 0f, r, 0f, 180f, 24)
        s.scale(1f, 1f / stretch, 1f)
        s.translate(-cx, -cy, 0f)
    }

    private fun parrot(s: ShapeRenderer, t: Float, hit: Boolean, blink: Boolean, breathe: Float) {
        val flap = MathUtils.sin(t * 6f) * 4f
        paint(s, Palette.PARROT_BLUE)
        s.rectLine(-0.25f, -0.25f, -0.6f, -0.58f, 0.13f)
        paint(s, Palette.PARROT_GREEN)
        s.rectLine(-0.2f, -0.3f, -0.48f, -0.62f, 0.11f)
        paint(s, Palette.HUB)
        s.rect(-0.08f, -0.6f, 0.06f, 0.14f)
        s.rect(0.08f, -0.6f, 0.06f, 0.14f)
        paint(s, Palette.PARROT_RED)
        s.ellipse(-0.34f, -0.52f, 0.64f, 0.86f + breathe, 24)
        paint(s, Palette.PARROT_GREEN)
        s.ellipse(-0.34f, -0.36f, 0.4f, 0.6f, 12f + flap, 16)
        paint(s, Palette.PARROT_BLUE)
        s.ellipse(-0.34f, -0.4f, 0.22f, 0.3f, 12f + flap, 12)
        val hy = 0.3f + breathe
        paint(s, Palette.PARROT_RED)
        s.triangle(-0.02f, hy + 0.15f, 0.06f, hy + 0.36f, 0.14f, hy + 0.2f)
        s.triangle(0.1f, hy + 0.2f, 0.2f, hy + 0.38f, 0.26f, hy + 0.18f)
        s.circle(0.12f, hy, 0.27f, 26)
        paint(s, Palette.WHITE)
        s.ellipse(0.14f, hy - 0.1f, 0.22f, 0.26f, 16)
        eye(s, 0.24f, hy + 0.04f, 0.065f, hit, blink, Palette.INK)
        paint(s, Palette.BEAK)
        s.triangle(0.33f, hy + 0.06f, 0.33f, hy - 0.12f, 0.56f, hy - 0.06f)
        paint(s, Palette.INK)
        if (hit) s.triangle(0.33f, hy - 0.14f, 0.33f, hy - 0.24f, 0.48f, hy - 0.2f)
        else s.triangle(0.33f, hy - 0.08f, 0.33f, hy - 0.16f, 0.46f, hy - 0.1f)
    }

    private fun eye(s: ShapeRenderer, x: Float, y: Float, r: Float, hit: Boolean, blink: Boolean, pupil: Color) {
        when {
            hit -> {
                paint(s, Palette.INK)
                s.rectLine(x - r, y - r, x + r, y + r, 0.035f)
                s.rectLine(x - r, y + r, x + r, y - r, 0.035f)
            }
            blink -> {
                paint(s, Palette.INK)
                s.rectLine(x - r, y, x + r, y, 0.035f)
            }
            else -> {
                paint(s, Palette.WHITE)
                s.circle(x, y, r, 14)
                paint(s, pupil)
                s.circle(x + r * 0.3f, y, r * 0.58f, 12)
                paint(s, Palette.WHITE)
                s.circle(x + r * 0.45f, y + r * 0.25f, r * 0.2f, 8)
            }
        }
    }

    private fun catEye(s: ShapeRenderer, x: Float, y: Float, hit: Boolean, blink: Boolean) {
        val r = 0.065f
        when {
            hit -> {
                paint(s, Palette.INK)
                s.rectLine(x - r, y - r, x + r, y + r, 0.03f)
                s.rectLine(x - r, y + r, x + r, y - r, 0.03f)
            }
            blink -> {
                paint(s, Palette.INK)
                s.rectLine(x - r, y, x + r, y, 0.03f)
            }
            else -> {
                paint(s, Palette.CAT_EYE)
                s.ellipse(x - 0.055f, y - 0.07f, 0.11f, 0.14f, 14)
                paint(s, Palette.INK)
                s.ellipse(x - 0.015f, y - 0.055f, 0.03f, 0.11f, 10)
            }
        }
    }

    /** Flies and stink lines for a very messy pet. */
    private fun stink(s: ShapeRenderer, x: Float, top: Float, mess: Float, t: Float) {
        if (mess >= 75f) {
            paint(s, tmpColor.set(0.45f, 0.6f, 0.2f, 0.55f))
            for (i in 0 until 3) {
                val bx = x - 0.35f + i * 0.35f
                val phase = t * 3f + i
                var y = top + 0.15f
                var px = bx
                for (k in 0 until 4) {
                    val nx = bx + MathUtils.sin(phase + k * 1.4f) * 0.08f
                    s.rectLine(px, y, nx, y + 0.14f, 0.035f)
                    px = nx
                    y += 0.14f
                }
            }
        }
        val flies = ((mess - 35f) / 20f).toInt().coerceIn(0, 3)
        for (i in 0 until flies) {
            val a = t * (3f + i) + i * 2.1f
            val fx = x + MathUtils.cos(a) * (0.55f + i * 0.1f)
            val fy = top + 0.35f + MathUtils.sin(a * 1.7f) * 0.25f
            paint(s, tmpColor.set(1f, 1f, 1f, 0.7f))
            s.ellipse(fx - 0.07f, fy, 0.08f, 0.05f, 8)
            s.ellipse(fx, fy, 0.08f, 0.05f, 8)
            paint(s, Palette.INK)
            s.circle(fx, fy, 0.045f, 8)
        }
    }

    // ---- Fort pieces ----

    fun piece(s: ShapeRenderer, piece: Piece) {
        val b = piece.body
        s.identity()
        s.translate(b.position.x, b.position.y, 0f)
        s.rotate(0f, 0f, 1f, b.angle * MathUtils.radiansToDegrees)
        pieceShape(s, piece.type, 1f)
        for (d in piece.decals) splat(s, d)
        s.identity()
    }

    /** A fort piece in its own unrotated coordinates; [alpha] < 1 draws a placement ghost. */
    fun pieceShape(s: ShapeRenderer, type: PieceType, alpha: Float) {
        val w = type.w
        val h = type.h
        val hw = w / 2f
        val hh = h / 2f
        fun col(c: Color) {
            paint(s, tmpColor.set(c.r, c.g, c.b, c.a * alpha))
        }
        when (type) {
            PieceType.BOX -> {
                col(Palette.BOX_DARK); s.rect(-hw, -hh, w, h)
                col(Palette.BOX); s.rect(-hw + 0.05f, -hh + 0.05f, w - 0.1f, h - 0.1f)
                col(Palette.TAPE); s.rect(-0.08f, -hh + 0.05f, 0.16f, h - 0.1f)
                col(Palette.BOX_DARK); s.rect(-hw + 0.05f, hh - 0.26f, w - 0.1f, 0.035f)
            }
            PieceType.CRATE -> {
                col(Palette.CRATE_DARK); s.rect(-hw, -hh, w, h)
                col(Palette.CRATE); s.rect(-hw + 0.06f, -hh + 0.06f, w - 0.12f, h - 0.12f)
                col(Palette.CRATE_DARK)
                s.rectLine(-hw + 0.18f, -hh + 0.18f, hw - 0.18f, hh - 0.18f, 0.13f)
                s.rect(-hw + 0.06f, hh - 0.2f, w - 0.12f, 0.05f)
                s.rect(-hw + 0.06f, -hh + 0.15f, w - 0.12f, 0.05f)
            }
            PieceType.PLANK -> {
                col(Palette.PLANK_DARK); s.rect(-hw, -hh, w, h)
                col(Palette.PLANK); s.rect(-hw + 0.04f, -hh + 0.04f, w - 0.08f, h - 0.08f)
                col(Palette.PLANK_DARK)
                s.rect(-hw + 0.3f, -0.015f, w * 0.45f, 0.03f)
                s.rect(0.1f, hh * 0.45f, w * 0.3f, 0.025f)
                s.circle(-hw + 0.14f, 0f, 0.035f, 8)
                s.circle(hw - 0.14f, 0f, 0.035f, 8)
            }
            PieceType.POT -> {
                val bw = w * 0.36f
                col(Palette.LEAF)
                s.circle(0f, hh + 0.14f, 0.16f, 14)
                s.circle(-0.17f, hh + 0.06f, 0.12f, 12)
                s.circle(0.17f, hh + 0.06f, 0.12f, 12)
                col(Palette.FLOWER); s.circle(0.02f, hh + 0.3f, 0.1f, 12)
                col(Palette.GOLD); s.circle(0.02f, hh + 0.3f, 0.04f, 8)
                col(Palette.POT_DARK)
                s.triangle(-bw - 0.04f, -hh, bw + 0.04f, -hh, hw + 0.04f, hh)
                s.triangle(-bw - 0.04f, -hh, hw + 0.04f, hh, -hw - 0.04f, hh)
                col(Palette.POT)
                s.triangle(-bw + 0.02f, -hh + 0.04f, bw - 0.02f, -hh + 0.04f, hw - 0.04f, hh - 0.04f)
                s.triangle(-bw + 0.02f, -hh + 0.04f, hw - 0.04f, hh - 0.04f, -hw + 0.04f, hh - 0.04f)
                col(Palette.POT_DARK); s.rect(-hw - 0.03f, hh - 0.18f, w + 0.06f, 0.18f)
            }
            PieceType.GNOME -> {
                col(Palette.INK); s.rect(-0.3f, -hh, 0.6f, 0.12f)
                col(Palette.GNOME_BODY); s.rect(-0.3f, -hh + 0.1f, 0.6f, 0.52f)
                s.circle(0f, -0.05f, 0.3f, 16)
                col(Palette.INK); s.rect(-0.3f, -0.3f, 0.6f, 0.06f)
                col(Palette.GNOME_FACE); s.circle(0f, 0.08f, 0.17f, 16)
                col(Palette.WHITE); s.triangle(-0.22f, 0.05f, 0.22f, 0.05f, 0f, -0.36f)
                col(Palette.PINK); s.circle(0f, 0.06f, 0.06f, 10)
                col(Palette.INK)
                s.circle(-0.07f, 0.13f, 0.025f, 6)
                s.circle(0.07f, 0.13f, 0.025f, 6)
                col(Palette.GNOME_HAT); s.triangle(-0.27f, 0.17f, 0.27f, 0.17f, 0.04f, hh)
            }
            PieceType.TIRE -> {
                col(Palette.TIRE); s.circle(0f, 0f, hw, 32)
                col(Palette.TIRE_INNER)
                for (i in 0 until 10) {
                    val a = i * 36f
                    s.circle(MathUtils.cosDeg(a) * hw * 0.83f, MathUtils.sinDeg(a) * hw * 0.83f, 0.06f, 8)
                }
                s.circle(0f, 0f, hw * 0.62f, 28)
                col(Palette.HUB); s.circle(0f, 0f, hw * 0.36f, 20)
                col(Palette.TIRE); s.circle(0f, 0f, hw * 0.12f, 12)
                col(Palette.WHITE); s.circle(hw * 0.2f, hw * 0.2f, 0.04f, 8)
            }
        }
    }

    // ---- Throwables ----

    fun projectile(s: ShapeRenderer, p: Projectile) {
        val b = p.body
        s.identity()
        s.translate(b.position.x, b.position.y, 0f)
        // A carrot points where it is going; everything else spins with its body.
        val angle = if (p.item == ItemType.CARROT) MathUtils.atan2(b.linearVelocity.y, b.linearVelocity.x) else b.angle
        s.rotate(0f, 0f, 1f, angle * MathUtils.radiansToDegrees)
        if (p.fragment && p.item == ItemType.SEEDS) seed(s, p.radius) else itemShape(s, p.item, p.radius)
        s.identity()
    }

    private fun seed(s: ShapeRenderer, r: Float) {
        paint(s, Palette.SEED_DARK)
        s.ellipse(-r * 1.1f, -r * 0.75f, r * 2.2f, r * 1.5f, 18)
        paint(s, Palette.SEED)
        s.ellipse(-r * 0.95f, -r * 0.6f, r * 1.9f, r * 1.2f, 18)
    }

    fun itemShape(s: ShapeRenderer, item: ItemType, r: Float) {
        when (item) {
            ItemType.MUD -> {
                paint(s, Palette.MUD_DARK); s.circle(0f, 0f, r, 18)
                paint(s, Palette.MUD); s.circle(0f, 0f, r * 0.88f, 18)
                paint(s, Palette.MUD_DARK)
                s.circle(r * 0.35f, r * 0.2f, r * 0.2f, 10)
                s.circle(-r * 0.3f, -r * 0.3f, r * 0.16f, 10)
                s.circle(-r * 0.2f, r * 0.4f, r * 0.12f, 8)
            }
            ItemType.WATER -> {
                paint(s, Palette.WATER)
                s.circle(0f, 0f, r, 22)
                s.triangle(-r * 0.25f, -r * 0.92f, r * 0.25f, -r * 0.92f, 0f, -r * 1.25f)
                paint(s, tmpColor.set(1f, 1f, 1f, 0.65f))
                s.ellipse(-r * 0.6f, r * 0.1f, r * 0.42f, r * 0.55f, 30f, 12)
            }
            ItemType.BOWLING -> {
                paint(s, Palette.BOWLING); s.circle(0f, 0f, r, 24)
                paint(s, Palette.INK)
                s.circle(r * 0.3f, r * 0.42f, r * 0.13f, 8)
                s.circle(r * 0.55f, r * 0.15f, r * 0.13f, 8)
                s.circle(r * 0.05f, r * 0.12f, r * 0.15f, 8)
                paint(s, tmpColor.set(1f, 1f, 1f, 0.3f))
                s.circle(-r * 0.4f, r * 0.35f, r * 0.18f, 10)
            }
            ItemType.BONE -> {
                val k = r / ItemType.BONE.radius
                paint(s, Palette.PANEL_EDGE)
                s.rect(-0.33f * k, -0.085f * k, 0.66f * k, 0.17f * k)
                for (sx in floatArrayOf(-0.33f, 0.33f)) for (sy in floatArrayOf(-0.08f, 0.08f)) s.circle(sx * k, sy * k, 0.105f * k, 12)
                paint(s, Palette.BONE)
                s.rect(-0.33f * k, -0.06f * k, 0.66f * k, 0.12f * k)
                for (sx in floatArrayOf(-0.33f, 0.33f)) for (sy in floatArrayOf(-0.08f, 0.08f)) s.circle(sx * k, sy * k, 0.085f * k, 12)
            }
            ItemType.CARROT -> {
                val k = r / ItemType.CARROT.radius
                paint(s, Palette.LEAF)
                s.triangle(-0.2f * k, 0f, -0.42f * k, 0.14f * k, -0.38f * k, 0.02f * k)
                s.triangle(-0.2f * k, 0f, -0.42f * k, -0.14f * k, -0.38f * k, -0.02f * k)
                s.triangle(-0.2f * k, 0f, -0.46f * k, 0.04f * k, -0.46f * k, -0.04f * k)
                paint(s, Palette.CARROT)
                s.triangle(0.36f * k, 0f, -0.22f * k, 0.13f * k, -0.22f * k, -0.13f * k)
                paint(s, Palette.ACCENT_DARK)
                s.rectLine(-0.1f * k, 0.06f * k, -0.04f * k, 0.02f * k, 0.02f * k)
                s.rectLine(0.06f * k, -0.05f * k, 0.12f * k, -0.02f * k, 0.02f * k)
            }
            ItemType.SHELL -> {
                paint(s, Palette.TURTLE_SHELL_DARK); s.circle(0f, 0f, r, 22)
                paint(s, Palette.TURTLE_SHELL); s.circle(0f, 0f, r * 0.86f, 22)
                paint(s, Palette.TURTLE_SPOT)
                s.circle(0f, 0f, r * 0.3f, 12)
                for (i in 0 until 6) {
                    val a = i * 60f
                    s.circle(MathUtils.cosDeg(a) * r * 0.58f, MathUtils.sinDeg(a) * r * 0.58f, r * 0.17f, 10)
                }
            }
            ItemType.SEEDS -> {
                paint(s, Palette.SEED_DARK); s.circle(0f, 0f, r, 20)
                paint(s, Palette.SEED); s.circle(0f, 0f, r * 0.86f, 20)
                paint(s, Palette.SEED_DARK)
                for (i in 0 until 7) {
                    val a = i * 51f + 10f
                    val d = if (i % 2 == 0) r * 0.5f else r * 0.2f
                    s.ellipse(MathUtils.cosDeg(a) * d - r * 0.12f, MathUtils.sinDeg(a) * d - r * 0.07f, r * 0.24f, r * 0.14f, a, 8)
                }
            }
            ItemType.HAIRBALL -> {
                paint(s, Palette.HAIR_DARK)
                for (i in 0 until 9) {
                    val a = i * 40f
                    s.triangle(
                        MathUtils.cosDeg(a - 14f) * r * 0.8f, MathUtils.sinDeg(a - 14f) * r * 0.8f,
                        MathUtils.cosDeg(a + 14f) * r * 0.8f, MathUtils.sinDeg(a + 14f) * r * 0.8f,
                        MathUtils.cosDeg(a) * r * 1.25f, MathUtils.sinDeg(a) * r * 1.25f,
                    )
                }
                paint(s, Palette.HAIR); s.circle(0f, 0f, r, 18)
                paint(s, Palette.HAIR_DARK)
                s.rectLine(-r * 0.5f, 0f, r * 0.4f, r * 0.3f, 0.025f)
                s.rectLine(-r * 0.3f, -r * 0.4f, r * 0.5f, -r * 0.1f, 0.025f)
            }
        }
    }

    // ---- Splats ----

    fun splatColor(item: ItemType): Color = when (item) {
        ItemType.MUD -> Palette.MUD
        ItemType.WATER -> Palette.WATER_DROP
        ItemType.BOWLING -> Palette.BOWLING
        ItemType.BONE -> Palette.GRAVY
        ItemType.HAIRBALL -> Palette.HAIR
        ItemType.CARROT -> Palette.CARROT
        ItemType.SHELL -> Palette.TURTLE_SHELL
        ItemType.SEEDS -> Palette.SEED
    }

    /** A blob with a few droplets, shaped by the decal's seed so each splat looks different. */
    fun splat(s: ShapeRenderer, d: Decal) {
        paint(s, splatColor(d.item))
        if (d.item == ItemType.WATER) {
            for (i in 0 until 3) {
                val ox = ((d.seed shr (i * 3)) and 7) / 7f - 0.5f
                val x = d.x + ox * 0.5f
                val y = d.y - i * 0.12f
                s.circle(x, y, 0.06f, 8)
                s.triangle(x - 0.05f, y + 0.02f, x + 0.05f, y + 0.02f, x, y + 0.13f)
            }
            return
        }
        s.circle(d.x, d.y, d.r, 14)
        for (i in 0 until 5) {
            val bits = (d.seed ushr (i * 5)) and 31
            val a = bits * (360f / 32f)
            val dist = d.r * (0.9f + (bits and 3) * 0.2f)
            s.circle(d.x + MathUtils.cosDeg(a) * dist, d.y + MathUtils.sinDeg(a) * dist, d.r * (0.25f + (bits and 1) * 0.15f), 8)
        }
    }
}
