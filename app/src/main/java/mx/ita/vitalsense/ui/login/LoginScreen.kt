package mx.ita.vitalsense.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import mx.ita.vitalsense.ui.login.LoginUiState
import mx.ita.vitalsense.ui.login.LoginViewModel
import mx.ita.vitalsense.ui.theme.Manrope

private val BgColor    = Color(0xFFFFFFFF)
private val TextDark   = Color(0xFF221F1F)
private val InputBg    = Color(0xFFF9F9FB)
private val PrimaryBtn = Color(0xFF1169FF)
private val Divider    = Color(0xFFE5E5E5)

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
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BgColor)
                    .padding(top = 52.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Header
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

                // Form
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    LoginField(
                        value = email,
                        onValueChange = { email = it },
                        placeholder = "Email",
                        leadingIcon = Icons.Outlined.Email,
                        keyboardType = KeyboardType.Email,
                        enabled = uiState !is LoginUiState.Loading
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
                        enabled = uiState !is LoginUiState.Loading
                    )
                }

                Spacer(Modifier.height(40.dp))

                Button(
                    onClick = {
                        if (email.isNotBlank() && password.isNotBlank()) {
                            vm.loginWithEmail(email, password)
                        }
                    },
                    modifier = Modifier.width(325.dp).height(59.dp),
                    shape = RoundedCornerShape(32.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBtn),
                    enabled = uiState !is LoginUiState.Loading
                ) {
                    if (uiState is LoginUiState.Loading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    } else {
                        Text("Entrar", fontFamily = Manrope, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Google Login
                OutlinedButton(
                    onClick = { vm.signInWithGoogle(context) },
                    modifier = Modifier.width(325.dp).height(52.dp),
                    shape = RoundedCornerShape(32.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Divider),
                    enabled = uiState !is LoginUiState.Loading
                ) {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = Color(0xFF4285F4))) { append("G") }
                            withStyle(SpanStyle(color = Color(0xFFEA4335))) { append("o") }
                            withStyle(SpanStyle(color = Color(0xFFFBBC05))) { append("o") }
                            withStyle(SpanStyle(color = Color(0xFF4285F4))) { append("g") }
                            withStyle(SpanStyle(color = Color(0xFF34A853))) { append("l") }
                            withStyle(SpanStyle(color = Color(0xFFEA4335))) { append("e") }
                        },
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Continuar con Google", color = TextDark)
                }

                Spacer(Modifier.height(32.dp))

                Text(
                    text = buildAnnotatedString {
                        append("¿No tienes cuenta? ")
                        withStyle(SpanStyle(color = PrimaryBtn, fontWeight = FontWeight.Bold)) {
                            append("Regístrate")
                        }
                    },
                    modifier = Modifier.clickable { onRegisterClick() },
                    fontFamily = Manrope,
                    fontSize = 14.sp
                )
            }
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
    enabled: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        placeholder = { Text(placeholder, color = TextDark.copy(0.4f)) },
        leadingIcon = { Icon(leadingIcon, null, tint = TextDark) },
        trailingIcon = if (trailingIcon != null) {
            { IconButton(onClick = { onTrailingClick?.invoke() }) { Icon(trailingIcon, null) } }
        } else null,
        visualTransformation = visualTransformation,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = InputBg,
            unfocusedContainerColor = InputBg,
            unfocusedBorderColor = Color.Transparent,
            focusedBorderColor = PrimaryBtn
        )
    )
}
