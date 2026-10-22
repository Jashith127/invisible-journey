package com.invisiblejourney.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.invisiblejourney.container
import com.invisiblejourney.domain.model.JourneyRecord
import com.invisiblejourney.domain.model.MeterState
import com.invisiblejourney.domain.model.StopType
import com.invisiblejourney.ui.Dimens
import com.invisiblejourney.ui.Format
import com.invisiblejourney.ui.Num
import com.invisiblejourney.ui.Palette
import com.invisiblejourney.ui.components.BigButton
import com.invisiblejourney.ui.components.Card
import com.invisiblejourney.ui.components.ConditionStrip
import com.invisiblejourney.ui.components.FitText
import com.invisiblejourney.ui.components.JourneyMap
import com.invisiblejourney.ui.components.MeterDial
import com.invisiblejourney.ui.components.PhaseStepper
import com.invisiblejourney.ui.components.SectionTitle
import com.invisiblejourney.ui.components.StatGrid
import com.invisiblejourney.ui.components.Tag
import com.invisiblejourney.ui.components.TimeBreakdownBar
import com.invisiblejourney.ui.components.TopBar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

@Composable
private fun Loading(text: String = "Loading…") {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator(color = Palette.AutoYellow)
        Spacer(Modifier.height(16.dp))
        Text(text, color = Palette.Muted)
    }
}

// --- Library ----------------------------------------------------------------------------------

@Composable
fun LibraryScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val store = context.container.store
    val items by store.items.collectAsState()
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching { store.importFrom(uri) }
                .onSuccess { onOpen(it) }
                .onFailure { Toast.makeText(context, "Couldn't import: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }

    LaunchedEffect(Unit) {
        store.refresh()
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("See a journey", onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.Gutter),
            verticalArrangement = Arrangement.spacedBy(Dimens.Gap),
        ) {
            Text(
                "Open a journey your rider shared with you, or try a sample to see how the meter works.",
                color = Palette.Muted,
            )
            if (loading && items.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(72.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Palette.AutoYellow, modifier = Modifier.size(28.dp))
                }
            }
            val df = remember { SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()) }
            items.forEach { j ->
                JourneyRow(
                    title = j.title,
                    sub = buildString {
                        append(df.format(Date(j.startedAt)))
                        j.actualMin?.let { append(" · ${Format.minutes(it)} of ${j.promisedEtaMin}") }
                    },
                    late = j.actualMin?.let { it > j.promisedEtaMin },
                    tag = if (j.isSample) ({ Tag("SAMPLE") }) else null,
                    onClick = { onOpen(j.id) },
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        Box(Modifier.padding(horizontal = Dimens.Gutter, vertical = 12.dp)) {
            BigButton(
                "Open a shared journey file",
                sub = ".journey.json from your rider",
                primary = false,
                onClick = { importer.launch(arrayOf("application/json", "application/octet-stream", "*/*")) },
            )
        }
    }
}

// --- Replay (the customer's view of a journey) -----------------------------------------------

private val SPEEDS = listOf(1, 10, 30, 60)

/** Latest snapshot at or before [t], with the clock interpolated so the dial moves smoothly. */
private fun meterAt(r: JourneyRecord, t: Long): MeterState? {
    val snaps = r.snapshots
    if (snaps.isEmpty()) return null
    var lo = 0
    var hi = snaps.size - 1
    while (lo < hi) {
        val mid = (lo + hi + 1) / 2
        if (snaps[mid].t <= t) lo = mid else hi = mid - 1
    }
    val s = snaps[lo]
    return if (s.phase.isFinal) s else s.copy(elapsedMs = (t - r.startedAt).coerceAtLeast(s.elapsedMs))
}

@Composable
fun ReplayScreen(journeyId: String, onBack: () -> Unit, onStory: () -> Unit) {
    val context = LocalContext.current
    var record by remember { mutableStateOf<JourneyRecord?>(null) }
    LaunchedEffect(journeyId) { record = context.container.store.load(journeyId) }

    val r = record
    if (r == null) {
        Column(Modifier.fillMaxSize()) { TopBar("Journey", onBack); Loading() }
        return
    }
    val start = r.startedAt
    val end = r.endedAt ?: r.snapshots.lastOrNull()?.t ?: start
    var t by remember(r) { mutableLongStateOf(start) }
    var playing by remember(r) { mutableStateOf(true) }
    var speed by remember { mutableIntStateOf(30) }

    LaunchedEffect(playing, speed, r) {
        while (playing && t < end) {
            delay(100)
            t = min(end, t + 100L * speed)
        }
        if (t >= end) playing = false
    }

    val meter = remember(r, t) { meterAt(r, t) }
    // Rebuilding the map overlays is the expensive part on a mid-range phone, so the map only
    // catches up about twice a second of real time, whatever the playback speed.
    val mapStep = 500L * speed
    val mapT = if (t >= end) end else start + ((t - start) / mapStep) * mapStep
    val track = remember(r, mapT) { r.track.takeWhile { it.t <= mapT } }
    val potholes = remember(r, mapT) { r.potholes.filter { it.t <= mapT } }
    val stops = remember(r, mapT) { r.stops.filter { it.end <= mapT } }
    val marks = remember(r, mapT) { r.marks.filter { it.t <= mapT } }

    Column(Modifier.fillMaxSize()) {
        TopBar(r.title, onBack) { if (r.isSample) Tag("SAMPLE", modifier = Modifier.padding(end = 12.dp)) }
        if (meter == null) {
            Text("This journey has no meter data.", color = Palette.Muted, modifier = Modifier.padding(Dimens.Gutter))
            return@Column
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.Gutter),
            verticalArrangement = Arrangement.spacedBy(Dimens.Gap),
        ) {
            PhaseStepper(meter.phase)
            MeterDial(meter, r.promisedEtaMin, Modifier.fillMaxWidth().height(250.dp))
            ConditionStrip(meter)

            PlaybackBar(
                playing = playing,
                progress = if (end > start) (t - start).toFloat() / (end - start) else 1f,
                position = Format.clock(t - start),
                total = Format.clock(end - start),
                speed = speed,
                onToggle = {
                    if (t >= end) t = start
                    playing = !playing
                },
                onSpeed = { speed = it },
                onSeek = { v ->
                    playing = false
                    t = start + ((end - start) * v).toLong()
                },
            )

            JourneyMap(track, potholes, stops, Modifier.fillMaxWidth().height(280.dp).clip(RoundedCornerShape(Dimens.Radius)), marks = marks)
            MapLegend()

            StatGrid(meter)
            SectionTitle("Where the time went")
            TimeBreakdownBar(meter.movingMs, meter.waitingByType)
            Spacer(Modifier.height(8.dp))
        }
        // Always present so the screen doesn't change shape when playback reaches the end.
        Box(Modifier.padding(horizontal = Dimens.Gutter, vertical = 12.dp)) {
            val done = meter.phase.isFinal && r.summary != null
            BigButton(
                if (done) "Read the journey story" else "Story unlocks at delivery",
                enabled = done,
                onClick = onStory,
            )
        }
    }
}

@Composable
private fun PlaybackBar(
    playing: Boolean,
    progress: Float,
    position: String,
    total: String,
    speed: Int,
    onToggle: () -> Unit,
    onSpeed: (Int) -> Unit,
    onSeek: (Float) -> Unit,
) {
    Card {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    onClick = onToggle,
                    modifier = Modifier.size(Dimens.Touch).semantics { contentDescription = if (playing) "Pause" else "Play" },
                    shape = CircleShape,
                    color = Palette.AutoYellow,
                    contentColor = Palette.Ink,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (playing) {
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                repeat(2) { Box(Modifier.width(6.dp).height(20.dp).background(Palette.Ink)) }
                            }
                        } else {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(32.dp))
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SPEEDS.forEach { s ->
                        val selected = speed == s
                        Surface(
                            onClick = { onSpeed(s) },
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) Palette.AutoYellow.copy(alpha = 0.2f) else Palette.SurfaceHigh,
                            contentColor = if (selected) Palette.AutoYellow else Color.White,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                FitText("$s×", maxSize = 15.sp, minSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            Slider(
                value = progress,
                onValueChange = onSeek,
                colors = SliderDefaults.colors(thumbColor = Palette.AutoYellow, activeTrackColor = Palette.AutoYellow),
            )
            Row {
                Text(position, style = Num.Small, color = Palette.Muted, modifier = Modifier.weight(1f))
                Text(total, style = Num.Small, color = Palette.Muted)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MapLegend() {
    val items = listOf(
        "Fast" to Palette.AutoGreen, "Slow" to Palette.AutoYellow, "Crawling" to Palette.Warn, "Stopped" to Palette.Danger,
        "Pothole" to Palette.Pothole, "Speed breaker" to Palette.Info, "Signal" to Palette.stop(StopType.SIGNAL_WAIT),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { (label, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(5.dp))
                Text(label, fontSize = 13.sp, color = Palette.Muted, maxLines = 1)
            }
        }
    }
}

// --- Story (post-delivery summary) -------------------------------------------------------------

@Composable
fun StoryScreen(journeyId: String, onBack: () -> Unit, onReplay: () -> Unit) {
    val context = LocalContext.current
    val store = context.container.store
    val scope = rememberCoroutineScope()
    var record by remember { mutableStateOf<JourneyRecord?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(journeyId) { record = store.load(journeyId) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            runCatching { store.exportTo(journeyId, uri) }
                .onSuccess { Toast.makeText(context, "Journey saved. Share the file with your customer.", Toast.LENGTH_LONG).show() }
                .onFailure { Toast.makeText(context, "Export failed: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }

    val r = record
    Column(Modifier.fillMaxSize()) {
        TopBar("Journey story", onBack) {
            if (r?.isSample == true) Tag("SAMPLE", modifier = Modifier.padding(end = 12.dp))
        }
        if (r == null) {
            Loading()
            return@Column
        }
        val s = r.summary
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.Gutter),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (s == null) {
                Text("This journey wasn't completed, so there is no story.", color = Palette.Muted)
            } else {
                Text(
                    "Your ${s.promisedEtaMin} minutes were…",
                    fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold,
                )
                EtaComparison(s.promisedEtaMin.toFloat(), s.actualMin)

                s.storyLines.drop(1).forEach { line ->
                    Card { Text(line, fontSize = 17.sp, lineHeight = 24.sp, modifier = Modifier.padding(16.dp)) }
                }

                r.snapshots.lastOrNull()?.let { last ->
                    SectionTitle("Where the time went")
                    TimeBreakdownBar(last.movingMs, last.waitingByType)
                }

                Text(
                    "Next time the countdown runs over, this is what was happening on the road.",
                    color = Palette.Muted, fontSize = 14.sp,
                )
            }
            if (!r.isSample) {
                TextButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.align(Alignment.CenterHorizontally).height(48.dp),
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, tint = Palette.Danger, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Delete this journey", color = Palette.Danger)
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        // Main actions side by side at the bottom, same place on every story.
        Row(
            Modifier.padding(horizontal = Dimens.Gutter, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(Dimens.Gap),
        ) {
            BigButton("Replay", icon = Icons.Filled.Refresh, primary = r.isSample, onClick = onReplay, modifier = Modifier.weight(1f))
            if (!r.isSample) {
                BigButton(
                    "Share", sub = "with the customer", icon = Icons.Filled.Share,
                    onClick = { exporter.launch("${r.id.take(8)}.journey.json") }, modifier = Modifier.weight(1f),
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete journey?") },
            text = { Text("This removes the recording from this phone. Files you already shared aren't affected.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        store.delete(journeyId)
                        onBack()
                    }
                }, modifier = Modifier.height(48.dp)) { Text("Delete", color = Palette.Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }, modifier = Modifier.height(48.dp)) { Text("Keep") } },
        )
    }
}

@Composable
private fun EtaComparison(promised: Float, actual: Float) {
    val max = maxOf(promised, actual).coerceAtLeast(1f)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            Triple("ETA shown", promised, Palette.AutoGreen),
            Triple("Actual", actual, if (actual > promised) Palette.Danger else Palette.AutoYellow),
        ).forEach { (label, v, color) ->
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(label, color = Palette.Muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(Format.minutes(v), style = Num.Small, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth().height(26.dp).clip(RoundedCornerShape(6.dp)).background(Palette.SurfaceHigh)) {
                    Box(Modifier.fillMaxWidth(v / max).fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(color))
                }
            }
        }
    }
}
