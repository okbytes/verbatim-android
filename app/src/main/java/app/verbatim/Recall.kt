package app.verbatim

import java.util.Locale

object Recall {
    private val word = Regex("[\\p{L}\\p{N}][\\p{L}\\p{M}\\p{N}]*(?:['’][\\p{L}\\p{M}\\p{N}]+)*")

    private fun key(s: String) = s.lowercase(Locale.ROOT).replace('’', '\'')

    private fun words(s: String) = word.findAll(s).map { it.value }.toList()

    data class Mark(
        val kind: String,
        val value: String,
        val range: IntRange? = null,
        val heard: String? = null,
    )

    sealed class Segment {
        data class Written(val text: String) : Segment()

        data class Missed(val text: String) : Segment()

        data class Unclear(val text: String, val heard: String) : Segment()

        data class Extra(val text: String) : Segment()
    }

    private data class ExtraNote(val location: Int, val text: String)

    data class Result(
        val marks: List<Mark>,
        val sourceText: String = "",
        val transcript: String = "",
    ) {
        val verbatim
            get() = marks.isNotEmpty() && marks.all { it.kind == "said" || it.kind == "unclear" }

        val missedCount
            get() = marks.count { it.kind == "missed" }

        val unclearCount
            get() = marks.count { it.kind == "unclear" }

        val extraCount
            get() = marks.count { it.kind == "extra" }

        fun annotated() =
            marks.joinToString(" ") {
                when (it.kind) {
                    "missed" -> "[missed: ${it.value}]"
                    "extra" -> "[extra: ${it.value}]"
                    "unclear" -> "[unclear: ${it.value}]"
                    else -> it.value
                }
            }

        /**
         * Preserves original punctuation, whitespace, and line breaks from [sourceText], marking
         * missed and unclear words in place and slotting extra spoken words beside where they were
         * said when [showExtraWords] is true.
         */
        fun segments(showExtraWords: Boolean): List<Segment> {
            val source = sourceText
            if (source.isEmpty()) return emptyList()

            val missedStarts = mutableSetOf<Int>()
            val unclearAt = mutableMapOf<Int, String>()
            val wordEnds = mutableMapOf<Int, Int>()
            val notes = mutableListOf<ExtraNote>()

            val heardWords = mutableListOf<String>()
            var unmatchedCount = 0
            var lastUnmatchedEnd: Int? = null
            var lastMatchedEnd: Int? = null

            fun flushExtra(beforeLocation: Int) {
                if (heardWords.isNotEmpty()) {
                    val normalized = heardWords.joinToString(" ").lowercase(Locale.ROOT)
                    val location =
                        when {
                            lastUnmatchedEnd != null && unmatchedCount <= SUBSTITUTION_LIMIT ->
                                lastUnmatchedEnd!!
                            unmatchedCount > 0 && lastMatchedEnd != null -> lastMatchedEnd!!
                            else -> beforeLocation
                        }
                    notes += ExtraNote(location, normalized)
                }
                heardWords.clear()
                unmatchedCount = 0
                lastUnmatchedEnd = null
            }

            for (mark in marks) {
                if (mark.kind == "extra") {
                    heardWords += mark.value
                    continue
                }
                val range = mark.range ?: continue
                wordEnds[range.first] = range.last + 1
                when (mark.kind) {
                    "said" -> {
                        flushExtra(range.first)
                        lastMatchedEnd = range.last + 1
                    }
                    "unclear" -> {
                        flushExtra(range.first)
                        lastMatchedEnd = range.last + 1
                        unclearAt[range.first] = mark.heard.orEmpty().lowercase(Locale.ROOT)
                    }
                    "missed" -> {
                        unmatchedCount++
                        missedStarts += range.first
                        lastUnmatchedEnd = range.last + 1
                    }
                }
            }
            flushExtra(source.length)

            val notesByLocation =
                if (showExtraWords) notes.groupBy { it.location } else emptyMap()
            val boundaries =
                buildSet {
                        add(0)
                        add(source.length)
                        for ((start, end) in wordEnds) {
                            add(start)
                            add(end)
                        }
                        addAll(notesByLocation.keys)
                    }
                    .sorted()

            val result = mutableListOf<Segment>()

            fun appendSegment(segment: Segment) {
                val last = result.lastOrNull()
                if (last is Segment.Written && segment is Segment.Written) {
                    result[result.lastIndex] = Segment.Written(last.text + segment.text)
                } else if (last is Segment.Extra && segment is Segment.Extra) {
                    result[result.lastIndex] = Segment.Extra(last.text + " " + segment.text)
                } else {
                    result += segment
                }
            }

            fun isNonWhitespace(offset: Int): Boolean =
                offset in source.indices && !source[offset].isWhitespace()

            for (i in boundaries.indices) {
                val loc = boundaries[i]
                val notesHere = notesByLocation[loc]
                if (!notesHere.isNullOrEmpty()) {
                    if (isNonWhitespace(loc - 1)) appendSegment(Segment.Written(" "))
                    appendSegment(Segment.Extra(notesHere.joinToString(" ") { it.text }))
                    if (isNonWhitespace(loc)) appendSegment(Segment.Written(" "))
                }
                if (i + 1 >= boundaries.size) continue
                val next = boundaries[i + 1]
                if (next <= loc) continue
                val slice = source.substring(loc, next)
                val seg =
                    when {
                        loc in missedStarts -> Segment.Missed(slice)
                        loc in unclearAt -> Segment.Unclear(slice, unclearAt.getValue(loc))
                        else -> Segment.Written(slice)
                    }
                appendSegment(seg)
            }
            return result
        }

        private companion object {
            const val SUBSTITUTION_LIMIT = 3
        }
    }

    fun grade(text: String, readings: List<String>): Result {
        val normalizedText = Entry.normalizeLineEndings(text)
        val expectedMatches = word.findAll(normalizedText).toList()
        val expected = expectedMatches.map { it.value }
        val expectedRanges = expectedMatches.map { it.range }
        val primaryTranscript = readings.firstOrNull().orEmpty()
        val heard = words(primaryTranscript)
        val a = expected.map(::key)
        val b = heard.map(::key)
        // Credit alternate readings only where all other words match the best reading.
        val alternatives = mutableMapOf<Int, MutableSet<String>>()
        for (reading in readings.drop(1)) {
            val alt = words(reading).map(::key)
            if (alt.size != b.size) continue
            val i = b.indices.singleOrNull { b[it] != alt[it] } ?: continue
            alternatives.getOrPut(i) { mutableSetOf() }.add(alt[i])
        }
        fun matches(i: Int, j: Int) = a[i] == b[j] || alternatives[j]?.contains(a[i]) == true
        // Hirschberg alignment: two score rows instead of a word-count-squared matrix.
        fun row(start: Int, end: Int, left: Int, right: Int, reverse: Boolean): IntArray {
            val scores = IntArray(right - left + 1)
            for (k in 0 until end - start) {
                val i = if (reverse) end - 1 - k else start + k
                var prev = 0
                for (p in 1 until scores.size) {
                    val j = if (reverse) right - p else left + p - 1
                    val old = scores[p]
                    scores[p] = if (matches(i, j)) prev + 1 else maxOf(old, scores[p - 1])
                    prev = old
                }
            }
            return scores
        }
        val marks = mutableListOf<Mark>()
        fun align(start: Int, end: Int, left: Int, right: Int) {
            if (start == end) {
                for (j in left until right) marks += Mark("extra", heard[j])
                return
            }
            if (left == right) {
                for (i in start until end) marks += Mark("missed", expected[i], expectedRanges[i])
                return
            }
            if (end == start + 1) {
                val hit = (left until right).firstOrNull { matches(start, it) }
                if (hit == null) marks += Mark("missed", expected[start], expectedRanges[start])
                for (j in left until right) marks +=
                    if (j == hit) {
                        if (a[start] == b[j]) {
                            Mark("said", expected[start], expectedRanges[start])
                        } else {
                            Mark("unclear", expected[start], expectedRanges[start], heard = heard[j])
                        }
                    } else {
                        Mark("extra", heard[j])
                    }
                return
            }
            val mid = (start + end) / 2
            val f = row(start, mid, left, right, false)
            val r = row(mid, end, left, right, true)
            val split = left + f.indices.maxBy { f[it] + r[f.lastIndex - it] }
            align(start, mid, left, split)
            align(mid, end, split, right)
        }
        align(0, a.size, 0, b.size)
        return Result(marks = marks, sourceText = normalizedText, transcript = primaryTranscript)
    }
}
