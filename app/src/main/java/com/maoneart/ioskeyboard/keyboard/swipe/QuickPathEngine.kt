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

        // Gather dictionary candidates starting with startChar & 2-letter prefix from 75k KBBI
        val candidatePool = linkedSetOf<String>()

        if (cleanLetters.size >= 2) {
            val prefix2 = "${cleanLetters[0]}${cleanLetters[1]}"
            candidatePool.addAll(correctionEngine.findKbbiPrefixMatches(prefix2, 250))
        }
        candidatePool.addAll(correctionEngine.findKbbiPrefixMatches(startChar.toString(), 250))
        candidatePool.addAll(correctionEngine.getSuggestions(startChar.toString(), lang))

        var bestWord: String? = null
        var bestScore = Int.MAX_VALUE

        for (candidate in candidatePool) {
            val candLower = candidate.lowercase()
            if (candLower.length < 2) continue

            // Must match starting character
            if (candLower.first() != startChar) continue

            // Must be subsequence in the swipe trace
            if (!isSubsequence(candLower, targetLettersStr)) continue

            val endsMatch = (candLower.last() == endChar)

            // Scoring: lower score is better match
            var score = abs(candLower.length - cleanLetters.size) * 8
            if (endsMatch) {
                score -= 30 // High boost when end letter matches perfectly
            } else {
                score += 45 // Penalty when ending letter differs
            }

            if (score < bestScore) {
                bestScore = score
                bestWord = candidate
            }
        }

        // If no candidate from prefix matches, try looking at top KBBI words for startChar
        if (bestWord == null) {
            val fallbackMatches = correctionEngine.getSuggestions(cleanLetters.take(3).joinToString(""), lang)
            if (fallbackMatches.isNotEmpty()) {
                bestWord = fallbackMatches.first()
            }
        }

        return bestWord
    }

    private fun isSubsequence(needle: String, haystack: String): Boolean {
        var i = 0
        var j = 0
        while (i < needle.length && j < haystack.length) {
            if (needle[i] == haystack[j]) {
                i++
            }
            j++
        }
        return i == needle.length
    }
}
