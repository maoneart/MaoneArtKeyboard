package com.maoneart.ioskeyboard.sticker

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.maoneart.ioskeyboard.R

class StickerUploadActivity : AppCompatActivity() {

    private lateinit var stickerManager: StickerManager
    private var selectedBitmap: Bitmap? = null

    private lateinit var ivPreview: ImageView
    private lateinit var layoutPlaceholder: LinearLayout
    private lateinit var btnPickImage: Button
    private lateinit var btnSaveSticker: Button

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val imageUri: Uri? = result.data?.data
            if (imageUri != null) {
                loadBitmapFromUri(imageUri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sticker_upload)

        stickerManager = StickerManager(this)

        ivPreview = findViewById(R.id.ivPreview)
        layoutPlaceholder = findViewById(R.id.layoutPlaceholder)
        btnPickImage = findViewById(R.id.btnPickImage)
        btnSaveSticker = findViewById(R.id.btnSaveSticker)

        findViewById<TextView>(R.id.btnCancel).setOnClickListener {
            finish()
        }

        btnPickImage.setOnClickListener {
            openGalleryPicker()
        }

        btnSaveSticker.setOnClickListener {
            saveSticker()
        }

        // Auto-open gallery on launch for immediate convenience
        if (savedInstanceState == null) {
            openGalleryPicker()
        }
    }

    private fun openGalleryPicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/png", "image/jpeg", "image/webp"))
        }
        pickImageLauncher.launch(Intent.createChooser(intent, "Pilih Gambar Stiker"))
    }

    private fun loadBitmapFromUri(uri: Uri) {
        try {
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.isMutableRequired = true
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(contentResolver, uri)
            }

            selectedBitmap = bitmap
            ivPreview.setImageBitmap(bitmap)
            layoutPlaceholder.visibility = View.GONE
            btnSaveSticker.visibility = View.VISIBLE
            btnPickImage.text = "Ganti Gambar Lain"
        } catch (t: Throwable) {
            Toast.makeText(this, "Gagal memuat gambar: ${t.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveSticker() {
        val bitmap = selectedBitmap ?: return
        val savedFile = stickerManager.convertAndSaveSticker(bitmap)

        if (savedFile != null && savedFile.exists()) {
            Toast.makeText(this, "✓ Stiker WebP berhasil dibuat dan disimpan!", Toast.LENGTH_LONG).show()
            finish()
        } else {
            Toast.makeText(this, "Gagal mengonversi gambar ke WebP", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            selectedBitmap?.recycle()
            selectedBitmap = null
        } catch (_: Throwable) {}
    }
}
