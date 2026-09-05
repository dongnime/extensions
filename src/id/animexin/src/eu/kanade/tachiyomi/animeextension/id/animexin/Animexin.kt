@file:Suppress("DEPRECATION_ERROR")

package eu.kanade.tachiyomi.animeextension.id.animexin

import android.util.Base64
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.lib.cloudflareinterceptor.CloudflareInterceptor
import eu.kanade.tachiyomi.lib.dailymotionextractor.DailymotionExtractor
import eu.kanade.tachiyomi.lib.doodextractor.DoodExtractor
import eu.kanade.tachiyomi.lib.okruextractor.OkruExtractor
import eu.kanade.tachiyomi.lib.playlistutils.PlaylistUtils
import eu.kanade.tachiyomi.lib.unpacker.Unpacker
import eu.kanade.tachiyomi.network.GET
import extensions.utils.Source
import extensions.utils.asJsoup
import keiyoushi.utils.addBaseUrlPreference
import keiyoushi.utils.addListPreference
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Locale

class Animexin : Source() {

    override val name = "AnimeXin"

    override val baseUrl: String
        get() = preferences.getString(PREF_DOMAIN_KEY, PREF_DOMAIN_DEFAULT) ?: PREF_DOMAIN_DEFAULT

    override val lang = "id"

    override val supportsLatest = true

    override val client: OkHttpClient = network.client.newBuilder()
        .addInterceptor(CloudflareInterceptor(network.client))
        .build()

    private val playlistUtils by lazy { PlaylistUtils(client, headers) }
    private val okruExtractor by lazy { OkruExtractor(client, headers) }
    private val dailymotionExtractor by lazy { DailymotionExtractor(client, headers) }
    private val doodExtractor by lazy { DoodExtractor(client) }

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        .add("Referer", "$baseUrl/")

    // ============================== Popular ==============================

    override suspend fun getPopularAnime(page: Int): AnimesPage {
        val url = if (page == 1) {
            "$baseUrl/?post_type=anime&order=popular"
        } else {
            "$baseUrl/?page=$page&post_type=anime&order=popular"
        }
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
        val orderFilter = filters.filterIsInstance<OrderFilter>().firstOrNull()
        val statusFilter = filters.filterIsInstance<StatusFilter>().firstOrNull()
        val genreFilter = filters.filterIsInstance<GenreFilter>().firstOrNull()

        val url = when {
            query.isNotBlank() -> {
                "$baseUrl/".toHttpUrl().newBuilder().apply {
                    addQueryParameter("s", query)
                    addQueryParameter("post_type", "anime")
                    if (page > 1) addQueryParameter("page", page.toString())
                }.build().toString()
            }
            genreFilter != null && genreFilter.selected().isNotBlank() -> {
                val base = "$baseUrl/genres/${genreFilter.selected()}/"
                if (page == 1) base else "${base}page/$page/"
            }
            else -> {
                "$baseUrl/".toHttpUrl().newBuilder().apply {
                    addQueryParameter("post_type", "anime")
                    orderFilter?.selected()?.ifBlank { null }?.let { addQueryParameter("order", it) }
                    statusFilter?.selected()?.ifBlank { null }?.let { addQueryParameter("status", it) }
                    if (page > 1) addQueryParameter("page", page.toString())
                }.build().toString()
            }
        }

        val doc = client.newCall(GET(url, headers)).execute().asJsoup()
        return parseArchiveListPage(doc)
    }

    override fun getFilterList() = AnimeFilterList(
        AnimeFilter.Header("Filter diabaikan jika kolom pencarian diisi"),
        OrderFilter(),
        StatusFilter(),
        GenreFilter(),
    )

    private class OrderFilter :
        AnimeFilter.Select<String>(
            "Urutkan",
            ORDERS.map { it.first }.toTypedArray(),
        ) {
        fun selected() = ORDERS[state].second
    }

    private class StatusFilter :
        AnimeFilter.Select<String>(
            "Status",
            STATUSES.map { it.first }.toTypedArray(),
        ) {
        fun selected() = STATUSES[state].second
    }

    private class GenreFilter :
        AnimeFilter.Select<String>(
            "Genre",
            GENRES.map { it.first }.toTypedArray(),
        ) {
        fun selected() = GENRES[state].second
    }

    // ============================== Parsing Helpers ==============================

    private fun hasNextPage(doc: Document): Boolean = doc.selectFirst("a.next.page-numbers, div.hpage a.r") != null

    private fun animeCards(doc: Document): List<Element> = doc.select("article.bs")

    private fun parseArchiveListPage(doc: Document): AnimesPage {
        val animeList = animeCards(doc).mapNotNull { card ->
            val link = card.selectFirst("a[href]") ?: return@mapNotNull null
            val title = card.selectFirst("div.tt")?.ownText()?.trim()?.ifBlank { null }
                ?: link.attr("title").ifBlank { return@mapNotNull null }

            SAnime.create().apply {
                this.title = title
                setUrlWithoutDomain(link.attr("href"))
                val img = card.selectFirst("div.limit img, img")
                thumbnail_url = img?.attr("abs:src")?.ifBlank { null }
                    ?: img?.attr("abs:data-src")?.ifBlank { null }
                    ?: img?.attr("abs:data-lazy-src")
            }
        }
        return AnimesPage(animeList, hasNextPage(doc))
    }

    private fun parseLatestUpdatesPage(doc: Document): AnimesPage {
        val container = doc.select(".listupd.normal article.bs").ifEmpty { animeCards(doc) }
        val animeList = container.mapNotNull { card ->
            val link = card.selectFirst("a[href]") ?: return@mapNotNull null
            val title = card.selectFirst("div.tt")?.ownText()?.trim()?.ifBlank { null }
                ?: link.attr("title").ifBlank { return@mapNotNull null }

            SAnime.create().apply {
                this.title = title
                setUrlWithoutDomain(link.attr("href"))
                val img = card.selectFirst("div.limit img, img")
                thumbnail_url = img?.attr("abs:src")?.ifBlank { null }
                    ?: img?.attr("abs:data-src")?.ifBlank { null }
                    ?: img?.attr("abs:data-lazy-src")
            }
        }.distinctBy { it.url }
        return AnimesPage(animeList, hasNextPage(doc))
    }

    // ============================== Details & Episodes ==============================

    private suspend fun getSeriesDocument(url: String): Document {
        val doc = client.newCall(GET("$baseUrl$url", headers)).execute().asJsoup()
        val seriesLink = doc.selectFirst("div.nvs.nvsc a[href]")?.attr("href")?.trim()
        return if (!seriesLink.isNullOrBlank() && !url.contains(seriesLink.trimEnd('/'))) {
            runCatching {
                client.newCall(GET(seriesLink, headers)).execute().asJsoup()
            }.getOrDefault(doc)
        } else {
            doc
        }
    }

    override suspend fun getAnimeDetails(anime: SAnime): SAnime {
        val doc = getSeriesDocument(anime.url)
        val statusText = doc.selectFirst("span:contains(Status)")?.text().orEmpty()
        val synopsis = doc.selectFirst("div.bixbox.synp div.entry-content")?.text()?.trim()
            ?: doc.selectFirst("div.desc")?.text()?.trim()
        val authorStudio = doc.selectFirst("span:contains(Studio) a")?.text()?.trim()
            ?: doc.selectFirst("span:contains(Network) a")?.text()?.trim()

        return SAnime.create().apply {
            title = doc.selectFirst("h1.entry-title")?.text()?.trim() ?: anime.title
            val img = doc.selectFirst("div.thumb img, .infox img")
            thumbnail_url = img?.attr("abs:src")?.ifBlank { null }
                ?: img?.attr("abs:data-src")?.ifBlank { null }
                ?: anime.thumbnail_url
            genre = doc.select("div.genxed a").joinToString { it.text() }.ifBlank { null }
            description = synopsis
            author = authorStudio
            artist = authorStudio
            status = when {
                statusText.contains("Ongoing", ignoreCase = true) -> SAnime.ONGOING
                statusText.contains("Completed", ignoreCase = true) -> SAnime.COMPLETED
                else -> SAnime.UNKNOWN
            }
            initialized = true
        }
    }

    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> {
        val doc = getSeriesDocument(anime.url)
        val episodes = doc.select("div.eplister ul li a").map { a ->
            SEpisode.create().apply {
                setUrlWithoutDomain(a.attr("href"))
                val epNumStr = a.selectFirst(".epl-num")?.text().orEmpty()
                episode_number = epNumStr.toFloatOrNull() ?: 1f
                val epTitle = a.selectFirst(".epl-title")?.text()?.trim()
                name = if (!epTitle.isNullOrBlank()) epTitle else "Episode $epNumStr"
                val dateStr = a.selectFirst(".epl-date")?.text()?.trim()
                date_upload = parseDate(dateStr)
            }
        }
        return episodes.ifEmpty {
            listOf(
                SEpisode.create().apply {
                    setUrlWithoutDomain(anime.url)
                    name = "Episode 1"
                    episode_number = 1f
                },
            )
        }
    }

    private fun parseDate(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0L
        return runCatching {
            DATE_FORMATTER.parse(dateStr)?.time ?: 0L
        }.getOrDefault(0L)
    }

    // ============================== Videos ==============================

    override suspend fun getVideoList(episode: SEpisode): List<Video> {
        val doc = client.newCall(GET("$baseUrl${episode.url}", headers)).execute().asJsoup()
        val videos = mutableListOf<Video>()

        // 1. Select.mirror options
        val mirrorOptions = doc.select("select.mirror option[value]")
        if (mirrorOptions.isNotEmpty()) {
            mirrorOptions.forEach { option ->
                val label = option.text().trim()
                val base64Value = option.attr("value").trim()
                if (label.isBlank() || base64Value.isBlank()) return@forEach

                val decodedHtml = runCatching { String(Base64.decode(base64Value, Base64.DEFAULT)) }
                    .getOrNull() ?: return@forEach
                val rawSrc = Jsoup.parse(decodedHtml).selectFirst("iframe")?.attr("src")?.trim()
                    ?: return@forEach
                val iframeSrc = resolveUrl(rawSrc, baseUrl)

                if (MIRROR_BLACKLIST.any { iframeSrc.contains(it, ignoreCase = true) }) {
                    return@forEach
                }

                videos.addAll(extractFromUrl(iframeSrc, label))
            }
        } else {
            // Default player frame
            val rawSrc = doc.selectFirst("#embed_holder #pembed iframe, .player-embed iframe, iframe")?.attr("src")?.trim()
            if (!rawSrc.isNullOrBlank()) {
                val iframeSrc = resolveUrl(rawSrc, baseUrl)
                if (!MIRROR_BLACKLIST.any { iframeSrc.contains(it, ignoreCase = true) }) {
                    videos.addAll(extractFromUrl(iframeSrc, "Default"))
                }
            }
        }

        // 2. Direct download hosters fallback (Mediafire)
        doc.select(".soraddlx").forEach { block ->
            val subLang = block.selectFirst(".sorattlx")?.text()?.trim() ?: "Download"
            block.select(".soraurlx").forEach { sora ->
                val quality = sora.selectFirst("strong")?.text()?.trim().orEmpty()
                sora.select("a[href*='mediafire.com']").forEach { a ->
                    val mfUrl = a.attr("href").trim()
                    val label = if (quality.isNotBlank()) "$subLang Mediafire - $quality" else "$subLang Mediafire"
                    extractMediafire(mfUrl, label)?.let { videos.add(it) }
                }
            }
        }

        return videos.distinctBy { it.videoUrl }.sortVideos()
    }

    private fun extractFromUrl(url: String, label: String): List<Video> {
        return runCatching {
            when {
                "ok.ru" in url -> okruExtractor.videosFromUrl(url, prefix = "$label - ")
                "dailymotion.com" in url -> dailymotionExtractor.videosFromUrl(url, prefix = "$label - ")
                "playmogo.com" in url || "dood" in url || "ds2play" in url -> {
                    doodExtractor.videoFromUrl(url, prefix = "$label - ")?.let { listOf(it) } ?: emptyList()
                }
                "mediafire.com" in url -> extractMediafire(url, label)?.let { listOf(it) } ?: emptyList()
                else -> extractGenericEmbed(url, label)
            }
        }.getOrDefault(emptyList())
    }

    private fun extractMediafire(url: String, quality: String): Video? {
        return runCatching {
            val doc = client.newCall(GET(url, headers)).execute().asJsoup()
            val directUrl = doc.selectFirst("#downloadButton, a[aria-label='Download file']")
                ?.attr("href")?.trim() ?: return null
            if (!directUrl.startsWith("http")) return null
            val qualityLabel = if (quality.isNotBlank()) quality else "Mediafire"
            Video(
                directUrl,
                qualityLabel,
                directUrl,
                Headers.Builder().add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36").build(),
                emptyList(),
                emptyList(),
            )
        }.getOrNull()
    }

    private fun extractGenericEmbed(embedUrl: String, label: String): List<Video> {
        val embedHeaders = headers.newBuilder().set("Referer", "$baseUrl/").build()
        val html = runCatching {
            client.newCall(GET(embedUrl, embedHeaders)).execute().body.string()
        }.getOrNull() ?: return emptyList()

        // 1. Direct M3U8 in HTML
        for (match in M3U8_REGEX.findAll(html)) {
            val raw = match.groupValues[1].ifEmpty { match.groupValues[2] }
            if (raw.isNotBlank()) {
                val resolved = resolveUrl(raw, embedUrl)
                val videos = runCatching {
                    playlistUtils.extractFromHls(resolved, referer = embedUrl, videoNameGen = { q -> "$label - $q" })
                }.getOrElse { emptyList() }
                if (videos.isNotEmpty()) return videos
            }
        }

        // 2. Direct MP4 in HTML
        for (match in MP4_REGEX.findAll(html)) {
            val raw = match.groupValues[1].ifEmpty { match.groupValues[2] }
            if (raw.isNotBlank() && !raw.contains("placeholder") && !raw.contains("loading")) {
                val resolved = resolveUrl(raw, embedUrl)
                return listOf(
                    Video(
                        resolved,
                        label,
                        resolved,
                        headers.newBuilder().set("Referer", embedUrl).build(),
                        emptyList(),
                        emptyList(),
                    ),
                )
            }
        }

        // 3. Packed JS
        for (match in PACKED_JS_REGEX.findAll(html)) {
            val unpacked = runCatching { Unpacker.unpack(match.value) }.getOrNull() ?: continue
            for (m3u8Match in M3U8_REGEX.findAll(unpacked)) {
                val raw = m3u8Match.groupValues[1].ifEmpty { m3u8Match.groupValues[2] }
                if (raw.isNotBlank()) {
                    val resolved = resolveUrl(raw, embedUrl)
                    val videos = runCatching {
                        playlistUtils.extractFromHls(resolved, referer = embedUrl, videoNameGen = { q -> "$label - $q" })
                    }.getOrElse { emptyList() }
                    if (videos.isNotEmpty()) return videos
                }
            }
        }

        return emptyList()
    }

    private fun resolveUrl(rawUrl: String, baseUrl: String): String {
        val clean = rawUrl.trim()
        return when {
            clean.startsWith("http://") || clean.startsWith("https://") -> clean
            clean.startsWith("//") -> "https:$clean"
            clean.startsWith("/") -> {
                val httpUrl = baseUrl.toHttpUrl()
                "${httpUrl.scheme}://${httpUrl.host}$clean"
            }
            else -> {
                val base = baseUrl.substringBefore("?")
                "${base.substringBeforeLast('/')}/$clean"
            }
        }
    }

    override fun List<Video>.sortVideos(): List<Video> {
        val quality = preferences.getString(PREF_QUALITY_KEY, PREF_QUALITY_DEFAULT) ?: PREF_QUALITY_DEFAULT
        return sortedWith(
            compareByDescending<Video> { it.videoTitle.contains(quality, ignoreCase = true) }
                .thenByDescending { it.videoTitle.contains("Adaptive", ignoreCase = true) }
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

    companion object {
        private val M3U8_REGEX = Regex("""["'](https?://[^"']*\.m3u8[^"']*)["']|file:\s*["'](https?://[^"']*\.m3u8[^"']*)["']""")
        private val MP4_REGEX = Regex("""["'](https?://[^"']*\.mp4[^"']*)["']|file:\s*["'](https?://[^"']*\.mp4[^"']*)["']""")
        private val PACKED_JS_REGEX = Regex("""eval\(function\(p,a,c,k,e,d\).*?\}\)""", RegexOption.DOT_MATCHES_ALL)
        private val DATE_FORMATTER = SimpleDateFormat("MMMM d, yyyy", Locale.ENGLISH)

        private val MIRROR_BLACKLIST = listOf(
            "rumble.com",
            "mega.nz",
            "d.tube",
            "odysee.com",
            "animexinfansub.seekplayer.vip",
        )

        private const val PREF_DOMAIN_KEY = "pref_domain"
        private const val PREF_DOMAIN_DEFAULT = "https://animexin.dev"

        private const val PREF_QUALITY_KEY = "pref_quality"
        private const val PREF_QUALITY_DEFAULT = "1080p"

        private val STATUSES = listOf(
            Pair("All", ""),
            Pair("Ongoing", "ongoing"),
            Pair("Completed", "completed"),
        )

        private val ORDERS = listOf(
            Pair("Default", ""),
            Pair("Popular", "popular"),
            Pair("Latest Update", "update"),
            Pair("Rating", "rating"),
            Pair("Title A-Z", "title"),
        )

        private val GENRES = listOf(
            Pair("All", ""),
            Pair("Action", "action"),
            Pair("Adventure", "adventure"),
            Pair("Chinese horror", "chinese-horror"),
            Pair("Chinese Style", "chinese-style"),
            Pair("Comedy", "comedy"),
            Pair("Comic adaptation", "comic-adaptation"),
            Pair("Cultivation", "cultivation"),
            Pair("dark humor", "dark-humor"),
            Pair("Demon", "demon"),
            Pair("Demons", "demons"),
            Pair("Drama", "drama"),
            Pair("Encouraging", "encouraging"),
            Pair("Fantasy", "fantasy"),
            Pair("folklore", "folklore"),
            Pair("Game", "game"),
            Pair("Historical", "historical"),
            Pair("Inspiring", "inspiring"),
            Pair("Isekai", "isekai"),
            Pair("Magic", "magic"),
            Pair("Man", "man"),
            Pair("Martial Arts", "martial-arts"),
            Pair("Modern", "modern"),
            Pair("Mystery", "mystery"),
            Pair("Mythology", "mythology"),
            Pair("Pet", "pet"),
            Pair("Rebirth", "rebirth"),
            Pair("Reincarnation", "reincarnation"),
            Pair("Romance", "romance"),
            Pair("School", "school"),
            Pair("Sci-Fi", "sci-fi"),
            Pair("Seinen", "seinen"),
            Pair("Shounen", "shounen"),
            Pair("Super Power", "super-power"),
            Pair("Supernatural", "supernatural"),
            Pair("Suspense", "suspense"),
            Pair("System", "system"),
            Pair("Urban", "urban"),
            Pair("Vampire", "vampire"),
            Pair("War", "war"),
            Pair("Wuxia", "wuxia"),
            Pair("Xianxia", "xianxia"),
        )
    }
}
