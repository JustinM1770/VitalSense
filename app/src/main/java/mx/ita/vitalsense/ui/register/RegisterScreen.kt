package mx.ita.vitalsense.ui.register

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
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

@Composable
fun RegisterScreen(
    onBack: () -> Unit,
    onRegisterSuccess: () -> Unit,
    onLoginClick: () -> Unit,
    vm: RegisterViewModel = viewModel(),
) {
    val uiState by vm.state.collectAsStateWithLifecycle()

    var name            by remember { mutableStateOf("") }
    var email           by remember { mutableStateOf("") }
    var password        by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var termsAccepted   by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(uiState) {
        when (val s = uiState) {
            is RegisterUiState.Success -> onRegisterSuccess()
            is RegisterUiState.Error   -> snackbar.showSnackbar(s.message)
            else -> Unit
        }
    }

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
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 52.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ── Header ───────────────────────────────────────────────────────
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
                    text = "Regístrate",
                    fontFamily = Manrope,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = TextDark,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            Spacer(Modifier.height(37.dp))

            // ── Form fields ──────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                FigmaTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = "Nombre",
                    leadingIcon = Icons.Outlined.Person,
                )
                FigmaTextField(
                    value = email,
                    onValueChange = { email = it },
                    placeholder = "Email",
                    leadingIcon = Icons.Outlined.Email,
                    keyboardType = KeyboardType.Email,
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
                )
            }

            Spacer(Modifier.height(20.dp))

            // ── Terms ────────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .clickable { termsAccepted = !termsAccepted },
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (termsAccepted) PrimaryBtn else Color.Transparent)
                        .border(
                            width = 1.5.dp,
                            color = if (termsAccepted) PrimaryBtn else Color(0xFFB0B0B0),
                            shape = RoundedCornerShape(4.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (termsAccepted) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = TextDark)) { append("Acepto los ") }
                        withStyle(SpanStyle(color = PrimaryBtn, fontWeight = FontWeight.SemiBold)) { append("Términos de Servicio") }
                        withStyle(SpanStyle(color = TextDark)) { append(" y ") }
                        withStyle(SpanStyle(color = PrimaryBtn, fontWeight = FontWeight.SemiBold)) { append("Políticas de Privacidad") }
                    },
                    fontFamily = Manrope,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            }

            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(40.dp))

            // ── Register Button ──────────────────────────────────────────────
            Button(
                onClick = { vm.registerWithEmail(name, email, password) },
                enabled = uiState !is RegisterUiState.Loading && termsAccepted,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(32.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryBtn,
                    contentColor = Color.White,
                    disabledContainerColor = PrimaryBtn.copy(alpha = 0.4f),
                ),
            ) {
                if (uiState is RegisterUiState.Loading) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                } else {
                    Text(
                        text = "Regístrate",
                        fontFamily = Manrope,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
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
                Box(Modifier.weight(1f).height(1.dp).background(Color(0xFFE5E5E5)))
                Text(
                    text = "  O  ",
                    fontFamily = Manrope,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = Color(0xFF8A8A8A),
                )
                Box(Modifier.weight(1f).height(1.dp).background(Color(0xFFE5E5E5)))
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
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E5E5)),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White),
            ) {
                Text("Regístrate con ", color = TextDark, fontFamily = Manrope, fontSize = 14.sp)
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
            }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = { vm.signInWithFacebook(context) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(32.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E5E5)),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White),
            ) {
                Text("Regístrate con ", color = TextDark, fontFamily = Manrope, fontSize = 14.sp)
                Text(
                    text = "Facebook",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color(0xFF1877F2),
                )
            }

            Spacer(Modifier.height(24.dp))

            // ── Login link ───────────────────────────────────────────────────
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = TextDark)) { append("¿Ya tienes cuenta? ") }
                    withStyle(SpanStyle(color = PrimaryBtn, fontWeight = FontWeight.SemiBold)) { append("Inicia Sesión") }
                },
                fontFamily = Manrope,
                fontSize = 14.sp,
                modifier = Modifier
                    .clickable(onClick = onLoginClick)
                    .padding(bottom = 40.dp),
            )
        }

        SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter))
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
