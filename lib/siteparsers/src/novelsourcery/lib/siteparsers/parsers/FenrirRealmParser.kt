package novelsourcery.lib.siteparsers.parsers

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import novelsourcery.lib.siteparsers.SiteParser
import novelsourcery.lib.siteparsers.combined
import novelsourcery.lib.siteparsers.domainKey
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

class FenrirRealmParser : SiteParser {
    override fun canHandle(doc: Document, url: HttpUrl) = url.domainKey() == "fenrirealm"

    override fun parse(doc: Document, url: HttpUrl, client: OkHttpClient, headers: Headers): String {
        val apiBaseUrl = "${url.scheme}://${url.host}/api/new/v2"
        val response = client.newCall(
            Request.Builder().url(apiBaseUrl + url.encodedPath).headers(headers).build(),
        ).execute()
        val chapter = Json.decodeFromString<ChapterDto>(response.body.string())
        val chapterDoc = Jsoup.parseBodyFragment(chapter.content)

        // Matches extractChapterContentFromDOM's fetchChapterWithAPI branch, which
        // uses the whole parsed-fragment body rather than selecting .reader-area.
        val readerArea = chapterDoc.body()

        // Strip real comment/reaction sections structurally. Never match on prose
        // text: a chapter sentence containing the word "comment" used to cut the
        // rest of the chapter off.
        readerArea.select("#comments").forEach { it.remove() }
        readerArea.select("h3:containsOwn(What do you think)").forEach { heading ->
            val section = heading.parents().firstOrNull { it.parent() === readerArea }
            (section ?: heading).remove()
        }

        // Remove invisible garbage divs
        val garbagePattern = "^((?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=|[A-Za-z0-9+/]{4}))(.{1,4})?$"
        readerArea.select("div[aria-hidden=true]:matchesOwn($garbagePattern)").forEach { it.remove() }

        val content = readerArea.children().joinToString("") { it.outerHtml() }
        val title = chapter.name?.trim().orEmpty()

        return combined(title, content)
    }

    @Serializable
    class ChapterDto(
        val id: Int,
        val name: String? = null,
        val title: String? = null,
        val content: String,
        val has_illustration: Boolean,
    )
}
