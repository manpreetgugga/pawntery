package com.pawtillery.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UiTest {
    private fun b(id: String, x: Float, layer: Int = 0, enabled: Boolean = true) = Button(id, x, 0f, 100f, 100f, id, enabled = enabled, layer = layer)

    @Test
    fun anOpenDialogTakesTapsBeforeTheButtonsBehindIt() {
        val card = b("pet:BUNNY", 0f)
        val unlock = b("offer_buy", 50f, layer = 1)
        val back = b("back", 400f)
        val buttons = listOf(card, back, unlock)
        assertEquals("offer_buy", buttons.hitTest(60f, 50f)?.id)
        assertNull("buttons behind the dialog are dimmed and untappable", buttons.hitTest(410f, 50f))
        assertEquals("with no dialog, the card works", "pet:BUNNY", listOf(card, back).hitTest(10f, 50f)?.id)
    }

    @Test
    fun laterButtonsSitOnTopAndDisabledOnesIgnoreTaps() {
        val buttons = listOf(b("under", 0f), b("over", 50f))
        assertEquals("over", buttons.hitTest(60f, 50f)?.id)
        assertNull(listOf(b("off", 0f, enabled = false)).hitTest(10f, 50f))
    }
}
