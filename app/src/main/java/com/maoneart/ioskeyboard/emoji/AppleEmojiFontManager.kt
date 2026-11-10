package com.maoneart.ioskeyboard.emoji

import android.content.Context
import android.graphics.Typeface

object AppleEmojiFontManager {
    @Volatile
    private var cachedTypeface: Typeface? = null
    private var isInitialized = false

    fun getAppleEmojiTypeface(context: Context): Typeface? {
        if (!isInitialized) {
            synchronized(this) {
                if (!isInitialized) {
                    try {
                        cachedTypeface = Typeface.createFromAsset(context.assets, "AppleColorEmoji.ttf")
                    } catch (_: Throwable) {
                        cachedTypeface = null
                    }
                    isInitialized = true
                }
            }
        }
        return cachedTypeface
    }
}
