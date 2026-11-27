package org.nekomanga.desktop

import coil3.request.transformations
import coil3.size.Size
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.nekomanga.desktop.screens.pageRequest

class PageRequestTest {
    @Test
    fun pagesAlwaysLoadAtFullResolution() {
        runBlocking {
            // Whatever size a page's placeholder has when it starts loading, it's decoded at full
            // size: fast scrolling must not leave pages blurry.
            for (crop in listOf(false, true)) {
                val request = pageRequest("https://example.org/page.jpg", crop)
                assertEquals(Size.ORIGINAL, request.sizeResolver.size())
            }
            assertTrue(
                pageRequest("https://example.org/page.jpg", true).transformations.isNotEmpty(),
            )
        }
    }
}
