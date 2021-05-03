package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.lang.Exception

class ReadLightNovelProvider : MainAPI() {
    override val name: String
        get() = "ReadLightNovel"
    override val mainUrl: String
        get() = "https://www.readlightnovel.org"
    override val iconId: Int
        get() = R.drawable.icon_readlightnovel4
    override val hasMainPage: Boolean
        get() = true

    override val orderBys: ArrayList<Pair<String, String>>
        get() = arrayListOf(
            Pair("Top Rated", "top_rated"),
            Pair("Most Viewed", "view")
        )

    override val tags: ArrayList<Pair<String, String>>
        get() = arrayListOf(
            Pair("All", ""),
            Pair("Action", "action"),
            Pair("Adventure", "adventure"),
            Pair("Celebrity", "celebrity"),
            Pair("Comedy", "comedy"),
            Pair("Drama", "drama"),
            Pair("Ecchi", "ecchi"),
            Pair("Fantasy", "fantasy"),
            Pair("Gender Bender", "gender-bender"),
            Pair("Harem", "harem"),
            Pair("Historical", "historical"),
            Pair("Horror", "horror"),
            Pair("Josei", "josei"),
            Pair("Martial Arts", "martial-arts"),
            Pair("Mature", "mature"),
            Pair("Mecha", "mecha"),
            Pair("Mystery", "mystery"),
            Pair("Psychological", "psychological"),
            Pair("Romance", "romance"),
            Pair("School Life", "school-life"),
            Pair("Sci-fi", "sci-fi"),
            Pair("Seinen", "seinen"),
            Pair("Shotacon", "shotacon"),
            Pair("Shoujo", "shoujo"),
            Pair("Shoujo Ai", "shoujo-ai"),
            Pair("Shounen", "shounen"),
            Pair("Shounen Ai", "shounen-ai"),
            Pair("Slice of Life", "slice-of-life"),
            Pair("Smut", "smut"),
            Pair("Sports", "sports"),
            Pair("Supernatural", "supernatural"),
            Pair("Tragedy", "tragedy"),
            Pair("Wuxia", "wuxia"),
            Pair("Xianxia", "xianxia"),
            Pair("Xuanhuan", "xuanhuan"),
            Pair("Yaoi", "yaoi"),
            Pair("Yuri", "yuri")
        )

    override fun loadMainPage(page: Int, mainCategory: String?, orderBy: String?, tag: String?): HeadMainPageResponse? {
        val url = "$mainUrl/${if (tag == "") "top-novel" else "category/$tag"}/$page?change_type=$orderBy"
        try {
            val response = khttp.get(url)

            val document = Jsoup.parse(response.text)
            val headers = document.select("div.top-novel-block")
            if (headers.size <= 0) return HeadMainPageResponse(url, ArrayList())

            val returnValue: ArrayList<MainPageResponse> = ArrayList()
            for (h in headers) {
                val content = h.selectFirst("> div.top-novel-content")
                val nameHeader = h.selectFirst("div.top-novel-header > h2 > a")
                val url = nameHeader.attr("href")
                val name = nameHeader.text()
                val posterUrl = content.selectFirst("> div.top-novel-cover > a > img").attr("src")
                val tags = ArrayList(
                    content.select("> div.top-novel-body > div.novel-item > div.content")
                        .last().select("> ul > li > a").map { t -> t.text() })
                returnValue.add(MainPageResponse(name, fixUrl(url), fixUrl(posterUrl), null, null, this.name, tags))
            }
            return HeadMainPageResponse(url, returnValue)
        } catch (e: Exception) {
            return null
        }
    }

    override fun loadHtml(url: String): String? {
        return try {
            val response = khttp.get(url)
            val document = Jsoup.parse(response.text)
            val content = document.selectFirst("div.chapter-content3 > div.desc")
            //content.select("div").remove()
            content.select("div.alert").remove()
            content.select("#podium-spot").remove()
            content.select("iframe").remove()
            content.select("small.ads-title").remove()
            content.select("script").remove()

            content.html()
        } catch (e: Exception) {
            null
        }
    }

    override fun search(query: String): ArrayList<SearchResponse>? {
        try {
            val response = khttp.post("$mainUrl/search/autocomplete",
                headers = mapOf(
                    "referer" to mainUrl,
                    "x-requested-with" to "XMLHttpRequest",
                    "content-type" to "application/x-www-form-urlencoded",
                    "accept" to "*/*",
                    "user-agent" to USER_AGENT
                ),
                data = mapOf("q" to query))
            val document = Jsoup.parse(response.text)
            val headers = document.select("li > a")
            if (headers.size <= 0) return ArrayList()
            val returnValue: ArrayList<SearchResponse> = ArrayList()
            for (h in headers) {
                val spans = h.select("> span")

                val name = spans[1].text()
                val url = h.attr("href")

                val posterUrl = spans[0].selectFirst("> img").attr("src")

                returnValue.add(SearchResponse(name, url, posterUrl, null, null, this.name))
            }
            return returnValue
        } catch (e: Exception) {
            return null
        }
    }

    override fun load(url: String): LoadResponse? {
        try {
            val response = khttp.get(url)

            val document = Jsoup.parse(response.text)

            val info = document.select("div.novel-detail-body") //div.novel-details > div.novel-detail-item >
            val names = document.select("div.novel-detail-header").map { t -> t.text() }

            // 0 = Type (ex Web Novel)
            // 1 = Genre
            // 2 = Tags
            // 3 = Language
            // 4 = Author(s)
            // 5 = Artist(s)
            // 6 = Year
            // 7 = Status
            // 8 = Description
            // 9 = Alternative Names
            // 10 = You May Also Like
            // 11 = Total Views
            // 12 = Rating (10 POINT SYSTEM)
            // 13 = Latest Chapters
            fun getIndex(name: String): Element { // Bruh, has to do this because sometimes it varies between 14 and 15 elements
                return info[names.indexOf(name)]
            }

            val rating = (getIndex("Rating").text().toFloat() * 100).toInt()
            val name = document.selectFirst("div.block-title").text()
            var author = getIndex("Author(s)").selectFirst("> ul > li").text()
            if (author == "N/A") author = null

            val tagsDoc = getIndex("Genre").select("ul > li > a")
            val tags: ArrayList<String> = ArrayList()
            for (t in tagsDoc) {
                tags.add(t.text())
            }

            var synopsis = ""
            val synoParts = getIndex("Description").select("> p")
            for (s in synoParts) {
                synopsis += s.text()!! + "\n\n"
            }

            val data: ArrayList<ChapterData> = ArrayList()
            val panels = document.select("div.panel")
            for (p in panels) {
                var pName = p.select("> div.panel-heading > h4.panel-title > a").text()
                pName = if (pName == "Chapters") "" else "$pName • "

                val chapterHeaders =
                    p.select("> div.panel-collapse > div.panel-body > div.tab-content > div.tab-pane > ul.chapter-chs > li > a")
                for (c in chapterHeaders) {
                    val name = c.text()
                    val url = c.attr("href")
                    var rName = name
                        .replace("CH ([0-9]*)".toRegex(), "Chapter $1")
                        .replace("CH ", "")

                    rName = when (rName) {
                        "Pr" -> "Prologue"
                        "Ep" -> "Epilogue"
                        else -> rName
                    }

                    data.add(ChapterData(pName + rName, fixUrl(url), null, null))
                    println(name)
                }
            }

            val posterUrl = document.selectFirst("div.novel-cover > a > img").attr("src")

            val views = getIndex("Total Views").text().replace(",", "").replace(".", "").toInt()
            val peopleRated = null

            val status = when (getIndex("Status").text()) {
                "Ongoing" -> 1
                "Completed" -> 2
                else -> 0
            }

            return LoadResponse(name,
                data,
                author,
                fixUrl(posterUrl),
                rating,
                peopleRated,
                views,
                synopsis,
                tags,
                status)
        } catch (e: Exception) {
            return null
        }
    }
}