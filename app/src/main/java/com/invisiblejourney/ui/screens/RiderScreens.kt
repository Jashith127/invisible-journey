package com.invisiblejourney.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import com.invisiblejourney.domain.model.Phase
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.invisiblejourney.container
import com.invisiblejourney.domain.SampleJourneys
import com.invisiblejourney.domain.model.MountType
import com.invisiblejourney.service.ActiveJourney
import com.invisiblejourney.service.ActiveJourneyState
import com.invisiblejourney.service.JourneyTrackingService
import com.invisiblejourney.ui.Dimens
import com.invisiblejourney.ui.Format
import com.invisiblejourney.ui.Num
import com.invisiblejourney.ui.Palette
import com.invisiblejourney.ui.PhaseText
import com.invisiblejourney.ui.components.BigButton
import com.invisiblejourney.ui.components.Card
import com.invisiblejourney.ui.components.ConditionStrip
import com.invisiblejourney.ui.components.FitText
import com.invisiblejourney.ui.components.MeterDial
import com.invisiblejourney.ui.components.PhaseStepper
import com.invisiblejourney.ui.components.SectionTitle
import com.invisiblejourney.ui.components.SelectTile
import com.invisiblejourney.ui.components.StatTile
import com.invisiblejourney.ui.components.StatusLight
import com.invisiblejourney.ui.components.SwipeToConfirm
import com.invisiblejourney.ui.components.TopBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private fun hasLocation(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

private const val SIM_SPEED = 10
private val ETA_PRESETS = listOf(10, 15, 20, 30)

private fun mountHint(m: MountType): String = when (m) {
    MountType.POCKET -> "jeans / jacket"
    MountType.HANDLEBAR -> "phone holder"
    MountType.BAG -> "delivery bag"
    MountType.CAR -> "test rides"
}

// --- Rider home -------------------------------------------------------------------------------

@Composable
fun RiderHomeScreen(onBack: () -> Unit, onLive: () -> Unit, onOpenStory: (String) -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val active by ActiveJourney.state.collectAsState()
    val journeys by c.store.items.collectAsState()

    var granted by remember { mutableStateOf(hasLocation(context)) }
    var mount by remember { mutableStateOf(c.settings.mountType) }
    var eta by remember { mutableIntStateOf(c.settings.promisedEtaMin) }
    fun setEta(v: Int) { eta = v.coerceIn(5, 60); c.settings.promisedEtaMin = eta }

    val permissions = remember {
        buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = hasLocation(context)
    }

    LaunchedEffect(Unit) { c.store.refresh() }
    val running = active?.let { !it.finished } == true

    Column(Modifier.fillMaxSize()) {
        TopBar("Rider", onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.Gutter),
            verticalArrangement = Arrangement.spacedBy(Dimens.Gap),
        ) {
            active?.takeIf { running }?.let { RunningBanner(it, onLive) }

            SectionTitle("Where is your phone?")
            MountType.entries.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Gap)) {
                    row.forEach { m ->
                        SelectTile(
                            m.label, selected = mount == m, sub = mountHint(m),
                            onClick = { mount = m; c.settings.mountType = m },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            SectionTitle("ETA the app showed the customer")
            Card {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(Dimens.Gap)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StepButton("−", "Less time", enabled = eta > 5) { setEta(eta - 1) }
                        Text(
                            "$eta min", style = Num.Big.copy(fontSize = 32.sp), textAlign = TextAlign.Center,
                            maxLines = 1, modifier = Modifier.weight(1f),
                        )
                        StepButton("+", "More time", enabled = eta < 60) { setEta(eta + 1) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ETA_PRESETS.forEach { p ->
                            Surface(
                                onClick = { setEta(p) },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = if (eta == p) Palette.AutoYellow else Palette.SurfaceHigh,
                                contentColor = if (eta == p) Palette.Ink else Color.White,
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    FitText("$p min", maxSize = 16.sp, minSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            SectionTitle("Practice")
            BigButton(
                "Try a demo ride",
                sub = "Scripted route at ${SIM_SPEED}× speed · real weather",
                primary = false,
                enabled = !running,
                onClick = {
                    ActiveJourney.clearFinished()
                    JourneyTrackingService.start(
                        context, MountType.HANDLEBAR, eta,
                        simulation = SampleJourneys.Scenario.HOT_AFTERNOON, simSpeed = SIM_SPEED,
                    )
                    onLive()
                },
            )

            Card {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = Palette.AutoGreen, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Records only while a journey runs; you stop it any time. No name, no phone number, no audio.",
                        color = Palette.Muted, fontSize = 14.sp, lineHeight = 19.sp,
                    )
                }
            }

            val mine = journeys.filter { !it.isSample }
            if (mine.isNotEmpty()) {
                SectionTitle("Your journeys")
                val df = remember { SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()) }
                mine.forEach { j ->
                    JourneyRow(
                        title = df.format(Date(j.startedAt)),
                        sub = j.actualMin?.let { "${Format.minutes(it)} · ETA ${j.promisedEtaMin} min" } ?: "Not completed",
                        late = j.actualMin?.let { it > j.promisedEtaMin },
                        onClick = { onOpenStory(j.id) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // Primary action lives at the bottom, under the thumb, and is always the same size.
        Box(Modifier.padding(horizontal = Dimens.Gutter, vertical = 12.dp)) {
            when {
                !granted -> BigButton(
                    "Allow location", sub = "Needed to record the route", icon = Icons.Filled.LocationOn,
                    height = Dimens.HeroButton, onClick = { launcher.launch(permissions) },
                )
                running -> BigButton(
                    "Open journey meter", icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    height = Dimens.HeroButton, container = Palette.AutoGreen, content = Color.White, onClick = onLive,
                )
                else -> BigButton(
                    "Start journey", sub = "ETA $eta min · ${mount.label}", icon = Icons.Filled.PlayArrow,
                    height = Dimens.HeroButton,
                    onClick = {
                        ActiveJourney.clearFinished()
                        JourneyTrackingService.start(context, mount, eta)
                        onLive()
                    },
                )
            }
        }
    }
}

@Composable
private fun RunningBanner(s: ActiveJourneyState, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(72.dp).padding(top = 4.dp),
        shape = RoundedCornerShape(Dimens.Radius),
        color = Palette.AutoGreen.copy(alpha = 0.18f),
        border = BorderStroke(2.dp, Palette.AutoGreen),
    ) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                FitText(if (s.simulated) "Demo ride running" else "Journey running", maxSize = 17.sp, fontWeight = FontWeight.Bold)
                FitText(s.meter.phase.label, maxSize = 14.sp, color = Palette.Muted)
            }
            Text(Format.clock(s.meter.elapsedMs), style = Num.Big, maxLines = 1)
        }
    }
}

@Composable
private fun StepButton(symbol: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(Dimens.Touch),
        shape = RoundedCornerShape(14.dp),
        color = Palette.SurfaceHigh,
        contentColor = if (enabled) Color.White else Palette.Faint,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (symbol == "+") Icon(Icons.Filled.Add, contentDescription = description, modifier = Modifier.size(28.dp))
            else Text(symbol, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** One saved journey in a list. Fixed height; long titles ellipsize instead of growing the row. */
@Composable
internal fun JourneyRow(title: String, sub: String, onClick: () -> Unit, late: Boolean? = null, tag: (@Composable () -> Unit)? = null) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(72.dp),
        shape = RoundedCornerShape(14.dp),
        color = Palette.Surface,
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            // late / on time marker, reserved even when unknown
            Box(
                Modifier.width(4.dp).height(40.dp).clip(RoundedCornerShape(2.dp)).background(
                    when (late) {
                        true -> Palette.Danger
                        false -> Palette.AutoGreen
                        null -> Palette.Line
                    },
                ),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(sub, color = Palette.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (tag != null) {
                Spacer(Modifier.width(8.dp))
                tag()
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Palette.Muted, modifier = Modifier.size(28.dp))
        }
    }
}

// --- Live meter -------------------------------------------------------------------------------

/**
 * The screen a rider glances at on the handlebar. Nothing scrolls and every block has a fixed
 * height; only the dial flexes to use whatever space the phone has left.
 */
@Composable
fun LiveJourneyScreen(onBack: () -> Unit, onFinished: (String) -> Unit) {
    val context = LocalContext.current
    val state by ActiveJourney.state.collectAsState()
    var confirmCancel by remember { mutableStateOf(false) }
    val promisedEta = remember { context.container.settings.promisedEtaMin }

    // Keep the screen on while the meter is open, so the bump buttons and volume keys work.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    LaunchedEffect(state?.finished, state?.journeyId) {
        val s = state
        if (s != null && s.finished) onFinished(s.journeyId)
    }

    val s = state
    Column(Modifier.fillMaxSize()) {
        TopBar(if (s?.simulated == true) "Demo ride" else "Live journey", onBack) {
            StatusLight("GPS", if (s == null) null else s.simulated || s.gpsFix)
            StatusLight("Weather", s?.conditionsOk)
            StatusLight("Traffic", if (s == null) null else s.trafficApi)
            IconButton(onClick = { confirmCancel = true }, enabled = s != null && !s.finished, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Cancel journey", tint = Palette.Danger, modifier = Modifier.size(28.dp))
            }
        }

        if (s == null || s.finished) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(color = Palette.AutoYellow)
                Spacer(Modifier.height(16.dp))
                Text(if (s?.finished == true) "Saving journey…" else "Starting sensors…", color = Palette.Muted)
            }
            return@Column
        }

        val m = s.meter
        Column(
            Modifier.weight(1f).padding(horizontal = 12.dp).padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PhaseStepper(m.phase)
            MeterDial(m, promisedEta, Modifier.weight(1f).fillMaxWidth())
            ConditionStrip(m)

            Row(Modifier.height(84.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val cell = Modifier.weight(1f).fillMaxHeight()
                StatTile("Distance", Format.km(m.distanceKm), cell, sub = "${Format.clock(m.movingMs)} moving")
                StatTile(
                    "Speed", "${m.speedKmh.roundToInt()} km/h", cell,
                    sub = "avg ${m.avgMovingKmh.roundToInt()}", accent = Palette.speed(m.speedKmh),
                )
                StatTile("Waiting", Format.clock(m.waitingMs), cell, sub = "${m.stopCount} stops")
            }

            Row(Modifier.height(96.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val cell = Modifier.weight(1f).fillMaxHeight()
                if (s.simulated) {
                    StatTile("Potholes found", m.potholeCount.toString(), cell, sub = "detected by motion")
                    StatTile("Speed breakers", m.speedHumpCount.toString(), cell, sub = "detected by motion")
                } else {
                    BumpButton(
                        "POTHOLE", "Vol − key", "${m.markedPotholes} marked · ${m.potholeCount} auto",
                        Palette.Danger, cell,
                    ) { JourneyTrackingService.markPothole(context) }
                    BumpButton(
                        "BREAKER", "Vol + key", "${m.markedHumps} marked · ${m.speedHumpCount} auto",
                        Palette.Info, cell,
                    ) { JourneyTrackingService.markSpeedHump(context) }
                }
            }

            if (s.simulated) {
                Box(
                    Modifier.fillMaxWidth().height(Dimens.HeroButton),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(Modifier.fillMaxSize(), shape = RoundedCornerShape(Dimens.HeroButton / 2), color = Palette.SurfaceHigh) {
                        Box(contentAlignment = Alignment.Center) {
                            FitText("Demo · steps move on their own", maxSize = 17.sp, color = Palette.Muted)
                        }
                    }
                }
            } else {
                key(m.phase) {
                    SwipeToConfirm(
                        "Swipe · ${PhaseText.action(m.phase)}",
                        onConfirm = { JourneyTrackingService.nextPhase(context) },
                        color = if (m.phase == Phase.ARRIVED) Palette.AutoGreen else Palette.AutoYellow,
                    )
                }
            }
        }
    }

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text("Cancel this journey?") },
            text = { Text("Recording stops. What was recorded so far is kept, but there'll be no delivery story.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmCancel = false
                    JourneyTrackingService.cancel(context)
                }, modifier = Modifier.height(48.dp)) { Text("Cancel journey", color = Palette.Danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmCancel = false }, modifier = Modifier.height(48.dp)) { Text("Keep recording") }
            },
        )
    }
}

/** Big tap target for marking a bump; buzzes so the rider knows it counted without looking. */
@Composable
private fun BumpButton(title: String, hint: String, counts: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Surface(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        modifier = modifier,
        shape = RoundedCornerShape(Dimens.Radius),
        color = color,
        contentColor = Color.White,
    ) {
        Column(
            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            FitText(title, maxSize = 24.sp, minSize = 14.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            FitText(counts, maxSize = 13.sp, minSize = 9.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            FitText(hint, maxSize = 11.sp, minSize = 8.sp, color = Color.White.copy(alpha = 0.75f), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}
