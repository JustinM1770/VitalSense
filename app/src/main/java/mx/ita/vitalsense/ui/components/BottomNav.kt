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
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(72.dp),
        shape = RoundedCornerShape(24.dp),
        color = DashBlue,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(72.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                NavItem(
                    icon = Icons.Outlined.Home,
                    label = "Inicio",
                    selected = selected == BottomNavTab.HOME,
                    onClick = { onSelect(BottomNavTab.HOME) },
                )
            }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                NavItem(
                    icon = Icons.Outlined.FavoriteBorder,
                    label = "Salud",
                    selected = selected == BottomNavTab.HEALTH,
                    onClick = { onSelect(BottomNavTab.HEALTH) },
                )
            }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                NavItem(
                    icon = Icons.Outlined.ChatBubbleOutline,
                    label = "Chat",
                    selected = selected == BottomNavTab.CHAT,
                    onClick = { onSelect(BottomNavTab.CHAT) },
                )
            }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                NavItem(
                    icon = Icons.Outlined.PersonOutline,
                    label = "Perfil",
                    selected = selected == BottomNavTab.PROFILE,
                    onClick = { onSelect(BottomNavTab.PROFILE) },
                )
            }
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
            .padding(horizontal = 16.dp, vertical = 8.dp),
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
                    .padding(top = 4.dp)
                    .size(width = 16.dp, height = 2.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White),
            )
        }
    }
}
