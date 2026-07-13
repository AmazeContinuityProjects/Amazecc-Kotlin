package com.amazecc.app.shared.utils

import kotlin.math.max
import kotlin.math.min

object StringSimilarity {
    fun getSimilarity(s1: String?, s2: String?): Double {
        if (s1.isNullOrEmpty() || s2.isNullOrEmpty()) return 0.0
        
        val str1 = s1.lowercase().trim()
        val str2 = s2.lowercase().trim()
        
        if (str1 == str2) return 1.0
        
        if (str1.contains(str2) || str2.contains(str1)) {
            // If one is fully contained in another, weight it highly (0.85 to 0.95 depending on length diff)
            val minLen = min(str1.length, str2.length).toDouble()
            val maxLen = max(str1.length, str2.length).toDouble()
            return 0.85 + (0.1 * (minLen / maxLen))
        }
        
        // Levenshtein distance
        val costs = IntArray(str2.length + 1)
        for (i in 0..str1.length) {
            var lastValue = i
            for (j in 0..str2.length) {
                if (i == 0) {
                    costs[j] = j
                } else {
                    if (j > 0) {
                        var newValue = costs[j - 1]
                        if (str1[i - 1] != str2[j - 1]) {
                            newValue = min(min(newValue, lastValue), costs[j]) + 1
                        }
                        costs[j - 1] = lastValue
                        lastValue = newValue
                    }
                }
            }
            if (i > 0) {
                costs[str2.length] = lastValue
            }
        }
        
        val maxLen = max(str1.length, str2.length).toDouble()
        val distance = costs[str2.length].toDouble()
        return (maxLen - distance) / maxLen
    }
}
