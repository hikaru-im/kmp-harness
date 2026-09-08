package im.hikaru.ruoyi.framework.xss.core.clean

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.safety.Safelist

class JsoupXssCleaner : XssCleaner {
    private val safelist: Safelist = Safelist.relaxed()
        .addAttributes(":all", "style", "class")
        .addAttributes("a", "target")
        .addProtocols("img", "src", "data")

    override fun clean(html: String): String = Jsoup.clean(
        html,
        "",
        safelist,
        Document.OutputSettings().prettyPrint(false),
    )
}
