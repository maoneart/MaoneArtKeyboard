package com.maoneart.ioskeyboard.shortcut

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class TextReplacementManager(private val context: Context) {

    private val gson = Gson()
    private val replacementsFile = File(context.filesDir, "user_shortcuts.json")
    private val memoryShortcuts = ConcurrentHashMap<String, String>()

    // Default iOS-style Text Replacements (Indonesian & English common abbreviations)
    private val defaultShortcuts = mapOf(
        "otw" to "On the way",
        "omw" to "On my way",
        "brb" to "Be right back",
        "asap" to "As soon as possible",
        "ty" to "Thank you",
        "thx" to "Thanks a lot",
        "gpp" to "Gak apa-apa",
        "yg" to "yang",
        "dgn" to "dengan",
        "utk" to "untuk",
        "sbg" to "sebagai",
        "kpn" to "kapan",
        "dmn" to "dimana",
        "gmn" to "gimana",
        "bgt" to "banget",
        "smg" to "semoga",
        "sy" to "saya",
        "km" to "kamu",
        "mks" to "makasih",
        "thx" to "terima kasih"
    )

    init {
        loadShortcuts()
    }

    private fun loadShortcuts() {
        try {
            if (replacementsFile.exists()) {
                val json = replacementsFile.readText(Charsets.UTF_8)
                val type = object : TypeToken<Map<String, String>>() {}.type
                val loaded: Map<String, String>? = gson.fromJson(json, type)
                if (loaded != null) {
                    memoryShortcuts.putAll(loaded)
                }
            } else {
                // Populate default shortcuts
                memoryShortcuts.putAll(defaultShortcuts)
                saveShortcuts()
            }
        } catch (_: Throwable) {
            memoryShortcuts.putAll(defaultShortcuts)
        }
    }

    private fun saveShortcuts() {
        try {
            val json = gson.toJson(memoryShortcuts)
            replacementsFile.writeText(json, Charsets.UTF_8)
        } catch (_: Throwable) {}
    }

    fun getReplacement(shortcut: String): String? {
        val key = shortcut.trim().lowercase()
        return memoryShortcuts[key]
    }

    fun hasShortcut(shortcut: String): Boolean {
        return memoryShortcuts.containsKey(shortcut.trim().lowercase())
    }

    fun getAllReplacements(): Map<String, String> {
        return memoryShortcuts.toMap()
    }

    fun addOrUpdate(shortcut: String, phrase: String) {
        val cleanKey = shortcut.trim().lowercase()
        val cleanPhrase = phrase.trim()
        if (cleanKey.isNotEmpty() && cleanPhrase.isNotEmpty()) {
            memoryShortcuts[cleanKey] = cleanPhrase
            saveShortcuts()
        }
    }

    fun remove(shortcut: String) {
        val cleanKey = shortcut.trim().lowercase()
        if (memoryShortcuts.remove(cleanKey) != null) {
            saveShortcuts()
        }
    }
}
