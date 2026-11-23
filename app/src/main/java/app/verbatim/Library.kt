package app.verbatim

import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

// Review cadence: 0 = daily, 1 = Sunday ... 7 = Saturday.
data class Review(val cadence: Int, val day: LocalDate) {
    fun due(today: LocalDate) = !day.isAfter(today)

    companion object {
        fun starting(cadence: Int, from: LocalDate): Review {
            require(cadence in 0..7)
            val offset =
                if (cadence == 0) 0 else Math.floorMod(cadence - (from.dayOfWeek.value % 7 + 1), 7)
            return Review(cadence, from.plusDays(offset.toLong()))
        }
    }
}

data class Entry(val title: String, val text: String, val progress: Boolean, val review: Review?) {
    companion object {
        /** Normalize line endings without flattening tabs, poetry, or paragraph boundaries. */
        fun normalizeLineEndings(raw: String): String =
            raw.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace('\u2028', '\n')
                .replace("\u2029", "\n\n")
                .replace('\u0085', '\n')
    }
}

data class AddEntriesResult(
    val library: Library,
    val added: Int,
    val skippedInLibrary: Int,
    val skippedRepeated: Int,
)

data class Library(val entries: List<Entry> = emptyList(), val hour: Int = 9, val minute: Int = 0) {
    fun adding(other: Library): Library = addingWithStats(other).library

    fun addingWithStats(other: Library): AddEntriesResult {
        val existing = entries.map { it.title.trim().lowercase() }.toSet()
        val seen = existing.toMutableSet()
        val toAdd = mutableListOf<Entry>()
        var skippedInLibrary = 0
        var skippedRepeated = 0
        for (item in other.entries) {
            val key = item.title.trim().lowercase()
            if (key in existing) {
                skippedInLibrary++
            } else if (!seen.add(key)) {
                skippedRepeated++
            } else {
                toAdd += item.copy(progress = false, review = null)
            }
        }
        return AddEntriesResult(
            library = copy(entries = entries + toAdd),
            added = toAdd.size,
            skippedInLibrary = skippedInLibrary,
            skippedRepeated = skippedRepeated,
        )
    }

    fun duplicateTitle(sourceTitle: String): String {
        val taken = entries.map { it.title.trim().lowercase() }.toSet()
        val base = sourceTitle.trim().ifEmpty { "Entry" }
        val first = "$base (Copy)"
        if (first.lowercase() !in taken) return first
        var n = 2
        while (true) {
            val candidate = "$base (Copy $n)"
            if (candidate.lowercase() !in taken) return candidate
            n++
        }
    }

    fun encode(): String {
        val items = JSONArray()
        for (e in entries) {
            val item =
                JSONObject()
                    .put("reference", e.title)
                    .put("text", e.text)
                    .put("isInProgress", e.progress)
            e.review?.let { r ->
                val review =
                    JSONObject()
                        .put("nextReviewDate", r.day.toString())
                        .put("cadence", if (r.cadence == 0) "daily" else "weekly")
                if (r.cadence != 0) review.put("weekday", r.cadence)
                item.put("review", review)
            }
            items.put(item)
        }
        return JSONObject()
            .put("format", FORMAT)
            .put("version", 1)
            .put("entries", items)
            .put("reminderHour", hour)
            .put("reminderMinute", minute)
            .toString(2)
    }

    companion object {
        private const val FORMAT = "verbatim-android-library"

        fun decode(raw: String): Library = decode(JSONObject(raw), allowEarlyStore = false)

        // The first Android build used a different header for its private library.
        internal fun decodePrivateStore(raw: String): Library =
            decode(JSONObject(raw), allowEarlyStore = true)

        private fun decode(json: JSONObject, allowEarlyStore: Boolean): Library {
            val format = json.get("format")
            require(
                (format == FORMAT || (allowEarlyStore && format == "verbatim-backup")) &&
                    json.get("version") == 1
            ) {
                "Unrecognized backup format or version."
            }
            val hour = json.get("reminderHour") as Int
            val minute = json.get("reminderMinute") as Int
            require(hour in 0..23 && minute in 0..59)
            val items = json.getJSONArray("entries")
            val entries =
                (0 until items.length()).map {
                    val obj = items.getJSONObject(it)
                    val title = obj.get("reference") as String
                    val text = obj.get("text") as String
                    require(title.isNotBlank() && text.isNotBlank())
                    val review =
                        if (obj.isNull("review")) null
                        else
                            obj.getJSONObject("review").let { r ->
                                val cadence =
                                    when (r.get("cadence")) {
                                        "daily" -> {
                                            require(r.isNull("weekday"))
                                            0
                                        }
                                        "weekly" ->
                                            (r.get("weekday") as Int).also { n ->
                                                require(n in 1..7)
                                            }
                                        else -> error("Unknown review schedule")
                                    }
                                Review(cadence, LocalDate.parse(r.get("nextReviewDate") as String))
                            }
                    Entry(title, text, obj.get("isInProgress") as Boolean, review)
                }
            return Library(entries, hour, minute)
        }
    }
}
