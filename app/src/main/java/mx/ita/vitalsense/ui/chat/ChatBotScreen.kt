package mx.ita.vitalsense.ui.chat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import mx.ita.vitalsense.R
import java.io.File
import kotlinx.coroutines.launch

@Composable
fun ChatBotScreen(
    onBack: () -> Unit,
    vm: ChatViewModel = viewModel()
) {
    val context = LocalContext.current
    val messages by vm.messages.collectAsState()
    val isTyping by vm.isTyping.collectAsState()

    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }
    var showImageMenu by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordingPath by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val localUri = copyUriToCache(context, uri, "chat_image_${System.currentTimeMillis()}.jpg")
        if (localUri != null) {
            vm.sendImageMessage(localUri.toString())
        } else {
            Toast.makeText(context, context.getString(R.string.chat_attach_image_error), Toast.LENGTH_LONG).show()
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            val started = startAudioRecording(context)
            if (started != null) {
                recorder = started.first
                recordingPath = started.second
                isRecording = true
                Toast.makeText(context, context.getString(R.string.chat_recording_audio), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, context.getString(R.string.chat_recording_start_error), Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(context, context.getString(R.string.chat_mic_permission_denied), Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(messages.size, isTyping) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ChatTopBar(onBack = onBack)
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Mensajes
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { Spacer(Modifier.height(16.dp)) }
                
                items(messages) { msg ->
                    ChatBubble(msg)
                }
                
                if (isTyping) {
                    item {
                        TypingIndicator()
                    }
                }
                
                item { Spacer(Modifier.height(16.dp)) }
            }

            // Input Area
            ChatInputArea(
                text = inputText,
                onTextChange = { inputText = it },
                showImageMenu = showImageMenu,
                onToggleImageMenu = { showImageMenu = !showImageMenu },
                onPickImage = {
                    showImageMenu = false
                    imagePicker.launch("image/*")
                },
                isRecording = isRecording,
                onAudioClick = {
                    if (!isRecording) {
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO,
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            val started = startAudioRecording(context)
                            if (started != null) {
                                recorder = started.first
                                recordingPath = started.second
                                isRecording = true
                                Toast.makeText(context, context.getString(R.string.chat_recording_audio), Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, context.getString(R.string.chat_recording_start_error), Toast.LENGTH_LONG).show()
                            }
                        } else {
                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    } else {
                        val path = recordingPath
                        stopAudioRecording(recorder)
                        recorder = null
                        isRecording = false
                        recordingPath = null

                        if (!path.isNullOrBlank()) {
                            vm.sendAudioMessage(Uri.fromFile(File(path)).toString())
                            Toast.makeText(context, context.getString(R.string.chat_audio_sent), Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, context.getString(R.string.chat_audio_not_found), Toast.LENGTH_LONG).show()
                        }
                    }
                },
                onSend = {
                    if (inputText.isNotBlank()) {
                        vm.sendMessage(inputText)
                        inputText = ""
                        coroutineScope.launch {
                            listState.animateScrollToItem(messages.size)
                        }
                    }
                }
            )
            
            Spacer(Modifier.height(96.dp)) // Espacio para el Bottom Navigation Global
        }
    }
}

@Composable
private fun ChatTopBar(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(0xFF1169FF))
                .clickable { onBack() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back), tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text = stringResource(R.string.chat_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = scheme.onBackground
        )
    }
}

@Composable
private fun ChatBubble(msg: ChatMessage) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (msg.isUser) Color(0xFF1169FF) else scheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            when (msg.type) {
                ChatMessageType.TEXT -> {
                    Text(
                        text = msg.text,
                        color = if (msg.isUser) Color.White else scheme.onSurfaceVariant,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(16.dp),
                    )
                }

                ChatMessageType.IMAGE -> {
                    Column(modifier = Modifier.padding(10.dp)) {
                        AsyncImage(
                            model = msg.mediaUri,
                            contentDescription = stringResource(R.string.chat_image_content_description),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(170.dp)
                                .clip(RoundedCornerShape(10.dp)),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = msg.text,
                            color = if (msg.isUser) Color.White else scheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 6.dp),
                        )
                    }
                }

                ChatMessageType.AUDIO -> {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                            .clickable {
                                runCatching {
                                    val player = MediaPlayer.create(context, Uri.parse(msg.mediaUri ?: ""))
                                    player?.setOnCompletionListener { it.release() }
                                    player?.start()
                                }
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(R.string.chat_play_audio),
                            tint = if (msg.isUser) Color.White else scheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = msg.text,
                            color = if (msg.isUser) Color.White else scheme.onSurfaceVariant,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TypingIndicator() {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = scheme.surfaceVariant
        ) {
            Text(
                text = "...",
                color = scheme.onSurfaceVariant,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun ChatInputArea(
    text: String,
    onTextChange: (String) -> Unit,
    showImageMenu: Boolean,
    onToggleImageMenu: () -> Unit,
    onPickImage: () -> Unit,
    isRecording: Boolean,
    onAudioClick: () -> Unit,
    onSend: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .border(1.dp, scheme.outline.copy(alpha = 0.35f), RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        color = scheme.surface
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Fila de Texto y Botón Send
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (text.isEmpty()) {
                        Text(stringResource(R.string.chat_input_placeholder), color = scheme.onSurfaceVariant.copy(alpha = 0.8f), fontSize = 15.sp)
                    }
                    BasicTextField(
                        value = text,
                        onValueChange = onTextChange,
                        textStyle = TextStyle(color = scheme.onSurface, fontSize = 15.sp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1169FF))
                        .clickable { onSend() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.Send,
                        contentDescription = stringResource(R.string.chat_send),
                        tint = Color.White,
                        modifier = Modifier.size(20.dp).offset(x = 2.dp)
                    )
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            // Fila de Íconos Inferiores
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    Icon(
                        Icons.Rounded.Image,
                        contentDescription = stringResource(R.string.chat_upload_image),
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable { onToggleImageMenu() },
                    )

                    DropdownMenu(
                        expanded = showImageMenu,
                        onDismissRequest = onToggleImageMenu,
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.chat_upload_image)) },
                            onClick = onPickImage,
                        )
                    }
                }

                Spacer(Modifier.width(24.dp))

                Icon(
                    Icons.Rounded.Mic,
                    contentDescription = stringResource(R.string.chat_record_audio),
                    tint = if (isRecording) Color(0xFFD32F2F) else scheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { onAudioClick() },
                )
            }
        }
    }
}

private fun copyUriToCache(context: android.content.Context, source: Uri, fileName: String): Uri? {
    return runCatching {
        val directory = File(context.cacheDir, "chat_media")
        if (!directory.exists()) directory.mkdirs()
        val target = File(directory, fileName)
        context.contentResolver.openInputStream(source).use { input ->
            if (input == null) return null
            target.outputStream().use { output -> input.copyTo(output) }
        }
        Uri.fromFile(target)
    }.getOrNull()
}

private fun startAudioRecording(context: android.content.Context): Pair<MediaRecorder, String>? {
    return runCatching {
        val directory = File(context.cacheDir, "chat_media")
        if (!directory.exists()) directory.mkdirs()
        val outputFile = File(directory, "chat_audio_${System.currentTimeMillis()}.m4a")

        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        recorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(outputFile.absolutePath)
            prepare()
            start()
        }

        recorder to outputFile.absolutePath
    }.getOrNull()
}

private fun stopAudioRecording(recorder: MediaRecorder?) {
    if (recorder == null) return
    runCatching {
        recorder.stop()
    }
    runCatching {
        recorder.reset()
    }
    runCatching {
        recorder.release()
    }
}
