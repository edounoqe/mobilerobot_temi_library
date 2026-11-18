package kr.kw.libri.chamchamcham

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kr.kw.libri.common.HandArt
import kr.kw.libri.common.LibriColors
import kr.kw.libri.common.bakeHandArt
import kr.kw.libri.common.drawHand
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 참참참 손 — 엄지를 위로 세운 손날이 손목을 축으로 왼쪽·오른쪽을 가리킨다.
 *  · 내가 공격할 때(mine = true): 내가 손을 내민 것처럼 팔이 화면 아래에서 올라온다.
 *  · 내가 수비할 때(mine = false): 상대가 손을 내민 것처럼 팔이 화면 위(상대편)에서 내려온다.
 * 그림은 연필 선에 색연필로 칠한 손(common/Doodles 의 HandArt)을 한 번 그려 두고, 프레임마다 돌려 찍기만 한다.
 *  rotation: 0 = 정면, 음수 = 화면 왼쪽, 양수 = 화면 오른쪽
 *  bounce  : 0~1, "참" 박자에 손이 까딱하는 정도
 *  sleeve  : 소매 색 (손 주인의 자리 색)
 *  topInset: 상대 손이 내려올 때 비워 둘 화면 위쪽 높이 (상단 띠)
 */
@Composable
fun ChamHand(
    rotation: Float,
    bounce: Float,
    modifier: Modifier = Modifier,
    mine: Boolean = true,
    sleeve: Color = LibriColors.Coral,
    topInset: Dp = 0.dp,
    alpha: Float = 1f,
) {
    val rot = rememberUpdatedState(rotation)
    val bob = rememberUpdatedState(bounce)
    val fade = rememberUpdatedState(alpha)
    Spacer(
        modifier.drawWithCache {
            // 손 길이(약 7)와 팔이 화면에 들어오도록 — 세로로 긴 화면에서는 폭이 기준이 된다
            val k = min(size.height / 14f, size.width / 9.5f)
            val art: HandArt = bakeHandArt(k, density, sleeve)
            val inset = topInset.toPx()
            onDrawBehind {
                // 내 손은 아래에서 위로, 상대 손은 위에서 아래로 뻗는다 — 까딱일 때는 손이 뻗은 쪽의 반대로 살짝 물러난다
                val wrist = if (mine) {
                    Offset(size.width / 2f, size.height - 3f * k + bob.value * 0.75f * k)
                } else {
                    Offset(size.width / 2f, inset + 2f * k - bob.value * 0.75f * k)
                }
                val r = rot.value
                // 손목이 돌아갈 때 손끝이 지나간 자리에 연필로 그은 바람 선
                if (abs(r) > 2f) {
                    val rest = if (mine) -90f else 90f                  // 정면을 가리킬 때의 각도 (화면 기준, 0° = 오른쪽)
                    val turn = if (mine) r else -r                      // 그 각도에서 돌아간 만큼
                    val dir = if (turn < 0f) -1f else 1f
                    val strength = (abs(r) / HAND_TURN_DEG).coerceIn(0f, 1f)
                    for (i in 0 until 3) {
                        val radius = (4.6f + i * 0.95f) * k
                        val a0 = rest + dir * (4f + i * 3f)
                        val a1 = rest + turn * (0.72f - i * 0.1f)
                        var prev = Offset.Unspecified
                        for (s in 0..8) {
                            val a = ((a0 + (a1 - a0) * s / 8f) * PI / 180.0).toFloat()
                            val p = Offset(wrist.x + cos(a) * radius, wrist.y + sin(a) * radius)
                            if (prev != Offset.Unspecified) {
                                drawLine(
                                    LibriColors.Pencil.copy(alpha = 0.5f * strength * fade.value), prev, p,
                                    strokeWidth = (2.6f - i * 0.5f) * density, cap = StrokeCap.Round,
                                )
                            }
                            prev = p
                        }
                    }
                }
                drawHand(art, wrist, r, fade.value, fromTop = !mine)
            }
        }
    )
}
