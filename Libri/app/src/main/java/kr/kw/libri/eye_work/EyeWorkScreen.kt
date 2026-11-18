package kr.kw.libri.eye_work

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kr.kw.libri.common.EyesDoodle
import kr.kw.libri.common.FaceDetectionView
import kr.kw.libri.common.LibriButton
import kr.kw.libri.common.LibriButtonStyle
import kr.kw.libri.common.LibriColors
import kr.kw.libri.common.LibriFonts
import kr.kw.libri.common.LibriLobby
import kr.kw.libri.common.LibriScreen
import kr.kw.libri.common.LibriStep
import kr.kw.libri.common.LibriTopBar
import kr.kw.libri.common.NoteText
import kr.kw.libri.common.SketchCard
import kr.kw.libri.common.SketchSurface
import kr.kw.libri.common.Stamp
import kr.kw.libri.common.TitleText
import kr.kw.libri.common.rememberCameraAccess
import kotlin.math.PI
import kotlin.math.sin

/**
 * 눈 운동 — 가운데 점을 보며 고개를 지정 방향으로 돌리는 운동.
 *  카메라(FaceDetectionView)로 고개 각도를 재고, 판정은 EyeWorkViewModel 이 한다.
 */
@Composable
fun EyeWorkScreen(
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    vm: EyeWorkViewModel = viewModel(),
) {
    BackHandler(onBack = onExit)
    var started by rememberSaveable { mutableStateOf(false) }
    if (started) {
        EyeGame(vm, onExit, modifier)
    } else {
        EyeIntro(
            onStart = {
                vm.restart()        // 지난번에 하던 단계가 남아 있지 않게 처음부터
                started = true
            },
            onExit = onExit,
            modifier = modifier,
        )
    }
}

// ───────── 시작 전 안내 ─────────

@Composable
private fun EyeIntro(onStart: () -> Unit, onExit: () -> Unit, modifier: Modifier) {
    var denied by rememberSaveable { mutableStateOf(false) }
    val camera = rememberCameraAccess { granted -> if (granted) onStart() else denied = true }

    LibriLobby(
        title = "눈 운동",
        tagline = "책을 오래 봤다면 잠깐! 고개만 돌려 눈 근육을 풀어요",
        onBack = onExit,
        modifier = modifier,
        art = { EyesDoodle(it) },
        artOnRight = true,          // 고를 것이 없어 오른쪽 쪽이 비므로 그림을 크게 놓는다
        intro = {
            SketchCard(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LibriStep(1, "화면 가운데 초록 점을 계속 바라봐요")
                LibriStep(2, "화살표가 가리키는 쪽으로 고개만 천천히 돌려요")
                LibriStep(3, "둥근 띠가 다 찰 때까지 잠깐 그대로! 왼쪽·오른쪽·위·아래를 두 번씩 해요")
            }
        },
    ) {
        LibriButton(
            "시작하기", { if (camera.granted) onStart() else camera.request() },
            Modifier.fillMaxWidth(), style = LibriButtonStyle.COLOR, minHeight = 84.dp, fontSize = 26,
            sub = "카메라로 고개 방향을 읽어요",
        )
        if (denied) {
            Text(
                "카메라 권한이 없어 시작할 수 없어요. 설정에서 카메라를 허용해 주세요.",
                color = LibriColors.Stamp, fontSize = 14.sp,
            )
        }
    }
}

// ───────── 운동 화면 ─────────

@Composable
private fun EyeGame(vm: EyeWorkViewModel, onExit: () -> Unit, modifier: Modifier) {
    val st by vm.state.collectAsStateWithLifecycle()
    // 카메라에 얼굴이 보이는가 — 화면 안내용 (판정은 ViewModel 이 한다)
    var faceSeen by remember { mutableStateOf(false) }

    LibriScreen(modifier) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth > maxHeight

            // 가운데 점 + 유지 진행 띠 + 방향 화살표
            if (!st.done) EyeTarget(st, Modifier.fillMaxSize())

            // 상단 — 진행 상황, 나가기
            LibriTopBar(onExit = onExit, modifier = Modifier.align(Alignment.TopCenter)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        if (st.done) "눈 운동 · 완료" else "눈 운동 · ${st.step} / ${st.total}",
                        color = LibriColors.Ink, fontSize = 17.sp, fontFamily = LibriFonts.Round,
                    )
                    StepDots(st)
                }
            }

            if (st.done) {
                DoneCard(onAgain = { vm.restart() }, onExit = onExit, modifier = Modifier.align(Alignment.Center))
            } else {
                // 안내 문구
                val target = st.target
                val at = Modifier.align(Alignment.TopCenter).padding(top = 92.dp, start = 14.dp, end = 14.dp).widthIn(max = 520.dp)
                when {
                    st.recenter -> Guide(at, "좋아요!", LibriColors.Mint, "정면으로 돌아오세요")
                    target != null -> Guide(
                        at, "${target.arrow}  ${target.label}", LibriColors.Ink,
                        if (faceSeen) "가운데 점을 계속 보면서\n고개만 ${target.toward()} 돌려요"
                        else "얼굴이 카메라에 보이게 화면 앞으로 와 주세요",
                    )
                    else -> Guide(at, "준비…", LibriColors.Ink, null)
                }

                // 카메라 작은 창 — 얼굴이 잘 잡히고 있는지 확인용 (책에 붙인 사진처럼)
                SketchSurface(
                    modifier = Modifier.align(if (wide) Alignment.BottomEnd else Alignment.BottomStart).padding(16.dp),
                    radius = 8.dp, shadow = 5.dp, seed = 13,
                ) {
                    Column(
                        modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 3.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Box(
                            Modifier
                                .size(if (wide) 188.dp else 108.dp, if (wide) 141.dp else 144.dp)
                                .background(LibriColors.Ink),
                        ) {
                            FaceDetectionView(
                                Modifier.fillMaxSize(), showInfo = false,
                                onFaces = { faces ->
                                    faceSeen = faces.isNotEmpty()
                                    vm.onFaces(faces)
                                },
                            )
                        }
                        NoteText(
                            if (faceSeen) "얼굴 인식 중" else "얼굴이 안 보여요",
                            size = 17.sp, color = if (faceSeen) LibriColors.Ink else LibriColors.Stamp,
                        )
                    }
                }
            }
        }
    }
}

/** 진행 표시 — 연필로 그린 동그라미가 하나씩 색칠된다 */
@Composable
private fun StepDots(st: EyeState) {
    Canvas(Modifier.size((st.total * 22).dp, 18.dp)) {
        val rad = 7.dp.toPx()
        for (i in 1..st.total) {
            val c = Offset((i - 0.5f) * 22.dp.toPx(), size.height / 2f)
            val current = !st.done && i == st.step
            when {
                st.done || i < st.step -> drawCircle(LibriColors.Mint, rad, c)
                current -> drawCircle(LibriColors.Yellow, rad, c)
            }
            drawCircle(
                if (current || st.done || i < st.step) LibriColors.Ink else LibriColors.Faint, rad, c,
                style = Stroke((if (current) 2.4f else 1.6f).dp.toPx()),
            )
        }
    }
}

/** "왼쪽으로" · "위로" — 받침에 맞는 조사 */
private fun EyeDir.toward(): String = if (this == EyeDir.UP || this == EyeDir.DOWN) "${label}로" else "${label}으로"

@Composable
private fun Guide(modifier: Modifier, title: String, titleColor: Color, sub: String?) {
    SketchCard(
        modifier.fillMaxWidth(), seed = 5,
        padding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TitleText(title, size = 44.sp, color = titleColor, align = TextAlign.Center)
        if (sub != null) {
            Text(sub, color = LibriColors.Pencil, fontSize = 17.sp, textAlign = TextAlign.Center, lineHeight = 24.sp)
        }
    }
}

/** 가운데 점(시선 고정), 유지 진행 띠, 고개를 돌릴 방향으로 흘러가는 화살표 */
@Composable
private fun EyeTarget(st: EyeState, modifier: Modifier) {
    val flow by rememberInfiniteTransition(label = "arrows").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "flow",
    )
    Canvas(modifier) {
        val c = Offset(size.width / 2f, size.height * 0.58f)
        val dot = 15.dp.toPx()
        val ringR = 50.dp.toPx()
        val stroke = 9.dp.toPx()
        val line = 2.6.dp.toPx()
        val accent = if (st.recenter) LibriColors.Amber else LibriColors.Mint

        // 색연필로 옅게 칠한 바탕
        drawCircle(accent.copy(alpha = 0.12f), ringR * 1.9f, c)
        // 유지 진행 띠 — 연필로 그린 두 겹의 동그라미 사이를 색이 채워 간다
        drawCircle(LibriColors.Card, ringR + stroke / 2f, c)
        if (st.progress > 0f) {
            drawArc(
                accent, startAngle = -90f, sweepAngle = 360f * st.progress, useCenter = false,
                topLeft = Offset(c.x - ringR, c.y - ringR), size = Size(ringR * 2f, ringR * 2f),
                style = Stroke(stroke),
            )
        }
        drawCircle(LibriColors.Paper, ringR - stroke / 2f, c)
        drawCircle(LibriColors.Ink, ringR + stroke / 2f, c, style = Stroke(line))
        drawCircle(LibriColors.Ink, ringR - stroke / 2f, c, style = Stroke(line * 0.7f))
        // 바라볼 점
        drawCircle(accent, dot, c)
        drawCircle(LibriColors.Ink, dot, c, style = Stroke(line))
        drawCircle(Color.White.copy(alpha = 0.8f), dot * 0.28f, Offset(c.x - dot * 0.32f, c.y - dot * 0.32f))

        // 고개를 돌릴 방향 — 화살표 세 개가 바깥쪽으로 차례로 진해진다
        val target = st.target ?: return@Canvas
        val dx = when (target) { EyeDir.LEFT -> -1f; EyeDir.RIGHT -> 1f; else -> 0f }
        val dy = when (target) { EyeDir.UP -> -1f; EyeDir.DOWN -> 1f; else -> 0f }
        for (i in 0..2) {
            val phase = ((flow - i * 0.22f) % 1f + 1f) % 1f
            val lit = sin(phase * PI).toFloat()
            val dist = ringR + (36 + i * 38).dp.toPx()
            val arm = (17 + i * 3).dp.toPx()
            val tip = Offset(c.x + dx * dist, c.y + dy * dist)
            val back = Offset(tip.x - dx * arm, tip.y - dy * arm)
            // 방향에 수직인 쪽(-dy, dx)으로 벌어진 두 선이 '>' 모양을 만든다 — 연필 테두리 위에 색을 얹는다
            val a = Offset(back.x - dy * arm, back.y + dx * arm)
            val b = Offset(back.x + dy * arm, back.y - dx * arm)
            val ink = LibriColors.Ink.copy(alpha = 0.3f + 0.7f * lit)
            val fill = LibriColors.Coral.copy(alpha = 0.25f + 0.75f * lit)
            drawLine(ink, a, tip, strokeWidth = 13.dp.toPx(), cap = StrokeCap.Round)
            drawLine(ink, b, tip, strokeWidth = 13.dp.toPx(), cap = StrokeCap.Round)
            drawLine(LibriColors.Paper, a, tip, strokeWidth = 8.dp.toPx(), cap = StrokeCap.Round)
            drawLine(LibriColors.Paper, b, tip, strokeWidth = 8.dp.toPx(), cap = StrokeCap.Round)
            drawLine(fill, a, tip, strokeWidth = 8.dp.toPx(), cap = StrokeCap.Round)
            drawLine(fill, b, tip, strokeWidth = 8.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun DoneCard(onAgain: () -> Unit, onExit: () -> Unit, modifier: Modifier) {
    SketchCard(
        modifier = modifier.padding(20.dp).widthIn(max = 420.dp),
        radius = 30.dp, shadow = 8.dp, seed = 9,
        padding = PaddingValues(horizontal = 26.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Stamp("참 잘했어요", fontSize = 30.sp, tilt = -6f)
        TitleText("눈 운동 완료!", size = 36.sp)
        Text(
            "왼쪽·오른쪽·위·아래를 두 번씩 모두 해냈어요.\n눈이 한결 편안해졌을 거예요.",
            color = LibriColors.Pencil, fontSize = 16.sp, textAlign = TextAlign.Center, lineHeight = 23.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            LibriButton("다시 하기", onAgain, Modifier.weight(1f), style = LibriButtonStyle.INK)
            LibriButton("나가기", onExit, Modifier.weight(1f))
        }
    }
}
