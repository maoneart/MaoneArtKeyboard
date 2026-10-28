package com.maoneart.ioskeyboard.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

class ClipboardHistoryManager(private val context: Context) {

    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    private val history = mutableListOf<String>()

    init {
        fetchCurrentClip()
    }

    fun fetchCurrentClip() {
        try {
            val clip = clipboard?.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0)?.coerceToText(context)?.toString()?.trim()
                if (!text.isNullOrEmpty() && !history.contains(text)) {
                    history.add(0, text)
                    if (history.size > 20) history.removeLast()
                }
            }
        } catch (_: Exception) {}
    }

    fun getHistory(): List<String> {
        fetchCurrentClip()
        return history
    }

    fun clearHistory() {
        history.clear()
    }
}
