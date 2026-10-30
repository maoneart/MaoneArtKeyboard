package com.maoneart.ioskeyboard.sticker

import android.content.ClipDescription
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

class StickerManager(private val context: Context) {

    private val stickerDir: File by lazy {
        val dir = File(context.filesDir, "stickers")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        dir
    }

    fun getStickers(): List<File> {
        return try {
            stickerDir.listFiles { file ->
                file.isFile && (file.extension.equals("webp", ignoreCase = true) || file.extension.equals("png", ignoreCase = true))
            }?.sortedByDescending { it.lastModified() } ?: emptyList()
        } catch (_: Throwable) {
            emptyList()
        }
    }

    /**
     * Converts any input Bitmap (PNG/JPG) to WhatsApp Sticker specifications:
     * - Exactly 512x512 pixels with alpha channel preserved
     * - Compressed as WEBP format (< 500 KB)
     */
    fun convertAndSaveSticker(sourceBitmap: Bitmap): File? {
        if (sourceBitmap.isRecycled) return null
        val srcWidth = sourceBitmap.width.toFloat()
        val srcHeight = sourceBitmap.height.toFloat()
        if (srcWidth <= 0f || srcHeight <= 0f) return null

        var scaledBitmap: Bitmap? = null
        return try {
            val size = 512
            scaledBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(scaledBitmap)

            // Scale to fit within 512x512 while keeping aspect ratio
            val scale = (size.toFloat() / max(srcWidth, srcHeight)).coerceAtMost(1.0f)
            val dstWidth = srcWidth * scale
            val dstHeight = srcHeight * scale

            val left = (size - dstWidth) / 2f
            val top = (size - dstHeight) / 2f

            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val srcRect = android.graphics.Rect(0, 0, sourceBitmap.width, sourceBitmap.height)
            val dstRect = android.graphics.RectF(left, top, left + dstWidth, top + dstHeight)
            canvas.drawBitmap(sourceBitmap, srcRect, dstRect, paint)

            val fileName = "sticker_${System.currentTimeMillis()}.webp"
            val destFile = File(stickerDir, fileName)

            @Suppress("DEPRECATION")
            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                Bitmap.CompressFormat.WEBP
            }

            FileOutputStream(destFile).use { fos ->
                scaledBitmap.compress(format, 85, fos)
                fos.flush()
            }

            destFile
        } catch (_: Throwable) {
            null
        } finally {
            try {
                scaledBitmap?.recycle()
            } catch (_: Throwable) {}
        }
    }

    fun deleteSticker(file: File): Boolean {
        return try {
            if (file.exists()) file.delete() else false
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Sends the sticker directly to WhatsApp or host chat app via Android CommitContent API
     */
    fun sendSticker(
        inputConnection: InputConnection?,
        editorInfo: EditorInfo?,
        stickerFile: File
    ): Boolean {
        if (inputConnection == null || editorInfo == null || !stickerFile.exists()) {
            return false
        }

        return try {
            val authority = "${context.packageName}.fileprovider"
            val contentUri: Uri = FileProvider.getUriForFile(context, authority, stickerFile)

            val mimeType = if (stickerFile.extension.equals("png", ignoreCase = true)) "image/png" else "image/webp"
            val description = ClipDescription("WhatsApp Sticker", arrayOf(mimeType, "image/webp", "image/png"))

            val inputContentInfo = InputContentInfoCompat(contentUri, description, null)

            var flags = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                flags = InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION
            }

            InputConnectionCompat.commitContent(inputConnection, editorInfo, inputContentInfo, flags, null)
        } catch (_: Throwable) {
            false
        }
    }
}
