package com.maoneart.ioskeyboard.translate

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class LiveTranslateManager {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    suspend fun translateText(text: String, sourceLang: String, targetLang: String): String = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext text
        
        try {
            val encoded = URLEncoder.encode(text, "UTF-8")
            val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=$sourceLang&tl=$targetLang&dt=t&q=$encoded"
            
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext text
                val body = response.body?.string() ?: return@withContext text
                
                val jsonArray = JsonParser.parseString(body).asJsonArray
                val sentences = jsonArray[0].asJsonArray
                val sb = StringBuilder()
                for (s in sentences) {
                    sb.append(s.asJsonArray[0].asString)
                }
                return@withContext sb.toString()
            }
        } catch (_: Exception) {
            // Fallback: return original text if offline or connection fails
            return@withContext text
        }
    }
}
