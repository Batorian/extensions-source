package novelsourcery.lib.siteparsers.parsers

import novelsourcery.lib.siteparsers.SiteParser
import novelsourcery.lib.siteparsers.combined
import novelsourcery.lib.siteparsers.domainKey
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

class NovelWorldTranslationsParser : SiteParser {
    override fun canHandle(doc: Document, url: HttpUrl) = url.host == "novelworldtranslations.blogspot.com"

    override fun parse(doc: Document, url: HttpUrl, client: OkHttpClient, headers: Headers): String {
        doc.select(".separator img").remove()
        doc.select(".entry-content a").filter { el ->
            el.attr("href").contains("https://novelworldtranslations.blogspot.com")
        }.forEach { it.parent()?.remove() }
        val title = doc.select(".entry-title").first()?.text() ?: ""

        val rawHtml = doc.select(".entry-content").html()

        val paragraphs = rawHtml
            .split(Regex("\n\\s*\n+"))
            .mapNotNull { chunk ->
                val fragment = Jsoup.parseBodyFragment(chunk).body()
                // Drop the indent-only spans (e.g. <span>&nbsp;&nbsp; &nbsp;</span>)
                fragment.select("span").forEach { el ->
                    if (el.text().replace("\u00a0", "").trim().isEmpty()) el.remove()
                }
                val html = fragment.html()
                    .replace("&nbsp;", "")
                    .trim()
                if (html.isEmpty()) null else "<p>$html</p>"
            }

        return combined(title, paragraphs.joinToString(""))
    }
}
