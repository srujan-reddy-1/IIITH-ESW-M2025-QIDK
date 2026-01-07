package com.google.mediapipe.examples.gesturerecognizer

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

class GestureAutocorrector(context: Context) {

    // Dictionary with word frequencies
    private val dictionary = mutableMapOf<String, Long>()

    init {
        // Load dictionary from CSV file in assets folder
        loadDictionaryFromAssets(context, "unigram_freq.csv")
    }

    /**
     * Load dictionary from CSV file in assets folder
     * Expected format: word,count
     */
    private fun loadDictionaryFromAssets(context: Context, fileName: String) {
        try {
            val inputStream = context.assets.open(fileName)
            val reader = BufferedReader(InputStreamReader(inputStream))

            // Skip header line
            reader.readLine()

            reader.forEachLine { line ->
                val parts = line.split(",")
                if (parts.size >= 2) {
                    val word = parts[0].trim().lowercase()
                    val count = parts[1].trim().toLongOrNull() ?: 0L
                    dictionary[word] = count
                }
            }

            reader.close()
            inputStream.close()

            println("Dictionary loaded: ${dictionary.size} words")
        } catch (e: Exception) {
            e.printStackTrace()
            println("Error loading dictionary: ${e.message}")
        }
    }

    /**
     * Calculate Levenshtein distance between two strings
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val s1Lower = s1.lowercase()
        val s2Lower = s2.lowercase()

        val dp = Array(s1Lower.length + 1) { IntArray(s2Lower.length + 1) }

        for (i in 0..s1Lower.length) {
            dp[i][0] = i
        }

        for (j in 0..s2Lower.length) {
            dp[0][j] = j
        }

        for (i in 1..s1Lower.length) {
            for (j in 1..s2Lower.length) {
                val cost = if (s1Lower[i - 1] == s2Lower[j - 1]) 0 else 1

                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // deletion
                    dp[i][j - 1] + 1,      // insertion
                    dp[i - 1][j - 1] + cost // substitution
                )
            }
        }

        return dp[s1Lower.length][s2Lower.length]
    }

    /**
     * Find the best correction for a recognized word using edit distance and word frequency
     * @param recognizedWord The word from gesture recognition
     * @param maxDistance Maximum edit distance to consider (default 2)
     * @return The corrected word, or the original if no good match found
     */
    fun correctWord(recognizedWord: String, maxDistance: Int = 2): String {
        val lowerWord = recognizedWord.lowercase()

        // If the word is already in dictionary, return it
        if (dictionary.contains(lowerWord)) {
            return recognizedWord
        }

        // Find candidates within edit distance threshold
        val candidates = mutableListOf<Pair<String, Int>>()

        for ((word, frequency) in dictionary) {
            val distance = levenshteinDistance(recognizedWord, word)

            if (distance <= maxDistance) {
                candidates.add(Pair(word, distance))
            }
        }

        if (candidates.isEmpty()) {
            return recognizedWord
        }

        // Sort by: 1) edit distance (lower is better), 2) frequency (higher is better)
        val bestMatch = candidates
            .sortedWith(compareBy<Pair<String, Int>> { it.second }
                .thenByDescending { dictionary[it.first] ?: 0L })
            .firstOrNull()

        return bestMatch?.first ?: recognizedWord
    }

    /**
     * Correct multiple words in a sentence
     */
    fun correctSentence(sentence: String): String {
        val words = sentence.split(" ")
        val correctedWords = words.map { word ->
            if (word.isNotBlank()) correctWord(word) else word
        }
        return correctedWords.joinToString(" ")
    }

    /**
     * Get top N suggestions for a recognized word
     */
    fun getSuggestions(recognizedWord: String, topN: Int = 3, maxDistance: Int = 2): List<String> {
        val suggestions = mutableListOf<Pair<String, Int>>()

        for ((word, _) in dictionary) {
            val distance = levenshteinDistance(recognizedWord, word)
            if (distance <= maxDistance) {
                suggestions.add(Pair(word, distance))
            }
        }

        // Sort by distance first, then by frequency
        return suggestions
            .sortedWith(compareBy<Pair<String, Int>> { it.second }
                .thenByDescending { dictionary[it.first] ?: 0L })
            .take(topN)
            .map { it.first }
    }
}
