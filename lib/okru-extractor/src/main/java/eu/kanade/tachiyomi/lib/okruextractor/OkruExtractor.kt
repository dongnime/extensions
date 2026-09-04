@file:Suppress("DEPRECATION_ERROR")

package eu.kanade.tachiyomi.lib.okruextractor

import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.lib.playlistutils.PlaylistUtils
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.OkHttpClient
import org.json.JSONObject

class OkruExtractor(private val client: OkHttpClient, private val headers: Headers = Headers.EMPTY) {
    private val playlistUtils by lazy { PlaylistUtils(client) }

    private fun fixQuality(quality: String): String {
        val qualities = listOf(
            Pair("ultra", "2160p"),
            Pair("quad", "1440p"),
            Pair("full", "1080p"),
            Pair("hd", "720p"),
            Pair("sd", "480p"),
            Pair("low", "360p"),
            Pair("lowest", "240p"),
            Pair("mobile", "144p"),
        )
        return qualities.find { it.first == quality }?.second ?: quality
    }

    fun videosFromUrl(url: String, prefix: String = "", fixQualities: Boolean = true): List<Video> {
        val document = runCatching {
            client.newCall(GET(url, headers)).execute().asJsoup()
        }.getOrNull() ?: return emptyList()

        val videoString = document.selectFirst("div[data-options]")
            ?.attr("data-options")
            ?: return emptyList()

        val videos = mutableListOf<Video>()

        runCatching {
            val json = JSONObject(videoString)
            val flashvars = json.optJSONObject("flashvars") ?: json
            val metadataStr = flashvars.optString("metadata")
            val metadata = if (!metadataStr.isNullOrBlank()) {
                runCatching { JSONObject(metadataStr) }.getOrNull()
            } else {
                flashvars.optJSONObject("metadata")
            }

            if (metadata != null) {
                val hlsUrl = metadata.optString("hlsManifestUrl")
                if (hlsUrl.isNotBlank() && hlsUrl.startsWith("http")) {
                    runCatching {
                        videos.addAll(playlistUtils.extractFromHls(hlsUrl, videoNameGen = { "Okru:$it".addPrefix(prefix) }))
                    }
                }

                val videosArray = metadata.optJSONArray("videos")
                if (videosArray != null && videosArray.length() > 0) {
                    for (i in 0 until videosArray.length()) {
                        val vObj = videosArray.optJSONObject(i) ?: continue
                        val vUrl = vObj.optString("url")
                        val vName = vObj.optString("name")
                        if (vUrl.startsWith("https://")) {
                            val quality = if (fixQualities) fixQuality(vName) else vName
                            videos.add(
                                Video(
                                    vUrl,
                                    "Okru:$quality".addPrefix(prefix),
                                    vUrl,
                                    headers,
                                    emptyList(),
                                    emptyList(),
                                ),
                            )
                        }
                    }
                }
            }
        }

        if (videos.isNotEmpty()) {
            return videos
        }

        return when {
            "ondemandHls" in videoString -> {
                val playlistUrl = videoString.extractLink("ondemandHls")
                playlistUtils.extractFromHls(playlistUrl, videoNameGen = { "Okru:$it".addPrefix(prefix) })
            }

            "ondemandDash" in videoString -> {
                val playlistUrl = videoString.extractLink("ondemandDash")
                playlistUtils.extractFromDash(playlistUrl, videoNameGen = { "Okru:$it".addPrefix(prefix) })
            }

            else -> videosFromJson(videoString, prefix, fixQualities)
        }
    }

    private fun String.addPrefix(prefix: String) = prefix.takeIf(String::isNotBlank)
        ?.let { "$prefix $this" }
        ?: this

    private fun String.extractLink(attr: String) = substringAfter("$attr\\\":\\\"")
        .substringBefore("\\\"")
        .replace("\\\\u0026", "&")

    private fun videosFromJson(videoString: String, prefix: String = "", fixQualities: Boolean = true): List<Video> {
        val arrayData = videoString.substringAfter("\\\"videos\\\":[{\\\"name\\\":\\\"")
            .substringBefore("]")

        return arrayData.split("{\\\"name\\\":\\\"").reversed().mapNotNull { data ->
            val videoUrl = data.extractLink("url")
            val quality = data.substringBefore("\\\"").let {
                if (fixQualities) fixQuality(it) else it
            }
            val videoQuality = "Okru:$quality".addPrefix(prefix)

            if (videoUrl.startsWith("https://")) {
                Video(
                    videoUrl,
                    videoQuality,
                    videoUrl,
                    headers,
                    emptyList(),
                    emptyList(),
                )
            } else {
                null
            }
        }
    }
}
