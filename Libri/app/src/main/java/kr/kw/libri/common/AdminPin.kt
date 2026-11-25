package kr.kw.libri.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * 관리자 확인 — '로봇 설정' 처럼 방문객이 건드리면 안 되는 화면 앞에 숫자 비밀번호를 묻는다.
 * temi 에서 시스템 키보드 없이 누를 수 있게 화면 안에 숫자판을 그린다.
 */

/** 관리자 비밀번호 (임시) — 바꾸려면 이 값만 고치면 된다 */
const val ADMIN_PIN = "1234"

/**
 * 비밀번호 입력 팝업. 맞으면 [onSuccess], 닫거나 바깥을 누르면 [onDismiss].
 * 틀리면 점이 흔들리고 지워진다.
 */
@Composable
fun AdminPinDialog(onSuccess: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    var entered by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    var fails by remember { mutableIntStateOf(0) }
    val shake = remember { Animatable(0f) }

    LaunchedEffect(fails) {
        if (fails == 0) return@LaunchedEffect
        shake.animateTo(0f, keyframes {
            durationMillis = 380
            -14f at 50
            14f at 120
            -9f at 190
            9f at 260
            -4f at 320
        })
        entered = ""
    }

    fun press(d: String) {
        if (entered.length >= ADMIN_PIN.length) return
        wrong = false
        entered += d
        if (entered.length == ADMIN_PIN.length) {
            if (entered == ADMIN_PIN) onSuccess() else { wrong = true; fails++ }
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        SketchCard(
            // 카드 안을 눌러도 닫히지 않게 클릭을 먹는다
            Modifier.width(380.dp).clickable(remember { MutableInteractionSource() }, indication = null) { },
            seed = 31,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TitleText("관리자 확인", size = 30.sp)
            NoteText(
                if (wrong) "비밀번호가 틀렸어요" else "비밀번호 ${ADMIN_PIN.length}자리를 눌러 주세요",
                size = 19.sp, color = if (wrong) LibriColors.Stamp else LibriColors.Pencil, align = TextAlign.Center,
            )
            // 입력한 자리 수만큼 채워지는 점
            Row(
                Modifier.padding(vertical = 6.dp).graphicsLayer { translationX = shake.value * density },
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                repeat(ADMIN_PIN.length) { i ->
                    Box(
                        Modifier
                            .size(20.dp)
                            .background(if (i < entered.length) (if (wrong) LibriColors.Stamp else LibriColors.Ink) else Color.Transparent, CircleShape)
                            .border(2.dp, if (wrong) LibriColors.Stamp else LibriColors.Ink, CircleShape),
                    )
                }
            }
            // 숫자판 3 × 4
            val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("닫기", "0", "지움"))
            for (row in rows) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (key in row) {
                        val digit = key.length == 1
                        LibriButton(
                            key,
                            {
                                when (key) {
                                    "닫기" -> onDismiss()
                                    "지움" -> { wrong = false; entered = entered.dropLast(1) }
                                    else -> press(key)
                                }
                            },
                            Modifier.weight(1f),
                            style = if (digit) LibriButtonStyle.PAPER else LibriButtonStyle.FLAT,
                            minHeight = 60.dp, fontSize = if (digit) 26 else 18,
                        )
                    }
                }
            }
        }
    }
}
