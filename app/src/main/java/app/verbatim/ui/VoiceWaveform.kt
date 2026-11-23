package app.verbatim.ui

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin

enum class WaveformActivity {
    Preparing,
    Listening,
    Transcribing,
    Succeeded,
}

private data class WaveBand(
    val centre: Double,
    val width: Double,
    val cycles: Double,
    val speed: Double,
    val phase: Double,
    val height: Double,
)

private val waveBands =
    listOf(
        WaveBand(centre = 0.42, width = 0.22, cycles = 2.6, speed = 0.5, phase = 0.0, height = 0.9),
        WaveBand(centre = 0.58, width = 0.24, cycles = 3.1, speed = -0.7, phase = 2.1, height = 1.0),
        WaveBand(centre = 0.50, width = 0.18, cycles = 3.8, speed = 0.9, phase = 4.2, height = 0.75),
    )

private const val RING_COUNT = 12

private val ringAngles: List<Double> =
    (0 until RING_COUNT)
        .map { it.toDouble() / RING_COUNT * 2.0 * PI }
        .sortedWith(compareBy({ round(cos(it) * 1_000.0) }, { sin(it) }))

private fun smoothstep(value: Double, lower: Double, upper: Double): Double {
    val t = ((value - lower) / (upper - lower)).coerceIn(0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)
}

private fun lerpDouble(from: Double, to: Double, t: Double): Double = from + (to - from) * t

private fun waveAmplitude(u: Double, time: Double): Double {
    var highest = 0.0
    for (band in waveBands) {
        val centre = band.centre + 0.08 * sin(time * 0.4 + band.phase)
        val dx = (u - centre) / band.width
        val hump = exp(-(dx * dx))
        val wave = 0.5 + 0.5 * sin((u * band.cycles - time * band.speed) * 2.0 * PI + band.phase)
        highest = max(highest, band.height * hump * wave)
    }
    return highest * smoothstep(u, 0.0, 0.15) * smoothstep(1.0 - u, 0.0, 0.15)
}

private fun ringSlot(index: Int, count: Int): Int? {
    val slot = ((index + 0.5) / count * RING_COUNT).toInt()
    val first = ((slot + 0.5) / RING_COUNT * count).toInt()
    return if (first == index) slot else null
}

/**
 * Animated voice feedback modeled on Verbatim's signature waveform. Reads [levelProvider] inside
 * its frame/draw pass so 20 Hz microphone updates never recompose parent screens, and honors the
 * system's reduced-motion / animator-duration-scale setting.
 */
@Composable
fun VoiceWaveform(
    activity: WaveformActivity?,
    levelProvider: () -> Float,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val verbatimColors = LocalVerbatimColors.current
    val neutralColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f)
    val accentColor = MaterialTheme.colorScheme.primary
    val surfaceColor = verbatimColors.readingSurface

    val reduceMotion =
        remember(context) {
            runCatching {
                    Settings.Global.getFloat(
                        context.contentResolver,
                        Settings.Global.ANIMATOR_DURATION_SCALE,
                        1f,
                    ) == 0f
                }
                .getOrDefault(false)
        }

    var lastShape by remember { mutableStateOf(WaveformActivity.Preparing) }
    var spinStartSec by remember { mutableFloatStateOf(0f) }
    var burstStartSec by remember { mutableStateOf<Float?>(null) }
    var nowSec by remember { mutableFloatStateOf(0f) }
    var smoothedRise by remember { mutableFloatStateOf(0f) }
    var frameClockAnchor by remember { mutableLongStateOf(0L) }

    LaunchedEffect(activity) {
        if (activity != null) {
            if (activity == WaveformActivity.Transcribing && lastShape != WaveformActivity.Transcribing) {
                spinStartSec = nowSec
            }
            burstStartSec = if (activity == WaveformActivity.Succeeded) nowSec else null
            lastShape = activity
        }
    }

    val presenceState =
        animateFloatAsState(
            targetValue = if (activity == null) 0f else 1f,
            animationSpec =
                if (reduceMotion) tween(120)
                else spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow),
            label = "waveformPresence",
        )
    val listeningState =
        animateFloatAsState(
            targetValue = if (lastShape == WaveformActivity.Listening) 1f else 0f,
            animationSpec = tween(durationMillis = if (reduceMotion) 120 else 320),
            label = "waveformListening",
        )
    val gatherState =
        animateFloatAsState(
            targetValue =
                if (
                    lastShape == WaveformActivity.Transcribing ||
                        lastShape == WaveformActivity.Succeeded
                ) {
                    1f
                } else {
                    0f
                },
            animationSpec =
                if (reduceMotion) tween(160)
                else spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessLow),
            label = "waveformGather",
        )
    val successState =
        animateFloatAsState(
            targetValue = if (lastShape == WaveformActivity.Succeeded) 1f else 0f,
            animationSpec =
                if (reduceMotion) tween(160)
                else spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow),
            label = "waveformSuccess",
        )

    val needsClock by
        remember(activity, reduceMotion) {
            derivedStateOf {
                val present = presenceState.value > 0.001f
                if (!present) return@derivedStateOf false
                if (activity == WaveformActivity.Listening) return@derivedStateOf true
                if (reduceMotion) return@derivedStateOf false
                activity != null || gatherState.value > 0.001f || listeningState.value < 0.999f
            }
        }

    LaunchedEffect(needsClock, activity) {
        if (!needsClock) {
            smoothedRise = 0f
            return@LaunchedEffect
        }
        while (true) {
            withFrameNanos { frameNanos ->
                if (frameClockAnchor == 0L) frameClockAnchor = frameNanos
                nowSec = (frameNanos - frameClockAnchor) / 1_000_000_000f
                val target =
                    if (activity == WaveformActivity.Listening) {
                        ((levelProvider() - 0.06f) * 1.45f).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                val factor = if (target > smoothedRise) 0.35f else 0.14f
                smoothedRise += (target - smoothedRise) * factor
            }
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val clampedPresence = presenceState.value.coerceIn(0f, 1f).toDouble()
            if (clampedPresence <= 0.001) return@Canvas

            val listening = listeningState.value
            val gather = gatherState.value
            val success = successState.value
            val rise = smoothedRise
            val time = if (reduceMotion) 0.0 else nowSec.toDouble()

            val density = this.density.toDouble()
            val barWidth = 3.2 * density
            val pitch = 7.5 * density
            val ringRadius = 46.0 * density
            val ringDot = 8.0 * density
            val circleRadius = 28.0 * density
            val edgeInset = 48.0 * density

            val smoothGather = smoothstep(gather.toDouble(), 0.0, 1.0)
            val cx = size.width / 2.0
            val cy = size.height / 2.0

            val gatheringColor =
                lerp(
                    lerp(neutralColor, verbatimColors.recite, listening.coerceIn(0f, 1f)),
                    accentColor,
                    smoothGather.toFloat(),
                )
            val dotColor = lerp(gatheringColor, verbatimColors.verbatim, success.coerceIn(0f, 1f))
            val circleColor = lerp(accentColor, verbatimColors.verbatim, success.coerceIn(0f, 1f))

            val breathing =
                if (reduceMotion) 0.0
                else (1.0 - listening) * (1.0 - smoothGather) * (0.5 + 0.5 * sin(time * 2.4))
            val lineOpacity = 1.0 - 0.5 * breathing

            val spinEnd = burstStartSec?.let { min(nowSec, it) } ?: nowSec
            val spin = if (reduceMotion) 0.0 else ((spinEnd - spinStartSec).toDouble() / 6.0) * 2.0 * PI
            val burst =
                if (reduceMotion) success.toDouble().coerceIn(0.0, 1.0)
                else burstStartSec?.let { ((nowSec - it).toDouble() / 0.9).coerceIn(0.0, 1.0) } ?: 0.0
            val throwDistance =
                if (reduceMotion) 0.0 else 64.0 * density * (1.0 - (1.0 - burst).pow(3.0))
            val burstFade = 1.0 - burst.pow(2.0)

            val rawCount = ((size.width - edgeInset) / pitch).toInt()
            val count = max(RING_COUNT, rawCount or 1)
            val span = (count - 1) * pitch
            val maxBar = min(size.height.toDouble(), 196.0 * density) * 0.8

            val discRadius =
                (circleRadius * smoothGather * clampedPresence * (1.0 + 0.1 * success)).toFloat()
            if (discRadius > 0.5f) {
                drawCircle(
                    color = circleColor,
                    radius = discRadius,
                    center = Offset(cx.toFloat(), cy.toFloat()),
                )
            }

            for (index in 0 until count) {
                val u = index.toDouble() / (count - 1).coerceAtLeast(1)
                val lineX = cx - span / 2.0 + index * pitch
                val lineY = cy
                val barHeight =
                    barWidth +
                        max(0.0, maxBar - barWidth) *
                            max(0.0, rise.toDouble()) *
                            waveAmplitude(u, time)

                val px: Double
                val py: Double
                val w: Double
                val h: Double
                val alpha: Double

                val slot = ringSlot(index, count)
                if (slot != null) {
                    val angle = ringAngles[slot] + spin
                    val r = ringRadius + throwDistance
                    val ringX = cx + cos(angle) * r
                    val ringY = cy + sin(angle) * r
                    px = lerpDouble(lineX, ringX, smoothGather)
                    py = lerpDouble(lineY, ringY, smoothGather)
                    w = lerpDouble(barWidth, ringDot * (1.0 - 0.4 * burst), smoothGather)
                    h = lerpDouble(barHeight, ringDot * (1.0 - 0.4 * burst), smoothGather)
                    alpha = lerpDouble(lineOpacity, burstFade, smoothGather)
                } else {
                    px = lerpDouble(lineX, cx, smoothGather)
                    py = lerpDouble(lineY, cy, smoothGather)
                    w = barWidth * (1.0 - smoothGather)
                    h = barHeight * (1.0 - smoothGather)
                    alpha = lineOpacity * (1.0 - smoothGather)
                }

                if (w <= 0.1 || alpha <= 0.01) continue
                val placedX = cx + (px - cx) * clampedPresence
                val placedY = cy + (py - cy) * clampedPresence
                val corner = (min(w, h) / 2.0).toFloat()
                drawRoundRect(
                    color = dotColor.copy(alpha = (alpha * clampedPresence).toFloat().coerceIn(0f, 1f)),
                    topLeft = Offset((placedX - w / 2.0).toFloat(), (placedY - h / 2.0).toFloat()),
                    size = Size(w.toFloat(), h.toFloat()),
                    cornerRadius = CornerRadius(corner, corner),
                )
            }
        }

        AnimatedVisibility(
            visible = activity == WaveformActivity.Succeeded,
            enter =
                if (reduceMotion) fadeIn(tween(150))
                else {
                    scaleIn(
                        initialScale = 0.35f,
                        animationSpec =
                            spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow),
                    ) + fadeIn(tween(220))
                },
            exit = fadeOut(tween(150)),
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = surfaceColor,
                modifier = Modifier.size(30.dp),
            )
        }
    }
}
