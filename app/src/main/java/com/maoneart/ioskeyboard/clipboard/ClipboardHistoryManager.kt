package com.maoneart.ioskeyboard.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

data class ClipboardItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    var timestamp: Long = System.currentTimeMillis(),
    var isPinned: Boolean = false
)

class ClipboardHistoryManager(private val context: Context) {

    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    private val file = File(context.filesDir, "clipboard_history.json")
    private val gson = Gson()
    private val items = mutableListOf<ClipboardItem>()
    private var ignoredClipText: String? = null
    private var lastSystemClipTimestamp: Long = -1L

    // Tracks freshly copied text for iOS 18 Smart Paste / OTP Autofill in Candidate Bar
    private var freshClipText: String? = null
    private var freshClipTimeMs: Long = 0L

    var onNewClipCopied: ((String) -> Unit)? = null

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        // User just copied something new! Clear any previous delete-ignore lock so re-copying the same text works 100%
        ignoredClipText = null
        fetchCurrentClip(fromListener = true)
    }

    init {
        loadFromDisk()
        try {
            clipboard?.addPrimaryClipChangedListener(clipListener)
        } catch (_: Throwable) {}
        fetchCurrentClip(fromListener = false)
    }

    fun destroy() {
        try {
            clipboard?.removePrimaryClipChangedListener(clipListener)
        } catch (_: Throwable) {}
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

    fun fetchCurrentClip(fromListener: Boolean = false) {
        try {
            val cm = clipboard ?: return
            if (!cm.hasPrimaryClip()) return
            val clip = cm.primaryClip ?: return
            if (clip.itemCount <= 0) return

            val sysTimestamp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                clip.description?.timestamp ?: -1L
            } else {
                -1L
            }

            // If Android reports a newer clip timestamp than when we last ignored a deleted item, unlock it!
            if (fromListener || (sysTimestamp > 0L && sysTimestamp != lastSystemClipTimestamp)) {
                if (sysTimestamp > 0L && lastSystemClipTimestamp > 0L && sysTimestamp > lastSystemClipTimestamp) {
                    ignoredClipText = null
                }
                if (sysTimestamp > 0L) {
                    lastSystemClipTimestamp = sysTimestamp
                }
            }

            val rawItem = clip.getItemAt(0) ?: return
            val extracted = (rawItem.text?.toString() ?: rawItem.coerceToText(context)?.toString())?.trim()
            if (extracted.isNullOrEmpty()) return

            if (extracted == ignoredClipText && !fromListener) {
                return
            }
            ignoredClipText = null

            val now = System.currentTimeMillis()
            val existingIdx = items.indexOfFirst { it.text == extracted }
            if (existingIdx >= 0) {
                val existing = items[existingIdx]
                // If triggered by a fresh copy event, update timestamp and move to top of unpinned
                if (fromListener || existingIdx > 0) {
                    existing.timestamp = now
                    if (!existing.isPinned) {
                        items.removeAt(existingIdx)
                        val firstUnpinned = items.indexOfFirst { !it.isPinned }
                        if (firstUnpinned >= 0) items.add(firstUnpinned, existing) else items.add(0, existing)
                    }
                    saveToDisk()
                }
                if (fromListener) {
                    freshClipText = extracted
                    freshClipTimeMs = now
                    onNewClipCopied?.invoke(extracted)
                }
            } else {
                val newItem = ClipboardItem(text = extracted, timestamp = now)
                val firstUnpinned = items.indexOfFirst { !it.isPinned }
                if (firstUnpinned >= 0) {
                    items.add(firstUnpinned, newItem)
                } else {
                    val lastPinned = items.indexOfLast { it.isPinned }
                    if (lastPinned >= 0) {
                        items.add(lastPinned + 1, newItem)
                    } else {
                        items.add(0, newItem)
                    }
                }
                // Prune oldest unpinned items if over capacity (keep pinned forever)
                val unpinnedCount = items.count { !it.isPinned }
                if (unpinnedCount > 40) {
                    val lastUnpinnedIdx = items.indexOfLast { !it.isPinned }
                    if (lastUnpinnedIdx >= 0) items.removeAt(lastUnpinnedIdx)
                }
                saveToDisk()

                freshClipText = extracted
                freshClipTimeMs = now
                onNewClipCopied?.invoke(extracted)
            }
        } catch (_: Throwable) {}
    }

    /**
     * Returns recently copied text (within 45 seconds) for iOS 18 Smart Paste / OTP pill in Candidate Bar.
     */
    fun getRecentFreshClip(maxAgeMs: Long = 45_000L): String? {
        val text = freshClipText ?: return null
        if (System.currentTimeMillis() - freshClipTimeMs > maxAgeMs) {
            freshClipText = null
            return null
        }
        return text
    }

    fun consumeFreshClip() {
        freshClipText = null
    }

    fun getItems(): List<ClipboardItem> {
        fetchCurrentClip(fromListener = false)
        return items.sortedWith(compareByDescending<ClipboardItem> { it.isPinned }.thenByDescending { it.timestamp })
    }

    fun togglePin(id: String): Boolean {
        val item = items.find { it.id == id } ?: return false
        item.isPinned = !item.isPinned
        saveToDisk()
        return item.isPinned
    }

    fun deleteItem(id: String) {
        val itemToDelete = items.find { it.id == id }
        if (itemToDelete != null) {
            if (freshClipText == itemToDelete.text) {
                freshClipText = null
            }
            try {
                val currentClipText = clipboard?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim()
                if (currentClipText == itemToDelete.text) {
                    ignoredClipText = currentClipText
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        clipboard?.clearPrimaryClip()
                    } else {
                        clipboard?.setPrimaryClip(ClipData.newPlainText("", ""))
                    }
                }
            } catch (_: Throwable) {}
        }
        items.removeAll { it.id == id }
        saveToDisk()
    }

    fun clearAllUnpinned() {
        freshClipText = null
        try {
            val currentClipText = clipboard?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim()
            if (!currentClipText.isNullOrEmpty()) {
                val isCurrentClipPinned = items.any { it.isPinned && it.text == currentClipText }
                if (!isCurrentClipPinned) {
                    ignoredClipText = currentClipText
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        clipboard?.clearPrimaryClip()
                    } else {
                        clipboard?.setPrimaryClip(ClipData.newPlainText("", ""))
                    }
                }
            }
        } catch (_: Throwable) {}

        // Remove ALL unpinned items (pinned items are 100% preserved)
        items.removeAll { !it.isPinned }
        saveToDisk()
    }
}
