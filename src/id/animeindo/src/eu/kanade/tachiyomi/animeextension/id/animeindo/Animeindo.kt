@file:Suppress("DEPRECATION_ERROR")

package eu.kanade.tachiyomi.animeextension.id.animeindo

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Track
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.lib.bloggerextractor.BloggerExtractor
import eu.kanade.tachiyomi.lib.dailymotionextractor.DailymotionExtractor
import eu.kanade.tachiyomi.lib.doodextractor.DoodExtractor
import eu.kanade.tachiyomi.lib.okruextractor.OkruExtractor
import eu.kanade.tachiyomi.lib.playlistutils.PlaylistUtils
import eu.kanade.tachiyomi.lib.unpacker.Unpacker
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import extensions.utils.Source
import keiyoushi.utils.addBaseUrlPreference
import keiyoushi.utils.addListPreference
import keiyoushi.utils.bodyString
import keiyoushi.utils.useAsJsoup
import okhttp3.Headers
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URI
import java.net.URLEncoder

class Animeindo : Source() {

    override val name = "Animeindo"

    override val baseUrl: String
        get() = preferences.getString(PREF_DOMAIN_KEY, PREF_DOMAIN_DEFAULT) ?: PREF_DOMAIN_DEFAULT

    override val lang = "id"

    override val supportsLatest = true

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", USER_AGENT)
        .add("Referer", "$baseUrl/")
        .add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")

    private val bloggerExtractor by lazy { BloggerExtractor(client) }
    private val dailymotionExtractor by lazy { DailymotionExtractor(client, headers) }
    private val doodExtractor by lazy { DoodExtractor(client) }
    private val okruExtractor by lazy { OkruExtractor(client, headers) }
    private val playlistUtils by lazy { PlaylistUtils(client, headers) }

    // ============================== Popular ==============================

    override suspend fun getPopularAnime(page: Int): AnimesPage {
        val url = "$baseUrl/trending?page=$page"
        val response = client.newCall(GET(url, headers)).awaitSuccess()
        return parseAnimePage(response.useAsJsoup())
    }

    // ============================== Latest ==============================

    override suspend fun getLatestUpdates(page: Int): AnimesPage {
        val url = "$baseUrl/browse?page=$page"
        val response = client.newCall(GET(url, headers)).awaitSuccess()
        return parseAnimePage(response.useAsJsoup())
    }

    // ============================== Search ==============================

    override suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage {
        val url = if (query.isNotBlank()) {
            val encoded = URLEncoder.encode(query, "UTF-8")
            if (page == 1) "$baseUrl/search/$encoded" else "$baseUrl/search/$encoded?page=$page"
        } else {
            var filterUrl: String? = null
            for (filter in filters) {
                when (filter) {
                    is TypeFilter -> {
                        val path = filter.selected()
                        if (path.isNotBlank()) {
                            filterUrl = if (page == 1) "$baseUrl/$path" else "$baseUrl/$path?page=$page"
                        }
                    }

                    is GenreFilter -> {
                        val slug = filter.selected()
                        if (slug.isNotBlank()) {
                            filterUrl = if (page == 1) "$baseUrl/genre/$slug" else "$baseUrl/genre/$slug?page=$page"
                        }
                    }

                    else -> {}
                }
            }
            filterUrl ?: (if (page == 1) "$baseUrl/browse" else "$baseUrl/browse?page=$page")
        }

        val response = client.newCall(GET(url, headers)).awaitSuccess()
        return parseAnimePage(response.useAsJsoup())
    }

    private fun parseAnimePage(doc: Document): AnimesPage {
        val elements = doc.select("div.relative.group.overflow-hidden, div.relative.group")
        val animeList = mutableListOf<SAnime>()
        val seen = mutableSetOf<String>()

        for (el in elements) {
            val a = el.selectFirst("a[href*=\"/tv-show/\"], a[href*=\"/movie/\"]") ?: continue
            val rawHref = a.attr("href")
            val absUrl = if (rawHref.startsWith("http")) rawHref else "$baseUrl$rawHref"
            if (!seen.add(absUrl)) continue

            val titleEl = el.selectFirst("h3, h2")
            val title = titleEl?.text()?.trim() ?: a.text().trim()
            if (title.isBlank()) continue

            val img = el.selectFirst("img")
            val posterUrl = img?.attr("data-src")?.ifEmpty { null }
                ?: img?.attr("src")?.takeIf { !it.startsWith("data:") }

            animeList.add(
                SAnime.create().apply {
                    this.url = absUrl
                    this.title = title
                    this.thumbnail_url = posterUrl
                },
            )
        }

        val hasNextPage = doc.select("nav.pagination button:contains(Next):not([disabled]), nav.pagination a:contains(Next):not([disabled])").isNotEmpty() ||
            doc.select("button[wire\\:click*=\"nextPage\"]:not([disabled])").isNotEmpty() ||
            animeList.size >= 24

        return AnimesPage(animeList, hasNextPage)
    }

    // ============================== Details ==============================

    override suspend fun getAnimeDetails(anime: SAnime): SAnime {
        val response = client.newCall(GET(anime.url, headers)).awaitSuccess()
        val doc = response.useAsJsoup()

        return anime.apply {
            title = doc.selectFirst("h1")?.text()?.trim() ?: title

            val ogDesc = doc.selectFirst("meta[property=\"og:description\"]")?.attr("content")?.trim()
            val textDesc = doc.select("div.text-gray-400 p, div.text-gray-300 p, div.text-gray-500 p").text().trim()
            description = when {
                !ogDesc.isNullOrBlank() -> ogDesc
                textDesc.isNotBlank() -> textDesc
                else -> null
            }

            val ogImage = doc.selectFirst("meta[property=\"og:image\"]")?.attr("content")
            val posterImg = doc.selectFirst("div.aspect-\\[2\\/3\\] img")?.attr("src")
            thumbnail_url = ogImage?.takeIf { it.isNotBlank() } ?: posterImg ?: thumbnail_url

            val genres = doc.select("a[href*=\"/genre/\"]").map { it.text().trim() }.distinct()
            if (genres.isNotEmpty()) {
                genre = genres.joinToString(", ")
            }

            status = when {
                anime.url.contains("/movie/") -> SAnime.COMPLETED
                doc.text().contains("Completed", ignoreCase = true) -> SAnime.COMPLETED
                else -> SAnime.ONGOING
            }
        }
    }

    // ============================== Episodes ==============================

    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> {
        val response = client.newCall(GET(anime.url, headers)).awaitSuccess()
        val doc = response.useAsJsoup()

        val episodeElements = doc.select("a[href*=\"/episode/\"]")
        if (episodeElements.isEmpty() || anime.url.contains("/movie/")) {
            return listOf(
                SEpisode.create().apply {
                    url = anime.url
                    name = "Full Movie"
                    episode_number = 1f
                },
            )
        }

        val episodes = mutableListOf<SEpisode>()
        for (a in episodeElements) {
            val href = a.attr("href")
            val absUrl = if (href.startsWith("http")) href else "$baseUrl$href"

            val cardContainer = a.parent()
            val title = cardContainer?.selectFirst("h3")?.text()?.trim()
                ?: a.selectFirst("h3")?.text()?.trim()
                ?: a.text().trim()

            val metaText = cardContainer?.selectFirst("div.text-xs")?.text()?.trim().orEmpty()
            val epNumMatch = Regex("""Episode\s+(\d+)""", RegexOption.IGNORE_CASE).find(metaText)
            val epNum = Regex("""-(\d+)$""").find(href)?.groupValues?.get(1)?.toFloatOrNull()
                ?: epNumMatch?.groupValues?.get(1)?.toFloatOrNull()
                ?: 1f

            val displayName = if (title.isNotBlank() && !title.equals("Episode ${epNum.toInt()}", ignoreCase = true)) {
                "Episode ${epNum.toInt()}: $title"
            } else {
                "Episode ${epNum.toInt()}"
            }

            episodes.add(
                SEpisode.create().apply {
                    url = absUrl
                    name = displayName
                    episode_number = epNum
                },
            )
        }

        return episodes.sortedByDescending { it.episode_number }
    }

    // ============================== Video Resolution ==============================

    override suspend fun getVideoList(episode: SEpisode): List<Video> {
        val response = client.newCall(GET(episode.url, headers)).awaitSuccess()
        val doc = response.useAsJsoup()

        val videos = mutableListOf<Video>()
        val servers = extractServers(doc)

        for (server in servers) {
            runCatching {
                val serverVideos = extractVideosFromServer(server.link, server.label)
                videos.addAll(serverVideos)
            }
        }

        return videos.distinctBy { it.videoUrl }.sortVideos()
    }

    private data class Server(val label: String, val link: String)

    private fun extractServers(doc: Document): List<Server> {
        val servers = mutableListOf<Server>()
        val snapshotElements = doc.select("[wire\\:snapshot]")
        for (el in snapshotElements) {
            val raw = el.attr("wire:snapshot")
            if (!raw.contains("watch-component")) continue
            val obj = JSONObject(raw)
            val data = obj.optJSONObject("data") ?: continue
            val rawVideos = data.optJSONArray("videos") ?: continue
            val videoListArray = rawVideos.optJSONArray(0) ?: continue
            for (i in 0 until videoListArray.length()) {
                val entryArray = videoListArray.optJSONArray(i) ?: continue
                val serverObj = entryArray.optJSONObject(0) ?: continue
                val label = serverObj.optString("label", "Server").trim()
                val link = serverObj.optString("link").trim()
                if (link.isNotBlank()) {
                    servers.add(Server(label, link))
                }
            }
        }
        return servers
    }

    private suspend fun extractVideosFromServer(embedUrl: String, label: String): List<Video> {
        val embedHeaders = headers.newBuilder().set("Referer", "$baseUrl/").build()
        val html = client.newCall(GET(embedUrl, embedHeaders)).awaitSuccess().bodyString()
        val doc = Jsoup.parse(html, embedUrl)

        val videos = mutableListOf<Video>()

        // 1. Direct <source> tags (e.g. KUMA, ARC direct mp4 or m3u8)
        val sourceElements = doc.select("source[src]")
        for (source in sourceElements) {
            val src = source.attr("abs:src").ifEmpty { source.attr("src") }
            if (src.isBlank()) continue
            if (src.contains(".m3u8")) {
                val hlsVideos = runCatching {
                    playlistUtils.extractFromHls(src, referer = embedUrl, videoNameGen = { q -> "$label - $q" })
                }.getOrElse { emptyList() }
                videos.addAll(hlsVideos)
            } else {
                val videoHeaders = headers.newBuilder().set("Referer", embedUrl).build()
                videos.add(
                    Video(
                        url = src,
                        quality = "$label - Direct",
                        videoUrl = src,
                        headers = videoHeaders,
                        subtitleTracks = emptyList<Track>(),
                        audioTracks = emptyList<Track>(),
                    ),
                )
            }
        }

        if (videos.isNotEmpty()) return videos

        // 2. <iframe> tags
        val iframeElements = doc.select("iframe[src]")
        for (iframe in iframeElements) {
            val iframeSrc = iframe.attr("abs:src").ifEmpty { iframe.attr("src") }
            if (iframeSrc.isBlank()) continue

            when {
                "ok.ru" in iframeSrc -> {
                    videos.addAll(okruExtractor.videosFromUrl(iframeSrc, prefix = "$label - "))
                }

                "dailymotion.com" in iframeSrc -> {
                    videos.addAll(dailymotionExtractor.videosFromUrl(iframeSrc, prefix = "$label - "))
                }

                "dood" in iframeSrc || "ds2play" in iframeSrc -> {
                    doodExtractor.videoFromUrl(iframeSrc, prefix = "$label - ")?.let { videos.add(it) }
                }

                "blogger.com" in iframeSrc || "blogspot.com" in iframeSrc || "googleusercontent" in iframeSrc -> {
                    videos.addAll(bloggerExtractor.videosFromUrl(iframeSrc, headers, prefix = label))
                }

                else -> {
                    videos.addAll(extractGenericEmbed(iframeSrc, label))
                }
            }
        }

        if (videos.isNotEmpty()) return videos

        // 3. Fallback: check direct m3u8/mp4 or packed JS in the embed page itself
        videos.addAll(extractGenericEmbed(embedUrl, label, html))

        return videos
    }

    private suspend fun extractGenericEmbed(embedUrl: String, label: String, preloadedHtml: String? = null): List<Video> {
        val embedHeaders = headers.newBuilder().set("Referer", "$baseUrl/").build()
        val html = preloadedHtml ?: runCatching {
            client.newCall(GET(embedUrl, embedHeaders)).awaitSuccess().bodyString()
        }.getOrNull() ?: return emptyList()

        // Search direct .m3u8
        for (match in M3U8_REGEX.findAll(html)) {
            val raw = match.groupValues[1]
            if (raw.isNotBlank()) {
                val resolved = resolveUrl(raw, embedUrl)
                val hlsVideos = runCatching {
                    playlistUtils.extractFromHls(resolved, referer = embedUrl, videoNameGen = { q -> "$label - $q" })
                }.getOrElse { emptyList() }
                if (hlsVideos.isNotEmpty()) return hlsVideos
            }
        }

        // Search direct .mp4
        for (match in MP4_REGEX.findAll(html)) {
            val raw = match.groupValues[1]
            if (raw.isNotBlank() && !raw.contains("placeholder") && !raw.contains("loading")) {
                val resolved = resolveUrl(raw, embedUrl)
                val videoHeaders = headers.newBuilder().set("Referer", embedUrl).build()
                return listOf(
                    Video(
                        url = resolved,
                        quality = label,
                        videoUrl = resolved,
                        headers = videoHeaders,
                        subtitleTracks = emptyList<Track>(),
                        audioTracks = emptyList<Track>(),
                    ),
                )
            }
        }

        // Search packed JS
        for (match in PACKED_JS_REGEX.findAll(html)) {
            val packedScript = match.value
            val unpacked = runCatching { Unpacker.unpack(packedScript) }.getOrNull() ?: continue
            for (m3u8 in M3U8_REGEX.findAll(unpacked)) {
                val raw = m3u8.groupValues[1]
                if (raw.isNotBlank()) {
                    val resolved = resolveUrl(raw, embedUrl)
                    val hlsVideos = runCatching {
                        playlistUtils.extractFromHls(resolved, referer = embedUrl, videoNameGen = { q -> "$label - $q" })
                    }.getOrElse { emptyList() }
                    if (hlsVideos.isNotEmpty()) return hlsVideos
                }
            }
            for (mp4 in MP4_REGEX.findAll(unpacked)) {
                val raw = mp4.groupValues[1]
                if (raw.isNotBlank() && !raw.contains("placeholder")) {
                    val resolved = resolveUrl(raw, embedUrl)
                    val videoHeaders = headers.newBuilder().set("Referer", embedUrl).build()
                    return listOf(
                        Video(
                            url = resolved,
                            quality = label,
                            videoUrl = resolved,
                            headers = videoHeaders,
                            subtitleTracks = emptyList<Track>(),
                            audioTracks = emptyList<Track>(),
                        ),
                    )
                }
            }
        }

        return emptyList()
    }

    private fun resolveUrl(raw: String, base: String): String = when {
        raw.startsWith("http://") || raw.startsWith("https://") -> raw

        raw.startsWith("//") -> "https:$raw"

        raw.startsWith("/") -> {
            runCatching {
                val uri = URI(base)
                "${uri.scheme}://${uri.host}$raw"
            }.getOrElse { "$baseUrl$raw" }
        }

        else -> "${base.substringBeforeLast('/')}/$raw"
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

    // ============================== Filters ==============================

    override fun getFilterList(): AnimeFilterList = AnimeFilterList(
        AnimeFilter.Header("Catatan: Filter diabaikan jika pencarian teks diisi"),
        TypeFilter(),
        GenreFilter(),
    )

    private class TypeFilter :
        AnimeFilter.Select<String>(
            "Tipe",
            arrayOf("Semua", "Movies", "TV Shows"),
        ) {
        fun selected(): String = when (state) {
            1 -> "movies"
            2 -> "tv-shows"
            else -> ""
        }
    }

    private class GenreFilter :
        AnimeFilter.Select<String>(
            "Genre",
            GENRES.map { it.first }.toTypedArray(),
        ) {
        fun selected(): String = GENRES[state].second
    }

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        private const val PREF_DOMAIN_KEY = "pref_domain"
        private const val PREF_DOMAIN_DEFAULT = "https://animeindo.skin"

        private const val PREF_QUALITY_KEY = "pref_quality"
        private const val PREF_QUALITY_DEFAULT = "1080p"

        private val M3U8_REGEX = Regex("""(?:"|')([^"']*\.m3u8(?:[^"']*)?)(?:"|')""")
        private val MP4_REGEX = Regex("""(?:"|')([^"']*\.mp4(?:[^"']*)?)(?:"|')""")
        private val PACKED_JS_REGEX = Regex("""eval\(function\(p,a,c,k,e,d\)[\s\S]*?\.split\('\|'\)\)\)""")

        private val GENRES = listOf(
            "Semua" to "",
            "Action" to "action",
            "Adventure" to "adventure",
            "Comedy" to "comedy",
            "Drama" to "drama",
            "Fantasy" to "fantasy",
            "History" to "history",
            "Horror" to "horror",
            "Music" to "music",
            "Mystery" to "mystery",
            "Romance" to "romance",
            "Science Fiction" to "science-fiction",
            "TV Movie" to "tv-movie",
            "Thriller" to "thriller",
            "War" to "war",
        )
    }
}
