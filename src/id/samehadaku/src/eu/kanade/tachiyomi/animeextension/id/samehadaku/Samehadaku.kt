@file:Suppress("DEPRECATION_ERROR")

package eu.kanade.tachiyomi.animeextension.id.samehadaku

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.lib.bloggerextractor.BloggerExtractor
import eu.kanade.tachiyomi.lib.playlistutils.PlaylistUtils
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import extensions.utils.Source
import extensions.utils.asJsoup
import keiyoushi.utils.addBaseUrlPreference
import keiyoushi.utils.addListPreference
import keiyoushi.utils.bodyString
import keiyoushi.utils.tryParse
import keiyoushi.utils.useAsJsoup
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class Samehadaku : Source() {

    override val name = "Samehadaku"

    override val baseUrl: String
        get() = preferences.getString(PREF_DOMAIN_KEY, PREF_DOMAIN_DEFAULT) ?: PREF_DOMAIN_DEFAULT

    override val lang = "id"

    override val supportsLatest = true

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("User-Agent", USER_AGENT)
        .add("Referer", "$baseUrl/")

    private val bloggerExtractor by lazy { BloggerExtractor(client) }
    private val playlistUtils by lazy { PlaylistUtils(client, headers) }

    // ============================== Popular ==============================

    override suspend fun getPopularAnime(page: Int): AnimesPage {
        val url = "$baseUrl/daftar-anime-2/page/$page/?order=popular"
        val response = client.newCall(GET(url, headers)).awaitSuccess()
        return getAnimeParse(response.useAsJsoup(), "div.relat > article")
    }

    // ============================== Latest ==============================

    override suspend fun getLatestUpdates(page: Int): AnimesPage {
        val url = "$baseUrl/daftar-anime-2/page/$page/?order=update"
        val response = client.newCall(GET(url, headers)).awaitSuccess()
        return getAnimeParse(response.useAsJsoup(), "div.relat > article")
    }

    // ============================== Search ==============================

    override suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage {
        val params = SamehadakuFilters.getSearchParameters(filters)
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("daftar-anime-2")
            if (page > 1) {
                addPathSegment("page")
                addPathSegment(page.toString())
            }
            addPathSegment("")
            if (query.isNotBlank()) {
                addQueryParameter("title", query)
            }
        }.build()
        val response = client.newCall(GET("$url${params.filter}", headers)).awaitSuccess()
        val doc = response.useAsJsoup()
        val searchSelector = "main.site-main.relat > article"
        return if (doc.selectFirst(searchSelector) != null) {
            getAnimeParse(doc, searchSelector)
        } else {
            getAnimeParse(doc, "div.relat > article")
        }
    }

    override fun getFilterList(): AnimeFilterList = SamehadakuFilters.FILTER_LIST

    // ============================== Details ==============================

    override suspend fun getAnimeDetails(anime: SAnime): SAnime {
        val fullUrl = if (anime.url.startsWith("http")) anime.url else "$baseUrl${anime.url}"
        val response = client.newCall(GET(fullUrl, headers)).awaitSuccess()
        val doc = response.useAsJsoup()
        val detail = doc.selectFirst("div.infox > div.spe")

        val extractedGenres = doc.select("div.genre-info a")
            .mapNotNull { it.text().takeIf(String::isNotBlank) }
            .joinToString().takeIf(String::isNotBlank)
            ?: detail?.selectFirst("span:has(b:contains(Genre))")?.let { genres: Element ->
                genres.select("a")
                    .mapNotNull { it.text().takeIf(String::isNotBlank) }
                    .joinToString().takeIf(String::isNotBlank)
                    ?: genres.text().substringAfter(":").trim()
            }

        return SAnime.create().apply {
            url = anime.url
            author = detail?.getInfo("Studio") ?: ""
            status = detail?.let { parseStatus(it.getInfo("Status")) } ?: SAnime.UNKNOWN

            title = (
                doc.selectFirst("h3.anim-detail")?.text()?.split("Detail Anime")?.getOrNull(1)
                    ?: doc.selectFirst("h2.entry-title[itemprop='partOfSeries']")?.text()?.removeSurrounding("Sinopsis Anime", "Indo")
                    ?: doc.selectFirst("h1.entry-title")?.text()?.removeSuffix("Sub Indo")
                    ?: anime.title
                ).trim()

            thumbnail_url = doc.selectFirst("div.infoanime.widget_senction > div.thumb > img")?.attr("src")
                ?: doc.selectFirst("div.episodeinf > div.infoanime > div.areainfo > div.thumb > img")?.attr("src")
                ?: anime.thumbnail_url

            description = doc.selectFirst("div.entry-content.entry-content-single > p")?.text()
                ?: doc.selectFirst("div.desc > div.entry-content.entry-content-single")?.text()

            extractedGenres?.let { genre = it }
        }
    }

    // ============================== Episodes ==============================

    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> {
        val fullUrl = if (anime.url.startsWith("http")) anime.url else "$baseUrl${anime.url}"
        val response = client.newCall(GET(fullUrl, headers)).awaitSuccess()
        val doc = response.useAsJsoup()
        return doc.select("div.lstepsiode > ul > li")
            .mapNotNull {
                val episode = it.selectFirst("span.eps > a") ?: return@mapNotNull null
                SEpisode.create().apply {
                    setUrlWithoutDomain(episode.attr("href"))
                    val epText = episode.text().trim()
                    episode_number = epText.toFloatOrNull()
                        ?: Regex("""\d+(?:\.\d+)?""").find(epText)?.value?.toFloatOrNull()
                        ?: 1F
                    name = it.selectFirst("span.lchx > a")?.text() ?: "Episode $episode_number"
                    date_upload = it.selectFirst("span.date")?.text()
                        ?.let { date -> DATE_FORMATTER.tryParse(date) }
                        ?: 0L
                }
            }
    }

    // ============================== Videos ==============================

    override suspend fun getVideoList(episode: SEpisode): List<Video> {
        val fullUrl = if (episode.url.startsWith("http")) episode.url else "$baseUrl${episode.url}"
        val response = client.newCall(GET(fullUrl, headers)).awaitSuccess()
        val doc = response.useAsJsoup()
        val videos = mutableListOf<Video>()

        val parseUrl = response.request.url
        val hostUrl = "${parseUrl.scheme}://${parseUrl.host}"

        // 1. Interactive player options via AJAX
        doc.select("#server > ul > li > div.east_player_option, div.east_player_option").forEach { opt ->
            runCatching {
                val post = opt.attr("data-post")
                val nume = opt.attr("data-nume")
                val type = opt.attr("data-type")
                val serverLabel = opt.selectFirst("span")?.text()?.trim() ?: opt.text().trim()
                if (post.isNotBlank() && nume.isNotBlank()) {
                    val embedLink = getEmbedLink(hostUrl, post, nume, type)
                    if (embedLink.isNotBlank()) {
                        videos.addAll(getVideosFromEmbed(serverLabel, embedLink))
                    }
                }
            }
        }

        // 2. Direct download links in download box (Pixeldrain, Filedon, etc.)
        doc.select("div.download-eps, div#downloadb").forEach { dlBox ->
            dlBox.select("li").forEach { li ->
                val quality = li.selectFirst("strong")?.text()?.trim().orEmpty()
                li.select("a[href*='pixeldrain.com']").forEach { a ->
                    extractPixeldrain(a.attr("href"), quality)?.let { videos.add(it) }
                }
                li.select("a[href*='filedon.co']").forEach { a ->
                    runCatching {
                        videos.addAll(extractFiledon(a.attr("href"), "Filedon $quality".trim()))
                    }
                }
            }
        }

        return videos.distinctBy { it.videoUrl }.sortVideos()
    }

    private suspend fun getEmbedLink(hostUrl: String, post: String, nume: String, type: String): String {
        val form = FormBody.Builder()
            .add("action", "player_ajax")
            .add("post", post)
            .add("nume", nume)
            .add("type", type)
            .build()

        val ajaxHeaders = headers.newBuilder()
            .set("User-Agent", USER_AGENT)
            .build()

        val html = client.newCall(POST("$hostUrl/wp-admin/admin-ajax.php", body = form, headers = ajaxHeaders))
            .awaitSuccess()
            .bodyString()

        return SRC_REGEX.find(html)?.groupValues?.get(1)?.trim().orEmpty()
    }

    private suspend fun getVideosFromEmbed(server: String, link: String): List<Video> {
        if ("mega.nz" in link) return emptyList()

        val videoHeaders = headers.newBuilder()
            .set("User-Agent", USER_AGENT)
            .set("Referer", link)
            .build()

        return runCatching {
            when {
                link.contains(".mp4") || link.contains(".webm") || link.contains(".m3u8") -> {
                    if (link.contains(".m3u8")) {
                        playlistUtils.extractFromHls(link, referer = link, videoNameGen = { q -> "$server - $q" })
                    } else {
                        listOf(Video(link, server, link, videoHeaders))
                    }
                }

                "blogger" in link -> bloggerExtractor.videosFromUrl(link, headers, prefix = server)

                "filedon" in link || "uservideo" in link || "userdrive" in link || "samevideo" in link -> {
                    extractFiledon(link, server)
                }

                "krakenfiles" in link -> {
                    val doc = client.newCall(GET(link, videoHeaders)).awaitSuccess().useAsJsoup()
                    val rawUrl = doc.selectFirst("source")?.attr("src")
                    val videoUrl = rawUrl?.replace("&amp;", "&")
                    if (!videoUrl.isNullOrBlank()) {
                        listOf(Video(videoUrl, server, videoUrl, videoHeaders))
                    } else {
                        emptyList()
                    }
                }

                else -> {
                    val doc = client.newCall(GET(link, videoHeaders)).awaitSuccess().useAsJsoup()
                    val videoUrl = doc.selectFirst("video source, video, source")?.attr("src")
                    if (!videoUrl.isNullOrBlank()) {
                        listOf(Video(videoUrl, server, videoUrl, videoHeaders))
                    } else {
                        emptyList()
                    }
                }
            }
        }.getOrElse { emptyList() }
    }

    private suspend fun extractFiledon(link: String, label: String): List<Video> {
        val videoHeaders = headers.newBuilder()
            .set("User-Agent", USER_AGENT)
            .set("Referer", link)
            .build()

        val doc = client.newCall(GET(link, videoHeaders)).awaitSuccess().useAsJsoup()
        val dataPage = doc.selectFirst("div#app")?.attr("data-page") ?: return emptyList()
        val json = JSONObject(dataPage)
        val props = json.optJSONObject("props") ?: return emptyList()
        val videoUrl = props.optString("url")
        return if (videoUrl.isNotBlank()) {
            listOf(Video(videoUrl, label, videoUrl, videoHeaders))
        } else {
            emptyList()
        }
    }

    private fun extractPixeldrain(url: String, quality: String): Video? {
        val fileId = when {
            "/u/" in url -> url.substringAfter("/u/").substringBefore("?").substringBefore("/")
            "/file/" in url -> url.substringAfter("/file/").substringBefore("?").substringBefore("/")
            else -> return null
        }
        if (fileId.isBlank()) return null
        val directUrl = "https://pixeldrain.com/api/file/$fileId"
        val label = if (quality.isNotBlank()) "Pixeldrain - $quality" else "Pixeldrain"
        return Video(
            directUrl,
            label,
            directUrl,
            headers.newBuilder().set("Referer", url).build(),
        )
    }

    // ============================= Utilities ==============================

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

    private fun Element.getInfo(info: String, cut: Boolean = true): String? = selectFirst("span:has(b:contains($info))")?.text()
        ?.let {
            when {
                cut -> it.substringAfter(" ")
                else -> it
            }.trim()
        }

    private fun getAnimeParse(document: Document, query: String): AnimesPage {
        val animes = document.select(query).mapNotNull { elm ->
            SAnime.create().apply {
                elm.selectFirst("div > a, a")?.attr("href")?.let { setUrlWithoutDomain(it) } ?: return@mapNotNull null
                title = elm.selectFirst("div.title > h2, h2.entry-title, h2")?.text() ?: return@mapNotNull null
                thumbnail_url = elm.selectFirst("div.content-thumb > img, div.thumb img, img")?.attr("src")
            }
        }
        val hasNextPage = runCatching {
            val pagination = document.selectFirst("div.pagination")!!
            val totalPage = pagination.selectFirst("span:nth-child(1)")!!.text().split(" ").last()
            val currentPage = pagination.selectFirst("span.page-numbers.current")!!.text()
            currentPage.toInt() < totalPage.toInt()
        }.getOrDefault(false)
        return AnimesPage(animes, hasNextPage)
    }

    private fun parseStatus(status: String?): Int = when (status?.trim()?.lowercase()) {
        "completed" -> SAnime.COMPLETED
        "ongoing", "currently airing" -> SAnime.ONGOING
        else -> SAnime.UNKNOWN
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
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        private const val PREF_DOMAIN_KEY = "pref_domain"
        private const val PREF_DOMAIN_DEFAULT = "https://v2.samehadaku.how"
        private const val PREF_QUALITY_KEY = "preferred_quality"
        private const val PREF_QUALITY_DEFAULT = "720p"

        private val DATE_FORMATTER by lazy {
            SimpleDateFormat("d MMMM yyyy", Locale("id", "ID"))
        }

        private val SRC_REGEX by lazy { Regex("""src\s*=\s*["']([^"']+)["']""") }
    }
}
