package com.invisiblejourney.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.invisiblejourney.domain.model.MeterState
import com.invisiblejourney.domain.model.Phase
import com.invisiblejourney.domain.model.SourceTag
import com.invisiblejourney.domain.model.StopType
import com.invisiblejourney.domain.processor.IndianAqi
import com.invisiblejourney.ui.Format
import com.invisiblejourney.ui.Num
import com.invisiblejourney.ui.Palette
import com.invisiblejourney.ui.PhaseText
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private const val SWEEP = 240f
private const val START_ANGLE = 150f

/**
 * The signature element: an auto-rickshaw-style meter. The dial runs in minutes; the green band
 * is the promised ETA, and the needle keeps going into red when the real journey runs past it.
 *
 * It fills whatever box it is given (the clock is drawn on the canvas and scales with the dial),
 * so the live screen can hand it "the rest of the height" without anything overflowing.
 */
@Composable
fun MeterDial(meter: MeterState, promisedEtaMin: Int, modifier: Modifier = Modifier) {
    val elapsedMin = meter.elapsedMs / 60_000f
    val scaleMax = max(promisedEtaMin * 2f, (elapsedMin / 5f).toInt() * 5f + 5f)
    val over = elapsedMin > promisedEtaMin
    val clock = Format.clock(meter.elapsedMs)
    val delta = Format.etaDelta(meter.elapsedMs, promisedEtaMin)
    val measurer = rememberTextMeasurer()

    Canvas(modifier.semantics { contentDescription = "Journey time $clock, $delta. ETA shown $promisedEtaMin minutes." }) {
        // The 240° arc plus the clock below its centre is about 2r wide and 1.8r tall.
        val r = min(size.width * 0.45f, size.height / 1.85f)
        if (r <= 0f) return@Canvas
        val stroke = r * 0.13f
        val center = Offset(size.width / 2, (size.height - 1.8f * r) / 2 + r)
        val topLeft = Offset(center.x - r, center.y - r)
        val arcSize = Size(2 * r, 2 * r)
        fun angleFor(min: Float) = START_ANGLE + SWEEP * (min / scaleMax).coerceIn(0f, 1f)

        drawArc(Palette.SurfaceHigh, START_ANGLE, SWEEP, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        drawArc(Palette.AutoGreen.copy(alpha = 0.45f), START_ANGLE, SWEEP * promisedEtaMin / scaleMax, false, topLeft, arcSize, style = Stroke(stroke))
        drawArc(
            if (over) Palette.Danger else Palette.AutoYellow,
            START_ANGLE, angleFor(elapsedMin) - START_ANGLE, false, topLeft, arcSize,
            style = Stroke(stroke * 0.55f, cap = StrokeCap.Round),
        )

        // minute ticks
        for (m in 0..scaleMax.toInt()) {
            val a = Math.toRadians(angleFor(m.toFloat()).toDouble())
            val major = m % 5 == 0
            val eta = m == promisedEtaMin
            val inner = r - stroke * (if (major || eta) 1.7f else 1.2f)
            val outer = r - stroke * 0.75f
            drawLine(
                if (eta) Palette.AutoGreen else Color.White.copy(alpha = if (major) 0.85f else 0.3f),
                Offset(center.x + inner * cos(a).toFloat(), center.y + inner * sin(a).toFloat()),
                Offset(center.x + outer * cos(a).toFloat(), center.y + outer * sin(a).toFloat()),
                strokeWidth = if (eta) stroke * 0.4f else if (major) stroke * 0.25f else stroke * 0.12f,
            )
        }
        // needle
        val na = angleFor(elapsedMin) * PI / 180
        val len = r - stroke * 2.2f
        drawLine(
            Palette.AutoYellow, center,
            Offset(center.x + len * cos(na).toFloat(), center.y + len * sin(na).toFloat()),
            strokeWidth = stroke * 0.4f, cap = StrokeCap.Round,
        )
        drawCircle(Palette.AutoYellow, stroke * 0.55f, center)

        // clock + time left, in the gap under the hub
        val clockLayout = measurer.measure(
            clock,
            Num.Hero.copy(fontSize = (r * 0.34f).toSp(), color = if (over) Palette.Danger else Color.White),
        )
        val clockTop = center.y + r * 0.14f
        drawText(clockLayout, topLeft = Offset(center.x - clockLayout.size.width / 2f, clockTop))
        val deltaLayout = measurer.measure(
            delta,
            Num.Small.copy(fontSize = (r * 0.13f).toSp(), fontWeight = FontWeight.Bold, color = if (over) Palette.Danger else Palette.AutoGreen),
        )
        drawText(deltaLayout, topLeft = Offset(center.x - deltaLayout.size.width / 2f, clockTop + clockLayout.size.height))
    }
}

/** Where the journey is: To store → Pickup → Riding → Drop. Fixed height; nothing reflows. */
@Composable
fun PhaseStepper(phase: Phase, modifier: Modifier = Modifier) {
    val steps = PhaseText.steps
    val current = when (phase) {
        Phase.DELIVERED -> steps.size
        Phase.CANCELLED -> -1
        else -> steps.indexOf(phase)
    }
    val dot = 26.dp
    Row(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .semantics { contentDescription = "Step: ${phase.label}" }
            .drawBehind {
                // connector line through the dot centres
                val step = size.width / steps.size
                val y = dot.toPx() / 2
                for (i in 0 until steps.size - 1) {
                    val done = i < current
                    drawLine(
                        if (done) Palette.AutoGreen else Palette.Line,
                        Offset(step * (i + 0.5f) + dot.toPx() / 2 + 4.dp.toPx(), y),
                        Offset(step * (i + 1.5f) - dot.toPx() / 2 - 4.dp.toPx(), y),
                        strokeWidth = 3.dp.toPx(),
                    )
                }
            },
    ) {
        steps.forEachIndexed { i, p ->
            val done = i < current
            val now = i == current
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(dot)
                        .clip(CircleShape)
                        .background(
                            when {
                                done -> Palette.AutoGreen
                                now -> Palette.AutoYellow
                                else -> Palette.SurfaceHigh
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (done) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    } else {
                        Text("${i + 1}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (now) Palette.Ink else Palette.Muted)
                    }
                }
                Spacer(Modifier.height(4.dp))
                FitText(
                    PhaseText.short(p),
                    maxSize = 13.sp,
                    minSize = 9.sp,
                    fontWeight = if (now) FontWeight.Bold else FontWeight.Normal,
                    color = if (now) Color.White else Palette.Muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                )
            }
        }
    }
}

/** A fixed-size tile: small label, big value, one-line note. Text shrinks rather than wrapping. */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    sub: String? = null,
    accent: Color = Color.White,
    tone: Color? = null,
) {
    Surface(modifier, shape = RoundedCornerShape(14.dp), color = tone?.copy(alpha = 0.2f) ?: Palette.Surface) {
        Box {
            if (tone != null) Box(Modifier.fillMaxWidth().height(3.dp).background(tone))
            Column(Modifier.fillMaxHeight().padding(horizontal = 10.dp, vertical = 7.dp)) {
                FitText(label.uppercase(Locale.US), maxSize = 11.sp, minSize = 8.sp, color = Palette.Muted, fontWeight = FontWeight.Bold)
                // The value takes whatever height is left and shrinks to it, so the note below always shows.
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                    FitText(value, maxSize = 26.sp, minSize = 11.sp, color = accent, style = Num.Big)
                }
                FitText(sub ?: "", maxSize = 12.sp, minSize = 8.sp, color = Palette.Muted)
            }
        }
    }
}

/** Two-column grid of fixed-height tiles (replay screen). */
@Composable
fun StatGrid(meter: MeterState, modifier: Modifier = Modifier, tileHeight: Dp = 88.dp) {
    val rows = listOf(
        listOf(
            Triple("Distance", Format.km(meter.distanceKm), null),
            Triple("Speed", "${meter.speedKmh.roundToInt()} km/h", "avg ${meter.avgMovingKmh.roundToInt()} moving"),
        ),
        listOf(
            Triple("Moving", Format.clock(meter.movingMs), null),
            Triple("Waiting", Format.clock(meter.waitingMs), "${meter.stopCount} stops on the road"),
        ),
        listOf(
            Triple("Potholes", meter.potholeCount.toString(), "${meter.speedHumpCount} speed breakers"),
            Triple("Traffic", congestionValue(meter), congestionNote(meter)),
        ),
    )
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (label, value, sub) ->
                    StatTile(label, value, Modifier.weight(1f).height(tileHeight), sub)
                }
            }
        }
    }
}

private fun congestionValue(m: MeterState) = m.congestionIndex?.let { String.format(Locale.US, "%.1f×", it) } ?: "—"

private fun congestionNote(m: MeterState) = when (m.congestionSource) {
    null -> "no data yet"
    SourceTag.ESTIMATED -> "estimated"
    else -> "vs empty roads"
}

/**
 * "Exposure" strip: what the rider is out in right now. Five slots that are always there, in the
 * same order; unknown values show "—" instead of appearing later and pushing the layout around.
 */
@Composable
fun ConditionStrip(meter: MeterState, modifier: Modifier = Modifier, height: Dp = 80.dp) {
    val feels = meter.feelsLikeC ?: meter.tempC
    val weatherKnown = feels != null
    val aqi = meter.aqiNow
    val traffic = meter.congestionIndex
    val road = meter.roughnessNow

    Row(modifier.fillMaxWidth().height(height), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val cell = Modifier.weight(1f).fillMaxHeight()
        StatTile(
            "Feels", feels?.let { "${it.roundToInt()}°" } ?: "—", cell,
            sub = feels?.let { if (it >= 35f) "Very hot" else if (it >= 30f) "Hot" else "OK" } ?: "waiting",
            tone = feels?.let { if (it >= 35f) Palette.Danger else if (it >= 30f) Palette.Warn else Palette.AutoGreen },
        )
        StatTile(
            "Rain", if (!weatherKnown) "—" else if (meter.raining) "Yes" else "Dry", cell,
            sub = if (!weatherKnown) "waiting" else if (meter.raining) "wet road" else "",
            tone = if (!weatherKnown) null else if (meter.raining) Palette.Info else Palette.AutoGreen,
        )
        StatTile(
            "AQI", aqi?.toString() ?: "—", cell,
            sub = aqi?.let { IndianAqi.category(it) } ?: "waiting",
            tone = aqi?.let { if (it > 200) Palette.Danger else if (it > 100) Palette.Warn else Palette.AutoGreen },
        )
        StatTile(
            "Traffic", congestionValue(meter), cell,
            sub = when {
                traffic == null -> "waiting"
                meter.congestionSource == SourceTag.ESTIMATED -> "estimated"
                else -> "live"
            },
            tone = traffic?.let { if (it >= 2f) Palette.Danger else if (it >= 1.5f) Palette.Warn else Palette.AutoGreen },
        )
        StatTile(
            "Road", road?.label ?: "—", cell,
            sub = "${meter.potholeCount} holes",
            tone = road?.let { Palette.roughness(it) },
        )
    }
}

/** Stacked bar: where the time went. Every category is always listed so rows never pop in. */
@Composable
fun TimeBreakdownBar(movingMs: Long, waitingByType: Map<StopType, Long>, modifier: Modifier = Modifier) {
    val parts = buildList {
        add(Triple("Riding", movingMs, Palette.AutoGreen))
        StopType.entries.forEach { t -> add(Triple(t.label, waitingByType[t] ?: 0L, Palette.stop(t))) }
    }
    val total = parts.sumOf { it.second }.coerceAtLeast(1)
    Column(modifier) {
        Row(Modifier.fillMaxWidth().height(20.dp).clip(RoundedCornerShape(10.dp)).background(Palette.SurfaceHigh)) {
            parts.forEach { (_, ms, color) ->
                if (ms > 0) Box(Modifier.weight(ms.toFloat() / total).fillMaxHeight().background(color))
            }
        }
        Spacer(Modifier.height(10.dp))
        parts.forEach { (label, ms, color) ->
            val empty = ms <= 0
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(30.dp)) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(if (empty) Palette.SurfaceHigh else color))
                Spacer(Modifier.width(10.dp))
                Text(
                    label, modifier = Modifier.weight(1f), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (empty) Palette.Faint else Color.White,
                )
                Text(
                    if (empty) "—" else Format.duration(ms), style = Num.Small,
                    color = if (empty) Palette.Faint else Palette.Muted, maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(Locale.US),
        modifier = modifier.padding(top = 8.dp, bottom = 2.dp),
        color = Palette.AutoYellow,
        fontSize = 13.sp,
        letterSpacing = 1.5.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
