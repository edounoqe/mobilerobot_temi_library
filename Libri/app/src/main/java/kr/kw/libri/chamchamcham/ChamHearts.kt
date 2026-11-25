package kr.kw.libri.chamchamcham

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * 체력바 — 게임 속 도트 하트처럼 네모 칸으로 찍은 하트를 줄지어 보여 준다.
 * 하트를 잃으면 줄이 잠깐 흔들리고, 잃은 하트는 속이 빈 회색 하트로 남는다.
 */

/** 하트 모양 (7 × 7 칸). # = 하트 속, + = 테두리 */
private val HEART = listOf(
    ".++.++.",
    "+##+##+",
    "+#####+",
    "+#####+",
    ".+###+.",
    "..+#+..",
    "...+...",
)
private val HEART_W = HEART[0].length
private val HEART_H = HEART.size

private val OUTLINE = Color(0xFF1E1A17)
private val RED = Color(0xFFE5332A)
private val RED_DARK = Color(0xFFA81E19)
private val SHINE = Color(0xFFFFF4EE)
private val EMPTY = Color(0xFF4A4440)
private val EMPTY_DARK = Color(0xFF332E2B)

/** 도트 하트 하나 */
@Composable
fun PixelHeart(full: Boolean, modifier: Modifier = Modifier, size: Dp = 32.dp) {
    Canvas(modifier.size(size, size * HEART_H / HEART_W)) {
        val px = this.size.width / HEART_W
        for (y in 0 until HEART_H) for (x in 0 until HEART_W) {
            val c = when (HEART[y][x]) {
                '+' -> OUTLINE
                '#' -> when {
                    full && x == 1 && y == 1 -> SHINE                       // 왼쪽 위 반짝임
                    full && (y >= 4 || x == HEART_W - 2) -> RED_DARK        // 아래 · 오른쪽 그늘
                    full -> RED
                    y >= 4 -> EMPTY_DARK
                    else -> EMPTY
                }
                else -> null
            } ?: continue
            // 칸 사이가 비지 않게 살짝 겹쳐 찍는다
            drawRect(c, Offset(x * px, y * px), Size(px + 0.6f, px + 0.6f))
        }
    }
}

/**
 * 체력바 — 남은 하트 [hp] 개를 [max] 칸 중에 보여 준다.
 * 하트가 줄어들면 줄 전체가 좌우로 흔들린다.
 */
@Composable
fun HeartBar(hp: Int, modifier: Modifier = Modifier, max: Int = CHAM_MAX_HP, heartSize: Dp = 32.dp) {
    val shake = remember { Animatable(0f) }
    var last by remember { mutableIntStateOf(hp) }
    LaunchedEffect(hp) {
        if (hp < last) {
            shake.animateTo(0f, keyframes {
                durationMillis = 420
                -10f at 50
                10f at 120
                -7f at 190
                7f at 260
                -3f at 330
            })
        }
        last = hp
    }
    Row(
        modifier = modifier.graphicsLayer { translationX = shake.value * density },
        horizontalArrangement = Arrangement.spacedBy(heartSize / 6),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 0 until max) PixelHeart(full = i < hp, size = heartSize)
    }
}
