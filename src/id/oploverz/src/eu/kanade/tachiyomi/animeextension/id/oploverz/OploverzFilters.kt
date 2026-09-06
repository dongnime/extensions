package eu.kanade.tachiyomi.animeextension.id.oploverz

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList

object OploverzFilters {
    open class QueryPartFilter(
        displayName: String,
        val vals: Array<Pair<String, String>>,
    ) : AnimeFilter.Select<String>(
        displayName,
        vals.map { it.first }.toTypedArray(),
    ) {
        fun toQueryPart(name: String): String {
            val v = vals[state].second
            return if (v.isNotBlank()) "&$name=$v" else ""
        }
    }

    open class CheckBoxFilterList(name: String, values: List<CheckBox>) : AnimeFilter.Group<AnimeFilter.CheckBox>(name, values)

    private class CheckBoxVal(name: String, state: Boolean = false) : AnimeFilter.CheckBox(name, state)

    private inline fun <reified R> AnimeFilterList.asQueryPart(name: String): String = (this.getFirst<R>() as QueryPartFilter).toQueryPart(name)

    private inline fun <reified R> AnimeFilterList.getFirst(): R = this.filterIsInstance<R>().first()

    private inline fun <reified R> AnimeFilterList.parseCheckbox(
        options: Array<Pair<String, String>>,
        name: String,
    ): String = (this.getFirst<R>() as CheckBoxFilterList).state
        .mapNotNull { checkbox ->
            if (checkbox.state) {
                options.find { it.first == checkbox.name }?.second
            } else {
                null
            }
        }.joinToString("&$name[]=").let {
            if (it.isBlank()) {
                ""
            } else {
                "&$name[]=$it"
            }
        }

    class GenreFilter :
        CheckBoxFilterList(
            "Genre",
            FiltersData.GENRE.map { CheckBoxVal(it.first, false) },
        )

    class TypeFilter : QueryPartFilter("Type", FiltersData.TYPE)

    class StatusFilter : QueryPartFilter("Status", FiltersData.STATUS)

    class OrderFilter : QueryPartFilter("Sort By", FiltersData.ORDER)

    val FILTER_LIST
        get() = AnimeFilterList(
            AnimeFilter.Header("Ignored with text search"),
            GenreFilter(),
            TypeFilter(),
            StatusFilter(),
            OrderFilter(),
        )

    data class FilterSearchParams(
        val filter: String = "",
    )

    internal fun getSearchParameters(filters: AnimeFilterList): FilterSearchParams {
        if (filters.isEmpty()) return FilterSearchParams()
        return FilterSearchParams(
            filters.parseCheckbox<GenreFilter>(FiltersData.GENRE, "genre") +
                filters.asQueryPart<TypeFilter>("type") +
                filters.asQueryPart<StatusFilter>("status") +
                filters.asQueryPart<OrderFilter>("order"),
        )
    }

    private object FiltersData {
        val ORDER = arrayOf(
            Pair("Default", ""),
            Pair("Popular", "popular"),
            Pair("Latest Update", "update"),
            Pair("Latest Added", "latest"),
            Pair("Rating", "rating"),
            Pair("A-Z", "title"),
            Pair("Z-A", "titlereverse"),
        )

        val STATUS = arrayOf(
            Pair("All", ""),
            Pair("Ongoing", "ongoing"),
            Pair("Completed", "completed"),
            Pair("Upcoming", "upcoming"),
        )

        val TYPE = arrayOf(
            Pair("All", ""),
            Pair("Anime", "anime"),
            Pair("Movie", "movie"),
            Pair("Special", "special"),
            Pair("Drama", "drama"),
            Pair("TV Show", "tv show"),
        )

        val GENRE = arrayOf(
            Pair("Action", "action"),
            Pair("Adult Cast", "adult-cast"),
            Pair("Adventure", "adventure"),
            Pair("Anthropomorphic", "anthropomorphic"),
            Pair("Avant Garde", "avant-garde"),
            Pair("Award Winning", "award-winning"),
            Pair("Boys Love", "boys-love"),
            Pair("CGDCT", "cgdct"),
            Pair("Childcare", "childcare"),
            Pair("Comedy", "comedy"),
            Pair("Crossdressing", "crossdressing"),
            Pair("Delinquents", "delinquents"),
            Pair("Detective", "detective"),
            Pair("Drama", "drama"),
            Pair("Ecchi", "ecchi"),
            Pair("Educational", "educational"),
            Pair("Erotica", "erotica"),
            Pair("Fantasy", "fantasy"),
            Pair("Gag Humor", "gag-humor"),
            Pair("Girls Love", "girls-love"),
            Pair("Gore", "gore"),
            Pair("Gourmet", "gourmet"),
            Pair("Harem", "harem"),
            Pair("Hentai", "hentai"),
            Pair("High Stakes Game", "high-stakes-game"),
            Pair("Historical", "historical"),
            Pair("Horror", "horror"),
            Pair("Idols (Female)", "idols-female"),
            Pair("Idols (Male)", "idols-male"),
            Pair("Isekai", "isekai"),
            Pair("Iyashikei", "iyashikei"),
            Pair("Josei", "josei"),
            Pair("Kids", "kids"),
            Pair("KPop Demon Hunters", "kpop-demon-hunters"),
            Pair("Love Polygon", "love-polygon"),
            Pair("Love Status Quo", "love-status-quo"),
            Pair("Magical Sex Shift", "magical-sex-shift"),
            Pair("Mahou Shoujo", "mahou-shoujo"),
            Pair("Martial Arts", "martial-arts"),
            Pair("Mecha", "mecha"),
            Pair("Medical", "medical"),
            Pair("Military", "military"),
            Pair("Music", "music"),
            Pair("Mystery", "mystery"),
            Pair("Mythology", "mythology"),
            Pair("Netflix", "netflix"),
            Pair("Organized Crime", "organized-crime"),
            Pair("Otaku Culture", "otaku-culture"),
            Pair("Parody", "parody"),
            Pair("Performing Arts", "performing-arts"),
            Pair("Pets", "pets"),
            Pair("Psychological", "psychological"),
            Pair("Racing", "racing"),
            Pair("Reincarnation", "reincarnation"),
            Pair("Reverse Harem", "reverse-harem"),
            Pair("Romance", "romance"),
            Pair("Romantic Subtext", "romantic-subtext"),
            Pair("Samurai", "samurai"),
            Pair("School", "school"),
            Pair("Sci-Fi", "sci-fi"),
            Pair("Seinen", "seinen"),
            Pair("Shoujo", "shoujo"),
            Pair("Shounen", "shounen"),
            Pair("Showbiz", "showbiz"),
            Pair("Slice of Life", "slice-of-life"),
            Pair("Space", "space"),
            Pair("Sports", "sports"),
            Pair("Strategy Game", "strategy-game"),
            Pair("Super Power", "super-power"),
            Pair("Supernatural", "supernatural"),
            Pair("Survival", "survival"),
            Pair("Suspense", "suspense"),
            Pair("Team Sports", "team-sports"),
            Pair("Time Travel", "time-travel"),
            Pair("Urban Fantasy", "urban-fantasy"),
            Pair("Vampire", "vampire"),
            Pair("Video Game", "video-game"),
            Pair("Villainess", "villainess"),
            Pair("Visual Arts", "visual-arts"),
            Pair("Workplace", "workplace"),
        )
    }
}
