package org.nekomanga.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.nekomanga.shared.model.Sources
import org.nekomanga.shared.model.Title
import org.nekomanga.shared.model.webLink

class WebLinkTest {
    @Test
    fun eachSourceLinksToItsOwnPage() {
        assertEquals(
            "https://comick.dev/comic/pick-me-up",
            Title(Sources.COMICK, "hid1", "Pick Me Up", slug = "pick-me-up").webLink(),
        )
        assertNull(Title(Sources.COMICK, "hid1", "No slug").webLink())
        assertEquals(
            "https://mangadex.org/title/uuid-1",
            Title(Sources.MANGADEX, "uuid-1", "Title").webLink(),
        )
        assertEquals(
            "https://example.org/manga/1",
            Title("ext:42", "https://example.org/manga/1", "Ext").webLink(),
        )
        assertNull(Title("ext:42", "/manga/1", "Relative").webLink())
    }
}
