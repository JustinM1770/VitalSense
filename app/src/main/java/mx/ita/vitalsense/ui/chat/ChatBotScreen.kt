package mx.ita.vitalsense.ui.chat

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
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

@Composable
fun ChatBotScreen(
    onBack: () -> Unit,
    vm: ChatViewModel = viewModel()
) {
    val messages by vm.messages.collectAsState()
    val isTyping by vm.isTyping.collectAsState()
    
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        containerColor = Color.White,
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
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Regresar", tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text = "Chat Bot AI",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0F172A)
        )
    }
}

@Composable
private fun ChatBubble(msg: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (msg.isUser) Color(0xFF1169FF) else Color(0xFFF2F4F7),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                text = msg.text,
                color = if (msg.isUser) Color.White else Color(0xFF334155),
                fontSize = 15.sp,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Composable
private fun TypingIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFFF2F4F7)
        ) {
            Text(
                text = "...",
                color = Color(0xFF334155),
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
    onSend: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        color = Color.White
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Fila de Texto y Botón Send
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (text.isEmpty()) {
                        Text("¿En qué puedo ayudarte?", color = Color(0xFFA0AEC0), fontSize = 15.sp)
                    }
                    BasicTextField(
                        value = text,
                        onValueChange = onTextChange,
                        textStyle = TextStyle(color = Color(0xFF334155), fontSize = 15.sp),
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
                        contentDescription = "Enviar",
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
                Icon(Icons.Rounded.Image, contentDescription = null, tint = Color(0xFF475569), modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(24.dp))
                Icon(Icons.Rounded.Code, contentDescription = null, tint = Color(0xFF475569), modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(24.dp))
                Icon(Icons.Rounded.Mic, contentDescription = null, tint = Color(0xFF475569), modifier = Modifier.size(24.dp))
            }
        }
    }
}
