package org.nekomanga.desktop

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import org.nekomanga.desktop.screens.LibraryScreen
import org.nekomanga.desktop.screens.dayLabel
import org.nekomanga.desktop.state.GroupBy
import org.nekomanga.desktop.state.LibraryModel
import org.nekomanga.desktop.state.LibrarySort
import org.nekomanga.desktop.theme.BeakoAppearance
import org.nekomanga.desktop.theme.BeakoTheme
import org.nekomanga.shared.library.LibraryStore
import org.nekomanga.shared.model.ComicType
import org.nekomanga.shared.model.Sources
import org.nekomanga.shared.model.Title

@OptIn(ExperimentalTestApi::class)
class LibraryTest {

    private var clock = 1L

    private fun store(): LibraryStore {
        val file = File(Files.createTempDirectory("beako").toFile(), "library.json")
        return LibraryStore(file) { clock++ }
            .apply {
                add(
                    Title(
                        Sources.COMICK,
                        "a",
                        "Akuyaku Sample",
                        altTitles = listOf("The Villainess"),
                        type = ComicType.MANGA,
                        tags = listOf("Female Protagonist"),
                    ),
                    listOf("Romance"),
                )
                add(Title(Sources.COMICK, "b", "Tower Climber", type = ComicType.MANHWA))
                add(
                    Title(Sources.MANGADEX, "c", "Blue Period", type = ComicType.MANGA),
                    listOf("Drama"),
                )
            }
    }

    @Test
    fun searchMatchesAlternativeTitlesAndTags() {
        val model = LibraryModel(store())
        model.query = "villainess"
        assertEquals(
            listOf("a"),
            model.groups(model.store.entries).single().entries.map { it.title.id },
        )
        model.query = "female prot"
        assertEquals(
            listOf("a"),
            model.groups(model.store.entries).single().entries.map { it.title.id },
        )
    }

    @Test
    fun groupsByTypeAndCategoryAndSortsByTitle() {
        val model = LibraryModel(store())
        model.sort = LibrarySort.TITLE
        model.descending = false

        model.groupBy = GroupBy.TYPE
        val byType = model.groups(model.store.entries)
        assertEquals(listOf("Manga", "Manhwa"), byType.map { it.name })
        assertEquals(
            listOf("Akuyaku Sample", "Blue Period"),
            byType[0].entries.map { it.title.title },
        )

        model.groupBy = GroupBy.CATEGORY
        assertEquals(
            listOf("Drama", LibraryModel.UNCATEGORISED, "Romance"),
            model.groups(model.store.entries).map { it.name },
        )

        model.groupBy = GroupBy.NONE
        model.categoryFilter = setOf("Drama")
        assertEquals(
            listOf("c"),
            model.groups(model.store.entries).single().entries.map { it.title.id },
        )
    }

    @Test
    fun screenSearchesAndOffersToSearchSourcesWhenNothingMatches() = runComposeUiTest {
        val model = LibraryModel(store())
        var searched: String? = null
        setContent {
            BeakoTheme(BeakoAppearance(reduceMotion = true)) {
                LibraryScreen(
                    model,
                    onOpen = {},
                    onSearchSources = { searched = it },
                    onBrowse = {},
                )
            }
        }
        onNodeWithContentDescription("Tower Climber").assertIsDisplayed()

        onNodeWithContentDescription("Search library by name, tag or genre")
            .performTextInput("solo leveling")
        waitForIdle()
        onNodeWithText("Nothing in your library matches").assertIsDisplayed()
        onNodeWithText("Search sources for “solo leveling”").performClick()
        assertEquals("solo leveling", searched)
    }

    @Test
    fun historyDaysAreNamedNaturally() {
        val today = java.time.LocalDate.of(2026, 11, 14)
        val noon =
            today.atTime(12, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals("Today", dayLabel(noon, today))
        assertEquals("Yesterday", dayLabel(noon - 86_400_000, today))
    }
}
