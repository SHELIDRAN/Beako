package org.nekomanga.desktop

import kotlin.test.Test
import kotlin.test.assertEquals

class AppIconTest {

    @Test
    fun bundledIconDecodes() {
        val icon = loadAppIcon()
        assertEquals(256, icon.width)
        assertEquals(256, icon.height)
    }
}
