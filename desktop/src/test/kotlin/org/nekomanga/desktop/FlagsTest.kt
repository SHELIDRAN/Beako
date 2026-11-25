package org.nekomanga.desktop

import androidx.compose.ui.unit.Density
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.nekomanga.desktop.components.Flags

class FlagsTest {
    @Test
    fun everyBundledFlagLoadsAndUnknownCountriesHaveNone() {
        val folder = File(Flags::class.java.getResource("/flags/jp.svg")!!.toURI()).parentFile
        val codes =
            folder.listFiles { f -> f.name.endsWith(".svg") }!!.map { it.nameWithoutExtension }
        assertEquals(25, codes.size)
        codes.forEach { assertNotNull(Flags.painter(it, Density(1f)), "flag $it") }
        assertNull(Flags.painter("zz", Density(1f)))
        assertEquals("South Korea", Flags.name("kr"))
    }
}
