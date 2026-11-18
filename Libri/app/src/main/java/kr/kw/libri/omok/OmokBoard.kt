package kr.kw.libri.omok

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kr.kw.libri.common.LibriColors
import kr.kw.libri.common.Sketch
import kr.kw.libri.common.SketchSurface
import kr.kw.libri.common.bake
import kr.kw.libri.common.penciled
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

/** 색연필로 칠한 나무판 */
internal val WOOD = Color(0xFFF5DCA6)
private val WOOD_GRAIN = Color(0xFFD9A441)

private const val EDGE = 0.058f     // 판 가장자리에서 첫 줄까지 (판 한 변에 대한 비율)

/** 바둑돌 한 알 — 연필로 그린 동그라미. 비트맵 가운데가 돌의 중심이다 */
private fun bakeStone(r: Float, stone: Int, u: Float): ImageBitmap {
    val m = ceil(3f * u)
    val d = ceil(2f * (r + m)).toInt()
    val c = Offset(d / 2f, d / 2f)
    return bake(d, d) {
        if (stone == BLACK) {
            penciled(0.2f, 1.4f * u) {
                drawCircle(LibriColors.Ink, r - 0.5f * u, c)
                Sketch(this, 3, u).circle(c.x, c.y, r - 0.8f * u, LibriColors.Ink, width = 1.6f, rough = 0.5f)
            }
            // 윤기 — 왼쪽 위에 옅은 호
            drawArc(
                Color.White.copy(alpha = 0.34f), 205f, 55f, useCenter = false,
                topLeft = Offset(c.x - r * 0.62f, c.y - r * 0.62f), size = Size(r * 1.24f, r * 1.24f),
                style = Stroke(r * 0.15f, cap = StrokeCap.Round),
            )
        } else {
            drawCircle(LibriColors.Card, r - 0.5f * u, c)
            // 오른쪽 아래의 옅은 그늘
            drawArc(
                LibriColors.Faint.copy(alpha = 0.55f), 15f, 80f, useCenter = false,
                topLeft = Offset(c.x - r * 0.68f, c.y - r * 0.68f), size = Size(r * 1.36f, r * 1.36f),
                style = Stroke(r * 0.16f, cap = StrokeCap.Round),
            )
            penciled(0.3f, 1.3f * u) {
                Sketch(this, 4, u).circle(c.x, c.y, r - 0.9f * u, LibriColors.Ink, width = 2f, rough = 0.5f)
            }
        }
    }
}

/**
 * 오목판 — 나무판과 줄은 한 번만 그려 두고, 돌과 표시만 다시 찍는다.
 * @param ghost      놓아 볼 자리 (-1 = 없음) — 같은 자리를 한 번 더 누르면 실제로 놓는다
 * @param ghostStone 놓아 볼 돌
 * @param ghostOk    false 면 둘 수 없는 자리(삼삼)라는 표시를 한다
 * @param onTap      칸을 눌렀을 때 (칸 번호)
 */
@Composable
fun OmokBoard(
    view: GameView,
    ghost: Int,
    ghostStone: Int,
    ghostOk: Boolean,
    onTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shown = rememberUpdatedState(view)
    val ghostAt = rememberUpdatedState(ghost)
    val ghostIs = rememberUpdatedState(ghostStone)
    val ghostFine = rememberUpdatedState(ghostOk)
    val tap = rememberUpdatedState(onTap)

    // 방금 놓인 돌이 '딱' 하고 내려앉는다
    val pop = remember { Animatable(1f) }
    LaunchedEffect(view.count) {
        if (view.count > 0) {
            pop.snapTo(1.4f)
            pop.animateTo(1f, tween(170))
        }
    }
    val pulse = rememberInfiniteTransition(label = "ghost").animateFloat(
        0f, 1f, infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse), label = "pulse",
    )

    SketchSurface(
        modifier = modifier.aspectRatio(1f),
        fill = WOOD, strokeWidth = 3f, radius = 18.dp, shadow = 7.dp, seed = 15, fillContent = true,
    ) {
        Spacer(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { p ->
                        val s = min(size.width, size.height).toFloat()
                        val pad = s * EDGE
                        val step = (s - 2f * pad) / (BOARD - 1)
                        val x = ((p.x - pad) / step).roundToInt()
                        val y = ((p.y - pad) / step).roundToInt()
                        if (x in 0 until BOARD && y in 0 until BOARD) tap.value(cellOf(x, y))
                    }
                }
                .drawWithCache {
                    val s = min(size.width, size.height)
                    val u = density
                    val pad = s * EDGE
                    val step = (s - 2f * pad) / (BOARD - 1)
                    val grid = bake(ceil(s).toInt(), ceil(s).toInt()) {
                        // 나뭇결 — 옅은 색연필 줄
                        penciled(0.55f, 1.7f * u) {
                            val inset = 7f * u
                            Sketch(this, 15, u).hatch(
                                Path().apply { addRect(Rect(inset, inset, s - inset, s - inset)) },
                                WOOD_GRAIN.copy(alpha = 0.4f), angle = -7f, gap = 6.5f, width = 2.4f,
                            )
                        }
                        // 줄 — 가장자리 줄은 조금 굵게
                        penciled(0.3f, 1.3f * u) {
                            val sk = Sketch(this, 16, u)
                            val ink = LibriColors.Ink.copy(alpha = 0.82f)
                            for (i in 0 until BOARD) {
                                val p = pad + i * step
                                val w = if (i == 0 || i == BOARD - 1) 2.1f else 1.3f
                                sk.line(pad, p, s - pad, p, ink, w, rough = 0.45f, passes = 1)
                                sk.line(p, pad, p, s - pad, ink, w, rough = 0.45f, passes = 1)
                            }
                        }
                        // 화점
                        for (y in intArrayOf(3, 7, 11)) for (x in intArrayOf(3, 7, 11)) {
                            if ((x == 7) != (y == 7)) continue
                            drawCircle(LibriColors.Ink, step * 0.11f, Offset(pad + x * step, pad + y * step))
                        }
                    }
                    val r = step * 0.46f
                    val black = bakeStone(r, BLACK, u)
                    val white = bakeStone(r, WHITE, u)
                    val half = Offset(black.width / 2f, black.height / 2f)

                    onDrawBehind {
                        val v = shown.value
                        fun at(cell: Int) = Offset(pad + (cell % BOARD) * step, pad + (cell / BOARD) * step)
                        drawImage(grid)

                        // 이긴 줄 — 돌 아래에 형광펜
                        val line = v.line
                        if (line != null) {
                            drawLine(LibriColors.Yellow, at(line.first()), at(line.last()), strokeWidth = step * 0.95f, cap = StrokeCap.Round)
                        }

                        for (cell in 0 until CELLS) {
                            val stone = v.cells[cell]
                            if (stone == EMPTY) continue
                            val p = at(cell)
                            val bmp = if (stone == BLACK) black else white
                            if (cell == v.last && pop.value != 1f) {
                                scale(pop.value, p) { drawImage(bmp, p - half) }
                            } else {
                                drawImage(bmp, p - half)
                            }
                        }

                        // 마지막에 놓인 돌
                        if (v.last >= 0) drawCircle(LibriColors.Coral, step * 0.13f, at(v.last))
                        if (line != null) {
                            for (cell in line) drawCircle(LibriColors.Coral, r * 1.02f, at(cell), style = Stroke(2.6f * u))
                        }

                        // 놓아 볼 자리
                        val g = ghostAt.value
                        if (g in 0 until CELLS && v.cells[g] == EMPTY) {
                            val p = at(g)
                            val fine = ghostFine.value
                            drawImage(if (ghostIs.value == BLACK) black else white, p - half, alpha = if (fine) 0.5f else 0.25f)
                            val ring = if (fine) LibriColors.Mint else LibriColors.Stamp
                            drawCircle(ring, r * (1.12f + 0.1f * pulse.value), p, style = Stroke(2.8f * u))
                            if (!fine) {
                                val k = r * 0.5f
                                drawLine(ring, Offset(p.x - k, p.y - k), Offset(p.x + k, p.y + k), strokeWidth = 3f * u, cap = StrokeCap.Round)
                                drawLine(ring, Offset(p.x - k, p.y + k), Offset(p.x + k, p.y - k), strokeWidth = 3f * u, cap = StrokeCap.Round)
                            }
                        }
                    }
                },
        )
    }
}
