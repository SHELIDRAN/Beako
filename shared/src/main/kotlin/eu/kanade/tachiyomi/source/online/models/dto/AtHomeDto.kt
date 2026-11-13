// Copied unchanged from Neko (app/src/main/java/eu/kanade/tachiyomi/source/online/models/dto).
package eu.kanade.tachiyomi.source.online.models.dto

import kotlinx.serialization.Serializable

@Serializable data class AtHomeDto(val baseUrl: String, val chapter: AtHomeChapterDto)

@Serializable
data class AtHomeChapterDto(val hash: String, val data: List<String>, val dataSaver: List<String>)
