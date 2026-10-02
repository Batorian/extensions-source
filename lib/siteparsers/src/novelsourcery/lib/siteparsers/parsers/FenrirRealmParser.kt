package novelsourcery.lib.siteparsers.parsers

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import novelsourcery.lib.siteparsers.SiteParser
import novelsourcery.lib.siteparsers.combined
import novelsourcery.lib.siteparsers.domainKey
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.TextNode

private val INVISIBLE_CHARS = Regex("[\\u200B\\u200C\\u200D\\u2060\\uFEFF]")

class FenrirRealmParser : SiteParser {
    override fun canHandle(doc: Document, url: HttpUrl) = url.domainKey() == "fenrirealm"

    override fun parse(doc: Document, url: HttpUrl, client: OkHttpClient, headers: Headers): String {
        // Reuse the page's own path - it's already in the exact "/series/<slug>/.../chapter-N[-part]"
        // shape the API expects, so there's no need to re-derive slug/number ourselves.
        val apiUrl = "${url.scheme}://${url.host}/api/new/v2${url.encodedPath}"

        val response = client.newCall(okhttp3.Request.Builder().url(apiUrl).headers(headers).build()).execute()
        val json = Json.parseToJsonElement(response.body.string()).jsonObject

        val number = json["number"]?.jsonPrimitive?.contentOrNull
        val rawTitle = json["name"]?.jsonPrimitive?.contentOrNull?.trim()
        val title = rawTitle?.takeIf { it.isNotBlank() } ?: number?.let { "Chapter $it" } ?: ""

        val rawContent = json["content"]?.jsonPrimitive?.content ?: ""
        val body = Jsoup.parseBodyFragment(rawContent).body()

        // Drop the anti-scraping camouflage: a <style> defining a visually-hidden
        // class, paired with aria-hidden divs full of fake base64-looking text.
        body.select("style").remove()
        body.select("[aria-hidden=true]").remove()

        // Strip zero-width characters injected mid-word/mid-sentence throughout the
        // remaining text, rather than only in throwaway paragraphs.
        body.traverse { node, _ ->
            if (node is TextNode) {
                val cleaned = node.text().replace(INVISIBLE_CHARS, "")
                if (cleaned != node.text()) node.text(cleaned)
            }
        }

        // After stripping invisible chars, some paragraphs that were entirely made
        // of them collapse to empty - remove those leftovers.
        body.select("p").forEach { p ->
            if (p.text().trim().isEmpty()) p.remove()
        }

        return combined(title, body.html())
    }
}
