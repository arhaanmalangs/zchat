package com.nick.xchatmini

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Free translation engines. Ek block ho jaye to agla chalu ho jata hai. */
object Translator {

    private val cache = HashMap<String, String>()
    private val cooldown = HashMap<String, Long>()
    @Volatile var lastEngine: String = "-"

    private const val UA = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

    private fun http(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 10000
        c.setRequestProperty("User-Agent", UA)
        c.setRequestProperty("Accept", "application/json")
        try {
            if (c.responseCode !in 200..299) throw RuntimeException("http " + c.responseCode)
            return c.inputStream.bufferedReader().use(BufferedReader::readText)
        } finally {
            c.disconnect()
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun guessLang(t: String): String {
        for (ch in t) {
            val c = ch.code
            if (c in 0x3040..0x30ff) return "ja"
            if (c in 0xac00..0xd7af) return "ko"
            if (c in 0x4e00..0x9fff) return "zh-CN"
            if (c in 0x0400..0x04ff) return "ru"
            if (c in 0x0600..0x06ff) return "ar"
            if (c in 0x0900..0x097f) return "hi"
            if (c in 0x0e00..0x0e7f) return "th"
        }
        return ""
    }

    private fun viaGtx(text: String): String {
        val body = http(
            "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=en&dt=t&q=" + enc(text)
        )
        val arr = JSONArray(body).getJSONArray(0)
        val sb = StringBuilder()
        for (i in 0 until arr.length()) sb.append(arr.getJSONArray(i).getString(0))
        return sb.toString()
    }

    private fun viaDict(text: String): String {
        val body = http(
            "https://clients5.google.com/translate_a/t?client=dict-chrome-ex&sl=auto&tl=en&q=" + enc(text)
        )
        return if (body.trimStart().startsWith("[")) {
            val a = JSONArray(body)
            if (a.get(0) is String) a.getString(0) else a.getJSONArray(0).getString(0)
        } else {
            val o = JSONObject(body)
            val sents = o.getJSONArray("sentences")
            val sb = StringBuilder()
            for (i in 0 until sents.length()) sb.append(sents.getJSONObject(i).optString("trans"))
            sb.toString()
        }
    }

    private fun viaMyMemory(text: String): String {
        val src = guessLang(text)
        if (src.isEmpty()) throw RuntimeException("mm: lang unknown")
        val body = http(
            "https://api.mymemory.translated.net/get?langpair=" + src + "|en&q=" + enc(text.take(480))
        )
        return JSONObject(body).getJSONObject("responseData").getString("translatedText")
    }

    private fun viaLingva(text: String): String {
        val body = http("https://lingva.ml/api/v1/auto/en/" + enc(text.take(480)))
        return JSONObject(body).getString("translation")
    }

    private val engines: List<Pair<String, (String) -> String>> = listOf(
        "gtx" to ::viaGtx,
        "dict" to ::viaDict,
        "mymemory" to ::viaMyMemory,
        "lingva" to ::viaLingva
    )

    private fun bad(s: String) =
        s.isBlank() ||
        s.contains("INVALID SOURCE LANGUAGE", true) ||
        s.contains("MYMEMORY WARNING", true) ||
        s.contains("QUERY LENGTH LIMIT", true)

    /** Returns English text, ya "" agar sab engines fail ho gaye. */
    fun translate(text: String): String {
        cache[text]?.let { return it }
        val now = System.currentTimeMillis()
        val ordered = engines.sortedBy { (cooldown[it.first] ?: 0L) > now }
        for ((name, fn) in ordered) {
            try {
                val out = fn(text)
                if (!bad(out)) {
                    cooldown[name] = 0
                    lastEngine = name
                    if (cache.size > 2000) cache.clear()
                    cache[text] = out
                    return out
                }
            } catch (e: Exception) {
                cooldown[name] = System.currentTimeMillis() + 120_000
            }
        }
        return ""
    }
}
