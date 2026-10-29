package com.maoneart.ioskeyboard.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

data class ClipboardItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    var isPinned: Boolean = false
)

class ClipboardHistoryManager(private val context: Context) {

    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    private val file = File(context.filesDir, "clipboard_history.json")
    private val gson = Gson()
    private val items = mutableListOf<ClipboardItem>()

    init {
        loadFromDisk()
        fetchCurrentClip()
    }

    private fun loadFromDisk() {
        try {
            if (file.exists()) {
                val json = file.readText(Charsets.UTF_8)
                val type = object : TypeToken<List<ClipboardItem>>() {}.type
                val loaded: List<ClipboardItem>? = gson.fromJson(json, type)
                if (loaded != null) {
                    items.clear()
                    items.addAll(loaded)
                }
            }
        } catch (_: Throwable) {}
    }

    private fun saveToDisk() {
        try {
            val json = gson.toJson(items)
            file.writeText(json, Charsets.UTF_8)
        } catch (_: Throwable) {}
    }

    fun fetchCurrentClip() {
        try {
            val clip = clipboard?.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0)?.coerceToText(context)?.toString()?.trim()
                if (!text.isNullOrEmpty()) {
                    val existingIdx = items.indexOfFirst { it.text == text }
                    if (existingIdx >= 0) {
                        val existing = items[existingIdx]
                        if (!existing.isPinned) {
                            items.removeAt(existingIdx)
                            val firstUnpinned = items.indexOfFirst { !it.isPinned }
                            if (firstUnpinned >= 0) items.add(firstUnpinned, existing) else items.add(existing)
                            saveToDisk()
                        }
                    } else {
                        val newItem = ClipboardItem(text = text)
                        val firstUnpinned = items.indexOfFirst { !it.isPinned }
                        if (firstUnpinned >= 0) {
                            items.add(firstUnpinned, newItem)
                        } else {
                            items.add(0, newItem)
                        }
                        // Prune oldest unpinned items if over capacity (keep pinned forever)
                        val unpinnedCount = items.count { !it.isPinned }
                        if (unpinnedCount > 35) {
                            val lastUnpinnedIdx = items.indexOfLast { !it.isPinned }
                            if (lastUnpinnedIdx >= 0) items.removeAt(lastUnpinnedIdx)
                        }
                        saveToDisk()
                    }
                }
            }
        } catch (_: Throwable) {}
    }

    fun getItems(): List<ClipboardItem> {
        fetchCurrentClip()
        return items.sortedWith(compareByDescending<ClipboardItem> { it.isPinned }.thenByDescending { it.timestamp })
    }

    fun togglePin(id: String): Boolean {
        val item = items.find { it.id == id } ?: return false
        item.isPinned = !item.isPinned
        saveToDisk()
        return item.isPinned
    }

    fun deleteItem(id: String) {
        items.removeAll { it.id == id }
        saveToDisk()
    }

    fun clearAllUnpinned() {
        items.removeAll { !it.isPinned }
        saveToDisk()
    }
}
