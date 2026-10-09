package com.trio.atmo.engine

import org.jsoup.Jsoup
import org.jsoup.safety.Safelist

object TrackerStripper {

    private val KNOWN_TRACKER_DOMAINS = listOf(
        "list-manage.com/track",
        "open.http.cool",
        "pixel.watch",
        "email-analytics",
        "beacon.gif",
        "track.hubspot.com",
        "mandrillapp.com/track"
    )

    fun sanitizeEmailHtml(rawHtml: String): String {
        val document = Jsoup.parse(rawHtml)

        // 1. Strip all <script> and <style> injection tags
        document.select("script, style, iframe, object, embed").remove()

        // 2. Scan and remove tracking <img> elements (1x1 pixels or known tracker URLs)
        val images = document.select("img")
        for (img in images) {
            val src = img.attr("src")
            val width = img.attr("width")
            val height = img.attr("height")

            val isZeroSize = (width == "1" || width == "0" || height == "1" || height == "0")
            val isKnownTracker = KNOWN_TRACKER_DOMAINS.any { domain -> src.contains(domain, ignoreCase = true) }

            if (isZeroSize || isKnownTracker) {
                img.remove()
            } else {
                // Strip lazy tracking tags
                img.removeAttr("data-src")
                img.removeAttr("onload")
                img.removeAttr("onerror")
            }
        }

        // 3. Convert all links to rel="noreferrer noopener"
        val links = document.select("a")
        for (link in links) {
            link.attr("rel", "noreferrer noopener")
            link.attr("target", "_blank")
        }

        return document.body().html()
    }
}
