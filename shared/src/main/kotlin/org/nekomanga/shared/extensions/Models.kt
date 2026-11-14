package org.nekomanga.shared.extensions

import kotlinx.serialization.Serializable

/** Mangayomi's code languages: Dart sources need a helper Beako doesn't have yet. */
object CodeLanguage {
    const val DART = 0
    const val JAVASCRIPT = 1
}

/** One source in a Mangayomi repository's `index.json`. Unknown fields are ignored. */
@Serializable
data class ExtensionInfo(
    val id: Long,
    val name: String,
    val lang: String = "",
    val baseUrl: String = "",
    val apiUrl: String = "",
    val iconUrl: String = "",
    val version: String = "",
    val isNsfw: Boolean = false,
    val hasCloudflare: Boolean = false,
    val sourceCodeUrl: String = "",
    /** 0 Dart, 1 JavaScript. */
    val sourceCodeLanguage: Int = CodeLanguage.DART,
    /** 0 manga, 1 anime, 2 novel: Beako lists manga only. */
    val itemType: Int = 0,
    val typeSource: String = "",
    val dateFormat: String = "",
    val dateFormatLocale: String = "",
    val additionalParams: String = "",
    val notes: String = "",
) {
    val isJavaScript: Boolean
        get() = sourceCodeLanguage == CodeLanguage.JAVASCRIPT

    /** Indexes mark copies of other sites "(unoriginal)"; they sort below originals. */
    val isUnoriginal: Boolean
        get() = name.contains("unoriginal", ignoreCase = true)
}

/** An installed source: its index entry, the repository it came from and when. */
@Serializable
data class InstalledExtension(
    val info: ExtensionInfo,
    val repositoryUrl: String,
    val installedAt: Long,
    val enabled: Boolean = true,
    /** The user's choices for the source's own settings, by key. */
    val preferences: Map<String, String> = emptyMap(),
)

/** A title in a source's lists or search results. */
data class ExtensionManga(val name: String, val url: String, val imageUrl: String?)

data class ExtensionPage(val titles: List<ExtensionManga>, val hasNextPage: Boolean)

data class ExtensionChapter(
    val name: String,
    val url: String,
    /** Milliseconds since the epoch, when the source gives it. */
    val dateUpload: Long?,
    val scanlator: String?,
)

data class ExtensionDetail(
    val name: String?,
    val imageUrl: String?,
    val description: String?,
    val author: String?,
    val genres: List<String>,
    val chapters: List<ExtensionChapter>,
)

/** A page image, with the headers the image server wants (usually a Referer). */
data class ExtensionPageImage(val url: String, val headers: Map<String, String> = emptyMap())
