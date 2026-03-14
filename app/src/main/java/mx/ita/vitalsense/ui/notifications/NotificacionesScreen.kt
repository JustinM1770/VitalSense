package mx.ita.vitalsense.ui.notifications

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
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
import mx.ita.vitalsense.ui.components.BottomNav
import mx.ita.vitalsense.ui.components.BottomNavTab
import mx.ita.vitalsense.ui.theme.DashBlue
import mx.ita.vitalsense.ui.theme.Manrope

data class NotifItem(
    val icon: ImageVector,
    val title: String,
    val body: String,
    val time: String,
    val isHighlighted: Boolean = false,
)

@Composable
fun NotificacionesScreen(
    onBack: () -> Unit = {},
    onHomeClick: () -> Unit = {},
    onHealthClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
) {
    val todayItems = listOf(
        NotifItem(Icons.Outlined.CalendarMonth, "Recordatorio", "se le notificó a jonathan que tomara su pastilla:  ibuprofeno", "2M"),
        NotifItem(Icons.Outlined.FavoriteBorder, "Datos De Sueño", "se cargó el estado de sueño", "2H", isHighlighted = true),
        NotifItem(Icons.Outlined.Chat, "Metricas De Salud", "se han cargado las metricas medicas diarias", "3H"),
    )
    val yesterdayItems = listOf(
        NotifItem(Icons.Outlined.FavoriteBorder, "Datos De Sueño", "se le notificó a jonathan que tomara su pastilla:  ibuprofeno", "1D"),
    )
    val olderItems = listOf(
        NotifItem(Icons.Outlined.Chat, "General", "se dio de alta en la aplicación", "5D"),
    )

    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 90.dp),
        ) {
            Spacer(Modifier.height(52.dp))

            // ── Header ─────────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(DashBlue.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Regresar",
                        tint = DashBlue,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "Notificaciones",
                    fontFamily = Manrope,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = DashBlue,
                )
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(DashBlue.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "News",
                            fontFamily = Manrope,
                            fontSize = 12.sp,
                            color = DashBlue,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(DashBlue),
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Today ──────────────────────────────────────────────────────────
            NotifSection(label = "Hoy", items = todayItems, showMarkAll = true)
            Spacer(Modifier.height(8.dp))

            // ── Yesterday ─────────────────────────────────────────────────────
            NotifSection(label = "Ayer", items = yesterdayItems)
            Spacer(Modifier.height(8.dp))

            // ── Older ─────────────────────────────────────────────────────────
            NotifSection(label = "15 Abril", items = olderItems)
        }

        BottomNav(
            selected = BottomNavTab.CHAT,
            onSelect = { tab ->
                when (tab) {
                    BottomNavTab.HOME    -> onHomeClick()
                    BottomNavTab.HEALTH  -> onHealthClick()
                    BottomNavTab.PROFILE -> onProfileClick()
                    else -> {}
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun NotifSection(
    label: String,
    items: List<NotifItem>,
    showMarkAll: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(DashBlue.copy(alpha = 0.12f))
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Text(
                text = label,
                fontFamily = Manrope,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = DashBlue,
            )
        }
        if (showMarkAll) {
            Spacer(Modifier.weight(1f))
            Text(
                text = "Mark all",
                fontFamily = Manrope,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = DashBlue,
            )
        }
    }

    items.forEach { item ->
        NotifRow(item = item)
    }
}

@Composable
private fun NotifRow(item: NotifItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (item.isHighlighted) DashBlue.copy(alpha = 0.07f)
                else Color.Transparent,
            )
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(DashBlue),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                item.icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                fontFamily = Manrope,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = Color(0xFF1A1A2E),
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = item.body,
                fontFamily = Manrope,
                fontSize = 13.sp,
                color = Color(0xFF8A8A8A),
            )
        }
        Text(
            text = item.time,
            fontFamily = Manrope,
            fontSize = 12.sp,
            color = Color(0xFFB0B0B0),
        )
    }
}
