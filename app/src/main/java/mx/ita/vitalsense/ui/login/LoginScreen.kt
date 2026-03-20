package mx.ita.vitalsense.ui.login

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
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
private val DividerClr = Color(0xFFE5E5E5)

@Composable
fun LoginScreen(
    onBack: () -> Unit,
    onLoginSuccess: () -> Unit,
    onRegisterClick: () -> Unit,
    vm: LoginViewModel = viewModel()
) {
    val uiState by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var email           by remember { mutableStateOf("") }
    var password        by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(uiState) {
        when (uiState) {
            is LoginUiState.Success -> onLoginSuccess()
            is LoginUiState.Error -> {
                snackbarHostState.showSnackbar((uiState as LoginUiState.Error).message)
                vm.clearError()
            }
            else -> {}
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
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // ── Header ───────────────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .height(40.dp),
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.align(Alignment.CenterStart)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás", tint = TextDark)
                    }
                    Text(
                        text = "Inicia Sesión",
                        fontFamily = Manrope,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextDark,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                Spacer(Modifier.height(40.dp))

                // ── Form ─────────────────────────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    FigmaTextField(
                        value = email,
                        onValueChange = { email = it },
                        placeholder = "Email",
                        leadingIcon = Icons.Outlined.Email,
                        keyboardType = KeyboardType.Email,
                        enabled = uiState !is LoginUiState.Loading
                    )
                    FigmaTextField(
                        value = password,
                        onValueChange = { password = it },
                        placeholder = "Contraseña",
                        leadingIcon = Icons.Outlined.Lock,
                        trailingIcon = if (passwordVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                        onTrailingClick = { passwordVisible = !passwordVisible },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardType = KeyboardType.Password,
                        enabled = uiState !is LoginUiState.Loading
                    )
                }

                // ── Forgot password ──────────────────────────────────────────
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "¿Olvidaste tu contraseña?",
                    fontFamily = Manrope,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = PrimaryBtn,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(end = 32.dp)
                        .clickable { /* TODO: reset password flow */ }
                )

                Spacer(Modifier.height(32.dp))

                // ── Login Button ─────────────────────────────────────────────
                Button(
                    onClick = {
                        if (email.isNotBlank() && password.isNotBlank()) {
                            vm.loginWithEmail(email, password)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                        .height(56.dp),
                    shape = RoundedCornerShape(32.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBtn),
                    enabled = uiState !is LoginUiState.Loading
                ) {
                    if (uiState is LoginUiState.Loading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    } else {
                        Text(
                            "Iniciar Sesión",
                            fontFamily = Manrope,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                // ── "O" separator ────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(1.dp)
                            .background(DividerClr)
                    )
                    Text(
                        text = "  O  ",
                        fontFamily = Manrope,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = Color(0xFF8A8A8A),
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .height(1.dp)
                            .background(DividerClr)
                    )
                }

                Spacer(Modifier.height(24.dp))

                // ── Google Button ────────────────────────────────────────────
                OutlinedButton(
                    onClick = { vm.signInWithGoogle(context) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                        .height(52.dp),
                    shape = RoundedCornerShape(32.dp),
                    border = BorderStroke(1.dp, DividerClr),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White),
                    enabled = uiState !is LoginUiState.Loading
                ) {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = Color(0xFF4285F4), fontWeight = FontWeight.Bold)) { append("G") }
                            withStyle(SpanStyle(color = Color(0xFFEA4335), fontWeight = FontWeight.Bold)) { append("o") }
                            withStyle(SpanStyle(color = Color(0xFFFBBC05), fontWeight = FontWeight.Bold)) { append("o") }
                            withStyle(SpanStyle(color = Color(0xFF4285F4), fontWeight = FontWeight.Bold)) { append("g") }
                            withStyle(SpanStyle(color = Color(0xFF34A853), fontWeight = FontWeight.Bold)) { append("l") }
                            withStyle(SpanStyle(color = Color(0xFFEA4335), fontWeight = FontWeight.Bold)) { append("e") }
                        },
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Inicia Sesión con Google", color = TextDark, fontFamily = Manrope, fontSize = 14.sp)
                }

                Spacer(Modifier.height(12.dp))

                // ── Facebook Button ──────────────────────────────────────────
                OutlinedButton(
                    onClick = { /* TODO: Facebook login */ },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                        .height(52.dp),
                    shape = RoundedCornerShape(32.dp),
                    border = BorderStroke(1.dp, DividerClr),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White),
                    enabled = uiState !is LoginUiState.Loading
                ) {
                    Text(
                        text = "f",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color(0xFF1877F2),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Inicia Sesión con Facebook", color = TextDark, fontFamily = Manrope, fontSize = 14.sp)
                }

                Spacer(Modifier.height(32.dp))

                // ── Register link ────────────────────────────────────────────
                Text(
                    text = buildAnnotatedString {
                        append("¿No tienes cuenta? ")
                        withStyle(SpanStyle(color = PrimaryBtn, fontWeight = FontWeight.Bold)) {
                            append("Regístrate")
                        }
                    },
                    modifier = Modifier
                        .clickable { onRegisterClick() }
                        .padding(bottom = 40.dp),
                    fontFamily = Manrope,
                    fontSize = 14.sp,
                    color = TextDark,
                )
            }
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
    trailingIcon: ImageVector? = null,
    onTrailingClick: (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardType: KeyboardType = KeyboardType.Text,
    enabled: Boolean = true,
) {
    Box(
        modifier = Modifier
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
                        color = IconTint,
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
                        color = TextDark,
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    visualTransformation = visualTransformation,
                    cursorBrush = SolidColor(PrimaryBtn),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (trailingIcon != null) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    trailingIcon,
                    contentDescription = null,
                    tint = IconTint,
                    modifier = Modifier
                        .size(22.dp)
                        .clickable { onTrailingClick?.invoke() }
                )
            }
        }
    }
}
