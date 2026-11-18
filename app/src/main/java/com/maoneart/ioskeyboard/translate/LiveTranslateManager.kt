package com.maoneart.ioskeyboard.translate

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class LiveTranslateManager {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    suspend fun translateText(text: String, sourceLang: String, targetLang: String): String = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext text

        // Strategy 1: HTTP POST with FormBody (no URL character limit, prevents URI 414 / truncation on long sentences)
        val clients = listOf("dict-chrome-ex", "at", "gtx")
        for (c in clients) {
            try {
                val url = "https://translate.googleapis.com/translate_a/single?client=$c&sl=$sourceLang&tl=$targetLang&dt=t"
                val formBody = FormBody.Builder()
                    .add("q", text)
                    .build()

                val request = Request.Builder()
                    .url(url)
                    .post(formBody)
                    .header("User-Agent", userAgent)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrBlank()) {
                            val parsed = parseTranslateResponse(body)
                            if (!parsed.isNullOrBlank()) {
                                return@withContext parsed
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Try next client
            }
        }

        // Strategy 2: Fallback GET for networks where POST might be restricted
        for (c in clients) {
            try {
                val encoded = URLEncoder.encode(text, "UTF-8")
                val url = "https://translate.googleapis.com/translate_a/single?client=$c&sl=$sourceLang&tl=$targetLang&dt=t&q=$encoded"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", userAgent)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrBlank()) {
                            val parsed = parseTranslateResponse(body)
                            if (!parsed.isNullOrBlank()) {
                                return@withContext parsed
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Try next client
            }
        }

        // Fallback: return original text if offline or connection fails
        return@withContext text
    }

    private fun parseTranslateResponse(body: String): String? {
        return try {
            val jsonElement = JsonParser.parseString(body)
            if (!jsonElement.isJsonArray) return null
            val rootArray = jsonElement.asJsonArray
            if (rootArray.size() == 0 || !rootArray[0].isJsonArray) return null
            val sentences = rootArray[0].asJsonArray
            val sb = StringBuilder()
            for (s in sentences) {
                if (s.isJsonArray) {
                    val sArr = s.asJsonArray
                    if (sArr.size() > 0 && !sArr[0].isJsonNull) {
                        sb.append(sArr[0].asString)
                    }
                }
            }
            val res = sb.toString()
            if (res.isNotBlank()) res else null
        } catch (_: Exception) {
            null
        }
    }
}
