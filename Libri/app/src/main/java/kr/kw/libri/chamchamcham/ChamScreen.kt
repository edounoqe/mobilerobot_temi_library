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
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kr.kw.libri.backend.InviteGame
import kr.kw.libri.backend.InviteMatch
import kr.kw.libri.common.MultiplayerPanel
import kr.kw.libri.common.FaceDetectionView
import kr.kw.libri.common.HandDoodle
import kr.kw.libri.common.HoldIdleTimer
import kr.kw.libri.common.LibriButton
import kr.kw.libri.common.LibriButtonStyle
import kr.kw.libri.common.LibriConfirmButton
import kr.kw.libri.common.LibriColors
import kr.kw.libri.common.LibriFonts
import kr.kw.libri.common.LibriLabel
import kr.kw.libri.common.LibriLobby
import kr.kw.libri.common.LibriScreen
import kr.kw.libri.common.LibriStep
import kr.kw.libri.common.LibriTag
import kr.kw.libri.common.LibriTopBar
import kr.kw.libri.common.LeaveOnDispose
import kr.kw.libri.common.NoteText
import kr.kw.libri.common.SketchCard
import kr.kw.libri.common.SketchSurface
import kr.kw.libri.common.SoundToggle
import kr.kw.libri.common.TitleText
import kr.kw.libri.common.highlighter
import kr.kw.libri.common.rememberCameraAccess

/** 자리마다의 색 — 점수판과 입장 버튼이 같은 색을 쓴다 */
private fun Seat.color(): Color = if (this == Seat.A) LibriColors.Coral else LibriColors.Sky

/** 참참참 — 자리를 고르기 전엔 대기실, 고른 뒤엔 게임 화면 */
@Composable
fun ChamScreen(
    onExit: () -> Unit,
    authorized: Boolean,
    modifier: Modifier = Modifier,
    match: InviteMatch? = null,
    onMatchUsed: () -> Unit = {},
    vm: ChamViewModel = viewModel(),
) {
    LifecycleStartEffect(vm) { onStopOrDispose { vm.leaveOnlineRoom() } }
    val seat by vm.seat.collectAsStateWithLifecycle()
    val me = seat
    // 사람이 떠나 얼굴 화면으로 돌아가는 등 화면이 바뀌면 방을 비운다 (온라인 자리가 잠긴 채 남지 않게)
    LeaveOnDispose { vm.leave(); vm.resetForNextVisitor() }

    // 기기 간 신청이 성사됐다 — 신청한 쪽은 A(먼저 공격), 받은 쪽은 B 로 그 방에 들어간다.
    // 수비할 때 카메라가 필요하니 권한이 없으면 먼저 묻는다 (거절해도 입장은 한다)
    var pendingMatch by remember { mutableStateOf<InviteMatch?>(null) }
    val camera = rememberCameraAccess {
        pendingMatch?.let { vm.join(it.room, if (it.asHost) Seat.A else Seat.B, online = true) }
        pendingMatch = null
    }
    LaunchedEffect(match?.id) {
        val m = match ?: return@LaunchedEffect
        onMatchUsed()
        if (camera.granted) vm.join(m.room, if (m.asHost) Seat.A else Seat.B, online = true)
        else { pendingMatch = m; camera.request() }
    }

    if (me == null) {
        BackHandler(onBack = onExit)
        ChamLobby(vm, onExit, modifier, authorized)
    } else {
        BackHandler { vm.leave() }
        ChamGame(vm, me, modifier)
    }
}

// ───────── 대기실 ─────────

@Composable
private fun ChamLobby(vm: ChamViewModel, onExit: () -> Unit, modifier: Modifier, authorized: Boolean) {
    var code by rememberSaveable { mutableStateOf("temi") }
    // 대부분은 혼자 온다 — 기본은 혼자 연습
    var selectedOnline by rememberSaveable { mutableStateOf(false) }
    val online = selectedOnline && authorized && vm.onlineAvailable
    val roomStatus by vm.roomStatus.collectAsStateWithLifecycle()

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
                LibriStep(3, "같은 쪽을 보면 수비자 하트 -1, 반대쪽이면 공격권이 넘어가요")
                LibriStep(4, "하트 ${CHAM_MAX_HP}개를 먼저 모두 잃으면 져요")
            }
        },
    ) {
        LibriLabel("누구와 할까요?")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            LibriButton(
                "Single Play", { selectedOnline = false }, Modifier.weight(1f),
                style = if (!online) LibriButtonStyle.COLOR else LibriButtonStyle.FLAT,
                minHeight = 68.dp, sub = "혼자 · 봇과 대결",
            )
            LibriButton(
                "Multi Play", { selectedOnline = true }, Modifier.weight(1f),
                style = if (online) LibriButtonStyle.COLOR else LibriButtonStyle.FLAT,
                enabled = authorized && vm.onlineAvailable, minHeight = 68.dp, sub = if (authorized && vm.onlineAvailable) "기기 2대 · 온라인" else "지금은 쓸 수 없어요",
            )
        }
        if (roomStatus == OnlineRoomStatus.FAILED) Text("게임방을 열지 못했어요. 다시 시도해 주세요.")
        if (online) {
            // 대결할 기기를 골라 신청 — 상대가 수락하면 MainActivity 가 두 기기를 같은 방에 넣는다
            MultiplayerPanel(InviteGame.CHAM)
        } else {
            // 혼자 할 때는 자리를 고를 필요가 없다 — 내가 먼저 공격
            LibriButton(
                "시작하기", { enter(Seat.A) }, Modifier.fillMaxWidth().padding(top = 6.dp),
                style = LibriButtonStyle.COLOR, color = Seat.A.color(), minHeight = 84.dp, fontSize = 26,
                sub = "내가 먼저 공격해요",
            )
        }
    }
}

// ───────── 게임 ─────────

@Composable
private fun ChamGame(vm: ChamViewModel, me: Seat, modifier: Modifier) {
    val room by vm.room.collectAsStateWithLifecycle()
    val online by vm.online.collectAsStateWithLifecycle()
    val roomStatus by vm.roomStatus.collectAsStateWithLifecycle()
    val playable = !online || roomStatus == OnlineRoomStatus.READY
    val soundOn = vm.soundOn
    val now by produceState(vm.now(), vm) {
        while (true) {
            withFrameMillis { }
            value = vm.now()
        }
    }

    val r = room.round
    // 결과까지 보여 주는 동안은 그 라운드 기준으로, 끝나면 다음 공격자 기준으로 화면을 그린다
    val shown = r?.takeIf { playable && (it.result == null || now < it.startAt + JUDGE_END + RESULT_SHOW_MS) }
    val attackerSeat = shown?.attacker ?: room.attacker
    val iAttack = attackerSeat == me
    val t = shown?.let { now - it.startAt }
    val pose = handPose(t, shown?.dir)
    val opponentHere = if (me == Seat.A) room.joinedB else room.joinedA
    var opponentWasHere by remember { mutableStateOf(false) }
    LaunchedEffect(opponentHere) { if (opponentHere) opponentWasHere = true }
    val winner = room.winner
    val gameOver = winner != null && shown == null      // 마지막 결과까지 보여 준 뒤 게임 끝 화면
    val canPick = playable && iAttack && shown == null && opponentHere && winner == null
    // 실제 대결 중에는 대기모드 타이머를 멈춘다 (상대 기다리기 · 게임 끝 화면은 센다)
    HoldIdleTimer(playable && opponentHere && !gameOver)

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

            // 상단 — 점수판, 초기화, 소리, 나가기. 판이 진행 중이라 '나가기'·'점수 초기화'는 한 번 더 눌러야 한다
            LibriTopBar(
                onExit = { vm.leave() }, modifier = Modifier.align(Alignment.TopCenter),
                confirmExit = opponentHere,                                   // 상대를 기다리는 빈 방은 바로 나간다
                exitConfirmLabel = if (wide) "정말 나갈까요?" else "정말?",   // 좁은 화면은 점수판 자리를 남긴다
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        // 좁은 화면에서는 모드 이름 대신 밑줄 친 글자 버튼을 둔다 (둘째 줄 점수판 자리를 남긴다)
                        if (wide) NoteText(if (online) "Multi Play" else "Single Play (봇)", size = 17.sp, color = LibriColors.Pencil)
                        if (!wide) {
                            var armReset by remember { mutableStateOf(false) }
                            LaunchedEffect(armReset) { if (armReset) { delay(3000); armReset = false } }
                            TextLink(if (armReset) "정말 다시?" else "다시 시작", if (armReset) LibriColors.Stamp else LibriColors.Pencil) {
                                if (armReset) { armReset = false; vm.resetScore() } else armReset = true
                            }
                            SoundToggle(soundOn, { vm.toggleSound() }, minHeight = 34.dp, fontSize = 14)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        SeatHp(Seat.A, room.hp(Seat.A), me, attackerSeat, wide)
                        Text("vs", fontSize = 18.sp, fontFamily = LibriFonts.Round, color = LibriColors.Pencil)
                        SeatHp(Seat.B, room.hp(Seat.B), me, attackerSeat, wide)
                    }
                }
                if (wide) {
                    SoundToggle(soundOn, { vm.toggleSound() })
                    LibriConfirmButton("다시 시작", "정말 다시?", { vm.resetScore() }, style = LibriButtonStyle.FLAT, fontSize = 16)
                }
            }

            // 누구 턴인지 — 내 턴(공격)과 상대 턴(수비)을 자리 색으로 구분한다
            val turn = if (iAttack) Turn("내 턴 · 공격", me.color()) else Turn("상대 턴 · 수비", me.other.color())

            // 상태 안내 — 지금 무엇을 해야 하는지
            val status: @Composable (Modifier) -> Unit = { at ->
                when {
                    !playable -> StatusCard(at, when (roomStatus) {
                        OnlineRoomStatus.PERMISSION_DENIED -> "게임방 접근 권한이 없어요"
                        OnlineRoomStatus.SEAT_TAKEN -> "이미 사용 중인 자리예요"
                        OnlineRoomStatus.DISCONNECTED -> "연결이 끊겼어요"
                        OnlineRoomStatus.TIMED_OUT -> "입장 응답이 늦어요"
                        OnlineRoomStatus.FAILED -> "게임을 동기화하지 못했어요"
                        else -> "온라인 게임방에 연결하는 중…"
                    }, sub = if (roomStatus == OnlineRoomStatus.CONNECTING) "" else "나간 뒤 다시 입장해 주세요")
                    gameOver -> Unit                                    // 가운데의 게임 끝 카드가 대신한다
                    !opponentHere && opponentWasHere -> StatusCard(at, "상대가 나갔어요", sub = "다시 입장하면 대결을 계속할 수 있어요")
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
                        val result = resultLines(shown, me, finalBlow = winner != null)
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
                    if (playable && opponentHere) mirror(Modifier.align(Alignment.BottomStart).padding(start = 28.dp, bottom = 24.dp))
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
                    if (playable && opponentHere) mirror(Modifier)
                }
            }

            // 내 체력바 — 화면 아래 가운데 (가로 화면. 세로 화면은 아래가 버튼·안내로 차서 위 점수판의 하트만 쓴다)
            if (wide && playable && opponentHere) {
                Column(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    HeartBar(room.hp(me), heartSize = 46.dp)
                    LibriTag("내 하트", me.color(), fontSize = 14.sp)
                }
            }

            // 게임 끝 — 승패와 다시 하기
            if (gameOver) {
                GameOverCard(
                    won = room.winner == me,
                    online = online,
                    onAgain = { vm.resetScore() },
                    onExit = { vm.leave() },
                    modifier = Modifier.align(Alignment.Center).width(if (wide) 420.dp else 320.dp),
                )
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

/** 밑줄 친 연필 글자 버튼 — 좁은 화면의 상단 띠처럼 자리가 모자랄 때 */
@Composable
private fun TextLink(text: String, color: Color, onClick: () -> Unit) {
    Text(
        text, fontSize = 16.sp, color = color, fontFamily = LibriFonts.Hand, maxLines = 1, softWrap = false,
        modifier = Modifier.clickable(onClick = onClick).drawBehind {
            val y = size.height - 2.dp.toPx()
            drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        },
    )
}

/** 누구 턴인지 알리는 꼬리표 */
private class Turn(val label: String, val color: Color)

/** 점수판의 한 자리 — 남은 하트, 누가 나인지, 누가 공격 중인지 함께 보여 준다 */
@Composable
private fun SeatHp(seat: Seat, hp: Int, me: Seat, attacker: Seat, wide: Boolean) {
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
        HeartBar(hp, Modifier.padding(vertical = 6.dp), heartSize = if (wide) 22.dp else 18.dp)
        if (attacking && wide) Text("공격", fontSize = 15.sp, fontFamily = LibriFonts.Round, color = LibriColors.Ink)
    }
}

/** 게임 끝 카드 — 승패, 다시 하기, 나가기 */
@Composable
private fun GameOverCard(won: Boolean, online: Boolean, onAgain: () -> Unit, onExit: () -> Unit, modifier: Modifier) {
    SketchCard(
        modifier, seed = 11,
        padding = PaddingValues(horizontal = 22.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LibriTag("게임 끝", LibriColors.Yellow, fontSize = 17.sp)
        TitleText(
            if (won) "승리!" else "패배…", size = 56.sp,
            color = if (won) LibriColors.Mint else LibriColors.Stamp, align = TextAlign.Center,
            modifier = if (won) Modifier.highlighter() else Modifier,
        )
        NoteText(
            if (won) "상대 하트를 모두 없앴어요" else "하트를 모두 잃었어요",
            size = 20.sp, color = LibriColors.Pencil,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            LibriButton("나가기", onExit, Modifier.weight(1f), style = LibriButtonStyle.FLAT, minHeight = 64.dp, fontSize = 20)
            LibriButton(
                "다시 하기", onAgain, Modifier.weight(1.4f), style = LibriButtonStyle.COLOR, minHeight = 64.dp, fontSize = 22,
                sub = if (online) "두 사람 모두 하트 3개로" else "하트 3개로 다시",
            )
        }
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
private fun resultLines(r: Round, me: Seat, finalBlow: Boolean = false): ResultLines {
    val look = headKo(r.look)
    val hit = r.result == "HIT"
    if (hit && finalBlow) {
        return if (r.attacker == me) ResultLines("공격 성공!", "상대 고개: $look", "상대 하트가 모두 없어졌어요!")
        else ResultLines("잡혔어요!", "내 고개: $look", "마지막 하트를 잃었어요")
    }
    return if (r.attacker == me) {
        if (hit) ResultLines("공격 성공!", "상대 고개: $look", "상대 하트 -1 · 내가 한 번 더 공격해요")
        else ResultLines("상대가 피했어요", "상대 고개: $look", "공격권이 상대에게 넘어가요")
    } else {
        if (hit) ResultLines("잡혔어요!", "내 고개: $look", "내 하트 -1 · 상대가 계속 공격해요")
        else ResultLines("피했어요!", "내 고개: $look", "공격권을 가져왔어요 · 이제 내가 공격!")
    }
}
