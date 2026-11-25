package kr.kw.libri.photobooth

import kr.kw.libri.R

import android.graphics.Rect

/**
 * 프레임 PNG(1200x3600) 안에서 사진이 들어갈 투명 구멍 4개의 좌표.
 * 좌표는 프레임 이미지의 알파 채널을 분석해서 구한 값이며, 위에서 아래 순서(1컷 -> 4컷)다.
 * Rect(left, top, right, bottom) - right/bottom 은 포함하지 않는 끝 좌표.
 */
object FrameSpecs {
    const val WIDTH = 1200
    const val HEIGHT = 3600

    private val slotsByFrame: Map<Int, List<Rect>> = mapOf(
        R.drawable.frame_1_library to listOf(
            Rect(129, 377, 1073, 1033),
            Rect(129, 1102, 1073, 1756),
            Rect(129, 1826, 1073, 2481),
            Rect(129, 2550, 1073, 3193)
        ),
        R.drawable.frame_2_libri_burgundy to listOf(
            Rect(80, 271, 1124, 952),
            Rect(80, 992, 1124, 1673),
            Rect(80, 1713, 1124, 2393),
            Rect(80, 2434, 1124, 3114)
        ),
        R.drawable.frame_3_libri_beige to listOf(
            Rect(78, 271, 1124, 952),
            Rect(78, 992, 1124, 1673),
            Rect(78, 1713, 1124, 2393),
            Rect(78, 2434, 1124, 3114)
        ),
        R.drawable.frame_4_central_library to listOf(
            Rect(105, 315, 1099, 1012),
            Rect(105, 1081, 1099, 1776),
            Rect(104, 1838, 1099, 2534),
            Rect(105, 2592, 1099, 3277)
        )
    )

    /** 알 수 없는 프레임이면 frame_1_library 의 좌표를 쓴다. */
    fun slotsFor(frameResId: Int): List<Rect> =
        slotsByFrame[frameResId] ?: slotsByFrame.getValue(R.drawable.frame_1_library)
}
