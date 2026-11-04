package kr.kw.libri.chamchamcham

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate

/**
 * 참참참 손 — 화면 아래에서 팔이 올라오고, 검지를 위로 편 손이 손목을 축으로 회전한다.
 *  rotation: 0 = 위, 음수 = 화면 왼쪽, 양수 = 화면 오른쪽
 *  bounce  : 0~1, "참" 박자에 손이 아래로 까딱하는 정도
 */
@Composable
fun ChamHand(
    rotation: Float,
    bounce: Float,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
) {
    Canvas(modifier) {
        val s = size.minDimension / 13f
        val cx = size.width / 2f
        val wristY = size.height - 3f * s + bounce * 0.8f * s   // 손목(회전축)
        val skin = Color(0xFFF1C27D).copy(alpha = alpha)
        val shade = Color(0xFFDDA35F).copy(alpha = alpha)
        val line = Color(0xFF8D5A2B).copy(alpha = alpha)
        val sleeve = Color(0xFF37474F).copy(alpha = alpha)
        val outline = Stroke(width = s * 0.08f)

        // 팔뚝 + 소매 — 회전하지 않는다
        drawRoundRect(
            skin, topLeft = Offset(cx - 1.1f * s, wristY - 0.3f * s),
            size = Size(2.2f * s, size.height - wristY + s), cornerRadius = CornerRadius(0.6f * s),
        )
        drawRect(sleeve, topLeft = Offset(cx - 1.6f * s, wristY + 1.4f * s), size = Size(3.2f * s, size.height))

        // 손 — 손목을 축으로 회전
        rotate(degrees = rotation, pivot = Offset(cx, wristY)) {
            // 손바닥
            val palmTL = Offset(cx - 1.6f * s, wristY - 3.2f * s)
            val palmSize = Size(3.2f * s, 3.5f * s)
            drawRoundRect(skin, topLeft = palmTL, size = palmSize, cornerRadius = CornerRadius(0.9f * s))
            drawRoundRect(line, topLeft = palmTL, size = palmSize, cornerRadius = CornerRadius(0.9f * s), style = outline)

            // 접은 손가락 3개
            for (i in 0 until 3) {
                val tl = Offset(cx - 0.45f * s + i * 0.72f * s, wristY - 3.75f * s)
                val sz = Size(0.68f * s, 1.3f * s)
                drawRoundRect(shade, topLeft = tl, size = sz, cornerRadius = CornerRadius(0.34f * s))
                drawRoundRect(line, topLeft = tl, size = sz, cornerRadius = CornerRadius(0.34f * s), style = outline)
            }

            // 검지 — 펴서 위를 가리킨다
            val idxTL = Offset(cx - 1.45f * s, wristY - 6.8f * s)
            val idxSize = Size(0.85f * s, 4.2f * s)
            drawRoundRect(skin, topLeft = idxTL, size = idxSize, cornerRadius = CornerRadius(0.42f * s))
            drawRoundRect(line, topLeft = idxTL, size = idxSize, cornerRadius = CornerRadius(0.42f * s), style = outline)
            drawRoundRect(
                Color.White.copy(alpha = 0.55f * alpha), topLeft = Offset(cx - 1.3f * s, wristY - 6.6f * s),
                size = Size(0.55f * s, 0.65f * s), cornerRadius = CornerRadius(0.25f * s),
            )

            // 엄지
            rotate(degrees = -35f, pivot = Offset(cx + 1.4f * s, wristY - 1.2f * s)) {
                val thTL = Offset(cx + 1.0f * s, wristY - 2.7f * s)
                val thSize = Size(0.8f * s, 2.0f * s)
                drawRoundRect(skin, topLeft = thTL, size = thSize, cornerRadius = CornerRadius(0.4f * s))
                drawRoundRect(line, topLeft = thTL, size = thSize, cornerRadius = CornerRadius(0.4f * s), style = outline)
            }
        }
    }
}
