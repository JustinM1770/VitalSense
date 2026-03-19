package mx.ita.vitalsense.ui.register

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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

// ─── Figma design tokens ──────────────────────────────────────────────────────
private val RegBg      = Color(0xFFFFFFFF)
private val TextDark   = Color(0xFF221F1F)
private val InputBg    = Color(0xFFF9F9FB)
private val PrimaryBtn = Color(0xFF1169FF)
private val CheckBlue  = Color(0xFF3B82F6)
private val Divider    = Color(0xFFE5E5E5)

// ─── Screen ───────────────────────────────────────────────────────────────────

@Composable
fun RegisterScreen(
    onBack: () -> Unit,
    onRegisterSuccess: () -> Unit,
    onLoginClick: () -> Unit,
    vm: RegisterViewModel = viewModel(),
) {
    val uiState by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var name            by remember { mutableStateOf("") }
    var email           by remember { mutableStateOf("") }
    var password        by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var termsAccepted   by remember { mutableStateOf(false) }

    val snackbar = remember { SnackbarHostState() }

    // Navegar al éxito o mostrar error
    LaunchedEffect(uiState) {
        when (val s = uiState) {
            is RegisterUiState.Success -> onRegisterSuccess()
            is RegisterUiState.Error   -> snackbar.showSnackbar(s.message)
            else -> Unit
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(RegBg)
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
                    text = "Registrate",
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
                    .padding(horizontal = 32.dp),
                verticalArrangement = Arrangement.spacedBy(27.dp),
            ) {
                RegFormField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = "Nombre",
                    leadingIcon = Icons.Outlined.Person,
                )
                RegFormField(
                    value = email,
                    onValueChange = { email = it },
                    placeholder = "Email",
                    leadingIcon = Icons.Outlined.Email,
                    keyboardType = KeyboardType.Email,
                )
                RegFormField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = "Contraseña",
                    leadingIcon = Icons.Outlined.Lock,
                    trailingIcon = if (passwordVisible) Icons.Outlined.Visibility
                                   else Icons.Outlined.VisibilityOff,
                    onTrailingClick = { passwordVisible = !passwordVisible },
                    visualTransformation = if (passwordVisible) VisualTransformation.None
                                           else PasswordVisualTransformation(),
                    keyboardType = KeyboardType.Password,
                )
            }

            Spacer(Modifier.height(19.dp))

            // ── Términos ──────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .clickable { termsAccepted = !termsAccepted },
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White)
                        .border(
                            width = 1.5.dp,
                            color = if (termsAccepted) CheckBlue else TextDark,
                            shape = RoundedCornerShape(8.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (termsAccepted) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            tint = CheckBlue,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Text(
                    text = "Acepto los Terminos de Servicio y Politicas de Privacidad",
                    fontFamily = Manrope,
                    fontWeight = FontWeight.Normal,
                    fontSize = 13.sp,
                    color = TextDark,
                    lineHeight = 18.sp,
                )
            }

            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(32.dp))

            // ── Botón Registrate ──────────────────────────────────────────────
            Button(
                onClick = { vm.registerWithEmail(name, email, password) },
                enabled = uiState !is RegisterUiState.Loading && termsAccepted,
                modifier = Modifier
                    .width(325.dp)
                    .height(59.dp),
                shape = RoundedCornerShape(32.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryBtn,
                    contentColor = Color.White,
                    disabledContainerColor = PrimaryBtn.copy(alpha = 0.4f),
                ),
            ) {
                if (uiState is RegisterUiState.Loading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(22.dp),
                    )
                } else {
                    Text(
                        text = "Registrate",
                        fontFamily = Manrope,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Divisor "o continúa con" ──────────────────────────────────────
            Row(
                modifier = Modifier
                    .width(325.dp)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = Divider)
                Text(
                    text = "  o continúa con  ",
                    fontFamily = Manrope,
                    fontSize = 12.sp,
                    color = TextDark.copy(alpha = 0.45f),
                )
                HorizontalDivider(modifier = Modifier.weight(1f), color = Divider)
            }

            Spacer(Modifier.height(16.dp))

            // ── Botón Google ──────────────────────────────────────────────────
            OutlinedButton(
                onClick = { vm.signInWithGoogle(context) },
                enabled = uiState !is RegisterUiState.Loading,
                modifier = Modifier
                    .width(325.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(32.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Divider),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextDark),
            ) {
                // "G" con colores de Google como texto estilizado
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = Color(0xFF4285F4), fontWeight = FontWeight.Bold)) { append("G") }
                        withStyle(SpanStyle(color = Color(0xFFEA4335), fontWeight = FontWeight.Bold)) { append("o") }
                        withStyle(SpanStyle(color = Color(0xFFFBBC05), fontWeight = FontWeight.Bold)) { append("o") }
                        withStyle(SpanStyle(color = Color(0xFF4285F4), fontWeight = FontWeight.Bold)) { append("g") }
                        withStyle(SpanStyle(color = Color(0xFF34A853), fontWeight = FontWeight.Bold)) { append("l") }
                        withStyle(SpanStyle(color = Color(0xFFEA4335), fontWeight = FontWeight.Bold)) { append("e") }
                    },
                    fontSize = 16.sp,
                    fontFamily = Manrope,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Continuar con Google",
                    fontFamily = Manrope,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = TextDark,
                )
            }

            Spacer(Modifier.height(20.dp))

            // ── Link "Ya tienes cuenta?" ──────────────────────────────────────
            Text(
                text = buildAnnotatedString {
                    append("Ya tienes cuenta?  ")
                    withStyle(SpanStyle(color = PrimaryBtn, fontWeight = FontWeight.SemiBold)) {
                        append("Inicia Sesión")
                    }
                },
                fontFamily = Manrope,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                color = TextDark,
                modifier = Modifier
                    .clickable(onClick = onLoginClick)
                    .padding(bottom = 32.dp),
            )
        }

        // Snackbar para errores
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

// ─── Campo de formulario ──────────────────────────────────────────────────────

@Composable
private fun RegFormField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leadingIcon: ImageVector,
    trailingIcon: ImageVector? = null,
    onTrailingClick: (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .height(59.dp),
        placeholder = {
            Text(
                text = placeholder,
                fontFamily = Manrope,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                color = TextDark.copy(alpha = 0.45f),
            )
        },
        leadingIcon = {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = TextDark,
                modifier = Modifier.size(24.dp),
            )
        },
        trailingIcon = if (trailingIcon != null) {
            {
                IconButton(onClick = { onTrailingClick?.invoke() }) {
                    Icon(
                        imageVector = trailingIcon,
                        contentDescription = null,
                        tint = TextDark,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        } else null,
        visualTransformation = visualTransformation,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true,
        shape = RoundedCornerShape(6.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = TextDark,
            unfocusedBorderColor = TextDark,
            focusedContainerColor = InputBg,
            unfocusedContainerColor = InputBg,
            focusedTextColor = TextDark,
            unfocusedTextColor = TextDark,
            cursorColor = TextDark,
        ),
    )
}
