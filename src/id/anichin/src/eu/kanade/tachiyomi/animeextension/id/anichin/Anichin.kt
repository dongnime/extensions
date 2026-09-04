package eu.kanade.tachiyomi.animeextension.id.anichin

import androidx.preference.PreferenceScreen
import extensions.utils.Source

class Anichin : Source() {

    override val name = "Anichin"

    override val baseUrl = "https://anichin.cafe"

    override val lang = "id"

    override val supportsLatest = true

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
    }
}
