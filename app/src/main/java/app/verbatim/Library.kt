package app.verbatim

import java.text.Normalizer
import java.time.LocalDate
import java.util.Locale
import java.util.UUID
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

/**
 * A library entry. Carries a stable in-memory [id] for navigation and Compose keying while keeping
 * value equality based solely on the persisted fields so backup round-trips compare cleanly.
 */
class Entry(
    val title: String,
    val text: String,
    val progress: Boolean,
    val review: Review?,
    val id: String = UUID.randomUUID().toString(),
) {
    fun copy(
        title: String = this.title,
        text: String = this.text,
        progress: Boolean = this.progress,
        review: Review? = this.review,
        id: String = this.id,
    ): Entry = Entry(title = title, text = text, progress = progress, review = review, id = id)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Entry) return false
        return title == other.title &&
            text == other.text &&
            progress == other.progress &&
            review == other.review
    }

    override fun hashCode(): Int {
        var result = title.hashCode()
        result = 31 * result + text.hashCode()
        result = 31 * result + progress.hashCode()
        result = 31 * result + (review?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String =
        "Entry(title=$title, text=$text, progress=$progress, review=$review)"

    companion object {
        /** Normalize line endings without flattening tabs, poetry, or paragraph boundaries. */
        fun normalizeLineEndings(raw: String): String =
            raw.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace('\u2028', '\n')
                .replace("\u2029", "\n\n")
                .replace('\u0085', '\n')

        /** Canonical comparison key matching NFC-normalized, trimmed, case-folded text. */
        fun normalizedTitleKey(raw: String): String =
            Normalizer.normalize(raw.trim(), Normalizer.Form.NFC).lowercase(Locale.ROOT)

        fun normalizedBodyKey(raw: String): String =
            Normalizer.normalize(normalizeLineEndings(raw).trim(), Normalizer.Form.NFC)
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
        val existing = entries.map { Entry.normalizedTitleKey(it.title) }.toSet()
        val seen = existing.toMutableSet()
        val toAdd = mutableListOf<Entry>()
        var skippedInLibrary = 0
        var skippedRepeated = 0
        for (item in other.entries) {
            val key = Entry.normalizedTitleKey(item.title)
            if (key in existing) {
                skippedInLibrary++
            } else if (!seen.add(key)) {
                skippedRepeated++
            } else {
                toAdd += item.copy(progress = false, review = null, id = UUID.randomUUID().toString())
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
        val taken = entries.map { Entry.normalizedTitleKey(it.title) }.toSet()
        val base = sourceTitle.trim().ifEmpty { "Entry" }
        val first = "$base (Copy)"
        if (Entry.normalizedTitleKey(first) !in taken) return first
        var n = 2
        while (true) {
            val candidate = "$base (Copy $n)"
            if (Entry.normalizedTitleKey(candidate) !in taken) return candidate
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
        private const val IOS_FORMAT = "verbatim-backup"

        /** Accepts both Android and iPhone Verbatim v1 backup JSON files. */
        fun decode(raw: String): Library = decode(JSONObject(raw))

        private fun decode(json: JSONObject): Library {
            val format = json.get("format")
            require(
                (format == FORMAT || format == IOS_FORMAT) && json.get("version") == 1
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
