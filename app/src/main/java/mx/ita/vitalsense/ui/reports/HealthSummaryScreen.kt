package mx.ita.vitalsense.ui.reports

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import mx.ita.vitalsense.R
import mx.ita.vitalsense.data.model.VitalsData
import mx.ita.vitalsense.data.model.SleepData
import mx.ita.vitalsense.ui.dashboard.DashboardUiState
import mx.ita.vitalsense.ui.dashboard.DashboardViewModel
import mx.ita.vitalsense.ui.theme.DashBlue
import mx.ita.vitalsense.ui.theme.Manrope
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class SummaryRange(val hours: Int) {
    LAST_24H(24),
    LAST_7D(24 * 7),
    LAST_30D(24 * 30),
}

@Composable
fun HealthSummaryScreen(
    onBack: () -> Unit,
    vm: DashboardViewModel = viewModel(),
) {
    val state by vm.uiState.collectAsState()
    var selectedRange by remember { mutableStateOf(SummaryRange.LAST_7D) }
    var rangeSleepData by remember { mutableStateOf<SleepData?>(null) }
    var rangeSleepRecords by remember { mutableStateOf(0) }

    val allVitals = if (state is DashboardUiState.Success) {
        (state as DashboardUiState.Success).vitalsHistory
    } else {
        emptyList()
    }
    val patients = if (state is DashboardUiState.Success) {
        (state as DashboardUiState.Success).patients
    } else {
        emptyList()
    }
    val sleepData = if (state is DashboardUiState.Success) {
        (state as DashboardUiState.Success).sleepData
    } else {
        null
    }
    val medicationsCount = if (state is DashboardUiState.Success) {
        (state as DashboardUiState.Success).medications.size
    } else {
        0
    }

    LaunchedEffect(selectedRange, sleepData) {
        rangeSleepData = sleepData
        rangeSleepRecords = if (sleepData?.hasSleep == true) 1 else 0

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@LaunchedEffect
        try {
            val (startDate, endDate) = resolveRangeDates(selectedRange)
            val startKey = startDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val endKey = endDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

            val snapshot = withContext(Dispatchers.IO) {
                FirebaseDatabase.getInstance()
                    .getReference("sleep/$uid")
                    .orderByKey()
                    .startAt(startKey)
                    .endAt(endKey)
                    .get()
                    .await()
            }
            val sleepMap = snapshot.children.associate { child ->
                child.key.orEmpty() to child.getValue(SleepData::class.java)
            }
            val records = sleepMap.values.count { it?.hasSleep == true }
            val latestSleep = sleepMap.entries
                .sortedByDescending { it.key }
                .mapNotNull { it.value?.takeIf { sleep -> sleep.hasSleep } }
                .firstOrNull()

            rangeSleepRecords = records
            rangeSleepData = latestSleep ?: sleepData
        } catch (_: Exception) {
            // Keep the in-memory fallback from the dashboard state.
        }
    }

    val now = System.currentTimeMillis()
    val minTimestamp = now - (selectedRange.hours * 60L * 60L * 1000L)
    val normalizedVitals = allVitals.map { vitals ->
        val normalizedTs = normalizeTimestamp(vitals.timestamp)
        if (normalizedTs == vitals.timestamp) vitals else vitals.copy(timestamp = normalizedTs)
    }
    val filtered = normalizedVitals.filter { it.timestamp >= minTimestamp }
    val patientsAsVitals = patients
        .filter { it.heartRate > 0 || it.spo2 > 0 || it.glucose > 0.0 }
        .map { vitals ->
            val normalizedTs = normalizeTimestamp(vitals.timestamp)
            val ts = if (normalizedTs > 0L) normalizedTs else now
            vitals.copy(timestamp = ts)
        }
    val effectiveVitals = when {
        filtered.isNotEmpty() -> filtered
        normalizedVitals.isNotEmpty() -> normalizedVitals
        patientsAsVitals.isNotEmpty() -> patientsAsVitals
        else -> emptyList()
    }

    val avgHr = effectiveVitals.map { it.heartRate }.filter { it > 0 }.average().takeIf { !it.isNaN() }?.toInt()
        ?: patients.firstOrNull { it.heartRate > 0 }?.heartRate
        ?: 0
    val avgSpo2 = effectiveVitals.map { it.spo2 }.filter { it > 0 }.average().takeIf { !it.isNaN() }?.toInt()
        ?: patients.firstOrNull { it.spo2 > 0 }?.spo2
        ?: 0
    val latestGlucose = effectiveVitals.lastOrNull { it.glucose > 0.0 }?.glucose
        ?: patients.lastOrNull { it.glucose > 0.0 }?.glucose
        ?: 0.0
    val sleepScore = rangeSleepData?.score ?: 0
    val trendValues = toTrendBuckets(effectiveVitals)
    val recordsCount = when {
        filtered.isNotEmpty() -> filtered.size
        normalizedVitals.isNotEmpty() -> normalizedVitals.size
        patientsAsVitals.isNotEmpty() -> patientsAsVitals.size
        rangeSleepRecords > 0 -> rangeSleepRecords
        else -> 0
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    )
                )
            )
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = stringResource(R.string.summary_title),
                style = MaterialTheme.typography.titleLarge,
                fontFamily = Manrope,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Text(
            text = stringResource(R.string.summary_subtitle),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = Manrope,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 12.dp, end = 8.dp, bottom = 12.dp),
        )

        SummaryRangeSelector(
            selectedRange = selectedRange,
            onRangeSelected = { selectedRange = it },
        )

        Spacer(Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryStatCard(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.summary_card_avg_hr),
                value = if (avgHr > 0) "$avgHr BPM" else "--",
                icon = { Icon(Icons.Rounded.Favorite, null, tint = Color(0xFFE53935)) },
            )
            SummaryStatCard(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.summary_card_avg_spo2),
                value = if (avgSpo2 > 0) "$avgSpo2%" else "--",
                icon = { Icon(Icons.Rounded.MonitorHeart, null, tint = Color(0xFF26A69A)) },
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryStatCard(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.summary_card_latest_glucose),
                value = if (latestGlucose > 0.0) "${"%.0f".format(Locale.US, latestGlucose)} mg/dL" else "--",
                icon = { Icon(Icons.Rounded.MonitorHeart, null, tint = DashBlue) },
            )
            SummaryStatCard(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.summary_card_sleep_score),
                value = if (sleepScore > 0) "$sleepScore%" else "--",
                icon = { Icon(Icons.Rounded.Bedtime, null, tint = Color(0xFF8E24AA)) },
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryStatCard(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.summary_card_medications),
                value = medicationsCount.toString(),
                icon = { Icon(Icons.Rounded.Medication, null, tint = Color(0xFFFF8F00)) },
            )
            SummaryStatCard(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.summary_card_records),
                value = recordsCount.toString(),
                icon = { Icon(Icons.Rounded.Favorite, null, tint = Color(0xFF3949AB)) },
            )
        }

        Spacer(Modifier.height(16.dp))

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.summary_trend_title),
                    fontFamily = Manrope,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(12.dp))
                if (trendValues.all { it == 0 }) {
                    Text(
                        text = stringResource(R.string.summary_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = Manrope,
                    )
                } else {
                    TrendBars(values = trendValues)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SummaryRangeSelector(
    selectedRange: SummaryRange,
    onRangeSelected: (SummaryRange) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(
            SummaryRange.LAST_24H to stringResource(R.string.summary_range_24h),
            SummaryRange.LAST_7D to stringResource(R.string.summary_range_7d),
            SummaryRange.LAST_30D to stringResource(R.string.summary_range_30d),
        ).forEach { (range, label) ->
            FilterChip(
                selected = selectedRange == range,
                onClick = { onRangeSelected(range) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun SummaryStatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                icon()
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = value,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                fontFamily = Manrope,
                fontSize = 18.sp,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = Manrope,
                fontSize = 12.sp,
                lineHeight = 14.sp,
            )
        }
    }
}

@Composable
private fun TrendBars(values: List<Int>) {
    val maxValue = (values.maxOrNull() ?: 1).coerceAtLeast(1)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        values.forEach { value ->
            val fill = (value.toFloat() / maxValue.toFloat()).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(120.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(12.dp),
                    ),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((100 * fill).dp)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(DashBlue.copy(alpha = 0.65f), DashBlue)
                            ),
                            shape = RoundedCornerShape(12.dp),
                        )
                )
            }
        }
    }
}

private fun toTrendBuckets(vitals: List<VitalsData>): List<Int> {
    if (vitals.isEmpty()) return List(8) { 0 }
    val valid = vitals.filter { it.heartRate > 0 }.sortedBy { it.timestamp }
    if (valid.isEmpty()) return List(8) { 0 }

    val first = valid.first().timestamp
    val last = valid.last().timestamp
    val span = (last - first).coerceAtLeast(1L)
    val bucketSize = (span / 8L).coerceAtLeast(1L)

    return (0 until 8).map { index ->
        val from = first + (bucketSize * index)
        val to = if (index == 7) Long.MAX_VALUE else from + bucketSize
        val bucketValues = valid
            .filter { it.timestamp in from until to }
            .map { it.heartRate }
        if (bucketValues.isEmpty()) 0 else bucketValues.average().toInt()
    }
}

private fun normalizeTimestamp(timestamp: Long): Long {
    if (timestamp <= 0L) return 0L
    // Some sources store Unix seconds while others use milliseconds.
    return if (timestamp < 1_000_000_000_000L) timestamp * 1000L else timestamp
}

private fun resolveRangeDates(range: SummaryRange): Pair<LocalDate, LocalDate> {
    val endDate = LocalDate.now(ZoneId.systemDefault())
    val startDate = when (range) {
        SummaryRange.LAST_24H -> endDate.minusDays(1)
        SummaryRange.LAST_7D -> endDate.minusDays(6)
        SummaryRange.LAST_30D -> endDate.minusDays(29)
    }
    return startDate to endDate
}
