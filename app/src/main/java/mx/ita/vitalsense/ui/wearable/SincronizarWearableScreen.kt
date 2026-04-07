package mx.ita.vitalsense.ui.wearable

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

// Azul de la pantalla Figma (distinto al PrimaryBlue #1169FF del design system general)
private val WearableBlue = Color(0xFF3D5AFE)

@Composable
fun SincronizarWearableScreen(
    onBack: () -> Unit,
    onSyncSuccess: () -> Unit,
    viewModel: SincronizarWearableViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context  = LocalContext.current
    val scope    = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val focusRequester = remember { FocusRequester() }

    // Launcher para solicitar permisos de Health Connect
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract(),
    ) { granted ->
        if (granted.containsAll(mx.ita.vitalsense.data.health.HealthConnectRepository.PERMISSIONS)) {
            viewModel.onPermissionsGranted(context)
        } else {
            scope.launch { snackbar.showSnackbar("Permisos de Health Connect denegados") }
        }
    }

    // Mostrar errores en Snackbar
    LaunchedEffect(uiState.syncState) {
        if (uiState.syncState is SyncState.Error) {
            snackbar.showSnackbar((uiState.syncState as SyncState.Error).message)
            viewModel.clearError()
        }
        if (uiState.syncState is SyncState.Success) {
            onSyncSuccess()
        }
    }

    // Auto-focus en el campo de texto oculto
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(16.dp))

            // ── Barra superior ──────────────────────────────────────────────
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterStart,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Regresar",
                        tint = Color.Black,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Título ──────────────────────────────────────────────────────
            Text(
                text = "Sincronizar\nWearable",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = Color.Black,
                lineHeight = 32.sp,
            )

            Spacer(Modifier.height(20.dp))

            // ── Subtítulo ───────────────────────────────────────────────────
            Text(
                text = "Introduce el código que te\nmuestra el wearable",
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center,
                color = Color(0xFF6B7A8D),
                lineHeight = 22.sp,
            )

            Spacer(Modifier.height(56.dp))

            // ── Celdas de código (campo invisible + overlay visual) ─────────
            Box(contentAlignment = Alignment.Center) {
                // Campo de texto oculto que captura el input real
                BasicTextField(
                    value = uiState.code,
                    onValueChange = { viewModel.onCodeChange(it) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Ascii,
                        capitalization = KeyboardCapitalization.Characters,
                    ),
                    cursorBrush = SolidColor(Color.Transparent),
                    modifier = Modifier
                        .size(1.dp)
                        .focusRequester(focusRequester),
                    decorationBox = { it() },
                )

                // Overlay visual — 4 celdas + guion + 4 celdas
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Primeras 4 celdas
                    repeat(4) { index ->
                        CodeCell(
                            char = uiState.code.getOrNull(index),
                            isActive = uiState.code.length == index,
                        )
                    }

                    // Separador
                    Text(
                        text = "—",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF3D3D3D),
                    )

                    // Ultimas 4 celdas
                    repeat(4) { index ->
                        val charIndex = index + 4
                        CodeCell(
                            char = uiState.code.getOrNull(charIndex),
                            isActive = uiState.code.length == charIndex,
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // ── Botón Sincronizar ───────────────────────────────────────────
            Button(
                onClick = {
                    viewModel.sincronizar(context) { permissions ->
                        permissionLauncher.launch(permissions)
                    }
                },
                enabled = uiState.code.length == 8 && uiState.syncState !is SyncState.Loading,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = WearableBlue,
                    disabledContainerColor = WearableBlue.copy(alpha = 0.4f),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                AnimatedVisibility(
                    visible = uiState.syncState is SyncState.Loading,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(22.dp),
                    )
                }
                AnimatedVisibility(
                    visible = uiState.syncState !is SyncState.Loading,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Text(
                        text = "Sincronizar",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                }
            }

            Spacer(Modifier.height(40.dp))
        }

        // ── Snackbar ────────────────────────────────────────────────────────
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun CodeCell(
    char: Char?,
    isActive: Boolean,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width = 72.dp, height = 80.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(WearableBlue)
            .then(
                if (isActive) Modifier.border(
                    width = 2.dp,
                    color = Color.White.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(16.dp),
                ) else Modifier
            ),
    ) {
        Text(
            text = char?.toString() ?: "",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
    }
}
