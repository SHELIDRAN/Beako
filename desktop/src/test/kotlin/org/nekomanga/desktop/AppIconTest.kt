package org.nekomanga.desktop

import kotlin.test.Test
import kotlin.test.assertEquals

class AppIconTest {

    @Test
    fun bundledIconDecodes() {
        val icon = loadAppIcon()
        assertEquals(512, icon.width)
        assertEquals(512, icon.height)
    }
}
