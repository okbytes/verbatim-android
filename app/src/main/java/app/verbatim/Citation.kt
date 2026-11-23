package app.verbatim

/** Bible-specific conveniences apply only to the explicit Paste action and optional title order. */
object Citation {
    private val books =
        "Genesis|Exodus|Leviticus|Numbers|Deuteronomy|Joshua|Judges|Ruth|1 Samuel|2 Samuel|1 Kings|2 Kings|1 Chronicles|2 Chronicles|Ezra|Nehemiah|Esther|Job|Psalms|Proverbs|Ecclesiastes|Song of Solomon|Isaiah|Jeremiah|Lamentations|Ezekiel|Daniel|Hosea|Joel|Amos|Obadiah|Jonah|Micah|Nahum|Habakkuk|Zephaniah|Haggai|Zechariah|Malachi|Matthew|Mark|Luke|John|Acts|Romans|1 Corinthians|2 Corinthians|Galatians|Ephesians|Philippians|Colossians|1 Thessalonians|2 Thessalonians|1 Timothy|2 Timothy|Titus|Philemon|Hebrews|James|1 Peter|2 Peter|1 John|2 John|3 John|Jude|Revelation"
            .lowercase()
            .split('|')
    private val pattern =
        Regex(
            """^((?:[1-3] )?[\p{L} ]+) (\d+)(?::(\d+))?(?:[-–,:][\d: ,–-]+)?(?:,? ([A-Z][A-Z0-9]+))?$"""
        )

    private fun rank(book: String): Int =
        books.indexOf(
            when (book.lowercase()) {
                "psalm" -> "psalms"
                "song of songs" -> "song of solomon"
                else -> book.lowercase()
            }
        )

    private fun key(s: String): List<Int> {
        val groups = pattern.matchEntire(s.trim())?.groupValues ?: return listOf(999, 0, 0)
        val book = rank(groups[1])
        return listOf(
            if (book < 0) 999 else book,
            groups[2].toIntOrNull() ?: 0,
            groups[3].toIntOrNull() ?: 0,
        )
    }

    val order =
        Comparator<String> { a, b ->
            val x = key(a)
            val y = key(b)
            x.indices.map { x[it].compareTo(y[it]) }.firstOrNull { it != 0 } ?: a.compareTo(b, true)
        }

    fun paste(raw: String): Pair<String?, String> {
        val lines = raw.replace(Regex("\\p{Cf}"), "").trim().lines().toMutableList()
        if (lines.size > 1 && Regex("https://(?:www\\.)?bible\\.com/.*").matches(lines.last()))
            lines.removeAt(lines.lastIndex)
        fun reference(s: String): String? {
            val m = pattern.matchEntire(s.trim()) ?: return null
            if (
                m.groupValues[4].isEmpty() &&
                    (rank(m.groupValues[1]) < 0 || m.groupValues[3].isEmpty())
            )
                return null
            return s.trim().removeSuffix(m.groupValues[4]).trimEnd(' ', ',')
        }
        fun paragraph(s: String) = s.trim().replace(Regex("\\s+"), " ")
        val body = lines.drop(1).joinToString("\n").trim()
        val heading = reference(lines.first())
        if (heading != null && Regex("^\\[\\d+]\\s").containsMatchIn(body))
            return heading to paragraph(body.replace(Regex("(?<!\\S)\\[\\d+](?=\\s)"), ""))
        val joined = lines.joinToString("\n").trimEnd()
        val last = joined.substringAfterLast('\n')
        reference(last)?.let {
            if ('\n' in joined)
                return it to
                    paragraph(joined.substringBeforeLast('\n'))
                        .removeSurrounding("“", "”")
                        .removeSurrounding("\"")
        }
        Regex("\\(([^()\\n]+)\\)$").find(joined)?.let { m ->
            reference(m.groupValues[1])?.let {
                return it to paragraph(joined.substring(0, m.range.first))
            }
        }
        return null to raw
    }
}
