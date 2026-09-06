@file:Suppress("DEPRECATION_ERROR")

package eu.kanade.tachiyomi.lib.bloggerextractor

import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import keiyoushi.utils.bodyString
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray

class BloggerExtractor(private val client: OkHttpClient) {

    suspend fun videosFromUrl(url: String, headers: Headers, prefix: String = ""): List<Video> {
        val bloggerReqHeaders = headers.newBuilder()
            .set("User-Agent", BLOGGER_USER_AGENT)
            .set("Referer", BLOGGER_BASE)
            .build()

        val body = runCatching {
            client.newCall(GET(url, bloggerReqHeaders)).awaitSuccess().bodyString()
        }.getOrNull() ?: return emptyList()

        return getStreamVideos(body, headers, prefix)
            .ifEmpty { getRpcVideos(url, body, headers, prefix) }
    }

    private fun getStreamVideos(body: String, headers: Headers, prefix: String = ""): List<Video> {
        if (body.contains("errorContainer") || !body.contains("\"streams\":[")) return emptyList()

        val bloggerHeaders = headers.newBuilder()
            .set("User-Agent", BLOGGER_USER_AGENT)
            .set("Referer", BLOGGER_BASE)
            .build()

        return body
            .substringAfter("\"streams\":[", "")
            .substringBefore("]")
            .split("},")
            .mapNotNull {
                val videoUrl = it.substringAfter("\"play_url\":\"").substringBefore('"')
                    .takeIf(String::isNotBlank)
                    ?: return@mapNotNull null
                val format = it.substringAfter("\"format_id\":").substringBefore('}')
                val quality = qualityFromFormat(format)
                val label = if (prefix.isNotBlank()) "$prefix - $quality" else "Blogger - $quality"
                Video(videoUrl, label.trim(), videoUrl, bloggerHeaders).copy(
                    mpvArgs = listOf(
                        Pair("user-agent", BLOGGER_USER_AGENT),
                        Pair("referrer", BLOGGER_BASE),
                    ),
                )
            }
    }

    private suspend fun getRpcVideos(
        url: String,
        body: String,
        headers: Headers,
        prefix: String = "",
    ): List<Video> {
        val token = url.toHttpUrl().queryParameter("token")?.takeIf(String::isNotBlank) ?: return emptyList()

        val formSessionId = body.substringAfter("\"FdrFJe\":\"", "").substringBefore("\"")
            .ifBlank { body.substringAfter("FdrFJe\":\"", "").substringBefore("\"") }
        val blogId = body.substringAfter("\"cfb2h\":\"", "").substringBefore("\"")
            .ifBlank { body.substringAfter("cfb2h\":\"", "").substringBefore("\"") }
        val requestId = ((System.currentTimeMillis() / 1000L) % 86400L).toString()

        val rpcUrl = BLOGGER_BASE.toHttpUrl().newBuilder()
            .addPathSegments("_/BloggerVideoPlayerUi/data/batchexecute")
            .addQueryParameter("rpcids", "WcwnYd")
            .addQueryParameter("source-path", "/video.g")
            .addQueryParameter("f.sid", formSessionId)
            .addQueryParameter("bl", blogId)
            .addQueryParameter("hl", "en-US")
            .addQueryParameter("_reqid", requestId)
            .addQueryParameter("rt", "c")
            .build()
            .toString()

        val rpcBody =
            "f.req=%5B%5B%5B%22WcwnYd%22%2C%22%5B%5C%22$token%5C%22%2C%5C%22%5C%22%2C0%5D%22%2Cnull%2C%22generic%22%5D%5D%5D&".toRequestBody()
        val rpcHeaders = Headers.headersOf(
            "accept", "*/*",
            "accept-language", "en-US,en;q=0.9",
            "content-type", "application/x-www-form-urlencoded;charset=UTF-8",
            "priority", "u=1, i",
            "sec-fetch-dest", "empty",
            "sec-fetch-mode", "cors",
            "sec-fetch-site", "same-origin",
            "User-Agent", BLOGGER_USER_AGENT,
            "x-same-domain", "1",
            "Referer", BLOGGER_BASE,
        )

        val rpcString = runCatching {
            client.newCall(POST(rpcUrl, body = rpcBody, headers = rpcHeaders))
                .awaitSuccess().bodyString()
        }.getOrNull() ?: return emptyList()

        if (!rpcString.contains("https://") && !rpcString.contains("googlevideo")) return emptyList()

        val bloggerHeaders = headers.newBuilder()
            .set("User-Agent", BLOGGER_USER_AGENT)
            .set("Referer", BLOGGER_BASE)
            .build()

        val videos = mutableListOf<Video>()

        // 1. Parse JSON response from batchexecute
        runCatching {
            val jsonStart = rpcString.indexOf("[[")
            if (jsonStart != -1) {
                val jsonEnd = rpcString.indexOf("\n", jsonStart).takeIf { it != -1 } ?: rpcString.length
                val jsonLine = rpcString.substring(jsonStart, jsonEnd).trim()
                val outerArray = JSONArray(jsonLine)
                for (i in 0 until outerArray.length()) {
                    val item = outerArray.optJSONArray(i) ?: continue
                    if (item.optString(1) == "WcwnYd") {
                        val innerStr = item.optString(2)
                        if (innerStr.isNotBlank()) {
                            val innerArray = JSONArray(innerStr)
                            val streamsArray = innerArray.optJSONArray(2) ?: continue
                            for (j in 0 until streamsArray.length()) {
                                val streamObj = streamsArray.optJSONArray(j) ?: continue
                                val videoUrl = streamObj.optString(0).takeIf { it.startsWith("http") } ?: continue
                                val itags = streamObj.optJSONArray(1)
                                val format = itags?.optInt(0)?.toString().orEmpty()
                                val quality = qualityFromFormat(format)
                                val label = if (prefix.isNotBlank()) "$prefix - $quality" else "Blogger - $quality"
                                videos.add(
                                    Video(videoUrl, label.trim(), videoUrl, bloggerHeaders).copy(
                                        mpvArgs = listOf(
                                            Pair("user-agent", BLOGGER_USER_AGENT),
                                            Pair("referrer", BLOGGER_BASE),
                                        ),
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }

        if (videos.isNotEmpty()) {
            return videos.distinctBy { it.videoUrl }
        }

        // 2. Fallback regex for extracting googlevideo streams
        runCatching {
            val cleanRpc = rpcString
                .replace("\\\\u003d", "=")
                .replace("\\u003d", "=")
                .replace("\\\\u0026", "&")
                .replace("\\u0026", "&")
                .replace("\\/", "/")
                .replace("\\\"", "\"")

            STREAM_FALLBACK_REGEX.findAll(cleanRpc).forEach { match ->
                val rawUrl = match.groupValues[1]
                val format = match.groupValues[2]
                val videoUrl = rawUrl.replace("\\", "")
                val quality = qualityFromFormat(format)
                val label = if (prefix.isNotBlank()) "$prefix - $quality" else "Blogger - $quality"
                videos.add(
                    Video(videoUrl, label.trim(), videoUrl, bloggerHeaders).copy(
                        mpvArgs = listOf(
                            Pair("user-agent", BLOGGER_USER_AGENT),
                            Pair("referrer", BLOGGER_BASE),
                        ),
                    ),
                )
            }
        }

        return videos.distinctBy { it.videoUrl }
    }

    private fun qualityFromFormat(format: String): String = when (format) {
        "7" -> "240p"
        "18" -> "360p"
        "22" -> "720p"
        "37" -> "1080p"
        else -> if (format.isNotBlank()) "${format}p" else "Unknown"
    }

    companion object {
        const val BLOGGER_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36"
        private const val BLOGGER_BASE = "https://www.blogger.com/"
        private val STREAM_FALLBACK_REGEX = Regex("""(https:[^"\\\]]+googlevideo\.com[^"\\\]]+).*?\[(\d+)\]""")
    }
}
