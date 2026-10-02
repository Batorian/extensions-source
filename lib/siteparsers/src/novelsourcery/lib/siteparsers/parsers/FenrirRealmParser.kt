package novelsourcery.lib.siteparsers.parsers

import novelsourcery.lib.siteparsers.SiteParser
import novelsourcery.lib.siteparsers.combined
import novelsourcery.lib.siteparsers.domainKey
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document

class FenrirRealmParser : SiteParser {
    override fun canHandle(doc: Document, url: HttpUrl) = url.domainKey() == "fenrirealm"

    override fun parse(doc: Document, url: HttpUrl, client: OkHttpClient, headers: Headers): String {
        // 1. We extract directly from the 'doc' provided by the app! No extra network calls.
        // This exactly matches the extension's fallback/standard extraction logic.
        val readerArea = doc.selectFirst("div.reader-area[id^=reader-area]")
            ?: doc.selectFirst("div.reader-area")
            ?: doc.selectFirst("div[role=region][id^=reader-area]")
            ?: throw Exception("Could not find reader area in the HTML")

        // 2. Clean up comments and irrelevant sections
        readerArea.select("#comments").forEach { it.remove() }
        readerArea.select("h3:containsOwn(What do you think)").forEach { heading ->
            val section = heading.parents().firstOrNull { it.parent() === readerArea }
            (section ?: heading).remove()
        }

        // 3. Remove invisible garbage divs inserted by the site
        val garbagePattern = "^((?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=|[A-Za-z0-9+/]{4}))(.{1,4})?$"
        readerArea.select("div[aria-hidden=true]:matchesOwn($garbagePattern)").forEach { it.remove() }

        // 4. Extract content and title
        val content = readerArea.children().joinToString("") { it.outerHtml() }

        // Grab the title from the standard webpage header
        val title = doc.selectFirst("#reader-area h2")
            ?.text()
            ?.trim()
            .orEmpty()

        return combined(title, content)
    }
}
