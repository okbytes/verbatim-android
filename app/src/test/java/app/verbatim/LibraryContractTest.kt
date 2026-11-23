package app.verbatim

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class LibraryContractTest {
    private val lib =
        Library(
            listOf(
                Entry("Poem", "First line\n    Café ☕", true, Review(6, LocalDate.of(2026, 2, 6))),
                Entry("Speech", "Opening words", false, null),
            ),
            hour = 18,
            minute = 45,
        )

    @Test
    fun androidBackupSurvivesRoundTrip() {
        assertEquals(lib, Library.decode(lib.encode()))
        val added = Library().adding(lib).adding(lib)
        assertEquals(lib.entries.map { it.copy(progress = false, review = null) }, added.entries)
    }

    @Test
    fun noPartialDecodeOfUnknownSchedules() {
        assertThrows(IllegalStateException::class.java) {
            Library.decode(lib.encode().replace("weekly", "monthly"))
        }
    }

    @Test
    fun earlyAndroidStoreStillOpensButIsNotAnImportFormat() {
        val earlyStore = lib.encode().replace("verbatim-android-library", "verbatim-backup")
        assertEquals(lib, Library.decodePrivateStore(earlyStore))
        assertThrows(IllegalArgumentException::class.java) { Library.decode(earlyStore) }
    }

    @Test
    fun weeklyUsesSundayBasedWeekdays() {
        val sun = LocalDate.of(2026, 1, 4)
        assertEquals(sun, Review.starting(1, sun).day)
        assertEquals(sun.plusDays(7), Review.starting(1, sun.plusDays(1)).day)
        assertEquals(sun.plusDays(6), Review.starting(7, sun).day)
    }

    @Test
    fun fillerWordsAndSkippedNumbersWithholdVerbatim() {
        val r = Recall.grade("1. A red bird!", listOf("a red um bird"))
        assertFalse(r.verbatim)
        assertEquals(listOf("1", "um"), r.marks.filter { it.kind != "said" }.map { it.value })
        assertTrue(Recall.grade("A red bird", listOf("a read bird", "a red bird")).verbatim)
    }

    @Test
    fun pasteOnlyStripsNumbersFromRecognizedCopies() {
        val raw = "Part 1\n  [2] Some text.\n"
        assertEquals(null to raw, Citation.paste(raw))
        assertEquals("John 1:1" to "Some text.", Citation.paste("John 1:1\n[1] Some text."))
    }

    @Test
    fun recallSegmentsPreservePunctuationLineBreaksAndExtraToggle() {
        val text = "For God so loved the world,\nthat he gave his Son."
        val result =
            Recall.grade(
                text,
                listOf("for god so liked the world that he um gave his son"),
            )
        assertFalse(result.verbatim)
        assertEquals(1, result.missedCount)
        assertEquals(2, result.extraCount)
        assertEquals(0, result.unclearCount)

        val withExtras = result.segments(showExtraWords = true)
        assertEquals(
            listOf(
                Recall.Segment.Written("For God so "),
                Recall.Segment.Missed("loved"),
                Recall.Segment.Written(" "),
                Recall.Segment.Extra("liked"),
                Recall.Segment.Written(" the world,\nthat he "),
                Recall.Segment.Extra("um"),
                Recall.Segment.Written(" gave his Son."),
            ),
            withExtras,
        )

        val withoutExtras = result.segments(showExtraWords = false)
        assertEquals(
            listOf(
                Recall.Segment.Written("For God so "),
                Recall.Segment.Missed("loved"),
                Recall.Segment.Written(" the world,\nthat he gave his Son."),
            ),
            withoutExtras,
        )
    }

    @Test
    fun recallUnclearSegmentCarriesPrimaryHeardWord() {
        val result = Recall.grade("A red bird.", listOf("a read bird", "a red bird"))
        assertTrue(result.verbatim)
        assertEquals(1, result.unclearCount)
        assertEquals(
            listOf(
                Recall.Segment.Written("A "),
                Recall.Segment.Unclear("red", heard = "read"),
                Recall.Segment.Written(" bird."),
            ),
            result.segments(showExtraWords = true),
        )
    }

    @Test
    fun duplicateTitleAvoidsExistingCollisions() {
        val existing =
            Library(
                listOf(
                    Entry("Psalm 23", "The Lord", false, null),
                    Entry("Psalm 23 (Copy)", "The Lord", false, null),
                )
            )
        assertEquals("Psalm 23 (Copy 2)", existing.duplicateTitle("Psalm 23"))
        assertEquals("Speech (Copy)", lib.duplicateTitle("Speech"))
    }

    @Test
    fun addingWithStatsTracksInLibraryAndFileDuplicatesSeparately() {
        val incoming =
            Library(
                listOf(
                    Entry("Poem", "dup", false, null),
                    Entry("Fresh", "text", true, Review(0, LocalDate.of(2026, 3, 1))),
                    Entry("fresh", "text2", false, null),
                )
            )
        val stats = lib.addingWithStats(incoming)
        assertEquals(1, stats.added)
        assertEquals(1, stats.skippedInLibrary)
        assertEquals(1, stats.skippedRepeated)
        assertEquals(3, stats.library.entries.size)
        val addedEntry = stats.library.entries.last()
        assertEquals("Fresh", addedEntry.title)
        assertFalse(addedEntry.progress)
        assertNull(addedEntry.review)
    }
}
