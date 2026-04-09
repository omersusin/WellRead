package com.omersusin.wellread.utils

object TextProcessor {

    fun applyBionic(text: String, fixationStrength: Float = 0.5f): List<BionicWord> {
        return text.split(" ").filter { it.isNotBlank() }.map { word ->
            val clean = word.trim()
            val boldCount = when {
                clean.length <= 1 -> 1
                clean.length <= 3 -> 1
                clean.length <= 6 -> (clean.length * fixationStrength).toInt().coerceAtLeast(1)
                else -> (clean.length * fixationStrength).toInt().coerceAtLeast(2)
            }
            BionicWord(
                original = clean,
                boldPart = clean.substring(0, boldCount.coerceAtMost(clean.length)),
                normalPart = clean.substring(boldCount.coerceAtMost(clean.length))
            )
        }
    }

    fun splitIntoSentences(text: String): List<String> {
        return text
            .split(Regex("(?<=[.!?])\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() && it.length > 3 }
    }

    fun splitIntoWords(text: String): List<String> {
        return text.split(Regex("\\s+")).filter { it.isNotBlank() }
    }

    fun splitIntoChunks(words: List<String>, chunkSize: Int): List<List<String>> {
        return words.chunked(chunkSize)
    }

    fun countWords(text: String): Int {
        return text.split(Regex("\\s+")).count { it.isNotBlank() }
    }

    fun estimateReadingTime(wordCount: Int, wpm: Int): Int {
        return if (wpm > 0) (wordCount / wpm.toFloat() * 60).toInt() else 0
    }
}

data class BionicWord(
    val original: String,
    val boldPart: String,
    val normalPart: String
)
