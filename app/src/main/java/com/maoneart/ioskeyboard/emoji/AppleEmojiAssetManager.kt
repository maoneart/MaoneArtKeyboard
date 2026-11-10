package com.maoneart.ioskeyboard.emoji

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache

object AppleEmojiAssetManager {
    // Fast memory cache for up to 600 bitmaps for 120 FPS buttery-smooth scrolling
    private val memoryCache = LruCache<String, Bitmap>(600)

    fun getFileNameForEmoji(emoji: String): String {
        val codes = mutableListOf<String>()
        var i = 0
        while (i < emoji.length) {
            val cp = emoji.codePointAt(i)
            codes.add(Integer.toHexString(cp).lowercase())
            i += Character.charCount(cp)
        }
        return codes.joinToString("_") + ".webp"
    }

    private fun loadBitmapFromAssets(context: Context, fileName: String): Bitmap? {
        return try {
            context.assets.open("apple_emojis/$fileName").use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (_: Throwable) {
            null
        }
    }

    fun getEmojiBitmap(context: Context, emoji: String): Bitmap? {
        val fileName = getFileNameForEmoji(emoji)
        memoryCache.get(fileName)?.let { return it }

        var bmp = loadBitmapFromAssets(context, fileName)
        if (bmp == null && fileName.contains("fe0f")) {
            val alt = fileName.replace("_fe0f", "").replace("fe0f_", "")
            bmp = loadBitmapFromAssets(context, alt)
        } else if (bmp == null && !fileName.contains("fe0f")) {
            val alt = fileName.substringBeforeLast(".webp") + "_fe0f.webp"
            bmp = loadBitmapFromAssets(context, alt)
        }

        if (bmp != null) {
            memoryCache.put(fileName, bmp)
        }
        return bmp
    }
}
