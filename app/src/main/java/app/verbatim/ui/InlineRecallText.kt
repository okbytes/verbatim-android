package app.verbatim.ui

import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import app.verbatim.Recall
import kotlin.math.max

data class UnclearSpanInfo(
    val start: Int,
    val end: Int,
)

/**
 * Renders a graded recitation preserving all original punctuation and line breaks:
 * - Said words: regular body ink
 * - Missed words: deep crimson ink with a soft tint background
 * - Extra spoken words (when [showExtraWords] is true): warm amber underlined ink
 * - Unclear words: regular ink with a row of round dots underneath and a [LinkAnnotation.Clickable]
 *   span for precise tap hit-testing, keyboard focus, and TalkBack accessibility activation.
 */
@Composable
fun InlineRecallText(
    result: Recall.Result,
    showExtraWords: Boolean,
    onUnclearTap: (expected: String, heard: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalVerbatimColors.current
    val bodyColor = MaterialTheme.colorScheme.onSurface
    val dotColor = MaterialTheme.colorScheme.onSurfaceVariant
    val currentOnUnclearTap by rememberUpdatedState(onUnclearTap)

    val segments = remember(result, showExtraWords) { result.segments(showExtraWords) }

    val (annotated, unclearSpans) =
        remember(segments, colors, bodyColor) {
            val spans = mutableListOf<UnclearSpanInfo>()
            val text =
                buildAnnotatedString {
                    for (seg in segments) {
                        when (seg) {
                            is Recall.Segment.Written -> append(seg.text)
                            is Recall.Segment.Missed -> {
                                withStyle(
                                    SpanStyle(
                                        color = colors.missedWord,
                                        background = colors.missedHighlight,
                                    )
                                ) {
                                    append(seg.text)
                                }
                            }
                            is Recall.Segment.Extra -> {
                                withStyle(
                                    SpanStyle(
                                        color = colors.extraWord,
                                        textDecoration = TextDecoration.Underline,
                                    )
                                ) {
                                    append(seg.text)
                                }
                            }
                            is Recall.Segment.Unclear -> {
                                val start = length
                                val expectedWord = seg.text
                                val heardWord = seg.heard
                                withLink(
                                    LinkAnnotation.Clickable(
                                        tag = "unclear_$start",
                                        linkInteractionListener = {
                                            currentOnUnclearTap(expectedWord, heardWord)
                                        },
                                    )
                                ) {
                                    append(expectedWord)
                                }
                                spans += UnclearSpanInfo(start, length)
                            }
                        }
                    }
                }
            text to spans
        }

    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    BasicText(
        text = annotated,
        style = VerbatimTypography.entryBody.copy(color = bodyColor),
        onTextLayout = { layoutResult = it },
        modifier =
            modifier.drawWithContent {
                drawContent()
                val layout = layoutResult ?: return@drawWithContent
                if (unclearSpans.isEmpty()) return@drawWithContent
                val radius = 1.6f * density
                val diameter = radius * 2f
                val pitch = diameter * 2.1f
                for (span in unclearSpans) {
                    if (span.start >= span.end || span.end > annotated.length) continue
                    val startBox = layout.getBoundingBox(span.start)
                    val endBox = layout.getBoundingBox(span.end - 1)
                    val line = layout.getLineForOffset(span.start)
                    val left = startBox.left
                    val right =
                        if (layout.getLineForOffset(span.end - 1) == line) endBox.right
                        else layout.getLineRight(line)
                    val width = (right - left).coerceAtLeast(diameter)
                    val count = max(1, ((width - diameter) / pitch).toInt() + 1)
                    val spanWidth = (count - 1) * pitch + diameter
                    val startX = left + (width - spanWidth) / 2f + radius
                    val y =
                        (layout.getLineBaseline(line) + 5f * density)
                            .coerceAtMost(layout.getLineBottom(line) - radius)
                    for (k in 0 until count) {
                        drawCircle(
                            color = dotColor,
                            radius = radius,
                            center = Offset(startX + k * pitch, y),
                        )
                    }
                }
            },
    )
}
