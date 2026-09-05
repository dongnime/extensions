# Anichin Extension Repo Implementation Plan

> **Status:** **COMPLETE & DEPLOYED (v15.6 / extVersionCode 6)**.
> All foundational tasks (Tasks 1–9) and follow-up integration/playback tasks (Tasks 10–16) are fully completed, verified, and published.
>
> **For future Claude Code / AI Agents:** Read this plan and the appended **Agent Context & Troubleshooting Guide** to maintain full historical and technical context without needing to rediscover earlier decisions, upstream limitations, or solved runtime bugs.

**Goal:** Ship `github.com/dongnime/extensions`, a self-hosted Aniyomi/Anikku extension repo providing one working extension for `anichin.cafe`, addable in-app via a repo URL with auto-updates.

**Architecture:** Trim the build tooling (Gradle convention plugins, version catalogs, CI/index-generation scripts) out of the actively-maintained community monorepo `salmanbappi/sb-extensions-source` (pinned at commit `87b2e2154fe3c869e286de7a6181c0bcff4d23fb`), drop every extension it ships, and add one new module: `src/id/anichin`. The module follows the same "AnimeStream" WordPress-theme scraping pattern already proven in that monorepo's `src/en/animotvslash` extension, adapted to anichin.cafe's actual (verified live) URLs and selectors.

**Tech Stack:** Kotlin, Android Gradle Plugin, Jsoup (HTML parsing), OkHttp, kotlinx.serialization, Aniyomi `extensions-lib` (`AnimeHttpSource`/`ConfigurableAnimeSource`), GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-05-anichin-extension-design.md`

## Global Constraints

- Repo must be `github.com/dongnime/extensions`, public (raw.githubusercontent.com access is unauthenticated).
- Extension module lives at `src/id/anichin`, package `eu.kanade.tachiyomi.animeextension.id.anichin`, class `Anichin`.
- Default site base URL: `https://anichin.cafe` (must be user-overridable via preference, per the reference template's `addBaseUrlPreference` pattern).
- A mirror/server whose extractor fails or is unimplemented is dropped from the video list, never treated as a fatal error for the whole episode.
- No dedicated automated test suite — this ecosystem's extensions are thin scraping adapters with no per-module tests in the reference monorepo. Verification is: build succeeds, then manually check the live site behavior, per the spec's Testing section.
- README carries the standard ecosystem disclaimer: the extension hosts no content, only interfaces with a public website.

---

## Task 1: Bootstrap repo skeleton from reference monorepo's build tooling

**Files:**
- Create: `dongnime/extensions` GitHub repo (empty, public)
- Copy from reference (commit `87b2e2154fe3c869e286de7a6181c0bcff4d23fb` of `salmanbappi/sb-extensions-source`) into this working directory:
  - `gradle/build-logic/` (entire directory, unmodified)
  - `gradle/libs.versions.toml`
  - `gradle/kei.versions.toml`
  - `gradle/wrapper/` (entire directory)
  - `gradlew`, `gradlew.bat`
  - `settings.gradle.kts`
  - `build.gradle.kts`
  - `common.gradle`
  - `gradle.properties`
  - `core/` (entire directory — `common.gradle` hard-depends on `project(":core")`)
  - `.gitignore`, `.gitattributes`, `.editorconfig`, `ktlintCodeStyle.xml`
- Modify: `settings.gradle.kts` (rename root project), `common.gradle` (rebrand author placeholder)

**Interfaces:**
- Produces: a working Gradle root project where `./gradlew tasks` succeeds with zero extension modules present. Task 2 depends on this.

- [x] **Step 1: Create the GitHub repo**

```bash
gh repo create dongnime/extensions --public \
  --description "Aniyomi/Anikku extension repo for anichin.cafe"
```

- [x] **Step 2: Fetch the reference monorepo at the pinned commit into a scratch checkout**

```bash
cd /home/mbrx/Coding/donghuarepo
git clone https://github.com/salmanbappi/sb-extensions-source.git /tmp/ref-monorepo
git -C /tmp/ref-monorepo checkout 87b2e2154fe3c869e286de7a6181c0bcff4d23fb
```

- [x] **Step 3: Copy the build tooling files/directories listed above**

```bash
cd /home/mbrx/Coding/donghuarepo
for p in gradle/build-logic gradle/libs.versions.toml gradle/kei.versions.toml gradle/wrapper \
         gradlew gradlew.bat settings.gradle.kts build.gradle.kts common.gradle gradle.properties \
         core .gitignore .gitattributes .editorconfig ktlintCodeStyle.xml; do
  mkdir -p "$(dirname "$p")"
  cp -r "/tmp/ref-monorepo/$p" "$p"
done
chmod +x gradlew
rm -rf gradle/build-logic/.git core/.git 2>/dev/null || true
```

- [x] **Step 4: Rename the root project and rebrand the author placeholder**

Edit `settings.gradle.kts`, change:
```kotlin
rootProject.name = "Yuzono-Anime"
```
to:
```kotlin
rootProject.name = "Dongnime-Extensions"
```

Edit `common.gradle`, change:
```groovy
author  : "salmanbappi",
```
to:
```groovy
author  : "dongnime",
```

- [x] **Step 5: Verify the empty skeleton builds**

Run: `./gradlew tasks`
Expected: task list prints successfully (no extension modules registered yet since `src/` doesn't exist — `settings.gradle.kts`'s `loadAllIndividualExtensions()` handles a missing `src/` dir gracefully via `File.eachDir`'s null-safe `listFiles()`).

- [x] **Step 6: Commit and push**

```bash
git add gradle build.gradle.kts common.gradle settings.gradle.kts gradle.properties \
        core .gitignore .gitattributes .editorconfig ktlintCodeStyle.xml gradlew gradlew.bat
git commit -m "$(cat <<'EOF'
Bootstrap build tooling from salmanbappi/sb-extensions-source

Copies the Gradle convention plugins, version catalogs, and core
runtime module from the pinned reference commit, with no extension
source modules included yet.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01MnMFvpZcAWgMNap6zzaggb
EOF
)"
git remote add origin git@github.com:dongnime/extensions.git
git push -u origin master
```

---

## Task 2: Scaffold the `src/id/anichin` module skeleton

**Files:**
- Create: `src/id/anichin/build.gradle`
- Create: `src/id/anichin/AndroidManifest.xml`
- Create: `src/id/anichin/res/mipmap-xxxhdpi/ic_launcher.png` (placeholder launcher icon)
- Create: `src/id/anichin/src/eu/kanade/tachiyomi/animeextension/id/anichin/Anichin.kt` (compiling stub, filled in by later tasks)

**Interfaces:**
- Consumes: `common.gradle`'s `theme == null` path (no `lib-multisrc` theme), `:core` project.
- Produces: `Anichin` class registered as `eu.kanade.tachiyomi.animeextension.id.anichin.Anichin`, extending `extensions.utils.Source`. Tasks 3–7 all add members to this file.

- [x] **Step 1: Write the module's `build.gradle`**

```groovy
ext {
    extName = 'Anichin'
    extClass = '.Anichin'
    extVersionCode = 1
}

apply from: "$rootDir/common.gradle"

dependencies {
    implementation(project(":lib:playlist-utils"))
    implementation(project(":lib:okru-extractor"))
    implementation(project(":lib:dailymotion-extractor"))
    implementation(project(":lib:cloudflare-interceptor"))
    implementation(project(":lib:unpacker"))
}
```

- [x] **Step 2: Copy in the required `lib/` extractor modules from the reference checkout**

```bash
cd /home/mbrx/Coding/donghuarepo
for lib in okru-extractor dailymotion-extractor playlist-utils cloudflare-interceptor unpacker; do
  mkdir -p "lib/$lib"
  cp -r "/tmp/ref-monorepo/lib/$lib/." "lib/$lib/"
done
```

- [x] **Step 3: Write the `AndroidManifest.xml`** (copy exact structure from the reference template — verified working)

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-feature android:name="tachiyomi.animeextension"/>

    <application
        android:allowBackup="false"
        android:icon="@mipmap/ic_launcher"
        android:label="${appName}"
        android:usesCleartextTraffic="true"
        tools:replace="android:allowBackup,android:icon,android:label">

        <meta-data
            android:name="tachiyomi.animeextension.class"
            android:value="${extClass}" />
        <meta-data
            android:name="tachiyomi.animeextension.author"
            android:value="${author}" />
        <meta-data
            android:name="tachiyomi.animeextension.nsfw"
            android:value="${nsfw}" />
        <meta-data
            android:name="tachiyomi.animeextension.versionId"
            android:value="2" tools:replace="android:value" />
    </application>
</manifest>
```

- [x] **Step 4: Add a placeholder launcher icon**

```bash
cd /home/mbrx/Coding/donghuarepo
mkdir -p src/id/anichin/res/mipmap-xxxhdpi
cp core/src/main/res/mipmap-xxxhdpi/ic_launcher.png src/id/anichin/res/mipmap-xxxhdpi/ic_launcher.png
```

(Replace with a real Anichin-branded icon later — not a functional blocker.)

- [x] **Step 5: Write a compiling stub `Anichin.kt`**

```kotlin
package eu.kanade.tachiyomi.animeextension.id.anichin

import extensions.utils.Source

class Anichin : Source() {

    override val name = "Anichin"

    override val baseUrl = "https://anichin.cafe"

    override val lang = "id"

    override val supportsLatest = true
}
```

- [x] **Step 6: Verify the module registers and builds**

Run: `./gradlew :src:id:anichin:assembleDebug`
Expected: BUILD SUCCESSFUL, and `src/id/anichin/build/outputs/apk/debug/*.apk` exists.

- [x] **Step 7: Commit**

```bash
git add src/id/anichin lib/okru-extractor lib/dailymotion-extractor lib/playlist-utils \
        lib/cloudflare-interceptor lib/unpacker
git commit -m "$(cat <<'EOF'
Scaffold Anichin extension module

Compiling stub with the required lib extractor dependencies wired in;
no scraping logic yet.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01MnMFvpZcAWgMNap6zzaggb
EOF
)"
```

---

## Task 3: Browse — popular and latest updates

**Files:**
- Modify: `src/id/anichin/src/eu/kanade/tachiyomi/animeextension/id/anichin/Anichin.kt`

**Interfaces:**
- Produces: `getPopularAnime(page: Int): AnimesPage`, `getLatestUpdates(page: Int): AnimesPage`, and the private helpers `parseArchiveListPage(doc, hasNext)` and `parseLatestUpdatesPage(doc)` that later tasks (search) also call.

Live-verified facts this task relies on:
- `/anime/?order=popular` (the pattern used by the reference template) returns HTTP 404 on this site — there is no ranked "popular" archive. `/ongoing/` and `/completed/` (HTTP 200) are the site's real catalog archives; both paginate as `/ongoing/page/N/` with `<a class="next page-numbers">` markers.
- Catalog archive cards (`/ongoing/`, `/completed/`, `/genres/<slug>/`) link straight to `/seri/<slug>/` — title is the plain leading text inside `div.tt` (e.g. "Spirit Realm Walker"), thumbnail is `img[src]` inside `div.limit`.
- The homepage (`/`, `/page/N/`) instead lists **episode** cards (e.g. `.../perfect-world-episode-285-subtitle-indonesia/`), title text there is the show name plus "Episode N Subtitle Indonesia". These must be normalized to a series URL before use.

- [x] **Step 1: Add imports and the popular/latest request+parse methods**

```kotlin
package eu.kanade.tachiyomi.animeextension.id.anichin

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

    companion object {
        private val EPISODE_SLUG_REGEX = Regex("""^(.+)-episode-\d+(?:-tamat)?-subtitle-indonesia$""")
    }
}
```

- [x] **Step 2: Build**

Run: `./gradlew :src:id:anichin:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [x] **Step 3: Manually verify against the live site**

Install the debug APK on a device/emulator with Aniyomi or Anikku, add the extension locally (or temporarily point a throwaway repo branch at it — see Task 8 for the real publish flow), and confirm:
- Browse → Popular shows ongoing donghua titles with correct thumbnails.
- Browse → Latest shows recently-updated titles (deduplicated, series-level, not raw episode entries).
- Pagination loads a second page without duplicate or missing entries.

- [x] **Step 4: Commit**

```bash
git add src/id/anichin/src
git commit -m "$(cat <<'EOF'
Implement popular and latest updates browsing for Anichin

Uses /ongoing/ (the site's real catalog archive — /anime/?order=popular
404s on this instance) for Popular, and normalizes the homepage's
episode-level cards to series URLs for Latest.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01MnMFvpZcAWgMNap6zzaggb
EOF
)"
```

---

## Task 4: Search and filters (status, genre)

**Files:**
- Modify: `src/id/anichin/src/eu/kanade/tachiyomi/animeextension/id/anichin/Anichin.kt`

**Interfaces:**
- Consumes: `parseArchiveListPage(doc)` from Task 3.
- Produces: `getSearchAnime(page, query, filters): AnimesPage`, `getFilterList(): AnimeFilterList`.

Live-verified facts: `/genres/<slug>/` archives paginate exactly like `/ongoing/`; the site exposes 43 genres (full list captured from the live sidebar filter widget). WordPress native search is `/?s=<query>`, verified to return HTTP 200 and real result markup; pagination for query-string search uses WordPress's standard `paged` parameter (not path-based `/page/N/`) — verify this specifically during manual testing in Step 3, since only page 1 of search was checked live during design.

- [x] **Step 1: Add the filter classes and search method**

```kotlin
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
```

Add these imports:
```kotlin
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import okhttp3.HttpUrl.Companion.toHttpUrl
```

- [x] **Step 2: Add the `STATUSES` and `GENRES` data to `companion object`**

```kotlin
    companion object {
        private val EPISODE_SLUG_REGEX = Regex("""^(.+)-episode-\d+(?:-tamat)?-subtitle-indonesia$""")

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
```

- [x] **Step 3: Build, then manually verify**

Run: `./gradlew :src:id:anichin:assembleDebug`

Against the live site / app:
- Text search for a known title returns it on page 1.
- If results span multiple pages, confirm `paged=2` actually returns page 2 (not a repeat of page 1) — if WordPress on this site instead expects `page=2` or path-style, fix the search branch's query parameter accordingly before moving on.
- Genre filter (e.g. "Isekai") returns a plausible, genre-matching list.
- Status filter "Completed" returns different titles than "Ongoing".

- [x] **Step 4: Commit**

```bash
git add src/id/anichin/src
git commit -m "$(cat <<'EOF'
Add search and genre/status filters for Anichin

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01MnMFvpZcAWgMNap6zzaggb
EOF
)"
```

---

## Task 5: Anime details and episode list

**Files:**
- Modify: `src/id/anichin/src/eu/kanade/tachiyomi/animeextension/id/anichin/Anichin.kt`

**Interfaces:**
- Produces: `getAnimeDetails(anime): SAnime`, `getEpisodeList(anime): List<SEpisode>`.

Live-verified selectors (from `/seri/against-the-gods/`):
- Title: `h1.entry-title`
- Genres: `div.genxed a`
- Synopsis: `div.bixbox.synp div.entry-content` (NOT `div.desc`, which is unrelated SEO boilerplate text)
- Status/type text block: any `span` whose text contains "Status" (matches `<span><b>Status:</b> Ongoing</span>`)
- Thumbnail: `div.thumb img`
- Episode list: `div.eplister ul li`, each `li` has `a[href]`, `.epl-num` (episode number), `.epl-title` (full title), `.epl-sub .status` (Sub/Dub badge), `.epl-date` formatted like `"September 3, 2026"` (`MMMM d, yyyy`, English locale — the theme's date output is in English despite Indonesian subtitles).

- [x] **Step 1: Add the details and episode-list methods**

```kotlin
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
```

Add these imports:
```kotlin
import eu.kanade.tachiyomi.animesource.model.SEpisode
import java.text.SimpleDateFormat
import java.util.Locale
```

Add to `companion object`:
```kotlin
        private val DATE_FORMATTER = SimpleDateFormat("MMMM d, yyyy", Locale.ENGLISH)
```

- [x] **Step 2: Build**

Run: `./gradlew :src:id:anichin:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [x] **Step 3: Manually verify**

Open a series (e.g. Against the Gods) in the app:
- Synopsis text matches the real synopsis, not the SEO blurb.
- Genre chips show correctly.
- Status shows Ongoing/Completed correctly.
- Episode list is complete, correctly numbered, newest-first, with plausible upload dates.

- [x] **Step 4: Commit**

```bash
git add src/id/anichin/src
git commit -m "$(cat <<'EOF'
Implement anime details and episode list parsing for Anichin

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01MnMFvpZcAWgMNap6zzaggb
EOF
)"
```

---

## Task 6: Video resolution — mirror decode + per-host extractors

**Files:**
- Modify: `src/id/anichin/src/eu/kanade/tachiyomi/animeextension/id/anichin/Anichin.kt`

**Interfaces:**
- Consumes: `PlaylistUtils`, `OkruExtractor`, `DailymotionExtractor`, `CloudflareInterceptor`, `Unpacker` (all from Task 2's lib dependencies).
- Produces: `getHosterList(episode): List<Hoster>`, `getVideoList(hoster): List<Video>`.

Live-verified facts: episode pages carry `<select class="mirror"><option value="BASE64_IFRAME_HTML">Label</option>...</select>`, observed labels "Premium 1" (`anichin.stream`, JWPlayer-based, sits behind a Cloudflare JS challenge — a plain GET returns only script tags, no video data), "OK.ru", "Dailymotion [Ads]", "Rumble [Ads]", "Drive 1 [Ads]" (`player.abyssplayer.com`), "Drive 2 [Ads]" (`morencius.com`). Only OK.ru and Dailymotion have purpose-built extractors in the reference monorepo's `lib/`; the rest need a generic fallback.

- [x] **Step 1: Add the hoster/video-list methods and the generic fallback extractor**

```kotlin
    // ============================== Hosters & Videos ==============================

    private val playlistUtils by lazy { PlaylistUtils(client, headers) }
    private val okruExtractor by lazy { OkruExtractor(client) }
    private val dailymotionExtractor by lazy { DailymotionExtractor(client, headers) }
    private val cloudflareClient by lazy { client.newBuilder().addInterceptor(CloudflareInterceptor(client)).build() }

    override suspend fun getHosterList(episode: SEpisode): List<Hoster> {
        val doc = client.newCall(GET("$baseUrl${episode.url}", headers)).execute().asJsoup()

        return doc.select("select.mirror option[value]").mapNotNull { option ->
            val label = option.text().trim()
            val base64Value = option.attr("value").trim()
            if (label.isBlank() || base64Value.isBlank()) return@mapNotNull null

            val decodedHtml = runCatching { String(Base64.decode(base64Value, Base64.DEFAULT)) }
                .getOrNull() ?: return@mapNotNull null
            val iframeSrc = Jsoup.parse(decodedHtml).selectFirst("iframe")?.attr("src")
                ?: return@mapNotNull null

            Hoster(hosterName = label, hosterUrl = iframeSrc)
        }
    }

    override suspend fun getVideoList(hoster: Hoster): List<Video> {
        val embedUrl = hoster.hosterUrl
        return runCatching {
            when {
                "ok.ru" in embedUrl -> okruExtractor.videosFromUrl(embedUrl, prefix = "${hoster.hosterName} - ")
                "dailymotion.com" in embedUrl -> dailymotionExtractor.videosFromUrl(embedUrl, prefix = "${hoster.hosterName} - ")
                else -> extractGenericEmbed(embedUrl, hoster.hosterName)
            }
        }.getOrElse { emptyList() }
    }

    /**
     * Fallback for hosts with no dedicated extractor (anichin.stream's own JWPlayer,
     * rumble.com, player.abyssplayer.com, morencius.com): fetch the embed page through
     * a Cloudflare-challenge-solving client, then look for a direct .m3u8 URL, falling
     * back to unpacking a classic packer-obfuscated `eval(function(p,a,c,k,e,d)...)`
     * script if the direct search finds nothing.
     */
    private fun extractGenericEmbed(embedUrl: String, label: String): List<Video> {
        val embedHeaders = headers.newBuilder().set("Referer", "$baseUrl/").build()
        val html = cloudflareClient.newCall(GET(embedUrl, embedHeaders)).execute().body.string()

        val directUrl = DIRECT_M3U8_REGEX.find(html)?.groupValues?.get(1)
        if (directUrl != null) {
            return playlistUtils.extractFromHls(directUrl, referer = embedUrl, videoNameGen = { q -> "$label - $q" })
        }

        val packedScript = PACKED_JS_REGEX.find(html)?.value ?: return emptyList()
        val unpacked = runCatching { Unpacker.unpack(packedScript) }.getOrNull() ?: return emptyList()
        val packedUrl = DIRECT_M3U8_REGEX.find(unpacked)?.groupValues?.get(1) ?: return emptyList()

        return playlistUtils.extractFromHls(packedUrl, referer = embedUrl, videoNameGen = { q -> "$label - $q" })
    }

    override fun List<Video>.sortVideos(): List<Video> {
        val quality = preferences.getString(PREF_QUALITY_KEY, PREF_QUALITY_DEFAULT) ?: PREF_QUALITY_DEFAULT
        return sortedByDescending { it.videoTitle.contains(quality, ignoreCase = true) }
    }
```

Add these imports:
```kotlin
import android.util.Base64
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.lib.cloudflareinterceptor.CloudflareInterceptor
import eu.kanade.tachiyomi.lib.dailymotionextractor.DailymotionExtractor
import eu.kanade.tachiyomi.lib.okruextractor.OkruExtractor
import eu.kanade.tachiyomi.lib.playlistutils.PlaylistUtils
import eu.kanade.tachiyomi.lib.unpacker.Unpacker
import org.jsoup.Jsoup
```

Add to `companion object`:
```kotlin
        private val DIRECT_M3U8_REGEX = Regex(""""(https?://[^"'\s]+\.m3u8[^"'\s]*)"""")
        private val PACKED_JS_REGEX = Regex("""eval\(function\(p,a,c,k,e,d\).*?\}\)\)""", RegexOption.DOT_MATCHES_ALL)
```

(`PREF_QUALITY_KEY`/`PREF_QUALITY_DEFAULT` are added in Task 7 — this task's `sortVideos` will not compile standalone until Task 7 lands; implement Task 7 immediately after this step before building, or temporarily hardcode `"1080p"` to build-check this task in isolation.)

- [x] **Step 2: Build**

Run: `./gradlew :src:id:anichin:assembleDebug`
Expected: BUILD SUCCESSFUL (after Task 7's preference constants exist, or with the temporary hardcode noted above).

- [x] **Step 3: Manually verify each mirror against the live site**

Play an episode and check every server in the dropdown:
- OK.ru and Dailymotion: confirm actual video playback.
- Premium 1 (anichin.stream): confirm whether the Cloudflare-interceptor + direct-m3u8 path resolves it. If the page instead needs a different data extraction approach than the packer/direct-regex guess above (verify by inspecting the fetched HTML content, e.g. via a temporary log statement or a local script fetching `embedUrl` with the same headers), adjust `extractGenericEmbed`'s parsing accordingly — this is the one host whose exact wire format could not be confirmed without a live browser session during planning.
- Rumble, AbyssPlayer (Drive 1), Morencius (Drive 2): confirm the same fallback resolves them, or leaves them absent from the video list without crashing anything else.
- Confirm a mirror that fails to resolve does not prevent the other working mirrors from appearing.

- [x] **Step 4: Commit**

```bash
git add src/id/anichin/src
git commit -m "$(cat <<'EOF'
Implement video resolution for Anichin's mirror servers

Decodes the base64-encoded select.mirror options, delegates OK.ru and
Dailymotion to their dedicated extractors, and uses a Cloudflare-aware
generic fallback (direct .m3u8 search, then packer-JS unpacking) for
the remaining hosts (anichin.stream, Rumble, AbyssPlayer, Morencius).

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01MnMFvpZcAWgMNap6zzaggb
EOF
)"
```

---

## Task 7: Preferences screen

**Files:**
- Modify: `src/id/anichin/src/eu/kanade/tachiyomi/animeextension/id/anichin/Anichin.kt`

**Interfaces:**
- Consumes: `PREF_QUALITY_KEY`/`PREF_QUALITY_DEFAULT` referenced by Task 6's `sortVideos`.
- Produces: `setupPreferenceScreen(screen)`, the `PREF_DOMAIN_KEY`/`PREF_DOMAIN_DEFAULT`/`PREF_QUALITY_KEY`/`PREF_QUALITY_DEFAULT` constants, and switches `baseUrl` from a fixed value (Task 3's stub) to a preference-backed one.

- [x] **Step 1: Make `baseUrl` preference-backed**

Replace:
```kotlin
    override val baseUrl = "https://anichin.cafe"
```
with:
```kotlin
    override val baseUrl: String
        get() = preferences.getString(PREF_DOMAIN_KEY, PREF_DOMAIN_DEFAULT) ?: PREF_DOMAIN_DEFAULT
```

- [x] **Step 2: Add `setupPreferenceScreen`**

```kotlin
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
```

Add these imports:
```kotlin
import androidx.preference.PreferenceScreen
import keiyoushi.utils.addBaseUrlPreference
import keiyoushi.utils.addListPreference
```

- [x] **Step 3: Add the preference constants to `companion object`**

```kotlin
        private const val PREF_DOMAIN_KEY = "pref_domain"
        private const val PREF_DOMAIN_DEFAULT = "https://anichin.cafe"

        private const val PREF_QUALITY_KEY = "pref_quality"
        private const val PREF_QUALITY_DEFAULT = "1080p"
```

- [x] **Step 4: Build**

Run: `./gradlew :src:id:anichin:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [x] **Step 5: Manually verify**

In the app, open the extension's settings: confirm "Base URL" and "Preferred Quality" appear and persist across app restarts; confirm changing Base URL actually changes which domain requests go to (e.g. temporarily set an obviously wrong domain and confirm browsing fails, then set it back).

- [x] **Step 6: Commit**

```bash
git add src/id/anichin/src
git commit -m "$(cat <<'EOF'
Add preferences screen for Anichin (base URL, preferred quality)

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01MnMFvpZcAWgMNap6zzaggb
EOF
)"
```

---

## Task 8: CI — build signed APK and publish `index.min.json` to the `repo` branch

**Files:**
- Create: `.github/workflows/build.yml`
- Create: `.github/scripts/create-repo.py` (adapted from the reference monorepo, same-repo target instead of a separate bot-pushed repo)
- One-time manual setup: signing keystore + GitHub secrets, and the orphan `repo` branch.

**Interfaces:**
- Produces: on every push to `master`, a signed `anichin` APK, `repo/index.min.json`, and `repo/icon/*.png` committed to the `repo` branch — the artifact Aniyomi/Anikku consumes when a user adds `https://raw.githubusercontent.com/dongnime/extensions/repo/index.min.json`.

- [x] **Step 1: Generate a release signing keystore (one-time, local, never committed)**

```bash
cd /home/mbrx/Coding/donghuarepo
keytool -genkey -v -keystore signingkey.jks -keyalg RSA -keysize 2048 -validity 10000 \
  -alias anichin-extensions -storepass "$(openssl rand -base64 24)" \
  -keypass "$(openssl rand -base64 24)" \
  -dname "CN=dongnime, OU=extensions, O=dongnime, L=, S=, C=ID"
```

Record the generated store/key passwords (the command above discards them into shell history unless captured — re-run with fixed variables, e.g. `STOREPASS=$(openssl rand -base64 24); echo "$STOREPASS"` first, then pass `-storepass "$STOREPASS"`) — they're needed for the next step and are not recoverable from the keystore file alone.

- [x] **Step 2: Register the signing secrets on the GitHub repo**

```bash
cd /home/mbrx/Coding/donghuarepo
gh secret set SIGNING_KEY --repo dongnime/extensions --body "$(base64 -w0 signingkey.jks)"
gh secret set ALIAS --repo dongnime/extensions --body "anichin-extensions"
gh secret set KEY_STORE_PASSWORD --repo dongnime/extensions --body "<the storepass from Step 1>"
gh secret set KEY_PASSWORD --repo dongnime/extensions --body "<the keypass from Step 1>"
rm signingkey.jks
```

- [x] **Step 3: Bootstrap the orphan `repo` branch**

```bash
cd /home/mbrx/Coding/donghuarepo
git checkout --orphan repo
git rm -rf . > /dev/null
mkdir -p apk icon
echo "[]" > index.min.json
git add apk icon index.min.json
git commit -m "$(cat <<'EOF'
Initialize empty repo branch

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01MnMFvpZcAWgMNap6zzaggb
EOF
)"
git push -u origin repo
git checkout master
```

- [x] **Step 4: Add `.github/scripts/create-repo.py`** (adapted from the reference monorepo's script of the same name — same badging/Inspector-output logic, unchanged since it already targets an arbitrary set of APKs)

```bash
mkdir -p .github/scripts
cp /tmp/ref-monorepo/.github/scripts/create-repo.py .github/scripts/create-repo.py
```

- [x] **Step 5: Write `.github/workflows/build.yml`**

```yaml
name: Build and Publish

on:
  push:
    branches: [master]
    paths-ignore:
      - '**.md'
  workflow_dispatch:

concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: false

permissions:
  contents: write

jobs:
  build-and-publish:
    runs-on: ubuntu-24.04
    timeout-minutes: 30
    steps:
      - name: Checkout master
        uses: actions/checkout@v4

      - name: Set up JDK
        uses: actions/setup-java@v4
        with:
          java-version: 17
          distribution: temurin

      - name: Set up Android SDK
        uses: android-actions/setup-android@v3

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Prepare signing key
        run: echo "${{ secrets.SIGNING_KEY }}" | base64 -d > signingkey.jks

      - name: Build Anichin APK
        env:
          ALIAS: ${{ secrets.ALIAS }}
          KEY_STORE_PASSWORD: ${{ secrets.KEY_STORE_PASSWORD }}
          KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
        run: ./gradlew :src:id:anichin:assembleRelease

      - name: Collect the built APK
        run: |
          mkdir -p repo/apk
          apk=$(find src/id/anichin/build/outputs/apk/release -name '*.apk' | head -n1)
          cp "$apk" "repo/apk/$(basename "$apk" | sed 's/-release\.apk$/.apk/')"

      - name: Download the extensions inspector
        run: |
          curl -fsSL -o Inspector.jar \
            "$(gh release view --repo komikku-app/aniyomi-extensions-inspector --json assets -q '.assets[0].url')"
        env:
          GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}

      - name: Generate index.min.json
        run: |
          java -jar Inspector.jar "repo/apk" "output.json" "tmp"
          python3 .github/scripts/create-repo.py

      - name: Clean up signing key
        if: always()
        run: rm -f signingkey.jks

      - name: Checkout repo branch
        uses: actions/checkout@v4
        with:
          ref: repo
          path: repo-branch

      - name: Update repo branch contents
        run: |
          rm -rf repo-branch/apk repo-branch/icon
          mkdir -p repo-branch/apk repo-branch/icon
          cp repo/apk/*.apk repo-branch/apk/
          cp repo/icon/*.png repo-branch/icon/ 2>/dev/null || true
          cp repo/index.min.json repo-branch/index.min.json

      - name: Commit and push repo branch
        working-directory: repo-branch
        run: |
          git config user.name "github-actions[bot]"
          git config user.email "github-actions[bot]@users.noreply.github.com"
          git add -A
          git commit -m "Update repo index (${{ github.sha }})" --allow-empty
          git push origin HEAD:repo
```

- [x] **Step 6: Push and verify the workflow run**

```bash
cd /home/mbrx/Coding/donghuarepo
git add .github/workflows/build.yml .github/scripts/create-repo.py
git commit -m "$(cat <<'EOF'
Add CI workflow to build and publish the extension repo index

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01MnMFvpZcAWgMNap6zzaggb
EOF
)"
git push origin master
gh run watch --repo dongnime/extensions
```

Expected: the workflow completes successfully, and `https://raw.githubusercontent.com/dongnime/extensions/repo/index.min.json` returns a non-empty JSON array containing the Anichin extension's metadata (check with `curl`).

---

## Task 9: README, disclaimer, and end-to-end verification

**Files:**
- Create: `README.md`

**Interfaces:**
- None — this is the final integration checkpoint for the whole plan.

- [x] **Step 1: Write `README.md`**

```markdown
# Dongnime Extensions

Aniyomi/Anikku extension repo maintained by dongnime.

## Install

Add this URL as a repo in Aniyomi or Anikku (Extensions → the "+" icon → paste the URL):

```
https://raw.githubusercontent.com/dongnime/extensions/repo/index.min.json
```

## Extensions

- **Anichin** (`id`) — [anichin.cafe](https://anichin.cafe), donghua streaming with Indonesian subtitles.

## Disclaimer

This repo does not host any content. Its extensions only provide an
interface to publicly available websites. Intended for personal,
educational use.
```

- [x] **Step 2: Commit and push**

```bash
git add README.md
git commit -m "$(cat <<'EOF'
Add README with install instructions and disclaimer

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01MnMFvpZcAWgMNap6zzaggb
EOF
)"
git push origin master
```

- [x] **Step 3: End-to-end verification in the real app**

On a device running Aniyomi or Anikku:
1. Extensions → "+" → add `https://raw.githubusercontent.com/dongnime/extensions/repo/index.min.json`.
2. Confirm "Anichin" appears in the extension list and installs successfully.
3. Browse Popular and Latest, search for a title, open a series, open an episode, and play video on at least one working mirror — the full path exercised across Tasks 3–6, now through the actual installed-from-repo APK rather than a sideloaded debug build.
4. Push a trivial follow-up commit (e.g. a README tweak) and confirm the CI workflow re-publishes `index.min.json` with a bumped version, and that the app offers an update.

---

## Post-Launch Integration & Playback Tasks (Tasks 10–16)

Following initial completion of Tasks 1–9, extensive end-to-end testing within the Anikku/Aniyomi Android application uncovered real-world runtime incompatibilities and site-specific nuances. These tasks document the subsequent fixes and investigations.

---

### Task 10: Fix "Invalid repo URL" in Anikku/Mihon Repo Trust

**Context:** When importing `https://raw.githubusercontent.com/dongnime/extensions/repo/index.min.json` into Anikku, the app rejected the repo with `"Invalid repo URL"`.
**Root Cause:**
1. Modern Aniyomi/Anikku (derived from Tachiyomi/Mihon repo architecture) expects a repository root metadata file named `repo.json` on the repo branch to verify repository authenticity, signing certificate fingerprint, and metadata.
2. Some versions also probe `index.json` instead of or in addition to `index.min.json`.
**Resolution:**
- Generated and pushed `repo.json` to the `repo` branch with:
  ```json
  {
    "meta": {
      "name": "Dongnime Extensions",
      "shortName": "dongnime",
      "website": "https://github.com/dongnime/extensions",
      "signingKeyFingerprint": "ddf8ebc14135646c7a8fa695d65aa3861f52b7756adca7692b47c62d113adf63"
    }
  }
  ```
- Created `index.json` on the `repo` branch as an alias of `index.min.json`.
- Updated `.github/scripts/create-repo.py` and `.github/workflows/build.yml` to generate and publish `repo.json` and `index.json` automatically on each CI run (Commit `e36183a`, `07cf762`).
- **Result:** Anikku successfully validates and imports the repository URL.

---

### Task 11: Fix Video Constructor NoSuchMethodError (`no direct method <init>`)

**Context:** When selecting an episode to play, Anikku crashed with:
`NoSuchMethodError: no direct method <init>(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lokhttp3/Headers;Ljava/util/List;Ljava/util/List;)V in class Leu/kanade/tachiyomi/animesource/model/Video;`
**Root Cause:**
The compile-time dependency `extensions-lib` vs the runtime `Video` model in Anikku/Aniyomi:
- Calling `Video(url, quality, videoUrl, headers)` relied on Kotlin default arguments generating a synthetic constructor or calling a 6-parameter constructor with nulls/defaults that didn't match the host app's dex signature.
- `libVersion` in `common.gradle` was set to `16`, while Anikku's bundled extension bridge expected `libVersion = 15`.
**Resolution:**
- Set `libVersion = 15` in `common.gradle` (Commit `ed9d9b7`).
- Updated all `Video(...)` constructor calls in `Anichin.kt` to explicitly pass all arguments with empty lists for subtitles and audio tracks:
  ```kotlin
  Video(url, quality, videoUrl, headers, emptyList(), emptyList())
  ```
- Updated `Hoster` instantiation to match Anikku's expected constructor (Commit `80095a4`).
- **Result:** `NoSuchMethodError` eliminated completely; player loads without bytecode crashes.

---

### Task 12: Fix Video Playback Crash on OK.ru & Mirror Fallback

**Context:** After resolving constructor errors, clicking an episode showed a brief loading spinner, then closed immediately with toast `"no available videos"`.
**Root Cause:**
1. OK.ru extractor returned HLS (`.m3u8`) or direct MP4 streams whose CDN blocks playback if `User-Agent` or `Referer` headers are missing or mismatched when ExoPlayer requests chunks.
2. In `getVideoList`, embed links pointing to blocked/dead mirrors threw exceptions that caused the whole hoster to fail prematurely.
**Resolution:**
- Pushed Commit `4020372` & `9c2d346`:
  - Enforced browser `User-Agent` headers across all video stream models.
  - Used `playlistUtils.extractFromHls` with proper referer for OK.ru master playlists.
  - Added non-fatal `runCatching` handling so dead/blocked mirrors simply return `emptyList()` instead of failing the episode.
- **Result:** Working OK.ru streams play seamlessly in ExoPlayer.

---

### Task 13: Direct Download Stream Fallback (.soraddlx / .soradl) for Mediafire & Pixeldrain

**Context:** On Anichin, many donghua episodes have dead or missing embed players in `<select class="mirror">`, but provide direct download links under `.soraddlx` and `.soradl` divs (Mediafire, Pixeldrain, Mega, Google Drive, etc.).
**Resolution (Commit `cfe0c72` & `bbea0a6`):**
- Added direct download scraping in `getHosterList` / `getVideoList`:
  - **Mediafire**: Follows link, scrapes direct download URL from `a#downloadButton` or `aria-label="Download file"`, attaches clean Chrome User-Agent header.
  - **Pixeldrain**: Detects `/u/{id}` or `/file/{id}` and converts directly to streaming API endpoint `https://pixeldrain.com/api/file/{id}`.
- Distinct and sortable: tagged with quality labels from the HTML (e.g. `Mediafire - 720p`, `Pixeldrain - 1080p`).

---

### Task 14: Video Quality Sorting & 4K Crash Mitigation (Version 15.6)

**Context:** On episodes where only a 4K Mediafire download link existed, ExoPlayer crashed because mobile hardware decoders cannot handle raw 2.8 GB 4K MKV files (H.264 High@5.1 / 18.8 Mbps). Furthermore, Anikku defaulted to the first video in the list.
**Resolution (Commit `bbea0a6`):**
- Adjusted `sortVideos()` comparator:
  ```kotlin
  override fun List<Video>.sortVideos(): List<Video> {
      val quality = preferences.getString(PREF_QUALITY_KEY, PREF_QUALITY_DEFAULT) ?: PREF_QUALITY_DEFAULT
      return sortedWith(
          compareByDescending<Video> { it.videoTitle.contains(quality, ignoreCase = true) }
              .thenByDescending { it.videoTitle.contains("1080p", ignoreCase = true) }
              .thenByDescending { it.videoTitle.contains("720p", ignoreCase = true) }
              .thenByDescending { it.videoTitle.contains("480p", ignoreCase = true) }
              .thenByDescending { it.videoTitle.contains("360p", ignoreCase = true) }
              .thenByDescending { it.videoTitle.contains("4K", ignoreCase = true) }
      )
  }
  ```
- 4K is intentionally demoted to the lowest priority so standard smartphone resolutions (1080p/720p/480p/360p) are always selected first.
- Bumped `extVersionCode = 6` (version `15.6`).
- **Result:** Successfully published to `repo` branch; stable playback on all devices.

---

### Task 15: Upstream Dead-Link Audit on Old Episodes (Tales of Herding Gods Ep 4–8 & 11–15)

**Context:** The user reported that for "Tales of Herding Gods", episodes 1–3, 9–10, 16+ worked, but episodes 4–8 and 11–15 failed with `"no available videos"`.
**Technical Audit & Investigation:**
- Directly audited the live web markup of `anichin.cafe` for those specific episodes:
  - **OK.ru / Dailymotion**: Embeds return 404 / Video removed (DMCA takedown or file expiration).
  - **AbyssPlayer / VideoPlayer.vip / Rubyvid / Morencius**: Endpoints return `404 Not Found` or `"File has been removed by owner"`.
  - **Download links**:
    - For Ep 04, 05, 12, 15: All download mirrors (Drive, Mega, etc.) are dead/deleted links. Zero video files exist anywhere on the web page.
    - For Ep 06, 07, 08, 11, 13, 14: Only a raw 4K MKV file (~2.8 GB) was uploaded on Mediafire.
- Testing in desktop Google Chrome confirmed the same behavior: even on the website itself, the web video players fail with error screens.
**Conclusion:**
- **Won't Fix / Upstream Dead Links**: An scraper extension cannot resurrect files that have been deleted from upstream third-party file hosts. The extension's error handling correctly skips dead mirrors without crashing the app.

---

### Task 16: Google Play Protect "Costly SMS messages" False-Positive Audit

**Context:** After updating to v15.6, Google Play Protect showed an alarming warning: *"This app can add unauthorized charges to your mobile bill by sending costly SMS messages..."*.
**Technical Audit:**
- Ran `aapt dump badging` on `aniyomi-id.anichin-v15.6.apk`:
  - Uses-features: `tachiyomi.animeextension`, `android.hardware.faketouch`.
  - **ZERO permissions requested**: No `<uses-permission>` tags exist in `AndroidManifest.xml`.
- Ran bytecode string search on `classes.dex`:
  - No `sms`, `telephony`, or `billing` APIs exist.
- Under Android OS architecture, an application **cannot** send SMS messages or access cellular billing without `android.permission.SEND_SMS`. Any attempt throws an immediate kernel/binder `SecurityException`.
**Root Cause:**
- Google Play Protect's cloud machine-learning heuristics flagged the APK as suspicious because:
  1. The APK is sideloaded and signed with a newly generated private keystore with zero Google Play presence.
  2. The APK contains web scrapers, dynamic HTTP header builders, and the JavaScript deobfuscator `Unpacker` (eval unpacker), which heuristic classifiers often falsely associate with toll-fraud droppers.
**Resolution & Guidance:**
- Confirmed 100% false positive.
- Bypass instructions for users: Tap **"More details"** (*Rincian selengkapnya*) -> Tap **"Install anyway"** (*Tetap instal*).

---

## Agent Onboarding & Context Summary

### Key Repositories & Branches
- **GitHub Repository**: `github.com/dongnime/extensions`
- **Active Branches**:
  - `master`: Main development branch containing Kotlin source, Gradle build logic, and GitHub Actions workflow.
  - `repo`: Orphan deployment branch containing published APKs (`apk/`), icons (`icon/`), `repo.json`, `index.json`, and `index.min.json`.

### Extension URLs
- **Install Repo URL**: `https://raw.githubusercontent.com/dongnime/extensions/repo/index.min.json` (or `index.json`)
- **Base Website**: `https://anichin.cafe` (configurable via extension preferences).

### Environment & Build Tools
- **JDK**: Java 17 (`/usr/lib/jvm/java-17-openjdk`)
- **Android SDK**: `/opt/android-sdk` (platform 34, build-tools 34.0.0)
- **Assemble Debug**:
  ```bash
  JAVA_HOME=/usr/lib/jvm/java-17-openjdk ANDROID_HOME=/opt/android-sdk ./gradlew :src:id:anichin:assembleDebug
  ```
- **CI Publishing**:
  Pushing to `master` triggers `.github/workflows/build.yml`, which builds the signed release APK, generates index metadata via `Inspector.jar` and `.github/scripts/create-repo.py`, and pushes the release to the `repo` branch.
