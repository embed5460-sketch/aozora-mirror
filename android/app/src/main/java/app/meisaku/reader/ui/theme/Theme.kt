package app.meisaku.reader.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// --- 白天：暖纸色，多级表面分层 ---
private val LightColors = lightColorScheme(
    primary = Color(0xFF8A5A44),            // テラコッタ茶（強調）
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF3DDCF),
    onPrimaryContainer = Color(0xFF3A1D11),
    secondary = Color(0xFF6F5B4E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEEE0D2),
    onSecondaryContainer = Color(0xFF463528),
    background = Color(0xFFF7F1E7),         // 生成り紙
    onBackground = Color(0xFF2A2420),
    surface = Color(0xFFFCF8F1),
    onSurface = Color(0xFF2A2420),
    surfaceVariant = Color(0xFFEAE0D2),
    onSurfaceVariant = Color(0xFF7C6F60),   // 副文字（読み/ローマ字）
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFDFAF4),
    surfaceContainer = Color(0xFFFFFFFF),   // カード面（背景より明るく＝浮き）
    surfaceContainerHigh = Color(0xFFF4ECE0),
    surfaceContainerHighest = Color(0xFFEEE5D7),
    outline = Color(0xFFCFC2B1),
    outlineVariant = Color(0xFFE3D8C8),
)

// --- 夜間：柔らかい暖い暗色（純黒を避ける）、カード面を一段明るく ---
private val DarkColors = darkColorScheme(
    primary = Color(0xFFE6C9A8),            // 暖いベージュ強調
    onPrimary = Color(0xFF402712),
    primaryContainer = Color(0xFF5C4228),
    onPrimaryContainer = Color(0xFFF3DDCF),
    secondary = Color(0xFFD2C0AE),
    onSecondary = Color(0xFF382C20),
    secondaryContainer = Color(0xFF4D3F32),
    onSecondaryContainer = Color(0xFFEEE0D2),
    background = Color(0xFF1A1714),         // 柔らかい暖黒
    onBackground = Color(0xFFE6DCCC),       // 純白でなく生成り
    surface = Color(0xFF211D19),
    onSurface = Color(0xFFE6DCCC),
    surfaceVariant = Color(0xFF3A342C),
    onSurfaceVariant = Color(0xFFB9AD9C),   // 副文字（やや暗く）
    surfaceContainerLowest = Color(0xFF14110E),
    surfaceContainerLow = Color(0xFF1F1B17),
    surfaceContainer = Color(0xFF26221C),   // カード面（背景より明るく＝浮き）
    surfaceContainerHigh = Color(0xFF312B23),
    surfaceContainerHighest = Color(0xFF3C352B),
    outline = Color(0xFF6B6052),
    outlineVariant = Color(0xFF453E34),
)

/** 作家頭像など装飾用の落ち着いた配色。明暗どちらでも視認可。 */
val AvatarPalette = listOf(
    Color(0xFFB5654A), // terracotta
    Color(0xFF6E8B6E), // sage
    Color(0xFF5E7A99), // slate blue
    Color(0xFF9A6B8B), // plum
    Color(0xFFB08A4F), // mustard
    Color(0xFF7A6FA3), // muted violet
    Color(0xFF4F8B86), // teal
    Color(0xFFB07258), // clay
)

fun avatarColor(seed: String): Color {
    val i = (seed.hashCode() % AvatarPalette.size + AvatarPalette.size) % AvatarPalette.size
    return AvatarPalette[i]
}

@Composable
fun MeisakuTheme(
    dark: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = Typography(),
        content = content,
    )
}
