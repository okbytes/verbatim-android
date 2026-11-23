package app.verbatim.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import app.verbatim.Recall
import kotlin.math.max

data class UnclearSpanInfo(
    val start: Int,
    val end: Int,
    val expected: String,
    val heard: String,
)

/**
 * Renders a graded recitation preserving all original punctuation and line breaks:
 * - Said words: regular body ink
 * - Missed words: deep crimson ink with a soft tint background
 * - Extra spoken words (when [showExtraWords] is true): warm amber underlined ink
 * - Unclear words: regular ink with a clean row of round dots underneath; tapping calls [onUnclearTap]
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

    val segments = remember(result, showExtraWords) { result.segments(showExtraWords) }

    val (annotated, unclearSpans, spokenDescription) =
        remember(segments, colors, bodyColor) {
            val spans = mutableListOf<UnclearSpanInfo>()
            val a11y = StringBuilder()
            val text =
                buildAnnotatedString {
                    for (seg in segments) {
                        when (seg) {
                            is Recall.Segment.Written -> {
                                append(seg.text)
                                a11y.append(seg.text)
                            }
                            is Recall.Segment.Missed -> {
                                withStyle(
                                    SpanStyle(
                                        color = colors.missedWord,
                                        background = colors.missedHighlight,
                                    )
                                ) {
                                    append(seg.text)
                                }
                                a11y.append(seg.text).append(" (missed)")
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
                                a11y.append("extra word: ").append(seg.text)
                            }
                            is Recall.Segment.Unclear -> {
                                val start = length
                                append(seg.text)
                                val end = length
                                spans += UnclearSpanInfo(start, end, seg.text, seg.heard)
                                a11y.append(seg.text)
                                if (seg.heard.isNotEmpty()) {
                                    a11y.append(" (unclear, heard ").append(seg.heard).append(")")
                                } else {
                                    a11y.append(" (unclear)")
                                }
                            }
                        }
                    }
                }
            Triple(text, spans, a11y.toString())
        }

    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    BasicText(
        text = annotated,
        style = VerbatimTypography.entryBody.copy(color = bodyColor),
        onTextLayout = { layoutResult = it },
        modifier =
            modifier
                .semantics { contentDescription = spokenDescription }
                .drawWithContent {
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
                }
                .pointerInput(unclearSpans) {
                    if (unclearSpans.isEmpty()) return@pointerInput
                    detectTapGestures { offset ->
                        val layout = layoutResult ?: return@detectTapGestures
                        val charOffset = layout.getOffsetForPosition(offset)
                        val hit =
                            unclearSpans.firstOrNull { charOffset in it.start until it.end }
                                ?: unclearSpans.firstOrNull { span ->
                                    if (span.start >= annotated.length) return@firstOrNull false
                                    val box = layout.getBoundingBox(span.start)
                                    val endBox = layout.getBoundingBox((span.end - 1).coerceAtLeast(span.start))
                                    val pad = 12f * density
                                    offset.x in (box.left - pad)..(endBox.right + pad) &&
                                        offset.y in (box.top - pad)..(box.bottom + pad)
                                }
                        if (hit != null) {
                            onUnclearTap(hit.expected, hit.heard)
                        }
                    }
                },
    )
}
