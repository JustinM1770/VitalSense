package mx.ita.vitalsense.ui.contact

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Message
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.ita.vitalsense.R
import mx.ita.vitalsense.ui.common.LockLandscapeOrientation

data class ContactAction(
    val title: String,
    val icon: @Composable () -> Unit,
    val onClick: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactScreen(onBack: () -> Unit) {
    LockLandscapeOrientation()
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme

    val actions = listOf(
        ContactAction(
            title = stringResource(R.string.contact_whatsapp),
            icon = { Icon(Icons.Rounded.Chat, contentDescription = null, tint = Color(0xFF25D366)) },
            onClick = { openUri(context, "https://wa.me/5215512345678") },
        ),
        ContactAction(
            title = stringResource(R.string.contact_email),
            icon = { Icon(Icons.Rounded.Email, contentDescription = null, tint = Color(0xFFEA4335)) },
            onClick = { sendEmail(context, "support@biometricai.app", "Soporte BioMetric AI") },
        ),
        ContactAction(
            title = stringResource(R.string.contact_call),
            icon = { Icon(Icons.Rounded.Call, contentDescription = null, tint = Color(0xFF1169FF)) },
            onClick = { dialPhone(context, "+525512345678") },
        ),
        ContactAction(
            title = stringResource(R.string.contact_sms),
            icon = { Icon(Icons.Rounded.Message, contentDescription = null, tint = Color(0xFF5E35B1)) },
            onClick = { sendSms(context, "+525512345678", "Hola, necesito soporte de BioMetric AI") },
        ),
        ContactAction(
            title = stringResource(R.string.contact_facebook),
            icon = { Icon(Icons.Rounded.Language, contentDescription = null, tint = Color(0xFF1877F2)) },
            onClick = { openUri(context, "https://www.facebook.com/") },
        ),
        ContactAction(
            title = stringResource(R.string.contact_instagram),
            icon = { Icon(Icons.Rounded.Share, contentDescription = null, tint = Color(0xFFE1306C)) },
            onClick = { openUri(context, "https://www.instagram.com/") },
        ),
        ContactAction(
            title = stringResource(R.string.contact_x),
            icon = { Icon(Icons.Rounded.Share, contentDescription = null, tint = Color.Black) },
            onClick = { openUri(context, "https://x.com/") },
        ),
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = colorScheme.surface,
                    titleContentColor = colorScheme.onSurface,
                    navigationIconContentColor = colorScheme.onSurface,
                ),
                title = { Text(stringResource(R.string.contact_title), fontWeight = FontWeight.Bold) },
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
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 20.dp),
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.contact_subtitle),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
            )
            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(actions) { action ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                            .clickable { action.onClick() }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(modifier = Modifier.size(28.dp), verticalAlignment = Alignment.CenterVertically) {
                            action.icon()
                        }
                        Spacer(modifier = Modifier.size(12.dp))
                        Text(action.title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

private fun openUri(context: Context, uri: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, context.getString(R.string.contact_open_error), Toast.LENGTH_SHORT).show()
    }
}

private fun sendEmail(context: Context, email: String, subject: String) {
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:")
        putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
        putExtra(Intent.EXTRA_SUBJECT, subject)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, context.getString(R.string.contact_open_error), Toast.LENGTH_SHORT).show()
    }
}

private fun dialPhone(context: Context, phone: String) {
    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, context.getString(R.string.contact_open_error), Toast.LENGTH_SHORT).show()
    }
}

private fun sendSms(context: Context, phone: String, message: String) {
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phone")).apply {
        putExtra("sms_body", message)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, context.getString(R.string.contact_open_error), Toast.LENGTH_SHORT).show()
    }
}
