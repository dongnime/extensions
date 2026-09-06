@file:Suppress("DEPRECATION_ERROR")

package eu.kanade.tachiyomi.animeextension.id.oploverz

import android.util.Base64
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.lib.bloggerextractor.BloggerExtractor
import eu.kanade.tachiyomi.lib.doodextractor.DoodExtractor
import eu.kanade.tachiyomi.lib.okruextractor.OkruExtractor
import eu.kanade.tachiyomi.lib.playlistutils.PlaylistUtils
import eu.kanade.tachiyomi.lib.unpacker.Unpacker
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import extensions.utils.Source
import extensions.utils.asJsoup
import keiyoushi.utils.addBaseUrlPreference
import keiyoushi.utils.addListPreference
import keiyoushi.utils.bodyString
import keiyoushi.utils.useAsJsoup
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class Oploverz : Source() {

    override val name = "Oploverz"

    override val baseUrl: String
        get() = preferences.getString(PREF_DOMAIN_KEY, PREF_DOMAIN_DEFAULT) ?: PREF_DOMAIN_DEFAULT

    override val lang = "id"

    override val supportsLatest = true

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("User-Agent", USER_AGENT)
        .add("Referer", "$baseUrl/")

    private val bloggerExtractor by lazy { BloggerExtractor(client) }
    private val doodExtractor by lazy { DoodExtractor(client) }
    private val okruExtractor by lazy { OkruExtractor(client, headers) }
    private val playlistUtils by lazy { PlaylistUtils(client, headers) }

    private val dateFormats = listOf(
        SimpleDateFormat("MMMM d, yyyy", Locale.ENGLISH),
        SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH),
        SimpleDateFormat("d MMMM yyyy", Locale.ENGLISH),
        SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH),
    )

    // ============================== Popular ==============================

    override suspend fun getPopularAnime(page: Int): AnimesPage {
        val url = "$baseUrl/series/?page=$page&order=popular"
        val response = client.newCall(GET(url, headers)).awaitSuccess()
        return getAnimeParse(response.useAsJsoup(), ".listupd article.bs")
    }

    // ============================== Latest ==============================

    override suspend fun getLatestUpdates(page: Int): AnimesPage {
        val url = "$baseUrl/series/?page=$page&order=update"
        val response = client.newCall(GET(url, headers)).awaitSuccess()
        return getAnimeParse(response.useAsJsoup(), ".listupd article.bs")
    }

    // ============================== Search ==============================

    override suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage {
        val url = if (query.isNotBlank()) {
            if (page == 1) {
                baseUrl.toHttpUrl().newBuilder().apply {
                    addQueryParameter("s", query)
                }.build().toString()
            } else {
                baseUrl.toHttpUrl().newBuilder().apply {
                    addPathSegment("page")
                    addPathSegment(page.toString())
                    addPathSegment("")
                    addQueryParameter("s", query)
                }.build().toString()
            }
        } else {
            val params = OploverzFilters.getSearchParameters(filters)
            "$baseUrl/series/?page=$page${params.filter}"
        }

        val response = client.newCall(GET(url, headers)).awaitSuccess()
        return getAnimeParse(response.useAsJsoup(), ".listupd article.bs")
    }

    override fun getFilterList(): AnimeFilterList = OploverzFilters.FILTER_LIST

    private fun getAnimeParse(doc: Document, selector: String): AnimesPage {
        val animeList = doc.select(selector).mapNotNull { element ->
            val a = element.selectFirst("a") ?: return@mapNotNull null
            SAnime.create().apply {
                setUrlWithoutDomain(a.attr("href"))
                title = a.attr("title").ifBlank {
                    element.selectFirst(".tt h2, .tt, h2")?.text()?.trim() ?: ""
                }
                val img = element.selectFirst("img")
                thumbnail_url = img?.attr("src")?.ifBlank { img.attr("data-src") }
            }
        }
        val hasNextPage = doc.selectFirst(".hpage a.r, .pagination a.next, a[rel=next], .hpage a:contains(Next)") != null
        return AnimesPage(animeList, hasNextPage)
    }

    // ============================== Details ==============================

    override suspend fun getAnimeDetails(anime: SAnime): SAnime {
        val fullUrl = if (anime.url.startsWith("http")) anime.url else "$baseUrl${anime.url}"
        val response = client.newCall(GET(fullUrl, headers)).awaitSuccess()
        val doc = response.useAsJsoup()

        val detail = doc.selectFirst(".spe")
        val extractedGenres = doc.select(".genxed a")
            .mapNotNull { it.text().takeIf(String::isNotBlank) }
            .joinToString().takeIf(String::isNotBlank)

        return SAnime.create().apply {
            url = anime.url
            title = doc.selectFirst("h1.entry-title")?.text()?.trim() ?: anime.title
            thumbnail_url = doc.selectFirst(".thumb img")?.let {
                it.attr("src").ifBlank { it.attr("data-src") }
            } ?: anime.thumbnail_url
            description = doc.selectFirst(".entry-content, .sinopsis, .desc")?.text()?.trim()
            author = detail?.selectFirst("span:contains(Studio)")?.text()?.substringAfter("Studio:")?.trim() ?: ""
            status = detail?.selectFirst("span:contains(Status)")?.text()?.let { parseStatus(it) } ?: SAnime.UNKNOWN
            extractedGenres?.let { genre = it }
        }
    }

    private fun parseStatus(status: String): Int = when {
        status.contains("Ongoing", ignoreCase = true) -> SAnime.ONGOING
        status.contains("Completed", ignoreCase = true) -> SAnime.COMPLETED
        else -> SAnime.UNKNOWN
    }

    // ============================== Episodes ==============================

    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> {
        val fullUrl = if (anime.url.startsWith("http")) anime.url else "$baseUrl${anime.url}"
        val response = client.newCall(GET(fullUrl, headers)).awaitSuccess()
        val doc = response.useAsJsoup()

        return doc.select(".eplister li a").map { a ->
            SEpisode.create().apply {
                setUrlWithoutDomain(a.attr("href"))
                val epNum = a.selectFirst(".epl-num")?.text()?.trim()
                val epTitle = a.selectFirst(".epl-title")?.text()?.trim()
                name = when {
                    !epTitle.isNullOrBlank() && !epNum.isNullOrBlank() -> {
                        if (epTitle.contains("Episode", ignoreCase = true)) epTitle else "Episode $epNum - $epTitle"
                    }

                    !epTitle.isNullOrBlank() -> epTitle

                    !epNum.isNullOrBlank() -> "Episode $epNum"

                    else -> a.text().trim()
                }
                episode_number = epNum?.toFloatOrNull()
                    ?: Regex("""\d+(?:\.\d+)?""").find(epTitle ?: "")?.value?.toFloatOrNull()
                    ?: 1F
                date_upload = a.selectFirst(".epl-date")?.text()?.trim()?.let { parseDate(it) } ?: 0L
            }
        }
    }

    private fun parseDate(dateStr: String): Long {
        for (format in dateFormats) {
            val time = runCatching { format.parse(dateStr)?.time }.getOrNull()
            if (time != null) return time
        }
        return 0L
    }

    // ============================== Video List ==============================

    override suspend fun getVideoList(episode: SEpisode): List<Video> {
        val fullUrl = if (episode.url.startsWith("http")) episode.url else "$baseUrl${episode.url}"
        val response = client.newCall(GET(fullUrl, headers)).awaitSuccess()
        val doc = response.useAsJsoup()

        val videos = mutableListOf<Video>()
        val seenUrls = mutableSetOf<String>()

        // 1. Check select.mirror option elements (base64 encoded iframes or direct URLs)
        val mirrorOptions = doc.select("select.mirror option")
        mirrorOptions.forEachIndexed { index, option ->
            val base64Value = option.attr("value").trim()
            if (base64Value.isBlank()) return@forEachIndexed

            val htmlToParse = if (base64Value.contains("<iframe") || base64Value.startsWith("http")) {
                base64Value
            } else {
                runCatching {
                    String(Base64.decode(base64Value, Base64.DEFAULT))
                }.getOrNull() ?: base64Value
            }

            val rawSrc = Jsoup.parse(htmlToParse).selectFirst("iframe")?.attr("src")?.trim()
                ?: (if (htmlToParse.startsWith("http")) htmlToParse.trim() else null)
                ?: return@forEachIndexed
            val iframeSrc = resolveUrl(rawSrc, baseUrl)
            if (iframeSrc in seenUrls) return@forEachIndexed
            seenUrls.add(iframeSrc)

            val rawLabel = option.text().trim()
            val label = if (rawLabel.isNotBlank()) rawLabel else guessServerName(iframeSrc, index + 1)

            extractFromUrl(iframeSrc, label).let { videos.addAll(it) }
        }

        // 2. Check direct iframes on the page
        val directIframes = doc.select(".video-content iframe, .megavid iframe, .player-embed iframe, #pembed iframe, iframe")
        directIframes.forEachIndexed { index, iframe ->
            val rawSrc = iframe.attr("src").ifBlank { iframe.attr("data-src") }.trim()
            if (rawSrc.isBlank()) return@forEachIndexed
            val iframeSrc = resolveUrl(rawSrc, baseUrl)
            if (iframeSrc in seenUrls) return@forEachIndexed
            seenUrls.add(iframeSrc)

            val label = guessServerName(iframeSrc, index + 1)
            extractFromUrl(iframeSrc, label).let { videos.addAll(it) }
        }

        return videos.distinctBy { it.videoUrl }.sortVideos()
    }

    private suspend fun extractFromUrl(url: String, label: String): List<Video> = runCatching {
        when {
            "blogger.com" in url || "blogspot.com" in url -> {
                bloggerExtractor.videosFromUrl(url, headers, prefix = label)
            }

            "ok.ru" in url -> okruExtractor.videosFromUrl(url, prefix = "$label - ")

            "playmogo.com" in url || "dood" in url || "ds2play" in url -> {
                doodExtractor.videoFromUrl(url, prefix = "$label - ")?.let { listOf(it) } ?: emptyList()
            }

            else -> extractGenericEmbed(url, label)
        }
    }.getOrDefault(emptyList())

    private fun extractGenericEmbed(embedUrl: String, label: String): List<Video> {
        val videos = mutableListOf<Video>()
        val body = runCatching {
            client.newCall(GET(embedUrl, headers.newBuilder().set("Referer", "$baseUrl/").build()))
                .execute()
                .bodyString()
        }.getOrNull() ?: return videos

        // 1. Direct M3U8 matches
        DIRECT_M3U8_REGEX.findAll(body).map { it.groupValues[1] }.distinct().forEach { m3u8 ->
            val resolved = resolveUrl(m3u8, embedUrl)
            runCatching {
                playlistUtils.extractFromHls(
                    playlistUrl = resolved,
                    referer = embedUrl,
                    videoNameGen = { quality -> "$label - $quality" },
                )
            }.getOrNull()?.let { videos.addAll(it) }
        }

        // 2. Unpack packed JS
        PACKED_JS_REGEX.findAll(body).forEach { match ->
            val unpacked = runCatching { Unpacker.unpack(match.value) }.getOrNull() ?: ""
            DIRECT_M3U8_REGEX.findAll(unpacked).map { it.groupValues[1] }.distinct().forEach { m3u8 ->
                val resolved = resolveUrl(m3u8, embedUrl)
                runCatching {
                    playlistUtils.extractFromHls(
                        playlistUrl = resolved,
                        referer = embedUrl,
                        videoNameGen = { quality -> "$label - $quality" },
                    )
                }.getOrNull()?.let { videos.addAll(it) }
            }
        }

        // 3. Direct MP4 fallback
        if (videos.isEmpty()) {
            DIRECT_MP4_REGEX.find(body)?.groupValues?.get(1)?.let { mp4 ->
                val resolved = resolveUrl(mp4, embedUrl)
                videos.add(Video(resolved, "$label - Direct MP4", resolved, headers))
            }
        }

        return videos
    }

    private fun guessServerName(url: String, index: Int): String = when {
        "blogger.com" in url || "blogspot.com" in url -> "Blogger"
        "wish" in url -> "StreamWish"
        "ok.ru" in url -> "OK.ru"
        "dood" in url || "ds2play" in url || "playmogo" in url -> "DoodStream"
        "filelions" in url -> "FileLions"
        else -> "Server $index"
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
        private const val PREF_DOMAIN_KEY = "pref_domain"
        private const val PREF_DOMAIN_DEFAULT = "https://oploverz.ch"

        private const val PREF_QUALITY_KEY = "pref_quality"
        private const val PREF_QUALITY_DEFAULT = "720p"

        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36"

        private val DIRECT_M3U8_REGEX = Regex("""(?:"|')(https?://[^"']+\.m3u8[^"']*)(?:"|')""")
        private val DIRECT_MP4_REGEX = Regex("""(?:"|')(https?://[^"']+\.mp4[^"']*)(?:"|')""")
        private val PACKED_JS_REGEX = Regex("""eval\(function\(p,a,c,k,e,d\)[\s\S]*?\.split\('\|'\)\)\)""")
    }
}
