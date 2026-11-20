package kr.kw.libri.chamchamcham

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kr.kw.libri.common.FaceDetectionView
import kr.kw.libri.common.HandDoodle
import kr.kw.libri.common.LibriButton
import kr.kw.libri.common.LibriButtonStyle
import kr.kw.libri.common.LibriColors
import kr.kw.libri.common.LibriFonts
import kr.kw.libri.common.LibriLabel
import kr.kw.libri.common.LibriLobby
import kr.kw.libri.common.LibriScreen
import kr.kw.libri.common.LibriStep
import kr.kw.libri.common.LibriTag
import kr.kw.libri.common.LibriTopBar
import kr.kw.libri.common.NoteText
import kr.kw.libri.common.SketchCard
import kr.kw.libri.common.SketchSurface
import kr.kw.libri.common.TitleText
import kr.kw.libri.common.highlighter
import kr.kw.libri.common.libriTextFieldColors
import kr.kw.libri.common.rememberCameraAccess

/** 자리마다의 색 — 점수판과 입장 버튼이 같은 색을 쓴다 */
private fun Seat.color(): Color = if (this == Seat.A) LibriColors.Coral else LibriColors.Sky

/** 참참참 — 자리를 고르기 전엔 대기실, 고른 뒤엔 게임 화면 */
@Composable
fun ChamScreen(
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    vm: ChamViewModel = viewModel(),
) {
    val seat by vm.seat.collectAsStateWithLifecycle()
    val me = seat
    if (me == null) {
        BackHandler(onBack = onExit)
        ChamLobby(vm, onExit, modifier)
    } else {
        BackHandler { vm.leave() }
        ChamGame(vm, me, modifier)
    }
}

// ───────── 대기실 ─────────

@Composable
private fun ChamLobby(vm: ChamViewModel, onExit: () -> Unit, modifier: Modifier) {
    var code by rememberSaveable { mutableStateOf("temi") }
    var online by rememberSaveable { mutableStateOf(vm.onlineAvailable) }

    // 수비할 때 카메라가 필요하다 — 게임 도중이 아니라 입장할 때 미리 묻는다 (거절해도 입장은 된다)
    var pendingSeat by rememberSaveable { mutableStateOf<Seat?>(null) }
    val camera = rememberCameraAccess {
        pendingSeat?.let { vm.join(code, it, online) }
        pendingSeat = null
    }
    val enter: (Seat) -> Unit = { seat ->
        if (camera.granted) {
            vm.join(code, seat, online)
        } else {
            pendingSeat = seat
            camera.request()
        }
    }

    LibriLobby(
        title = "참참참",
        tagline = "\"참, 참, 참!\" 손이 가리키는 쪽을 피해 고개를 돌려요",
        onBack = onExit,
        modifier = modifier,
        art = { HandDoodle(it) },
        intro = {
            SketchCard(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LibriStep(1, "공격하는 사람은 손이 가리킬 방향을 미리 골라요")
                LibriStep(2, "수비하는 사람은 \"참!\" 소리에 맞춰 고개를 돌려요")
                LibriStep(3, "손과 같은 쪽을 보면 공격 성공 +1점, 반대쪽을 보면 공격권이 넘어가요")
            }
        },
    ) {
        LibriLabel("누구와 할까요?")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            LibriButton(
                "기기 2대로 대결", { online = true }, Modifier.weight(1f),
                style = if (online) LibriButtonStyle.COLOR else LibriButtonStyle.FLAT,
                enabled = vm.onlineAvailable, minHeight = 68.dp, sub = "온라인",
            )
            LibriButton(
                "혼자 연습", { online = false }, Modifier.weight(1f),
                style = if (!online) LibriButtonStyle.COLOR else LibriButtonStyle.FLAT,
                minHeight = 68.dp, sub = "봇과 대결",
            )
        }
        if (!vm.onlineAvailable) {
            Text(
                "Firebase 설정이 없어 온라인 대결을 쓸 수 없어요. libri/local.properties 에 firebase.* 값을 넣고 다시 빌드하세요.",
                color = LibriColors.Stamp, fontSize = 13.sp, lineHeight = 18.sp,
            )
        }
        if (online) {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.trim() },
                label = { Text("방 코드 (두 기기가 같게)") },
                singleLine = true,
                colors = libriTextFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        LibriLabel("자리를 골라 입장하세요", Modifier.padding(top = 6.dp))
        if (online) {
            Text("두 기기가 서로 다른 자리를 골라요. A 가 먼저 공격합니다.", color = LibriColors.Pencil, fontSize = 15.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            for (seat in Seat.entries) {
                LibriButton(
                    "${seat.name} 로 입장", { enter(seat) }, Modifier.weight(1f),
                    style = LibriButtonStyle.COLOR, color = seat.color(), minHeight = 84.dp, fontSize = 24,
                    sub = if (seat == Seat.A) "먼저 공격" else "먼저 수비",
                )
            }
        }
    }
}

// ───────── 게임 ─────────

@Composable
private fun ChamGame(vm: ChamViewModel, me: Seat, modifier: Modifier) {
    val room by vm.room.collectAsStateWithLifecycle()
    val online by vm.online.collectAsStateWithLifecycle()
    val now by produceState(vm.now(), vm) {
        while (true) {
            withFrameMillis { }
            value = vm.now()
        }
    }

    val r = room.round
    // 결과까지 보여 주는 동안은 그 라운드 기준으로, 끝나면 다음 공격자 기준으로 화면을 그린다
    val shown = r?.takeIf { it.result == null || now < it.startAt + JUDGE_END + RESULT_SHOW_MS }
    val attackerSeat = shown?.attacker ?: room.attacker
    val iAttack = attackerSeat == me
    val t = shown?.let { now - it.startAt }
    val pose = handPose(t, shown?.dir)
    val opponentHere = if (me == Seat.A) room.joinedB else room.joinedA
    val canPick = iAttack && shown == null && opponentHere

    // 수비 중 카메라에 비친 내 고개 방향 — 화면 안내용 (판정은 ViewModel 이 한다). null = 얼굴이 안 보임
    var myLook by remember { mutableStateOf<HeadDir?>(null) }
    LaunchedEffect(iAttack) { myLook = null }

    LibriScreen(modifier) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth > maxHeight

            // 손 — 내가 공격할 때는 내 손이 화면 아래에서, 수비할 때는 상대 손이 화면 위(상대편)에서 나온다.
            // 공격권이 넘어가면 손이 제 쪽으로 물러나고 맞은편에서 새 손이 들어온다.
            AnimatedContent(
                targetState = iAttack,
                transitionSpec = {
                    (slideInVertically(tween(380)) { h -> if (targetState) h / 2 else -h / 2 } + fadeIn(tween(260))) togetherWith
                        (slideOutVertically(tween(320)) { h -> if (initialState) h / 2 else -h / 2 } + fadeOut(tween(240)))
                },
                modifier = Modifier.fillMaxSize(),
                label = "hand",
            ) { mine ->
                ChamHand(
                    pose.rotation, pose.bounce, Modifier.fillMaxSize(),
                    mine = mine, sleeve = (if (mine) me else me.other).color(), topInset = 64.dp,
                )
            }

            // 상단 — 점수판, 초기화, 나가기
            LibriTopBar(onExit = { vm.leave() }, modifier = Modifier.align(Alignment.TopCenter)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        NoteText(if (online) "온라인 대결" else "혼자 연습 (봇)", size = 17.sp, color = LibriColors.Pencil)
                        // 좁은 화면에서는 버튼 대신 밑줄 친 글자로 (점수판 자리를 남긴다)
                        if (!wide) {
                            NoteText(
                                "점수 초기화", size = 16.sp, color = LibriColors.Pencil,
                                modifier = Modifier.clickable { vm.resetScore() }.drawBehind {
                                    val y = size.height - 2.dp.toPx()
                                    drawLine(LibriColors.Pencil, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                                },
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        SeatScore(Seat.A, room.scoreA, me, attackerSeat, wide)
                        Text(":", fontSize = 24.sp, fontFamily = LibriFonts.Round)
                        SeatScore(Seat.B, room.scoreB, me, attackerSeat, wide)
                    }
                }
                if (wide) LibriButton("점수 초기화", { vm.resetScore() }, style = LibriButtonStyle.FLAT, minHeight = 46.dp, fontSize = 16)
            }

            // 누구 턴인지 — 내 턴(공격)과 상대 턴(수비)을 자리 색으로 구분한다
            val turn = if (iAttack) Turn("내 턴 · 공격", me.color()) else Turn("상대 턴 · 수비", me.other.color())

            // 상태 안내 — 지금 무엇을 해야 하는지
            val status: @Composable (Modifier) -> Unit = { at ->
                when {
                    !opponentHere -> StatusCard(
                        at, "상대를 기다리는 중…", sub = "다른 기기에서 같은 방 코드로 입장해 주세요",
                    )
                    shown == null -> if (iAttack) {
                        StatusCard(at, "내가 공격할 차례!", turn = turn, highlight = true, sub = "손이 가리킬 방향을 골라 주세요")
                    } else {
                        StatusCard(
                            at, "상대가 공격할 차례", turn = turn,
                            sub = "상대가 방향을 고르는 중이에요.\n\"참!\" 에 손과 반대쪽으로 고개를 돌려 피하세요",
                        )
                    }
                    shown.result != null -> {
                        val hit = shown.result == "HIT"
                        val goodForMe = (shown.attacker == me) == hit       // 공격 성공 or 수비 성공
                        val result = resultLines(shown, me)
                        StatusCard(
                            at, result.title, turn = turn, titleColor = if (goodForMe) LibriColors.Mint else LibriColors.Stamp,
                            sub = result.detail, next = result.next,
                        )
                    }
                    else -> StatusCard(
                        at, pose.label, turn = turn, titleSize = if (pose.label.length <= 2) 60 else 40,     // "참" · "참!" 은 크게
                        sub = if (iAttack) "내가 고른 방향: ${shown.dir.ko()} (상대 화면 기준)" else "\"참!\" 에 손과 반대쪽으로 고개를 돌리세요",
                        beats = when {
                            t == null || t < 0 -> 0
                            t < BEAT_MS -> 1
                            t < FINAL_AT -> 2
                            else -> 3
                        },
                    )
                }
            }
            // 수비자: 내 얼굴(카메라) — 고개 방향을 ViewModel 로 보낸다. 공격자: 카메라 없음(상대 얼굴도 안 보임)
            val mirror: @Composable (Modifier) -> Unit = { at ->
                Mirror(
                    look = myLook,
                    wide = wide,
                    modifier = at,
                    onFaces = { faces ->
                        myLook = if (faces.isEmpty()) null else yawDirection(faces.maxByOrNull { it.boundingBox.width() })
                        vm.onFaces(faces)
                    },
                )
            }
            if (wide) {
                // 가로 화면: 손이 나오는 쪽의 맞은편에 안내를 둔다 — 공격할 때는 오른쪽 위, 수비할 때는 오른쪽 아래.
                // 수비할 때 거울(내 얼굴)은 내 쪽인 왼쪽 아래 — 위에서 내려오는 상대 손을 가리지 않는다
                if (iAttack) {
                    status(Modifier.align(Alignment.TopEnd).padding(top = 92.dp, end = 20.dp).width(360.dp))
                } else {
                    status(Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 24.dp).width(360.dp))
                    mirror(Modifier.align(Alignment.BottomStart).padding(start = 28.dp, bottom = 24.dp))
                }
            } else if (iAttack) {
                // 세로 화면, 공격: 안내는 위, 내 손은 아래에서 올라온다
                status(Modifier.align(Alignment.TopCenter).padding(top = 88.dp, start = 14.dp, end = 14.dp).fillMaxWidth())
            } else {
                // 세로 화면, 수비: 상대 손이 위에서 내려오므로 안내와 거울(내 얼굴)은 내 쪽인 아래에 둔다
                Column(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(start = 14.dp, end = 14.dp, bottom = 22.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    status(Modifier.fillMaxWidth())
                    mirror(Modifier)
                }
            }

            // 공격자: 방향 고르기 (수비자 화면 기준)
            if (canPick) {
                if (wide) {
                    LibriButton(
                        "←  왼쪽", { vm.attack(Dir.LEFT) },
                        Modifier.align(Alignment.BottomStart).padding(28.dp).width(270.dp),
                        style = LibriButtonStyle.COLOR, color = LibriColors.Sky, minHeight = 104.dp, fontSize = 34,
                    )
                    LibriButton(
                        "오른쪽  →", { vm.attack(Dir.RIGHT) },
                        Modifier.align(Alignment.BottomEnd).padding(28.dp).width(270.dp),
                        style = LibriButtonStyle.COLOR, color = LibriColors.Coral, minHeight = 104.dp, fontSize = 34,
                    )
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(20.dp),
                    ) {
                        LibriButton(
                            "←  왼쪽", { vm.attack(Dir.LEFT) }, Modifier.weight(1f),
                            style = LibriButtonStyle.COLOR, color = LibriColors.Sky, minHeight = 84.dp, fontSize = 26,
                        )
                        LibriButton(
                            "오른쪽  →", { vm.attack(Dir.RIGHT) }, Modifier.weight(1f),
                            style = LibriButtonStyle.COLOR, color = LibriColors.Coral, minHeight = 84.dp, fontSize = 26,
                        )
                    }
                }
            }
        }
    }
}

/** 누구 턴인지 알리는 꼬리표 */
private class Turn(val label: String, val color: Color)

/** 점수판의 한 자리 — 누가 나인지, 누가 공격 중인지 함께 보여 준다 */
@Composable
private fun SeatScore(seat: Seat, score: Int, me: Seat, attacker: Seat, wide: Boolean) {
    val attacking = seat == attacker
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .drawBehind {
                val r = CornerRadius(size.height / 2f)
                drawRoundRect(if (attacking) seat.color().copy(alpha = 0.3f) else LibriColors.Card, cornerRadius = r)
                drawRoundRect(
                    if (attacking) LibriColors.Ink else LibriColors.Faint, cornerRadius = r,
                    style = Stroke((if (attacking) 2.2f else 1.4f).dp.toPx()),
                )
            }
            .padding(horizontal = 12.dp, vertical = 1.dp),
    ) {
        Box(Modifier.size(12.dp).background(seat.color(), CircleShape))
        Text(
            if (seat == me) "${seat.name} 나" else seat.name,
            fontSize = 16.sp, fontFamily = LibriFonts.Round, color = LibriColors.Pencil,
        )
        Text("$score", fontSize = 28.sp, fontFamily = LibriFonts.Round, color = LibriColors.Ink)
        if (attacking && wide) Text("공격", fontSize = 15.sp, fontFamily = LibriFonts.Round, color = LibriColors.Ink)
    }
}

/**
 * 지금 무엇을 해야 하는지 알려 주는 카드.
 * @param turn 누구 턴인지 (카드 맨 위 꼬리표)
 * @param highlight 제목에 형광펜을 긋는다 (내가 무언가 해야 할 차례)
 * @param beats "참 · 참 · 참!" 세 박자 중 지나간 박자 수 (null 이면 박자 표시 없음)
 * @param next 결과가 난 뒤 공격권이 어떻게 되는지 (굵게 한 줄)
 */
@Composable
private fun StatusCard(
    modifier: Modifier,
    title: String,
    turn: Turn? = null,
    titleColor: Color = LibriColors.Ink,
    titleSize: Int = 34,
    highlight: Boolean = false,
    sub: String? = null,
    beats: Int? = null,
    next: String? = null,
) {
    SketchCard(
        modifier, seed = 7,
        padding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (turn != null) LibriTag(turn.label, turn.color, fontSize = 17.sp)
        TitleText(
            title, size = titleSize.sp, color = titleColor, align = TextAlign.Center,
            modifier = if (highlight) Modifier.highlighter() else Modifier,
        )
        if (beats != null) {
            // 박자 — 연필로 그린 동그라미 셋이 차례로 색칠된다 (마지막 "참!" 은 다른 색)
            Canvas(Modifier.size(104.dp, 26.dp)) {
                for (i in 1..3) {
                    val rad = (if (i == 3) 12f else 9.5f).dp.toPx()
                    val c = Offset(size.width * (i - 0.5f) / 3f, size.height / 2f)
                    if (i <= beats) drawCircle(if (i == 3) LibriColors.Coral else LibriColors.Yellow, rad, c)
                    drawCircle(LibriColors.Ink, rad, c, style = Stroke(2.2.dp.toPx()))
                }
            }
        }
        if (sub != null) {
            Text(sub, color = LibriColors.Pencil, fontSize = 16.sp, textAlign = TextAlign.Center, lineHeight = 22.sp)
        }
        if (next != null) {
            Text(next, color = LibriColors.Ink, fontSize = 18.sp, fontFamily = LibriFonts.Round, textAlign = TextAlign.Center)
        }
    }
}

/** 수비자의 거울 — 책에 붙여 둔 사진처럼 보이는 카메라 창과, 지금 읽힌 고개 방향 */
@Composable
private fun Mirror(
    look: HeadDir?,
    wide: Boolean,
    modifier: Modifier,
    onFaces: (List<com.google.mlkit.vision.face.Face>) -> Unit,
) {
    Box(modifier) {
        SketchSurface(radius = 8.dp, shadow = 5.dp, seed = 11) {
            Column(
                modifier = Modifier.padding(start = 9.dp, end = 9.dp, top = 9.dp, bottom = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Box(
                    Modifier
                        // 가로 화면에서는 휘두르는 손끝이 지나가는 자리를 피할 만큼만 크게
                        .size(if (wide) 236.dp else 138.dp, if (wide) 177.dp else 166.dp)
                        .background(LibriColors.Ink),
                ) {
                    FaceDetectionView(Modifier.fillMaxSize(), showInfo = false, onFaces = onFaces)
                }
                NoteText(
                    when (look) {
                        null -> "얼굴이 안 보여요"
                        HeadDir.LEFT -> "내 고개  ← 왼쪽"
                        HeadDir.RIGHT -> "내 고개  오른쪽 →"
                        HeadDir.CENTER -> "내 고개  정면"
                    },
                    size = 21.sp, color = if (look == null) LibriColors.Stamp else LibriColors.Ink,
                )
            }
        }
        // 사진을 붙여 둔 종이테이프
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(x = (-3).dp, y = (-11).dp)
                .size(76.dp, 22.dp)
                .graphicsLayer { rotationZ = -4f }
                .background(LibriColors.Yellow.copy(alpha = 0.82f)),
        )
    }
}

/** 라운드 결과 안내 — 제목, 근거(누가 어디를 봤는지), 그래서 공격권이 어떻게 되는지 */
private class ResultLines(val title: String, val detail: String, val next: String)

/**
 * 규칙: 수비자가 손과 같은 쪽을 보거나 고개를 못 돌리면 공격 성공 — 공격자가 +1점을 얻고 계속 공격한다.
 *       수비자가 반대쪽을 보면 공격 실패 — 점수 없이 공격권이 수비자에게 넘어간다. (판정은 ChamModels 의 applyResult)
 */
private fun resultLines(r: Round, me: Seat): ResultLines {
    val look = headKo(r.look)
    val hit = r.result == "HIT"
    return if (r.attacker == me) {
        if (hit) ResultLines("공격 성공!", "상대 고개: $look", "+1점 · 내가 한 번 더 공격해요")
        else ResultLines("상대가 피했어요", "상대 고개: $look", "공격권이 상대에게 넘어가요")
    } else {
        if (hit) ResultLines("잡혔어요!", "내 고개: $look", "상대 +1점 · 상대가 계속 공격해요")
        else ResultLines("피했어요!", "내 고개: $look", "공격권을 가져왔어요 · 이제 내가 공격!")
    }
}
