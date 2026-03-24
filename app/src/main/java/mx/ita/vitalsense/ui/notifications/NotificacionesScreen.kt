package mx.ita.vitalsense.ui.notifications

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import mx.ita.vitalsense.ui.components.BottomNav
import mx.ita.vitalsense.ui.components.BottomNavTab
import mx.ita.vitalsense.ui.theme.DashBlue
import mx.ita.vitalsense.ui.theme.Manrope
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ── Data model ───────────────────────────────────────────────────────────────
data class NotifItem(
    val firebaseKey: String = "",
    val icon: ImageVector,
    val title: String,
    val body: String,
    val time: String,
    val timestamp: Long = 0L,
    val isRead: Boolean = false,
    val isHighlighted: Boolean = false,
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val onClick: () -> Unit = {}
)

// ── Helpers ──────────────────────────────────────────────────────────────────
private fun formatRelativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val minutes = diff / 60_000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        days > 0 -> "${days}D"
        hours > 0 -> "${hours}H"
        minutes > 0 -> "${minutes}M"
        else -> "Ahora"
    }
}

private fun dateLabel(timestamp: Long): String {
    val cal = Calendar.getInstance()
    val todayStart = cal.apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val yesterdayStart = todayStart - 86_400_000L

    return when {
        timestamp >= todayStart -> "Hoy"
        timestamp >= yesterdayStart -> "Ayer"
        else -> SimpleDateFormat("dd MMMM", Locale.forLanguageTag("es")).format(Date(timestamp))
    }
}

// ── Main Composable ──────────────────────────────────────────────────────────
@Composable
fun NotificacionesScreen(
    onBack: () -> Unit = {},
    onHomeClick: () -> Unit = {},
    onHealthClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val database = remember { FirebaseDatabase.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }
    val userId = auth.currentUser?.uid ?: "global"

    var allAlerts by remember { mutableStateOf<List<NotifItem>>(emptyList()) }
    var showUnreadOnly by remember { mutableStateOf(false) }
    var selectedDateFilter by remember { mutableStateOf<String?>(null) }

    // ── Firebase Listener ────────────────────────────────────────────────────
    DisposableEffect(userId) {
        val ref = database.getReference("alerts").child(userId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val items = mutableListOf<NotifItem>()
                for (child in snapshot.children) {
                    @Suppress("UNCHECKED_CAST")
                    val data = child.value as? Map<String, Any> ?: continue
                    val type = data["type"] as? String ?: "SOS"
                    val lat = (data["lat"] as? Number)?.toDouble() ?: 0.0
                    val lng = (data["lng"] as? Number)?.toDouble() ?: 0.0
                    val ts = (data["timestamp"] as? Number)?.toLong()
                        ?: System.currentTimeMillis()
                    val read = data["read"] as? Boolean ?: false

                    val title = if (type == "SOS") "Alerta de Emergencia" else type
                    val body = when {
                        type == "SOS" && lat != 0.0 ->
                            "Se detectó caída o SOS manual. Toca para ver ubicación."
                        type == "SOS" ->
                            "Se detectó SOS sin ubicación disponible."
                        else -> data["message"] as? String ?: ""
                    }

                    items.add(
                        NotifItem(
                            firebaseKey = child.key ?: "",
                            icon = if (type == "SOS") Icons.Outlined.Warning
                            else Icons.Outlined.FavoriteBorder,
                            title = title,
                            body = body,
                            time = formatRelativeTime(ts),
                            timestamp = ts,
                            isRead = read,
                            isHighlighted = !read,
                            lat = lat,
                            lng = lng,
                            onClick = {
                                if (child.key != null) {
                                    ref.child(child.key!!).child("read").setValue(true)
                                }
                                if (lat != 0.0 && lng != 0.0) {
                                    val uri = "geo:$lat,$lng?q=$lat,$lng(SOS)"
                                    val mapIntent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                                    mapIntent.setPackage("com.google.android.apps.maps")
                                    context.startActivity(mapIntent)
                                }
                            }
                        )
                    )
                }
                allAlerts = items.sortedByDescending { it.timestamp }
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(listener)
        onDispose { ref.removeEventListener(listener) }
    }

    // ── Filtering logic ──────────────────────────────────────────────────────
    val filtered = allAlerts.let { list ->
        var result = list
        if (showUnreadOnly) result = result.filter { !it.isRead }
        if (selectedDateFilter != null) result = result.filter { dateLabel(it.timestamp) == selectedDateFilter }
        result
    }

    val unreadCount = allAlerts.count { !it.isRead }
    val grouped = filtered.groupBy { dateLabel(it.timestamp) }

    // ── Mark all as read ─────────────────────────────────────────────────────
    val markAllRead = {
        val ref = database.getReference("alerts").child(userId)
        allAlerts.filter { !it.isRead }.forEach { item ->
            if (item.firebaseKey.isNotEmpty()) {
                ref.child(item.firebaseKey).child("read").setValue(true)
            }
        }
    }

    // ── UI ────────────────────────────────────────────────────────────────────
    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 90.dp),
        ) {
            Spacer(Modifier.height(52.dp))

            // ── Header ───────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Back
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(DashBlue.copy(alpha = 0.12f))
                        .clickable { onBack() },
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

                // ── News toggle ──────────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (showUnreadOnly) DashBlue else DashBlue.copy(alpha = 0.12f)
                        )
                        .clickable { showUnreadOnly = !showUnreadOnly }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Nuevos",
                            fontFamily = Manrope, fontSize = 12.sp,
                            color = if (showUnreadOnly) Color.White else DashBlue,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (unreadCount > 0) {
                            Spacer(Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (showUnreadOnly) Color.White else DashBlue
                                    ),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Hoy filter + Mark All ────────────────────────────────────────
            val allDistinctDates = allAlerts.map { dateLabel(it.timestamp) }.distinct()
            val availableDates = allDistinctDates.sortedWith(
                compareBy {
                    when (it) {
                        "Hoy" -> 0
                        "Ayer" -> 1
                        else -> 2
                    }
                }
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(availableDates) { dateKey ->
                        val isSelected = selectedDateFilter == dateKey
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isSelected) DashBlue else DashBlue.copy(alpha = 0.12f))
                                .clickable {
                                    selectedDateFilter = if (isSelected) null else dateKey
                                }
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = dateKey,
                                fontFamily = Manrope,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = if (isSelected) Color.White else DashBlue,
                            )
                        }
                    }
                }
                Spacer(Modifier.width(16.dp))
                Text(
                    text = "Marcar todo",
                    fontFamily = Manrope,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = DashBlue,
                    modifier = Modifier.clickable { markAllRead() }
                )
            }

            // ── Grouped Sections ─────────────────────────────────────────────
            if (filtered.isEmpty()) {
                Spacer(Modifier.height(60.dp))
                Text(
                    text = "No hay notificaciones",
                    fontFamily = Manrope,
                    fontSize = 15.sp,
                    color = Color(0xFF8A8A8A),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            } else {
                val orderedKeys = grouped.keys.sortedWith(
                    compareBy {
                        when (it) {
                            "Hoy" -> 0
                            "Ayer" -> 1
                            else -> 2
                        }
                    }
                )
                orderedKeys.forEach { dateKey ->
                    val items = grouped[dateKey] ?: emptyList()

                    if (dateKey != "Hoy") {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(DashBlue.copy(alpha = 0.12f))
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    text = dateKey,
                                    fontFamily = Manrope,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = DashBlue,
                                )
                            }
                        }
                    }

                    items.forEach { item ->
                        NotifRow(item = item)
                    }
                }
            }
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

// ── Notification Row ─────────────────────────────────────────────────────────
@Composable
private fun NotifRow(item: NotifItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (item.isHighlighted) DashBlue.copy(alpha = 0.07f)
                else Color.Transparent
            )
            .padding(horizontal = 20.dp, vertical = 20.dp)
            .clickable { /* Handle click */ },
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (item.isRead) DashBlue.copy(alpha = 0.5f) else DashBlue),
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
                fontWeight = if (item.isRead) FontWeight.Normal else FontWeight.Bold,
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
