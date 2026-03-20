package mx.ita.vitalsense.ui.forgotpassword

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import mx.ita.vitalsense.ui.theme.Manrope

// ── Design tokens ────────────────────────────────────────────────────────────
private val TextDark   = Color(0xFF221F1F)
private val InputBg    = Color(0xFFF0F2F5)
private val PrimaryBtn = Color(0xFF1169FF)
private val IconTint   = Color(0xFFB0B8C4)

@Composable
fun ForgotPasswordScreen(
    onBack: () -> Unit,
    onBackToLogin: () -> Unit,
    vm: ForgotPasswordViewModel = viewModel()
) {
    val uiState by vm.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var email by remember { mutableStateOf("") }

    LaunchedEffect(uiState) {
        if (uiState is ForgotPasswordUiState.Error) {
            snackbarHostState.showSnackbar((uiState as ForgotPasswordUiState.Error).message)
            vm.resetState()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFFF0F4FF), Color.White),
                        startY = 0f,
                        endY = 600f
                    )
                )
                .padding(paddingValues)
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 52.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ── Header ───────────────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .height(60.dp)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.align(Alignment.CenterStart)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            "Atrás",
                            tint = TextDark
                        )
                    }
                    Text(
                        text = "Restablecer\nContraseña",
                        fontFamily = Manrope,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextDark,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                Spacer(Modifier.height(40.dp))

                // ── Content switches between form and confirmation ──────────
                AnimatedContent(
                    targetState = uiState is ForgotPasswordUiState.EmailSent,
                    transitionSpec = {
                        fadeIn() + slideInHorizontally { it } togetherWith
                                fadeOut() + slideOutHorizontally { -it }
                    },
                    label = "forgot_password_content"
                ) { emailSent ->
                    if (!emailSent) {
                        // ── STEP 1: Email input ──────────────────────────────
                        EmailInputContent(
                            email = email,
                            onEmailChange = { email = it },
                            isLoading = uiState is ForgotPasswordUiState.Loading,
                            onSend = { vm.sendResetEmail(email) },
                            onBackToLogin = onBackToLogin
                        )
                    } else {
                        // ── STEP 2: Confirmation ─────────────────────────────
                        EmailSentConfirmation(
                            email = email,
                            onBackToLogin = {
                                vm.resetState()
                                onBackToLogin()
                            }
                        )
                    }
                }
            }
        }
    }
}

// ── Step 1: Email Input ──────────────────────────────────────────────────────
@Composable
private fun EmailInputContent(
    email: String,
    onEmailChange: (String) -> Unit,
    isLoading: Boolean,
    onSend: () -> Unit,
    onBackToLogin: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Introduce tu email para\nrecibir un enlace de\nrestablecimiento",
            fontFamily = Manrope,
            fontSize = 14.sp,
            color = Color(0xFF6B7280),
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
            modifier = Modifier.padding(horizontal = 32.dp)
        )

        Spacer(Modifier.height(40.dp))

        // ── Email label ──────────────────────────────────────────────────
        Text(
            text = "Email",
            fontFamily = Manrope,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            color = TextDark,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .padding(bottom = 8.dp)
        )

        // ── Email field ──────────────────────────────────────────────────
        FigmaTextField(
            value = email,
            onValueChange = onEmailChange,
            placeholder = "tu@email.com",
            leadingIcon = Icons.Outlined.Email,
            keyboardType = KeyboardType.Email,
            enabled = !isLoading,
            modifier = Modifier.padding(horizontal = 32.dp)
        )

        Spacer(Modifier.height(40.dp))

        // ── Send button ──────────────────────────────────────────────────
        Button(
            onClick = onSend,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .height(56.dp),
            shape = RoundedCornerShape(32.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBtn),
            enabled = !isLoading && email.isNotBlank()
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    "Enviar enlace",
                    fontFamily = Manrope,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Back to login link ───────────────────────────────────────────
        OutlinedButton(
            onClick = onBackToLogin,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .height(52.dp),
            shape = RoundedCornerShape(32.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryBtn),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryBtn)
        ) {
            Text(
                "Volver al Login",
                fontFamily = Manrope,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = PrimaryBtn
            )
        }
    }
}

// ── Step 2: Confirmation ─────────────────────────────────────────────────────
@Composable
private fun EmailSentConfirmation(
    email: String,
    onBackToLogin: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))

        // ── Success icon ─────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(40.dp))
                .background(PrimaryBtn.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.MarkEmailRead,
                contentDescription = null,
                tint = PrimaryBtn,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(Modifier.height(32.dp))

        Text(
            text = "¡Correo enviado!",
            fontFamily = Manrope,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            color = TextDark
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Hemos enviado un enlace de\nrestablecimiento de contraseña a:",
            fontFamily = Manrope,
            fontSize = 14.sp,
            color = Color(0xFF6B7280),
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = email,
            fontFamily = Manrope,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            color = PrimaryBtn,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Revisa tu bandeja de entrada y sigue\nlas instrucciones del correo para\ncambiar tu contraseña.",
            fontFamily = Manrope,
            fontSize = 13.sp,
            color = Color(0xFF9CA3AF),
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )

        Spacer(Modifier.height(48.dp))

        // ── Back to Login button ─────────────────────────────────────────
        Button(
            onClick = onBackToLogin,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(32.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBtn)
        ) {
            Text(
                "Volver al Login",
                fontFamily = Manrope,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
        }
    }
}

// ── Figma-Style TextField ────────────────────────────────────────────────────
@Composable
private fun FigmaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leadingIcon: ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(InputBg)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                leadingIcon,
                contentDescription = null,
                tint = IconTint,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(12.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        fontFamily = Manrope,
                        fontSize = 14.sp,
                        color = IconTint
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    singleLine = true,
                    textStyle = TextStyle(
                        fontFamily = Manrope,
                        fontSize = 14.sp,
                        color = TextDark
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    cursorBrush = SolidColor(PrimaryBtn),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
