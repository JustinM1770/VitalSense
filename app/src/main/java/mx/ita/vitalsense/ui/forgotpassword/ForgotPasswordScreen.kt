package mx.ita.vitalsense.ui.forgotpassword

import android.util.Patterns
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import mx.ita.vitalsense.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgotPasswordScreen(
    onBack: () -> Unit = {},
    onBackToLogin: () -> Unit = {}
) {
    val context = LocalContext.current
    var email by remember { mutableStateOf("") }
    var feedback by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.forgot_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.forgot_subtitle), style = MaterialTheme.typography.bodyMedium)

            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                    feedback = null
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.forgot_email_label)) },
            )

            Button(
                onClick = {
                    if (!Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
                        feedback = context.getString(R.string.forgot_invalid_email)
                        return@Button
                    }
                    FirebaseAuth.getInstance().sendPasswordResetEmail(email.trim())
                        .addOnSuccessListener {
                            feedback = context.getString(R.string.forgot_success)
                        }
                        .addOnFailureListener {
                            feedback = context.getString(R.string.forgot_error)
                        }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.forgot_send))
            }

            Button(
                onClick = onBackToLogin,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.forgot_back_login))
            }

            feedback?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
