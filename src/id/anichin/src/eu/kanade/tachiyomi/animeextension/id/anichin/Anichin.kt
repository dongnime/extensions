package eu.kanade.tachiyomi.animeextension.id.anichin

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.network.GET
import extensions.utils.Source
import extensions.utils.asJsoup
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

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

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
    }

    companion object {
        private val EPISODE_SLUG_REGEX = Regex("""^(.+)-episode-\d+(?:-tamat)?-subtitle-indonesia$""")
    }
}
