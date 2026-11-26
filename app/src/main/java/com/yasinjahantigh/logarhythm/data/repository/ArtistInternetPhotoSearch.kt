package com.yasinjahantigh.logarhythm.data.repository

import com.yasinjahantigh.logarhythm.data.analytics.AppLog
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

internal data class WikiPhotoResult(
    val url: String,
    val title: String,
    val description: String,
    val isMusician: Boolean
)

/**
 * Pure-HTTP open-internet artist photo search (Bing Images, Wikipedia PageImages, Wikimedia Commons).
 * Works cleanly across both Latin and Persian artist queries without requiring a VPN.
 */
internal object ArtistInternetPhotoSearch {

    private const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    private const val WIKI_USER_AGENT =
        "LogaRhythmMusicPlayer/2.2 (https://github.com/Yasinjahantigh/LogaRhythm-Music-Player)"

    private val BING_M_ATTR_REGEX = Regex("""\bm="(\{[^"]*?murl[^"]*?\})"""")
    private val RAW_MURL_REGEX = Regex(""""murl"\s*:\s*"(https?://[^"]+)"""")

    private val BLOCKED_IMAGE_HINTS = listOf(
        "/images/artist//",
        "d41d8cd98f00b204e9800998ecf8427e",
        "favicon",
        "sprite",
        "placeholder",
        "default_avatar",
        "wikipedia-logo",
        "commons-logo",
        "ambox",
        "shutterstock",
        "gettyimages",
        "istockphoto",
        "alamy.com",
        "dreamstime",
        "depositphotos",
        "123rf.com"
    )

    private val MUSICIAN_KEYWORDS = listOf(
        "singer", "musician", "rapper", "songwriter", "composer", "vocalist",
        "band", "hip hop", "hip-hop", "pop artist", "rock", "dj", "producer",
        "recording artist", "pianist", "guitarist", "violinist", "music",
        "خواننده", "نوازنده", "آهنگساز", "رپر", "موسیقی", "تنظیم‌کننده", "تنظیم کننده"
    )

    fun containsPersianOrArabic(text: String): Boolean = text.any { it in '\u0600'..'\u06FF' }

    fun isMusicianHint(title: String, description: String): Boolean {
        val combined = "$title $description".lowercase()
        return MUSICIAN_KEYWORDS.any { combined.contains(it) }
    }

    fun isUsableWebPhotoUrl(rawUrl: String?): Boolean {
        if (rawUrl.isNullOrBlank()) return false
        val trimmed = rawUrl.trim()
        if (!trimmed.startsWith("https://", ignoreCase = true)) return false
        val pathOnly = trimmed.substringBefore('?').substringBefore('#').lowercase()
        if (pathOnly.endsWith(".svg") || pathOnly.endsWith(".ico") || pathOnly.endsWith(".gif")) {
            return false
        }
        val lower = trimmed.lowercase()
        return BLOCKED_IMAGE_HINTS.none { lower.contains(it) }
    }

    suspend fun searchWebImages(
        client: OkHttpClient,
        query: String,
        limit: Int = 16
    ): List<ArtistPhotoCandidate> {
        val term = query.trim()
        if (term.isBlank()) return emptyList()
        val taggedQuery = if (containsPersianOrArabic(term)) "$term خواننده" else "$term singer"
        val firstPass = fetchBingOnce(client, taggedQuery, term, limit)
        if (firstPass.size >= 3) return firstPass
        val fallbackPass = fetchBingOnce(client, term, term, limit)
        return (firstPass + fallbackPass).distinctBy { it.url }.take(limit)
    }

    private fun fetchBingOnce(
        client: OkHttpClient,
        searchQuery: String,
        labelFallback: String,
        limit: Int
    ): List<ArtistPhotoCandidate> = runCatching {
        val encoded = URLEncoder.encode(searchQuery, "UTF-8")
        val url = "https://www.bing.com/images/search?q=$encoded&first=1&count=28&FORM=HDRSC2"
        val http = client.newBuilder()
            .connectTimeout(7, TimeUnit.SECONDS)
            .readTimeout(7, TimeUnit.SECONDS)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", BROWSER_USER_AGENT)
            .header("Accept-Language", "fa-IR,fa;q=0.9,en-US;q=0.8,en;q=0.7")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                AppLog.w("ArtistPhoto", "Bing image search HTTP ${response.code} for '$searchQuery'")
                return emptyList()
            }
            val html = response.body?.string().orEmpty()
            parseBingImageHtml(html, labelFallback, limit)
        }
    }.onFailure { e ->
        AppLog.w("ArtistPhoto", "Bing image search failed for '$searchQuery': ${e.message}")
    }.getOrDefault(emptyList())

    suspend fun searchWikipediaPhotos(
        client: OkHttpClient,
        query: String
    ): List<WikiPhotoResult> = coroutineScope {
        val term = query.trim()
        if (term.isBlank()) return@coroutineScope emptyList()
        val langs = if (containsPersianOrArabic(term)) listOf("fa", "en") else listOf("en", "fa")
        langs.map { lang ->
            async { fetchWikipediaLang(client, term, lang) }
        }.awaitAll()
            .flatten()
            .distinctBy { it.url }
            .sortedByDescending { it.isMusician }
    }

    private fun fetchWikipediaLang(
        client: OkHttpClient,
        query: String,
        lang: String
    ): List<WikiPhotoResult> = runCatching {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://$lang.wikipedia.org/w/api.php?action=query&format=json" +
            "&generator=search&gsrsearch=$encoded&gsrlimit=6" +
            "&prop=pageimages|pageterms&piprop=original|thumbnail&pithumbsize=800&wbptterms=description"
        val http = client.newBuilder()
            .connectTimeout(7, TimeUnit.SECONDS)
            .readTimeout(7, TimeUnit.SECONDS)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", WIKI_USER_AGENT)
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string().orEmpty()
            parseWikipediaJson(body)
        }
    }.onFailure { e ->
        AppLog.w("ArtistPhoto", "Wikipedia ($lang) search failed for '$query': ${e.message}")
    }.getOrDefault(emptyList())

    suspend fun searchWikimediaCommons(
        client: OkHttpClient,
        query: String,
        limit: Int = 6
    ): List<ArtistPhotoCandidate> = runCatching {
        val term = query.trim()
        if (term.isBlank()) return emptyList()
        val encoded = URLEncoder.encode(term, "UTF-8")
        val url = "https://commons.wikimedia.org/w/api.php?action=query&format=json" +
            "&generator=search&gsrnamespace=6&gsrsearch=$encoded&gsrlimit=$limit" +
            "&prop=imageinfo&iiprop=url|mime|size&iiurlwidth=800"
        val http = client.newBuilder()
            .connectTimeout(7, TimeUnit.SECONDS)
            .readTimeout(7, TimeUnit.SECONDS)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", WIKI_USER_AGENT)
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string().orEmpty()
            parseWikimediaCommonsJson(body).take(limit)
        }
    }.onFailure { e ->
        AppLog.w("ArtistPhoto", "Wikimedia Commons search failed for '$query': ${e.message}")
    }.getOrDefault(emptyList())

    fun parseBingImageHtml(
        html: String,
        fallbackLabel: String,
        limit: Int = 16
    ): List<ArtistPhotoCandidate> {
        if (html.isBlank()) return emptyList()
        val out = mutableListOf<ArtistPhotoCandidate>()
        val seen = HashSet<String>()

        for (match in BING_M_ATTR_REGEX.findAll(html)) {
            if (out.size >= limit) break
            val unescaped = decodeHtmlEntities(match.groupValues[1])
            val obj = runCatching { JSONObject(unescaped) }.getOrNull() ?: continue
            val murl = normalizeWebUrl(obj.optString("murl"))
            if (!isUsableWebPhotoUrl(murl)) continue
            val mw = obj.optInt("mw", 0)
            val mh = obj.optInt("mh", 0)
            if ((mw in 1 until 160) || (mh in 1 until 160)) continue
            if (!seen.add(murl)) continue
            val title = obj.optString("t")
                .replace("\uE000", "")
                .replace("\uE001", "")
                .trim()
                .ifBlank { fallbackLabel }
            out += ArtistPhotoCandidate(url = murl, label = title, source = "Web")
        }

        if (out.size < limit) {
            for (match in RAW_MURL_REGEX.findAll(html)) {
                if (out.size >= limit) break
                val raw = match.groupValues[1].replace("\\/", "/")
                val url = normalizeWebUrl(raw)
                if (isUsableWebPhotoUrl(url) && seen.add(url)) {
                    out += ArtistPhotoCandidate(url = url, label = fallbackLabel, source = "Web")
                }
            }
        }
        return out
    }

    fun parseWikipediaJson(json: String): List<WikiPhotoResult> {
        if (json.isBlank()) return emptyList()
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
        val pages = root.optJSONObject("query")?.optJSONObject("pages") ?: return emptyList()
        val out = mutableListOf<WikiPhotoResult>()
        val keys = pages.keys()
        while (keys.hasNext()) {
            val page = pages.optJSONObject(keys.next()) ?: continue
            val title = page.optString("title").trim()
            val thumb = page.optJSONObject("thumbnail")?.optString("source")?.trim().orEmpty()
            val original = page.optJSONObject("original")?.optString("source")?.trim().orEmpty()
            // Prefer 800px thumbnail when available so Coil loads crisply without huge RAW images.
            val picked = normalizeWebUrl(thumb.ifBlank { original })
            if (!isUsableWebPhotoUrl(picked)) continue
            val desc = page.optJSONObject("terms")
                ?.optJSONArray("description")
                ?.optString(0)
                ?.trim()
                .orEmpty()
            out += WikiPhotoResult(
                url = picked,
                title = title,
                description = desc,
                isMusician = isMusicianHint(title, desc)
            )
        }
        return out.sortedByDescending { it.isMusician }
    }

    fun parseWikimediaCommonsJson(json: String): List<ArtistPhotoCandidate> {
        if (json.isBlank()) return emptyList()
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
        val pages = root.optJSONObject("query")?.optJSONObject("pages") ?: return emptyList()
        val out = mutableListOf<ArtistPhotoCandidate>()
        val keys = pages.keys()
        while (keys.hasNext()) {
            val page = pages.optJSONObject(keys.next()) ?: continue
            val title = page.optString("title").removePrefix("File:").substringBeforeLast('.').trim()
            val info = page.optJSONArray("imageinfo")?.optJSONObject(0) ?: continue
            val mime = info.optString("mime").lowercase()
            if (mime.isNotBlank() && mime !in setOf("image/jpeg", "image/jpg", "image/png", "image/webp")) {
                continue
            }
            val w = info.optInt("width", 0)
            val h = info.optInt("height", 0)
            if ((w in 1 until 200) || (h in 1 until 200)) continue
            val thumb = info.optString("thumburl").trim()
            val raw = info.optString("url").trim()
            val url = normalizeWebUrl(thumb.ifBlank { raw })
            if (!isUsableWebPhotoUrl(url)) continue
            out += ArtistPhotoCandidate(
                url = url,
                label = title.ifBlank { "Commons" },
                source = "Commons"
            )
        }
        return out
    }

    private fun decodeHtmlEntities(raw: String): String = raw
        .replace("&quot;", "\"")
        .replace("&amp;", "&")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")

    private fun normalizeWebUrl(url: String): String {
        val clean = url.trim()
        return if (clean.startsWith("http://", ignoreCase = true) &&
            (clean.contains("wikimedia.org") || clean.contains("wikipedia.org") || clean.contains("deezer") || clean.contains("mzstatic.com"))
        ) {
            "https://" + clean.substring(7)
        } else {
            clean
        }
    }
}
