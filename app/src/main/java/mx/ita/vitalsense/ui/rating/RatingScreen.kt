package mx.ita.vitalsense.ui.rating

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import mx.ita.vitalsense.R
import mx.ita.vitalsense.ui.common.LockLandscapeOrientation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RatingScreen(onBack: () -> Unit) {
    LockLandscapeOrientation()
    val context = LocalContext.current
    var rating by remember { mutableFloatStateOf(4f) }
    var comment by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.rating_title), fontWeight = FontWeight.Bold) },
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.rating_subtitle), style = MaterialTheme.typography.bodyLarge)

            RowStars(rating = rating.roundToInt())

            Slider(
                value = rating,
                onValueChange = { rating = it },
                valueRange = 1f..5f,
                steps = 3,
            )

            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.rating_comment_label)) },
                minLines = 3,
            )

            Button(
                onClick = {
                    openPlayStore(context)
                    Toast.makeText(context, context.getString(R.string.rating_thanks), Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.rating_send))
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text("$rating / 5", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun RowStars(rating: Int) {
    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(5) { index ->
            Icon(
                imageVector = Icons.Rounded.Star,
                contentDescription = null,
                tint = if (index < rating) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            )
        }
    }
}

private fun openPlayStore(context: Context) {
    val packageName = context.packageName
    val playIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))
    try {
        context.startActivity(playIntent)
    } catch (_: Exception) {
        context.startActivity(webIntent)
    }
}
