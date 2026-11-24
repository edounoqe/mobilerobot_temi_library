package kr.kw.libri.stretch

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kr.kw.libri.common.LibriButton
import kr.kw.libri.common.LibriButtonStyle
import kr.kw.libri.common.LeaveOnDispose
import kr.kw.libri.common.LibriColors
import kr.kw.libri.common.LibriFonts
import kr.kw.libri.common.LibriLabel
import kr.kw.libri.common.LibriLobby
import kr.kw.libri.common.LibriStep
import kr.kw.libri.common.LibriTopBar
import kr.kw.libri.common.NoteText
import kr.kw.libri.common.SketchCard
import kr.kw.libri.common.SketchSurface
import kr.kw.libri.common.Stamp
import kr.kw.libri.common.StretchDoodle
import kr.kw.libri.common.TitleText
import kr.kw.libri.common.rememberCameraAccess
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 책장 통과 스트레칭 — 다가오는 책장 벽의 구멍과 같은 자세를 잡아 통과하는 게임.
 *  카메라(ML Kit 포즈 검출)로 몸 뼈대를 읽어 아바타를 움직이고, 판정은 StretchEngine 이 한다.
 */
@Composable
fun StretchScreen(
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    vm: StretchViewModel = viewModel(),
) {
    val session = vm.session
    // 사람이 떠나 얼굴 화면으로 돌아가는 등 화면이 바뀌면 판을 접는다 (다음 사람은 대기실부터)
    LeaveOnDispose { vm.quit() }
    if (session == null) {
        BackHandler(onBack = onExit)
        StretchLobby(vm, onExit, modifier)
    } else {
        BackHandler { vm.quit() }
        StretchGame(vm, session, modifier)
    }
}

// ───────── 대기실 ─────────

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun StretchLobby(vm: StretchViewModel, onExit: () -> Unit, modifier: Modifier) {
    var denied by rememberSaveable { mutableStateOf(false) }
    val camera = rememberCameraAccess { granted -> if (granted) vm.start(demo = false) else denied = true }

    LibriLobby(
        title = "책장 통과\n스트레칭",
        tagline = "책장 벽이 다가와요! 구멍과 같은 자세를 잡으면 통과",
        onBack = onExit,
        modifier = modifier,
        art = { StretchDoodle(it) },
        intro = {
            SketchCard(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LibriStep(1, "카메라에서 두세 걸음 떨어져, 머리부터 허리까지 보이게 서요")
                LibriStep(2, "화면 속 내 인형을 파란 윤곽선(구멍 모양)에 맞춰요")
                LibriStep(3, "벽이 닿을 때까지 자세를 유지하면 점수가 더 올라요")
            }
        },
    ) {
        LibriLabel("코스")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            for (c in StretchCourses.ALL) {
                LibriButton(
                    // 이름이 긴 코스는 자리를 조금 더 준다
                    c.title, { vm.course = c }, Modifier.weight(if (c.title.length > 4) 1.5f else 1f),
                    style = if (vm.course === c) LibriButtonStyle.COLOR else LibriButtonStyle.FLAT,
                    minHeight = 68.dp, fontSize = 18, sub = c.desc,
                )
            }
        }
        if (vm.course === StretchCourses.CREATIVE) NoteText(StretchShapes.HINT, size = 19.sp)
        // 이 코스에 나오는 동작 미리보기 — 옆으로 숨지 않게 줄을 바꿔 모두 보여 준다
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
        ) {
            vm.course.poses.forEachIndexed { i, pose ->
                SketchSurface(Modifier.width(86.dp), radius = 14.dp, shadow = 4.dp, strokeWidth = 2f, seed = 100 + i) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp),
                    ) {
                        PoseIcon(pose, Modifier.size(64.dp))
                        Text(
                            "${i + 1}. ${pose.name}", color = LibriColors.Ink, fontSize = 12.sp, fontFamily = LibriFonts.Round,
                            textAlign = TextAlign.Center, maxLines = 2, lineHeight = 16.sp,
                        )
                    }
                }
            }
        }

        LibriButton(
            "시작하기", { if (camera.granted) vm.start(demo = false) else camera.request() },
            Modifier.fillMaxWidth(), style = LibriButtonStyle.COLOR, minHeight = 84.dp, fontSize = 26,
            sub = "카메라로 몸 동작을 읽어요",
        )
        if (denied) {
            Text(
                "카메라 권한이 없어 시작할 수 없어요. 설정에서 카메라를 허용하거나, 아래 '튜토리얼 영상'으로 구경해 보세요.",
                color = LibriColors.Stamp, fontSize = 14.sp,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            LibriButton(
                "튜토리얼 영상", { vm.start(demo = true) }, Modifier.weight(1.25f),
                minHeight = 64.dp, sub = "카메라 없이 구경하기",
            )
            LibriButton(
                if (vm.soundOn) "효과음 켜짐" else "효과음 꺼짐", { vm.toggleSound() }, Modifier.weight(1f),
                style = LibriButtonStyle.FLAT, minHeight = 64.dp, fontSize = 17, sub = "눌러서 바꾸기",
            )
        }
    }
}

/** 동작 하나를 작은 종이 인형 그림으로 */
@Composable
private fun PoseIcon(pose: StretchPose, modifier: Modifier = Modifier, fill: Color = LibriColors.Card) {
    val figure = remember(pose) { Figure().also { pose.pose.layout(it) } }
    Canvas(modifier) {
        val unit = size.height / 4.2f
        val shape = pose.shape
        if (shape != null) {
            // 도형 구멍 — 구멍 모양과, 그 안에 들어간 예시 인형을 옅게
            val hole = shapePath(shape, size.width / 2f, size.height * 0.97f, unit * 0.92f)
            clipRect {
                drawPath(hole, GUIDE.copy(alpha = 0.22f))
                drawPath(hole, LibriColors.Ink, style = Stroke(2.dp.toPx(), join = StrokeJoin.Round))
            }
            FigurePainter(this, size.width / 2f, size.height * 0.97f, unit * 0.92f).silhouette(figure, 0f, Color(0xFF8FA6BC))
        } else {
            FigurePainter(this, size.width / 2f, size.height * 0.97f, unit).doll(figure, fill)
        }
    }
}

// ───────── 게임 ─────────

@Composable
private fun StretchGame(vm: StretchViewModel, s: StretchSession, modifier: Modifier) {
    val renderer = remember(s) { StageRenderer() }
    LaunchedEffect(s) {
        while (true) withFrameNanos { vm.onFrame(it) }
    }
    KeepScreenOn()

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val wide = maxWidth > maxHeight

        // 무대 — 통로, 책장 벽, 아바타, 효과 (바탕 종이는 창 전체에 이미 깔려 있다)
        Canvas(Modifier.fillMaxSize().clipToBounds()) {
            with(renderer) { drawStage(s, vm.frame) }
        }

        // 카메라 작은 창 — 내가 화면에 잘 들어와 있는지 확인용 (책에 붙인 사진처럼)
        if (!s.demo) {
            SketchSurface(
                modifier = Modifier.align(if (wide) Alignment.BottomEnd else Alignment.BottomStart).padding(14.dp),
                radius = 8.dp, shadow = 5.dp, seed = 17,
            ) {
                Column(
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Box(Modifier.size(if (wide) 180.dp else 100.dp, if (wide) 135.dp else 134.dp).clipToBounds().background(LibriColors.Ink)) {
                        PoseCameraView(Modifier.fillMaxSize(), onObservation = vm::onObservation)
                    }
                    NoteText(
                        if (vm.tracked) "인식 중" else "몸이 안 보여요",
                        size = 17.sp, color = if (vm.tracked) LibriColors.Ink else LibriColors.Stamp,
                    )
                }
            }
        }

        TopBar(vm, s, Modifier.align(Alignment.TopCenter))

        when (vm.phase) {
            Phase.READY -> ReadyCard(
                vm, s,
                // 아바타를 가리지 않게 — 가로 화면은 왼쪽 패널, 세로 화면은 머리 위
                if (wide) Modifier.align(Alignment.CenterStart).padding(start = 18.dp).width(SIDE_CARD_W)
                else Modifier.align(Alignment.TopCenter).padding(top = 84.dp, start = 16.dp, end = 16.dp).widthIn(max = 460.dp),
            )
            Phase.COUNTDOWN -> CountdownNumber(vm.countdown, wide, Modifier.align(sideOrTop(wide, -0.74f)))
            Phase.PLAYING -> {
                val target = s.engine.walls.getOrNull(vm.targetIndex)
                if (target != null) {
                    PoseCard(
                        vm, s, target, wide,
                        if (wide) Modifier.align(Alignment.CenterStart).padding(start = 18.dp, top = 30.dp)
                        else Modifier.align(Alignment.TopCenter).padding(top = 78.dp, start = 12.dp, end = 12.dp),
                    )
                    FitBar(
                        vm, s,
                        if (wide) Modifier.align(Alignment.BottomStart).padding(start = 18.dp, bottom = 16.dp).width(SIDE_CARD_W)
                        else Modifier.align(Alignment.BottomCenter)
                            .padding(start = if (s.demo) 20.dp else 140.dp, end = 20.dp, bottom = 30.dp).fillMaxWidth(),
                    )
                }
                GradePopupStamp(vm.popup, wide, Modifier.align(sideOrTop(wide, -0.5f)))
            }
            Phase.RESULT -> {
                s.engine.result?.let { ResultCard(vm, s, it, Modifier.align(Alignment.Center)) }
            }
        }
    }
}

/** 가로 화면 왼쪽 패널(안내·동작·일치도 카드)의 폭 — 제목이 한 줄에 들어가고 아바타는 가리지 않는 정도 */
private val SIDE_CARD_W = 300.dp

/**
 * 판정 도장·카운트다운 자리 — 아바타를 가리지 않는 곳.
 * 가로 화면은 아바타 오른쪽 여백(왼쪽 동작 안내의 맞은편), 세로 화면은 아바타 머리 위.
 */
private fun sideOrTop(wide: Boolean, topBias: Float): Alignment =
    if (wide) BiasAlignment(0.8f, -0.3f) else BiasAlignment(0f, topBias)

/** 프레임마다 다시 그리는 작은 캔버스 — 그리기 단계에서 프레임 번호를 읽어 두면 Compose 가 매 프레임 다시 그린다 */
@Composable
private fun FrameCanvas(vm: StretchViewModel, modifier: Modifier, onDraw: DrawScope.() -> Unit) {
    Canvas(modifier) {
        if (vm.frame >= 0L) onDraw()
    }
}

/** 연필 테두리 안을 색이 채워 가는 막대. fraction 은 0~1 */
private fun DrawScope.inkBar(fraction: Float, color: Color) {
    val r = CornerRadius(size.height / 2f)
    drawRoundRect(LibriColors.Card, cornerRadius = r)
    val w = size.width * fraction.coerceIn(0f, 1f)
    if (w > size.height * 0.5f) drawRoundRect(color, size = Size(w, size.height), cornerRadius = r)
    drawRoundRect(LibriColors.Ink, cornerRadius = r, style = Stroke(2.dp.toPx()))
}

/** 게임 중에는 화면이 꺼지지 않게 */
@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

@Composable
private fun TopBar(vm: StretchViewModel, s: StretchSession, modifier: Modifier) {
    // 직접 하는 판이 진행 중이면 '나가기'를 한 번 더 눌러야 한다 (튜토리얼·결과 화면은 바로)
    val inProgress = !s.demo && vm.phase != Phase.RESULT
    LibriTopBar(onExit = { vm.quit() }, modifier = modifier, confirmExit = inProgress) {
        Column(Modifier.weight(1f)) {
            NoteText(if (s.demo) "튜토리얼 · ${s.course.title}" else "${s.course.title} 코스", size = 17.sp, color = LibriColors.Pencil)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("%,d".format(vm.score), fontSize = 30.sp, fontFamily = LibriFonts.Round, color = LibriColors.Ink)
                if (vm.combo >= 2) NoteText("${vm.combo}연속 통과!", size = 21.sp, color = LibriColors.Stamp)
            }
        }
        LibriButton(
            if (vm.soundOn) "소리 켬" else "소리 끔", { vm.toggleSound() },
            style = LibriButtonStyle.FLAT, minHeight = 46.dp, fontSize = 16,
        )
    }
}

@Composable
private fun ReadyCard(vm: StretchViewModel, s: StretchSession, modifier: Modifier) {
    SketchCard(
        modifier, seed = 21,
        padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (s.demo) {
            TitleText("튜토리얼을 시작합니다", size = 24.sp, align = TextAlign.Center)
        } else {
            TitleText(
                if (vm.tracked) "좋아요, 그대로!" else "카메라 앞에 서 주세요",
                size = 25.sp, color = if (vm.tracked) FIT_ON else LibriColors.Ink, align = TextAlign.Center,
            )
            Text(
                "두세 걸음 떨어져 머리부터 허리까지 보이면 시작해요",
                color = LibriColors.Pencil, fontSize = 15.sp, textAlign = TextAlign.Center, lineHeight = 21.sp,
            )
        }
        // 몸이 잡힌 채로 잠깐 유지하면 시작 — 그 진행을 보여 준다
        FrameCanvas(vm, Modifier.fillMaxWidth().height(12.dp)) {
            inkBar(s.engine.readyMs / StretchEngine.READY_HOLD_MS, FIT_ON)
        }
    }
}

@Composable
private fun CountdownNumber(n: Int, wide: Boolean, modifier: Modifier) {
    key(n) {
        val pop = remember { Animatable(1.5f) }
        LaunchedEffect(Unit) { pop.animateTo(1f, tween(300)) }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
            // 형광펜으로 칠한 동그라미 안의 숫자 (세로 화면은 아바타 머리 위 좁은 자리에 들어가게 작게)
            SketchSurface(
                modifier = Modifier.size(if (wide) 150.dp else 112.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value },
                fill = LibriColors.Yellow, radius = Dp.Infinity, shadow = 7.dp, strokeWidth = 3.2f, seed = n,
                contentAlignment = Alignment.Center,
            ) {
                TitleText("$n", size = if (wide) 96.sp else 70.sp)
            }
        }
    }
}

/** 다음 벽의 동작 안내 — 그림, 이름, 방법, 진행 상황 */
@Composable
private fun PoseCard(vm: StretchViewModel, s: StretchSession, target: Wall, wide: Boolean, modifier: Modifier) {
    val progress: @Composable () -> Unit = {
        // 벽마다 동그라미 하나 — 통과하면 판정 색으로 칠해진다
        val judged = vm.judgedCount      // 판정이 날 때마다 다시 칠한다
        Canvas(Modifier.size((s.engine.walls.size * 16).dp, 14.dp)) {
            for (w in s.engine.walls) {
                val isTarget = w === target
                val c = Offset((w.index + 0.5f) * 16.dp.toPx(), size.height / 2f)
                val rad = (if (isTarget) 6.5f else 5f).dp.toPx()
                when (if (w.index < judged) w.grade else null) {
                    Grade.PERFECT -> drawCircle(FIT_ON, rad, c)
                    Grade.GOOD -> drawCircle(LibriColors.Amber, rad, c)
                    Grade.MISS -> drawCircle(FIT_OFF, rad, c)
                    null -> if (isTarget) drawCircle(LibriColors.Yellow, rad, c)
                }
                drawCircle(
                    if (isTarget || w.index < judged) LibriColors.Ink else LibriColors.Faint, rad, c,
                    style = Stroke((if (isTarget) 2.2f else 1.4f).dp.toPx()),
                )
            }
        }
    }
    // 벽이 닿기까지 남은 시간
    val timer: @Composable () -> Unit = {
        FrameCanvas(vm, Modifier.fillMaxWidth().height(12.dp)) {
            val left = s.engine.approach()
            inkBar(left, if (left < 0.25f) FIT_OFF else LibriColors.Yellow)
        }
    }

    if (wide) {
        SketchCard(
            modifier.width(SIDE_CARD_W), seed = 23,
            padding = PaddingValues(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            progress()
            PoseIcon(target.pose, Modifier.size(128.dp))
            TitleText(target.pose.name, size = 24.sp, align = TextAlign.Center)
            Text(target.pose.cue, color = LibriColors.Pencil, fontSize = 15.sp, textAlign = TextAlign.Center, lineHeight = 20.sp)
            timer()
        }
    } else {
        SketchSurface(modifier.fillMaxWidth(), seed = 23, radius = 20.dp, shadow = 5.dp) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PoseIcon(target.pose, Modifier.size(84.dp))
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    progress()
                    TitleText(target.pose.name, size = 23.sp)
                    Text(target.pose.cue, color = LibriColors.Pencil, fontSize = 14.sp, lineHeight = 19.sp)
                    timer()
                }
            }
        }
    }
}

/** 일치도 막대 — 칸 표시는 '좋아요'와 '완벽' 기준선 */
@Composable
private fun FitBar(vm: StretchViewModel, s: StretchSession, modifier: Modifier) {
    SketchCard(
        modifier, radius = 16.dp, shadow = 4.dp, seed = 25,
        padding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 9.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        NoteText("일치도", size = 19.sp, color = LibriColors.Ink)
        FrameCanvas(vm, Modifier.fillMaxWidth().height(16.dp)) {
            val v = s.engine.judgeFit.coerceIn(0f, 1f)
            inkBar(
                v,
                when {
                    v >= StretchEngine.PERFECT_FIT -> FIT_ON
                    v >= StretchEngine.GOOD_FIT -> LibriColors.Amber
                    else -> FIT_OFF
                },
            )
            for (mark in floatArrayOf(StretchEngine.GOOD_FIT, StretchEngine.PERFECT_FIT)) {
                drawLine(
                    LibriColors.Ink, Offset(size.width * mark, 0f), Offset(size.width * mark, size.height),
                    strokeWidth = 2.dp.toPx(),
                )
            }
        }
    }
}

/** 판정 — 도장을 쾅 찍듯이 나타났다가 사라진다 */
@Composable
private fun GradePopupStamp(popup: GradePopup?, wide: Boolean, modifier: Modifier) {
    if (popup == null) return
    key(popup.seq) {
        val anim = remember { Animatable(0f) }
        LaunchedEffect(Unit) { anim.animateTo(1f, tween(950, easing = LinearEasing)) }
        val t = anim.value
        val scale = when {
            t < 0.12f -> 1.9f - 0.98f * (t / 0.12f)              // 위에서 내려와 쾅
            t < 0.2f -> 0.92f + 0.08f * ((t - 0.12f) / 0.08f)     // 살짝 튀었다가 제자리
            else -> 1f
        }
        val alpha = when {
            t < 0.08f -> t / 0.08f
            t > 0.72f -> 1f - (t - 0.72f) / 0.28f
            else -> 1f
        }
        val color = when (popup.grade) {
            Grade.PERFECT -> FIT_ON
            Grade.GOOD -> LibriColors.Amber
            Grade.MISS -> LibriColors.Stamp
        }
        if (t < 1f) {
            // 종이쪽지 위에 찍힌 도장 — 책장 그림 위에서도 또렷하게 보인다
            SketchSurface(
                modifier = modifier.graphicsLayer {
                    scaleX = scale; scaleY = scale; rotationZ = -4f
                    this.alpha = alpha.coerceIn(0f, 1f)
                },
                radius = 14.dp, shadow = 6.dp, seed = 29,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 8.dp),
                ) {
                    Stamp(popup.grade.label, color = color, fontSize = if (wide) 46.sp else 44.sp, tilt = -5f)
                    if (popup.gained > 0) {
                        Text("+${popup.gained}", fontSize = 30.sp, fontFamily = LibriFonts.Round, color = LibriColors.Ink)
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultCard(vm: StretchViewModel, s: StretchSession, r: StretchResult, modifier: Modifier) {
    var cameraDenied by remember { mutableStateOf(false) }
    SketchCard(
        modifier = modifier.padding(20.dp).widthIn(max = 400.dp),
        radius = 30.dp, shadow = 8.dp, seed = 27,
        padding = PaddingValues(horizontal = 26.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TitleText(if (s.demo) "튜토리얼 끝!" else "스트레칭 완료!", size = 32.sp)
        Stars(r.stars)
        Text("%,d점".format(r.score), fontSize = 50.sp, fontFamily = LibriFonts.Round, color = LibriColors.Ink)
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Tally("완벽", r.perfect, FIT_ON)
            Tally("좋아요", r.good, LibriColors.Amber)
            Tally("쿵", r.miss, FIT_OFF)
        }
        Text("최다 ${r.maxCombo}연속 통과", color = LibriColors.Pencil, fontSize = 15.sp)
        NoteText(
            when {
                s.demo -> "이제 직접 해 볼까요?"
                r.stars == 3 -> "몸이 활짝 펴졌어요. 훌륭해요!"
                r.stars == 2 -> "잘했어요! 한결 가벼워졌죠?"
                else -> "천천히 한 번 더 해 보면 금방 늘어요"
            },
            size = 23.sp, color = LibriColors.Ink, align = TextAlign.Center,
        )
        Spacer(Modifier.height(2.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            if (s.demo) {
                // 튜토리얼을 본 사람에게는 '직접 해 보기'가 다음 할 일이다
                val camera = rememberCameraAccess { granted -> if (granted) vm.start(demo = false) else cameraDenied = true }
                LibriButton(
                    "직접 해 보기", { if (camera.granted) vm.start(demo = false) else camera.request() },
                    Modifier.weight(1f), style = LibriButtonStyle.INK,
                )
            } else {
                LibriButton("다시 하기", { vm.start(demo = false) }, Modifier.weight(1f), style = LibriButtonStyle.INK)
            }
            LibriButton("나가기", { vm.quit() }, Modifier.weight(1f))
        }
        if (cameraDenied) {
            Text(
                "카메라 권한이 없어 직접 할 수 없어요. 설정에서 카메라를 허용해 주세요.",
                color = LibriColors.Stamp, fontSize = 14.sp, textAlign = TextAlign.Center,
            )
        }
    }
}

/** 별 셋 — 받은 만큼 형광펜으로 칠해진다 */
@Composable
private fun Stars(count: Int) {
    Spacer(
        Modifier.size(170.dp, 54.dp).drawWithCache {
            val r = size.height * 0.46f
            val stars = List(3) { i ->
                val cx = size.width * (i + 0.5f) / 3f
                val cy = size.height * 0.52f
                Path().apply {
                    for (k in 0 until 10) {
                        val a = (-90f + k * 36f) * PI.toFloat() / 180f
                        val rr = if (k % 2 == 0) r else r * 0.46f
                        val x = cx + cos(a) * rr
                        val y = cy + sin(a) * rr
                        if (k == 0) moveTo(x, y) else lineTo(x, y)
                    }
                    close()
                }
            }
            val outline = Stroke(2.6.dp.toPx(), join = StrokeJoin.Round)
            onDrawBehind {
                stars.forEachIndexed { i, star ->
                    drawPath(star, if (i < count) LibriColors.Yellow else LibriColors.Card)
                    drawPath(star, if (i < count) LibriColors.Ink else LibriColors.Faint, style = outline)
                }
            }
        }
    )
}

@Composable
private fun Tally(label: String, n: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$n", fontSize = 30.sp, fontFamily = LibriFonts.Round, color = color)
        Text(label, color = LibriColors.Pencil, fontSize = 14.sp, fontFamily = LibriFonts.Round)
    }
}
