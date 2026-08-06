package com.amazecc.app.shared.utils

data class ParsedFaculty(
    val id: String?,       // e.g., "10294"
    val name: String,      // e.g., "Amit Kumar" or "Dr. Amit Kumar"
    val school: String?,   // e.g., "SCOPE"
    val raw: String
)

object FacultyUtils {

    private val knownSchools = setOf(
        "SCOPE", "SELECT", "SENSE", "SITE", "SSL", "SMEC", "VIM", "VITBS",
        "SAS", "SCH", "SBST", "SCORE", "HOTEL", "VITSOL", "VFSTR", "VIT",
        "SEEE", "SMEC", "COE"
    )

    /**
     * Parses raw faculty strings from VTOP.
     * Handles space-separated ("10294 AMIT KUMAR SCOPE"), hyphenated ("10294 - AMIT KUMAR - SCOPE"),
     * and plain names ("DR. AMIT KUMAR").
     */
    fun parseFaculty(raw: String?): ParsedFaculty {
        if (raw.isNullOrBlank()) return ParsedFaculty(null, "", null, "")
        val trimmed = raw.trim().replace(Regex("\\s+"), " ")

        val cleanStr = trimmed.replace("-", " ").replace("–", " ").replace(Regex("\\s+"), " ").trim()
        val tokens = cleanStr.split(" ").filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return ParsedFaculty(null, "", null, trimmed)

        var id: String? = null
        var school: String? = null
        var startIndex = 0
        var endIndex = tokens.size

        // 1. Check if first token is Faculty ID (digits only or length 4..8)
        if (tokens[0].all { it.isDigit() } || (tokens[0].any { it.isDigit() } && tokens[0].length in 4..8)) {
            id = tokens[0]
            startIndex = 1
        }

        // 2. Check if last token is School / Dept acronym
        if (endIndex > startIndex + 1) {
            val lastToken = tokens.last().uppercase()
            if (knownSchools.contains(lastToken) || (lastToken.length in 2..6 && lastToken.all { it.isLetter() } && lastToken == tokens.last().uppercase())) {
                school = lastToken
                endIndex -= 1
            }
        }

        // 3. Middle tokens form the Faculty Name
        val nameTokens = if (startIndex < endIndex) tokens.subList(startIndex, endIndex) else tokens
        val rawName = nameTokens.joinToString(" ")
        val formattedName = formatName(rawName)

        return ParsedFaculty(
            id = id,
            name = formattedName.ifEmpty { trimmed },
            school = school,
            raw = trimmed
        )
    }

    private fun formatName(nameStr: String): String {
        var clean = nameStr.trim().replace(Regex("\\s+"), " ")
        if (clean.isEmpty()) return ""

        if (clean == clean.uppercase() && clean.length > 2) {
            clean = clean.lowercase().split(" ").joinToString(" ") { word ->
                when {
                    word.lowercase() == "dr" || word.lowercase() == "dr." -> "Dr."
                    word.lowercase() == "prof" || word.lowercase() == "prof." -> "Prof."
                    word.lowercase() == "mr" || word.lowercase() == "mr." -> "Mr."
                    word.lowercase() == "mrs" || word.lowercase() == "mrs." -> "Mrs."
                    word.lowercase() == "ms" || word.lowercase() == "ms." -> "Ms."
                    word.length > 1 -> word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                    else -> word.uppercase()
                }
            }
        }
        return clean
    }

    private val titleRegex = Regex("""\b(dr|prof|mr|mrs|ms)\.?\b""")

    fun normalizeName(name: String): String {
        var n = name.lowercase()
        n = n.replace(titleRegex, " ")
        n = n.replace(Regex("""[^a-z0-9]+"""), " ")
        return n.trim()
    }

    /**
     * Bigram Dice coefficient of two names (0.0 - 1.0). Identical normalized names score 1.0.
     */
    fun nameSimilarity(a: String, b: String): Double {
        val na = normalizeName(a)
        val nb = normalizeName(b)
        if (na.isEmpty() || nb.isEmpty()) return 0.0
        if (na == nb) return 1.0
        val bigramsA = na.windowed(2).toSet()
        val bigramsB = nb.windowed(2).toSet()
        if (bigramsA.isEmpty() || bigramsB.isEmpty()) return 0.0
        val intersection = bigramsA.intersect(bigramsB).size
        return 2.0 * intersection / (bigramsA.size + bigramsB.size)
    }
}
