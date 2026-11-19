package com.maoneart.ioskeyboard.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

data class ClipboardItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    var timestamp: Long = System.currentTimeMillis(),
    var isPinned: Boolean = false,
    val isImage: Boolean = false,
    val imagePath: String? = null,
    val mimeType: String? = "image/png"
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

    // Tracks freshly taken screenshot for instant quick-send pill in Candidate Bar
    private var freshScreenshotItem: ClipboardItem? = null
    private var freshScreenshotTimeMs: Long = 0L

    var onNewClipCopied: ((String) -> Unit)? = null

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        ignoredClipText = null
        fetchCurrentClip(fromListener = true)
    }

    init {
        loadFromDisk()
        try {
            clipboard?.addPrimaryClipChangedListener(clipListener)
        } catch (_: Throwable) {}
        fetchCurrentClip(fromListener = false)
        scanRecentScreenshots()
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

            if (fromListener || (sysTimestamp > 0L && sysTimestamp != lastSystemClipTimestamp)) {
                if (sysTimestamp > 0L && lastSystemClipTimestamp > 0L && sysTimestamp > lastSystemClipTimestamp) {
                    ignoredClipText = null
                }
                if (sysTimestamp > 0L) {
                    lastSystemClipTimestamp = sysTimestamp
                }
            }

            val rawItem = clip.getItemAt(0) ?: return

            // 1. Check if clipboard contains an image / screenshot URI
            if (rawItem.uri != null || (clip.description != null && clip.description.hasMimeType("image/*"))) {
                val uri = rawItem.uri
                if (uri != null) {
                    val mime = context.contentResolver.getType(uri) ?: "image/png"
                    val ext = if (mime.contains("jpeg") || mime.contains("jpg")) "jpg" else "png"
                    val clipDir = File(context.filesDir, "clipboard")
                    if (!clipDir.exists()) clipDir.mkdirs()
                    val targetFile = File(clipDir, "clip_${System.currentTimeMillis()}.$ext")

                    try {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            targetFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        if (targetFile.exists() && targetFile.length() > 0) {
                            val newItem = ClipboardItem(
                                text = "📸 Screenshot",
                                timestamp = System.currentTimeMillis(),
                                isImage = true,
                                imagePath = targetFile.absolutePath,
                                mimeType = mime
                            )
                            val firstUnpinned = items.indexOfFirst { !it.isPinned }
                            if (firstUnpinned >= 0) items.add(firstUnpinned, newItem) else items.add(0, newItem)
                            saveToDisk()
                            freshScreenshotItem = newItem
                            freshScreenshotTimeMs = System.currentTimeMillis()
                            return
                        }
                    } catch (_: Throwable) {}
                }
            }

            // 2. Otherwise extract plain text
            val extracted = (rawItem.text?.toString() ?: rawItem.coerceToText(context)?.toString())?.trim()
            if (extracted.isNullOrEmpty()) return

            if (extracted == ignoredClipText && !fromListener) {
                return
            }
            ignoredClipText = null

            val now = System.currentTimeMillis()
            val existingIdx = items.indexOfFirst { !it.isImage && it.text == extracted }
            if (existingIdx >= 0) {
                val existing = items[existingIdx]
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
                val unpinnedCount = items.count { !it.isPinned }
                if (unpinnedCount > 50) {
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
     * Scans DCIM/Screenshots, Pictures/Screenshots, and MediaStore for newly taken screenshots.
     * Automatically captures screenshots into keyboard clipboard history!
     */
    fun scanRecentScreenshots(): ClipboardItem? {
        return try {
            val now = System.currentTimeMillis()
            val maxAgeMs = 15 * 60 * 1000L // 15 minutes window
            val cutoffTime = now - maxAgeMs

            var latestScreenshotFile: File? = null
            var latestTimestamp = 0L

            // 1. Direct file scan on known Screenshot directories
            val screenshotDirs = listOf(
                File("/sdcard/DCIM/Screenshots"),
                File("/sdcard/Pictures/Screenshots"),
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Screenshots"),
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Screenshots")
            )

            for (dir in screenshotDirs) {
                if (!dir.exists() || !dir.isDirectory) continue
                val files = dir.listFiles { f ->
                    f.isFile && (f.name.startsWith("Screenshot", ignoreCase = true) ||
                                f.name.contains("Screenshot", ignoreCase = true) ||
                                f.name.startsWith("Screen_", ignoreCase = true) ||
                                f.name.startsWith("ss_", ignoreCase = true)) &&
                    (f.extension.equals("jpg", true) || f.extension.equals("jpeg", true) || f.extension.equals("png", true) || f.extension.equals("webp", true))
                } ?: continue

                for (f in files) {
                    val mod = f.lastModified()
                    if (mod > cutoffTime && mod > latestTimestamp) {
                        latestTimestamp = mod
                        latestScreenshotFile = f
                    }
                }
            }

            // 2. MediaStore query fallback
            if (latestScreenshotFile == null) {
                try {
                    val projection = arrayOf(
                        MediaStore.Images.Media._ID,
                        MediaStore.Images.Media.DATA,
                        MediaStore.Images.Media.DATE_ADDED,
                        MediaStore.Images.Media.DISPLAY_NAME
                    )
                    val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
                    val cursor = context.contentResolver.query(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        projection,
                        null,
                        null,
                        sortOrder
                    )
                    cursor?.use { c ->
                        val dataIdx = c.getColumnIndex(MediaStore.Images.Media.DATA)
                        val nameIdx = c.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                        val dateIdx = c.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)
                        var count = 0
                        while (c.moveToNext() && count < 10) {
                            count++
                            val path = if (dataIdx >= 0) c.getString(dataIdx) else null
                            val name = if (nameIdx >= 0) c.getString(nameIdx) else ""
                            val dateAddedSec = if (dateIdx >= 0) c.getLong(dateIdx) else 0L
                            val dateAddedMs = dateAddedSec * 1000L

                            if (dateAddedMs > cutoffTime && (name.contains("screenshot", ignoreCase = true) || path?.contains("screenshot", ignoreCase = true) == true)) {
                                if (path != null) {
                                    val f = File(path)
                                    if (f.exists() && f.length() > 0) {
                                        latestScreenshotFile = f
                                        latestTimestamp = if (f.lastModified() > 0) f.lastModified() else dateAddedMs
                                        break
                                    }
                                }
                            }
                        }
                    }
                } catch (_: Throwable) {}
            }

            val targetFile = latestScreenshotFile
            if (targetFile != null && targetFile.exists()) {
                val path = targetFile.absolutePath
                val mime = if (targetFile.extension.equals("jpg", true) || targetFile.extension.equals("jpeg", true)) "image/jpeg" else "image/png"

                val existing = items.find { it.isImage && it.imagePath == path }
                if (existing != null) {
                    if (now - latestTimestamp < 5 * 60 * 1000L) {
                        freshScreenshotItem = existing
                        freshScreenshotTimeMs = latestTimestamp
                    }
                    return existing
                }

                // Add as new screenshot item at the top of clipboard
                val item = ClipboardItem(
                    text = "📸 Screenshot",
                    timestamp = latestTimestamp,
                    isPinned = false,
                    isImage = true,
                    imagePath = path,
                    mimeType = mime
                )
                val firstUnpinned = items.indexOfFirst { !it.isPinned }
                if (firstUnpinned >= 0) {
                    items.add(firstUnpinned, item)
                } else {
                    items.add(0, item)
                }
                saveToDisk()
                freshScreenshotItem = item
                freshScreenshotTimeMs = latestTimestamp
                item
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Returns recently taken screenshot (within 5 minutes) for instant QuickType chip.
     */
    fun getRecentScreenshot(maxAgeMs: Long = 5 * 60 * 1000L): ClipboardItem? {
        val item = freshScreenshotItem ?: return null
        if (System.currentTimeMillis() - freshScreenshotTimeMs > maxAgeMs) {
            freshScreenshotItem = null
            return null
        }
        val f = item.imagePath?.let { File(it) }
        if (f == null || !f.exists()) {
            freshScreenshotItem = null
            return null
        }
        return item
    }

    fun consumeRecentScreenshot() {
        freshScreenshotItem = null
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
        scanRecentScreenshots()
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
            if (freshScreenshotItem?.id == id) {
                freshScreenshotItem = null
            }
            if (itemToDelete.isImage && itemToDelete.imagePath != null) {
                try {
                    val f = File(itemToDelete.imagePath)
                    if (f.absolutePath.contains(context.filesDir.absolutePath)) {
                        f.delete()
                    }
                } catch (_: Throwable) {}
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
        freshScreenshotItem = null
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
