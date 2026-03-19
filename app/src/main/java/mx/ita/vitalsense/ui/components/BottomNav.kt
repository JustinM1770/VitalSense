package mx.ita.vitalsense.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.ita.vitalsense.ui.theme.DashBlue
import mx.ita.vitalsense.ui.theme.Manrope

enum class BottomNavTab { HOME, HEALTH, CHAT, PROFILE }

@Composable
fun BottomNav(
    selected: BottomNavTab,
    onSelect: (BottomNavTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(DashBlue)
            .navigationBarsPadding()
            .padding(vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            NavItem(
                icon = Icons.Outlined.Home,
                label = "Inicio",
                selected = selected == BottomNavTab.HOME,
                onClick = { onSelect(BottomNavTab.HOME) },
            )
            NavItem(
                icon = Icons.Outlined.FavoriteBorder,
                label = "Salud",
                selected = selected == BottomNavTab.HEALTH,
                onClick = { onSelect(BottomNavTab.HEALTH) },
            )
            NavItem(
                icon = Icons.Outlined.ChatBubbleOutline,
                label = "Chat",
                selected = selected == BottomNavTab.CHAT,
                onClick = { onSelect(BottomNavTab.CHAT) },
            )
            NavItem(
                icon = Icons.Outlined.PersonOutline,
                label = "Perfil",
                selected = selected == BottomNavTab.PROFILE,
                onClick = { onSelect(BottomNavTab.PROFILE) },
            )
        }
    }
}

@Composable
private fun NavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.height(4.dp))
        if (selected) {
            Box(
                modifier = Modifier
                    .size(width = 20.dp, height = 3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White),
            )
        } else {
            Text(
                text = label,
                fontFamily = Manrope,
                fontWeight = FontWeight.Normal,
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.7f),
            )
        }
    }
}
