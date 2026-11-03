package ai.pleos.plugrest.ui

import ai.pleos.plugrest.R
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** docs/design/tokens.md 그대로. 다크 고정. */
object C {
    val bg = Color(0xFF1D1E21)
    val bgRest = Color(0xFF000000)
    val surface = Color(0xFF313236)
    val surfaceRest = Color(0xFF141518)
    val textPrimary = Color(0xFFFFFFFF)
    val textSecondary = Color(0xFFB4B5B8)
    val textTertiary = Color(0xFF8A8B90) // bg 위에서만
    val bgSunrise = Color(0xFF241C14)    // Resting 선라이즈 배경 (v0.7) — 이 위에선 restText만
    val segTrack = Color(0xFF131417)     // 세그먼트 컨트롤 트랙 (v0.7)
    val primary = Color(0xFF0064FF)
    val charge = Color(0xFF43E0A0)
    val warn = Color(0xFFFFC75A)
    val restText = Color(0xFFB4B5B8)
    val restTextDim = Color(0xFF7A7B80)
    val restCharge = Color(0xFF2B8F67)
    val track = Color(0xFF26272B)
    val disabledBg = Color(0xFF2A2B2F)
    val disabledText = Color(0xFF6B6C70)
}

// 가변 폰트(wght 축): 굵기를 variationSettings로 명시해야 Bold/ExtraBold가 그려짐
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun asta(weight: FontWeight) =
    Font(R.font.asta_sans, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))

val AstaSans = FontFamily(asta(FontWeight.Normal), asta(FontWeight.Bold), asta(FontWeight.ExtraBold))

private fun style(size: Int, line: Int, weight: FontWeight, tnum: Boolean = false) = TextStyle(
    fontFamily = AstaSans, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight,
    fontFeatureSettings = if (tnum) "tnum" else null,
)

object T {
    val displayXL = style(160, 168, FontWeight.ExtraBold, tnum = true)
    val displayL = style(112, 120, FontWeight.Bold, tnum = true)
    val headlineL = style(60, 80, FontWeight.ExtraBold)
    val titleL = style(40, 52, FontWeight.Bold)
    val titleS = style(32, 40, FontWeight.Bold)
    val bodyL = style(30, 38, FontWeight.Normal)
    val bodyS = style(26, 34, FontWeight.Normal)
    val labelM = style(24, 28, FontWeight.Normal)
    val segment = style(28, 32, FontWeight.Bold) // 세그먼트 컨트롤 (v0.7)
    val labelS = style(20, 24, FontWeight.Normal) // "학습 기록 지우기" 텍스트 버튼, "실측" 태그 (v0.6)
}

object D {
    val buttonL = 100.dp; val radiusL = 32.dp
    val buttonM = 80.dp; val radiusM = 24.dp
    val cardRadius = 24.dp
    val screenPad = 48.dp
    val section = 48.dp
    val gap = 24.dp
    val cardPad = 32.dp
    val ringStroke = 28.dp
}
