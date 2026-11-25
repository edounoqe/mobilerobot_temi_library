package kr.kw.libri.omok

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kr.kw.libri.backend.InviteGame
import kr.kw.libri.backend.InviteMatch
import kr.kw.libri.common.MultiplayerPanel
import kr.kw.libri.common.LibriButton
import kr.kw.libri.common.LibriButtonStyle
import kr.kw.libri.common.LibriConfirmButton
import kr.kw.libri.common.HoldIdleTimer
import kr.kw.libri.common.LeaveOnDispose
import kr.kw.libri.common.LibriColors
import kr.kw.libri.common.LibriFonts
import kr.kw.libri.common.LibriLabel
import kr.kw.libri.common.LibriLobby
import kr.kw.libri.common.LibriScreen
import kr.kw.libri.common.LibriStep
import kr.kw.libri.common.LibriTag
import kr.kw.libri.common.LibriTopBar
import kr.kw.libri.common.NoteText
import kr.kw.libri.common.OmokDoodle
import kr.kw.libri.common.SketchCard
import kr.kw.libri.common.SketchSurface
import kr.kw.libri.common.Stamp
import kr.kw.libri.common.TitleText
import kr.kw.libri.common.highlighter
import kr.kw.libri.common.libriTextFieldColors

private val TURN_FILL = Color(0xFFFFF1B8)       // 차례인 사람의 이름표 — 옅은 형광펜

/** 오목 — 방에 들어가기 전엔 대기실, 들어간 뒤엔 대국 화면. 랭킹은 어디서든 열 수 있다 */
@Composable
fun OmokScreen(
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    match: InviteMatch? = null,
    onMatchUsed: () -> Unit = {},
    vm: OmokViewModel = viewModel(),
) {
    // 기기 간 신청이 성사됐다 — 두 기기가 같은 방(신청 방 코드)에 들어간다. 먼저 들어온 쪽이 흑
    LaunchedEffect(match?.id) {
        val m = match ?: return@LaunchedEffect
        onMatchUsed()
        vm.closeRanking()
        vm.start(OmokMode.ONLINE, vm.preferredName.ifBlank { vm.suggestedName }, m.room)
    }
    val seat by vm.seat.collectAsStateWithLifecycle()
    val rank by vm.rank.collectAsStateWithLifecycle()
    val me = seat
    val ranking = rank
    // 사람이 떠나 얼굴 화면으로 돌아가는 등 화면이 바뀌면 방을 비운다 (온라인 판은 기권 처리)
    LeaveOnDispose { vm.closeRanking(); vm.leave(); vm.clearError() }
    when {
        ranking != null -> {
            BackHandler { vm.closeRanking() }
            OmokRankingPage(ranking, vm, modifier)
        }
        me == null -> {
            BackHandler(onBack = onExit)
            OmokLobby(vm, onExit, modifier)
        }
        else -> {
            BackHandler { vm.leave() }
            OmokGame(vm, me, modifier)
        }
    }
}

// ───────── 대기실 ─────────

@Composable
private fun OmokLobby(vm: OmokViewModel, onExit: () -> Unit, modifier: Modifier) {
    var online by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf(vm.preferredName.ifBlank { vm.myName.value.ifBlank { vm.suggestedName } }) }
    LaunchedEffect(name) { vm.preferredName = name }       // 신청이 성사돼 바로 들어갈 때도 이 이름을 쓴다
    var level by rememberSaveable { mutableStateOf(BotLevel.NORMAL) }
    val joining by vm.joining.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()

    LibriLobby(
        title = "오목",
        tagline = "다섯 알을 먼저 나란히 놓으면 이겨요",
        onBack = onExit,
        modifier = modifier,
        art = { OmokDoodle(it) },
        intro = {
            SketchCard(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LibriStep(1, "흑이 먼저, 번갈아 한 알씩 놓아요")
                LibriStep(2, "가로·세로·대각선으로 다섯 알을 먼저 이으면 승리!")
                LibriStep(3, "한 번에 '열린 3' 을 두 줄 만드는 삼삼 자리에는 둘 수 없어요")
            }
        },
    ) {
        LibriLabel("누구와 할까요?")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            LibriButton(
                "Single Play", { online = false }, Modifier.weight(1f),
                style = if (!online) LibriButtonStyle.COLOR else LibriButtonStyle.FLAT,
                minHeight = 68.dp, sub = "혼자 · 봇과 대결",
            )
            LibriButton(
                "Multi Play", { online = true }, Modifier.weight(1f),
                style = if (online) LibriButtonStyle.COLOR else LibriButtonStyle.FLAT,
                minHeight = 68.dp, sub = "기기 2대 · 랭킹",
            )
        }
        if (!online) {
            LibriLabel("봇 난이도", Modifier.padding(top = 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                for (l in BotLevel.entries) {
                    LibriButton(
                        l.label, { level = l }, Modifier.weight(1f),
                        style = if (level == l) LibriButtonStyle.COLOR else LibriButtonStyle.FLAT,
                        minHeight = 62.dp, fontSize = 19,
                    )
                }
            }
        }
        if (online) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(NAME_MAX) },
                label = { Text("이름 (랭킹에 올라가요)") },
                singleLine = true,
                colors = libriTextFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            // 대결할 기기를 골라 신청 — 상대가 수락하면 MainActivity 가 두 기기를 같은 방에 넣는다
            MultiplayerPanel(InviteGame.OMOK)
        }
        error?.let { Text(it, color = LibriColors.Stamp, fontSize = 15.sp, lineHeight = 21.sp) }

        if (!online || joining) {
            LibriButton(
                if (joining) "연결하는 중…" else "시작하기",
                { vm.start(OmokMode.BOT, name, "", level) },
                Modifier.fillMaxWidth().padding(top = 4.dp),
                style = LibriButtonStyle.COLOR, color = LibriColors.Mint, enabled = !joining, minHeight = 84.dp, fontSize = 26,
                sub = if (joining) null else "내가 흑(선)으로 시작해요",
            )
        }
        LibriButton("랭킹 보기", { vm.openRanking() }, Modifier.fillMaxWidth(), minHeight = 56.dp, fontSize = 19)
    }
}

// ───────── 대국 ─────────

@Composable
private fun OmokGame(vm: OmokViewModel, me: Seat, modifier: Modifier) {
    val room by vm.room.collectAsStateWithLifecycle()
    val mode by vm.mode.collectAsStateWithLifecycle()
    val myName by vm.myName.collectAsStateWithLifecycle()
    val online = mode == OmokMode.ONLINE

    // 내가 두는 판만 보여 준다 (방에 남아 있는 남의 판은 새 판이 열릴 때까지 무시)
    val game = room.game?.takeIf { it.idOf(me) == vm.myId }
    val view = remember(game?.id, game?.moves) { replay(game?.moves ?: emptyList()) }
    val outcome = game?.let { outcomeOf(it, view) }
    val opponent = room.player(me.other)
    val myStone = game?.stoneOf(me) ?: BLACK
    val playing = game != null && outcome == null && opponent != null
    val myTurn = playing && view.turn == myStone
    // 실제 대국 중에는 대기모드 타이머를 멈춘다 (상대 기다리기 · 결과 화면은 센다)
    HoldIdleTimer(playing)

    // 이번 수의 남은 시간 (초) — 15초 제한, 다 쓰면 진다
    val deadline by vm.deadline.collectAsStateWithLifecycle()
    val leftMs by produceState(MOVE_TIME_MS, deadline) {
        val end = deadline ?: return@produceState
        while (true) {
            value = (end - System.currentTimeMillis()).coerceAtLeast(0L)
            if (value == 0L) break
            withFrameMillis { }
        }
    }

    // 놓아 볼 자리 — 한 번 누르면 표시, 같은 자리를 한 번 더 누르면 놓는다 (잘못 누른 수를 막는다)
    var ghost by remember(game?.id, view.count) { mutableIntStateOf(-1) }
    val ghostCheck = if (myTurn && ghost >= 0) checkPlace(view.cells, ghost, myStone) else null
    val onTap: (Int) -> Unit = { cell ->
        if (myTurn && view.cells[cell] == EMPTY) {
            if (cell == ghost && ghostCheck == Place.OK) vm.place(cell) else ghost = cell
        }
    }

    // 기권은 한 번 더 눌러야 한다
    var confirmResign by remember(game?.id) { mutableStateOf(false) }
    LaunchedEffect(confirmResign) {
        if (confirmResign) {
            delay(3000)
            confirmResign = false
        }
    }

    val board: @Composable (Modifier) -> Unit = { at ->
        OmokBoard(view, ghost = if (myTurn) ghost else -1, ghostStone = myStone, ghostOk = ghostCheck != Place.DOUBLE_THREE, onTap = onTap, modifier = at)
    }
    val players: @Composable (Modifier, Boolean) -> Unit = { at, stacked ->
        val mine: @Composable (Modifier) -> Unit = { PlayerChip("$myName (나)", myStone, turn = myTurn, modifier = it, tag = stacked) }
        val theirs: @Composable (Modifier) -> Unit = {
            PlayerChip(opponent?.name ?: "기다리는 중…", otherStone(myStone), turn = playing && !myTurn, waiting = opponent == null, modifier = it, tag = stacked)
        }
        if (stacked) {
            Column(at, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                mine(Modifier.fillMaxWidth())
                theirs(Modifier.fillMaxWidth())
            }
        } else {
            Row(at, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                mine(Modifier.weight(1f))
                theirs(Modifier.weight(1f))
            }
        }
    }
    val clock: @Composable (Modifier) -> Unit = { at ->
        if (playing && deadline != null) MoveClock(leftMs, myTurn, at)
    }
    val status: @Composable (Modifier) -> Unit = { at ->
        when {
            outcome != null -> ResultCard(
                at, outcome, me, opponentLeft = opponent == null, ranked = online && (game?.moves?.size ?: 0) >= RANK_MIN_MOVES,
                onRematch = { vm.rematch() }, onRanking = if (online) ({ vm.openRanking() }) else null,
            )
            opponent == null -> StatusCard(
                at, "상대를 기다리는 중…", sub = "상대 기기가 게임방에 들어오고 있어요",
            )
            game == null -> StatusCard(at, "판을 펴는 중…")
            myTurn -> StatusCard(
                at, "내 차례!", highlight = true,
                sub = when (ghostCheck) {
                    Place.OK -> "같은 자리를 한 번 더 누르면 돌이 놓여요"
                    Place.DOUBLE_THREE -> "삼삼 자리라 둘 수 없어요. 다른 자리를 골라 주세요"
                    else -> "돌을 놓을 자리를 눌러 주세요"
                },
                warn = ghostCheck == Place.DOUBLE_THREE,
            )
            else -> StatusCard(at, "상대 차례", sub = "${opponent.name} 님이 생각하고 있어요")
        }
    }
    val actions: @Composable (Modifier) -> Unit = { at ->
        if (playing) {
            Row(at, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LibriButton(
                    "여기에 두기", { if (ghost >= 0) vm.place(ghost) }, Modifier.weight(1f),
                    style = LibriButtonStyle.COLOR, color = LibriColors.Mint, enabled = ghostCheck == Place.OK, minHeight = 64.dp, fontSize = 22,
                )
                LibriButton(
                    if (confirmResign) "정말 기권?" else "기권",
                    { if (confirmResign) vm.resign() else confirmResign = true },
                    style = if (confirmResign) LibriButtonStyle.COLOR else LibriButtonStyle.FLAT, color = LibriColors.Coral,
                    minHeight = 64.dp, fontSize = 18,
                )
            }
        }
    }
    val label = if (online) "Multi Play" else "Single Play (봇)"

    LibriScreen(modifier) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            if (maxWidth > maxHeight) {
                // 넓은 화면(temi) — 왼쪽에 판, 오른쪽에 이름표 · 안내 · 버튼
                Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    board(Modifier.fillMaxHeight())
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            NoteText(label, Modifier.weight(1f), size = 20.sp)
                            LibriConfirmButton("나가기", "정말 나갈까요?", { vm.leave() }, needConfirm = playing)
                        }
                        players(Modifier.fillMaxWidth(), true)
                        clock(Modifier.fillMaxWidth())
                        status(Modifier.fillMaxWidth())
                        Spacer(Modifier.weight(1f))
                        actions(Modifier.fillMaxWidth())
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    LibriTopBar(onExit = { vm.leave() }, confirmExit = playing) { NoteText(label, Modifier.weight(1f), size = 18.sp) }
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        players(Modifier.fillMaxWidth(), false)
                        board(Modifier.fillMaxWidth())
                        clock(Modifier.fillMaxWidth())
                        status(Modifier.fillMaxWidth())
                        actions(Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

/**
 * 이번 수의 남은 시간 — 숫자와 줄어드는 막대. 5초 남으면 빨갛게 바뀐다.
 * @param mine 내 차례인지 (상대 차례면 옅게)
 */
@Composable
private fun MoveClock(leftMs: Long, mine: Boolean, modifier: Modifier) {
    val sec = ((leftMs + 999) / 1000).toInt()
    val urgent = sec <= 5
    val color = if (urgent) LibriColors.Stamp else if (mine) LibriColors.Ink else LibriColors.Pencil
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            if (mine) "내 시간" else "상대 시간", fontSize = 16.sp, fontFamily = LibriFonts.Round,
            color = if (mine) LibriColors.Ink else LibriColors.Pencil,
        )
        Canvas(Modifier.weight(1f).height(16.dp)) {
            val r = CornerRadius(size.height / 2f)
            drawRoundRect(LibriColors.Card, cornerRadius = r)
            val f = (leftMs.toFloat() / MOVE_TIME_MS).coerceIn(0f, 1f)
            if (f > 0f) drawRoundRect(
                if (urgent) LibriColors.Coral else if (mine) LibriColors.Mint else LibriColors.Faint,
                size = Size(size.width * f, size.height), cornerRadius = r,
            )
            drawRoundRect(LibriColors.Ink, cornerRadius = r, style = Stroke(1.6.dp.toPx()))
        }
        Text("${sec}초", fontSize = if (urgent) 26.sp else 22.sp, fontFamily = LibriFonts.Round, color = color)
    }
}

/** 돌 하나 — 이름표에 붙는 작은 그림 */
@Composable
private fun StoneDot(stone: Int, modifier: Modifier) {
    Canvas(modifier) {
        val r = size.minDimension / 2f - 1.dp.toPx()
        drawCircle(if (stone == BLACK) LibriColors.Ink else LibriColors.Card, r)
        drawCircle(LibriColors.Ink, r, style = Stroke(1.8.dp.toPx()))
    }
}

/** 이름표 — 돌 색, 이름, 지금 차례인지 (차례인 쪽은 형광펜 색). tag: 자리가 넉넉하면 '차례' 꼬리표도 붙인다 */
@Composable
private fun PlayerChip(name: String, stone: Int, turn: Boolean, modifier: Modifier, waiting: Boolean = false, tag: Boolean = true) {
    SketchSurface(
        modifier, fill = if (turn) TURN_FILL else LibriColors.Card, radius = 18.dp, shadow = 4.dp, seed = stone * 7, fillContent = true,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StoneDot(stone, Modifier.size(30.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    name, fontSize = 19.sp, fontFamily = LibriFonts.Round, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (waiting) LibriColors.Faint else LibriColors.Ink,
                )
                Text(if (stone == BLACK) "흑 · 선" else "백", fontSize = 13.sp, color = LibriColors.Pencil, maxLines = 1)
            }
            if (turn && tag) LibriTag("차례", LibriColors.Yellow)
        }
    }
}

/** 지금 무엇을 해야 하는지 */
@Composable
private fun StatusCard(modifier: Modifier, title: String, sub: String? = null, highlight: Boolean = false, warn: Boolean = false) {
    SketchCard(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TitleText(title, size = 30.sp, modifier = if (highlight) Modifier.highlighter() else Modifier)
        if (sub != null) Text(sub, fontSize = 16.sp, lineHeight = 22.sp, color = if (warn) LibriColors.Stamp else LibriColors.Pencil)
    }
}

/** 판이 끝났을 때 — 도장, 한 줄 설명, 한 판 더 */
@Composable
private fun ResultCard(
    modifier: Modifier,
    outcome: Outcome,
    me: Seat,
    opponentLeft: Boolean,
    ranked: Boolean,
    onRematch: () -> Unit,
    onRanking: (() -> Unit)?,
) {
    val won = outcome.winner == me
    val drawn = outcome.winner == null
    SketchCard(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Stamp(
                if (drawn) "무승부" else if (won) "승리!" else "패배",
                color = if (won) LibriColors.Stamp else LibriColors.Pencil, fontSize = 26.sp,
            )
            Column(Modifier.weight(1f)) {
                TitleText(if (drawn) "비겼어요" else if (won) "이겼어요!" else "졌어요", size = 28.sp)
                Text(
                    when (outcome.reason) {
                        Reason.FIVE -> if (won) "다섯 알을 먼저 이었어요" else "상대가 다섯 알을 이었어요"
                        Reason.RESIGN -> if (!won) "기권했어요" else if (opponentLeft) "상대가 나갔어요" else "상대가 기권했어요"
                        Reason.TIMEOUT -> if (won) "상대가 제한시간 ${MOVE_TIME_MS / 1000}초를 넘겼어요" else "제한시간 ${MOVE_TIME_MS / 1000}초를 넘겼어요"
                        Reason.FULL -> "판이 가득 찼어요"
                    },
                    fontSize = 16.sp, color = LibriColors.Pencil,
                )
            }
        }
        if (ranked) NoteText("이번 판이 랭킹에 올라갔어요", size = 18.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            LibriButton(
                "한 판 더", onRematch, Modifier.weight(1f), style = LibriButtonStyle.COLOR, enabled = !opponentLeft,
                minHeight = 60.dp, fontSize = 21, sub = if (opponentLeft) "상대를 기다리는 중" else "흑을 바꿔서",
            )
            if (onRanking != null) LibriButton("랭킹 보기", onRanking, Modifier.weight(1f), minHeight = 60.dp, fontSize = 19)
        }
    }
}

// ───────── 랭킹 ─────────

@Composable
private fun OmokRankingPage(state: RankState, vm: OmokViewModel, modifier: Modifier) {
    val myName by vm.myName.collectAsStateWithLifecycle()
    LibriScreen(modifier) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LibriButton("←  돌아가기", { vm.closeRanking() }, style = LibriButtonStyle.FLAT, minHeight = 44.dp, fontSize = 16)
                TitleText("오목 랭킹", size = 44.sp)
                NoteText("기기 2대 대결에서 이긴 판이 많은 순서예요")
                SketchCard(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    when (state) {
                        RankState.Loading -> NoteText("불러오는 중…", Modifier.padding(vertical = 10.dp))
                        RankState.Failed -> {
                            Text(
                                "랭킹을 불러오지 못했어요.\n인터넷 연결과 Firebase(Firestore) 의 omok_ranking 읽기 규칙을 확인해 주세요.",
                                color = LibriColors.Stamp, fontSize = 16.sp, lineHeight = 23.sp, modifier = Modifier.padding(vertical = 6.dp),
                            )
                            LibriButton("다시 불러오기", { vm.openRanking() }, minHeight = 52.dp, fontSize = 18)
                        }
                        is RankState.Loaded -> if (state.list.isEmpty()) {
                            NoteText("아직 기록이 없어요. 첫 승리의 주인공이 되어 보세요!", Modifier.padding(vertical = 10.dp))
                        } else {
                            for ((i, e) in state.list.withIndex()) RankRow(i + 1, e, mine = e.name == myName, last = i == state.list.lastIndex)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RankRow(rank: Int, e: RankEntry, mine: Boolean, last: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {       // 줄 사이에 연필로 그은 선
                if (!last) drawLine(LibriColors.Faint, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            }
            .padding(vertical = 9.dp),
    ) {
        Box(
            Modifier.size(38.dp).drawBehind {
                val medal = when (rank) {
                    1 -> LibriColors.Yellow
                    2 -> Color(0xFFD9D5CC)
                    3 -> Color(0xFFF2B98A)
                    else -> Color.Transparent
                }
                val r = size.minDimension / 2f - 1.dp.toPx()
                drawCircle(medal, r)
                drawCircle(if (rank <= 3) LibriColors.Ink else LibriColors.Faint, r, style = Stroke(1.8.dp.toPx()))
            },
            contentAlignment = Alignment.Center,
        ) {
            Text("$rank", fontSize = 18.sp, fontFamily = LibriFonts.Round, color = LibriColors.Ink)
        }
        Text(
            e.name, fontSize = 20.sp, fontFamily = LibriFonts.Round, color = LibriColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false).then(if (mine) Modifier.highlighter() else Modifier),
        )
        Spacer(Modifier.weight(1f))
        Text("${e.win}승 ${e.lose}패", fontSize = 19.sp, fontFamily = LibriFonts.Round, color = LibriColors.Ink)
    }
}
