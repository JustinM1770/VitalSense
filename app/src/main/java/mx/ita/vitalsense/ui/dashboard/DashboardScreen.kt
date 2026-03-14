package mx.ita.vitalsense.ui.dashboard

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import com.google.firebase.auth.FirebaseAuth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import mx.ita.vitalsense.data.model.VitalsData
import mx.ita.vitalsense.ui.components.BottomNav
import mx.ita.vitalsense.ui.components.BottomNavTab
import mx.ita.vitalsense.ui.theme.ChartRed
import mx.ita.vitalsense.ui.theme.DashBg
import mx.ita.vitalsense.ui.theme.DashBlue
import mx.ita.vitalsense.ui.theme.DashCard
import mx.ita.vitalsense.ui.theme.Manrope
import mx.ita.vitalsense.ui.theme.SleepGreen
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun DashboardScreen(
    onConnectDevice: () -> Unit = {},
    onPatientClick: (String) -> Unit = {},
    onProfileClick: () -> Unit = {},
    onReportClick: () -> Unit = {},
    onNotifClick: () -> Unit = {},
    vm: DashboardViewModel = viewModel(),
) {
    val uiState by vm.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(DashBg)) {
        when (val state = uiState) {
            is DashboardUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DashBlue, strokeWidth = 3.dp)
            }
            else -> {
                val patients = if (state is DashboardUiState.Success) state.patients else emptyList()
                DashboardContent(
                    patients = patients,
                    onPatientClick = onPatientClick,
                    onReportClick = onReportClick,
                    onProfileClick = onProfileClick,
                    onNotifClick = onNotifClick,
                )
            }
        }

        BottomNav(
            selected = BottomNavTab.HOME,
            onSelect = { tab ->
                when (tab) {
                    BottomNavTab.HEALTH  -> onReportClick()
                    BottomNavTab.PROFILE -> onProfileClick()
                    BottomNavTab.CHAT    -> onNotifClick()
                    else -> {}
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DashboardContent(
    patients: List<VitalsData>,
    onPatientClick: (String) -> Unit,
    onReportClick: () -> Unit,
    onProfileClick: () -> Unit,
    onNotifClick: () -> Unit = {},
) {
    val userName = FirebaseAuth.getInstance().currentUser?.displayName
        ?: patients.firstOrNull()?.patientName
        ?: "Usuario"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 90.dp),
    ) {
        Spacer(Modifier.height(48.dp))

        // ── Header: avatar + saludo + campana ────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Avatar circular con inicial
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(DashBlue)
                    .clickable { onProfileClick() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = userName.firstOrNull()?.uppercaseChar()?.toString() ?: "U",
                    fontFamily = Manrope,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color.White,
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Bienvenido",
                    fontFamily = Manrope,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = Color(0xFF5A7A9A),
                )
                Text(
                    text = userName,
                    fontFamily = Manrope,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF0D1B2A),
                )
            }

            // Campana de notificaciones
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .clickable { onNotifClick() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Notifications,
                    contentDescription = "Notificaciones",
                    tint = Color(0xFF0D1B2A),
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Search bar ───────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    tint = Color(0xFFB0B8C4),
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Buscar",
                    fontFamily = Manrope,
                    fontSize = 14.sp,
                    color = Color(0xFFB0B8C4),
                )
            }

            Spacer(Modifier.width(10.dp))

            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(DashBlue),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Tune,
                    contentDescription = "Filtros",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── "Esta semana" — blue rounded section ──────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(DashBg)
                .padding(18.dp),
        ) {
            Column {
                // Header row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Esta semana",
                        fontFamily = Manrope,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = Color(0xFF1A1A2E),
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowForward,
                        contentDescription = null,
                        tint = Color(0xFF1A1A2E),
                        modifier = Modifier.size(18.dp),
                    )
                }

                Spacer(Modifier.height(14.dp))

                // Sleep / HR / Kcal pager
                val pagerState = rememberPagerState(pageCount = { 3 })
                val sleepPct = if (patients.isNotEmpty()) {
                    ((patients.first().spo2 - 85).coerceIn(0, 15) * 100 / 15 + 60).coerceIn(0, 100)
                } else 70

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth(),
                ) { page ->
                    when (page) {
                        0 -> SleepCard(percent = sleepPct)
                        1 -> HrMiniCard(patients)
                        else -> KcalMiniCard(patients)
                    }
                }

                Spacer(Modifier.height(12.dp))
                PagerDots(count = 3, selected = pagerState.currentPage)
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Métricas de Salud — white card ────────────────────────────────────
        WhiteCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Metricas de Salud",
                        fontFamily = Manrope,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color(0xFF1A1A2E),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Ver todo",
                            fontFamily = Manrope,
                            fontSize = 13.sp,
                            color = Color(0xFF1A1A2E),
                        )
                        Spacer(Modifier.width(8.dp))
                        ArrowCircle(onClick = onReportClick)
                    }
                }
                Spacer(Modifier.height(16.dp))
                WeeklyHrChart(patients = patients)
            }
        }

        Spacer(Modifier.height(12.dp))

        // Pager dots for metrics
        val metricsPager = rememberPagerState(pageCount = { 3 })
        PagerDots(count = 3, selected = metricsPager.currentPage)

        Spacer(Modifier.height(20.dp))

        // ── Medicamentos — white card ──────────────────────────────────────────
        WhiteCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Medicamentos",
                            fontFamily = Manrope,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color(0xFF1A1A2E),
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowForward,
                            contentDescription = null,
                            tint = Color(0xFF1A1A2E),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "Ver todo", fontFamily = Manrope, fontSize = 13.sp, color = Color(0xFF1A1A2E))
                        Spacer(Modifier.width(8.dp))
                        ArrowCircle(onClick = onReportClick)
                    }
                }

                Spacer(Modifier.height(16.dp))
                DateStrip()
                Spacer(Modifier.height(20.dp))

                val kcal = if (patients.isNotEmpty()) 800 + patients.sumOf { it.heartRate } * 2 else 1038
                Text(
                    text = "%,d Kcal".format(kcal),
                    fontFamily = Manrope,
                    fontWeight = FontWeight.Bold,
                    fontSize = 36.sp,
                    color = Color(0xFF1A1A2E),
                )
                Text(
                    text = "Total del dia",
                    fontFamily = Manrope,
                    fontSize = 13.sp,
                    color = Color(0xFF8A8A8A),
                )
            }
        }

        // ── Patients ──────────────────────────────────────────────────────────
        if (patients.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            patients.forEach { patient ->
                WhiteCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clickable { onPatientClick(patient.patientId) },
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier.size(44.dp).clip(CircleShape).background(DashBlue.copy(0.15f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = patient.patientName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                                fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = DashBlue,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(patient.patientName, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color(0xFF1A1A2E))
                            Text("❤️ ${patient.heartRate} BPM · SpO₂ ${patient.spo2}%", fontFamily = Manrope, fontSize = 12.sp, color = Color(0xFF8A8A8A))
                        }
                        Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = Color(0xFFB0B0B0), modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

// ─── Sleep card ───────────────────────────────────────────────────────────────

@Composable
private fun SleepCard(percent: Int) {
    WhiteCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SleepRing(percent = percent)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Sueño", fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = SleepGreen)
                val today = LocalDate.now()
                Text(
                    text = "${today.dayOfMonth} ${today.month.getDisplayName(TextStyle.FULL, Locale("es")).replaceFirstChar { it.uppercase() }} ${today.year}",
                    fontFamily = Manrope, fontSize = 12.sp, color = Color(0xFF8A8A8A),
                )
            }
            Text(text = "+10%", fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = SleepGreen)
        }
    }
}

@Composable
private fun SleepRing(percent: Int) {
    Box(modifier = Modifier.size(72.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = Stroke(8.dp.toPx(), cap = StrokeCap.Round)
            drawArc(Color(0xFFE0E0E0), -90f, 360f, false, style = stroke)
            drawArc(SleepGreen, -90f, 360f * percent / 100f, false, style = stroke)
        }
        Text(text = "$percent%", fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1A1A2E))
    }
}

@Composable
private fun HrMiniCard(patients: List<VitalsData>) {
    val hr = patients.firstOrNull()?.heartRate ?: 80
    WhiteCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("❤️", fontSize = 28.sp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Ritmo cardiaco", fontFamily = Manrope, fontSize = 13.sp, color = Color(0xFF8A8A8A))
                Text("$hr BPM", fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Color(0xFF1A1A2E))
            }
        }
    }
}

@Composable
private fun KcalMiniCard(patients: List<VitalsData>) {
    val kcal = if (patients.isNotEmpty()) 800 + patients.sumOf { it.heartRate } * 2 else 1038
    WhiteCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("%,d".format(kcal), fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 36.sp, color = Color(0xFF1A1A2E))
            Text("Kcal hoy", fontFamily = Manrope, fontSize = 14.sp, color = Color(0xFF8A8A8A))
        }
    }
}

// ─── Weekly HR Chart ──────────────────────────────────────────────────────────

@Composable
private fun WeeklyHrChart(patients: List<VitalsData>) {
    val baseHr = patients.firstOrNull()?.heartRate ?: 80
    val days = listOf("Sun", "Mon", "Tue", "Wed", "Thru", "Fri", "Sat")
    val values = listOf(
        baseHr - 2, baseHr + 15, baseHr + 18, baseHr + 8,
        baseHr - 3, baseHr + 10, baseHr + 5,
    ).map { it.coerceIn(50, 180) }
    val yLabels = listOf(100, 110, 120, 130, 140)

    Row(modifier = Modifier.fillMaxWidth().height(120.dp)) {
        Column(
            modifier = Modifier.padding(end = 4.dp).height(100.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            yLabels.reversed().forEach { Text("$it", fontFamily = Manrope, fontSize = 9.sp, color = Color(0xFFB0B0B0)) }
        }
        Canvas(modifier = Modifier.weight(1f).height(100.dp)) {
            val w = size.width; val h = size.height
            val min = 95f; val max = 145f
            fun xOf(i: Int) = i * w / (values.size - 1)
            fun yOf(v: Int) = h - (v - min) / (max - min) * h

            // grid
            yLabels.forEach { y -> drawLine(Color(0xFFEEEEEE), Offset(0f, yOf(y)), Offset(w, yOf(y)), 1.dp.toPx()) }
            // vertical grid lines
            days.indices.forEach { i -> drawLine(Color(0xFFEEEEEE), Offset(xOf(i), 0f), Offset(xOf(i), h), 0.5.dp.toPx()) }

            val path = Path().apply {
                moveTo(xOf(0), yOf(values[0]))
                for (i in 1 until values.size) {
                    val cx = (xOf(i - 1) + xOf(i)) / 2f
                    cubicTo(cx, yOf(values[i - 1]), cx, yOf(values[i]), xOf(i), yOf(values[i]))
                }
            }
            // fill
            drawPath(Path().apply {
                addPath(path); lineTo(xOf(values.size - 1), h); lineTo(0f, h); close()
            }, Brush.verticalGradient(listOf(ChartRed.copy(0.25f), Color.Transparent), 0f, h))
            // line
            drawPath(path, ChartRed, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
            // active dot on Fri (index 5)
            val dotX = xOf(5); val dotY = yOf(values[5])
            drawCircle(ChartRed, 6.dp.toPx(), Offset(dotX, dotY))
            drawCircle(Color.White, 3.dp.toPx(), Offset(dotX, dotY))
            drawLine(ChartRed.copy(0.4f), Offset(dotX, dotY), Offset(dotX, h), 1.dp.toPx())

            // Tooltip bubble (rounded rect)
            val tooltipW = 68.dp.toPx(); val tooltipH = 36.dp.toPx()
            val tx = (dotX - tooltipW / 2).coerceIn(0f, w - tooltipW)
            val ty = dotY - tooltipH - 10.dp.toPx()
            drawRoundRect(Color.White, Offset(tx, ty), Size(tooltipW, tooltipH), CornerRadius(8.dp.toPx()),
                style = Stroke(1.dp.toPx()))
        }
    }

    // Day labels
    Row(modifier = Modifier.fillMaxWidth().padding(start = 24.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf("0").plus(days).forEach { Text(it, fontFamily = Manrope, fontSize = 9.sp, color = Color(0xFFB0B0B0)) }
    }
}

// ─── Date strip ───────────────────────────────────────────────────────────────

@Composable
private fun DateStrip() {
    val today = LocalDate.now()
    val days = (-3..3).map { today.plusDays(it.toLong()) }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        days.forEach { day ->
            val isToday = day == today
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isToday) DashBg.copy(alpha = 0.4f) else Color.Transparent)
                    .padding(horizontal = if (isToday) 8.dp else 4.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (isToday) {
                    Text(
                        text = "Today, ${day.dayOfMonth} ${day.month.getDisplayName(TextStyle.SHORT, Locale("es")).replaceFirstChar { it.uppercase() }}",
                        fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF1A1A2E),
                    )
                } else {
                    Text("${day.dayOfMonth}", fontFamily = Manrope, fontSize = 13.sp, color = Color(0xFFB0B0B0))
                }
            }
        }
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

@Composable
private fun PagerDots(count: Int, selected: Int) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        (0 until count).forEach { i ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(if (i == selected) 16.dp else 8.dp, 8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (i == selected) DashBlue else Color.White.copy(0.5f)),
            )
        }
    }
}

@Composable
private fun ArrowCircle(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(DashBlue)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun WhiteCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier = modifier.clip(RoundedCornerShape(20.dp)).background(DashCard)) { content() }
}

// Keep DashWhiteCard as alias for backward compatibility
@Composable
fun DashWhiteCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) = WhiteCard(modifier, content)
