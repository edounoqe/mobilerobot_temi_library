package kr.kw.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

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
        ChamLobby(vm, onExit, modifier)
    } else {
        ChamGame(vm, me, modifier)
    }
}

@Composable
private fun ChamLobby(vm: ChamViewModel, onExit: () -> Unit, modifier: Modifier) {
    var code by rememberSaveable { mutableStateOf("temi") }
    var online by rememberSaveable { mutableStateOf(vm.onlineAvailable) }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("참참참", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text(
            "공격자는 손가락 방향을 미리 고르고, 수비자는 \"참!\"에 맞춰 고개를 돌립니다.\n" +
                "손가락과 같은 쪽을 보면 공격 성공(+1점, 공격권 유지), 반대쪽을 보면 공격권이 넘어갑니다.",
            style = MaterialTheme.typography.bodyLarge,
        )

        Text("모드", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { online = true },
                enabled = vm.onlineAvailable,
                colors = if (online) DarkBtn else LightBtn,
                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
            ) { Text("온라인 (기기 2대)") }
            Button(
                onClick = { online = false },
                colors = if (!online) DarkBtn else LightBtn,
                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
            ) { Text("혼자 테스트 (봇)") }
        }
        if (!vm.onlineAvailable) {
            Text(
                "Firebase 설정이 없어 온라인 모드를 쓸 수 없습니다. DetectionDemo/local.properties 에 firebase.* 값을 넣고 다시 빌드하세요.",
                color = Color(0xFFB00020),
            )
        }
        if (online) {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.trim() },
                label = { Text("방 코드 (두 기기가 같게)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Text("자리 선택 — 두 기기가 서로 다른 자리를 고릅니다. A가 먼저 공격합니다.", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { vm.join(code, Seat.A, online) },
                colors = DarkBtn,
                modifier = Modifier.weight(1f).heightIn(min = 64.dp),
            ) { Text("A 로 입장") }
            Button(
                onClick = { vm.join(code, Seat.B, online) },
                colors = DarkBtn,
                modifier = Modifier.weight(1f).heightIn(min = 64.dp),
            ) { Text("B 로 입장") }
        }
        OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) { Text("뒤로") }
    }
}

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
    val pose = handPose(shown?.let { now - it.startAt }, shown?.dir)
    val opponentHere = if (me == Seat.A) room.joinedB else room.joinedA
    val canPick = iAttack && shown == null && opponentHere

    Box(modifier = modifier.fillMaxSize().background(Color(0xFF12161B))) {
        // 수비자: 내 얼굴(카메라) — 고개 방향을 ViewModel 로 보낸다. 공격자: 카메라 없음(상대 얼굴도 안 보임)
        if (!iAttack) {
            FaceDetectionView(Modifier.fillMaxSize(), showInfo = false, onFaces = vm::onFaces)
        }
        ChamHand(pose.rotation, pose.bounce, Modifier.fillMaxSize())

        // 상단 바 — 내 자리·역할, 점수, 나가기
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                "나: ${me.name} · ${if (iAttack) "공격" else "수비"}" + if (online) "" else " · 봇 테스트",
                color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
            )
            Text("A ${room.scoreA} : ${room.scoreB} B", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            OutlinedButton(onClick = { vm.resetScore() }) { Text("초기화", color = Color.White) }
            Button(onClick = { vm.leave() }, colors = LightBtn) { Text("나가기") }
        }

        // 가운데 위 — 상태 메시지
        val msg = when {
            !opponentHere -> "상대를 기다리는 중…"
            shown == null -> if (iAttack) "손가락 방향을 고르세요" else "상대가 방향을 고르는 중…"
            shown.result != null -> resultText(shown, me)
            else -> pose.label
        }
        Text(
            msg,
            color = Color.White,
            fontSize = if (shown != null && shown.result == null) 56.sp else 28.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 80.dp, start = 16.dp, end = 16.dp)
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 20.dp, vertical = 8.dp),
        )

        // 공격자: 방향 고르기 (수비자 화면 기준)
        if (canPick) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(24.dp),
            ) {
                Button(
                    onClick = { vm.attack(Dir.LEFT) }, colors = LightBtn,
                    modifier = Modifier.weight(1f).heightIn(min = 72.dp),
                ) { Text("👈 왼쪽", fontSize = 24.sp) }
                Button(
                    onClick = { vm.attack(Dir.RIGHT) }, colors = LightBtn,
                    modifier = Modifier.weight(1f).heightIn(min = 72.dp),
                ) { Text("오른쪽 👉", fontSize = 24.sp) }
            }
        }
        if (iAttack && shown != null && shown.result == null) {
            Text(
                "내가 고른 방향: ${shown.dir.ko()} (상대 화면 기준)",
                color = Color.White,
                modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
            )
        }
    }
}

private fun resultText(r: Round, me: Seat): String {
    val look = headKo(r.look)
    val hit = r.result == "HIT"
    return if (r.attacker == me) {
        if (hit) "공격 성공! (상대: $look)\n공격권 유지 +1점" else "상대가 피했어요 (상대: $look)\n공격권이 넘어갑니다"
    } else {
        if (hit) "잡혔어요! (내 고개: $look)\n상대 +1점" else "피했어요! (내 고개: $look)\n이제 내가 공격"
    }
}

private val DarkBtn: ButtonColors
    @Composable get() = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White)

private val LightBtn: ButtonColors
    @Composable get() = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
