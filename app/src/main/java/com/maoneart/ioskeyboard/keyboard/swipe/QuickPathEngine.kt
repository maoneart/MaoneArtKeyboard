package com.maoneart.ioskeyboard.keyboard.swipe

import com.maoneart.ioskeyboard.correction.SmartCorrectionEngine
import kotlin.math.abs

class QuickPathEngine(private val correctionEngine: SmartCorrectionEngine) {

    /**
     * Matches a gesture swipe trace of visited characters against the dictionary.
     * Returns best matching word, or null if no confident match found.
     */
    fun matchSwipe(rawLetters: List<Char>, lang: String): String? {
        if (rawLetters.size < 2) return null

        // Deduplicate consecutive identical characters
        val cleanLetters = mutableListOf<Char>()
        for (ch in rawLetters) {
            if (cleanLetters.isEmpty() || cleanLetters.last() != ch) {
                cleanLetters.add(ch)
            }
        }

        if (cleanLetters.size < 2) return null

        val startChar = cleanLetters.first()
        val endChar = cleanLetters.last()
        val targetLettersStr = cleanLetters.joinToString("")

        // Gather candidates starting with startChar and ending near/at endChar
        val candidatePool = correctionEngine.findSwipeCandidates(startChar, endChar, lang)

        var bestWord: String? = null
        var bestScore = Int.MAX_VALUE

        for (candidate in candidatePool) {
            val candLower = candidate.lowercase()
            if (candLower.length < 2) continue

            // Must match starting character
            if (candLower.first() != startChar) continue

            // Must be subsequence in the swipe trace (supporting collapsed repeats like 'saat' -> 'sat')
            if (!isSubsequence(candLower, targetLettersStr)) continue

            val endsMatch = (candLower.last() == endChar)

            // Length difference penalty
            var score = abs(candLower.length - cleanLetters.size) * 6

            // High priority boost for common everyday words and user vocabulary
            if (correctionEngine.isCommonWord(candLower)) {
                score -= 40
            }

            if (endsMatch) {
                score -= 25 // High boost when ending letter matches exactly
            } else {
                score += 35 // Moderate penalty when ending letter is a QWERTY neighbor
            }

            if (score < bestScore) {
                bestScore = score
                bestWord = candidate
            }
        }

        // If no candidate from pool, try exact 2-letter fallback
        if (bestWord == null && cleanLetters.size == 2) {
            bestWord = targetLettersStr
        }

        // If still null, try looking at suggestions for the first 3 letters
        if (bestWord == null) {
            val fallbackMatches = correctionEngine.getSuggestions(cleanLetters.take(3).joinToString(""), lang)
            if (fallbackMatches.isNotEmpty()) {
                bestWord = fallbackMatches.first()
            }
        }

        return bestWord
    }

    private fun collapseConsecutiveRepeats(s: String): String {
        val sb = StringBuilder()
        for (ch in s) {
            if (sb.isEmpty() || sb.last() != ch) {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun isSubsequence(needle: String, haystack: String): Boolean {
        // Match both exact needle and collapsed needle (e.g. 'saat' matches 'sat')
        val needleCollapsed = collapseConsecutiveRepeats(needle)
        var i = 0
        var j = 0
        while (i < needleCollapsed.length && j < haystack.length) {
            if (needleCollapsed[i] == haystack[j]) {
                i++
            }
            j++
        }
        return i == needleCollapsed.length
    }
}
