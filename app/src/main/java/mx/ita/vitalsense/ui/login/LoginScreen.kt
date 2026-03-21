package mx.ita.vitalsense.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.SpanStyle
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
private val InputBg    = Color(0xFFF9F9FB)
private val PrimaryBtn = Color(0xFF1169FF)
private val IconTint   = Color(0xFFB0B8C4)
private val DividerClr = Color(0xFFE5E5E5)

@Composable
fun LoginScreen(
    onBack: () -> Unit,
    onLoginSuccess: () -> Unit,
    onRegisterClick: () -> Unit,
    onForgotPassword: () -> Unit = {},
    vm: LoginViewModel = viewModel(),
) {
    val uiState by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var email           by remember { mutableStateOf("") }
    var password        by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(uiState) {
        when (val s = uiState) {
            is LoginUiState.Success -> onLoginSuccess()
            is LoginUiState.Error   -> snackbar.showSnackbar(s.message)
            else -> Unit
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(top = 52.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ── Header ────────────────────────────────────────────────────────
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
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Regresar",
                        tint = TextDark,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Text(
                    text = "Iniciar Sesión",
                    fontFamily = Manrope,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = TextDark,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            Spacer(Modifier.height(37.dp))

            // ── Form fields ───────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                LoginField(
                    value = email,
                    onValueChange = { email = it },
                    placeholder = "Email",
                    leadingIcon = Icons.Outlined.Email,
                    keyboardType = KeyboardType.Email,
                    enabled = uiState !is LoginUiState.Loading,
                )
                LoginField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = "Contraseña",
                    leadingIcon = Icons.Outlined.Lock,
                    trailingIcon = if (passwordVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                    onTrailingClick = { passwordVisible = !passwordVisible },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardType = KeyboardType.Password,
                    enabled = uiState !is LoginUiState.Loading,
                )
            }

            Spacer(Modifier.height(10.dp))

            // ── Olvidaste contraseña ───────────────────────────────────────────
            Text(
                text = "¿Olvidaste tu contraseña?",
                fontFamily = Manrope,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = PrimaryBtn,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(end = 24.dp)
                    .clickable { onForgotPassword() },
            )

            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(28.dp))

            // ── Botón Iniciar Sesión ───────────────────────────────────────────
            Button(
                onClick = {
                    if (email.isNotBlank() && password.isNotBlank()) {
                        vm.signInWithEmail(email, password)
                    }
                },
                enabled = uiState !is LoginUiState.Loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(59.dp),
                shape = RoundedCornerShape(32.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryBtn,
                    contentColor = Color.White,
                    disabledContainerColor = PrimaryBtn.copy(alpha = 0.4f),
                ),
            ) {
                if (uiState is LoginUiState.Loading) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                } else {
                    Text("Iniciar Sesión", fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── "No tienes cuenta?" ───────────────────────────────────────────
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = TextDark)) { append("No tienes cuenta?  ") }
                    withStyle(SpanStyle(color = PrimaryBtn, fontWeight = FontWeight.SemiBold)) { append("Registrate") }
                },
                fontFamily = Manrope,
                fontSize = 14.sp,
                modifier = Modifier.clickable(onClick = onRegisterClick),
            )

            Spacer(Modifier.height(24.dp))

            // ── Divisor con círculo ───────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = DividerClr)
                Box(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .size(20.dp)
                        .border(1.dp, DividerClr, CircleShape),
                )
                HorizontalDivider(modifier = Modifier.weight(1f), color = DividerClr)
            }

            Spacer(Modifier.height(20.dp))

            // ── Botón Google (colorido) ───────────────────────────────────────
            SocialButton(
                onClick = { vm.signInWithGoogle(context) },
                enabled = uiState !is LoginUiState.Loading,
            ) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = Color(0xFF4285F4), fontWeight = FontWeight.Bold, fontSize = 18.sp)) { append("G") }
                        withStyle(SpanStyle(color = Color(0xFFEA4335), fontWeight = FontWeight.Bold, fontSize = 18.sp)) { append("o") }
                        withStyle(SpanStyle(color = Color(0xFFFBBC05), fontWeight = FontWeight.Bold, fontSize = 18.sp)) { append("o") }
                        withStyle(SpanStyle(color = Color(0xFF4285F4), fontWeight = FontWeight.Bold, fontSize = 18.sp)) { append("g") }
                        withStyle(SpanStyle(color = Color(0xFF34A853), fontWeight = FontWeight.Bold, fontSize = 18.sp)) { append("l") }
                        withStyle(SpanStyle(color = Color(0xFFEA4335), fontWeight = FontWeight.Bold, fontSize = 18.sp)) { append("e") }
                    },
                    fontFamily = Manrope,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "Continuar con Google",
                    fontFamily = Manrope,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = TextDark,
                )
            }

            Spacer(Modifier.height(12.dp))

            // ── Botón Facebook ────────────────────────────────────────────────
            SocialButton(onClick = { vm.signInWithFacebook(context) }, enabled = uiState !is LoginUiState.Loading) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1877F2)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "f",
                        fontFamily = Manrope,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "Inicia Sesion con Facebook",
                    fontFamily = Manrope,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = TextDark,
                )
            }

            Spacer(Modifier.height(40.dp))
        }

        SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun SocialButton(
    onClick: () -> Unit,
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .height(52.dp)
            .border(1.dp, DividerClr, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            content()
        }
    }
}

@Composable
private fun LoginField(
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
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().height(59.dp),
        enabled = enabled,
        placeholder = {
            Text(placeholder, fontFamily = Manrope, fontSize = 14.sp, color = TextDark.copy(alpha = 0.4f))
        },
        leadingIcon = {
            Icon(leadingIcon, contentDescription = null, tint = TextDark.copy(alpha = 0.5f), modifier = Modifier.size(22.dp))
        },
        trailingIcon = if (trailingIcon != null) {
            { IconButton(onClick = { onTrailingClick?.invoke() }) {
                Icon(trailingIcon, contentDescription = null, tint = TextDark.copy(alpha = 0.5f), modifier = Modifier.size(22.dp))
            } }
        } else null,
        visualTransformation = visualTransformation,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true,
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor      = PrimaryBtn,
            unfocusedBorderColor    = Color.Transparent,
            focusedContainerColor   = InputBg,
            unfocusedContainerColor = InputBg,
            focusedTextColor   = TextDark,
            unfocusedTextColor = TextDark,
            cursorColor = PrimaryBtn,
        ),
    )
}
