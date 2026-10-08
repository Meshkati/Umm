package net.meshkati.umm

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Chart and legend color for "Opened anyway"; "Turned back" uses the theme's primary. */
val OpenedColor = Color(0xFFC8741F)

/** Top app bar; with [onBack] it shows a back arrow before the title. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UmmTopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.back))
                }
            }
        },
        actions = actions,
    )
}

private val placeholderColors = listOf(
    Color(0xFF8A4F7D), Color(0xFF3F6CB5), Color(0xFF3B7A57), Color(0xFFB5683F), Color(0xFF5B5F97),
)

/** The app's launcher icon, or its first letter on a colored square when it isn't installed any more. */
@Composable
fun AppIcon(packageName: String, app: AppEntry?, size: Dp) {
    if (app != null) {
        Image(bitmap = app.icon, contentDescription = null, modifier = Modifier.size(size))
        return
    }
    val color = placeholderColors[Math.floorMod(packageName.hashCode(), placeholderColors.size)]
    Box(
        modifier = Modifier.size(size).background(color, RoundedCornerShape(size / 4)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            packageName.substringAfterLast('.').take(1).uppercase(),
            color = Color.White,
            fontSize = (size.value * 0.42f).sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
