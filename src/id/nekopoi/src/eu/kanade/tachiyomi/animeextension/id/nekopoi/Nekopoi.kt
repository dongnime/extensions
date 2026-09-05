@file:Suppress("DEPRECATION_ERROR")

package eu.kanade.tachiyomi.animeextension.id.nekopoi

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.lib.playlistutils.PlaylistUtils
import eu.kanade.tachiyomi.lib.unpacker.Unpacker
import eu.kanade.tachiyomi.network.GET
import extensions.utils.Source
import extensions.utils.asJsoup
import keiyoushi.utils.addBaseUrlPreference
import keiyoushi.utils.addListPreference
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Locale

class Nekopoi : Source() {

    override val name = "Nekopoi"

    override val baseUrl: String
        get() = preferences.getString(PREF_DOMAIN_KEY, PREF_DOMAIN_DEFAULT) ?: PREF_DOMAIN_DEFAULT

    override val lang = "id"

    override val supportsLatest = true

    private val playlistUtils by lazy { PlaylistUtils(client, headers) }

    override fun headersBuilder() = super.headersBuilder()
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        .add("Referer", "$baseUrl/")

    // ============================== Popular ==============================

    override suspend fun getPopularAnime(page: Int): AnimesPage {
        val url = if (page == 1) "$baseUrl/category/hentai/" else "$baseUrl/category/hentai/page/$page/"
        val doc = client.newCall(GET(url, headers)).execute().asJsoup()
        return parseCategoryPage(doc)
    }

    // ============================== Latest ==============================

    override suspend fun getLatestUpdates(page: Int): AnimesPage {
        val url = if (page == 1) "$baseUrl/" else "$baseUrl/page/$page/"
        val doc = client.newCall(GET(url, headers)).execute().asJsoup()
        return parseLatestUpdatesPage(doc)
    }

    // ============================== Search ==============================

    override suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage {
        val categoryFilter = filters.filterIsInstance<CategoryFilter>().firstOrNull()
        val genreFilter = filters.filterIsInstance<GenreFilter>().firstOrNull()

        val url = when {
            query.isNotBlank() -> {
                "$baseUrl/".toHttpUrl().newBuilder().apply {
                    addQueryParameter("s", query)
                    addQueryParameter("post_type", "anime")
                    if (page > 1) addQueryParameter("paged", page.toString())
                }.build().toString()
            }
            categoryFilter != null && categoryFilter.selected().isNotBlank() -> {
                val cat = categoryFilter.selected()
                if (page == 1) "$baseUrl/category/$cat/" else "$baseUrl/category/$cat/page/$page/"
            }
            genreFilter != null && genreFilter.selected().isNotBlank() -> {
                val genre = genreFilter.selected()
                if (page == 1) "$baseUrl/genres/$genre/" else "$baseUrl/genres/$genre/page/$page/"
            }
            else -> {
                if (page == 1) "$baseUrl/category/hentai/" else "$baseUrl/category/hentai/page/$page/"
            }
        }

        val doc = client.newCall(GET(url, headers)).execute().asJsoup()
        return parseCategoryPage(doc)
    }

    override fun getFilterList() = AnimeFilterList(
        AnimeFilter.Header("Filter diabaikan jika kolom pencarian diisi"),
        CategoryFilter(),
        GenreFilter(),
    )

    // ============================== Details ==============================

    override suspend fun getAnimeDetails(anime: SAnime): SAnime {
        val doc = client.newCall(GET("$baseUrl${anime.url}", headers)).execute().asJsoup()

        // Check if this is a series page (.nk-series-detail)
        if (doc.selectFirst(".nk-series-detail") != null) {
            val title = doc.selectFirst(".nk-series-synopsis b")?.text()?.trim()
                ?: doc.selectFirst("h2")?.text()?.trim()
                ?: anime.title
            val posterStyle = doc.selectFirst(".nk-series-poster")?.attr("style").orEmpty()
            val thumbnail = extractBgUrl(posterStyle) ?: anime.thumbnail_url
            val synopsis = doc.selectFirst(".nk-series-synopsis p")?.text()?.trim()
            val genres = doc.select(".nk-series-meta-list li:contains(Genre) a").joinToString { it.text() }
            val statusText = doc.selectFirst(".nk-series-meta-list li:contains(Status)")?.text().orEmpty()
            val author = doc.selectFirst(".nk-series-meta-list li:contains(Produser)")?.ownText()?.trim()

            return SAnime.create().apply {
                this.title = title
                thumbnail_url = thumbnail
                description = synopsis
                genre = genres.ifBlank { null }
                this.author = author
                status = when {
                    statusText.contains("Completed", ignoreCase = true) -> SAnime.COMPLETED
                    statusText.contains("Ongoing", ignoreCase = true) -> SAnime.ONGOING
                    else -> SAnime.UNKNOWN
                }
                initialized = true
            }
        }

        // Fallback: standalone episode page (.nk-article / .konten)
        val title = doc.selectFirst("h1")?.text()?.replace(BRACKET_REGEX, "")?.substringBefore(" Episode ")?.trim() ?: anime.title
        val thumbnail = doc.selectFirst(".nk-featured-img img")?.attr("abs:src") ?: anime.thumbnail_url
        val synopsis = doc.selectFirst(".konten p:contains(Sinopsis)")?.text()?.substringAfter(":")?.trim()
        val genre = doc.selectFirst(".konten p:contains(Genre)")?.text()?.substringAfter(":")?.trim()
        val producer = doc.selectFirst(".konten p:contains(Producers)")?.text()?.substringAfter(":")?.trim()

        return SAnime.create().apply {
            this.title = title
            thumbnail_url = thumbnail
            description = synopsis
            this.genre = genre
            author = producer
            status = SAnime.COMPLETED
            initialized = true
        }
    }

    // ============================== Episodes ==============================

    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> {
        val doc = client.newCall(GET("$baseUrl${anime.url}", headers)).execute().asJsoup()

        val episodeCards = doc.select(".nk-episode-grid ul li a.nk-episode-card")
        if (episodeCards.isNotEmpty()) {
            return episodeCards.mapNotNull { a ->
                val link = a.attr("href").ifBlank { return@mapNotNull null }
                val title = a.selectFirst(".nk-episode-card-title")?.text()?.trim().orEmpty()
                val badgeText = a.selectFirst(".nk-episode-badge")?.text().orEmpty()
                val epNum = Regex("\\d+").find(badgeText)?.value?.toFloatOrNull() ?: 1f
                val dateStr = a.selectFirst(".nk-episode-card-date")?.text()?.trim()

                SEpisode.create().apply {
                    setUrlWithoutDomain(link)
                    name = title.ifBlank { "Episode $epNum" }
                    episode_number = epNum
                    date_upload = parseIndonesianDate(dateStr)
                }
            }.sortedByDescending { it.episode_number }
        }

        // If it was already a direct episode URL
        val singleEpisodeTitle = doc.selectFirst("h1")?.text()?.trim() ?: "Episode 1"
        val dateStr = doc.selectFirst(".nk-post-header-meta span:has(.dashicons-calendar-alt)")?.text()?.trim()
        return listOf(
            SEpisode.create().apply {
                setUrlWithoutDomain(anime.url)
                name = singleEpisodeTitle
                episode_number = 1f
                date_upload = parseIndonesianDate(dateStr)
            },
        )
    }

    // ============================== Videos ==============================

    override suspend fun getVideoList(episode: SEpisode): List<Video> {
        val doc = client.newCall(GET("$baseUrl${episode.url}", headers)).execute().asJsoup()
        val videos = mutableListOf<Video>()

        // 1. Scan player frames (#nk-player .nk-player-frame iframe)
        doc.select("#nk-player .nk-player-frame iframe, .nk-player-frame iframe, iframe[src*='playmogo'], iframe[src*='streampoi']").forEach { iframe ->
            val src = iframe.attr("src").trim()
            if (src.isBlank()) return@forEach

            when {
                "playmogo.com" in src || "dood" in src -> {
                    extractPlaymogo(src)?.let { videos.add(it) }
                }
                "streampoi.com" in src || "streamruby" in src -> {
                    videos.addAll(extractStreampoi(src))
                }
                else -> {
                    videos.addAll(extractGenericEmbed(src, "Server"))
                }
            }
        }

        return videos.distinctBy { it.videoUrl }.sortVideos()
    }

    private fun extractPlaymogo(embedUrl: String): Video? {
        return runCatching {
            val embedHeaders = headers.newBuilder()
                .set("Referer", "$baseUrl/")
                .build()

            val pageHtml = client.newCall(GET(embedUrl, embedHeaders)).execute().body.string()
            val md5Match = Regex("/pass_md5/[^'\"]*").find(pageHtml)?.value ?: return null
            val token = md5Match.substringAfterLast("/")
            val host = URI(embedUrl).let { "${it.scheme}://${it.host}" }
            val md5Url = "$host$md5Match"

            val passHeaders = headers.newBuilder()
                .set("Referer", embedUrl)
                .set("X-Requested-With", "XMLHttpRequest")
                .build()

            val videoUrlStart = client.newCall(GET(md5Url, passHeaders)).execute().body.string().trim()
            if (videoUrlStart.isBlank()) return null

            val randomStr = createRandomString(10)
            val expiry = System.currentTimeMillis()
            val finalUrl = "$videoUrlStart$randomStr?token=$token&expiry=$expiry"

            val videoHeaders = Headers.Builder()
                .add("User-Agent", headers["User-Agent"] ?: "Mozilla/5.0")
                .add("Referer", "$host/")
                .build()

            val quality = Regex("\\d{3,4}[pP]").find(pageHtml.substringAfter("<title>").substringBefore("</title>"))?.value ?: "720p"

            Video(
                url = finalUrl,
                quality = "Playmogo - $quality",
                videoUrl = finalUrl,
                headers = videoHeaders,
                subtitleTracks = emptyList(),
                audioTracks = emptyList(),
            )
        }.getOrNull()
    }

    private fun extractStreampoi(embedUrl: String): List<Video> {
        return runCatching {
            val embedHeaders = headers.newBuilder()
                .set("Referer", "$baseUrl/")
                .build()

            val html = client.newCall(GET(embedUrl, embedHeaders)).execute().body.string()
            val packedScript = PACKED_JS_REGEX.find(html)?.value ?: return emptyList()
            val unpacked = runCatching { Unpacker.unpack(packedScript) }.getOrNull() ?: return emptyList()
            val m3u8Url = DIRECT_M3U8_REGEX.find(unpacked)?.groupValues?.get(1) ?: return emptyList()

            playlistUtils.extractFromHls(
                m3u8Url,
                referer = embedUrl,
                videoNameGen = { q -> "StreamPoi - $q" },
            )
        }.getOrElse { emptyList() }
    }

    private fun extractGenericEmbed(embedUrl: String, label: String): List<Video> {
        return runCatching {
            val embedHeaders = headers.newBuilder()
                .set("Referer", "$baseUrl/")
                .build()

            val html = client.newCall(GET(embedUrl, embedHeaders)).execute().body.string()

            val directUrl = DIRECT_M3U8_REGEX.find(html)?.groupValues?.get(1)
            if (directUrl != null) {
                return playlistUtils.extractFromHls(directUrl, referer = embedUrl, videoNameGen = { q -> "$label - $q" })
            }

            val packedScript = PACKED_JS_REGEX.find(html)?.value ?: return emptyList()
            val unpacked = runCatching { Unpacker.unpack(packedScript) }.getOrNull() ?: return emptyList()
            val packedUrl = DIRECT_M3U8_REGEX.find(unpacked)?.groupValues?.get(1) ?: return emptyList()

            playlistUtils.extractFromHls(packedUrl, referer = embedUrl, videoNameGen = { q -> "$label - $q" })
        }.getOrElse { emptyList() }
    }

    override fun List<Video>.sortVideos(): List<Video> {
        val quality = preferences.getString(PREF_QUALITY_KEY, PREF_QUALITY_DEFAULT) ?: PREF_QUALITY_DEFAULT
        return sortedWith(
            compareByDescending<Video> { it.videoTitle.contains(quality, ignoreCase = true) }
                .thenByDescending { it.videoTitle.contains("1080p", ignoreCase = true) }
                .thenByDescending { it.videoTitle.contains("720p", ignoreCase = true) }
                .thenByDescending { it.videoTitle.contains("480p", ignoreCase = true) }
                .thenByDescending { it.videoTitle.contains("360p", ignoreCase = true) },
        )
    }

    // ============================== Settings ==============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        screen.addBaseUrlPreference(
            preferences = preferences,
            defaultUrl = PREF_DOMAIN_DEFAULT,
            title = "Base URL",
            key = PREF_DOMAIN_KEY,
        )

        screen.addListPreference(
            key = PREF_QUALITY_KEY,
            default = PREF_QUALITY_DEFAULT,
            title = "Preferred Quality",
            summary = "%s",
            entries = listOf("1080p", "720p", "480p", "360p"),
            entryValues = listOf("1080p", "720p", "480p", "360p"),
        )
    }

    // ============================== Helper Parsing ==============================

    private fun parseCategoryPage(doc: Document): AnimesPage {
        val animeList = doc.select(".nk-search-results ul li a.nk-search-item").mapNotNull { card ->
            val href = card.attr("href").ifBlank { return@mapNotNull null }
            val title = card.selectFirst("h2")?.text()?.replace(BRACKET_REGEX, "")?.substringBefore(" Episode ")?.trim().orEmpty()
            if (title.isBlank()) return@mapNotNull null

            val thumbStyle = card.selectFirst(".nk-search-thumb")?.attr("style").orEmpty()
            val thumbnail = extractBgUrl(thumbStyle)

            // Normalize episode URL to series URL if applicable
            val targetUrl = episodeUrlToSeriesUrl(href) ?: href

            SAnime.create().apply {
                this.title = title
                setUrlWithoutDomain(targetUrl)
                thumbnail_url = thumbnail
            }
        }.distinctBy { it.url }

        val hasNext = doc.selectFirst("a.next.page-numbers") != null
        return AnimesPage(animeList, hasNext)
    }

    private fun parseLatestUpdatesPage(doc: Document): AnimesPage {
        val animeList = doc.select(".nk-episodes-area #nk-episode-grid .nk-post-card").mapNotNull { card ->
            val meta = card.selectFirst(".nk-post-meta") ?: return@mapNotNull null
            val epLink = meta.selectFirst("h2 a[href]") ?: return@mapNotNull null
            val seriesLink = meta.selectFirst("span a[href*='/hentai/'], span a[href*='/jav/']")

            val targetHref = seriesLink?.attr("href")
                ?: episodeUrlToSeriesUrl(epLink.attr("href"))
                ?: epLink.attr("href")

            val title = seriesLink?.text()?.trim()
                ?: epLink.text().replace(BRACKET_REGEX, "").substringBefore(" Episode ").trim()

            val thumbStyle = card.selectFirst(".nk-thumb-crop")?.attr("style").orEmpty()
            val thumbnail = extractBgUrl(thumbStyle)

            SAnime.create().apply {
                this.title = title
                setUrlWithoutDomain(targetHref)
                thumbnail_url = thumbnail
            }
        }.distinctBy { it.url }

        val hasNext = doc.selectFirst("a.next.page-numbers") != null
        return AnimesPage(animeList, hasNext)
    }

    private fun episodeUrlToSeriesUrl(href: String): String? {
        val slug = href.trimEnd('/').substringAfterLast('/')
        if (!slug.contains("-episode-")) return null
        val seriesSlug = slug.substringBefore("-episode-")
        return "/hentai/$seriesSlug/"
    }

    private fun extractBgUrl(style: String): String? {
        return BG_URL_REGEX.find(style)?.groupValues?.get(1)
    }

    private fun createRandomString(length: Int = 10): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..length).map { chars.random() }.joinToString("")
    }

    private fun parseIndonesianDate(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0L
        val cleanDate = dateStr.replace(Regex("(?i)(Senin|Selasa|Rabu|Kamis|Jumat|Sabtu|Minggu),?"), "").trim()
        val formattedDate = cleanDate
            .replace("Januari", "January", ignoreCase = true)
            .replace("Februari", "February", ignoreCase = true)
            .replace("Maret", "March", ignoreCase = true)
            .replace("Mei", "May", ignoreCase = true)
            .replace("Juni", "June", ignoreCase = true)
            .replace("Juli", "July", ignoreCase = true)
            .replace("Agustus", "August", ignoreCase = true)
            .replace("Oktober", "October", ignoreCase = true)
            .replace("Desember", "December", ignoreCase = true)

        return runCatching {
            DATE_FORMATTER.parse(formattedDate)?.time ?: 0L
        }.getOrDefault(0L)
    }

    // ============================== Filter Classes ==============================

    private class CategoryFilter : AnimeFilter.Select<String>(
        "Kategori",
        CATEGORIES.map { it.first }.toTypedArray(),
    ) {
        fun selected() = CATEGORIES[state].second
    }

    private class GenreFilter : AnimeFilter.Select<String>(
        "Genre",
        GENRES.map { it.first }.toTypedArray(),
    ) {
        fun selected() = GENRES[state].second
    }

    companion object {
        private val BRACKET_REGEX = Regex("""\[.*?\]""")
        private val BG_URL_REGEX = Regex("""url\(['"]?(.*?)['"]?\)""")
        private val DIRECT_M3U8_REGEX = Regex("""(https?://[^"'\s]+\.m3u8[^"'\s]*)""")
        private val PACKED_JS_REGEX = Regex("""eval\(function\(p,a,c,k,e,d\).*?\}\)""", RegexOption.DOT_MATCHES_ALL)
        private val DATE_FORMATTER = SimpleDateFormat("d MMMM yyyy", Locale.ENGLISH)

        private const val PREF_DOMAIN_KEY = "pref_domain"
        private const val PREF_DOMAIN_DEFAULT = "https://nekopoi.care"

        private const val PREF_QUALITY_KEY = "pref_quality"
        private const val PREF_QUALITY_DEFAULT = "720p"

        private val CATEGORIES = listOf(
            Pair("Hentai", "hentai"),
            Pair("2D Animation", "2d-animation"),
            Pair("3D Hentai", "3d-hentai"),
            Pair("JAV", "jav"),
            Pair("JAV Cosplay", "jav-cosplay"),
        )

        private val GENRES = listOf(
            Pair("Semua", ""),
            Pair("Ahegao", "ahegao"),
            Pair("Anal", "anal"),
            Pair("BDSM", "bdsm"),
            Pair("Big Oppai", "big-oppai"),
            Pair("Blackmail", "blackmail"),
            Pair("Blowjob", "blowjob"),
            Pair("Bondage", "bondage"),
            Pair("Creampie", "creampie"),
            Pair("Dark Skin", "dark-skin"),
            Pair("DILF", "dilf"),
            Pair("Elf", "elf"),
            Pair("Femdom", "femdom"),
            Pair("Forced", "forced"),
            Pair("Futanari", "futanari"),
            Pair("Gangbang", "gangbang"),
            Pair("Handjob", "handjob"),
            Pair("Harem", "harem"),
            Pair("Housewife", "housewife"),
            Pair("Incest", "incest"),
            Pair("Lactation", "lactation"),
            Pair("Loli", "loli"),
            Pair("Maid", "maid"),
            Pair("Masturbation", "masturbation"),
            Pair("Megane", "megane"),
            Pair("MILF", "milf"),
            Pair("Mind Control", "mind-control"),
            Pair("Monster", "monster"),
            Pair("Netorare", "netorare"),
            Pair("Nurse", "nurse"),
            Pair("Oral", "oral"),
            Pair("Paizuri", "paizuri"),
            Pair("Pantyhose", "pantyhose"),
            Pair("Rape", "rape"),
            Pair("Romance", "romance"),
            Pair("Schoolgirl", "schoolgirl"),
            Pair("Sex Toys", "sex-toys"),
            Pair("Shibari", "shibari"),
            Pair("Stocking", "stocking"),
            Pair("Swimsuit", "swimsuit"),
            Pair("Tentacles", "tentacles"),
            Pair("Tsundere", "tsundere"),
            Pair("Uncensored", "uncensored"),
            Pair("Vanilla", "vanilla"),
            Pair("Virgin", "virgin"),
            Pair("Yaoi", "yaoi"),
            Pair("Yuri", "yuri"),
        )
    }
}
