package eu.kanade.tachiyomi.animeextension.id.anichin

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.network.GET
import extensions.utils.Source
import extensions.utils.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class Anichin : Source() {

    override val name = "Anichin"

    override val baseUrl = "https://anichin.cafe"

    override val lang = "id"

    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        .add("Referer", "$baseUrl/")

    // ============================== Popular ==============================

    override suspend fun getPopularAnime(page: Int): AnimesPage {
        val url = if (page == 1) "$baseUrl/ongoing/" else "$baseUrl/ongoing/page/$page/"
        val doc = client.newCall(GET(url, headers)).execute().asJsoup()
        return parseArchiveListPage(doc)
    }

    // ============================== Latest ==============================

    override suspend fun getLatestUpdates(page: Int): AnimesPage {
        val url = if (page == 1) "$baseUrl/" else "$baseUrl/page/$page/"
        val doc = client.newCall(GET(url, headers)).execute().asJsoup()
        return parseLatestUpdatesPage(doc)
    }

    // ============================== Search ==============================

    override suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage {
        val genreFilter = filters.filterIsInstance<GenreFilter>().firstOrNull()
        val statusFilter = filters.filterIsInstance<StatusFilter>().firstOrNull()

        val url = when {
            query.isNotBlank() -> {
                "$baseUrl/".toHttpUrl().newBuilder().apply {
                    addQueryParameter("s", query)
                    if (page > 1) addQueryParameter("paged", page.toString())
                }.build().toString()
            }
            genreFilter != null && genreFilter.selected() != "" -> {
                val base = "$baseUrl/genres/${genreFilter.selected()}/"
                if (page == 1) base else "${base}page/$page/"
            }
            else -> {
                val base = "$baseUrl/${(statusFilter?.selected() ?: "ongoing")}/"
                if (page == 1) base else "${base}page/$page/"
            }
        }

        val doc = client.newCall(GET(url, headers)).execute().asJsoup()
        return parseArchiveListPage(doc)
    }

    override fun getFilterList() = AnimeFilterList(
        AnimeFilter.Header("Filter diabaikan jika kolom pencarian diisi"),
        StatusFilter(),
        GenreFilter(),
    )

    private class StatusFilter : AnimeFilter.Select<String>(
        "Status",
        STATUSES.map { it.first }.toTypedArray(),
    ) {
        fun selected() = STATUSES[state].second
    }

    private class GenreFilter : AnimeFilter.Select<String>(
        "Genre",
        GENRES.map { it.first }.toTypedArray(),
    ) {
        fun selected() = GENRES[state].second
    }

    // ============================== Shared list parsing ==============================

    private fun hasNextPage(doc: Document): Boolean =
        doc.selectFirst("a.next.page-numbers, div.hpage a.r") != null

    private fun animeCards(doc: Document): List<Element> =
        doc.select("article.bs")

    /** Archive-style pages (`/ongoing/`, `/completed/`, `/genres/<slug>/`): cards link straight to `/seri/<slug>/`. */
    private fun parseArchiveListPage(doc: Document): AnimesPage {
        val animeList = animeCards(doc).mapNotNull { card ->
            val link = card.selectFirst("a[href]") ?: return@mapNotNull null
            val title = card.selectFirst("div.tt")?.ownText()?.trim()?.ifBlank { null }
                ?: link.attr("title").ifBlank { return@mapNotNull null }

            SAnime.create().apply {
                this.title = title
                setUrlWithoutDomain(link.attr("href"))
                thumbnail_url = card.selectFirst("div.limit img")?.attr("abs:src")
            }
        }
        return AnimesPage(animeList, hasNextPage(doc))
    }

    /** Homepage-style pages: cards link to an episode; normalize to the series `/seri/<slug>/` URL. */
    private fun parseLatestUpdatesPage(doc: Document): AnimesPage {
        val animeList = animeCards(doc).mapNotNull { card ->
            val link = card.selectFirst("a[href]") ?: return@mapNotNull null
            val title = card.selectFirst("div.tt")?.ownText()?.trim()?.ifBlank { null }
                ?: return@mapNotNull null
            val seriesUrl = episodeUrlToSeriesUrl(link.attr("href")) ?: return@mapNotNull null

            SAnime.create().apply {
                this.title = title
                setUrlWithoutDomain(seriesUrl)
                thumbnail_url = card.selectFirst("div.limit img")?.attr("abs:src")
            }
        }.distinctBy { it.url }
        return AnimesPage(animeList, hasNextPage(doc))
    }

    private fun episodeUrlToSeriesUrl(href: String): String? {
        val slug = href.trimEnd('/').substringAfterLast('/')
        val match = EPISODE_SLUG_REGEX.find(slug) ?: return null
        return "/seri/${match.groupValues[1]}/"
    }

    // ============================== Details ==============================

    override suspend fun getAnimeDetails(anime: SAnime): SAnime {
        val doc = client.newCall(GET("$baseUrl${anime.url}", headers)).execute().asJsoup()

        val statusText = doc.selectFirst("span:contains(Status)")?.text().orEmpty()
        val synopsis = doc.selectFirst("div.bixbox.synp div.entry-content")?.text()?.trim()

        return SAnime.create().apply {
            title = doc.selectFirst("h1.entry-title")?.text()?.trim() ?: anime.title
            thumbnail_url = doc.selectFirst("div.thumb img")?.attr("abs:src") ?: anime.thumbnail_url
            genre = doc.select("div.genxed a").joinToString { it.text() }.ifBlank { null }
            description = synopsis
            status = when {
                statusText.contains("Ongoing", ignoreCase = true) -> SAnime.ONGOING
                statusText.contains("Completed", ignoreCase = true) -> SAnime.COMPLETED
                else -> SAnime.UNKNOWN
            }
            initialized = true
        }
    }

    // ============================== Episodes ==============================

    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> {
        val doc = client.newCall(GET("$baseUrl${anime.url}", headers)).execute().asJsoup()

        return doc.select("div.eplister ul li").mapNotNull { li ->
            val link = li.selectFirst("a[href]") ?: return@mapNotNull null
            val epNumStr = li.selectFirst(".epl-num")?.text()?.trim().orEmpty()
            val epNum = epNumStr.toFloatOrNull() ?: 0f
            val epTitle = li.selectFirst(".epl-title")?.text()?.trim().orEmpty()
            val subBadge = li.selectFirst(".epl-sub .status")?.text()?.trim()
            val dateStr = li.selectFirst(".epl-date")?.text()?.trim()

            SEpisode.create().apply {
                setUrlWithoutDomain(link.attr("href"))
                name = epTitle.ifBlank { "Episode $epNumStr" }
                episode_number = epNum
                date_upload = parseDate(dateStr)
                scanlator = subBadge
            }
        }.sortedByDescending { it.episode_number }
    }

    private fun parseDate(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0L
        return runCatching { DATE_FORMATTER.parse(dateStr)?.time ?: 0L }.getOrDefault(0L)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
    }

    companion object {
        private val EPISODE_SLUG_REGEX = Regex("""^(.+)-episode-\d+(?:-tamat)?-subtitle-indonesia$""")
        private val DATE_FORMATTER = SimpleDateFormat("MMMM d, yyyy", Locale.ENGLISH)

        private val STATUSES = listOf(
            Pair("Ongoing", "ongoing"),
            Pair("Completed", "completed"),
        )

        private val GENRES = listOf(
            Pair("Any", ""),
            Pair("Action", "action"),
            Pair("Action Drama", "action-drama"),
            Pair("Adventure", "adventure"),
            Pair("Comedy", "comedy"),
            Pair("Crossdressing", "crossdressing"),
            Pair("Cultivation", "cultivation"),
            Pair("Demons", "demons"),
            Pair("Drama", "drama"),
            Pair("Ecchi", "ecchi"),
            Pair("Fantasy", "fantasy"),
            Pair("Friendship", "friendship"),
            Pair("Game", "game"),
            Pair("Gore", "gore"),
            Pair("Gourmet", "gourmet"),
            Pair("Harem", "harem"),
            Pair("Historical", "historical"),
            Pair("Horror", "horror"),
            Pair("Isekai", "isekai"),
            Pair("Life", "life"),
            Pair("Magic", "magic"),
            Pair("Martial Arts", "martial-arts"),
            Pair("Mecha", "mecha"),
            Pair("Military", "military"),
            Pair("Music", "music"),
            Pair("Mystery", "mystery"),
            Pair("Mythology", "mythology"),
            Pair("Psychological", "psychological"),
            Pair("Reincarnation", "reincarnation"),
            Pair("Romance", "romance"),
            Pair("School", "school"),
            Pair("Sci-Fi", "sci-fi"),
            Pair("Shoujo", "shoujo"),
            Pair("Shounen", "shounen"),
            Pair("Slice of Life", "slice-of-life"),
            Pair("Space", "space"),
            Pair("Sports", "sports"),
            Pair("Supernatural", "supernatural"),
            Pair("Super Power", "super-power"),
            Pair("Suspense", "suspense"),
            Pair("Team Sports", "team-sports"),
            Pair("Thriller", "thriller"),
            Pair("Urban Fantasy", "urban-fantasy"),
        )
    }
}
