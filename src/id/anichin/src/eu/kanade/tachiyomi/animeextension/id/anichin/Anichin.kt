@file:Suppress("DEPRECATION_ERROR")

package eu.kanade.tachiyomi.animeextension.id.anichin

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
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class Anichin : Source() {

    override val name = "Anichin"

    override val baseUrl: String
        get() = preferences.getString(PREF_DOMAIN_KEY, PREF_DOMAIN_DEFAULT) ?: PREF_DOMAIN_DEFAULT

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

    // ============================== Shared list parsing ==============================

    private fun hasNextPage(doc: Document): Boolean = doc.selectFirst("a.next.page-numbers, div.hpage a.r") != null

    private fun animeCards(doc: Document): List<Element> = doc.select("article.bs")

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

    // ============================== Hosters & Videos ==============================

    private val playlistUtils by lazy { PlaylistUtils(client, headers) }
    private val okruExtractor by lazy { OkruExtractor(client, headers) }
    private val dailymotionExtractor by lazy { DailymotionExtractor(client, headers) }
    private val cloudflareClient by lazy { client.newBuilder().addInterceptor(CloudflareInterceptor(client)).build() }
    private val genericClient by lazy {
        client.newBuilder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun getVideoList(episode: SEpisode): List<Video> {
        val doc = client.newCall(GET("$baseUrl${episode.url}", headers)).execute().asJsoup()

        val mirrorOptions = doc.select("select.mirror option[value]")
        val videos = mutableListOf<Video>()

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

                // Skip blacklisted / network-blocked / unstreamable mirrors immediately
                if (MIRROR_BLACKLIST.any { iframeSrc.contains(it, ignoreCase = true) }) {
                    return@forEach
                }

                runCatching {
                    when {
                        "ok.ru" in iframeSrc -> okruExtractor.videosFromUrl(iframeSrc, prefix = "$label - ")
                        "dailymotion.com" in iframeSrc -> dailymotionExtractor.videosFromUrl(iframeSrc, prefix = "$label - ")
                        else -> extractGenericEmbed(iframeSrc, label)
                    }
                }.getOrNull()?.let { videos.addAll(it) }
            }
        } else {
            val rawSrc = doc.selectFirst(".player-embed iframe, .video-content iframe, iframe")?.attr("src")?.trim()
            if (!rawSrc.isNullOrBlank()) {
                val iframeSrc = resolveUrl(rawSrc, baseUrl)
                if (!MIRROR_BLACKLIST.any { iframeSrc.contains(it, ignoreCase = true) }) {
                    runCatching {
                        when {
                            "ok.ru" in iframeSrc -> okruExtractor.videosFromUrl(iframeSrc, prefix = "Default - ")
                            "dailymotion.com" in iframeSrc -> dailymotionExtractor.videosFromUrl(iframeSrc, prefix = "Default - ")
                            else -> extractGenericEmbed(iframeSrc, "Default")
                        }
                    }.getOrNull()?.let { videos.addAll(it) }
                }
            }
        }

        // Direct download hosters fallback (e.g. Mediafire, Pixeldrain) from .soraddlx / .soradl
        doc.select(".soraddlx .soraurlx, .soradl .soraurlx").forEach { sora ->
            val quality = sora.selectFirst("strong")?.text()?.trim().orEmpty()
            sora.select("a[href*='mediafire.com']").forEach { a ->
                val mfUrl = a.attr("href").trim()
                extractMediafire(mfUrl, quality)?.let { videos.add(it) }
            }
            sora.select("a[href*='pixeldrain.com']").forEach { a ->
                val pdUrl = a.attr("href").trim()
                extractPixeldrain(pdUrl, quality)?.let { videos.add(it) }
            }
        }

        return videos.distinctBy { it.videoUrl }.sortVideos()
    }

    private fun extractMediafire(url: String, quality: String): Video? {
        return runCatching {
            val doc = client.newCall(GET(url, headers)).execute().asJsoup()
            val directUrl = doc.selectFirst("#downloadButton, a[aria-label='Download file']")
                ?.attr("href")?.trim() ?: return null
            if (!directUrl.startsWith("http")) return null
            val qualityLabel = if (quality.isNotBlank()) "Mediafire - $quality" else "Mediafire"
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

    private fun extractPixeldrain(url: String, quality: String): Video? {
        val fileId = when {
            "/u/" in url -> url.substringAfter("/u/").substringBefore("?").substringBefore("/")
            "/file/" in url -> url.substringAfter("/file/").substringBefore("?").substringBefore("/")
            else -> return null
        }
        if (fileId.isBlank()) return null
        val directUrl = "https://pixeldrain.com/api/file/$fileId"
        val qualityLabel = if (quality.isNotBlank()) "Pixeldrain - $quality" else "Pixeldrain"
        return Video(
            directUrl,
            qualityLabel,
            directUrl,
            Headers.Builder().add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36").build(),
            emptyList(),
            emptyList(),
        )
    }

    /**
     * Fallback for hosts with no dedicated extractor (anichin.stream's own JWPlayer,
     * rumble.com, player.abyssplayer.com, morencius.com): fetch the embed page, look
     * for direct .m3u8/.mp4 URLs or packed JS containing streams, resolve relative paths,
     * and extract video qualities.
     */
    private fun extractGenericEmbed(embedUrl: String, label: String): List<Video> {
        val embedHeaders = headers.newBuilder().set("Referer", "$baseUrl/").build()
        val html = runCatching {
            genericClient.newCall(GET(embedUrl, embedHeaders)).execute().body.string()
        }.getOrNull() ?: runCatching {
            cloudflareClient.newCall(GET(embedUrl, embedHeaders)).execute().body.string()
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

        // Direct MP4 in HTML
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

        // 2. Packed JS scripts (e.g. anichin.stream JWPlayer setup)
        for (match in PACKED_JS_REGEX.findAll(html)) {
            val packedScript = match.value
            val unpacked = runCatching { Unpacker.unpack(packedScript) }.getOrNull() ?: continue
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
            for (mp4Match in MP4_REGEX.findAll(unpacked)) {
                val raw = mp4Match.groupValues[1].ifEmpty { mp4Match.groupValues[2] }
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
                .thenByDescending { it.videoTitle.contains("360p", ignoreCase = true) }
                .thenByDescending { it.videoTitle.contains("4K", ignoreCase = true) },
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
        private val MIRROR_BLACKLIST = listOf(
            "rumble.com",
            "rmb.anichin",
            "abyssplayer.com",
            "abyss.to",
            "videoplayer.vip",
            "rubyvid.com",
            "listeamed.net",
            "terabox.com",
            "1024terabox.com",
            "mirrored.to",
        )

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

        private val M3U8_REGEX = Regex(
            """(?:file|src)\s*:\s*["']([^"']+\.m3u8[^"']*)["']|["']([^"'\s]+\.m3u8[^"'\s]*)["']""",
            RegexOption.IGNORE_CASE,
        )
        private val MP4_REGEX = Regex(
            """(?:file|src)\s*:\s*["']([^"']+\.mp4[^"']*)["']|["']([^"'\s]+\.mp4[^"'\s]*)["']""",
            RegexOption.IGNORE_CASE,
        )
        private val PACKED_JS_REGEX = Regex(
            """eval\(function\(p,a,c,k,e,d\).*?\.split\('[|]'\),\d+,\{\}\)\)|eval\(function\(p,a,c,k,e,d\).*?\}\)\)""",
            RegexOption.DOT_MATCHES_ALL,
        )

        private const val PREF_QUALITY_KEY = "pref_quality"
        private const val PREF_QUALITY_DEFAULT = "1080p"
        private const val PREF_DOMAIN_KEY = "pref_domain"
        private const val PREF_DOMAIN_DEFAULT = "https://anichin.cafe"
    }
}
