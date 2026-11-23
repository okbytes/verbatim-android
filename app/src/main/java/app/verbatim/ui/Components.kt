package app.verbatim.ui

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.verbatim.Review
import java.text.DateFormatSymbols
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

object ReviewFormat {
    private val englishShortWeekdays = listOf("Su", "M", "Tu", "W", "Th", "F", "Sa")

    private fun weekdayFullNames(): List<String> = DateFormatSymbols().weekdays.drop(1)

    fun weekdayShort(cadence: Int): String {
        val idx = (cadence - 1).coerceIn(0, 6)
        val locale = Locale.getDefault()
        if (locale.language == Locale.ENGLISH.language) return englishShortWeekdays[idx]
        val dayOfWeek = if (idx == 0) DayOfWeek.SUNDAY else DayOfWeek.of(idx)
        return dayOfWeek.getDisplayName(TextStyle.SHORT_STANDALONE, locale).take(2)
    }

    fun weekdayFull(cadence: Int): String = weekdayFullNames()[(cadence - 1).coerceIn(0, 6)]

    fun scheduleName(cadence: Int): String =
        if (cadence == 0) "Daily" else weekdayFull(cadence)

    fun rowDateLabel(day: LocalDate, today: LocalDate): String {
        val locale = Locale.getDefault()
        return when {
            !day.isAfter(today) -> "TODAY"
            day == today.plusDays(1) -> "TMRW"
            else -> day.format(DateTimeFormatter.ofPattern("MMM d", locale)).uppercase(locale)
        }
    }

    fun nextReviewSentence(day: LocalDate, today: LocalDate): String =
        when {
            !day.isAfter(today) -> "Today"
            day == today.plusDays(1) -> "Tomorrow"
            else -> day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
        }

    fun accessibilitySummary(review: Review, today: LocalDate): String {
        val schedule =
            if (review.cadence == 0) "Daily review"
            else "Weekly review, ${weekdayFull(review.cadence)}"
        val whenDue =
            when {
                review.due(today) -> "Due today"
                review.day == today.plusDays(1) -> "Due tomorrow"
                else -> "Due ${review.day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))}"
            }
        return "$schedule. $whenDue."
    }

    fun formatReminderTime(context: Context, hour: Int, minute: Int): String {
        val time = LocalTime.of(hour, minute)
        val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
        return time.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
    }
}

/**
 * Compact rounded chip for a review cadence (repeat icon for Daily, weekday abbreviation for Weekly),
 * echoing the iOS MetadataBadge in native Material 3 styling.
 */
@Composable
fun CadenceBadge(
    cadence: Int,
    modifier: Modifier = Modifier,
    containerColor: Color = LocalVerbatimColors.current.badgeContainer,
    contentColor: Color = LocalVerbatimColors.current.badgeContent,
) {
    val description =
        if (cadence == 0) "Daily review" else "Weekly review, ${ReviewFormat.weekdayFull(cadence)}"
    Box(
        modifier =
            modifier
                .defaultMinSize(minWidth = 22.dp, minHeight = 20.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(containerColor)
                .padding(horizontal = 6.dp, vertical = 2.dp)
                .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (cadence == 0) {
            Icon(
                imageVector = Icons.Rounded.Repeat,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(13.dp),
            )
        } else {
            Text(
                text = ReviewFormat.weekdayShort(cadence),
                style = VerbatimTypography.badge,
                color = contentColor,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun MetadataChip(
    label: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Box(
        modifier =
            modifier
                .defaultMinSize(minHeight = 20.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(containerColor)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = VerbatimTypography.badge,
            color = contentColor,
            maxLines = 1,
        )
    }
}
