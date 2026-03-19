package mx.ita.vitalsense.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import mx.ita.vitalsense.ui.navigation.Route

@Composable
fun GlobalBottomNavigationBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .height(72.dp),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF1169FF), // PrimaryBlue
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val icons = listOf(
                Route.DASHBOARD to Icons.Rounded.Home,
                Route.DAILY_REPORT to Icons.Rounded.FavoriteBorder,
                Route.CHAT to Icons.Rounded.ChatBubbleOutline,
                Route.PROFILE to Icons.Rounded.PersonOutline
            )
            
            icons.forEach { (route, icon) ->
                val isSelected = currentRoute == route
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onNavigate(route) }
                ) {
                    Icon(
                        icon, 
                        contentDescription = null, 
                        tint = Color.White, 
                        modifier = Modifier.size(28.dp)
                    )
                    if (isSelected) {
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier
                                .size(width = 16.dp, height = 2.dp)
                                .background(Color.White)
                        )
                    }
                }
            }
        }
    }
}
