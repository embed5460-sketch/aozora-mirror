package app.meisaku.reader.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.meisaku.reader.ui.theme.avatarColor

/** 作家頭像：姓の一文字を彩色した円に。色は ID から決定的に選ぶ。 */
@Composable
fun AuthorAvatar(
    name: String,
    seed: String,
    size: Int = 44,
    modifier: Modifier = Modifier,
) {
    val color = avatarColor(seed)
    val initial = name.firstOrNull()?.toString() ?: "?"
    Surface(
        modifier = modifier.size(size.dp),
        shape = CircleShape,
        color = color,
        shadowElevation = 2.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = initial,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = (size * 0.42f).sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}
