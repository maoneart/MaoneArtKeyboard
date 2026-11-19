package com.maoneart.ioskeyboard.dictation

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class DictationPermissionActivity : Activity() {

    companion object {
        const val ACTION_PERMISSION_GRANTED = "com.maoneart.ioskeyboard.ACTION_RECORD_AUDIO_GRANTED"
        private const val PERMISSION_REQUEST_CODE = 4021
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            broadcastPermissionGranted()
            finish()
            return
        }

        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                broadcastPermissionGranted()
            } else {
                Toast.makeText(this, "Izin mikrofon dibutuhkan untuk fitur Dikte Suara iOS", Toast.LENGTH_SHORT).show()
            }
        }
        finish()
    }

    private fun broadcastPermissionGranted() {
        val intent = Intent(ACTION_PERMISSION_GRANTED).apply {
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }
}
