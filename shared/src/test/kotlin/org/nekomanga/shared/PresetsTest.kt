package org.nekomanga.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import org.nekomanga.shared.comick.ComickFilters
import org.nekomanga.shared.comick.ComickSort
import org.nekomanga.shared.library.SearchPreset
import org.nekomanga.shared.library.Settings
import org.nekomanga.shared.library.withPreset
import org.nekomanga.shared.model.ComicType
import org.nekomanga.shared.net.BeakoJson

class PresetsTest {
    private val romance =
        ComickFilters(
            includedTags = listOf("female-lead"),
            types = setOf(ComicType.MANHWA),
            sort = ComickSort.POPULAR,
            fromYear = 2018,
        )

    @Test
    fun presetsAndRememberedFiltersSurviveASaveAndLoad() {
        val settings =
            Settings(
                searchFilters = romance,
                searchPresets = listOf(SearchPreset("Romance", romance)),
            )
        val loaded =
            BeakoJson.decodeFromString(
                Settings.serializer(),
                BeakoJson.encodeToString(Settings.serializer(), settings),
            )
        assertEquals(romance, loaded.searchFilters)
        assertEquals("Romance", loaded.searchPresets.single().name)
        assertEquals(romance, loaded.searchPresets.single().filters)
    }

    @Test
    fun savingUnderAnExistingNameReplacesIt() {
        val first =
            listOf(SearchPreset("Romance", romance), SearchPreset("Action", ComickFilters()))
        val updated = withPreset(first, SearchPreset("romance", romance.copy(fromYear = 2020)))
        assertEquals(listOf("Action", "romance"), updated.map { it.name })
        assertEquals(2020, updated.last().filters.fromYear)
    }
}
