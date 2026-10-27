package com.pawtillery.game

/**
 * Predicts a throw with the same fixed-step, semi-implicit Euler integration Box2D uses
 * (gravity scale, wind and boomerang pull included). Collisions are ignored, so callers decide
 * where the path stops by returning false from [visit].
 */
object Trajectory {
    inline fun simulate(
        x0: Float,
        y0: Float,
        vx0: Float,
        vy0: Float,
        item: ItemType,
        side: Side,
        wind: Float,
        maxSteps: Int,
        visit: (step: Int, x: Float, y: Float, vx: Float, vy: Float) -> Boolean,
    ) {
        val h = Arena.STEP
        val ax = wind - side.dir * item.returnAccel
        val ay = Arena.GRAVITY * item.gravityScale
        var x = x0
        var y = y0
        var vx = vx0
        var vy = vy0
        for (step in 1..maxSteps) {
            vx += h * ax
            vy += h * ay
            x += h * vx
            y += h * vy
            if (!visit(step, x, y, vx, vy)) return
        }
    }
}
