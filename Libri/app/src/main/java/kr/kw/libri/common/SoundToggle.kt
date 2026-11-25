package kr.kw.libri.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.kw.libri.R

/*
 * 소리 켜기/끄기 버튼 — 글자만 바뀌면 지금 상태가 눈에 잘 안 띄어서,
 * 스피커 아이콘과 버튼 색으로도 상태를 보여 준다.
 *  · 켜짐: 흰 종이 버튼 + 검은 스피커(volume_up)
 *  · 꺼짐: 빨간 테두리 · 옅은 빨간 바탕 + 빨간 음소거 스피커(volume_off)
 */

/** 스피커 아이콘 (Material Icons volume_up / volume_off) — 꺼지면 빨간 음소거 표시 */
@Composable
fun SpeakerIcon(on: Boolean, modifier: Modifier = Modifier, size: Dp = 26.dp, color: Color = LibriColors.Ink) {
    Icon(
        painter = painterResource(if (on) R.drawable.ic_volume_up else R.drawable.ic_volume_off),
        contentDescription = null,      // 버튼 전체에 설명을 붙인다
        tint = if (on) color else LibriColors.Stamp,
        modifier = modifier.size(size),
    )
}

/**
 * 소리 켜기/끄기 버튼.
 * @param label 켜고 끄는 대상 이름 ("소리", "효과음")
 * @param sub 아래에 작게 붙는 설명 (예: "눌러서 바꾸기")
 */
@Composable
fun SoundToggle(
    soundOn: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "소리",
    minHeight: Dp = 46.dp,
    fontSize: Int = 16,
    iconSize: Dp = (fontSize + 8).dp,
    sub: String? = null,
) {
    val text = if (soundOn) "$label 켜짐" else "$label 꺼짐"
    val content = if (soundOn) LibriColors.Ink else LibriColors.Stamp
    SketchSurface(
        modifier = modifier
            .heightIn(min = minHeight)
            .semantics { contentDescription = "$text, 눌러서 바꾸기" },
        onClick = onToggle,
        fill = if (soundOn) LibriColors.Card else LibriColors.Coral.copy(alpha = 0.16f),
        stroke = if (soundOn) LibriColors.Ink else LibriColors.Stamp,
        strokeWidth = if (soundOn) 2.2f else 2.8f,
        radius = Dp.Infinity,
        shadow = 0.dp,
        seed = label.hashCode(),          // 켜고 끌 때 테두리 모양이 흔들리지 않게 고정
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SpeakerIcon(soundOn, size = iconSize)
                Text(text, color = content, fontSize = fontSize.sp, fontFamily = LibriFonts.Round)
            }
            if (sub != null) {
                Text(sub, color = content.copy(alpha = 0.72f), fontSize = 13.sp, lineHeight = 16.sp)
            }
        }
    }
}
