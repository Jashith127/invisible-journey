package com.invisiblejourney.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.invisiblejourney.ui.Dimens
import com.invisiblejourney.ui.Palette
import com.invisiblejourney.ui.components.FitText
import androidx.compose.foundation.background

@Composable
fun ModeSelectScreen(onRider: () -> Unit, onCustomer: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = Dimens.Gutter)) {
        // Copy scrolls if the font is set very large; the two choices stay pinned to the thumb zone.
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(Modifier.height(24.dp))
            Text("THE INVISIBLE JOURNEY", color = Palette.AutoYellow, fontSize = 14.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text(
                "What does it take to deliver your order in 10 minutes?",
                fontSize = 30.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "A journey meter for instant delivery: the waiting, the traffic, the heat, the air and the potholes behind the countdown.",
                color = Palette.Muted,
                fontSize = 16.sp,
                lineHeight = 22.sp,
            )
            Spacer(Modifier.height(24.dp))
        }
        Column(Modifier.padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            RoleCard(
                icon = Icons.Filled.LocationOn,
                title = "I'm a rider",
                sub = "Record a delivery journey",
                onClick = onRider,
                container = Palette.AutoYellow,
                content = Palette.Ink,
            )
            RoleCard(
                icon = Icons.Filled.Search,
                title = "I'm a customer",
                sub = "See what a journey took",
                onClick = onCustomer,
                container = Palette.Surface,
                content = Color.White,
            )
        }
    }
}

@Composable
private fun RoleCard(icon: ImageVector, title: String, sub: String, onClick: () -> Unit, container: Color, content: Color) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(88.dp),
        shape = RoundedCornerShape(20.dp),
        color = container,
        contentColor = content,
        border = if (container == Palette.Surface) BorderStroke(1.dp, Palette.Line) else null,
    ) {
        Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(content.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp)) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                FitText(title, maxSize = 21.sp, minSize = 13.sp, fontWeight = FontWeight.Bold)
                FitText(sub, maxSize = 14.sp, minSize = 10.sp, color = content.copy(alpha = 0.75f))
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(28.dp))
        }
    }
}
